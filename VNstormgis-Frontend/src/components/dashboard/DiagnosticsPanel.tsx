/**
 * @file DiagnosticsPanel.tsx
 * @description Bảng điều khiển kiểm định hạ tầng trắc địa và kết nối Backend (Task W1-10 & W1-11).
 * Hiển thị kết quả kiểm định kết nối Spring Boot, PostGIS và bản đồ kiểm định Marker Icon.
 *
 * @author VNstormgis Team - Frontend & QA Specialist
 * @version 1.0.0
 */

import React, { useState, useEffect } from "react";
import {
  Database,
  Radio,
  Server,
  Layers,
  RefreshCw,
  ExternalLink,
  MapPin,
  Compass,
} from "lucide-react";
import { StatusBadge, StatusType } from "@/components/common/StatusBadge";
import { locationService } from "@/services/locationService";
import { LocationItem } from "@/types/location";
import { MarkerVerificationMap } from "@/components/map/MarkerVerificationMap";

export const DiagnosticsPanel: React.FC = () => {
  const [backendStatus, setBackendStatus] = useState<StatusType>("loading");
  const [stationCount, setStationCount] = useState<number | null>(null);
  const [sampleStations, setSampleStations] = useState<LocationItem[]>([]);
  const [lastChecked, setLastChecked] = useState<string>("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  const verifyConnectivity = async () => {
    setIsRefreshing(true);
    setErrorMessage(null);
    try {
      await locationService.checkBackendHealth();
      const locations = await locationService.getAllLocations();
      setStationCount(locations.length);
      setSampleStations(locations.slice(0, 5));
      setBackendStatus("online");
      setLastChecked(new Date().toLocaleTimeString("vi-VN"));
    } catch (err: unknown) {
      setBackendStatus("error");
      const msg = err instanceof Error ? err.message : "Không rõ lỗi";
      setErrorMessage(
        `Không thể kết nối tới máy chủ dữ liệu (${msg}). Hãy kiểm tra xem Docker và máy chủ Backend đã được bật chưa.`,
      );
      setLastChecked(new Date().toLocaleTimeString("vi-VN"));
    } finally {
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    verifyConnectivity();
  }, []);

  return (
    <div className="max-w-6xl mx-auto space-y-6 select-none font-mono text-[#dde2e4]">
      {/* Thanh Tiêu Đề Bảng Trạng Thái Hệ Thống */}
      <div className="flex items-center justify-between border-b border-[#2d3329] pb-3">
        <div>
          <h1 className="text-base font-bold uppercase tracking-wider text-[#e2ffcc]">
            TRẠNG THÁI KẾT NỐI HỆ THỐNG &amp; BẢN ĐỒ MẪU
          </h1>
          <p className="text-[11px] text-[#84907f] mt-0.5">
            Kiểm tra tình trạng hoạt động của máy chủ dữ liệu, cơ sở dữ liệu trạm đo và cách hiển thị các mức cảnh báo trên bản đồ
          </p>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge
            status={backendStatus}
            label={backendStatus === "online" ? "MÁY CHỦ HOẠT ĐỘNG" : "MẤT KẾT NỐI MÁY CHỦ"}
            subLabel={stationCount ? `${stationCount} TRẠM ĐO` : undefined}
          />
          <button
            onClick={verifyConnectivity}
            disabled={isRefreshing}
            className="p-1.5 border border-[#2d3329] bg-[#1a2117] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors disabled:opacity-50"
            title="Kiểm tra lại kết nối máy chủ"
          >
            <RefreshCw
              className={`w-3.5 h-3.5 ${isRefreshing ? "animate-spin text-[#e2ffcc]" : ""}`}
            />
          </button>
        </div>
      </div>

      {/* Thông Báo Cảnh Báo Nếu Có */}
      {errorMessage && (
        <div className="p-3 border border-[#dc2626] bg-[#161b13] text-[#dc2626] text-xs flex items-start gap-2">
          <span className="font-bold uppercase tracking-wider">[LỖI KẾT NỐI]:</span>
          <div className="flex-1">{errorMessage}</div>
        </div>
      )}

      {/* Tóm Tắt Các Thành Phần Hệ Thống */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Card 1: Giao diện Web */}
        <div className="p-4 border border-[#2d3329] bg-[#1a2117] space-y-2.5 shadow-flat">
          <div className="flex items-center justify-between text-[10px] text-[#84907f] uppercase">
            <span>1. GIAO DIỆN NGƯỜI DÙNG</span>
            <Radio className="w-3.5 h-3.5 text-[#e2ffcc]" />
          </div>
          <div className="font-display text-xl uppercase tracking-wide text-[#dde2e4]">
            ỨNG DỤNG BẢN ĐỒ WEBGIS
          </div>
          <p className="text-[11px] text-[#84907f] leading-relaxed">
            Hiển thị trực quan bản đồ thời tiết, lượng mưa, hướng gió, quỹ đạo bão và trợ lý hỏi đáp tự động bằng tiếng Việt.
          </p>
          <div className="pt-1 flex items-center gap-2 text-[10px] text-[#e2ffcc] uppercase">
            <span className="w-1.5 h-1.5 bg-[#e2ffcc]" />
            GIAO DIỆN WEB // ĐANG CHẠY
          </div>
        </div>

        {/* Card 2: Kết nối API */}
        <div className="p-4 border border-[#2d3329] bg-[#1a2117] space-y-2.5 shadow-flat">
          <div className="flex items-center justify-between text-[10px] text-[#84907f] uppercase">
            <span>2. KẾT NỐI MÁY CHỦ</span>
            <Server className="w-3.5 h-3.5 text-[#f59e0b]" />
          </div>
          <div className="font-display text-xl uppercase tracking-wide text-[#dde2e4]">
            CỔNG DỮ LIỆU KHÍ TƯỢNG
          </div>
          <p className="text-[11px] text-[#84907f] leading-relaxed">
            Tự động đồng bộ dữ liệu thời tiết từ OpenWeatherMap (mỗi 15 phút) và thông tin bão quốc tế từ GDACS (mỗi 30 phút).
          </p>
          <div className="pt-1 flex items-center gap-2 text-[10px] text-[#e2ffcc] uppercase">
            <span className="w-1.5 h-1.5 bg-[#e2ffcc]" />
            KẾT NỐI API (/api ➔ :8080) // SẴN SÀNG
          </div>
        </div>

        {/* Card 3: Phân cấp cảnh báo */}
        <div className="p-4 border border-[#2d3329] bg-[#1a2117] space-y-2.5 shadow-flat">
          <div className="flex items-center justify-between text-[10px] text-[#84907f] uppercase">
            <span>3. TIÊU CHUẨN HIỂN THỊ</span>
            <Layers className="w-3.5 h-3.5 text-[#e2ffcc]" />
          </div>
          <div className="font-display text-xl uppercase tracking-wide text-[#dde2e4]">
            PHÂN CẤP MÀU RỦI RO RÕ RÀNG
          </div>
          <p className="text-[11px] text-[#84907f] leading-relaxed">
            Sử dụng bảng màu tương phản cao giúp dễ dàng nhận biết ngay các trạm đang mưa lớn, gió mạnh hoặc vùng nguy hiểm.
          </p>
          <div className="pt-1 flex items-center gap-2 text-[10px] text-[#e2ffcc] uppercase">
            <span className="w-1.5 h-1.5 bg-[#e2ffcc]" />
            CHẾ ĐỘ ĐỘ TƯƠNG PHẢN CAO // ĐANG BẬT
          </div>
        </div>
      </div>

      {/* Khu Vực Kiểm Tra Dữ Liệu Thực Tế Từ Máy Chủ */}
      <div className="p-4 border border-[#2d3329] bg-[#1a2117] space-y-3 shadow-flat">
        <div className="flex items-center justify-between border-b border-[#2d3329] pb-3">
          <div className="flex items-center gap-2">
            <Database className="w-4 h-4 text-[#e2ffcc]" />
            <h2 className="text-xs font-bold text-[#dde2e4] uppercase tracking-wider">
              DANH SÁCH TRẠM QUAN TRẮC TỪ MÁY CHỦ (
              {stationCount !== null ? `${stationCount} TRẠM` : "ĐANG TẢI..."})
            </h2>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-[10px] text-[#84907f]">
              KIỂM TRA LÚC: {lastChecked || "CHƯA KIỂM TRA"}
            </span>
            <a
              href="/swagger-ui.html"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 text-[10px] text-[#84907f] hover:text-[#e2ffcc] border border-[#2d3329] hover:border-[#e2ffcc] px-2 py-0.5 uppercase transition-colors"
            >
              TÀI LIỆU API (SWAGGER) <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>

        {sampleStations.length > 0 ? (
          <div className="space-y-2.5">
            <p className="text-[11px] text-[#84907f]">
              5 trạm đo mẫu vừa tải thành công từ máy chủ dữ liệu:
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2.5">
              {sampleStations.map((station) => (
                <div
                  key={station.id}
                  className="p-3 bg-[#161b13] border border-[#2d3329] hover:border-[#e2ffcc] transition-colors space-y-1.5"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-[#e2ffcc]">{station.code}</span>
                    <span className="text-[9px] px-1 py-0.2 border border-[#2d3329] text-[#84907f] uppercase">
                      {station.region}
                    </span>
                  </div>
                  <div className="text-xs font-medium text-[#dde2e4] truncate">{station.name}</div>
                  <div className="text-[10px] text-[#84907f] flex items-center gap-1">
                    <MapPin className="w-3 h-3 text-[#84907f]" />
                    <span>
                      {station.latitude.toFixed(4)}, {station.longitude.toFixed(4)}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div className="py-6 text-center text-[#84907f] text-xs">
            {backendStatus === "loading"
              ? "Đang kiểm tra kết nối tới máy chủ dữ liệu..."
              : "Chưa kết nối được tới máy chủ. Hãy khởi động dịch vụ Backend và nhấn nút kiểm tra lại!"}
          </div>
        )}
      </div>

      {/* Bản Đồ Minh Họa Ký Hiệu & Cấp Độ Cảnh Báo */}
      <section className="space-y-2">
        <div className="flex items-center justify-between border-b border-[#2d3329] pb-2">
          <div className="flex items-center space-x-2">
            <Compass className="w-4 h-4 text-[#e2ffcc]" />
            <h2 className="text-xs font-bold uppercase tracking-wider text-[#dde2e4]">
              BẢN ĐỒ MINH HỌA CÁC CẤP ĐỘ CẢNH BÁO &amp; QUỸ ĐẠO BÃO MẪU
            </h2>
          </div>
          <span className="text-[10px] text-[#84907f]">VỊ TRÍ TRUNG TÂM: ĐÀ NẴNG | MỨC PHÓNG: 6</span>
        </div>

        <MarkerVerificationMap />
      </section>
    </div>
  );
};

export default DiagnosticsPanel;
