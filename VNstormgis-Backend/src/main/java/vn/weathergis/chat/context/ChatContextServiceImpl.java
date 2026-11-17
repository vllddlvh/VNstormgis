package vn.weathergis.chat.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.weathergis.alert.domain.AlertEntity;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.repository.AlertRepository;
import vn.weathergis.chat.config.ChatContextProperties;
import vn.weathergis.chat.exception.ChatContextException;
import vn.weathergis.chat.intent.ChatIntent;
import vn.weathergis.chat.intent.IntentClassification;
import vn.weathergis.chat.intent.IntentClassifier;
import vn.weathergis.chat.repository.ChatContextQueryRepository;
import vn.weathergis.chat.repository.StationObservationRow;
import vn.weathergis.chat.repository.StormSummaryRow;

/**
 * Intent → bounded PostGIS retrieval → typed {@link FactualContext} (W5-02). Missing data is an
 * honest outcome; real DB failures propagate. INFO logs carry intent/outcome/size only — never the
 * question text, tokens or prompts (contract §5).
 */
@Service
public class ChatContextServiceImpl implements ChatContextService {

    private static final Logger log = LoggerFactory.getLogger(ChatContextServiceImpl.class);
    private static final int MAX_RANKED_STATIONS = 5;
    private static final int MAX_ACTIVE_STORMS = 5;
    private static final String DB_UPDATE_KIND = "db_update";
    private static final String STORM_SOURCE = "GDACS";

    private static final Map<String, String> STATION_UNITS = stationUnits();
    private static final Map<String, String> STORM_UNITS = stormUnits();
    private static final Map<String, Integer> SEVERITY_RANK =
            Map.of("CRITICAL", 0, "DANGER", 1, "WARNING", 2, "INFO", 3);

    private final IntentClassifier classifier;
    private final ChatContextQueryRepository queryRepository;
    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;
    private final FactualContextSerializer serializer;
    private final ChatContextShaper shaper;
    private final ChatContextProperties properties;
    private final Clock clock;

