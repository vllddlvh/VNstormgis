package vn.weathergis.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Unit test cấu hình {@code RedisCacheManager} (Task W3-03, TC-14) — kiểm chứng nguyên văn
 * blueprint README W3 Mục 5.2 về vùng / TTL / prefix / serializer mà không cần Redis chạy thật.
 *
 * <p>Đây là lưới an toàn cho quyết định #1: value serializer PHẢI là {@code StringRedisSerializer}
 * (cache chuỗi JSON đã render), không phải {@code GenericJackson2JsonRedisSerializer}.
 */
@DisplayName(
        "RedisCacheConfig — 3 vùng TTL, prefix weathergis:cache:, StringRedisSerializer (W3-03)")
class RedisCacheConfigTest {

    private RedisCacheManager cacheManager;
    private Map<String, RedisCacheConfiguration> configurations;

    @BeforeEach
    void setUp() {
        cacheManager = new RedisCacheConfig().cacheManager(mock(RedisConnectionFactory.class));
        // RedisCacheManager chỉ tạo RedisCache từ initialCacheConfig ở afterPropertiesSet() —
        // Spring
        // gọi callback này khi khởi tạo bean, unit test phải gọi tay. Bỏ bước này thì
        // getCache(name)
        // rơi về cấu hình MẶC ĐỊNH (15m) và mọi assert TTL dưới nước.
        cacheManager.afterPropertiesSet();
        // Đọc cấu hình HIỆU DỤNG trên từng RedisCache đã tạo — đây chính là giá trị Lettuce dùng
        // khi
        // SET key, nên là mặt assertion trung thực nhất.
        configurations = new HashMap<>();
        for (String region :
                List.of(
                        CacheRegions.LOCATIONS,
                        CacheRegions.WEATHER_CURRENT,
                        CacheRegions.ACTIVE_STORMS)) {
            Cache cache = cacheManager.getCache(region);
            assertThat(cache).isInstanceOf(RedisCache.class);
            configurations.put(region, ((RedisCache) cache).getCacheConfiguration());
        }
        assertThat(configurations).hasSize(3);
    }

    @Test
    @DisplayName("TC-14a: đúng 3 vùng mang tên CacheRegions — không vùng thừa, không magic string")
    void shouldRegisterExactlyThreeRegions() {
        assertThat(cacheManager.getCacheConfigurations())
                .containsOnlyKeys(
                        CacheRegions.LOCATIONS,
                        CacheRegions.WEATHER_CURRENT,
                        CacheRegions.ACTIVE_STORMS);
        assertThat(cacheManager.getCacheNames())
                .containsExactlyInAnyOrder(
                        CacheRegions.LOCATIONS,
                        CacheRegions.WEATHER_CURRENT,
                        CacheRegions.ACTIVE_STORMS);
    }

    @Test
    @DisplayName(
            "TC-14b: TTL khớp chu kỳ sync — locations 24h, weather_current 15m, active_storms 30m")
    void shouldConfigurePerRegionTtl() {
        assertThat(configurations.get(CacheRegions.LOCATIONS).getTtl())
                .isEqualTo(Duration.ofHours(24));
        assertThat(configurations.get(CacheRegions.WEATHER_CURRENT).getTtl())
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(configurations.get(CacheRegions.ACTIVE_STORMS).getTtl())
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("TC-14c: mọi vùng bật key prefix — không vùng nào rơi về key trần")
    void shouldUsePrefixForEveryRegion() {
        for (RedisCacheConfiguration configuration : configurations.values()) {
            assertThat(configuration.usePrefix()).isTrue();
        }
    }

    @Test
    @DisplayName(
            "TC-14d: key Redis cuối cùng có dạng weathergis:cache:<vùng>::  (tiền đề assert W3-09)")
    void shouldPrefixCacheNameWithStandardPrefix() {
        for (String region : configurations.keySet()) {
            assertThat(configurations.get(region).getKeyPrefixFor(region))
                    .isEqualTo(RedisCacheConfig.CACHE_KEY_PREFIX + region + "::");
        }
    }

    @Test
    @DisplayName("TC-14e: key & value là chuỗi nguyên văn — quyết định #1, chống 3 bẫy serialize")
    @SuppressWarnings("unchecked")
    void shouldSerializeKeysAndValuesAsPlainStrings() {
        String payload = "{\"type\":\"FeatureCollection\",\"features\":[]}";
        for (RedisCacheConfiguration configuration : configurations.values()) {
            ByteBuffer keyBuffer = configuration.getKeySerializationPair().write(payload);
            byte[] valueBytes =
                    bytesOf(
                            ((SerializationPair<Object>) configuration.getValueSerializationPair())
                                    .write(payload));

            // Không @class typing, không header JDK-serialization: byte trên Redis == đúng chuỗi.
            assertThat(new String(valueBytes, StandardCharsets.UTF_8)).isEqualTo(payload);
            assertThat(configuration.getKeySerializationPair().read(ByteBuffer.wrap(valueBytes)))
                    .isEqualTo(payload);
            assertThat(keyBuffer.remaining()).isEqualTo(valueBytes.length);
        }
    }

    private static byte[] bytesOf(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    @Test
    @DisplayName("TC-14f: disableCachingNullValues — không lưu null, tránh miss ảo")
    void shouldNotCacheNullValues() {
        for (RedisCacheConfiguration configuration : configurations.values()) {
            assertThat(configuration.getAllowCacheNullValues()).isFalse();
        }
    }

    @Test
    @DisplayName("TC-14g: bean là RedisCacheManager — CacheManager bàn giao cho eviction W3-04")
    void shouldExposeCacheManagerForW304() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
        assertThat(cacheManager.getCache(CacheRegions.WEATHER_CURRENT)).isNotNull();
        assertThat(cacheManager.getCacheNames())
                .containsExactlyInAnyOrder(
                        CacheRegions.LOCATIONS,
                        CacheRegions.WEATHER_CURRENT,
                        CacheRegions.ACTIVE_STORMS);
    }

    @Test
    @DisplayName("TC-14h: prefix chuẩn là hằng số dùng được bởi báo cáo kiểm định & W3-09")
    void shouldUseDocumentedKeyPrefix() {
        assertThat(RedisCacheConfig.CACHE_KEY_PREFIX).isEqualTo("weathergis:cache:");
    }
}
