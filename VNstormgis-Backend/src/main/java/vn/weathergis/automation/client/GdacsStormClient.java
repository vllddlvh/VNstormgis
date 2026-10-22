package vn.weathergis.automation.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import vn.weathergis.automation.client.dto.GdacsEventCollection;
import vn.weathergis.automation.client.dto.GdacsTrackCollection;
import vn.weathergis.automation.client.exception.GdacsClientException;

/**
 * Client 2 tầng gọi GDACS — KHÔNG biết về CSDL, chỉ trả dữ liệu đã bóc tách + JSON gốc:
 *
 * <ol>
 *   <li>{@code fetchActiveTropicalCyclones()} → danh sách sự kiện TC đang hoạt động (Search API);
 *   <li>{@code fetchStormTrack(event)} → quỹ đạo từng sự kiện (geometry API — URL mà chính payload
 *       GDACS tham chiếu qua {@code url.geometry}; tài nguyên datareport cũ trả 403 cho client phi
 *       trình duyệt, xem biên bản quyết định trong Decisions.md).
 * </ol>
 *
 * Retry cục bộ theo backoffMs (mạng + 5xx) — kế thừa khuôn mẫu OpenWeatherMapClient (W2-03). Chuẩn
 * hóa gió về m/s tại đây (quyết định #6): km/h ÷ 3.6, knot × 0.514444. Parser phòng vệ (RSK-W2-02):
 * bản ghi thiếu trường bắt buộc bị loại khỏi kết quả + WARN kèm lý do.
 */
@Component
public class GdacsStormClient {

    private static final Logger log = LoggerFactory.getLogger(GdacsStormClient.class);
    private static final String EVENT_TYPE_TC = "TC";
    private static final String LINE_STRING = "LineString";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String searchPath;
    private final String trackPath;
    private final long[] backoffMs;

    public GdacsStormClient(
            RestClient gdacsRestClient,
            ObjectMapper objectMapper,
            @Value(
                            "${weathergis.external.gdacs.search-path:/gdacsapi/api/Events/geteventlist/search}")
                    String searchPath,
            @Value("${weathergis.external.gdacs.track-path:/gdacsapi/api/polygons/getgeometry}")
                    String trackPath,
            @Value("${weathergis.external.gdacs.backoff-ms:500,1000,2000}") long[] backoffMs) {
        this.restClient = gdacsRestClient;
        this.objectMapper = objectMapper;
        this.searchPath = searchPath;
        this.trackPath = trackPath;
        this.backoffMs = backoffMs;
    }

    /**
     * Sự kiện bão đã bóc tách — gió đã chuẩn hóa m/s; áp suất NULL khi nguồn không công bố (V6).
     */
    public record GdacsEvent(
            String eventId,
            String episodeId,
            String name,
            OffsetDateTime fromDate,
            OffsetDateTime toDate,
            String category, // cấp cảnh báo GDACS Green/Orange/Red (quyết định #14)
            double maxWindSpeedMs,
            Double minPressureHpa,
            double longitude, // GeoJSON [lng, lat] — AGENTS.md mục 1
            double latitude,
            String rawJson) {}

    /** Kết quả tầng 1: các sự kiện hợp lệ + danh sách lý do bản ghi bị loại (ghi audit). */
    public record GdacsEventFeed(List<GdacsEvent> events, List<String> skipReasons) {}

    /**
     * Mốc quỹ đạo đã bóc tách. Gió/gust/áp suất theo mốc NULL vì nguồn GDACS hiện hành không công
     * bố (xác minh 2026-10-22 — biên bản quyết định V6); cấp bão lấy từ polygonlabel (HU/TS/TD).
     */
    public record GdacsTrackPoint(
            String trackType, // "historical" | "forecast"
            OffsetDateTime atTime, // → forecast_time
            double longitude,
            double latitude,
            Double windSpeedMs,
            Double windGustMs,
            Double pressureHpa,
            String category,
            double impactRadiusKm) {}

    /** Chuẩn hóa tốc độ gió về m/s (quyết định #6) — public để unit test độc lập. */
    public static double toMs(double value, String unit) {
        if (unit != null && "kt".equalsIgnoreCase(unit.trim())) {
            return value * 0.514444;
        }
        return value / 3.6; // mặc định km/h — đơn vị GDACS công bố trong severityunit
    }

