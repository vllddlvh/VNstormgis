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
  Search,
  LocateFixed,
  Mail,
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
  /** Callback mở tìm kiếm nhanh tỉnh/trạm (Ctrl+K) */
  onOpenSearch?: () => void;
  /** Callback định vị vị trí hiện tại của người dùng */
  onLocateMe?: () => void;
  /** Cờ đang định vị GPS */
  isLocatingMe?: boolean;
  /** Callback mở hộp thoại đăng ký nhận thông báo qua Email */
  onOpenSubscribe?: () => void;
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
  onOpenSearch,
  onLocateMe,
  isLocatingMe = false,
  onOpenSubscribe,
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
              TRỰC BAN KHÍ TƯỢNG
            </span>
          </div>
          <span className="text-[9px] text-[#84907f] uppercase tracking-tight mt-0.5 hidden sm:inline">
            BẢN ĐỒ THEO DÕI THỜI TIẾT &amp; CẢNH BÁO BÃO LŨ VIỆT NAM
          </span>
        </div>

        {/* Bộ nút chuyển đổi Tab: Bản Đồ Giám Sát vs Trạng Thái Hệ Thống */}
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
            <span>BẢN ĐỒ GIÁM SÁT</span>
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
            <span>TRẠNG THÁI HỆ THỐNG</span>
          </button>
        </div>
      </div>

      {/* 2. KHỐI GIỮA: THÔNG TIN VỊ TRÍ BẢN ĐỒ THỜI GIAN THỰC */}
      <div className="hidden lg:flex items-center gap-4 text-[10px] text-[#84907f] border border-[#2d3329] bg-[#1a2117] px-3 py-1">
        <div className="flex items-center gap-1.5">
          <MapPin className="w-3 h-3 text-[#e2ffcc]" />
          <span>TÂM BẢN ĐỒ:</span>
          <span className="text-[#dde2e4] font-bold">{formatCoordinate(mapCenter, 4)}</span>
        </div>
        <span className="text-[#2d3329]">|</span>
        <div>
          <span>MỨC PHÓNG:</span> <span className="text-[#e2ffcc] font-bold">Mức {mapZoom}</span>
        </div>
        {cursorCoords && (
          <>
            <span className="text-[#2d3329]">|</span>
            <div className="flex items-center gap-1.5">
              <Compass className="w-3 h-3 text-[#84907f]" />
              <span>VỊ TRÍ CHUỘT:</span>
              <span className="text-[#e2ffcc] font-bold">{formatCoordinate(cursorCoords, 4)}</span>
            </div>
          </>
        )}
      </div>

      {/* 3. KHỐI PHẢI: HUY HIỆU KẾT NỐI & CÁC NÚT TÁC VỤ NHANH */}
      <div className="flex items-center gap-1.5 sm:gap-2">
        {onOpenSearch && (
          <button
            id="btn-header-search"
            type="button"
            onClick={onOpenSearch}
            className="px-2 py-1.5 border border-[#2d3329] bg-[#1a2117] hover:border-[#e2ffcc] text-[#dde2e4] hover:text-[#e2ffcc] transition-colors flex items-center gap-1.5 text-[10px]"
            title="Tìm kiếm nhanh Tỉnh/Thành phố hoặc Trạm đo (Ctrl+K)"
          >
            <Search className="w-3.5 h-3.5 text-[#e2ffcc]" />
            <span className="hidden xl:inline">TÌM TRẠM</span>
            <span className="hidden 2xl:inline text-[9px] text-[#84907f] border border-[#2d3329] px-1">
              ⌘K
            </span>
          </button>
        )}

        {onLocateMe && (
          <button
            id="btn-header-my-location"
            type="button"
            onClick={onLocateMe}
            className="px-2 py-1.5 border border-[#2d3329] bg-[#1a2117] hover:border-[#e2ffcc] text-[#dde2e4] hover:text-[#e2ffcc] transition-colors flex items-center gap-1.5 text-[10px]"
            title="Định vị vị trí hiện tại của tôi (GPS + Phân tích PostGIS)"
          >
            <LocateFixed
              className={`w-3.5 h-3.5 text-[#e2ffcc] ${isLocatingMe ? "animate-spin" : ""}`}
            />
            <span className="hidden xl:inline font-bold text-[#e2ffcc]">
              VỊ TRÍ CỦA TÔI
            </span>
          </button>
        )}

        {onOpenSubscribe && (
          <button
            id="btn-header-subscribe"
            type="button"
            onClick={onOpenSubscribe}
            className="px-2 py-1.5 border border-[#e2ffcc]/70 bg-[#1a2117] hover:bg-[#e2ffcc] text-[#e2ffcc] hover:text-[#161b13] transition-colors flex items-center gap-1.5 text-[10px] font-bold uppercase"
            title="Đăng ký nhận thông báo cảnh báo thiên tai qua Email"
          >
            <Mail className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">NHẬN EMAIL</span>
          </button>
        )}

        <div className="hidden md:block">
          <StatusBadge
            status={backendStatus}
            label={backendStatus === "online" ? "MÁY CHỦ HOẠT ĐỘNG" : "MẤT KẾT NỐI MÁY CHỦ"}
            subLabel={stationCount ? `${stationCount} TRẠM ĐO` : undefined}
          />
        </div>

        {/* Nút reset khung nhìn về toàn cảnh Việt Nam */}
        <button
          id="btn-header-recenter"
          onClick={onResetView}
          className="p-1.5 border border-[#2d3329] bg-[#1a2117] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors"
          title="Đưa bản đồ về toàn cảnh Việt Nam"
        >
          <Compass className="w-3.5 h-3.5" />
        </button>

        {/* Nút làm mới trạng thái kết nối */}
        <button
          id="btn-header-refresh"
          onClick={onRefreshHealth}
          disabled={isRefreshing}
          className="p-1.5 border border-[#2d3329] bg-[#1a2117] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors disabled:opacity-50"
          title="Tải lại dữ liệu thời tiết và cảnh báo mới nhất"
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
