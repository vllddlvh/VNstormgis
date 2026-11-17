package vn.weathergis.chat.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.intent.ChatIntent;

/**
 * Closed per-intent grammar (W5-04 §5, contract §4.3). Every number is compared with {@code
 * BigDecimal.compareTo} against the context value and every entity must belong to the current
 * context; the whole item list is traversed, not just the first number. Free prose can never pass,
 * so injected instructions have no publication path. Returns a verdict — never throws content.
 */
@Component
public class ChatReplyGuardrail {

    public static final String REASON_SCHEMA = "schema";
    public static final String REASON_NUMBER_MISMATCH = "number_mismatch";
    public static final String REASON_ENTITY_UNKNOWN = "entity_unknown";
    public static final String REASON_INTENT_MISMATCH = "intent_mismatch";

    public enum Variant {
        FULL,
        COMPACT
    }

    public record Selection(
            Variant variant,
            List<String> itemCodes,
            List<String> fields,
            List<String> violationKeys) {

        public Selection {
            itemCodes = itemCodes == null ? List.of() : List.copyOf(itemCodes);
            fields = fields == null ? List.of() : List.copyOf(fields);
            violationKeys = violationKeys == null ? List.of() : List.copyOf(violationKeys);
        }
    }

    public sealed interface Verdict permits Accepted, Rejected {}

    public record Accepted(Selection selection) implements Verdict {}

    public record Rejected(String reason) implements Verdict {}

    private final ObjectMapper mapper;

    public ChatReplyGuardrail(ObjectMapper mapper) {
        this.mapper =
                mapper.copy()
                        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public Verdict validate(FactualContext context, String modelJson, ChatIntent expectedIntent) {
        JsonNode root;
        try {
            root = mapper.readTree(modelJson);
        } catch (Exception e) {
            return new Rejected(REASON_SCHEMA);
        }
        if (root == null || !root.isObject()) {
            return new Rejected(REASON_SCHEMA);
        }
        JsonNode intentNode = root.get("intent");
        if (intentNode == null || !intentNode.isTextual()) {
            return new Rejected(REASON_SCHEMA);
        }
        if (!intentNode.asText().equals(expectedIntent.wireName())) {
            return new Rejected(REASON_INTENT_MISMATCH);
        }
        Variant variant = variant(root.get("variant"));
        if (variant == null) {
            return new Rejected(REASON_SCHEMA);
        }
        return switch (expectedIntent) {
            case RAIN_TOP -> validateTop(context, root, variant, "rain_1h");
            case WIND_TOP -> validateTop(context, root, variant, "wind_speed");
            case STATION_CURRENT -> validateStation(context, root, variant);
            case STORM_ACTIVE -> validateStorms(context, root, variant);
            case ALERT_EXPLAIN -> validateAlert(context, root, variant);
            case OUT_OF_SCOPE -> new Rejected(REASON_SCHEMA);
        };
    }

    private static Variant variant(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        return switch (node.asText()) {
            case "FULL" -> Variant.FULL;
            case "COMPACT" -> Variant.COMPACT;
            default -> null;
        };
    }

    private Verdict validateTop(
            FactualContext context, JsonNode root, Variant variant, String valueField) {
        if (!fieldNames(root).equals(Set.of("intent", "items", "variant"))) {
            return new Rejected(REASON_SCHEMA);
        }
        JsonNode items = root.get("items");
        List<FactualContext.StationObservation> stations = context.stations();
        if (!items.isArray() || items.isEmpty() || items.size() > stations.size()) {
            return new Rejected(REASON_SCHEMA);
        }
        Set<String> codes = new HashSet<>();
        for (FactualContext.StationObservation station : stations) {
            codes.add(station.code());
        }
        List<String> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String firstReason = null;
        for (JsonNode item : items) {
            String reason = null;
            if (!item.isObject() || !fieldNames(item).equals(Set.of("code", valueField))) {
                reason = REASON_SCHEMA;
            } else {
                JsonNode code = item.get("code");
                JsonNode value = item.get(valueField);
                if (!code.isTextual() || !seen.add(code.asText())) {
                    reason = REASON_SCHEMA;
                } else if (!codes.contains(code.asText())) {
                    reason = REASON_ENTITY_UNKNOWN;
                } else if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                    reason = REASON_SCHEMA;
                } else {
                    Double contextValue =
                            "rain_1h".equals(valueField)
                                    ? stationByCode(stations, code.asText()).rain1h()
                                    : stationByCode(stations, code.asText()).windSpeed();
                    if (contextValue == null
                            || value.decimalValue().compareTo(BigDecimal.valueOf(contextValue))
                                    != 0) {
                        reason = REASON_NUMBER_MISMATCH;
                    }
                }
            }
            if (reason != null && firstReason == null) {
                firstReason = reason;
            }
            if (item.isObject() && item.path("code").isTextual()) {
                selected.add(item.path("code").asText());
            }
        }
        if (firstReason != null) {
            return new Rejected(firstReason);
        }
        return new Accepted(new Selection(variant, selected, List.of(), List.of()));
    }

