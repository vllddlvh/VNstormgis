package vn.weathergis.chat.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.chat.config.ChatReplyProperties;
import vn.weathergis.chat.context.ChatContextOutcome;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.context.NoDataReason;
import vn.weathergis.chat.intent.ChatIntent;

/**
 * Deterministic Vietnamese reply engine (W5-04 §6): the SAME renderer produces verified AI replies
 * (from guardrail-approved selections) and fallbacks (from the full context). Text is always built
 * from context values — never from model prose or model number copies. Plain text, no markdown;
 * length is bounded by structure (FULL → COMPACT → minimal), never by cutting mid-sentence. Broken
 * input invariants throw {@link IllegalStateException} so the generator maps them to the narrow
 * Unavailable branch instead of rendering guesses.
 */
@Component
public class ChatReplyRenderer {

    public record RenderedReply(String text, ChatReplyGuardrail.Variant variant, boolean minimal) {}

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm 'UTC' 'ngày' dd/MM/yyyy").withZone(ZoneOffset.UTC);
    private static final String STALE_LABEL = " (số liệu cũ)";
    private static final Set<String> OPERATORS = Set.of(">=", ">", "<=", "<", "=");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "DANGER", "CRITICAL");
    private static final Map<String, String> VIOLATION_LABELS =
            Map.of(
                    "rain_1h", "lượng mưa 1 giờ",
                    "rain_3h", "lượng mưa 3 giờ",
                    "wind_speed", "tốc độ gió",
                    "wind_gust", "tốc độ gió giật",
                    "storm_dist", "khoảng cách đến bão");
    private static final Map<String, String> FIELD_LABELS = fieldLabels();
    private static final List<String> FIELD_ORDER =
            List.of(
                    "temperature",
                    "humidity",
                    "pressure",
                    "wind_speed",
                    "wind_gust",
                    "wind_direction",
                    "rain_1h",
                    "rain_3h",
                    "weather_condition",
                    "weather_description");

    private final ChatReplyProperties properties;

    public ChatReplyRenderer(ChatReplyProperties properties) {
        this.properties = properties;
    }

    public String renderHonest(ChatContextOutcome outcome, NoDataReason reason) {
        if (outcome == ChatContextOutcome.OUT_OF_SCOPE) {
            return "Xin lỗi, tôi chỉ có thể trả lời về lượng mưa, gió, bão đang hoạt động và cảnh"
                    + " báo thời tiết trong hệ thống. Bạn hãy thử hỏi về mưa, gió hoặc bão hiện"
                    + " nay.";
        }
        if (outcome == ChatContextOutcome.NO_SNAPSHOT) {
            return "Cảnh báo này không còn dữ kiện gốc để giải thích. Hệ thống không tái dựng nội"
                    + " dung từ ngưỡng hiện tại.";
        }
        if (outcome == ChatContextOutcome.NO_DATA) {
            return switch (reason) {
                case UNKNOWN_STATION ->
                        "Hệ thống chưa ghi nhận trạm bạn hỏi. Bạn có thể hỏi về lượng mưa, gió hoặc"
                                + " bão đang hoạt động.";
                case EMPTY_BBOX ->
                        "Không có dữ liệu quan trắc trong vùng bản đồ bạn đã chọn. Hãy thử vùng khác"
                                + " hoặc hỏi về mưa, gió, bão hiện nay.";
                case NO_ACTIVE_STORM ->
                        "Hiện không có bão đang hoạt động trong hệ thống. Bạn có thể hỏi về lượng"
                                + " mưa hoặc gió.";
                case MISSING_RECORDS ->
                        "Hệ thống chưa ghi nhận số liệu này tại thời điểm bạn hỏi. Bạn có thể hỏi về"
                                + " lượng mưa, gió hoặc bão đang hoạt động.";
            };
        }
        throw new IllegalStateException("No honest template for outcome " + outcome);
    }

    /** FULL → COMPACT → minimal; the returned text never exceeds the configured reply cap. */
    public RenderedReply renderBestEffort(FactualContext context, int remainingViolations) {
        int cap = properties.getReplyMaxChars();
        String full =
                renderData(
                        context, null, remainingViolations, ChatReplyGuardrail.Variant.FULL, true);
        if (full.length() <= cap) {
            return new RenderedReply(full, ChatReplyGuardrail.Variant.FULL, false);
        }
        String compact =
                renderData(
                        context,
                        null,
                        remainingViolations,
                        ChatReplyGuardrail.Variant.COMPACT,
                        true);
        if (compact.length() <= cap) {
            return new RenderedReply(compact, ChatReplyGuardrail.Variant.COMPACT, false);
        }
        return new RenderedReply(
                minimal(context, remainingViolations), ChatReplyGuardrail.Variant.COMPACT, true);
    }

    /** Renders only guardrail-approved selections; values still come from the context. */
    public String renderVerified(
            FactualContext context,
            ChatReplyGuardrail.Selection selection,
            int remainingViolations) {
        int cap = properties.getReplyMaxChars();
        String text =
                renderData(context, selection, remainingViolations, selection.variant(), false);
        if (text.length() <= cap || selection.variant() == ChatReplyGuardrail.Variant.COMPACT) {
            return text;
        }
        return renderData(
                context, selection, remainingViolations, ChatReplyGuardrail.Variant.COMPACT, false);
    }

    private String renderData(
            FactualContext context,
            ChatReplyGuardrail.Selection selection,
            int remainingViolations,
            ChatReplyGuardrail.Variant variant,
            boolean fallback) {
        return switch (context.intent()) {
            case RAIN_TOP -> renderTop(context, selection, variant, fallback, true);
            case WIND_TOP -> renderTop(context, selection, variant, fallback, false);
            case STATION_CURRENT -> renderStation(context, selection, variant);
            case STORM_ACTIVE -> renderStorms(context, selection, variant);
            case ALERT_EXPLAIN -> renderAlert(context, selection, remainingViolations, variant);
            case OUT_OF_SCOPE -> throw new IllegalStateException("Out-of-scope has no data reply");
        };
    }

    private String renderTop(
            FactualContext context,
            ChatReplyGuardrail.Selection selection,
            ChatReplyGuardrail.Variant variant,
            boolean fallback,
            boolean rain) {
        List<FactualContext.StationObservation> stations = context.stations();
        if (stations.isEmpty()) {
            throw new IllegalStateException("Context ready without stations");
        }
        List<FactualContext.StationObservation> ordered = selectStations(stations, selection);
        String field = rain ? "rain_1h" : "wind_speed";
        String subject = rain ? "lượng mưa 1 giờ" : "tốc độ gió";
        StringBuilder out = new StringBuilder();
        if (variant == ChatReplyGuardrail.Variant.FULL) {
            for (int i = 0; i < ordered.size(); i++) {
                FactualContext.StationObservation station = ordered.get(i);
                double value = requireFinite(rain ? station.rain1h() : station.windSpeed());
                String unit = unit(station.units(), field);
                String time = time(station.recordedAt());
                String stale = station.stale() ? STALE_LABEL : "";
                if (i > 0) {
                    out.append(' ');
                }
                if (i == 0 && fallback && ordered.size() == stations.size()) {
                    out.append("Trạm ")
                            .append(station.name())
                            .append(" (")
                            .append(station.code())
                            .append(") đang có ")
                            .append(subject)
                            .append(rain ? " cao nhất: " : " mạnh nhất: ")
                            .append(number(value))
                            .append(' ')
                            .append(unit)
                            .append(", ghi nhận lúc ")
                            .append(time)
                            .append(stale)
                            .append('.');
                } else {
                    out.append("Trạm ")
                            .append(station.name())
                            .append(" (")
                            .append(station.code())
                            .append("): ")
                            .append(number(value))
                            .append(' ')
                            .append(unit)
                            .append(" lúc ")
                            .append(time)
                            .append(stale)
                            .append('.');
                }
            }
            if (!fallback || ordered.size() != stations.size()) {
                out.insert(0, (rain ? "Lượng mưa 1 giờ ghi nhận: " : "Tốc độ gió ghi nhận: "));
            }
        } else {
            out.append(rain ? "Mưa 1 giờ: " : "Gió: ");
            for (int i = 0; i < ordered.size(); i++) {
                FactualContext.StationObservation station = ordered.get(i);
                double value = requireFinite(rain ? station.rain1h() : station.windSpeed());
                if (i > 0) {
                    out.append("; ");
                }
                out.append(station.code())
                        .append(' ')
                        .append(number(value))
                        .append(' ')
                        .append(unit(station.units(), field))
                        .append(" lúc ")
                        .append(time(station.recordedAt()))
                        .append(station.stale() ? STALE_LABEL : "");
            }
            out.append('.');
        }
        return out.toString();
    }

    private String renderStation(
            FactualContext context,
            ChatReplyGuardrail.Selection selection,
            ChatReplyGuardrail.Variant variant) {
        if (context.stations().size() != 1) {
            throw new IllegalStateException(
                    "Station current context must hold exactly one station");
        }
        FactualContext.StationObservation station = context.stations().get(0);
        List<String> fields =
                selection == null
                        ? FIELD_ORDER.stream().filter(f -> hasValue(station, f)).toList()
                        : FIELD_ORDER.stream()
                                .filter(selection.fields()::contains)
                                .filter(f -> hasValue(station, f))
                                .toList();
        if (fields.isEmpty()) {
            throw new IllegalStateException("Station has no renderable observation field");
        }
        StringBuilder out = new StringBuilder();
        if (variant == ChatReplyGuardrail.Variant.FULL) {
            out.append("Trạm ")
                    .append(station.name())
                    .append(" (")
                    .append(station.code())
                    .append(") lúc ")
                    .append(time(station.recordedAt()))
                    .append(station.stale() ? STALE_LABEL : "")
                    .append(": ");
        } else {
            out.append("Trạm ")
                    .append(station.code())
                    .append(" lúc ")
                    .append(time(station.recordedAt()))
                    .append(station.stale() ? STALE_LABEL : "")
                    .append(": ");
        }
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append("; ");
            }
            String field = fields.get(i);
            out.append(FIELD_LABELS.get(field)).append(' ').append(fieldValue(station, field));
        }
        return out.append('.').toString();
    }

    private String renderStorms(
            FactualContext context,
            ChatReplyGuardrail.Selection selection,
            ChatReplyGuardrail.Variant variant) {
        List<FactualContext.StormSummary> storms = context.storms();
        if (storms.isEmpty()) {
            throw new IllegalStateException("Context ready without active storms");
        }
        List<FactualContext.StormSummary> ordered = selectStorms(storms, selection);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(renderStorm(ordered.get(i), variant));
        }
        return out.toString();
    }

    private String renderStorm(
            FactualContext.StormSummary storm, ChatReplyGuardrail.Variant variant) {
        if (storm.stormId() == null || storm.stormId().isBlank() || storm.name() == null) {
            throw new IllegalStateException("Storm identity missing");
        }
        StringBuilder out = new StringBuilder();
        if (variant == ChatReplyGuardrail.Variant.FULL) {
            out.append("Bão ")
                    .append(storm.name())
                    .append(" (mã GDACS ")
                    .append(storm.stormId())
                    .append(")");
            if (storm.category() != null && !storm.category().isBlank()) {
                out.append(", ").append(storm.category());
            }
            out.append(": ");
            List<String> parts = new ArrayList<>();
            if (finite(storm.maxWindSpeed())) {
                parts.add(
                        "gió cực đại "
                                + number(storm.maxWindSpeed())
                                + " "
                                + unit(storm.units(), "max_wind_speed"));
            }
            if (finite(storm.minPressure())) {
                parts.add(
                        "áp suất tối thiểu "
                                + number(storm.minPressure())
                                + " "
                                + unit(storm.units(), "min_pressure"));
            }
            if (storm.center() != null && storm.center().size() == 2) {
                parts.add("tâm " + coordinates(storm.center()));
            }
            if (storm.updatedAt() != null) {
                String kind =
                        "db_update".equals(storm.updatedAtKind())
                                ? " (nguồn GDACS; mốc cập nhật là thời điểm ghi nhận trong cơ sở dữ liệu)"
                                : " (nguồn GDACS)";
                parts.add("cập nhật lúc " + time(storm.updatedAt()) + kind);
            }
            out.append(String.join(", ", parts));
            if (storm.stale()) {
                out.append(STALE_LABEL);
            }
            out.append('.');
            FactualContext.ForecastPoint forecast = storm.nearestForecast();
            if (forecast != null && forecast.forecastTime() != null) {
                out.append(" Dự báo gần nhất lúc ").append(time(forecast.forecastTime()));
                if (finite(forecast.windSpeed())) {
                    out.append(": gió ")
                            .append(number(forecast.windSpeed()))
                            .append(' ')
                            .append(unit(storm.units(), "wind_speed"));
                }
                if (forecast.coordinates() != null && forecast.coordinates().size() == 2) {
                    out.append(", tâm ").append(coordinates(forecast.coordinates()));
                }
                out.append(" (dữ liệu dự báo, không phải số thực đo).");
            }
        } else {
            out.append("Bão ")
                    .append(storm.name())
                    .append(" (")
                    .append(storm.stormId())
                    .append("): ");
            if (finite(storm.maxWindSpeed())) {
                out.append("gió ")
                        .append(number(storm.maxWindSpeed()))
                        .append(' ')
                        .append(unit(storm.units(), "max_wind_speed"))
                        .append(", ");
            }
            if (storm.center() != null && storm.center().size() == 2) {
                out.append("tâm ").append(coordinates(storm.center())).append(", ");
            }
            out.append("cập nhật ")
                    .append(time(storm.updatedAt()))
                    .append(storm.stale() ? STALE_LABEL : "")
                    .append(" (GDACS).");
        }
        return out.toString();
    }

    private String renderAlert(
            FactualContext context,
            ChatReplyGuardrail.Selection selection,
            int remainingViolations,
            ChatReplyGuardrail.Variant variant) {
        FactualContext.AlertContext alert = context.alert();
        if (alert == null || !alert.snapshotPresent() || alert.factualSnapshot() == null) {
            throw new IllegalStateException("Alert context ready without immutable snapshot");
        }
        AlertFactualSnapshot snapshot = alert.factualSnapshot();
        if (snapshot.violations().isEmpty()) {
            throw new IllegalStateException("Alert snapshot without violations");
        }
        List<ViolationSnapshot> ordered = selectViolations(alert, selection);
        String station = stationName(snapshot);
        String issued =
                time(alert.triggeredAt() != null ? alert.triggeredAt() : snapshot.evaluatedAt());
        StringBuilder out = new StringBuilder();
        if (variant == ChatReplyGuardrail.Variant.FULL) {
            out.append("Cảnh báo ")
                    .append(severity(alert.severityLevel()))
                    .append(" tại trạm ")
                    .append(station)
                    .append(" phát lúc ")
                    .append(issued)
                    .append(": ");
            for (int i = 0; i < ordered.size(); i++) {
                if (i > 0) {
                    out.append("; ");
                }
                out.append(violationFull(ordered.get(i)));
            }
            out.append('.');
        } else {
            out.append("Cảnh báo ")
                    .append(severity(alert.severityLevel()))
                    .append(" trạm ")
                    .append(snapshot.locationCode())
                    .append(" lúc ")
                    .append(issued)
                    .append(": ");
            for (int i = 0; i < ordered.size(); i++) {
                if (i > 0) {
                    out.append("; ");
                }
                ViolationSnapshot v = ordered.get(i);
                validateViolation(v);
                out.append(v.parameter())
                        .append(' ')
                        .append(number(v.measuredValue()))
                        .append(' ')
                        .append(v.operator())
                        .append(' ')
                        .append(number(v.thresholdValue()))
                        .append(' ')
                        .append(v.unit())
                        .append(" (chênh ")
                        .append(exceedance(v))
                        .append(", mức ")
                        .append(severity(v.severityLevel()))
                        .append(')');
            }
        }
        if (remainingViolations > 0) {
            out.append(" Và ")
                    .append(remainingViolations)
                    .append(" vi phạm khác (không liệt kê chi tiết).");
        }
        out.append(" Trạng thái: ").append(alert.status());
        if (alert.resolvedAt() != null) {
            out.append(" (kết thúc lúc ").append(time(alert.resolvedAt())).append(')');
        }
        return out.append('.').toString();
    }

    private String violationFull(ViolationSnapshot v) {
        validateViolation(v);
        String label = VIOLATION_LABELS.get(v.parameter());
        if ("storm_dist".equals(v.parameter()) && v.stormName() != null && v.stormId() != null) {
            label = label + " " + v.stormName() + " (" + v.stormId() + ")";
        }
        String relation =
                switch (v.operator()) {
                    case ">", ">=" -> "vượt";
                    case "<", "<=" -> "dưới";
                    default -> "bằng";
                };
        return label
                + " đo được "
                + number(v.measuredValue())
                + " "
                + v.unit()
                + ", "
                + relation
                + " ngưỡng "
                + v.operator()
                + " "
                + number(v.thresholdValue())
                + " "
                + v.unit()
                + " (chênh "
                + exceedance(v)
                + " "
                + v.unit()
                + ", mức "
                + severity(v.severityLevel())
                + ")";
    }

    /** Minimal alert template — still grounded only in the snapshot (W5-04 §7 last rung). */
    private String minimal(FactualContext context, int remainingViolations) {
        if (context.intent() == ChatIntent.ALERT_EXPLAIN && context.alert() != null) {
            FactualContext.AlertContext alert = context.alert();
            AlertFactualSnapshot snapshot = alert.factualSnapshot();
            if (snapshot != null && snapshot.representativeThreshold() != null) {
                var rep = snapshot.representativeThreshold();
                int total = snapshot.violations().size() + remainingViolations;
                return "Cảnh báo "
                        + severity(alert.severityLevel())
                        + " tại trạm "
                        + stationName(snapshot)
                        + " phát lúc "
                        + time(
                                alert.triggeredAt() != null
                                        ? alert.triggeredAt()
                                        : snapshot.evaluatedAt())
                        + ": "
                        + VIOLATION_LABELS.getOrDefault(rep.parameter(), rep.parameter())
                        + " ngưỡng "
                        + rep.operator()
                        + " "
                        + number(rep.thresholdValue())
                        + " "
                        + rep.unit()
                        + "; tổng "
                        + total
                        + " vi phạm (không liệt kê chi tiết).";
            }
        }
        return "Hệ thống chưa thể trình bày chi tiết dữ liệu lúc này. Bạn có thể hỏi lại về lượng"
                + " mưa, gió hoặc bão đang hoạt động.";
    }

    private List<FactualContext.StationObservation> selectStations(
            List<FactualContext.StationObservation> stations,
            ChatReplyGuardrail.Selection selection) {
        if (selection == null) {
            return stations;
        }
        List<FactualContext.StationObservation> ordered =
                stations.stream().filter(s -> selection.itemCodes().contains(s.code())).toList();
        if (ordered.isEmpty()) {
            throw new IllegalStateException("Verified selection resolves to no station");
        }
        return ordered;
    }

    private List<FactualContext.StormSummary> selectStorms(
            List<FactualContext.StormSummary> storms, ChatReplyGuardrail.Selection selection) {
        if (selection == null) {
            return storms;
        }
        List<FactualContext.StormSummary> ordered =
                storms.stream().filter(s -> selection.itemCodes().contains(s.stormId())).toList();
        if (ordered.isEmpty()) {
            throw new IllegalStateException("Verified selection resolves to no storm");
        }
        return ordered;
    }

    private List<ViolationSnapshot> selectViolations(
            FactualContext.AlertContext alert, ChatReplyGuardrail.Selection selection) {
        Map<String, ViolationSnapshot> byKey = new LinkedHashMap<>();
        for (ViolationSnapshot v : alert.factualSnapshot().violations()) {
            byKey.put(v.violationKey(), v);
        }
        List<String> keys =
                selection == null
                        ? alert.violationOrder().stream()
                                .map(FactualContext.ViolationOrder::violationKey)
                                .toList()
                        : alert.violationOrder().stream()
                                .map(FactualContext.ViolationOrder::violationKey)
                                .filter(selection.violationKeys()::contains)
                                .toList();
        List<ViolationSnapshot> ordered = new ArrayList<>();
        for (String key : keys) {
            ViolationSnapshot v = byKey.get(key);
            if (v == null) {
                throw new IllegalStateException("Violation order references unknown key");
            }
            ordered.add(v);
        }
        if (ordered.isEmpty()) {
            throw new IllegalStateException("Verified selection resolves to no violation");
        }
        return ordered;
    }

    private void validateViolation(ViolationSnapshot v) {
        if (!VIOLATION_LABELS.containsKey(v.parameter())
                || !OPERATORS.contains(v.operator())
                || !SEVERITIES.contains(v.severityLevel())
                || v.unit() == null
                || v.unit().isBlank()
                || !Double.isFinite(v.measuredValue())
                || !Double.isFinite(v.thresholdValue())) {
            throw new IllegalStateException("Snapshot violation violates rendering invariants");
        }
    }

    private static String stationName(AlertFactualSnapshot snapshot) {
        String name =
                snapshot.locationName() == null ? snapshot.locationCode() : snapshot.locationName();
        String code = snapshot.locationCode();
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Snapshot station code missing");
        }
        return name + " (" + code + ")";
    }

    private static String severity(String level) {
        if (!SEVERITIES.contains(level)) {
            throw new IllegalStateException("Unknown severity level");
        }
        return level;
    }

    private static String exceedance(ViolationSnapshot v) {
        return BigDecimal.valueOf(v.measuredValue())
                .subtract(BigDecimal.valueOf(v.thresholdValue()))
                .stripTrailingZeros()
                .toPlainString();
    }

    private static boolean hasValue(FactualContext.StationObservation station, String field) {
        return switch (field) {
            case "temperature" -> finite(station.temperature());
            case "humidity" -> finite(station.humidity());
            case "pressure" -> finite(station.pressure());
            case "wind_speed" -> finite(station.windSpeed());
            case "wind_gust" -> finite(station.windGust());
            case "wind_direction" -> finite(station.windDirection());
            case "rain_1h" -> finite(station.rain1h());
            case "rain_3h" -> finite(station.rain3h());
            case "weather_condition" ->
                    station.weatherCondition() != null && !station.weatherCondition().isBlank();
            case "weather_description" ->
                    station.weatherDescription() != null && !station.weatherDescription().isBlank();
            default -> false;
        };
    }

    private static String fieldValue(FactualContext.StationObservation station, String field) {
        Double numeric =
                switch (field) {
                    case "temperature" -> station.temperature();
                    case "humidity" -> station.humidity();
                    case "pressure" -> station.pressure();
                    case "wind_speed" -> station.windSpeed();
                    case "wind_gust" -> station.windGust();
                    case "wind_direction" -> station.windDirection();
                    case "rain_1h" -> station.rain1h();
                    case "rain_3h" -> station.rain3h();
                    default -> null;
                };
        if (numeric != null) {
            return number(numeric) + " " + unit(station.units(), field);
        }
        if ("weather_condition".equals(field)) {
            return station.weatherCondition();
        }
        if ("weather_description".equals(field)) {
            return station.weatherDescription();
        }
        throw new IllegalStateException("Field has no value: " + field);
    }

    private static String unit(Map<String, String> units, String field) {
        String unit = units.get(field);
        if (unit == null || unit.isBlank()) {
            throw new IllegalStateException("Context unit missing for " + field);
        }
        return unit;
    }

    private static String coordinates(List<Double> lngLat) {
        if (lngLat.size() != 2 || !finite(lngLat.get(0)) || !finite(lngLat.get(1))) {
            throw new IllegalStateException("Invalid coordinates");
        }
        return "[" + number(lngLat.get(0)) + "," + number(lngLat.get(1)) + "]";
    }

    private static String time(Instant instant) {
        if (instant == null) {
            throw new IllegalStateException("Context timestamp missing");
        }
        return TIME.format(instant);
    }

    private static double requireFinite(Double value) {
        if (!finite(value)) {
            throw new IllegalStateException("Context value missing or non-finite");
        }
        return value;
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    /** W4 pattern: locale-independent, no trailing zeros, no rounding. */
    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static Map<String, String> fieldLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("temperature", "nhiệt độ");
        labels.put("humidity", "độ ẩm");
        labels.put("pressure", "áp suất");
        labels.put("wind_speed", "tốc độ gió");
        labels.put("wind_gust", "gió giật");
        labels.put("wind_direction", "hướng gió");
        labels.put("rain_1h", "lượng mưa 1 giờ");
        labels.put("rain_3h", "lượng mưa 3 giờ");
        labels.put("weather_condition", "trạng thái thời tiết");
        labels.put("weather_description", "mô tả thời tiết");
        return Map.copyOf(labels);
    }
}
