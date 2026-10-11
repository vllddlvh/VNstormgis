/**
 * Thông tin chi tiết một trạm quan trắc khí tượng thủy văn.
 */
export interface LocationItem {
  id: string;
  code: string;
  name: string;
  province?: string;
  region: string;
  latitude: number;
  longitude: number;
  elevation?: number;
  isActive: boolean;
  distanceKm?: number;
  updatedAt?: string;
}

/**
 * Chuẩn cấu trúc GeoJSON Feature (RFC 7946).
 */
export interface GeoJsonFeature<TProps = Record<string, unknown>> {
  type: "Feature";
  id?: string;
  geometry: {
    type: "Point";
    coordinates: [number, number]; // [Kinh độ (Longitude), Vĩ độ (Latitude)]
  };
  properties: TProps;
}

/**
 * Chuẩn cấu trúc GeoJSON FeatureCollection phục vụ nạp thẳng vào Leaflet L.geoJSON.
 */
export interface GeoJsonFeatureCollection<TProps = Record<string, unknown>> {
  type: "FeatureCollection";
  features: GeoJsonFeature<TProps>[];
}
