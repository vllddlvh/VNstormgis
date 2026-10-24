/**
 * @file weather.ts
 * @description Kiểu dữ liệu thời tiết khí tượng tiêu thụ hợp đồng API W2-07
 * (GET /api/v1/weather/current GeoJSON RFC 7946 + GET /api/v1/weather/history).
 *
 * @author VNstormgis Team - Frontend Engineer
 * @version 1.0.0
 */

import { GeoJsonFeature, GeoJsonFeatureCollection } from "@/types/location";

/**
 * Phân cấp lượng mưa theo QĐ 18/2021/QĐ-TTg — phát hành sẵn bởi backend (W2-07),
 * frontend TUYỆT ĐỐI không tự phân loại lại.
 */
export type RainSeverityLevel = "NONE" | "MODERATE" | "WARNING" | "DANGER";

/**
 * properties của mỗi Feature trong FeatureCollection /weather/current.
 * Khớp 1-1 với WeatherServiceImpl#getCurrentWeatherAsGeoJson (LinkedHashMap 17 khóa).
 * Giá trị số cho phép null (cột DOUBLE PRECISION, chỉ temperature/humidity/... một phần NOT NULL).
 */
export interface WeatherProperties {
  /** UUID trạm — khóa tra cứu chéo về /locations. */
  location_id: string;
  /** Mã trạm (vd "HAN") — trùng Feature.id, UNIQUE trong collection. */
  code: string;
  name: string;
  region: string;
  /** Nhiệt độ °C (metric OWM). */
  temperature: number | null;
  feels_like: number | null;
  /** Độ ẩm %. */
  humidity: number | null;
  /** Áp suất hPa. */
  pressure: number | null;
  /** Vận tốc gió m/s. */
  wind_speed: number | null;
  /** Hướng gió độ azimuth 0–360 (0 = Bắc). */
  wind_direction: number | null;
  wind_gust: number | null;
  /** Lượng mưa 1 giờ / 3 giờ, mm (null → coi là 0.0 khi hiển thị). */
  rain_1h: number | null;
  rain_3h: number | null;
  /** Mã điều kiện OWM (vd "Rain"). */
  weather_condition: string;
  /** Mô tả điều kiện tiếng Việt (OWM weather[0].description). */
  weather_description: string;
  severity_rain: RainSeverityLevel;
  /** Thời điểm đo ISO-8601 (backend OffsetDateTime). */
  recorded_at: string;
}

/** Feature thời tiết — geometry.coordinates là [KINH ĐỘ, VĨ ĐỘ] (RFC 7946). */
export type WeatherFeature = GeoJsonFeature<WeatherProperties>;

/** FeatureCollection nạp được thẳng vào L.geoJSON (nếu cần). */
export type WeatherGeoJsonCollection = GeoJsonFeatureCollection<WeatherProperties>;

/** Một mốc đo đạc trong chuỗi thời gian history (trật tự tăng dần theo recordedAt). */
export interface WeatherReading {
  recordedAt: string;
  rain1h: number | null;
  rain3h: number | null;
  windSpeed: number | null;
  windDirection: number | null;
  windGust: number | null;
  temperature: number | null;
  feelsLike: number | null;
  humidity: number | null;
  pressure: number | null;
  weatherCondition: string;
}

/** data của ApiResponse trả về từ GET /weather/history (camelCase theo DTO backend). */
export interface WeatherHistoryData {
  locationId: string;
  code: string;
  name: string;
  region: string;
  hours: number;
  from: string;
  to: string;
  count: number;
  readings: WeatherReading[];
}
