package vn.weathergis.automation.client.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test thuần cho ánh xạ DTO OpenWeatherMapCurrentWeather — không mạng, không WireMock (phạm vi
 * duy nhất được phép của W2-03 theo Mục 3.10; fault-injection thuộc W2-04).
 */
@DisplayName("OpenWeatherMap Current Weather DTO Mapping Tests")
class OpenWeatherMapCurrentWeatherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName(
            "Bóc tách đầy đủ trường nghiệp vụ từ payload OWM chuẩn — bẫy @JsonProperty(\"1h\")")
    void shouldMapFullPayload() throws Exception {
        String json =
                """
                {
                  "coord": {"lon": 105.8542, "lat": 21.0285},
                  "weather": [{"id": 501, "main": "Rain", "description": "moderate rain", "icon": "10d"}],
                  "main": {"temp": 28.5, "feels_like": 32.0, "pressure": 1008, "humidity": 82},
                  "wind": {"speed": 4.5, "deg": 45, "gust": 8.2},
                  "rain": {"1h": 8.5, "3h": 12.3},
                  "clouds": {"all": 100},
                  "dt": 1750000000,
                  "sys": {"type": 2, "id": 2037050, "country": "VN"},
                  "timezone": 25200,
                  "id": 1581130,
                  "name": "Hanoi",
                  "cod": 200
                }
                """;

        OpenWeatherMapCurrentWeather dto =
                objectMapper.readValue(json, OpenWeatherMapCurrentWeather.class);

        assertThat(dto.observationEpochSeconds()).isEqualTo(1750000000L);
        assertThat(dto.main().temp()).isEqualTo(28.5);
        assertThat(dto.main().feelsLike()).isEqualTo(32.0);
        assertThat(dto.main().humidity()).isEqualTo(82.0);
        assertThat(dto.main().pressure()).isEqualTo(1008.0);
        assertThat(dto.wind().speed()).isEqualTo(4.5);
        assertThat(dto.wind().deg()).isEqualTo(45.0);
        assertThat(dto.wind().gust()).isEqualTo(8.2);
        assertThat(dto.rain().oneHour()).isEqualTo(8.5);
        assertThat(dto.rain().threeHour()).isEqualTo(12.3);
        assertThat(dto.weather().get(0).main()).isEqualTo("Rain");
        assertThat(dto.weather().get(0).description()).isEqualTo("moderate rain");
    }

    @Test
    @DisplayName("Trường rain/gust vắng mặt khi trời không mưa — null để service mặc định 0.0")
    void shouldMapPayloadWithoutRain() throws Exception {
        String json =
                """
                {
                  "coord": {"lon": 105.8542, "lat": 21.0285},
                  "weather": [{"id": 800, "main": "Clear", "description": "clear sky", "icon": "01d"}],
                  "main": {"temp": 30.1, "feels_like": 33.2, "pressure": 1005, "humidity": 70},
                  "wind": {"speed": 2.1, "deg": 180},
                  "dt": 1750000060
                }
                """;

        OpenWeatherMapCurrentWeather dto =
                objectMapper.readValue(json, OpenWeatherMapCurrentWeather.class);

        assertThat(dto.rain()).isNull();
        assertThat(dto.wind().gust()).isNull();
        assertThat(dto.main().temp()).isEqualTo(30.1);
    }

    @Test
    @DisplayName("Jackson bỏ qua mọi trường thừa của payload OWM (ignoreUnknown)")
    void shouldIgnoreUnknownFields() throws Exception {
        String json =
                """
                {
                  "coord": {"lon": 105.8542, "lat": 21.0285},
                  "weather": [{"id": 800, "main": "Clear", "description": "clear sky"}],
                  "main": {"temp": 30.1, "feels_like": 33.2, "pressure": 1005, "humidity": 70},
                  "wind": {"speed": 2.1, "deg": 180},
                  "dt": 1750000060,
                  "visibility": 10000,
                  "unexpectedFutureField": {"nested": true}
                }
                """;

        OpenWeatherMapCurrentWeather dto =
                objectMapper.readValue(json, OpenWeatherMapCurrentWeather.class);

        assertThat(dto.main().temp()).isEqualTo(30.1);
        assertThat(dto.weather()).hasSize(1);
    }
}
