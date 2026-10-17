/**
 * @file LayerControlDrawer.tsx
 * @description Bảng điều khiển lớp dữ liệu nổi (Floating Layer Control Panel) chuẩn thiết kế San Rita.
 * Cho phép kỹ sư vận hành chuyển đổi linh hoạt nguồn bản đồ nền (Voyager / Dark / OSM),
 * bật/tắt các lớp chuyên đề thời tiết (Trạm quan trắc, Radar mưa, Gió, Mây vệ tinh, Tâm bão)
 * và tinh chỉnh độ trong suốt Opacity của lớp.
 *
 * @author VNstormgis Team - Frontend & GIS Specialist
 * @version 1.0.0
 */

import React, { useState } from "react";
import {
  Layers,
  ChevronLeft,
  ChevronRight,
  Eye,
  EyeOff,
  CloudRain,
  Wind,
  Cloud,
  Radio,
  Sliders,
} from "lucide-react";
import { LayerVisibilityState } from "@/types/map";

export interface LayerControlDrawerProps {
  /** Nguồn bản đồ nền hiện tại */
  activeTileId: string;
  /** Callback thay đổi nguồn bản đồ nền */
  onTileChange: (tileId: string) => void;
  /** Trạng thái hiển thị của các lớp dữ liệu */
  layerVisibility: LayerVisibilityState;
  /** Callback bật/tắt lớp dữ liệu */
  onToggleLayer: (layerKey: keyof LayerVisibilityState) => void;
  /** Mức độ trong suốt của các lớp phủ */
  layerOpacity: { [key: string]: number };
  /** Callback điều chỉnh độ trong suốt */
  onOpacityChange: (layerKey: string, value: number) => void;
  /** Số lượng trạm quan trắc hiện có sau khi lọc */
  stationCount?: number;
  /** Tổng số lượng trạm quan trắc trong hệ thống */
  totalStationCount?: number;
  /** Vùng miền đang được lọc ('ALL', 'BẮC BỘ', 'TRUNG BỘ', 'TÂY NGUYÊN', 'NAM BỘ', 'BIỂN ĐẢO') */
  selectedRegion?: string;
  /** Callback khi người dùng thay đổi vùng miền lọc */
  onRegionChange?: (region: string) => void;
}

