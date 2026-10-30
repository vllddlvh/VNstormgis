package vn.weathergis.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import vn.weathergis.common.api.GeoJsonFeatureCollection;

/**
 * Helper cache get-or-load cho tầng đọc GeoJSON (W3-03).
 *
 * <p>Cache CHUỖI JSON đã render bằng chính {@link ObjectMapper} Spring đang serialize payload qua
 * MVC (quyết định #1/#15) — cache-hit trả về nguyên chuỗi, bảo toàn byte-identical hợp đồng RFC
 * 7946 (kể cả NullNode D-1 và tọa độ [lng, lat]).
 *
 * <p>Fail-open (quyết định #5): mọi thao tác cache nằm trong try/catch — Redis mất kết nối chỉ sản
 * xuất WARN + bộ đếm fallback, request vẫn được phục vụ trực tiếp từ PostGIS. Cache không bao giờ
 * là nguyên nhân API 500. Lỗi render và lỗi loader thì KHÔNG bị nuốt (anti-pattern #3/#4).
 */
@Component
public class GeoJsonCacheService {

    private static final Logger log = LoggerFactory.getLogger(GeoJsonCacheService.class);

    private final ObjectProvider<CacheManager> cacheManagerProvider;
    private final ObjectMapper objectMapper;
    private final boolean cacheEnabled;

    private final Counter fallbackCount;

    public GeoJsonCacheService(
            ObjectProvider<CacheManager> cacheManagerProvider,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${weathergis.cache.enabled:true}") boolean cacheEnabled) {
        this.cacheManagerProvider = cacheManagerProvider;
        this.objectMapper = objectMapper;
        this.fallbackCount = meterRegistry.counter("weathergis.cache.fallback");
        this.cacheEnabled = cacheEnabled;
    }

    /**
     * Lấy chuỗi JSON GeoJSON từ vùng cache {@code cacheName} dưới key {@code key}; cache-miss thì
     * load qua {@code loader}, render đúng 1 lần rồi ghi vào cache. Redis sai sự → WARN + đếm
     * fallback + render trực tiếp (fail-open).
     *
     * <p>Ghi chú: nếu Redis chết ngay sau lượt GET-miss thành công (PUT thất bại), loader có thể
     * chạy 2 lần — chấp nhận được vì loader thuần đọc, idempotent.
     */
    public String getOrLoad(
            String cacheName, Object key, Supplier<GeoJsonFeatureCollection> loader) {
        if (cacheEnabled) {
            CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
            if (cacheManager != null) {
                Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    try {
                        return cache.get(key, () -> render(loader.get()));
                    } catch (Exception ex) {
                        fallbackCount.increment();
                        long n = (long) fallbackCount.count();
                        log.warn(
                                "[CACHE-FALLBACK#{}] Redis vùng '{}' không dùng được (key={}) — "
                                        + "truy vấn trực tiếp PostGIS: {}",
                                n,
                                cacheName,
                                key,
                                ex.getMessage());
                        // rơi xuống render trực tiếp bên dưới
                    }
                }
            }
        }
        return render(loader.get());
    }

    /** Render bằng ObjectMapper Spring (JtsModule + NullNode + OffsetDateTime) — quyết định #15. */
    private String render(GeoJsonFeatureCollection collection) {
        try {
            return objectMapper.writeValueAsString(collection);
        } catch (JsonProcessingException ex) {
            // Lỗi render là bug nghiệp vụ, KHÔNG nuốt — fail loud đúng GlobalExceptionHandler.
            throw new IllegalStateException("Serialize GeoJSON thất bại", ex);
        }
    }

    /** Số lượt fallback từ khi khởi động. */
    public long getFallbackCount() {
        return (long) fallbackCount.count();
    }
}
