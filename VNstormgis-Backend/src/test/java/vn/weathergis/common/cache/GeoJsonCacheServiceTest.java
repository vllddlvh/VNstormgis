package vn.weathergis.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import vn.weathergis.common.api.GeoJsonFeature;
import vn.weathergis.common.api.GeoJsonFeatureCollection;

/**
 * Unit test của helper cache get-or-load (Task W3-03, TC-13). Chạy không cần Spring context lẫn
 * Redis — mọi hành vi quyết định tại Mục 2 đều kiểm chứng được bằng {@link CacheManager} giả:
 *
 * <pre>
 *   công tắc enabled=false  -> loader chạy mỗi lượt, không chạm cache
 *   cache-hit                -> loader KHÔNG chạy, trả nguyên chuỗi đã lưu
 *   cache-lỗi (Redis chết)   -> fail-open: loader chạy, không ném ra ngoài, bộ đếm tăng
 *   CacheManager vắng        -> render trực tiếp (slice test / bean điều kiện tắt)
 * </pre>
 */
@DisplayName("GeoJsonCacheService — get-or-load, fail-open & render GeoJSON (W3-03)")
class GeoJsonCacheServiceTest {

    private static final String KEY = "geojson";
    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private ObjectMapper objectMapper;
    private CacheManager cacheManager;
    private Cache cache;
    private ObjectProvider<CacheManager> cacheManagerProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JtsModule());
        cacheManager = mock(CacheManager.class);
        cache = mock(Cache.class);
        cacheManagerProvider = mock(ObjectProvider.class);
    }

    @Test
    @DisplayName(
            "TC-13a: weathergis.cache.enabled=false → loader chạy mỗi lượt, không chạm CacheManager")
    void shouldLoadEveryTimeAndSkipCacheWhenDisabled() {
        GeoJsonCacheService service = service(false);
        AtomicInteger loads = new AtomicInteger();

        String first = service.getOrLoad(CacheRegions.WEATHER_CURRENT, KEY, () -> count(loads));
        String second = service.getOrLoad(CacheRegions.WEATHER_CURRENT, KEY, () -> count(loads));

        assertThat(loads).hasValue(2);
        assertThat(first).isEqualTo(second);
        verifyNoInteractions(cacheManagerProvider, cacheManager, cache);
        assertThat(service.getFallbackCount()).isZero();
    }

    @Test
    @DisplayName("TC-13b: cache-hit → trả nguyên chuỗi đã lưu, loader KHÔNG được gọi")
    void shouldReturnCachedStringWithoutLoading() {
        when(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager);
        when(cacheManager.getCache(CacheRegions.WEATHER_CURRENT)).thenReturn(cache);
        doReturn("{\"type\":\"FeatureCollection\",\"cached\":true}")
                .when(cache)
                .get(eq(KEY), any(Callable.class));
        GeoJsonCacheService service = service(true);

        String result =
                service.getOrLoad(CacheRegions.WEATHER_CURRENT, KEY, this::loaderMustNotRun);

        assertThat(result).isEqualTo("{\"type\":\"FeatureCollection\",\"cached\":true}");
        assertThat(service.getFallbackCount()).isZero();
    }

    @Test
    @DisplayName(
            "TC-13c: cache-miss → loader chạy đúng 1 lần và chuỗi đã render được đưa vào cache")
    void shouldLoadAndStoreRenderedStringOnMiss() {
        when(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager);
        when(cacheManager.getCache(CacheRegions.WEATHER_CURRENT)).thenReturn(cache);
        AtomicInteger loads = new AtomicInteger();
        // Mô phỏng Cache#get(key, valueLoader): gọi loader đúng 1 lần rồi lưu kết quả.
        doAnswer(
                        invocation -> {
                            Callable<?> valueLoader = invocation.getArgument(1);
                            return valueLoader.call();
                        })
                .when(cache)
                .get(eq(KEY), any(Callable.class));
        GeoJsonCacheService service = service(true);

        String result = service.getOrLoad(CacheRegions.WEATHER_CURRENT, KEY, () -> count(loads));

        assertThat(loads).hasValue(1);
        assertThat(result).contains("\"type\":\"FeatureCollection\"");
        verify(cache).get(eq(KEY), any(Callable.class));
        assertThat(service.getFallbackCount()).isZero();
    }

    @Test
    @DisplayName(
            "TC-13d: Redis lỗi khi thao tác cache → fail-open, loader vẫn chạy, không ném ra ngoài")
    void shouldFallOpenWhenCacheThrows() {
        when(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager);
        when(cacheManager.getCache(CacheRegions.WEATHER_CURRENT)).thenReturn(cache);
        doThrow(new IllegalStateException("Redis connection refused"))
                .when(cache)
                .get(eq(KEY), any(Callable.class));
        GeoJsonCacheService service = service(true);
        AtomicInteger loads = new AtomicInteger();

        assertThatNoException()
                .isThrownBy(
                        () -> {
                            String result =
                                    service.getOrLoad(
                                            CacheRegions.WEATHER_CURRENT, KEY, () -> count(loads));
                            assertThat(result).contains("\"type\":\"FeatureCollection\"");
                        });

        assertThat(loads).hasValue(1);
        assertThat(service.getFallbackCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName(
            "TC-13e: CacheManager vắng (ObjectProvider rỗng) → vẫn render đúng, không fallback")
    void shouldRenderWhenCacheManagerAbsent() {
        when(cacheManagerProvider.getIfAvailable()).thenReturn(null);
        GeoJsonCacheService service = service(true);

        String result = service.getOrLoad(CacheRegions.LOCATIONS, KEY, this::stormCollection);

        assertThat(result).contains("\"type\":\"FeatureCollection\"");
        assertThat(service.getFallbackCount()).isZero();
        verify(cacheManager, never()).getCache(any());
    }

    @Test
    @DisplayName("TC-13f: vùng cache chưa cấu hình (getCache trả null) → render trực tiếp")
    void shouldRenderWhenCacheNameUnknown() {
        when(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager);
        when(cacheManager.getCache("vung_khong_ton_tai")).thenReturn(null);
        GeoJsonCacheService service = service(true);

        String result = service.getOrLoad("vung_khong_ton_tai", KEY, this::stormCollection);

        assertThat(result).contains("\"type\":\"FeatureCollection\"");
        assertThat(service.getFallbackCount()).isZero();
    }

    @Test
    @DisplayName(
            "TC-13g: render bằng ObjectMapper có JtsModule — FeatureCollection + tọa độ [lng, lat]")
    void shouldRenderFeatureCollectionWithLngLatOrder() {
        GeoJsonCacheService service = service(false);

        String json = service.getOrLoad(CacheRegions.ACTIVE_STORMS, KEY, this::stormCollection);

        assertThat(json)
                .contains("\"type\":\"FeatureCollection\"")
                .contains("\"type\":\"Feature\"");
        // [lng, lat]: 116.4 (kinh độ Biển Đông) phải đứng trước 18.2 (vĩ độ).
        assertThat(json).contains("\"coordinates\":[116.4,18.2]");
        assertThat(json).contains("Bắc Bộ"); // UTF-8 không bị escape hay vỡ charset
    }

    @Test
    @DisplayName("TC-13h: khóa null trong properties được NullNode giữ lại nguyên qua render")
    void shouldKeepNullValuedPropertyOnRender() {
        GeoJsonCacheService service = service(false);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("min_pressure", NullNode.instance);

        String json =
                service.getOrLoad(
                        CacheRegions.ACTIVE_STORMS,
                        KEY,
                        () -> new GeoJsonFeatureCollection(List.of(feature(properties))));

        assertThat(json).contains("\"min_pressure\":null");
    }

    @Test
    @DisplayName("TC-13i: bộ đếm fallback tăng theo số lượt lỗi, không reset")
    void shouldCountEveryFallback() {
        when(cacheManagerProvider.getIfAvailable()).thenReturn(cacheManager);
        when(cacheManager.getCache(CacheRegions.WEATHER_CURRENT)).thenReturn(cache);
        doThrow(new IllegalStateException("Redis down"))
                .when(cache)
                .get(eq(KEY), any(Callable.class));
        GeoJsonCacheService service = service(true);

        service.getOrLoad(CacheRegions.WEATHER_CURRENT, KEY, this::stormCollection);
        service.getOrLoad(CacheRegions.WEATHER_CURRENT, KEY, this::stormCollection);
        service.getOrLoad(CacheRegions.WEATHER_CURRENT, KEY, this::stormCollection);

        assertThat(service.getFallbackCount()).isEqualTo(3L);
        verify(cache, times(3)).get(eq(KEY), any(Callable.class));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private GeoJsonCacheService service(boolean enabled) {
        return new GeoJsonCacheService(cacheManagerProvider, objectMapper, enabled);
    }

    /** Loader phải KHÔNG chạy trên đường cache-hit — chạy là fail test ngay. */
    private GeoJsonFeatureCollection loaderMustNotRun() {
        throw new AssertionError("loader phải không được gọi khi cache-hit");
    }

    private GeoJsonFeatureCollection count(AtomicInteger loads) {
        loads.incrementAndGet();
        return stormCollection();
    }

    private GeoJsonFeatureCollection stormCollection() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", "teststorm01");
        properties.put("name", "TEST STORM A");
        properties.put("region", "Bắc Bộ");
        properties.put("status", "active");
        return new GeoJsonFeatureCollection(List.of(feature(properties)));
    }

    private GeoJsonFeature feature(Map<String, Object> properties) {
        return new GeoJsonFeature(
                "teststorm01",
                GEOMETRY_FACTORY.createPoint(new Coordinate(116.4, 18.2)),
                properties);
    }
}