    public ChatContextServiceImpl(
            IntentClassifier classifier,
            ChatContextQueryRepository queryRepository,
            AlertRepository alertRepository,
            ObjectMapper objectMapper,
            FactualContextSerializer serializer,
            ChatContextShaper shaper,
            ChatContextProperties properties,
            Clock clock) {
        this.classifier = classifier;
        this.queryRepository = queryRepository;
        this.alertRepository = alertRepository;
        this.objectMapper = objectMapper;
        this.serializer = serializer;
        this.shaper = shaper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public ChatContextResult retrieve(ChatContextRequest request) {
        return retrieve(
                request, classifier.classify(request.message(), request.referencedAlertId()));
    }

    @Override
    public ChatContextResult retrieve(
            ChatContextRequest request, IntentClassification classification) {
        long startNanos = System.nanoTime();
        Instant now = clock.instant();
        ChatContextResult result =
                switch (classification.intent()) {
                    case OUT_OF_SCOPE ->
                            emptyResult(
                                    classification.intent(),
                                    ChatContextOutcome.OUT_OF_SCOPE,
                                    null,
                                    now);
                    case ALERT_EXPLAIN -> alertExplain(classification.referencedAlertId(), now);
                    case RAIN_TOP, WIND_TOP ->
                            topStations(classification.intent(), request.bbox(), now);
                    case STATION_CURRENT -> stationCurrent(classification, now);
                    case STORM_ACTIVE -> stormActive(now);
                };
        FactualContext context = result.context();
        log.info(
                "chat context intent={} outcome={} durationMs={} sizeBytes={} stations={} storms={} alert={}",
                classification.intent().wireName(),
                result.outcome(),
                Duration.ofNanos(System.nanoTime() - startNanos).toMillis(),
                context.sizeBytes(),
                context.stations().size(),
                context.storms().size(),
                context.alert() != null);
        return result;
    }

    private ChatContextResult topStations(
            ChatIntent intent, ChatContextRequest.Bbox bbox, Instant now) {
        List<StationObservationRow> rows = queryRepository.findLatestPerActiveStation(bbox);
        boolean rain = intent == ChatIntent.RAIN_TOP;
        List<StationObservationRow> ranked =
                rows.stream()
                        .filter(
                                row ->
                                        row.hasWeather()
                                                && finite(rain ? row.rain1h() : row.windSpeed()))
                        .sorted(
                                Comparator.comparingDouble(
                                                (StationObservationRow row) ->
                                                        rain ? row.rain1h() : row.windSpeed())
                                        .reversed()
                                        .thenComparing(StationObservationRow::code))
                        .limit(MAX_RANKED_STATIONS)
                        .toList();
        if (ranked.isEmpty()) {
            NoDataReason reason =
                    bbox != null && rows.isEmpty()
                            ? NoDataReason.EMPTY_BBOX
                            : NoDataReason.MISSING_RECORDS;
            return emptyResult(intent, ChatContextOutcome.NO_DATA, reason, now);
        }
        List<FactualContext.StationObservation> stations =
                ranked.stream().map(row -> toStation(row, now)).toList();
        return ready(intent, stations, List.of(), null, now);
    }

    private ChatContextResult stationCurrent(IntentClassification classification, Instant now) {
        if (classification.stationId() == null) {
            return emptyResult(
                    ChatIntent.STATION_CURRENT,
                    ChatContextOutcome.NO_DATA,
                    NoDataReason.UNKNOWN_STATION,
                    now);
        }
        StationObservationRow row =
                queryRepository.findLatestForStation(classification.stationId());
        if (row == null || !row.hasWeather()) {
            return emptyResult(
                    ChatIntent.STATION_CURRENT,
                    ChatContextOutcome.NO_DATA,
                    NoDataReason.MISSING_RECORDS,
                    now);
        }
        return ready(
                ChatIntent.STATION_CURRENT, List.of(toStation(row, now)), List.of(), null, now);
    }

    private ChatContextResult stormActive(Instant now) {
        List<StormSummaryRow> rows =
                queryRepository.findActiveStormSummaries(now, MAX_ACTIVE_STORMS);
        if (rows.isEmpty()) {
            return emptyResult(
                    ChatIntent.STORM_ACTIVE,
                    ChatContextOutcome.NO_DATA,
                    NoDataReason.NO_ACTIVE_STORM,
                    now);
        }
        List<FactualContext.StormSummary> storms =
                rows.stream().map(row -> toStorm(row, now)).toList();
        return ready(ChatIntent.STORM_ACTIVE, List.of(), storms, null, now);
    }

    private ChatContextResult alertExplain(UUID alertId, Instant now) {
        AlertEntity entity = alertRepository.findById(alertId).orElse(null);
        if (entity == null) {
            return emptyResult(
                    ChatIntent.ALERT_EXPLAIN, ChatContextOutcome.ALERT_NOT_FOUND, null, now);
        }
        String rawSnapshot = entity.getFactualSnapshot();
        if (rawSnapshot == null || rawSnapshot.isBlank()) {
            FactualContext.AlertContext alert =
                    new FactualContext.AlertContext(
                            entity.getId(),
                            entity.getStatus(),
                            entity.getSeverityLevel(),
                            toInstant(entity.getTriggeredAt()),
                            toInstant(entity.getResolvedAt()),
                            false,
                            null,
                            List.of(),
                            false,
                            0);
            return ready(
                    ChatIntent.ALERT_EXPLAIN,
                    List.of(),
                    List.of(),
                    alert,
                    now,
                    ChatContextOutcome.NO_SNAPSHOT,
                    null);
        }
        AlertFactualSnapshot snapshot = parseSnapshot(rawSnapshot);
        long snapshotBytes = serializer.measureBytes(snapshot);
        boolean oversize = snapshotBytes > properties.getMaxContextBytes();
        FactualContext.AlertContext alert =
                new FactualContext.AlertContext(
                        entity.getId(),
                        entity.getStatus(),
                        entity.getSeverityLevel(),
                        toInstant(entity.getTriggeredAt()),
                        toInstant(entity.getResolvedAt()),
                        true,
                        snapshot,
                        violationOrder(snapshot.violations()),
                        oversize,
                        snapshotBytes);
        return ready(ChatIntent.ALERT_EXPLAIN, List.of(), List.of(), alert, now);
    }

    private AlertFactualSnapshot parseSnapshot(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, AlertFactualSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new ChatContextException("Không thể đọc factual_snapshot của alert", e);
        }
    }

