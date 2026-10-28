package vn.weathergis.common.cache;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cấu hình Redis Distributed Cache Layer (W3-03) — 3 vùng TTL chuyên biệt theo đặc tính dữ liệu
 * (README W3 Mục 5.2): locations 24h (danh mục tĩnh), weather_current 15m (khớp sync OWM),
 * active_storms 30m (khớp sync GDACS).
 *
 * <p>Value serializer là {@link StringRedisSerializer} — cache CHUỖI JSON GeoJSON đã render (quyết
 * định #1): tránh 3 bẫy serialize (NullNode D-1 / Geometry JTS / record + ListN) và bảo toàn hợp
 * đồng RFC 7946 byte-identical giữa cache-miss và cache-hit.
 *
 * <p>Không dùng {@code @EnableCaching}/{@code @Cacheable} — truy cập programmatic qua {@link
 * GeoJsonCacheService} (quyết định #2/#14).
 */
@Configuration
public class RedisCacheConfig {

    /** Prefix chuẩn mọi key cache — tiền đề assert {@code redis-cli --scan} của W3-09. */
    public static final String CACHE_KEY_PREFIX = "weathergis:cache:";

    @Bean
    @ConditionalOnProperty(
            prefix = "weathergis.cache",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base =
                RedisCacheConfiguration.defaultCacheConfig()
                        // Key cuối: weathergis:cache:<vùng>::<key>. Khai báo tường minh thay vì
                        // dựa vào CacheKeyPrefix.prefixed vì ký tự nối của nó không thuộc hợp
                        // đồng công khai.
                        .computePrefixWith(cacheName -> CACHE_KEY_PREFIX + cacheName + "::")
                        .entryTtl(Duration.ofMinutes(15)) // mặc định = ngắn nhất (weather_current)
                        .disableCachingNullValues()
                        .serializeKeysWith(
                                SerializationPair.fromSerializer(new StringRedisSerializer()))
                        .serializeValuesWith(
                                SerializationPair.fromSerializer(new StringRedisSerializer()));

        Map<String, RedisCacheConfiguration> byName = new HashMap<>();
        byName.put(CacheRegions.LOCATIONS, base.entryTtl(Duration.ofHours(24)));
        byName.put(CacheRegions.WEATHER_CURRENT, base.entryTtl(Duration.ofMinutes(15)));
        byName.put(CacheRegions.ACTIVE_STORMS, base.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(byName)
                .build();
    }
}
