package vn.weathergis.weather.service;

import java.util.List;
import vn.weathergis.common.api.GeoJsonFeatureCollection;
import vn.weathergis.weather.dto.WeatherCurrentResponse;
import vn.weathergis.weather.dto.WeatherHistoryResponse;

/** Hợp đồng đọc dữ liệu khí tượng của bounded context weather. */
public interface WeatherService {

    /** GeoJSON FeatureCollection RFC 7946 — bản ghi mới nhất từng trạm active. */
    GeoJsonFeatureCollection getCurrentWeatherAsGeoJson();

    /** Danh sách DTO — cho format=json. */
    List<WeatherCurrentResponse> getCurrentWeather();

    /** Chuỗi thời gian của một trạm theo mã; code không tồn tại → WTH_404_NOT_FOUND. */
    WeatherHistoryResponse getWeatherHistory(String locationCode, int hours);
}