    private List<FactualContext.ViolationOrder> violationOrder(List<ViolationSnapshot> violations) {
        return violations.stream()
                .sorted(
                        Comparator.comparingInt(
                                        (ViolationSnapshot v) ->
                                                SEVERITY_RANK.getOrDefault(
                                                        v.severityLevel(), Integer.MAX_VALUE))
                                .thenComparing(
                                        ViolationSnapshot::sourceTimestamp,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(ViolationSnapshot::violationKey))
                .map(
                        v ->
                                new FactualContext.ViolationOrder(
                                        v.violationKey(),
                                        v.severityLevel(),
                                        v.measuredValue() - v.thresholdValue()))
                .toList();
    }

    private FactualContext.StationObservation toStation(StationObservationRow row, Instant now) {
        return new FactualContext.StationObservation(
                row.code(),
                row.name(),
                coordinates(row.longitude(), row.latitude()),
                row.recordedAt(),
                isStale(row.recordedAt(), now),
                row.temperature(),
                row.humidity(),
                row.pressure(),
                row.windSpeed(),
                row.windGust(),
                row.windDirection(),
                row.rain1h(),
                row.rain3h(),
                row.weatherCondition(),
                row.weatherDescription(),
                STATION_UNITS);
    }

    private FactualContext.StormSummary toStorm(StormSummaryRow row, Instant now) {
        FactualContext.ForecastPoint forecast =
                row.hasForecast()
                        ? new FactualContext.ForecastPoint(
                                row.forecastTime(),
                                row.trackType(),
                                coordinates(row.forecastLongitude(), row.forecastLatitude()),
                                row.forecastWindSpeed(),
                                row.forecastWindGust(),
                                row.forecastPressure(),
                                row.forecastCategory())
                        : null;
        return new FactualContext.StormSummary(
                row.stormId(),
                row.name(),
                row.category(),
                row.maxWindSpeed(),
                row.minPressure(),
                coordinates(row.longitude(), row.latitude()),
                row.updatedAt(),
                DB_UPDATE_KIND,
                isStale(row.updatedAt(), now),
                STORM_SOURCE,
                forecast,
                STORM_UNITS);
    }

    private boolean isStale(Instant timestamp, Instant now) {
        return timestamp != null
                && Duration.between(timestamp, now).compareTo(properties.getStaleThreshold()) > 0;
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static List<Double> coordinates(Double longitude, Double latitude) {
        return longitude == null || latitude == null ? null : List.of(longitude, latitude);
    }

    private static Instant toInstant(java.time.OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private ChatContextResult emptyResult(
            ChatIntent intent, ChatContextOutcome outcome, NoDataReason reason, Instant now) {
        return ready(intent, List.of(), List.of(), null, now, outcome, reason);
    }

    private ChatContextResult ready(
            ChatIntent intent,
            List<FactualContext.StationObservation> stations,
            List<FactualContext.StormSummary> storms,
            FactualContext.AlertContext alert,
            Instant now) {
        return ready(intent, stations, storms, alert, now, ChatContextOutcome.CONTEXT_READY, null);
    }

    private ChatContextResult ready(
            ChatIntent intent,
            List<FactualContext.StationObservation> stations,
            List<FactualContext.StormSummary> storms,
            FactualContext.AlertContext alert,
            Instant now,
            ChatContextOutcome outcome,
            NoDataReason reason) {
        FactualContext draft =
                new FactualContext(
                        FactualContext.SCHEMA_VERSION,
                        intent,
                        now,
                        outcome,
                        reason,
                        stations,
                        storms,
                        alert,
                        new FactualContext.ReferencedEntities(List.of(), List.of(), List.of()),
                        new FactualContext.ContextMeta(
                                properties.getStaleThreshold().toMinutes(), 0, 0, false),
                        0);
        FactualContext shaped = shaper.shape(draft);
        FactualContext sized = shaped.withSizeBytes(serializer.payloadBytes(shaped));
        return new ChatContextResult(outcome, reason, sized);
    }

    private static Map<String, String> stationUnits() {
        Map<String, String> units = new LinkedHashMap<>();
        units.put("temperature", "°C");
        units.put("humidity", "%");
        units.put("pressure", "hPa");
        units.put("wind_speed", "m/s");
        units.put("wind_gust", "m/s");
        units.put("wind_direction", "deg");
        units.put("rain_1h", "mm");
        units.put("rain_3h", "mm");
        return Map.copyOf(units);
    }

    private static Map<String, String> stormUnits() {
        Map<String, String> units = new LinkedHashMap<>();
        units.put("max_wind_speed", "m/s");
        units.put("min_pressure", "hPa");
        units.put("wind_speed", "m/s");
        units.put("wind_gust", "m/s");
        units.put("pressure", "hPa");
        return Map.copyOf(units);
    }
}
