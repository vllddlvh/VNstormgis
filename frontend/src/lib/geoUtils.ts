/**
 * @file geoUtils.ts
 * @description Bộ công cụ tiện ích chuyển đổi tọa độ trắc địa WGS84, kiểm tra giới hạn không gian
 * và tính toán khoảng cách cầu trắc địa Haversine.
 *
 * @author VNstormgis Team - GIS & Geodesy Specialist
 * @version 1.0.0
 */

import { CoordinateTuple, GeoJsonCoordinate } from "@/types/map";

/**
 * Bounding Box bao trùm toàn bộ không phận, hải phận, vùng đất liền và hai quần đảo Hoàng Sa, Trường Sa của Việt Nam.
 * Cực Tây Nam: [5.5°N, 100.0°E]
 * Cực Đông Bắc: [24.5°N, 118.0°E]
 */
export const VIETNAM_BOUNDING_BOX = {
  minLat: 5.5,
  maxLat: 24.5,
  minLng: 100.0,
  maxLng: 118.0,
} as const;

/**
 * Tọa độ trung tâm trắc địa của lãnh thổ Việt Nam (khu vực thành phố Đà Nẵng).
 * Chuẩn hiển thị Leaflet: [Latitude, Longitude]
 */
export const VIETNAM_DEFAULT_CENTER: CoordinateTuple = [16.047079, 108.20623];

/**
 * Mức thu phóng (Zoom Level) mặc định bao quát toàn cảnh lãnh thổ Việt Nam.
 */
export const VIETNAM_DEFAULT_ZOOM = 6;

/**
 * Chuyển đổi mảng tọa độ GeoJSON RFC 7946 [Longitude, Latitude] sang tọa độ hiển thị Leaflet [Latitude, Longitude].
 *
 * @param {GeoJsonCoordinate} geoJsonCoord Mảng tọa độ GeoJSON [Kinh độ, Vĩ độ]
 * @returns {CoordinateTuple} Mảng tọa độ Leaflet [Vĩ độ, Kinh độ]
 * @throws {Error} Nếu dữ liệu tọa độ không hợp lệ
 */
export function geojsonToLatLng(geoJsonCoord: GeoJsonCoordinate | number[]): CoordinateTuple {
  if (!Array.isArray(geoJsonCoord) || geoJsonCoord.length < 2) {
    throw new Error(
      `[geoUtils] Tọa độ GeoJSON không hợp lệ: ${JSON.stringify(geoJsonCoord)}. Yêu cầu mảng ít nhất 2 phần tử [lng, lat].`,
    );
  }

  const [lng, lat] = geoJsonCoord;

  if (typeof lng !== "number" || typeof lat !== "number" || isNaN(lng) || isNaN(lat)) {
    throw new Error(
      `[geoUtils] Tọa độ chứa giá trị NaN hoặc không phải dạng số: lng=${lng}, lat=${lat}`,
    );
  }

  return [lat, lng];
}

/**
 * Chuyển đổi tọa độ Leaflet [Latitude, Longitude] sang tọa độ chuẩn GeoJSON RFC 7946 [Longitude, Latitude].
 *
 * @param {CoordinateTuple} latLng Mảng tọa độ Leaflet [Vĩ độ, Kinh độ]
 * @returns {GeoJsonCoordinate} Mảng tọa độ GeoJSON [Kinh độ, Vĩ độ]
 */
export function latLngToGeojson(latLng: CoordinateTuple): GeoJsonCoordinate {
  const [lat, lng] = latLng;
  return [lng, lat];
}

/**
 * Kiểm tra xem một tọa độ địa lý có nằm trong phạm vi không phận, hải phận của Việt Nam hay không.
 *
 * @param {number} lat Vĩ độ cần kiểm tra
 * @param {number} lng Kinh độ cần kiểm tra
 * @returns {boolean} True nếu nằm trong Bounding Box của Việt Nam
 */
export function isWithinVietnamBounds(lat: number, lng: number): boolean {
  return (
    lat >= VIETNAM_BOUNDING_BOX.minLat &&
    lat <= VIETNAM_BOUNDING_BOX.maxLat &&
    lng >= VIETNAM_BOUNDING_BOX.minLng &&
    lng <= VIETNAM_BOUNDING_BOX.maxLng
  );
}

/**
 * Tính toán khoảng cách trắc địa mặt cầu theo công thức Haversine giữa hai điểm trên Trái Đất (bán kính R = 6,371 km).
 *
 * @param {CoordinateTuple} pointA Tọa độ điểm A [lat, lng]
 * @param {CoordinateTuple} pointB Tọa độ điểm B [lat, lng]
 * @returns {number} Khoảng cách tính bằng kilomet (km), làm tròn 2 chữ số thập phân
 */
export function calculateHaversineDistance(
  pointA: CoordinateTuple,
  pointB: CoordinateTuple,
): number {
  const [lat1, lon1] = pointA;
  const [lat2, lon2] = pointB;

  const EARTH_RADIUS_KM = 6371.0088; // Bán kính trung bình chuẩn WGS84

  const toRad = (degree: number): number => (degree * Math.PI) / 180;

  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);

  const radLat1 = toRad(lat1);
  const radLat2 = toRad(lat2);

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  const distance = EARTH_RADIUS_KM * c;
  return Math.round(distance * 100) / 100;
}

/**
 * Định dạng tọa độ trắc địa thành chuỗi văn bản chuyên ngành dễ đọc.
 * Ví dụ: formatCoordinate([16.0544, 108.2022]) => "16.0544°N, 108.2022°E"
 *
 * @param {CoordinateTuple} coords Mảng tọa độ [lat, lng]
 * @param {number} precision Số chữ số phần thập phân (mặc định 4)
 * @returns {string} Chuỗi văn bản tọa độ
 */
export function formatCoordinate(coords: CoordinateTuple, precision = 4): string {
  const [lat, lng] = coords;
  const latDirection = lat >= 0 ? "N" : "S";
  const lngDirection = lng >= 0 ? "E" : "W";

  return `${Math.abs(lat).toFixed(precision)}°${latDirection}, ${Math.abs(lng).toFixed(precision)}°${lngDirection}`;
}
