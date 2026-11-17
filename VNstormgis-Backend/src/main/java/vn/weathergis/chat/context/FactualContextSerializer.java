package vn.weathergis.chat.context;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.TimeZone;
import org.springframework.stereotype.Component;
import vn.weathergis.chat.exception.ChatContextException;

/**
 * Canonical deterministic JSON for the factual context: fixed record field order, snake_case keys,
 * sorted map keys, UTC ISO-8601 instants, explicit nulls, locale-independent numbers.
 */
@Component
public class FactualContextSerializer {

    private final ObjectMapper mapper;

    public FactualContextSerializer(ObjectMapper mapper) {
        this.mapper =
                mapper.copy()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .setSerializationInclusion(JsonInclude.Include.ALWAYS)
                        .setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public String serialize(FactualContext context) {
        try {
            return mapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new ChatContextException("Không thể tuần tự hóa factual context", e);
        }
    }

    /** UTF-8 byte length of the canonical payload without the {@code size_bytes} field. */
    public long payloadBytes(FactualContext context) {
        ObjectNode node = mapper.valueToTree(context);
        node.remove("size_bytes");
        return writeBytes(node).length;
    }

    public long measureBytes(Object value) {
        return writeBytes(mapper.valueToTree(value)).length;
    }

    private byte[] writeBytes(ObjectNode node) {
        try {
            return mapper.writeValueAsBytes(node);
        } catch (JsonProcessingException e) {
            throw new ChatContextException("Không thể đo kích thước factual context", e);
        }
    }
}
