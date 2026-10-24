/**
 * @file weatherService.ts
 * @description Dịch vụ dữ liệu thời tiết khí tượng — tiêu thụ hợp đồng W2-07
 * (GeoJSON RFC 7946 bản ghi mới nhất từng trạm + chuỗi thời gian theo mã trạm).
 *
 * @author VNstormgis Team - Frontend Engineer
 * @version 1.0.0
 */

import { apiClient } from "./apiClient";
import { ApiResponse } from "@/types/api";
import { WeatherGeoJsonCollection, WeatherHistoryData } from "@/types/weather";

export const weatherService = {
  /**
   * Lấy bản ghi thời tiết MỚI NHẤT của từng trạm active dưới dạng GeoJSON RFC 7946.
   * KHÔNG truyền ?format= — backend mặc định geojson (W2-07 quyết định #10),
   * response là FeatureCollection thô, KHÔNG bọc ApiResponse.
   */
  async getCurrentWeatherGeoJson(): Promise<WeatherGeoJsonCollection> {
    const response = await apiClient.get<WeatherGeoJsonCollection>("/v1/weather/current");
    return response.data;
  },

  /**
   * Lấy chuỗi thời gian khí tượng của một trạm trong cửa sổ N giờ qua (mặc định 24).
   * Trả về data đã bóc khỏi envelope ApiResponse. Mã trạm không phân biệt hoa/thường.
   */
  async getWeatherHistory(locationCode: string, hours = 24): Promise<WeatherHistoryData> {
    const response = await apiClient.get<ApiResponse<WeatherHistoryData>>("/v1/weather/history", {
      params: { location_code: locationCode, hours },
    });
    return response.data.data;
  },
};
