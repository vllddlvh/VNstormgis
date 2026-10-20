import { apiClient } from "./apiClient";
import { ApiResponse } from "@/types/api";
import { LocationItem, GeoJsonFeatureCollection } from "@/types/location";

export const locationService = {
  /**
   * Lấy toàn bộ danh sách trạm quan trắc dưới dạng JSON Envelope tiêu chuẩn.
   */
  async getAllLocations(): Promise<LocationItem[]> {
    const response = await apiClient.get<ApiResponse<LocationItem[]>>("/v1/locations?format=json");
    return response.data.data;
  },

  /**
   * Lấy toàn bộ danh sách trạm quan trắc dưới dạng GeoJSON RFC 7946 cho Leaflet.
   */
  async getAllLocationsAsGeoJson(): Promise<GeoJsonFeatureCollection<LocationItem>> {
    const response = await apiClient.get<GeoJsonFeatureCollection<LocationItem>>(
      "/v1/locations?format=geojson",
    );
    return response.data;
  },

  /**
   * Tìm kiếm các trạm quan trắc lân cận trong bán kính cho trước.
   */
  async getNearbyLocations(lng: number, lat: number, radiusKm: number): Promise<LocationItem[]> {
    const response = await apiClient.get<ApiResponse<LocationItem[]>>("/v1/locations/nearby", {
      params: { lng, lat, radiusKm },
    });
    return response.data.data;
  },

  /**
   * Kiểm tra trạng thái sức khỏe của Backend Spring Boot qua Actuator.
   */
  async checkBackendHealth(): Promise<{ status: string }> {
    const response = await apiClient.get<{ status: string }>("/actuator/health", {
      baseURL: "", // Bỏ qua '/api' để gọi thẳng '/actuator/health' qua Vite proxy
    });
    return response.data;
  },
};
