package vn.weathergis.chat.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.chat.context.ChatContextOutcome;
import vn.weathergis.chat.context.FactualContext;
import vn.weathergis.chat.intent.ChatIntent;

/** Closed grammar per intent (W5-04 §5, contract §4.3): every number and entity is checked. */
class ChatReplyGuardrailTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final UUID ALERT_ID = UUID.fromString("00000000-0000-0000-0000-000000000504");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatReplyGuardrail guardrail = new ChatReplyGuardrail(mapper);

    private FactualContext.StationObservation station(
            String code, String name, Double rain, Double wind) {
        return new FactualContext.StationObservation(
                code,
                name,
                List.of(108.2, 16.0),
                NOW.minusSeconds(600),
                false,
                null,
                null,
                null,
                wind,
                null,
                null,
                rain,
                null,
                null,
                null,
                Map.of("rain_1h", "mm", "wind_speed", "m/s"));
    }

    private FactualContext rainContext() {
        return context(
                ChatIntent.RAIN_TOP,
                List.of(
                        station("DNG", "Trạm Đà Nẵng", 82.4, 5.0),
                        station("SGN", "Trạm SGN", 12.0, 3.0)),
                List.of(),
                null);
    }

    private FactualContext context(
            ChatIntent intent,
            List<FactualContext.StationObservation> stations,
            List<FactualContext.StormSummary> storms,
            FactualContext.AlertContext alert) {
        return new FactualContext(
                FactualContext.SCHEMA_VERSION,
                intent,
                NOW,
                ChatContextOutcome.CONTEXT_READY,
                null,
                stations,
                storms,
                alert,
                new FactualContext.ReferencedEntities(List.of(), List.of(), List.of()),
                new FactualContext.ContextMeta(60, 0, 0, false),
                100);
    }

    private FactualContext stormContext() {
        FactualContext.StormSummary storm =
                new FactualContext.StormSummary(
                        "W504TC",
                        "Storm W504TC",
                        "Tropical Storm",
                        45.0,
                        960.0,
                        List.of(127.4, 31.2),
                        NOW.minusSeconds(600),
                        "db_update",
                        false,
                        "GDACS",
                        null,
                        Map.of("max_wind_speed", "m/s", "min_pressure", "hPa"));
        return context(ChatIntent.STORM_ACTIVE, List.of(), List.of(storm), null);
    }

    private FactualContext alertContext(List<String> cutKeys) {
        List<ViolationSnapshot> violations =
                cutKeys.stream()
                        .map(
                                key ->
                                        new ViolationSnapshot(
                                                1,
                                                "rain_1h",
                                                ">=",
                                                50.0,
                                                "DANGER",
                                                65.5,
                                                "mm",
                                                NOW.minusSeconds(600),
                                                NOW.minusSeconds(300),
                                                key,
                                                null,
                                                null,
                                                null))
                        .toList();
        AlertFactualSnapshot snapshot =
                new AlertFactualSnapshot(
                        UUID.randomUUID(),
                        "DNG",
                        "Trạm Đà Nẵng",
                        List.of(108.2, 16.0),
                        UUID.randomUUID(),
                        NOW.minusSeconds(300),
                        new RepresentativeThresholdSnapshot(
                                1, "rain_1h", ">=", 50.0, "DANGER", "mm", cutKeys.get(0)),
                        violations);
        FactualContext.AlertContext alert =
                new FactualContext.AlertContext(
                        ALERT_ID,
                        "active",
                        "DANGER",
                        NOW.minusSeconds(300),
                        null,
                        true,
                        snapshot,
                        cutKeys.stream()
                                .map(k -> new FactualContext.ViolationOrder(k, "DANGER", 15.5))
                                .toList(),
                        false,
                        1000);
        return context(ChatIntent.ALERT_EXPLAIN, List.of(), List.of(), alert);
    }

    private ChatReplyGuardrail.Verdict validate(
            FactualContext ctx, String json, ChatIntent intent) {
        return guardrail.validate(ctx, json, intent);
    }

    private void assertRejected(ChatReplyGuardrail.Verdict verdict, String reason) {
        assertThat(verdict).isInstanceOf(ChatReplyGuardrail.Rejected.class);
        assertThat(((ChatReplyGuardrail.Rejected) verdict).reason()).isEqualTo(reason);
    }

    @Test
    void acceptsValidRainTopWithExactContextNumbers() {
        var verdict =
                validate(
                        rainContext(),
                        "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.4},"
                                + "{\"code\":\"SGN\",\"rain_1h\":12.0}],\"variant\":\"FULL\"}",
                        ChatIntent.RAIN_TOP);

        assertThat(verdict).isInstanceOf(ChatReplyGuardrail.Accepted.class);
        var selection = ((ChatReplyGuardrail.Accepted) verdict).selection();
        assertThat(selection.variant()).isEqualTo(ChatReplyGuardrail.Variant.FULL);
        assertThat(selection.itemCodes()).containsExactly("DNG", "SGN");
    }

    @Test
    void scaleTrapPassesBecauseComparisonIsBigDecimalCompareTo() {
        var verdict =
                validate(
                        rainContext(),
                        "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.400}],"
                                + "\"variant\":\"COMPACT\"}",
                        ChatIntent.RAIN_TOP);
        assertThat(verdict).isInstanceOf(ChatReplyGuardrail.Accepted.class);
    }

    @Test
    void rejectsWrongNumberOnAnyItemNotOnlyTheFirst() {
        assertRejected(
                validate(
                        rainContext(),
                        "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.4},"
                                + "{\"code\":\"SGN\",\"rain_1h\":12.1}],\"variant\":\"FULL\"}",
                        ChatIntent.RAIN_TOP),
                "number_mismatch");
        assertRejected(
                validate(
                        rainContext(),
                        "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.5}],"
                                + "\"variant\":\"FULL\"}",
                        ChatIntent.RAIN_TOP),
                "number_mismatch");
    }

    @Test
    void rejectsUnknownOrDuplicateStationCodes() {
        assertRejected(
                validate(
                        rainContext(),
                        "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"XXX\",\"rain_1h\":82.4}],"
                                + "\"variant\":\"FULL\"}",
                        ChatIntent.RAIN_TOP),
                "entity_unknown");
        assertRejected(
                validate(
                        rainContext(),
                        "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.4},"
                                + "{\"code\":\"DNG\",\"rain_1h\":82.4}],\"variant\":\"FULL\"}",
                        ChatIntent.RAIN_TOP),
                "schema");
    }

    @Test
    void rejectsEmptyOrOversizedItems() {
        assertRejected(
                validate(
                        rainContext(),
                        "{\"intent\":\"rain_top\",\"items\":[],\"variant\":\"FULL\"}",
                        ChatIntent.RAIN_TOP),
                "schema");
        assertRejected(
                validate(
                        rainContext(),
                        "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.4},"
                                + "{\"code\":\"SGN\",\"rain_1h\":12.0},"
                                + "{\"code\":\"DNG\",\"rain_1h\":82.4}],\"variant\":\"FULL\"}",
                        ChatIntent.RAIN_TOP),
                "schema");
    }

    @Test
    void rejectsStructuralViolations() {
        String[] bad = {
            "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.4,\"extra\":1}],"
                    + "\"variant\":\"FULL\"}",
            "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\"}],\"variant\":\"FULL\"}",
            "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":\"82.4\"}],"
                    + "\"variant\":\"FULL\"}",
            "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":null}],"
                    + "\"variant\":\"FULL\"}",
            "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.4}],"
                    + "\"variant\":\"FULL\",\"variant\":\"COMPACT\"}",
            "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.4}],"
                    + "\"variant\":\"FULL\"} {}",
            "{\"intent\":\"rain_top\",\"items\":[{\"code\":\"DNG\",\"rain_1h\":82.4}],"
                    + "\"variant\":\"SHORT\"}",
            "Tuyệt vời, hôm nay mưa 82.4 mm!",
            "{\"intent\":\"rain_top\",\"items\":\"DNG\",\"variant\":\"FULL\"}",
        };
        for (String json : bad) {
            assertRejected(validate(rainContext(), json, ChatIntent.RAIN_TOP), "schema");
        }
    }

    @Test
    void rejectsIntentSwitchAttempt() {
        assertRejected(
                validate(
                        rainContext(),
                        "{\"intent\":\"wind_top\",\"items\":[{\"code\":\"DNG\",\"wind_speed\":5.0}],"
                                + "\"variant\":\"FULL\"}",
                        ChatIntent.RAIN_TOP),
                "intent_mismatch");
    }

    @Test
    void windTopChecksWindSpeedAgainstContext() {
        var ok =
                validate(
                        context(
                                ChatIntent.WIND_TOP,
                                List.of(station("DNG", "Trạm Đà Nẵng", 82.4, 5.0)),
                                List.of(),
                                null),
                        "{\"intent\":\"wind_top\",\"items\":[{\"code\":\"DNG\",\"wind_speed\":5.00}],"
                                + "\"variant\":\"FULL\"}",
                        ChatIntent.WIND_TOP);
        assertThat(ok).isInstanceOf(ChatReplyGuardrail.Accepted.class);
        assertRejected(
                validate(
                        context(
                                ChatIntent.WIND_TOP,
                                List.of(station("DNG", "Trạm Đà Nẵng", 82.4, 5.0)),
                                List.of(),
                                null),
                        "{\"intent\":\"wind_top\",\"items\":[{\"code\":\"DNG\",\"wind_speed\":5.1}],"
                                + "\"variant\":\"FULL\"}",
                        ChatIntent.WIND_TOP),
                "number_mismatch");
    }

    @Test
    void stationCurrentFieldsMustBeNonNullObservationFields() {
        FactualContext ctx =
                context(
                        ChatIntent.STATION_CURRENT,
                        List.of(station("DNG", "Trạm Đà Nẵng", 0.0, 5.0)),
                        List.of(),
                        null);
        var ok =
                validate(
                        ctx,
                        "{\"intent\":\"station_current\",\"code\":\"DNG\","
                                + "\"fields\":[\"rain_1h\",\"wind_speed\"],\"variant\":\"FULL\"}",
                        ChatIntent.STATION_CURRENT);
        assertThat(ok).isInstanceOf(ChatReplyGuardrail.Accepted.class);
        assertThat(((ChatReplyGuardrail.Accepted) ok).selection().fields())
                .containsExactly("rain_1h", "wind_speed");

        assertRejected(
                validate(
                        ctx,
                        "{\"intent\":\"station_current\",\"code\":\"DNG\","
                                + "\"fields\":[\"temperature\"],\"variant\":\"FULL\"}",
                        ChatIntent.STATION_CURRENT),
                "schema");
        assertRejected(
                validate(
                        ctx,
                        "{\"intent\":\"station_current\",\"code\":\"DNG\","
                                + "\"fields\":[\"bogus\"],\"variant\":\"FULL\"}",
                        ChatIntent.STATION_CURRENT),
                "schema");
        assertRejected(
                validate(
                        ctx,
                        "{\"intent\":\"station_current\",\"code\":\"SGN\","
                                + "\"fields\":[\"rain_1h\"],\"variant\":\"FULL\"}",
                        ChatIntent.STATION_CURRENT),
                "entity_unknown");
        assertRejected(
                validate(
                        ctx,
                        "{\"intent\":\"station_current\",\"code\":\"DNG\",\"fields\","
                                + "\"variant\":\"FULL\"}",
                        ChatIntent.STATION_CURRENT),
                "schema");
    }

    @Test
    void stormActiveChecksIdAndMaxWindAgainstContext() {
        var ok =
                validate(
                        stormContext(),
                        "{\"intent\":\"storm_active\",\"items\":[{\"storm_id\":\"W504TC\","
                                + "\"max_wind_speed\":45.00}],\"variant\":\"FULL\"}",
                        ChatIntent.STORM_ACTIVE);
        assertThat(ok).isInstanceOf(ChatReplyGuardrail.Accepted.class);
        assertRejected(
                validate(
                        stormContext(),
                        "{\"intent\":\"storm_active\",\"items\":[{\"storm_id\":\"W504TC\","
                                + "\"max_wind_speed\":45.1}],\"variant\":\"FULL\"}",
                        ChatIntent.STORM_ACTIVE),
                "number_mismatch");
        assertRejected(
                validate(
                        stormContext(),
                        "{\"intent\":\"storm_active\",\"items\":[{\"storm_id\":\"OTHER\","
                                + "\"max_wind_speed\":45.0}],\"variant\":\"FULL\"}",
                        ChatIntent.STORM_ACTIVE),
                "entity_unknown");
    }

    @Test
    void alertExplainKeysMustBeSubsetOfTheCutOrder() {
        FactualContext ctx = alertContext(List.of("K1", "K2"));
        var ok =
                validate(
                        ctx,
                        "{\"intent\":\"alert_explain\",\"alert_id\":\""
                                + ALERT_ID
                                + "\","
                                + "\"violation_keys\":[\"K2\",\"K1\"],\"variant\":\"COMPACT\"}",
                        ChatIntent.ALERT_EXPLAIN);
        assertThat(ok).isInstanceOf(ChatReplyGuardrail.Accepted.class);
        assertThat(((ChatReplyGuardrail.Accepted) ok).selection().violationKeys())
                .containsExactly("K2", "K1");

        assertRejected(
                validate(
                        ctx,
                        "{\"intent\":\"alert_explain\",\"alert_id\":\""
                                + ALERT_ID
                                + "\","
                                + "\"violation_keys\":[\"K3\"],\"variant\":\"FULL\"}",
                        ChatIntent.ALERT_EXPLAIN),
                "entity_unknown");
        assertRejected(
                validate(
                        ctx,
                        "{\"intent\":\"alert_explain\",\"alert_id\":\""
                                + UUID.randomUUID()
                                + "\","
                                + "\"violation_keys\":[\"K1\"],\"variant\":\"FULL\"}",
                        ChatIntent.ALERT_EXPLAIN),
                "entity_unknown");
        assertRejected(
                validate(
                        ctx,
                        "{\"intent\":\"alert_explain\",\"alert_id\":\""
                                + ALERT_ID
                                + "\","
                                + "\"violation_keys\":[],\"variant\":\"FULL\"}",
                        ChatIntent.ALERT_EXPLAIN),
                "schema");
        assertRejected(
                validate(
                        ctx,
                        "{\"intent\":\"alert_explain\",\"alert_id\":\""
                                + ALERT_ID
                                + "\","
                                + "\"violation_keys\":[\"K1\",\"K1\"],\"variant\":\"FULL\"}",
                        ChatIntent.ALERT_EXPLAIN),
                "schema");
    }
}
