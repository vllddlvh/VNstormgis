package vn.weathergis.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Chat module configuration; reuses the application-wide UTC Clock bean. */
@Configuration
@EnableConfigurationProperties(ChatContextProperties.class)
public class ChatConfig {}
