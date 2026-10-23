package vn.weathergis.weather.service.impl;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import vn.weathergis.weather.service.WeatherService;

/** Hiện thực tầng đọc dữ liệu khí tượng — GeoJSON RFC 7946 & chuỗi thời gian. */
@Service
@Transactional(readOnly = true)
public class WeatherServiceImpl implements WeatherService {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final WeatherRecordQueryRepository weatherRecordQueryRepository;
    private final LocationRepository locationRepository;

    public WeatherServiceImpl(
            WeatherRecordQueryRepository weatherRecordQueryRepository,
            LocationRepository locationRepository) {
        this.weatherRecordQueryRepository = weatherRecordQueryRepository;
        this.locationRepository = locationRepository;
    }

    @Override
    public GeoJsonFeatureCollection getCurrentWeatherAsGeoJson() {
        List<WeatherRecordQueryRepository.LatestWeatherPerStation> rows =
                weatherRecordQueryRepository.findLatestWeatherPerActiveStation();
        GeoJsonFeatureCollection collection = new GeoJsonFeatureCollection();

        for (var row : rows) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("location_id", row.getLocationId().toString());
            properties.put("code", row.getCode());
            properties.put("name", row.getName());
            properties.put("region", row.getRegion());
            properties.put("temperature", row.getTemperature());
            properties.put("feels_like", row.getFeelsLike());
            properties.put("humidity", row.getHumidity());
            properties.put("pressure", row.getPressure());
            properties.put("wind_speed", row.getWindSpeed());
            properties.put("wind_direction", row.getWindDirection());
            properties.put("wind_gust", row.getWindGust());
            properties.put("rain_1h", row.getRain1h());
            properties.put("rain_3h", row.getRain3h());
            properties.put("weather_condition", row.getWeatherCondition());
            properties.put("weather_description", row.getWeatherDescription());
            properties.put("severity_rain", classifyRainSeverity(row.getRain1h()));
            properties.put("recorded_at", toOffsetDateTime(row.getRecordedAt()));

            GeoJsonFeature feature =
                    new GeoJsonFeature(
                            row.getCode(),
                            toPoint(row.getLongitude(), row.getLatitude()),
                            properties);
            collection.addFeature(feature);
        }

        return collection;
    }

    @Override
    public List<WeatherCurrentResponse> getCurrentWeather() {
        List<WeatherRecordQueryRepository.LatestWeatherPerStation> rows =
                weatherRecordQueryRepository.findLatestWeatherPerActiveStation();

        List<WeatherCurrentResponse> responses = new ArrayList<>(rows.size());
        for (var row : rows) {
            responses.add(
                    new WeatherCurrentResponse(
                            row.getLocationId(),
                            row.getCode(),
                            row.getName(),
                            row.getRegion(),
                            row.getLongitude(),
                            row.getLatitude(),
                            row.getTemperature(),
                            row.getFeelsLike(),
                            row.getHumidity(),
                            row.getPressure(),
                            row.getWindSpeed(),
                            row.getWindDirection(),
                            row.getWindGust(),
                            row.getRain1h(),
                            row.getRain3h(),
                            row.getWeatherCondition(),
                            row.getWeatherDescription(),
                            classifyRainSeverity(row.getRain1h()),
                            toOffsetDateTime(row.getRecordedAt())));
        }
        return responses;
    }

    @Override
    public WeatherHistoryResponse getWeatherHistory(String locationCode, int hours) {
        String normalizedCode =
                locationCode == null ? "" : locationCode.trim().toUpperCase(Locale.ROOT);
        LocationEntity location =
                locationRepository
                        .findByCode(normalizedCode)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                ErrorCode.WEATHER_NOT_FOUND,
                                                "Không tìm thấy trạm: " + normalizedCode));

        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = to.minusHours(hours);
        List<WeatherRecordEntity> records =
                weatherRecordQueryRepository.findHistoryByLocationId(location.getId(), from);

        List<WeatherHistoryResponse.WeatherReading> readings =
                records.stream()
                        .map(
                                record ->
                                        new WeatherHistoryResponse.WeatherReading(
                                                record.getRecordedAt(),
                                                record.getRain1h(),
                                                record.getRain3h(),
                                                record.getWindSpeed(),
                                                record.getWindDirection(),
                                                record.getWindGust(),
                                                record.getTemperature(),
                                                record.getFeelsLike(),
                                                record.getHumidity(),
                                                record.getPressure(),
                                                record.getWeatherCondition()))
                        .toList();

        return new WeatherHistoryResponse(
                location.getId(),
                location.getCode(),
                location.getName(),
                location.getRegion(),
                hours,
                from,
                to,
                readings.size(),
                readings);
    }

    /**
     * Phân cấp mưa theo thang QĐ 18/2021/QĐ-TTg — khớp bảng màu RainfallLayer (W2-08) và ngưỡng
     * seed 50mm DANGER của V5. Hàm thuần — KHÔNG truy vấn alert_thresholds (Tuần 3).
     */
    static String classifyRainSeverity(Double rain1h) {
        double rain = rain1h == null ? 0.0 : rain1h;
        if (rain <= 0.0) {
            return "NONE";
        }
        if (rain < 16.0) {
            return "MODERATE";
        }
        if (rain < 50.0) {
            return "WARNING";
        }
        return "DANGER";
    }

    /** Dựng Point JTS chuẩn SRID 4326 — x = KINH ĐỘ, y = VĨ ĐỘ (AGENTS.md mục 1). */
    private Point toPoint(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