    /** Tầng 1: danh sách sự kiện TC đang hoạt động (iscurrent = true). */
    public GdacsEventFeed fetchActiveTropicalCyclones() {
        GdacsClientException lastError = null;
        for (int attempt = 0; attempt <= backoffMs.length; attempt++) {
            if (attempt > 0) {
                log.debug("GDACS search retry lần {} sau {}ms", attempt, backoffMs[attempt - 1]);
                sleepQuietly(backoffMs[attempt - 1]);
            }
            try {
                return fetchEventsOnce();
            } catch (GdacsClientException e) {
                lastError = e;
                if (!e.isRetryable()) {
                    throw e;
                }
            } catch (ResourceAccessException e) {
                lastError =
                        new GdacsClientException("Lỗi mạng/timeout khi gọi GDACS search", true, e);
            }
        }
        throw lastError;
    }

    private GdacsEventFeed fetchEventsOnce() {
        String rawJson =
                restClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path(searchPath)
                                                .queryParam("eventlist", EVENT_TYPE_TC)
                                                .queryParam("pageSize", 100)
                                                .build())
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::is5xxServerError,
                                (req, res) -> {
                                    throw new GdacsClientException(
                                            "GDACS trả lỗi máy chủ " + res.getStatusCode().value(),
                                            true,
                                            res.getStatusCode().value(),
                                            null);
                                })
                        .onStatus(
                                HttpStatusCode::is4xxClientError,
                                (req, res) -> {
                                    throw new GdacsClientException(
                                            "GDACS từ chối yêu cầu " + res.getStatusCode().value(),
                                            false,
                                            res.getStatusCode().value(),
                                            null);
                                })
                        .body(String.class);

        List<GdacsEvent> events = new ArrayList<>();
        List<String> skipReasons = new ArrayList<>();
        JsonNode root = parseTree(rawJson, "search");
        if (root == null || !root.hasNonNull("features")) {
            return new GdacsEventFeed(events, skipReasons);
        }
        for (JsonNode featureNode : root.get("features")) {
            try {
                GdacsEventCollection.GdacsEventFeature feature =
                        objectMapper.treeToValue(
                                featureNode, GdacsEventCollection.GdacsEventFeature.class);
                // rawJson lấy từ JSON tree gốc — giữ nguyên vẹn mọi trường chưa biết cho
                // raw_metadata
                GdacsEvent event = toEvent(feature, featureNode.toString());
                if (event != null) {
                    events.add(event);
                }
            } catch (IllegalArgumentException e) {
                log.warn("GDACS search: bỏ qua bản ghi lỗi — {}", e.getMessage());
                skipReasons.add(e.getMessage());
            } catch (IOException e) {
                log.warn("GDACS search: bỏ qua bản ghi không parse được — {}", e.getMessage());
                skipReasons.add("feature không parse được: " + e.getMessage());
            }
        }
        return new GdacsEventFeed(events, skipReasons);
    }

    /** Tầng 2: quỹ đạo một sự kiện. 404 (nguồn không phát hành) → danh sách rỗng + WARN. */
    public List<GdacsTrackPoint> fetchStormTrack(GdacsEvent event) {
        GdacsClientException lastError = null;
        for (int attempt = 0; attempt <= backoffMs.length; attempt++) {
            if (attempt > 0) {
                log.debug(
                        "GDACS track retry lần {} cho {} sau {}ms",
                        attempt,
                        event.eventId(),
                        backoffMs[attempt - 1]);
                sleepQuietly(backoffMs[attempt - 1]);
            }
            try {
                return fetchTrackOnce(event);
            } catch (GdacsClientException e) {
                if (!e.isRetryable() && e.getHttpStatus() != null) {
                    // 404: GDACS không phát hành quỹ đạo cho mọi sự kiện — degradation an toàn,
                    // sự kiện vẫn được ghi từ dữ liệu tầng 1 (thiết kế Mục 3.4 Task W2-05)
                    log.warn(
                            "GDACS không có tài nguyên quỹ đạo cho {} (HTTP {}) — bỏ qua mốc quỹ đạo",
                            event.eventId(),
                            e.getHttpStatus());
                    return List.of();
                }
                lastError = e;
                if (!e.isRetryable()) {
                    throw e;
                }
            } catch (ResourceAccessException e) {
                lastError =
                        new GdacsClientException(
                                "Lỗi mạng/timeout khi gọi GDACS track cho " + event.eventId(),
                                true,
                                e);
            }
        }
        throw lastError;
    }

    private List<GdacsTrackPoint> fetchTrackOnce(GdacsEvent event) {
        String rawJson =
                restClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path(trackPath)
                                                .queryParam("eventtype", EVENT_TYPE_TC)
                                                .queryParam("eventid", event.eventId())
                                                .queryParam("episodeid", event.episodeId())
                                                .build())
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::is5xxServerError,
                                (req, res) -> {
                                    throw new GdacsClientException(
                                            "GDACS trả lỗi máy chủ " + res.getStatusCode().value(),
                                            true,
                                            res.getStatusCode().value(),
                                            null);
                                })
                        .onStatus(
                                HttpStatusCode::is4xxClientError,
                                (req, res) -> {
                                    throw new GdacsClientException(
                                            "GDACS từ chối yêu cầu " + res.getStatusCode().value(),
                                            false,
                                            res.getStatusCode().value(),
                                            null);
                                })
                        .body(String.class);

        List<GdacsTrackPoint> points = new ArrayList<>();
        GdacsTrackCollection collection = parseJson(rawJson, GdacsTrackCollection.class, "track");
        if (collection.features() == null) {
            return points;
        }
        points.addAll(toTrackPoints(collection.features(), event));
        log.debug("GDACS track {}: {} mốc quỹ đạo hợp lệ", event.eventId(), points.size());
        return points;
    }

    /** Đoạn quỹ đạo thô: 2 đỉnh [vị trí tại t, vị trí tại t+6h] + cờ forecast + cấp bão. */
    private record TrackSegment(
            String startKey,
            String endKey,
            double startLng,
            double startLat,
            double endLng,
            double endLat,
            boolean forecast,
            String category,
            OffsetDateTime polygonDate) {}

    private static String vertexKey(JsonNode vertex) {
        return vertex.get(0).asDouble() + "," + vertex.get(1).asDouble();
    }

    /**
     * Ghép các đoạn LineString thành quỹ đạo theo thời gian. GDACS đóng dấu CÙNG MỘT {@code
     * polygondate} (giờ chạy mô hình) cho mọi đoạn, nên thời gian từng mốc phải suy từ cấu trúc
     * chuỗi: các đoạn nối tiếp nhau (end của đoạn cũ = start của đoạn mới), bước thời gian =
     * (todate − fromdate) / số đoạn historical (chuẩn 6 giờ của GDACS), đỉnh đầu đoạn cổ nhất trùng
     * fromdate, mốc cuối chuỗi historical trùng todate, chuỗi forecast tiếp nối về phía trước. Chỉ
     * suy thời gian khi khoảng thời gian chia hết chính xác cho số đoạn; ngược lại fallback dùng
     * polygondate đồng nhất (không bịa thời gian mốc).
     */
    private List<GdacsTrackPoint> toTrackPoints(
            List<GdacsTrackCollection.GdacsTrackFeature> features, GdacsEvent event) {
        List<TrackSegment> historical = new ArrayList<>();
        List<TrackSegment> forecast = new ArrayList<>();
        OffsetDateTime fromDate = event.fromDate();
        OffsetDateTime toDate = event.toDate();
        for (GdacsTrackCollection.GdacsTrackFeature feature : features) {
            TrackSegment segment = toSegment(feature, event.category());
            if (segment == null) {
                continue;
            }
            if (fromDate == null) {
                fromDate = parseUtc(feature.properties().fromDate());
            }
            if (toDate == null) {
                toDate = parseUtc(feature.properties().toDate());
            }
            (segment.forecast() ? forecast : historical).add(segment);
        }
        Duration step = null;
        if (fromDate != null && toDate != null && !historical.isEmpty()) {
            Duration span = Duration.between(fromDate, toDate);
            step = span.dividedBy(historical.size());
            if (step.isZero()
                    || step.isNegative()
                    || !step.multipliedBy(historical.size()).equals(span)) {
                step = null; // không chia hết → cấu trúc chuỗi bất thường, đừng suy thời gian
            }
        }
        if (step == null) {
            return fallbackTrackPoints(historical, forecast);
        }
        List<TrackSegment> histChain = chainBackward(historical);
        List<TrackSegment> fcChain = chainForward(forecast);
        List<GdacsTrackPoint> points = new ArrayList<>();
        if (!histChain.isEmpty()) {
            TrackSegment oldest = histChain.get(0);
            points.add(toPoint(oldest, false, oldest.startLng(), oldest.startLat(), fromDate));
            for (int i = 0; i < histChain.size(); i++) {
                TrackSegment seg = histChain.get(i);
                points.add(
                        toPoint(
                                seg,
                                false,
                                seg.endLng(),
                                seg.endLat(),
                                fromDate.plus(step.multipliedBy(i + 1))));
            }
        }
        for (int i = 0; i < fcChain.size(); i++) {
            TrackSegment seg = fcChain.get(i);
            points.add(
                    toPoint(
                            seg,
                            true,
                            seg.endLng(),
                            seg.endLat(),
                            toDate.plus(step.multipliedBy(i + 1))));
        }
        return points;
    }

    /**
     * Nối chuỗi historical từ mốc mới nhất (đoạn có end không là start của đoạn historical nào) lùi
     * dần về quá khứ qua khớp end-cũ = start-mới. Trả chuỗi cổ nhất → mới nhất.
     */
    private List<TrackSegment> chainBackward(List<TrackSegment> segments) {
        Map<String, TrackSegment> byStart = new HashMap<>();
        Map<String, TrackSegment> byEnd = new HashMap<>();
        for (TrackSegment seg : segments) {
            byStart.putIfAbsent(seg.startKey(), seg);
            byEnd.putIfAbsent(seg.endKey(), seg);
        }
        TrackSegment tip = null;
        for (TrackSegment seg : segments) {
            if (!byStart.containsKey(seg.endKey())) {
                tip = seg;
            }
        }
        if (tip == null) {
            return List.of(); // vòng kín bất thường — không suy được hướng thời gian
        }
        List<TrackSegment> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        TrackSegment cur = tip;
        while (cur != null && visited.add(cur.endKey())) {
            chain.add(0, cur);
            cur = byEnd.get(cur.startKey());
        }
        return chain;
    }

    /**
     * Nối chuỗi forecast từ đoạn không có tiền thân (start không là end của đoạn forecast nào) tiến
     * về tương lai. Trả chuỗi cổ nhất → mới nhất.
     */
    private List<TrackSegment> chainForward(List<TrackSegment> segments) {
        Map<String, TrackSegment> byStart = new HashMap<>();
        Set<String> endKeys = new HashSet<>();
        for (TrackSegment seg : segments) {
            byStart.putIfAbsent(seg.startKey(), seg);
            endKeys.add(seg.endKey());
        }
        TrackSegment tip = null;
        for (TrackSegment seg : segments) {
            if (!endKeys.contains(seg.startKey())) {
                tip = seg;
            }
        }
        if (tip == null) {
            return List.of();
        }
        List<TrackSegment> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        TrackSegment cur = tip;
        while (cur != null && visited.add(cur.endKey())) {
            chain.add(cur);
            cur = byStart.get(cur.endKey());
        }
        return chain;
    }

    private List<GdacsTrackPoint> fallbackTrackPoints(
            List<TrackSegment> historical, List<TrackSegment> forecast) {
        List<GdacsTrackPoint> points = new ArrayList<>();
        for (TrackSegment seg : historical) {
            addFallbackPoint(points, seg);
        }
        for (TrackSegment seg : forecast) {
            addFallbackPoint(points, seg);
        }
        return points;
    }

    private void addFallbackPoint(List<GdacsTrackPoint> points, TrackSegment seg) {
        if (seg.polygonDate() == null) {
            log.warn("GDACS track: bỏ qua mốc không parse được polygondate");
            return;
        }
        points.add(toPoint(seg, seg.forecast(), seg.endLng(), seg.endLat(), seg.polygonDate()));
    }

    private GdacsTrackPoint toPoint(
            TrackSegment segment, boolean forecast, double lng, double lat, OffsetDateTime atTime) {
        return new GdacsTrackPoint(
                forecast ? "forecast" : "historical",
                atTime,
                lng,
                lat,
                null, // gió theo mốc: GDACS không công bố (V6)
                null,
                null,
                segment.category(),
                100.0); // impact_radius_km: giữ DEFAULT 100 của V4 — nguồn không có bán kính đơn
        // giản
    }

    private TrackSegment toSegment(
            GdacsTrackCollection.GdacsTrackFeature feature, String fallbackCategory) {
        JsonNode coords = feature.geometry() == null ? null : feature.geometry().coordinates();
        if (feature.geometry() == null
                || !LINE_STRING.equals(feature.geometry().type())
                || coords == null
                || !coords.isArray()
                || coords.size() < 2
                || feature.properties() == null) {
            return null;
        }
        JsonNode start = coords.get(0);
        JsonNode end = coords.get(coords.size() - 1);
        if (start == null
                || !start.isArray()
                || start.size() < 2
                || !start.get(0).isNumber()
                || !start.get(1).isNumber()
                || end == null
                || !end.isArray()
                || end.size() < 2
                || !end.get(0).isNumber()
                || !end.get(1).isNumber()) {
            return null;
        }
        String category = feature.properties().polygonLabel();
        if (category == null || category.isBlank()) {
            category = fallbackCategory; // quyết định #14: fallback cấp của sự kiện
        }
        return new TrackSegment(
                vertexKey(start),
                vertexKey(end),
                start.get(0).asDouble(),
                start.get(1).asDouble(),
                end.get(0).asDouble(),
                end.get(1).asDouble(),
                Boolean.TRUE.equals(feature.properties().forecast()),
                category,
                parseUtc(feature.properties().polygonDate()));
    }

    /**
     * Ánh xạ một feature search → GdacsEvent. Trả null khi sự kiện đã kết thúc (iscurrent=false)
     * hoặc thiếu trường bắt buộc: eventId, name, tọa độ, gió, cấp cảnh báo, thời điểm bắt đầu.
     * KHÔNG loại vì thiếu áp suất — nguồn hiện hành không công bố, cột đã nới lỏng theo V6.
     */
    private GdacsEvent toEvent(
            GdacsEventCollection.GdacsEventFeature feature, String rawFeatureJson) {
        GdacsEventCollection.GdacsEventProperties props = feature.properties();
        if (props == null) {
            throw new IllegalArgumentException("feature thiếu properties");
        }
        if (!"true".equalsIgnoreCase(props.isCurrent())) {
            return null; // sự kiện đã kết thúc nằm trong cửa sổ lịch sử của Search API — không phải
            // active
        }
        if (props.eventId() == null) {
            throw new IllegalArgumentException("thiếu eventid");
        }
        if (props.name() == null || props.name().isBlank()) {
            throw new IllegalArgumentException("event " + props.eventId() + " thiếu name");
        }
        if (props.alertLevel() == null || props.alertLevel().isBlank()) {
            throw new IllegalArgumentException("event " + props.eventId() + " thiếu alertlevel");
        }
        if (props.severityData() == null || props.severityData().severity() == null) {
            throw new IllegalArgumentException("event " + props.eventId() + " thiếu dữ liệu gió");
        }
        List<Double> coords = feature.geometry() == null ? null : feature.geometry().coordinates();
        if (coords == null || coords.size() < 2 || coords.get(0) == null || coords.get(1) == null) {
            throw new IllegalArgumentException(
                    "event " + props.eventId() + " thiếu tọa độ tâm bão");
        }
        OffsetDateTime fromDate = parseUtc(props.fromDate());
        if (fromDate == null) {
            throw new IllegalArgumentException(
                    "event " + props.eventId() + " không parse được fromdate: " + props.fromDate());
        }

        double windMs = toMs(props.severityData().severity(), props.severityData().severityUnit());
        return new GdacsEvent(
                String.valueOf(props.eventId()),
                props.episodeId() == null ? null : String.valueOf(props.episodeId()),
                props.name(),
                fromDate,
                parseUtc(props.toDate()),
                props.alertLevel(),
                windMs,
                null, // áp suất: GDACS không công bố (V6)
                coords.get(0),
                coords.get(1),
                rawFeatureJson);
    }

    private <T> T parseJson(String rawJson, Class<T> type, String tier) {
        try {
            return objectMapper.readValue(rawJson, type);
        } catch (IOException e) {
            throw new GdacsClientException(
                    "Payload GDACS " + tier + " không parse được JSON", true, e);
        }
    }

    private JsonNode parseTree(String rawJson, String tier) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (IOException e) {
            throw new GdacsClientException(
                    "Payload GDACS " + tier + " không parse được JSON", true, e);
        }
    }

    /**
     * ISO-8601 không offset (giờ UTC của GDACS) → OffsetDateTime UTC; null khi không parse được.
     */
    private static OffsetDateTime parseUtc(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GdacsClientException("Tiến trình bị gián đoạn khi backoff", false, e);
        }
    }
}
