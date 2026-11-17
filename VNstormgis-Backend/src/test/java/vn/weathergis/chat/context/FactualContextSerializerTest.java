package vn.weathergis.chat.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.weathergis.chat.intent.ChatIntent;

/** Canonical deterministic serialization contract for W5-04 (W5-02 §4.7). */
class FactualContextSerializerTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");

    private FactualContextSerializer serializer;
    private ObjectMapper reader;

    @BeforeEach
    void setUp() {
        reader = new ObjectMapper().registerModule(new JavaTimeModule());
        serializer = new FactualContextSerializer(reader);
    }

    private FactualContext context() {
        FactualContext.StationObservation station =
                new FactualContext.StationObservation(
                        "DNG",
                        "Đà Nẵng",
                        List.of(108.2, 16.0),
                        NOW.minusSeconds(600),
                        false,
                        25.0,
                        70.0,
                        1000.0,
                        5.0,
                        null,
                        90.0,
                        10.0,
                        30.0,
                        "Rain",
                        "mưa vừa",
                        Map.of("rain_1h", "mm", "wind_speed", "m/s"));
        return new FactualContext(
                FactualContext.SCHEMA_VERSION,
                ChatIntent.RAIN_TOP,
                NOW,
                ChatContextOutcome.CONTEXT_READY,
                null,
                List.of(station),
                List.of(),
                null,
                new FactualContext.ReferencedEntities(List.of("DNG"), List.of(), List.of()),
                new FactualContext.ContextMeta(60, 0, 0, false),
                0);
    }

    @Test
    void fieldOrderIsStableAndSnakeCase() throws Exception {
        String json = serializer.serialize(context().withSizeBytes(123));

        JsonNode node = reader.readTree(json);
        Iterator<String> fields = node.fieldNames();
        assertThat(fields.next()).isEqualTo("schema_version");
        assertThat(fields.next()).isEqualTo("intent");
        assertThat(fields.next()).isEqualTo("generated_at");
        assertThat(json)
                .contains("\"recorded_at\"")
                .contains("\"wind_speed\"")
                .contains("\"rain_1h\"");
        assertThat(json).contains("\"size_bytes\":123");
    }

    @Test
    void nullObservationFieldsArePreserved() throws Exception {
        String json = serializer.serialize(context());

        assertThat(reader.readTree(json).get("stations").get(0).has("wind_gust")).isTrue();
        assertThat(reader.readTree(json).get("stations").get(0).get("wind_gust").isNull()).isTrue();
    }

    @Test
    void instantsSerializeAsUtcIso8601() throws Exception {
        String json = serializer.serialize(context());

        assertThat(reader.readTree(json).get("generated_at").asText())
                .isEqualTo("2026-11-17T09:00:00Z");
    }

    @Test
    void serializationIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            String german = serializer.serialize(context());
            Locale.setDefault(Locale.US);
            String us = serializer.serialize(context());

            assertThat(german).isEqualTo(us);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void repeatedSerializationIsByteIdentical() {
        FactualContext ctx = context();

        assertThat(serializer.serialize(ctx)).isEqualTo(serializer.serialize(ctx));
    }

    @Test
    void sizeBytesMeasuresPayloadExcludingTheSizeFieldItself() throws Exception {
        FactualContext ctx = context();
        long payloadBytes = serializer.payloadBytes(ctx);

        String json = serializer.serialize(ctx.withSizeBytes(payloadBytes));
        com.fasterxml.jackson.databind.node.ObjectNode node =
                (com.fasterxml.jackson.databind.node.ObjectNode) reader.readTree(json);
        assertThat(node.get("size_bytes").asLong()).isEqualTo(payloadBytes);
        node.remove("size_bytes");
        assertThat(reader.writeValueAsBytes(node).length).isEqualTo((int) payloadBytes);
        assertThat(json.getBytes(StandardCharsets.UTF_8).length).isGreaterThan((int) payloadBytes);
    }
}
