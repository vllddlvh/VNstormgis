/**
 * @file HeaderNavbar.tsx
 * @description Thanh điều hướng đầu trang (Chrome Header) chuẩn thiết kế San Rita.
 * Tích hợp Brand Stamp, bảng đồng hồ Telemetry tọa độ thời gian thực, huy hiệu trạng thái hệ thống
 * và các phím tắt tác chiến nhanh.
 *
 * @author VNstormgis Team - Frontend & Design Specialist
 * @version 1.0.0
 */

import React from "react";
import {
  CloudLightning,
  RefreshCw,
  Maximize2,
  Minimize2,
  Compass,
  MapPin,
  Layers,
  Wrench,
} from "lucide-react";
import { StatusBadge, StatusType } from "@/components/common/StatusBadge";
import { CoordinateTuple } from "@/types/map";
import { formatCoordinate } from "@/lib/geoUtils";

export interface HeaderNavbarProps {
  /** Trạng thái kết nối backend */
  backendStatus: StatusType;
  /** Số lượng trạm quan trắc tải từ PostGIS */
  stationCount?: number | null;
  /** Tọa độ tâm bản đồ thời gian thực */
  mapCenter: CoordinateTuple;
  /** Mức zoom hiện tại của bản đồ */
  mapZoom: number;
  /** Tọa độ con trỏ chuột khi hover trên bản đồ */
  cursorCoords: CoordinateTuple | null;
  /** Cờ trạng thái đang refresh kết nối */
  isRefreshing: boolean;
  /** Callback làm mới kiểm tra kết nối */
  onRefreshHealth: () => void;
  /** Callback đưa bản đồ về tâm toàn cảnh Việt Nam */
  onResetView: () => void;
  /** Chế độ xem: 'operations' (Bản đồ tác chiến) hoặc 'diagnostics' (Trang kiểm định W1-10/11) */
  activeTab: "operations" | "diagnostics";
  /** Callback chuyển đổi chế độ xem */
  onTabChange: (tab: "operations" | "diagnostics") => void;
  /** Trạng thái toàn màn hình */
  isFullscreen: boolean;
  /** Callback toggle toàn màn hình */
  onToggleFullscreen: () => void;
}

