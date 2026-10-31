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
 *
 * Mặc định giữ nguyên Point + [lng, lat] cho 3 consumer hiện hành (locationService,
 * weatherService, MarkerVerificationMap); quỹ đạo bão (W3-02) khai báo LineString/Polygon
 * qua tham số generic thay vì nới lỏng kiểu của lớp nạp trạm.
 */
export interface GeoJsonFeature<
  TProps = Record<string, unknown>,
  TGeomType extends string = "Point",
  TCoords = [number, number],
> {
  type: "Feature";
  id?: string;
  geometry: {
    type: TGeomType;
    /** GeoJSON RFC 7946 luôn [KINH ĐỘ, VĨ ĐỘ] — AGENTS.md mục 1. */
    coordinates: TCoords;
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
