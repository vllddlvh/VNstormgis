package vn.weathergis.storm.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.n52.jackson.datatype.jts.JtsModule;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.domain.StormTrackEntity;
import vn.weathergis.storm.dto.StormDetailResponse;
import vn.weathergis.storm.dto.StormSummaryResponse;
import vn.weathergis.storm.repository.StormRepository;
import vn.weathergis.storm.repository.StormTrackRepository;

/**
 * Unit test tầng đọc bão (W3-01) — không cần container: repository được mock, chứng minh logic dựng
 * GeoJSON, hợp đồng 11 khóa, quy đổi gió và nhánh 404 của StormServiceImpl.
 *
 * <p>ObjectMapper mô phỏng production: JtsModule + JavaTimeModule + {@code non_null}
 * (application.yml {@code default-property-inclusion}). Nhờ đó assertions trên JSON đo đúng văn bản
 * đi trên dây truyền, không chỉ Map trong tiến trình.
 */
class StormServiceImplTest {

    private static final String ACTIVE_ID = "teststorm01";
    private static final String DISSIPATED_ID = "teststorm02";
    private static final List<String> CONTRACT_KEYS =
            List.of(
                    "id",
                    "name",
                    "year",
                    "category",
                    "max_wind_speed",
                    "max_wind_speed_kmh",
                    "min_pressure",
                    "status",
                    "start_date",
                    "end_date",
                    "updated_at");

