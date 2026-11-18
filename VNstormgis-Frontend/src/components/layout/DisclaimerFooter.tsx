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
    activeTileId === "arcgis-dark"
      ? "NỀN TỐI (ARCGIS)"
      : activeTileId === "osm-fr"
        ? "BẢN ĐỒ DỰ PHÒNG (OSM PHÁP)"
        : "BẢN ĐỒ TIÊU CHUẨN (OSM)";

  return (
    <footer className="h-6 px-4 bg-[#161b13]/95 border-t border-[#2d3329] flex items-center justify-between text-[10px] text-[#84907f] font-mono z-[1100] select-none">
      {/* 1. Bên trái: Tuyên bố pháp lý & Nguồn tham khảo */}
      <div className="flex items-center gap-2 truncate">
        <span className="text-[#e2ffcc] font-bold">LƯU Ý:</span>
        <span className="truncate">
          Dữ liệu mang tính tham khảo · Hãy theo dõi bản tin chính thức từ Trung tâm Dự báo KTTV Quốc gia
        </span>
      </div>

      {/* 2. Ở giữa: Tọa độ con trỏ chuột thời gian thực */}
      <div className="hidden sm:flex items-center gap-2">
        <span className="text-[#84907f]">VỊ TRÍ CHUỘT:</span>
        <span className="text-[#dde2e4] font-bold">
          {cursorCoords ? formatCoordinate(cursorCoords, 4) : "Di chuột lên bản đồ để xem tọa độ"}
        </span>
      </div>

      {/* 3. Bên phải: Nguồn dữ liệu và nền bản đồ */}
      <div className="flex items-center gap-3 text-[9px] uppercase">
        <span className="hidden md:inline">NGUỒN: OPENWEATHERMAP &amp; GDACS</span>
        <span className="hidden md:inline text-[#2d3329]">|</span>
        <span className="text-[#e2ffcc]">{tileProviderLabel}</span>
      </div>
    </footer>
  );
};

export default DisclaimerFooter;
