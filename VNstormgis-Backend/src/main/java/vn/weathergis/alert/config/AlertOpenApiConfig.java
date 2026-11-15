package vn.weathergis.alert.config;

import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Makes nullable alert links explicit even when springdoc emits a component reference. */
@Configuration
public class AlertOpenApiConfig {
    private static final String SCHEMA_PREFIX = "#/components/schemas/";

    @Bean
    OpenApiCustomizer nullableAlertDetailLinks() {
        return openApi -> {
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            Schema<?> detail = openApi.getComponents().getSchemas().get("AlertDetailResponse");
            if (detail == null || detail.getProperties() == null) {
                return;
            }
            for (String field : List.of("location", "threshold", "storm", "factual_snapshot")) {
                Schema<?> property = detail.getProperties().get(field);
                if (property != null
                        && property.get$ref() != null
                        && property.get$ref().startsWith(SCHEMA_PREFIX)) {
                    String componentName = property.get$ref().substring(SCHEMA_PREFIX.length());
                    Schema<?> target = openApi.getComponents().getSchemas().get(componentName);
                    if (target != null) {
                        Schema<?> nullable = new Schema<>().type("object").nullable(true);
                        nullable.setDescription(
                                "Current link or historical snapshot; null when unavailable");
                        nullable.setProperties(target.getProperties());
                        nullable.setRequired(target.getRequired());
                        detail.getProperties().put(field, nullable);
                    }
                }
            }
        };
    }
}