    /** Hợp đồng 9 khóa của mỗi Point mốc quỹ đạo — W3-02 quyết định #7. */
    private static final List<String> POINT_CONTRACT_KEYS =
            List.of(
                    "track_id",
                    "storm_id",
                    "track_type",
                    "forecast_time",
                    "wind_speed",
                    "wind_gust",
                    "pressure",
                    "category",
                    "impact_radius_km");

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 26, 9, 0, 0, 0, ZoneOffset.UTC);

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final ObjectMapper objectMapper =
            new ObjectMapper()
                    .registerModule(new JtsModule())
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final StormRepository stormRepository = mock(StormRepository.class);
    private final StormTrackRepository stormTrackRepository = mock(StormTrackRepository.class);
    private final StormServiceImpl service =
            new StormServiceImpl(stormRepository, stormTrackRepository);

    // ------------------------------------------------------------------
    // TC-12: quy đổi m/s → km/h
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("TC-12: toKmh quy đổi ×3.6 làm tròn 1 chữ số thập phân")
    class ToKmhTests {

        @ParameterizedTest
        @CsvSource({
            "40.0, 144.0",
            "17.2, 61.9",
            "0.0, 0.0",
            "60.0, 216.0",
            "16.7, 60.1",
            "82.304, 296.3",
        })
        void shouldConvertMsToKmh(double windSpeedMs, double expectedKmh) {
            assertThat(StormServiceImpl.toKmh(windSpeedMs)).isEqualTo(expectedKmh);
        }

        @Test
        @DisplayName("TC-12: tỷ lệ 3.6 đúng ở biên thấp và monoton")
        void shouldScaleMonotonically() {
            assertThat(StormServiceImpl.toKmh(1.0)).isCloseTo(3.6, within(1e-9));
            assertThat(StormServiceImpl.toKmh(33.0)).isGreaterThan(33.0);
        }
    }

    // ------------------------------------------------------------------
    // TC-04 / TC-16: dựng GeoJSON từ entity
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("TC-04/TC-16: GeoJSON assembly — 11 khóa theo trật tự, geometry thẳng từ entity")
    class GeoJsonAssemblyTests {

        @Test
        @DisplayName(
                "TC-04: properties đúng 11 khóa theo trật tự hợp đồng, không raw_metadata/code")
        void shouldExposeElevenPropertyKeysInOrder() throws Exception {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of(activeStorm(), dissipatedStorm()));

            GeoJsonFeatureCollection collection = service.getStormsAsGeoJson("active");
            JsonNode root = jsonOf(collection);
            JsonNode feature = root.get("features").get(0);
            Map<String, Object> props = collection.getFeatures().get(0).getProperties();

            assertThat(new ArrayList<>(props.keySet())).containsExactlyElementsOf(CONTRACT_KEYS);
            // Chứng cứ mạnh hơn Map: kể cả với non_null toàn cục, wire JSON vẫn đủ 11 khóa
            assertThat(keysOf(root.get("features").get(0).get("properties")))
                    .containsExactlyElementsOf(CONTRACT_KEYS);

            assertThat(root.get("type").asText()).isEqualTo("FeatureCollection");
            assertThat(root.has("success")).as("GeoJSON trả thô, không envelope").isFalse();
            assertThat(root.get("features")).hasSize(2);
            assertThat(feature.get("type").asText()).isEqualTo("Feature");
            assertThat(feature.get("id").asText()).isEqualTo(ACTIVE_ID);
            assertThat(props.get("id")).isEqualTo(ACTIVE_ID);
            assertThat(props).doesNotContainKey("raw_metadata");
            assertThat(props).doesNotContainKey("code");
            assertThat(props).doesNotContainKey("central_pressure");
            assertThat(props.get("name")).isEqualTo("TEST STORM A");
            assertThat(props.get("year")).isEqualTo(2026);
            assertThat(props.get("status")).isEqualTo("active");
        }

        @Test
        @DisplayName("TC-11: geometry đi thẳng từ entity — coordinates [ST_X=lng, ST_Y=lat]")
        void shouldSerializeGeometryAsLngLatPoint() throws Exception {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of(activeStorm()));

            JsonNode geometry =
                    jsonOf(service.getStormsAsGeoJson("active"))
                            .get("features")
                            .get(0)
                            .get("geometry");
            JsonNode coordinates = geometry.get("coordinates");

            assertThat(geometry.get("type").asText()).isEqualTo("Point");
            assertThat(coordinates.get(0).asDouble()).isEqualTo(116.4); // KINH ĐỘ
            assertThat(coordinates.get(1).asDouble()).isEqualTo(18.2); // VĨ ĐỘ
            assertThat(coordinates.get(0).asDouble())
                    .as("Hoán đổi lat/lng sẽ đẩy tâm bão sang nửa bán cầu khác")
                    .isGreaterThan(coordinates.get(1).asDouble());
        }

        @Test
        @DisplayName("TC-12: max_wind_speed giữ m/s nguyên bản, max_wind_speed_kmh = ×3.6")
        void shouldPublishBothWindUnits() {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of(activeStorm()));

            Map<String, Object> props =
                    service.getStormsAsGeoJson("active").getFeatures().get(0).getProperties();

            assertThat((Double) props.get("max_wind_speed")).isCloseTo(40.0, within(1e-9));
            assertThat((Double) props.get("max_wind_speed_kmh")).isCloseTo(144.0, within(1e-9));
        }

        @Test
        @DisplayName("TC-13 / SYS-RULE-004: min_pressure null → JSON null, không điền 0 hay 1013")
        void shouldKeepNullMinPressureHonest() throws Exception {
            StormEntity noPressure = activeStorm();
            noPressure.setMinPressure(null);
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of(noPressure));

            JsonNode json =
                    jsonOf(service.getStormsAsGeoJson("active"))
                            .get("features")
                            .get(0)
                            .get("properties");

            // Service phải truyền null tường minh — không 0, không 1013, không bất kỳ số giả nào.
            assertThat(json.has("min_pressure"))
                    .as("khóa phải hiện diện, không bị non_null bỏ mất")
                    .isTrue();
            assertThat(json.get("min_pressure").isNull()).isTrue();
        }

        @Test
        @DisplayName("TC-13: dissipated thiếu áp suất vẫn được list hóa, end_date có giá trị")
        void shouldListStormWithoutPressure() throws Exception {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("dissipated"))
                    .thenReturn(List.of(dissipatedStorm()));

            JsonNode json =
                    jsonOf(service.getStormsAsGeoJson("dissipated"))
                            .get("features")
                            .get(0)
                            .get("properties");

            assertThat(keysOf(json)).containsExactlyElementsOf(CONTRACT_KEYS);
            assertThat(json.get("min_pressure").isNull()).isTrue();
            assertThat(json.get("end_date").isNull()).isFalse();
        }

        @Test
        @DisplayName("TC-04: bão active còn sống → end_date null tường minh trên wire, đủ 11 khóa")
        void shouldEmitNullEndDateForLiveStorm() throws Exception {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of(activeStorm()));

            JsonNode json =
                    jsonOf(service.getStormsAsGeoJson("active"))
                            .get("features")
                            .get(0)
                            .get("properties");

            assertThat(json.get("end_date").isNull()).isTrue();
            assertThat(keysOf(json)).containsExactlyElementsOf(CONTRACT_KEYS);
        }

        @Test
        @DisplayName("TC-10: repository rỗng → FeatureCollection rỗng, không ném ngoại lệ")
        void shouldReturnEmptyCollectionWhenNoStorm() {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of());

            GeoJsonFeatureCollection collection = service.getStormsAsGeoJson("active");

            assertThat(collection.getType()).isEqualTo("FeatureCollection");
            assertThat(collection.getFeatures()).isEmpty();
        }

        @Test
        @DisplayName("TC-05: status=all đi query không lọc; dissipated đi query filter status")
        void shouldRouteAllToUnfilteredQuery() {
            when(stormRepository.findAllByOrderByUpdatedAtDesc())
                    .thenReturn(List.of(activeStorm(), dissipatedStorm()));
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("dissipated"))
                    .thenReturn(List.of(dissipatedStorm()));

            assertThat(service.getStormsAsGeoJson("all").getFeatures()).hasSize(2);
            assertThat(service.getStormsAsGeoJson("dissipated").getFeatures()).hasSize(1);

            verify(stormRepository, times(1)).findAllByOrderByUpdatedAtDesc();
            verify(stormRepository, times(1))
                    .findByStatusIgnoreCaseOrderByUpdatedAtDesc("dissipated");
            verifyNoMoreInteractions(stormRepository);
        }

        @Test
        @DisplayName("TC-16: mỗi lời gọi list đúng 1 truy vấn repository — không N+1")
        void shouldQueryRepositoryOncePerListCall() {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of(activeStorm(), dissipatedStorm()));

            service.getStormsAsGeoJson("active");
            service.getStorms("active");

            ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
            verify(stormRepository, times(2))
                    .findByStatusIgnoreCaseOrderByUpdatedAtDesc(status.capture());
            assertThat(status.getAllValues()).containsExactly("active", "active");
            verifyNoMoreInteractions(stormRepository);
            verifyNoInteractions(stormTrackRepository); // list không đếm quỹ đạo
        }

        @Test
        @DisplayName("TC-05: mặc định lọc status='active' — nhánh khẩn cấp không thấy bão đã tan")
        void shouldFilterActiveByDefault() {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of(activeStorm()));

            service.getStorms("active");

            verify(stormRepository).findByStatusIgnoreCaseOrderByUpdatedAtDesc("active");
            verify(stormRepository, never()).findAllByOrderByUpdatedAtDesc();
        }
    }

    // ------------------------------------------------------------------
    // TC-07 / TC-08 / TC-09: DTO và nhánh 404
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("TC-07/TC-08/TC-09: DTO summary, detail và 404")
    class DtoTests {

        @Test
        @DisplayName("TC-07: getStorms map đủ 11 trường DTO camelCase kèm kmh quy đổi")
        void shouldMapSummaryDto() {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of(activeStorm(), dissipatedStorm()));

            List<StormSummaryResponse> result = service.getStorms("active");

            assertThat(result).hasSize(2);
            StormSummaryResponse first = result.get(0);
            assertThat(first.id()).isEqualTo(ACTIVE_ID);
            assertThat(first.name()).isEqualTo("TEST STORM A");
            assertThat(first.year()).isEqualTo(2026);
            assertThat(first.category()).isEqualTo("Typhoon");
            assertThat(first.maxWindSpeed()).isEqualTo(40.0);
            assertThat(first.maxWindSpeedKmh()).isEqualTo(144.0);
            assertThat(first.minPressure()).isEqualTo(960.0);
            assertThat(first.status()).isEqualTo("active");
            assertThat(first.startDate()).isEqualTo(NOW.minusDays(3));
            assertThat(first.endDate()).isNull();
            assertThat(first.updatedAt()).isEqualTo(NOW);
            assertThat(result.get(1).minPressure()).as("TC-13 null giữ null").isNull();
        }

        @Test
        @DisplayName("TC-07: DTO summary serialize với non_null vẫn giữ khóa minPressure/endDate")
        void shouldKeepNullDtoFieldsOnWire() throws Exception {
            when(stormRepository.findByStatusIgnoreCaseOrderByUpdatedAtDesc("active"))
                    .thenReturn(List.of(activeStorm(), dissipatedStorm()));

            JsonNode json =
                    objectMapper
                            .readTree(objectMapper.writeValueAsString(service.getStorms("active")))
                            .get(0);

            assertThat(json.has("minPressure")).isTrue();
            assertThat(json.has("endDate")).isTrue();
            assertThat(json.get("endDate").isNull()).isTrue();
            assertThat(json.get("minPressure").isNull()).as("active có áp suất thật").isFalse();
        }

        @Test
        @DisplayName(
                "TC-08: detail dùng getX()=longitude, getY()=latitude và đếm quỹ đạo đúng 1 lần")
        void shouldBuildDetailWithFlatCoordinates() {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.countByStormId(ACTIVE_ID)).thenReturn(3L);

            StormDetailResponse detail = service.getStormDetail(ACTIVE_ID);

            assertThat(detail.id()).isEqualTo(ACTIVE_ID);
            assertThat(detail.longitude()).isCloseTo(116.4, within(1e-9)); // KINH ĐỘ
            assertThat(detail.latitude()).isCloseTo(18.2, within(1e-9)); // VĨ ĐỘ
            assertThat(detail.longitude()).isGreaterThan(detail.latitude());
            assertThat(detail.maxWindSpeed()).isEqualTo(40.0);
            assertThat(detail.maxWindSpeedKmh()).isEqualTo(144.0);
            assertThat(detail.minPressure()).isEqualTo(960.0);
            assertThat(detail.totalTrackPoints()).isEqualTo(3L);
            assertThat(detail.status()).isEqualTo("active");
            assertThat(detail.endDate()).isNull();

            verify(stormRepository, times(1)).findById(ACTIVE_ID);
            verify(stormTrackRepository, times(1)).countByStormId(ACTIVE_ID);
            verifyNoMoreInteractions(stormRepository, stormTrackRepository); // 2 truy vấn chặt
        }

        @Test
        @DisplayName("TC-09: id không tồn tại → ResourceNotFoundException mang STM_404_NOT_FOUND")
        void shouldThrowStormNotFoundForUnknownId() {
            when(stormRepository.findById("khong-ton-tai-999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getStormDetail("khong-ton-tai-999"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy bão: khong-ton-tai-999")
                    .satisfies(
                            throwable -> {
                                ErrorCode code =
                                        ((ResourceNotFoundException) throwable).getErrorCode();
                                assertThat(code).isEqualTo(ErrorCode.STORM_NOT_FOUND);
                                assertThat(code.getCode()).isEqualTo("STM_404_NOT_FOUND");
                                assertThat(code.getHttpStatus().value()).isEqualTo(404);
                            });

            verifyNoInteractions(stormTrackRepository); // 404 ngắn trước, không đếm quỹ đạo
        }

        @Test
        @DisplayName("TC-08: bão chưa có mốc quỹ đạo → totalTrackPoints = 0")
        void shouldReportZeroTrackPoints() {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.countByStormId(ACTIVE_ID)).thenReturn(0L);

            assertThat(service.getStormDetail(ACTIVE_ID).totalTrackPoints()).isZero();
        }
    }

    // ------------------------------------------------------------------
    // TC-15: assembly quỹ đạo (W3-02)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("TC-15: getStormTrack — 4 loại feature, đỉnh nối, polygon trung thực, 2 truy vấn")
    class TrackAssemblyTests {

        @Test
        @DisplayName(
                "TC-04: trật tự cố định track-history → track-forecast → impact-zone → Point từng mốc")
        void shouldEmitFourFeatureKindsInFixedOrder() throws Exception {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(threeHistoricalTwoForecastWithPolygon());

            JsonNode root = jsonOf(service.getStormTrack(ACTIVE_ID));
            JsonNode features = root.get("features");

            assertThat(root.get("type").asText()).isEqualTo("FeatureCollection");
            assertThat(root.has("success")).as("GeoJSON trả thô, không envelope").isFalse();
            assertThat(idsOf(features))
                    .containsExactly(
                            "track-history",
                            "track-forecast",
                            "impact-zone",
                            "track-point-1",
                            "track-point-2",
                            "track-point-3",
                            "track-point-4",
                            "track-point-5");
            assertThat(features.get(0).get("geometry").get("type").asText())
                    .isEqualTo("LineString");
            assertThat(features.get(1).get("geometry").get("type").asText())
                    .isEqualTo("LineString");
            assertThat(features.get(2).get("geometry").get("type").asText()).isEqualTo("Polygon");
            for (int i = 3; i < features.size(); i++) {
                assertThat(features.get(i).get("geometry").get("type").asText()).isEqualTo("Point");
            }
            assertThat(keysOf(features.get(0).get("properties")))
                    .containsExactly("kind", "storm_id", "points");
            assertThat(features.get(0).get("properties").get("kind").asText()).isEqualTo("history");
            assertThat(features.get(1).get("properties").get("kind").asText())
                    .isEqualTo("forecast");
            assertThat(features.get(2).get("properties").get("kind").asText()).isEqualTo("impact");
        }

        @Test
        @DisplayName("TC-05: lịch sử 3 mốc nối theo forecast_time ASC — [lng, lat] không hoán đổi")
        void shouldBuildHistoryLineInTimeOrder() throws Exception {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(threeHistoricalTwoForecastWithPolygon());

            JsonNode coordinates =
                    jsonOf(service.getStormTrack(ACTIVE_ID))
                            .get("features")
                            .get(0)
                            .get("geometry")
                            .get("coordinates");

            assertThat(coordinates).hasSize(3);
            assertThat(coordinates.get(0).get(0).asDouble()).isEqualTo(116.0); // KINH ĐỘ
            assertThat(coordinates.get(0).get(1).asDouble()).isEqualTo(18.0); // VĨ ĐỘ
            assertThat(coordinates.get(1).get(0).asDouble()).isEqualTo(114.0);
            assertThat(coordinates.get(2).get(0).asDouble()).isEqualTo(112.0);
            assertThat(coordinates.get(0).get(0).asDouble())
                    .as("Đảo lat/lng đưa quỹ đạo sang nửa bán cầu khác")
                    .isGreaterThan(coordinates.get(0).get(1).asDouble());
        }

        @Test
        @DisplayName(
                "TC-04/quyết định #4: đỉnh nối = mốc historical cuối, points chỉ đếm mốc forecast")
        void shouldPrependJunctionWithoutCountingItInPoints() throws Exception {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(threeHistoricalTwoForecastWithPolygon());

            JsonNode forecast = jsonOf(service.getStormTrack(ACTIVE_ID)).get("features").get(1);

            assertThat(forecast.get("geometry").get("coordinates")).hasSize(3);
            assertThat(forecast.get("geometry").get("coordinates").get(0).get(0).asDouble())
                    .as("đỉnh nối lấy từ mốc historical cuối (112.0), không phải geom_current")
                    .isEqualTo(112.0);
            assertThat(forecast.get("geometry").get("coordinates").get(1).get(0).asDouble())
                    .isEqualTo(110.0);
            assertThat(forecast.get("geometry").get("coordinates").get(2).get(0).asDouble())
                    .isEqualTo(108.0);
            assertThat(forecast.get("properties").get("points").asInt())
                    .as("Đỉnh nối là chi tiết render — không tính vào số mốc")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("quyết định #4: không có mốc historical → đỉnh nối fallback geom_current")
        void shouldFallBackToStormCenterForJunction() throws Exception {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(
                            List.of(
                                    track(4, "forecast", NOW.plusHours(6), 110.0, 20.5),
                                    track(5, "forecast", NOW.plusHours(12), 108.0, 21.0)));

            JsonNode root = jsonOf(service.getStormTrack(ACTIVE_ID));

            assertThat(idsOf(root.get("features")))
                    .as("chỉ nhánh forecast nên LineString lịch sử vắng mặt")
                    .containsExactly("track-forecast", "track-point-4", "track-point-5");
            JsonNode coordinates = root.get("features").get(0).get("geometry").get("coordinates");
            assertThat(coordinates.get(0).get(0).asDouble())
                    .as("đỉnh nối = tâm bão hiện tại 116.4 từ storms.geom_current")
                    .isEqualTo(116.4);
            assertThat(coordinates.get(0).get(1).asDouble()).isEqualTo(18.2);
        }

        @Test
        @DisplayName("TC-11 / quyết định #14: 1 mốc duy nhất → không LineString, vẫn đủ Point")
        void shouldSkipLineStringWhenOnlyOneTrackOfKind() throws Exception {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(List.of(track(1, "historical", NOW, 116.0, 18.0)));

            JsonNode root = jsonOf(service.getStormTrack(ACTIVE_ID));

            assertThat(idsOf(root.get("features")))
                    .as("JTS LineString yêu cầu ≥ 2 đỉnh — không vá bằng đỉnh giả")
                    .containsExactly("track-point-1");
        }

        @Test
        @DisplayName("TC-10 / quyết định #13: bão tồn tại 0 mốc → collection rỗng, không ngoại lệ")
        void shouldReturnEmptyCollectionWhenStormHasNoTrack() {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(List.of());

            GeoJsonFeatureCollection collection = service.getStormTrack(ACTIVE_ID);

            assertThat(collection.getType()).isEqualTo("FeatureCollection");
            assertThat(collection.getFeatures()).isEmpty();
        }

        @Test
        @DisplayName(
                "TC-09: id không tồn tại → 404 STM_404_NOT_FOUND, không chạm repository quỹ đạo")
        void shouldThrowStormNotFoundBeforeTouchingTrackRepository() {
            when(stormRepository.findById("khong-ton-tai-999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getStormTrack("khong-ton-tai-999"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy bão: khong-ton-tai-999")
                    .satisfies(
                            throwable ->
                                    assertThat(
                                                    ((ResourceNotFoundException) throwable)
                                                            .getErrorCode()
                                                            .getCode())
                                            .isEqualTo("STM_404_NOT_FOUND"));

            verifyNoInteractions(stormTrackRepository);
        }

        @Test
        @DisplayName("TC-08 / quyết định #9: không mốc nào có polygon → không feature impact-zone")
        void shouldOmitImpactFeatureWhenNoPolygonExists() throws Exception {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(
                            List.of(
                                    track(1, "historical", NOW, 116.0, 18.0),
                                    track(2, "historical", NOW.plusHours(6), 114.0, 19.0)));

            JsonNode root = jsonOf(service.getStormTrack(ACTIVE_ID));

            assertThat(idsOf(root.get("features")))
                    .as("Tuyệt đối không bịa vùng ảnh hưởng từ bán kính default")
                    .doesNotContain("impact-zone");
        }

        @Test
        @DisplayName("quyết định #11: nhiều mốc có polygon → chỉ mốc forecast_time lớn nhất thắng")
        void shouldPickImpactPolygonOfLatestForecastTime() throws Exception {
            StormTrackEntity older = track(1, "historical", NOW.minusHours(6), 116.0, 18.0);
            older.setImpactPolygon(envelope(115.0, 17.0, 117.0, 19.0));
            older.setImpactRadiusKm(80.0);
            StormTrackEntity newer = track(2, "historical", NOW, 114.0, 19.0);
            newer.setImpactPolygon(envelope(113.0, 18.0, 115.0, 20.0));
            newer.setImpactRadiusKm(150.0);
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(List.of(older, newer));

            JsonNode root = jsonOf(service.getStormTrack(ACTIVE_ID));
            List<String> ids = idsOf(root.get("features"));

            assertThat(ids).filteredOn("impact-zone"::equals).hasSize(1);
            JsonNode impact = root.get("features").get(ids.indexOf("impact-zone"));
            assertThat(impact.get("geometry").get("coordinates").get(0).get(0).get(0).asDouble())
                    .as("vùng nguy hiểm là polygon tại thời điểm mới nhất, không phải mốc cũ")
                    .isEqualTo(113.0);
            assertThat(impact.get("properties").get("radius_km").asDouble())
                    .as("radius_km đọc từ chính mốc thắng, không phải hằng số")
                    .isEqualTo(150.0);
        }

        @Test
        @DisplayName(
                "TC-07 / SYS-RULE-004 + D-1: wind/gust/pressure NULL → khóa hiện diện giá trị null")
        void shouldKeepNullWindFieldsOnWireAsExplicitNull() throws Exception {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(
                            List.of(
                                    track(1, "historical", NOW, 116.0, 18.0),
                                    track(2, "historical", NOW.plusHours(6), 114.0, 19.0)));

            List<JsonNode> points = pointFeatures(jsonOf(service.getStormTrack(ACTIVE_ID)));
            assertThat(points).hasSize(2);

            for (JsonNode props : points.stream().map(f -> f.get("properties")).toList()) {
                assertThat(keysOf(props)).containsExactlyElementsOf(POINT_CONTRACT_KEYS);
                for (String nullableKey : List.of("wind_speed", "wind_gust", "pressure")) {
                    assertThat(props.has(nullableKey))
                            .as(
                                    "%s phải hiện diện nhờ NullNode, không bị non_null nuốt",
                                    nullableKey)
                            .isTrue();
                    assertThat(props.get(nullableKey).isNull())
                            .as(
                                    "%s phải null, không phải số bịa: %s",
                                    nullableKey, props.get(nullableKey))
                            .isTrue();
                }
                assertThat(props.get("category").isNull()).as("category NOT NULL").isFalse();
                assertThat(props.get("impact_radius_km").isNull())
                        .as("impact_radius_km NOT NULL")
                        .isFalse();
            }
        }

        @Test
        @DisplayName("TC-06: Point historical trước, forecast sau, mỗi mốc đúng 1 lần")
        void shouldEmitEachTrackOnceHistoricalThenForecast() throws Exception {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(threeHistoricalTwoForecastWithPolygon());

            JsonNode root = jsonOf(service.getStormTrack(ACTIVE_ID));
            List<String> trackTypes = new ArrayList<>();
            root.get("features")
                    .forEach(
                            f -> {
                                JsonNode type = f.get("properties").get("track_type");
                                if (type != null) {
                                    trackTypes.add(type.asText());
                                }
                            });

            assertThat(trackTypes)
                    .containsExactly(
                            "historical", "historical", "historical", "forecast", "forecast");
            assertThat(idsOf(root.get("features")))
                    .filteredOn(id -> id.startsWith("track-point-"))
                    .hasSize(5);
        }

        @Test
        @DisplayName(
                "quyết định #16 / anti-pattern #5: 2 truy vấn chặt, không chạm proxy LAZY storm")
        void shouldQueryExactlyTwiceAndNeverTouchLazyStorm() {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(threeHistoricalTwoForecastWithPolygon());

            // storm == null trên mọi track: chạm track.getStorm() sẽ NPE tại đây
            service.getStormTrack(ACTIVE_ID);

            verify(stormRepository, times(1)).findById(ACTIVE_ID);
            verify(stormTrackRepository, times(1)).findByStormIdOrderByForecastTimeAsc(ACTIVE_ID);
            verifyNoMoreInteractions(stormRepository, stormTrackRepository);
        }

        @Test
        @DisplayName("quyết định #16: id bão truyền xuống truy vấn quỹ đạo nguyên văn, không count")
        void shouldPassStormIdToTrackQuery() {
            when(stormRepository.findById(ACTIVE_ID)).thenReturn(Optional.of(activeStorm()));
            when(stormTrackRepository.findByStormIdOrderByForecastTimeAsc(ACTIVE_ID))
                    .thenReturn(List.of());

            service.getStormTrack(ACTIVE_ID);

            ArgumentCaptor<String> id = ArgumentCaptor.forClass(String.class);
            verify(stormTrackRepository).findByStormIdOrderByForecastTimeAsc(id.capture());
            assertThat(id.getValue()).isEqualTo(ACTIVE_ID);
            verify(stormTrackRepository, never()).countByStormId(ACTIVE_ID);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private JsonNode jsonOf(GeoJsonFeatureCollection collection) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(collection));
    }

    private static List<String> keysOf(JsonNode properties) {
        List<String> keys = new ArrayList<>();
        properties.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static List<String> idsOf(JsonNode features) {
        List<String> ids = new ArrayList<>();
        features.forEach(f -> ids.add(f.get("id").asText()));
        return ids;
    }

    /** Chỉ các feature Point mốc quỹ đạo — LineString/Polygon cũng là feature hợp lệ. */
    private static List<JsonNode> pointFeatures(JsonNode root) {
        List<JsonNode> points = new ArrayList<>();
        root.get("features")
                .forEach(
                        f -> {
                            if (f.get("id").asText().startsWith("track-point-")) {
                                points.add(f);
                            }
                        });
        return points;
    }

    /** Mốc quỹ đạo thuần: storm == null để phát hiện mọi nỗ lực chạm proxy LAZY. */
    private StormTrackEntity track(
            long id, String trackType, OffsetDateTime at, double lng, double lat) {
        StormTrackEntity t = new StormTrackEntity();
        t.setId(id);
        t.setTrackType(trackType);
        t.setForecastTime(at);
        t.setGeom(point(lng, lat));
        t.setWindSpeed(null); // V6 — GDACS không công bố theo mốc
        t.setWindGust(null);
        t.setPressure(null);
        t.setCategory("HU");
        t.setImpactRadiusKm(100.0);
        return t;
    }

    /** 3 mốc historical + 2 mốc forecast, polygon chỉ gắn ở mốc historical cuối. */
    private List<StormTrackEntity> threeHistoricalTwoForecastWithPolygon() {
        StormTrackEntity h1 = track(1, "historical", NOW.minusHours(12), 116.0, 18.0);
        StormTrackEntity h2 = track(2, "historical", NOW.minusHours(6), 114.0, 19.0);
        StormTrackEntity h3 = track(3, "historical", NOW, 112.0, 20.0);
        h3.setImpactPolygon(envelope(110.0, 18.0, 114.0, 22.0));
        StormTrackEntity f1 = track(4, "forecast", NOW.plusHours(6), 110.0, 20.5);
        StormTrackEntity f2 = track(5, "forecast", NOW.plusHours(12), 108.0, 21.0);
        return List.of(h1, h2, h3, f1, f2);
    }

    private Polygon envelope(double minLng, double minLat, double maxLng, double maxLat) {
        return geometryFactory.createPolygon(
                new Coordinate[] {
                    new Coordinate(minLng, minLat),
                    new Coordinate(maxLng, minLat),
                    new Coordinate(maxLng, maxLat),
                    new Coordinate(minLng, maxLat),
                    new Coordinate(minLng, minLat),
                });
    }

    private StormEntity activeStorm() {
        StormEntity storm = new StormEntity();
        storm.setId(ACTIVE_ID);
        storm.setName("TEST STORM A");
        storm.setYear(2026);
        storm.setCategory("Typhoon");
        storm.setMaxWindSpeed(40.0);
        storm.setMinPressure(960.0);
        storm.setStatus("active");
        storm.setStartDate(NOW.minusDays(3));
        storm.setEndDate(null);
        storm.setGeomCurrent(point(116.4, 18.2));
        storm.setUpdatedAt(NOW);
        storm.setRawMetadata("{\"gdacs\":\"payload nội bộ\"}");
        return storm;
    }

    private StormEntity dissipatedStorm() {
        StormEntity storm = new StormEntity();
        storm.setId(DISSIPATED_ID);
        storm.setName("TEST STORM B");
        storm.setYear(2025);
        storm.setCategory("Tropical Storm");
        storm.setMaxWindSpeed(15.0);
        storm.setMinPressure(null);
        storm.setStatus("dissipated");
        storm.setStartDate(NOW.minusDays(300));
        storm.setEndDate(NOW.minusDays(280));
        storm.setGeomCurrent(point(112.0, 15.0));
        storm.setUpdatedAt(NOW.minusDays(1));
        return storm;
    }

    private Point point(double lng, double lat) {
        return geometryFactory.createPoint(new Coordinate(lng, lat));
    }
}
