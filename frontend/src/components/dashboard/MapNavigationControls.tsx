/**
 * @file MapNavigationControls.tsx
 * @description Thanh công cụ điều hướng bản đồ nổi (Floating Map Navigation Controls) chuẩn San Rita.
 * Bao gồm các nút thu phóng (Zoom In/Out), định vị trung tâm toàn cảnh Việt Nam và điều hướng góc nhìn.
 *
 * @author VNstormgis Team - Frontend & Design Specialist
 * @version 1.0.0
 */

import React from "react";
import { Plus, Minus, Compass, Maximize2, Minimize2 } from "lucide-react";

export interface MapNavigationControlsProps {
  /** Callback phóng to bản đồ */
  onZoomIn: () => void;
  /** Callback thu nhỏ bản đồ */
  onZoomOut: () => void;
  /** Callback đưa về tâm toàn cảnh lãnh thổ Việt Nam */
  onResetCenter: () => void;
  /** Trạng thái toàn màn hình */
  isFullscreen?: boolean;
  /** Callback bật/tắt toàn màn hình */
  onToggleFullscreen?: () => void;
}

export const MapNavigationControls: React.FC<MapNavigationControlsProps> = ({
  onZoomIn,
  onZoomOut,
  onResetCenter,
  isFullscreen,
  onToggleFullscreen,
}) => {
  return (
    <div className="absolute top-4 right-4 z-[1000] flex flex-col space-y-1 font-mono select-none">
      {/* Nút Phóng to (+) */}
      <button
        id="btn-nav-zoom-in"
        onClick={onZoomIn}
        className="w-8 h-8 bg-[#161b13] border border-[#2d3329] hover:border-[#e2ffcc] text-[#dde2e4] hover:text-[#e2ffcc] flex items-center justify-center transition-colors shadow-flat"
        title="Phóng to bản đồ (+)"
      >
        <Plus className="w-4 h-4" />
      </button>

      {/* Nút Thu nhỏ (−) */}
      <button
        id="btn-nav-zoom-out"
        onClick={onZoomOut}
        className="w-8 h-8 bg-[#161b13] border border-[#2d3329] hover:border-[#e2ffcc] text-[#dde2e4] hover:text-[#e2ffcc] flex items-center justify-center transition-colors shadow-flat"
        title="Thu nhỏ bản đồ (−)"
      >
        <Minus className="w-4 h-4" />
      </button>

      {/* Nút Định vị Trung tâm Việt Nam (Home / Focus) */}
      <button
        id="btn-nav-recenter"
        onClick={onResetCenter}
        className="w-8 h-8 bg-[#161b13] border border-[#2d3329] hover:border-[#e2ffcc] text-[#dde2e4] hover:text-[#e2ffcc] flex items-center justify-center transition-colors shadow-flat"
        title="Trở về trung tâm toàn cảnh Việt Nam (16.047°N, 108.206°E)"
      >
        <Compass className="w-4 h-4 text-[#e2ffcc]" />
      </button>

      {/* Nút Chế độ Toàn màn hình nếu có callback */}
      {onToggleFullscreen && (
        <button
          id="btn-nav-fullscreen"
          onClick={onToggleFullscreen}
          className="w-8 h-8 bg-[#161b13] border border-[#2d3329] hover:border-[#e2ffcc] text-[#dde2e4] hover:text-[#e2ffcc] flex items-center justify-center transition-colors shadow-flat"
          title={isFullscreen ? "Thu nhỏ cửa sổ" : "Mở rộng toàn màn hình"}
        >
          {isFullscreen ? (
            <Minimize2 className="w-3.5 h-3.5" />
          ) : (
            <Maximize2 className="w-3.5 h-3.5" />
          )}
        </button>
      )}
    </div>
  );
};

export default MapNavigationControls;