    private Verdict validateStation(FactualContext context, JsonNode root, Variant variant) {
        if (!fieldNames(root).equals(Set.of("intent", "code", "fields", "variant"))) {
            return new Rejected(REASON_SCHEMA);
        }
        if (context.stations().size() != 1) {
            return new Rejected(REASON_SCHEMA);
        }
        FactualContext.StationObservation station = context.stations().get(0);
        JsonNode code = root.get("code");
        if (!code.isTextual()) {
            return new Rejected(REASON_SCHEMA);
        }
        if (!code.asText().equals(station.code())) {
            return new Rejected(REASON_ENTITY_UNKNOWN);
        }
        JsonNode fields = root.get("fields");
        if (!fields.isArray() || fields.isEmpty()) {
            return new Rejected(REASON_SCHEMA);
        }
        Set<String> allowed = nonNullFields(station);
        Set<String> seen = new HashSet<>();
        List<String> selected = new ArrayList<>();
        for (JsonNode field : fields) {
            if (!field.isTextual()
                    || !allowed.contains(field.asText())
                    || !seen.add(field.asText())) {
                return new Rejected(REASON_SCHEMA);
            }
            selected.add(field.asText());
        }
        return new Accepted(new Selection(variant, List.of(station.code()), selected, List.of()));
    }

    private Verdict validateStorms(FactualContext context, JsonNode root, Variant variant) {
        if (!fieldNames(root).equals(Set.of("intent", "items", "variant"))) {
            return new Rejected(REASON_SCHEMA);
        }
        JsonNode items = root.get("items");
        List<FactualContext.StormSummary> storms = context.storms();
        if (!items.isArray() || items.isEmpty() || items.size() > storms.size()) {
            return new Rejected(REASON_SCHEMA);
        }
        List<String> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String firstReason = null;
        for (JsonNode item : items) {
            String reason = null;
            if (!item.isObject()
                    || !fieldNames(item).equals(Set.of("storm_id", "max_wind_speed"))) {
                reason = REASON_SCHEMA;
            } else {
                JsonNode id = item.get("storm_id");
                JsonNode value = item.get("max_wind_speed");
                FactualContext.StormSummary storm = stormById(storms, id.asText());
                if (!id.isTextual() || !seen.add(id.asText())) {
                    reason = REASON_SCHEMA;
                } else if (storm == null) {
                    reason = REASON_ENTITY_UNKNOWN;
                } else if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                    reason = REASON_SCHEMA;
                } else if (storm.maxWindSpeed() == null
                        || value.decimalValue().compareTo(BigDecimal.valueOf(storm.maxWindSpeed()))
                                != 0) {
                    reason = REASON_NUMBER_MISMATCH;
                }
            }
            if (reason != null && firstReason == null) {
                firstReason = reason;
            }
            if (item.isObject() && item.path("storm_id").isTextual()) {
                selected.add(item.path("storm_id").asText());
            }
        }
        if (firstReason != null) {
            return new Rejected(firstReason);
        }
        return new Accepted(new Selection(variant, selected, List.of(), List.of()));
    }

    private Verdict validateAlert(FactualContext context, JsonNode root, Variant variant) {
        if (!fieldNames(root).equals(Set.of("intent", "alert_id", "violation_keys", "variant"))) {
            return new Rejected(REASON_SCHEMA);
        }
        FactualContext.AlertContext alert = context.alert();
        if (alert == null || alert.alertId() == null) {
            return new Rejected(REASON_SCHEMA);
        }
        JsonNode alertId = root.get("alert_id");
        if (!alertId.isTextual()) {
            return new Rejected(REASON_SCHEMA);
        }
        if (!alertId.asText().equals(alert.alertId().toString())) {
            return new Rejected(REASON_ENTITY_UNKNOWN);
        }
        JsonNode keys = root.get("violation_keys");
        if (!keys.isArray() || keys.isEmpty()) {
            return new Rejected(REASON_SCHEMA);
        }
        Set<String> allowed = new LinkedHashSet<>();
        for (FactualContext.ViolationOrder order : alert.violationOrder()) {
            allowed.add(order.violationKey());
        }
        Set<String> seen = new HashSet<>();
        List<String> selected = new ArrayList<>();
        for (JsonNode key : keys) {
            if (!key.isTextual() || !seen.add(key.asText())) {
                return new Rejected(REASON_SCHEMA);
            }
            if (!allowed.contains(key.asText())) {
                return new Rejected(REASON_ENTITY_UNKNOWN);
            }
            selected.add(key.asText());
        }
        return new Accepted(new Selection(variant, List.of(), List.of(), selected));
    }

    private static Set<String> nonNullFields(FactualContext.StationObservation station) {
        Set<String> allowed = new LinkedHashSet<>();
        if (finite(station.temperature())) allowed.add("temperature");
        if (finite(station.humidity())) allowed.add("humidity");
        if (finite(station.pressure())) allowed.add("pressure");
        if (finite(station.windSpeed())) allowed.add("wind_speed");
        if (finite(station.windGust())) allowed.add("wind_gust");
        if (finite(station.windDirection())) allowed.add("wind_direction");
        if (finite(station.rain1h())) allowed.add("rain_1h");
        if (finite(station.rain3h())) allowed.add("rain_3h");
        if (station.weatherCondition() != null && !station.weatherCondition().isBlank()) {
            allowed.add("weather_condition");
        }
        if (station.weatherDescription() != null && !station.weatherDescription().isBlank()) {
            allowed.add("weather_description");
        }
        return allowed;
    }

    private static boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static FactualContext.StationObservation stationByCode(
            List<FactualContext.StationObservation> stations, String code) {
        return stations.stream()
                .filter(s -> s.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Station vanished from context"));
    }

    private static FactualContext.StormSummary stormById(
            List<FactualContext.StormSummary> storms, String id) {
        return storms.stream().filter(s -> s.stormId().equals(id)).findFirst().orElse(null);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
