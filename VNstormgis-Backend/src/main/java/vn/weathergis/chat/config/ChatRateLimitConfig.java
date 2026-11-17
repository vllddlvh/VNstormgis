package vn.weathergis.chat.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import vn.weathergis.chat.port.ChatRateLimiter;
import vn.weathergis.chat.ratelimit.ChatClientIpResolver;
import vn.weathergis.chat.ratelimit.RedisChatRateLimiter;

/** Redis quota storage is independent of the optional read cache. */
@Configuration
public class ChatRateLimitConfig {
    @Bean
    ChatRateLimiter chatRateLimiter(
            LettuceConnectionFactory connectionFactory,
            ChatRateLimitProperties properties,
            MeterRegistry meters) {
        properties.validate();
        ChatClientIpResolver resolver = new ChatClientIpResolver(properties);
        Supplier<ProxyManager<byte[]>> manager =
                () -> {
                    // A Spring wrapper can be closed while its shared native Lettuce connection
                    // lives on.
                    try (RedisConnection connection = connectionFactory.getConnection()) {
                        @SuppressWarnings("unchecked")
                        RedisAsyncCommands<byte[], byte[]> commands =
                                (RedisAsyncCommands<byte[], byte[]>)
                                        connection.getNativeConnection();
                        return LettuceBasedProxyManager.builderFor(commands)
                                .withClientSideConfig(
                                        ClientSideConfig.getDefault()
                                                .withRequestTimeout(
                                                        connectionFactory
                                                                .getClientConfiguration()
                                                                .getCommandTimeout())
                                                .withExpirationAfterWriteStrategy(
                                                        ExpirationAfterWriteStrategy
                                                                .basedOnTimeForRefillingBucketUpToMax(
                                                                        Duration.ofSeconds(1))))
                                .build();
                    }
                };
        return new RedisChatRateLimiter(manager, resolver, properties, meters);
    }
}
