package vn.weathergis.chat.config;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chat module configuration; reuses the application-wide UTC Clock bean. */
@Configuration
@EnableConfigurationProperties({
    ChatContextProperties.class,
    ChatSessionProperties.class,
    ChatReplyProperties.class,
    ChatRateLimitProperties.class
})
public class ChatConfig {

    @Bean
    GlobalOpenApiCustomizer chatOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Schema<?> req = openApi.getComponents().getSchemas().get("SendMessageRequest");
            if (req != null && req.getProperties() != null) {
                ArraySchema bbox = new ArraySchema();
                bbox.setItems(new NumberSchema());
                bbox.setMinItems(4);
                bbox.setMaxItems(4);
                bbox.setDescription("WGS84 [minLng, minLat, maxLng, maxLat]");
                Schema<?> spatial = new Schema<>().type("object").nullable(true);
                spatial.setDescription(
                        "Bộ lọc không gian tùy chọn dạng {\"bbox\":[minLng,minLat,maxLng,maxLat]} WGS84");
                spatial.setRequired(List.of("bbox"));
                spatial.setProperties(Map.of("bbox", bbox));
                Map<String, Schema> props = new LinkedHashMap<>(req.getProperties());
                props.put("spatial_filter", spatial);
                req.setProperties(props);
            }
        };
    }
}
