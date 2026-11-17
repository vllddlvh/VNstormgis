package vn.weathergis.chat.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.weathergis.chat.port.ChatRateLimiter;
import vn.weathergis.chat.port.NoOpChatRateLimiter;

/** Chat module configuration; reuses the application-wide UTC Clock bean. */
@Configuration
@EnableConfigurationProperties({
    ChatContextProperties.class,
    ChatSessionProperties.class,
    ChatReplyProperties.class
})
public class ChatConfig {

    /** Allow-all until W5-05 replaces it with Bucket4j+Redis (must not survive W5-05). */
    @Bean
    @ConditionalOnMissingBean(ChatRateLimiter.class)
    public ChatRateLimiter noOpChatRateLimiter() {
        return new NoOpChatRateLimiter();
    }
}