export const HeaderNavbar: React.FC<HeaderNavbarProps> = ({
  backendStatus,
  stationCount,
  mapCenter,
  mapZoom,
  cursorCoords,
  isRefreshing,
  onRefreshHealth,
  onResetView,
  activeTab,
  onTabChange,
  isFullscreen,
  onToggleFullscreen,
}) => {
  return (
    <header className="h-14 px-4 bg-[#161b13] border-b border-[#2d3329] flex items-center justify-between z-[1100] select-none font-mono text-[#dde2e4]">
      {/* 1. KHỐI TRÁI: BRAND STAMP & TAB CHUYỂN ĐỔI CHẾ ĐỘ TÁC CHIẾN */}
      <div className="flex items-center gap-3">
        {/* Biểu tượng thương hiệu */}
        <div className="w-8 h-8 border border-[#2d3329] bg-[#1a2117] flex items-center justify-center text-[#e2ffcc]">
          <CloudLightning className="w-4 h-4" />
        </div>

        {/* Nhãn thương hiệu dạng Stamp */}
        <div className="flex flex-col">
          <div className="flex items-center gap-2">
            <span className="font-display text-xl tracking-wider uppercase text-[#e2ffcc] leading-none">
              VNstormgis
            </span>
            <span className="text-[9px] uppercase px-1.5 py-0.2 border border-[#2d3329] bg-[#1a2117] text-[#84907f]">
              OPS CENTER // W1-12
            </span>
          </div>
          <span className="text-[9px] text-[#84907f] uppercase tracking-tight mt-0.5 hidden sm:inline">
            HỆ THỐNG GIÁM SÁT THỜI TIẾT &amp; CẢNH BÁO BÃO LŨ VIỆT NAM
          </span>
        </div>

        {/* Bộ nút chuyển đổi Tab: Tác Chiến Bản Đồ vs Kiểm Định Hạ Tầng */}
        <div className="hidden md:flex items-center border border-[#2d3329] bg-[#161b13] ml-2 p-0.5">
          <button
            id="btn-tab-operations"
            onClick={() => onTabChange("operations")}
            className={`px-2.5 py-1 text-[10px] uppercase font-bold flex items-center gap-1.5 transition-colors ${
              activeTab === "operations"
                ? "bg-[#e2ffcc] text-[#161b13]"
                : "text-[#84907f] hover:text-[#dde2e4] hover:bg-[#1a2117]"
            }`}
          >
            <Layers className="w-3 h-3" />
            <span>TÁC CHIẾN (GIS OPS)</span>
          </button>
          <button
            id="btn-tab-diagnostics"
            onClick={() => onTabChange("diagnostics")}
            className={`px-2.5 py-1 text-[10px] uppercase font-bold flex items-center gap-1.5 transition-colors ${
              activeTab === "diagnostics"
                ? "bg-[#e2ffcc] text-[#161b13]"
                : "text-[#84907f] hover:text-[#dde2e4] hover:bg-[#1a2117]"
            }`}
          >
            <Wrench className="w-3 h-3" />
            <span>KIỂM ĐỊNH (DIAGNOSTICS)</span>
          </button>
        </div>
      </div>

      {/* 2. KHỐI GIỮA: ĐỒNG HỒ TELEMETRY TRẮC ĐỊA THỜI GIAN THỰC */}
      <div className="hidden lg:flex items-center gap-4 text-[10px] text-[#84907f] border border-[#2d3329] bg-[#1a2117] px-3 py-1">
        <div className="flex items-center gap-1.5">
          <MapPin className="w-3 h-3 text-[#e2ffcc]" />
          <span>TÂM KHUNG NHÌN:</span>
          <span className="text-[#dde2e4] font-bold">{formatCoordinate(mapCenter, 4)}</span>
        </div>
        <span className="text-[#2d3329]">|</span>
        <div>
          <span>MỨC ZOOM:</span> <span className="text-[#e2ffcc] font-bold">Z{mapZoom}</span>
        </div>
        {cursorCoords && (
          <>
            <span className="text-[#2d3329]">|</span>
            <div className="flex items-center gap-1.5">
              <Compass className="w-3 h-3 text-[#84907f]" />
              <span>CON TRỎ:</span>
              <span className="text-[#e2ffcc] font-bold">{formatCoordinate(cursorCoords, 4)}</span>
            </div>
          </>
        )}
      </div>

      {/* 3. KHỐI PHẢI: HUY HIỆU KẾT NỐI & CÁC NÚT TÁC VỤ NHANH */}
      <div className="flex items-center gap-2">
        <StatusBadge
          status={backendStatus}
          label={backendStatus === "online" ? "SPRING BOOT 3.3 // ACTIVE" : "BACKEND OFFLINE"}
          subLabel={stationCount ? `${stationCount} TRẠM POSTGIS` : undefined}
        />

        {/* Nút reset khung nhìn về toàn cảnh Việt Nam */}
        <button
          id="btn-header-recenter"
          onClick={onResetView}
          className="p-1.5 border border-[#2d3329] bg-[#1a2117] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors"
          title="Định vị về trung tâm Việt Nam (16.047°N, 108.206°E, Z6)"
        >
          <Compass className="w-3.5 h-3.5" />
        </button>

        {/* Nút làm mới trạng thái kết nối */}
        <button
          id="btn-header-refresh"
          onClick={onRefreshHealth}
          disabled={isRefreshing}
          className="p-1.5 border border-[#2d3329] bg-[#1a2117] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors disabled:opacity-50"
          title="Làm mới kết nối máy chủ"
        >
          <RefreshCw
            className={`w-3.5 h-3.5 ${isRefreshing ? "animate-spin text-[#e2ffcc]" : ""}`}
          />
        </button>

        {/* Nút bật/tắt toàn màn hình */}
        <button
          id="btn-header-fullscreen"
          onClick={onToggleFullscreen}
          className="p-1.5 border border-[#2d3329] bg-[#1a2117] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors"
          title={isFullscreen ? "Thoát toàn màn hình" : "Chế độ toàn màn hình"}
        >
          {isFullscreen ? (
            <Minimize2 className="w-3.5 h-3.5" />
          ) : (
            <Maximize2 className="w-3.5 h-3.5" />
          )}
        </button>
      </div>
    </header>
  );
};

export default HeaderNavbar;
