package vn.weathergis.weather.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Point;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.weathergis.common.api.GeoJsonFeature;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.location.domain.LocationEntity;
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.weather.domain.WeatherRecordEntity;
import vn.weathergis.weather.dto.WeatherCurrentResponse;
import vn.weathergis.weather.dto.WeatherHistoryResponse;
import vn.weathergis.weather.repository.WeatherRecordQueryRepository;

@DisplayName("WeatherServiceImpl — GeoJSON assembly, severity & history (W2-07)")
@ExtendWith(MockitoExtension.class)
class WeatherServiceImplTest {

    private static final UUID HAN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DNG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final double HAN_LNG = 105.8542;
    private static final double HAN_LAT = 21.0285;
    private static final OffsetDateTime RECORDED_AT =
            OffsetDateTime.of(2026, 10, 23, 9, 0, 0, 0, ZoneOffset.UTC);

    @Mock private WeatherRecordQueryRepository weatherRecordQueryRepository;
    @Mock private LocationRepository locationRepository;

    private WeatherServiceImpl weatherService;

    @BeforeEach
    void setUp() {
        weatherService = new WeatherServiceImpl(weatherRecordQueryRepository, locationRepository);
    }

    // ------------------------------------------------------------------
    // TC-17: Phân cấp mưa theo QĐ 18/2021/QĐ-TTg — hàm thuần
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "rain_1h={0} mm → {1}")
    @CsvSource({
        "0.0, NONE",
        "0.1, MODERATE",
        "15.9, MODERATE",
        "16.0, WARNING",
        "49.9, WARNING",
        "50.0, DANGER",
        "120.0, DANGER"
    })
    @DisplayName("TC-17: classifyRainSeverity bám đúng biên 0 / 16 / 50 mm")
    void shouldClassifyRainSeverityAtBoundaries(double rain1h, String expected) {
        assertThat(WeatherServiceImpl.classifyRainSeverity(rain1h)).isEqualTo(expected);
    }

    @Test
    @DisplayName("TC-17: rain_1h null được coi là 0 mm → NONE")
    void shouldClassifyNullRainAsNone() {
        assertThat(WeatherServiceImpl.classifyRainSeverity(null)).isEqualTo("NONE");
    }

    // ------------------------------------------------------------------
    // GeoJSON current — 17 khóa properties, [lng, lat], severity
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-04/TC-13: dựng FeatureCollection với toạ độ [lng, lat] và đủ properties")
    void shouldBuildGeoJsonFeatureCollectionFromLatestRows() {
        when(weatherRecordQueryRepository.findLatestWeatherPerActiveStation())
                .thenReturn(
                        List.of(
                                new FakeLatestWeather(
                                        HAN_ID,
                                        "HAN",
                                        "Hà Nội",
                                        "Bắc Bộ",
                                        HAN_LNG,
                                        HAN_LAT,
                                        28.5,
                                        31.2,
                                        78.0,
                                        1008.0,
                                        4.5,
                                        135.0,
                                        7.2,
                                        18.5,
                                        25.5,
                                        "Rain",
                                        "mưa rào",
                                        RECORDED_AT.toInstant()),
                                new FakeLatestWeather(
                                        DNG_ID,
                                        "DNG",
                                        "Đà Nẵng",
                                        "Nam Trung Bộ",
                                        108.2022,
                                        16.0544,
                                        31.0,
                                        34.0,
                                        70.0,
                                        1005.0,
                                        6.0,
                                        90.0,
                                        9.5,
                                        0.0,
                                        0.0,
                                        "Clear",
                                        "trời trong",
                                        RECORDED_AT.minusMinutes(15).toInstant())));

        GeoJsonFeatureCollection collection = weatherService.getCurrentWeatherAsGeoJson();

        assertThat(collection.getType()).isEqualTo("FeatureCollection");
        assertThat(collection.getFeatures()).hasSize(2);

        GeoJsonFeature hanoi = collection.getFeatures().get(0);
        assertThat(hanoi.getId()).isEqualTo("HAN");
        assertThat(hanoi.getGeometry()).isInstanceOf(Point.class);

        Point point = (Point) hanoi.getGeometry();
        assertThat(point.getSRID()).isEqualTo(4326);
        assertThat(point.getX()).isCloseTo(HAN_LNG, within(1e-9)); // ST_X = KINH ĐỘ
        assertThat(point.getY()).isCloseTo(HAN_LAT, within(1e-9)); // ST_Y = VĨ ĐỘ

        assertThat(new ArrayList<>(hanoi.getProperties().keySet()))
                .containsExactly(
                        "location_id",
                        "code",
                        "name",
                        "region",
                        "temperature",
                        "feels_like",
                        "humidity",
                        "pressure",
                        "wind_speed",
                        "wind_direction",
                        "wind_gust",
                        "rain_1h",
                        "rain_3h",
                        "weather_condition",
                        "weather_description",
                        "severity_rain",
                        "recorded_at");
        assertThat(hanoi.getProperties())
                .containsEntry("location_id", HAN_ID.toString())
                .containsEntry("code", "HAN")
                .containsEntry("name", "Hà Nội")
                .containsEntry("region", "Bắc Bộ")
                .containsEntry("temperature", 28.5)
                .containsEntry("rain_1h", 18.5)
                .containsEntry("severity_rain", "WARNING")
                .containsEntry("recorded_at", RECORDED_AT);
        assertThat(hanoi.getProperties()).doesNotContainKey("raw_data");
    }

    @Test
    @DisplayName("Định dạng JSON phẳng đối xứng GeoJSON — cùng một truy vấn repository")
    void shouldBuildFlatCurrentWeatherDtos() {
        when(weatherRecordQueryRepository.findLatestWeatherPerActiveStation())
                .thenReturn(List.of(rowHan(50.0)));

        List<WeatherCurrentResponse> response = weatherService.getCurrentWeather();

        assertThat(response).hasSize(1);
        WeatherCurrentResponse hanoi = response.get(0);
        assertThat(hanoi.code()).isEqualTo("HAN");
        assertThat(hanoi.longitude()).isCloseTo(HAN_LNG, within(1e-9));
        assertThat(hanoi.latitude()).isCloseTo(HAN_LAT, within(1e-9));
        assertThat(hanoi.severityRain()).isEqualTo("DANGER");
        assertThat(hanoi.weatherDescription()).isEqualTo("mưa rào");
        assertThat(hanoi.recordedAt()).isEqualTo(RECORDED_AT);
    }

    @Test
    @DisplayName("TC-16: không có bản ghi nào → FeatureCollection rỗng (không ném lỗi)")
    void shouldReturnEmptyFeatureCollectionWhenNoRecords() {
        when(weatherRecordQueryRepository.findLatestWeatherPerActiveStation())
                .thenReturn(List.of());

        GeoJsonFeatureCollection collection = weatherService.getCurrentWeatherAsGeoJson();

        assertThat(collection.getFeatures()).isEmpty();
        assertThat(collection.getType()).isEqualTo("FeatureCollection");
    }

    // ------------------------------------------------------------------
    // History — chuẩn hóa mã, cửa sổ giờ, thứ tự tăng dần, 404
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TC-07: history chuẩn hóa 'han' → HAN, readings tăng dần theo recorded_at")
    void shouldReturnAscendingHistoryForNormalizedCode() {
        LocationEntity hanoi = locationHan();
        when(locationRepository.findByCode("HAN")).thenReturn(Optional.of(hanoi));
        when(weatherRecordQueryRepository.findHistoryByLocationId(
                        org.mockito.ArgumentMatchers.eq(HAN_ID),
                        org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(
                        List.of(
                                record(hanoi, RECORDED_AT.minusHours(2), 2.0),
                                record(hanoi, RECORDED_AT.minusHours(1), 18.5)));

        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        WeatherHistoryResponse response = weatherService.getWeatherHistory(" han ", 24);
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);

        assertThat(response.code()).isEqualTo("HAN");
        assertThat(response.name()).isEqualTo("Hà Nội");
        assertThat(response.region()).isEqualTo("Bắc Bộ");
        assertThat(response.locationId()).isEqualTo(HAN_ID);
        assertThat(response.hours()).isEqualTo(24);
        assertThat(response.count()).isEqualTo(2);
        assertThat(response.readings()).hasSize(2);
        assertThat(response.readings().get(0).recordedAt())
                .isBefore(response.readings().get(1).recordedAt());
        assertThat(response.readings().get(0).rain1h()).isEqualTo(2.0);
        assertThat(response.readings().get(1).rain1h()).isEqualTo(18.5);
        assertThat(response.from()).isBetween(before.minusHours(24), after.minusHours(24));
        assertThat(response.to()).isBetween(before, after);
    }

    @Test
    @DisplayName("TC-10: trạm hợp lệ nhưng chưa có bản ghi → readings rỗng, count 0")
    void shouldReturnEmptyHistoryWhenStationHasNoRecords() {
        LocationEntity hanoi = locationHan();
        when(locationRepository.findByCode("HAN")).thenReturn(Optional.of(hanoi));
        when(weatherRecordQueryRepository.findHistoryByLocationId(
                        org.mockito.ArgumentMatchers.eq(HAN_ID),
                        org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
                .thenReturn(List.of());

        WeatherHistoryResponse response = weatherService.getWeatherHistory("HAN", 168);

        assertThat(response.count()).isZero();
        assertThat(response.readings()).isEmpty();
    }

    @Test
    @DisplayName("TC-08: mã trạm không tồn tại → ResourceNotFoundException WTH_404_NOT_FOUND")
    void shouldThrowWeatherNotFoundForUnknownCode() {
        when(locationRepository.findByCode("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> weatherService.getWeatherHistory("XXX", 24))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResourceNotFoundException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.WEATHER_NOT_FOUND))
                .hasMessageContaining("XXX");

        verifyNoInteractions(weatherRecordQueryRepository);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private LocationEntity locationHan() {
        LocationEntity entity = new LocationEntity();
        entity.setId(HAN_ID);
        entity.setCode("HAN");
        entity.setName("Hà Nội");
        entity.setRegion("Bắc Bộ");
        return entity;
    }

    private WeatherRecordEntity record(LocationEntity location, OffsetDateTime time, double rain) {
        WeatherRecordEntity entity = new WeatherRecordEntity();
        entity.setLocation(location);
        entity.setRecordedAt(time);
        entity.setTemperature(28.5);
        entity.setFeelsLike(31.2);
        entity.setHumidity(78.0);
        entity.setPressure(1008.0);
        entity.setWindSpeed(4.5);
        entity.setWindDirection(135.0);
        entity.setWindGust(7.2);
        entity.setRain1h(rain);
        entity.setRain3h(rain * 2);
        entity.setWeatherCondition("Rain");
        entity.setWeatherDescription("mưa rào");
        return entity;
    }

    private WeatherRecordQueryRepository.LatestWeatherPerStation rowHan(double rain) {
        return new FakeLatestWeather(
                HAN_ID,
                "HAN",
                "Hà Nội",
                "Bắc Bộ",
                HAN_LNG,
                HAN_LAT,
                28.5,
                31.2,
                78.0,
                1008.0,
                4.5,
                135.0,
                7.2,
                rain,
                rain * 2,
                "Rain",
                "mưa rào",
                RECORDED_AT.toInstant());
    }

    /** Bản ghi projection giả — thay cho dòng native query trong unit test. */
    private static final class FakeLatestWeather
            implements WeatherRecordQueryRepository.LatestWeatherPerStation {

        private final UUID locationId;
        private final String code;
        private final String name;
        private final String region;
        private final double longitude;
        private final double latitude;
        private final Double temperature;
        private final Double feelsLike;
        private final Double humidity;
        private final Double pressure;
        private final Double windSpeed;
        private final Double windDirection;
        private final Double windGust;
        private final Double rain1h;
        private final Double rain3h;
        private final String weatherCondition;
        private final String weatherDescription;
        private final Instant recordedAt;

        FakeLatestWeather(
                UUID locationId,
                String code,
                String name,
                String region,
                double longitude,
                double latitude,
                Double temperature,
                Double feelsLike,
                Double humidity,
                Double pressure,
                Double windSpeed,
                Double windDirection,
                Double windGust,
                Double rain1h,
                Double rain3h,
                String weatherCondition,
                String weatherDescription,
                Instant recordedAt) {
            this.locationId = locationId;
            this.code = code;
            this.name = name;
            this.region = region;
            this.longitude = longitude;
            this.latitude = latitude;
            this.temperature = temperature;
            this.feelsLike = feelsLike;
            this.humidity = humidity;
            this.pressure = pressure;
            this.windSpeed = windSpeed;
            this.windDirection = windDirection;
            this.windGust = windGust;
            this.rain1h = rain1h;
            this.rain3h = rain3h;
            this.weatherCondition = weatherCondition;
            this.weatherDescription = weatherDescription;
            this.recordedAt = recordedAt;
        }

        @Override
        public UUID getLocationId() {
            return locationId;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getRegion() {
            return region;
        }

        @Override
        public double getLongitude() {
            return longitude;
        }

        @Override
        public double getLatitude() {
            return latitude;
        }

        @Override
        public Double getTemperature() {
            return temperature;
        }

        @Override
        public Double getFeelsLike() {
            return feelsLike;
        }

        @Override
        public Double getHumidity() {
            return humidity;
        }

        @Override
        public Double getPressure() {
            return pressure;
        }

        @Override
        public Double getWindSpeed() {
            return windSpeed;
        }

        @Override
        public Double getWindDirection() {
            return windDirection;
        }

        @Override
        public Double getWindGust() {
            return windGust;
        }

        @Override
        public Double getRain1h() {
            return rain1h;
        }

        @Override
        public Double getRain3h() {
            return rain3h;
        }

        @Override
        public String getWeatherCondition() {
            return weatherCondition;
        }

        @Override
        public String getWeatherDescription() {
            return weatherDescription;
        }

        @Override
        public Instant getRecordedAt() {
            return recordedAt;
        }
    }
}