export const LayerControlDrawer: React.FC<LayerControlDrawerProps> = ({
  activeTileId,
  onTileChange,
  layerVisibility,
  onToggleLayer,
  layerOpacity,
  onOpacityChange,
  stationCount = 68,
  totalStationCount = 68,
  selectedRegion = "ALL",
  onRegionChange,
}) => {
  const [isOpen, setIsOpen] = useState<boolean>(true);

  // Đếm tổng số lớp dữ liệu đang kích hoạt
  const activeLayerCount = Object.values(layerVisibility).filter(Boolean).length;

  return (
    <div className="absolute top-4 left-4 z-[1000] font-mono select-none">
      {/* Nút Toggle mở/đóng nhanh khi panel thu gọn */}
      {!isOpen ? (
        <button
          id="btn-open-layers"
          onClick={() => setIsOpen(true)}
          className="flex items-center gap-2 px-3 py-2 bg-[#161b13] border border-[#2d3329] hover:border-[#e2ffcc] text-[#dde2e4] hover:text-[#e2ffcc] transition-colors shadow-flat"
          title="Mở bảng điều khiển lớp bản đồ"
        >
          <Layers className="w-4 h-4 text-[#e2ffcc]" />
          <span className="text-xs uppercase font-bold tracking-wider">LỚP BẢN ĐỒ</span>
          <span className="text-[10px] px-1 py-0.2 bg-[#1a2117] border border-[#2d3329] text-[#e2ffcc]">
            {activeLayerCount}
          </span>
          <ChevronRight className="w-3.5 h-3.5 text-[#84907f]" />
        </button>
      ) : (
        /* Bảng điều khiển mở rộng */
        <div className="w-72 bg-[#161b13] border border-[#2d3329] shadow-flat text-[#dde2e4]">
          {/* Header Panel */}
          <div className="p-3 border-b border-[#2d3329] bg-[#1a2117] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Layers className="w-4 h-4 text-[#e2ffcc]" />
              <span className="text-xs uppercase font-bold tracking-wider text-[#e2ffcc]">
                ĐIỀU KHIỂN LỚP (LAYERS)
              </span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-[9px] px-1.5 py-0.2 border border-[#2d3329] text-[#84907f] bg-[#161b13]">
                {activeLayerCount} ĐANG BẬT
              </span>
              <button
                id="btn-close-layers"
                onClick={() => setIsOpen(false)}
                className="p-1 border border-[#2d3329] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors"
                title="Thu gọn bảng điều khiển"
              >
                <ChevronLeft className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>

          <div className="p-3 space-y-4 max-h-[calc(100vh-180px)] overflow-y-auto">
            {/* 1. NGUỒN BẢN ĐỒ NỀN (BASE TILES) */}
            <div className="space-y-1.5">
              <div className="text-[10px] uppercase font-bold text-[#84907f] flex items-center justify-between">
                <span>[01 // BẢN ĐỒ NỀN BASEMAP]</span>
                <span className="text-[9px] text-[#e2ffcc]">CARTO / OSM</span>
              </div>
              <div className="grid grid-cols-3 gap-1">
                <button
                  id="btn-tile-voyager"
                  onClick={() => onTileChange("voyager")}
                  className={`py-1.5 px-1 text-[9px] uppercase font-bold border transition-colors ${
                    activeTileId === "voyager"
                      ? "border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc]"
                      : "border-[#2d3329] bg-[#161b13] text-[#84907f] hover:text-[#dde2e4]"
                  }`}
                >
                  VOYAGER
                </button>
                <button
                  id="btn-tile-dark"
                  onClick={() => onTileChange("dark")}
                  className={`py-1.5 px-1 text-[9px] uppercase font-bold border transition-colors ${
                    activeTileId === "dark"
                      ? "border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc]"
                      : "border-[#2d3329] bg-[#161b13] text-[#84907f] hover:text-[#dde2e4]"
                  }`}
                >
                  DARK TACTICAL
                </button>
                <button
                  id="btn-tile-osm"
                  onClick={() => onTileChange("osm")}
                  className={`py-1.5 px-1 text-[9px] uppercase font-bold border transition-colors ${
                    activeTileId === "osm"
                      ? "border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc]"
                      : "border-[#2d3329] bg-[#161b13] text-[#84907f] hover:text-[#dde2e4]"
                  }`}
                >
                  OSM STANDARD
                </button>
              </div>
            </div>

            {/* 2. CÁC LỚP DỮ LIỆU THỜI TIẾT (WEATHER LAYERS) */}
            <div className="space-y-2">
              <div className="text-[10px] uppercase font-bold text-[#84907f]">
                [02 // CÁC LỚP CHUYÊN ĐỀ KHÔNG GIAN]
              </div>

              {/* Lớp Trạm quan trắc */}
              <div
                id="layer-toggle-stations"
                onClick={() => onToggleLayer("stations")}
                className={`p-2 border cursor-pointer flex items-center justify-between transition-colors ${
                  layerVisibility.stations
                    ? "border-[#2d3329] bg-[#1a2117] text-[#dde2e4]"
                    : "border-[#2d3329]/50 bg-[#161b13] text-[#84907f]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <Radio
                    className={`w-3.5 h-3.5 ${layerVisibility.stations ? "text-[#e2ffcc]" : "text-[#84907f]"}`}
                  />
                  <div>
                    <div className="text-xs font-bold leading-tight">TRẠM QUAN TRẮC KTTV</div>
                    <div className="text-[9px] text-[#84907f]">
                      {stationCount === totalStationCount
                        ? `${stationCount} ĐIỂM TRẠM TOÀN QUỐC`
                        : `HIỂN THỊ ${stationCount}/${totalStationCount} TRẠM`}
                    </div>
                  </div>
                </div>
                {layerVisibility.stations ? (
                  <Eye className="w-3.5 h-3.5 text-[#e2ffcc]" />
                ) : (
                  <EyeOff className="w-3.5 h-3.5 text-[#84907f]" />
                )}
              </div>

              {/* Lớp Cơn bão & Áp thấp */}
              <div
                id="layer-toggle-cyclones"
                onClick={() => onToggleLayer("cyclones")}
                className={`p-2 border cursor-pointer flex items-center justify-between transition-colors ${
                  layerVisibility.cyclones
                    ? "border-[#2d3329] bg-[#1a2117] text-[#dde2e4]"
                    : "border-[#2d3329]/50 bg-[#161b13] text-[#84907f]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <div className="w-3.5 h-3.5 border border-red-500 bg-[#dc2626]/20 flex items-center justify-center text-[8px] font-bold text-red-400">
                    🌀
                  </div>
                  <div>
                    <div className="text-xs font-bold leading-tight">
                      BÃO &amp; ÁP THẤP NHIỆT ĐỚI
                    </div>
                    <div className="text-[9px] text-[#84907f]">TÂM BÃO &amp; QUỸ ĐẠO DỰ BÁO</div>
                  </div>
                </div>
                {layerVisibility.cyclones ? (
                  <Eye className="w-3.5 h-3.5 text-[#e2ffcc]" />
                ) : (
                  <EyeOff className="w-3.5 h-3.5 text-[#84907f]" />
                )}
              </div>

              {/* Lớp Vùng mưa Radar */}
              <div
                id="layer-toggle-radar"
                onClick={() => onToggleLayer("radar")}
                className={`p-2 border cursor-pointer flex items-center justify-between transition-colors ${
                  layerVisibility.radar
                    ? "border-[#2d3329] bg-[#1a2117] text-[#dde2e4]"
                    : "border-[#2d3329]/50 bg-[#161b13] text-[#84907f]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <CloudRain
                    className={`w-3.5 h-3.5 ${layerVisibility.radar ? "text-[#f59e0b]" : "text-[#84907f]"}`}
                  />
                  <div>
                    <div className="text-xs font-bold leading-tight">RADAR PHẢN HỒI MƯA</div>
                    <div className="text-[9px] text-[#84907f]">ĐỘ PHẢN HỒI REFLECTIVITY (dBZ)</div>
                  </div>
                </div>
                {layerVisibility.radar ? (
                  <Eye className="w-3.5 h-3.5 text-[#e2ffcc]" />
                ) : (
                  <EyeOff className="w-3.5 h-3.5 text-[#84907f]" />
                )}
              </div>

              {/* Lớp Vector Gió */}
              <div
                id="layer-toggle-wind"
                onClick={() => onToggleLayer("wind")}
                className={`p-2 border cursor-pointer flex items-center justify-between transition-colors ${
                  layerVisibility.wind
                    ? "border-[#2d3329] bg-[#1a2117] text-[#dde2e4]"
                    : "border-[#2d3329]/50 bg-[#161b13] text-[#84907f]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <Wind
                    className={`w-3.5 h-3.5 ${layerVisibility.wind ? "text-[#e2ffcc]" : "text-[#84907f]"}`}
                  />
                  <div>
                    <div className="text-xs font-bold leading-tight">VECTOR GIÓ BỀ MẶT</div>
                    <div className="text-[9px] text-[#84907f]">HƯỚNG &amp; CƯỜNG ĐỘ DÒNG GIÓ</div>
                  </div>
                </div>
                {layerVisibility.wind ? (
                  <Eye className="w-3.5 h-3.5 text-[#e2ffcc]" />
                ) : (
                  <EyeOff className="w-3.5 h-3.5 text-[#84907f]" />
                )}
              </div>

              {/* Lớp Mây vệ tinh */}
              <div
                id="layer-toggle-clouds"
                onClick={() => onToggleLayer("clouds")}
                className={`p-2 border cursor-pointer flex items-center justify-between transition-colors ${
                  layerVisibility.clouds
                    ? "border-[#2d3329] bg-[#1a2117] text-[#dde2e4]"
                    : "border-[#2d3329]/50 bg-[#161b13] text-[#84907f]"
                }`}
              >
                <div className="flex items-center gap-2">
                  <Cloud
                    className={`w-3.5 h-3.5 ${layerVisibility.clouds ? "text-[#dde2e4]" : "text-[#84907f]"}`}
                  />
                  <div>
                    <div className="text-xs font-bold leading-tight">MÂY VỆ TINH HỒNG NGOẠI</div>
                    <div className="text-[9px] text-[#84907f]">ẢNH VỆ TINH HIMAWARI-9</div>
                  </div>
                </div>
                {layerVisibility.clouds ? (
                  <Eye className="w-3.5 h-3.5 text-[#e2ffcc]" />
                ) : (
                  <EyeOff className="w-3.5 h-3.5 text-[#84907f]" />
                )}
              </div>
            </div>

            {/* 3. BỘ LỌC VÙNG MIỀN (REGION FILTER) */}
            <div className="space-y-1.5 pt-2 border-t border-[#2d3329]">
              <div className="text-[10px] uppercase font-bold text-[#84907f] flex items-center justify-between">
                <span>[03 // BỘ LỌC VÙNG MIỀN]</span>
                <span className="text-[9px] text-[#e2ffcc]">{selectedRegion}</span>
              </div>
              <div className="grid grid-cols-3 gap-1">
                {[
                  { id: "btn-filter-all", key: "ALL", label: "TẤT CẢ" },
                  { id: "btn-filter-north", key: "BẮC BỘ", label: "BẮC BỘ" },
                  { id: "btn-filter-central", key: "TRUNG BỘ", label: "TRUNG BỘ" },
                  { id: "btn-filter-highlands", key: "TÂY NGUYÊN", label: "TÂY NGUYÊN" },
                  { id: "btn-filter-south", key: "NAM BỘ", label: "NAM BỘ" },
                  { id: "btn-filter-islands", key: "BIỂN ĐẢO", label: "BIỂN ĐẢO" },
                ].map((item) => {
                  const isActive = selectedRegion === item.key;
                  return (
                    <button
                      key={item.key}
                      id={item.id}
                      onClick={() => onRegionChange?.(item.key)}
                      className={`py-1.5 px-1 text-[9px] uppercase font-bold border transition-colors ${
                        isActive
                          ? "border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc]"
                          : "border-[#2d3329] bg-[#161b13] text-[#84907f] hover:text-[#dde2e4]"
                      }`}
                    >
                      {item.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 4. ĐIỀU CHỈNH ĐỘ TRONG SUỐT (OPACITY CONTROLS) */}
            <div className="space-y-2 pt-2 border-t border-[#2d3329]">
              <div className="text-[10px] uppercase font-bold text-[#84907f] flex items-center gap-1.5">
                <Sliders className="w-3 h-3 text-[#e2ffcc]" />
                <span>[04 // ĐỘ TRONG SUỐT LỚP PHỦ]</span>
              </div>

              <div className="space-y-2">
                <div className="space-y-1">
                  <div className="flex justify-between text-[9px] text-[#84907f]">
                    <span>RADAR MƯA:</span>
                    <span className="text-[#dde2e4]">
                      {Math.round((layerOpacity.radar || 0.65) * 100)}%
                    </span>
                  </div>
                  <input
                    type="range"
                    min="0.1"
                    max="1.0"
                    step="0.05"
                    value={layerOpacity.radar || 0.65}
                    onChange={(e) => onOpacityChange("radar", parseFloat(e.target.value))}
                    className="w-full h-1 bg-[#2d3329] appearance-none cursor-pointer accent-[#e2ffcc]"
                  />
                </div>

                <div className="space-y-1">
                  <div className="flex justify-between text-[9px] text-[#84907f]">
                    <span>MÂY VỆ TINH:</span>
                    <span className="text-[#dde2e4]">
                      {Math.round((layerOpacity.clouds || 0.5) * 100)}%
                    </span>
                  </div>
                  <input
                    type="range"
                    min="0.1"
                    max="1.0"
                    step="0.05"
                    value={layerOpacity.clouds || 0.5}
                    onChange={(e) => onOpacityChange("clouds", parseFloat(e.target.value))}
                    className="w-full h-1 bg-[#2d3329] appearance-none cursor-pointer accent-[#e2ffcc]"
                  />
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default LayerControlDrawer;
