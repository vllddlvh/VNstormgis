/**
 * @file stormService.ts
 * @description Dịch vụ dữ liệu bão — tiêu thụ hợp đồng W3-01/W3-02/W3-03.
 * Clone khuôn weatherService.ts (W2-08): GeoJSON trả THẲNG không bóc ApiResponse;
 * endpoint envelope thì bóc `.data`. Không hàm nào tự render, không tác dụng phụ cấp module
 * ngoài dedupe in-flight.
 *
 * @author VNstormgis Team - Frontend Engineer
 * @version 1.0.0
 */

import { apiClient } from "./apiClient";
import { ApiResponse } from "@/types/api";
import {
  StormDetail,
  StormGeoJsonCollection,
  StormSummary,
  StormTrackCollection,
} from "@/types/storm";

/** Dedupe request trùng khi StrictMode double-mount / 2 component gọi cùng nhịp (Mục 2 #2). */
let inFlightActive: Promise<StormGeoJsonCollection> | null = null;

export const stormService = {
  /**
   * GET /api/v1/storms (mặc định status=active, format=geojson) — FeatureCollection Point
   * tại geom_current, tọa độ [lng, lat]. KHÔNG truyền query: nhánh active là nhánh
   * được đệm `active_storms` TTL 30m (W3-03) và là nguồn DUY NHẤT của lớp bão.
   * Lưu ý DevTools: Content-Type là `application/json` (W3-03 quyết định #10), không phải geo+json.
   */
  async getActiveStormsGeoJson(): Promise<StormGeoJsonCollection> {
    if (inFlightActive) return inFlightActive;
    inFlightActive = apiClient
      .get<StormGeoJsonCollection>("/v1/storms")
      .then((response) => response.data)
      .finally(() => {
        inFlightActive = null;
      });
    return inFlightActive;
  },

  /**
   * GET /api/v1/storms?status=<status>&format=json — danh sách phẳng trong ApiResponse.
   * CHỈ phục vụ ĐỐI SOÁT nghiệm thu (TC-08/TC-09: chứng minh bão dissipated không được render).
   * KHÔNG dùng làm nguồn render — xem Mục 2 #6.
   */
  async getStormsSummary(
    status: "active" | "dissipated" | "all" = "all",
  ): Promise<StormSummary[]> {
    const response = await apiClient.get<ApiResponse<StormSummary[]>>(
      "/v1/storms",
      {
        params: { status, format: "json" },
      },
    );
    return response.data.data;
  },

  /** GET /api/v1/storms/{id} — chi tiết một cơn bão (bóc ApiResponse.data). id là mã GDACS, KHÔNG phải UUID. */
  async getStormDetail(id: string): Promise<StormDetail> {
    const response = await apiClient.get<ApiResponse<StormDetail>>(
      `/v1/storms/${encodeURIComponent(id)}`,
    );
    return response.data.data;
  },

  /**
   * GET /api/v1/storms/{id}/track — FeatureCollection 4 loại feature (history/forecast/impact/Point).
   * Bàn giao cho StormTrackLayer (W3-06); W3-05 KHÔNG gọi.
   */
  async getStormTrack(id: string): Promise<StormTrackCollection> {
    const response = await apiClient.get<StormTrackCollection>(
      `/v1/storms/${encodeURIComponent(id)}/track`,
    );
    return response.data;
  },
};
