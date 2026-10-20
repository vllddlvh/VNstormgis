/**
 * @file map.ts
 * @description Định nghĩa các kiểu dữ liệu địa lý trắc địa, phân cấp rủi ro thiên tai và cấu hình bản đồ WebGIS.
 *
 * @author VNstormgis Team - Frontend & GIS Specialist
 * @version 1.0.0
 */

/**
 * Kiểu tọa độ trắc địa hiển thị của Leaflet: [Vĩ độ (Latitude), Kinh độ (Longitude)]
 * Phạm vi hợp lệ tại Việt Nam: Lat [8.0, 24.5], Lng [100.0, 118.0]
 */
export type CoordinateTuple = [latitude: number, longitude: number];

/**
 * Kiểu tọa độ hình học chuẩn GeoJSON (RFC 7946): [Kinh độ (Longitude), Vĩ độ (Latitude)]
 * Bắt buộc tuân thủ theo chuẩn PostGIS và JTS Backend.
 */
export type GeoJsonCoordinate = [longitude: number, latitude: number];

/**
 * Các cấp độ rủi ro khí tượng thủy văn phục vụ hiển thị biểu tượng trạm cảnh báo:
 * - SAFE: Cấp 1 - Bình thường / An toàn
 * - ADVISORY: Cấp 2 - Theo dõi / Chú ý
 * - WARNING: Cấp 3 - Cảnh báo thiên tai cấp độ vừa
 * - SEVERE: Cấp 4 - Nguy hiểm / Mưa rất to / Gió bão
 * - EXTREME: Cấp 5 - Cực kỳ nguy hiểm / Siêu bão
 * - CATASTROPHIC: Cấp thảm họa đặc biệt nghiêm trọng
 */
export type WeatherSeverityLevel =
  "SAFE" | "ADVISORY" | "WARNING" | "SEVERE" | "EXTREME" | "CATASTROPHIC";

/**
 * Cấu trúc thông tin điểm trạm quan trắc khí tượng biểu diễn trên bản đồ.
 */
export interface StationMarkerData {
  id: string;
  code: string;
  name: string;
  region: string;
  coordinates: CoordinateTuple; // Tọa độ hiển thị [Lat, Lng]
  severity: WeatherSeverityLevel;
  isActive: boolean;
  temperatureC?: number;
  rainfallMm?: number;
  windSpeedKmh?: number;
  windDirection?: string;
  elevationM?: number;
  lastUpdated?: string;
}

/**
 * Cấu hình tham số khung nhìn khởi tạo của bản đồ số.
 */
export interface MapViewportConfig {
  center: CoordinateTuple;
  zoom: number;
  minZoom: number;
  maxZoom: number;
  maxBounds: [CoordinateTuple, CoordinateTuple];
  maxBoundsViscosity: number;
}

/**
 * Tùy chọn nguồn bản đồ nền (TileLayer Provider).
 */
export interface TileLayerOption {
  id: string;
  name: string;
  url: string;
  attribution: string;
  subdomains?: string | string[];
  maxZoom?: number;
}

/**
 * Trạng thái bật/tắt các lớp dữ liệu không gian trên bản đồ.
 */
export interface LayerVisibilityState {
  stations: boolean;
  /** Lớp Mưa (SR-MAP-002) — CircleMarker phân cấp màu theo lượng mưa, render bởi RainfallLayer (W2-08). */
  rainfall: boolean;
  radar: boolean;
  wind: boolean;
  clouds: boolean;
  cyclones: boolean;
}

/**
 * Cấu trúc thông tin bản tin cảnh báo thiên tai thời gian thực (hiển thị trên AlertTickerBar).
 */
export interface WeatherAlertItem {
  id: string;
  code: string;
  title: string;
  stationName: string;
  region: string;
  severity: WeatherSeverityLevel;
  coordinates: CoordinateTuple;
  headline: string;
  detail: string;
  rainfallMm?: number;
  windSpeedKmh?: number;
  time: string;
}

/**
 * Cấu trúc thông tin cơn bão / áp thấp nhiệt đới đang hoạt động trên biển Đông.
 */
export interface ActiveCycloneData {
  id: string;
  name: string;
  category: number;
  coordinates: CoordinateTuple;
  pressureHpa: number;
  windSpeedKmh: number;
  forecastTrack: CoordinateTuple[];
  lastUpdated: string;
}
