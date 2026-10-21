package vn.weathergis.automation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Bản đồ payload Current Weather API của OpenWeatherMap — chỉ khai báo các trường hệ thống tiêu thụ
 * (Jackson bỏ qua trường thừa). Nguồn tham chiếu: https://openweathermap.org/current
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenWeatherMapCurrentWeather(
        @JsonProperty("dt") Long observationEpochSeconds,
        @JsonProperty("main") Main main,
        @JsonProperty("wind") Wind wind,
        @JsonProperty("rain") Rain rain,
        @JsonProperty("weather") List<Weather> weather) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Main(
            @JsonProperty("temp") Double temp,
            @JsonProperty("feels_like") Double feelsLike,
            @JsonProperty("humidity") Double humidity,
            @JsonProperty("pressure") Double pressure) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Wind(
            @JsonProperty("speed") Double speed,
            @JsonProperty("deg") Double deg,
            @JsonProperty("gust") Double gust) {}

    /** OWM chỉ trả đối tượng "rain" khi đang/có mưa — nullable, mặc định 0.0 khi vắng. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rain(@JsonProperty("1h") Double oneHour, @JsonProperty("3h") Double threeHour) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Weather(
            @JsonProperty("main") String main, @JsonProperty("description") String description) {}
}
