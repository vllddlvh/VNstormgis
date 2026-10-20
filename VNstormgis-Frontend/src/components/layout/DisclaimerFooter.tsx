/**
 * @file DisclaimerFooter.tsx
 * @description Thanh ghi chú chân trang (Chrome Sub-Footer) chuẩn thiết kế San Rita.
 * Hiển thị chuẩn mực trắc địa WGS84 EPSG:4326, thông tin bản quyền và tọa độ con trỏ thời gian thực.
 *
 * @author VNstormgis Team - Frontend & Design Specialist
 * @version 1.0.0
 */

import React from "react";
import { CoordinateTuple } from "@/types/map";
import { formatCoordinate } from "@/lib/geoUtils";

export interface DisclaimerFooterProps {
  /** Tọa độ con trỏ chuột thời gian thực khi di chuyển trên bản đồ */
  cursorCoords: CoordinateTuple | null;
  /** Nguồn bản đồ nền đang áp dụng */
  activeTileId: string;
}

export const DisclaimerFooter: React.FC<DisclaimerFooterProps> = ({
  cursorCoords,
  activeTileId,
}) => {
  const tileProviderLabel =
    activeTileId === "dark"
      ? "CARTO DARK MATTER"
      : activeTileId === "osm"
        ? "OPENSTREETMAP"
        : "CARTO VOYAGER";

  return (
    <footer className="h-6 px-4 bg-[#161b13]/95 border-t border-[#2d3329] flex items-center justify-between text-[10px] text-[#84907f] font-mono z-[1100] select-none">
      {/* 1. Bên trái: Tuyên bố pháp lý & Bản quyền trắc địa */}
      <div className="flex items-center gap-2 truncate">
        <span className="text-[#e2ffcc] font-bold">DỮ LIỆU ĐỊA CHÍNH:</span>
        <span className="truncate">
          WGS84 EPSG:4326 // BẢN QUYỀN © 2026 VIETNAM WEATHERGIS &amp; TRUNG TÂM KTTVQG
        </span>
      </div>

      {/* 2. Ở giữa: Tọa độ con trỏ chuột thời gian thực */}
      <div className="hidden sm:flex items-center gap-2">
        <span className="text-[#84907f]">CON TRỎ:</span>
        <span className="text-[#dde2e4] font-bold">
          {cursorCoords ? formatCoordinate(cursorCoords, 4) : "NGOÀI PHẠM VI VIEWPORT"}
        </span>
      </div>

      {/* 3. Bên phải: Tham số hiển thị Leaflet Engine */}
      <div className="flex items-center gap-3 text-[9px] uppercase">
        <span className="hidden md:inline">CRS: EPSG:3857 (WEB MERCATOR)</span>
        <span className="hidden md:inline text-[#2d3329]">|</span>
        <span className="text-[#e2ffcc]">{tileProviderLabel}</span>
      </div>
    </footer>
  );
};

export default DisclaimerFooter;
