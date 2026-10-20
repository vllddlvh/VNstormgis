/**
 * @file MarkerVerificationMap.tsx
 * @description Component bản đồ kiểm định sự cố Leaflet Icon và trực quan hóa các cấp độ cảnh báo thời tiết.
 * Phục vụ nghiệm thu định lượng Task W1-11.
 *
 * @author VNstormgis Team - Frontend & GIS Specialist
 * @version 1.0.0
 */

import React, { useEffect, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup, ZoomControl } from "react-leaflet";
import "leaflet/dist/leaflet.css";

import { initLeafletIcons } from "./leafletConfig";
import { createWeatherStationIcon, createStormVortexIcon, SEVERITY_CONFIG } from "./markerFactory";
import {
  VIETNAM_DEFAULT_CENTER,
  VIETNAM_DEFAULT_ZOOM,
  formatCoordinate,
  calculateHaversineDistance,
} from "@/lib/geoUtils";
import { StationMarkerData, WeatherSeverityLevel } from "@/types/map";

// Danh mục 6 trạm mẫu đại diện cho 6 cấp độ rủi ro khí tượng trên toàn quốc
const MOCK_STATIONS: StationMarkerData[] = [
  {
    id: "loc-han",
    code: "HAN",
    name: "Trạm Khí Tượng Hà Nội",
    region: "Bắc Bộ",
    coordinates: [21.0285, 105.8542], // [Lat, Lng]
    severity: "SAFE",
    isActive: true,
    temperatureC: 28.5,
    rainfallMm: 2.4,
    windSpeedKmh: 12.0,
    windDirection: "Đông Bắc",
    lastUpdated: "17:00",
  },
  {
    id: "loc-hph",
    code: "HPH",
    name: "Trạm Khí Tượng Hải Phòng",
    region: "Duyên Hải Bắc Bộ",
    coordinates: [20.8449, 106.6881],
    severity: "ADVISORY",
    isActive: true,
    temperatureC: 27.2,
    rainfallMm: 24.0,
    windSpeedKmh: 28.5,
    windDirection: "Bắc",
    lastUpdated: "17:05",
  },
  {
    id: "loc-dng",
    code: "DNG",
    name: "Trạm Khí Tượng Đà Nẵng",
    region: "Trung Bộ",
    coordinates: [16.0544, 108.2022],
    severity: "WARNING",
    isActive: true,
    temperatureC: 25.8,
    rainfallMm: 78.5,
    windSpeedKmh: 48.0,
    windDirection: "Đông",
    lastUpdated: "17:10",
  },
  {
    id: "loc-bdh",
    code: "BDH",
    name: "Trạm Khí Tượng Quy Nhơn",
    region: "Nam Trung Bộ",
    coordinates: [13.783, 109.2197],
    severity: "SEVERE",
    isActive: true,
    temperatureC: 24.1,
    rainfallMm: 165.0,
    windSpeedKmh: 75.0,
    windDirection: "Tây Bắc",
    lastUpdated: "17:15",
  },
  {
    id: "loc-sgn",
    code: "SGN",
    name: "Trạm Khí Tượng TP. Hồ Chí Minh",
    region: "Nam Bộ",
    coordinates: [10.8231, 106.6297],
    severity: "EXTREME",
    isActive: true,
    temperatureC: 26.0,
    rainfallMm: 210.0,
    windSpeedKmh: 92.0,
    windDirection: "Tây Nam",
    lastUpdated: "17:20",
  },
  {
    id: "loc-tsa",
    code: "TSA",
    name: "Trạm Khí Tượng Quần Đảo Trường Sa",
    region: "Biển Đông",
    coordinates: [8.65, 114.3667],
    severity: "CATASTROPHIC",
    isActive: true,
    temperatureC: 23.5,
    rainfallMm: 340.0,
    windSpeedKmh: 135.0,
    windDirection: "Xoáy Mạnh",
    lastUpdated: "17:25",
  },
];

export const MarkerVerificationMap: React.FC = () => {
  const [selectedStation, setSelectedStation] = useState<StationMarkerData | null>(null);
  const [filterSeverity, setFilterSeverity] = useState<WeatherSeverityLevel | "ALL">("ALL");

  useEffect(() => {
    // 1. Kích hoạt việc khắc phục lỗi Leaflet Marker Icon 404
    initLeafletIcons();
  }, []);

  const filteredStations =
    filterSeverity === "ALL"
      ? MOCK_STATIONS
      : MOCK_STATIONS.filter((st) => st.severity === filterSeverity);

  return (
    <div className="w-full flex flex-col border border-[#2d3329] bg-[#161b13]">
      {/* Thanh Điều Khiển Bộ Lọc & Trạng Thái */}
      <div className="flex flex-wrap items-center justify-between p-3 border-b border-[#2d3329] bg-[#1a2117] text-xs font-mono">
        <div className="flex items-center space-x-2">
          <span className="text-[#84907f] uppercase font-bold tracking-wider">
            [Kiểm Định W1-11]
          </span>
          <span className="text-[#e2ffcc] font-bold">
            Leaflet 1.9 & Custom Weather Markers Engine
          </span>
        </div>

        {/* Nút lọc cấp độ cảnh báo */}
        <div className="flex items-center space-x-1.5 mt-2 sm:mt-0">
          <span className="text-[#84907f] mr-1">Bộ lọc:</span>
          {(
            ["ALL", "SAFE", "ADVISORY", "WARNING", "SEVERE", "EXTREME", "CATASTROPHIC"] as const
          ).map((level) => (
            <button
              key={level}
              onClick={() => setFilterSeverity(level)}
              className={`px-2 py-0.5 border text-[10px] uppercase transition-colors ${
                filterSeverity === level
                  ? "bg-[#e2ffcc] text-[#161b13] border-[#e2ffcc] font-bold"
                  : "bg-[#161b13] text-[#dde2e4] border-[#2d3329] hover:border-[#84907f]"
              }`}
            >
              {level}
            </button>
          ))}
        </div>
      </div>

      {/* Khung Nhìn Bản Đồ */}
      <div className="relative w-full h-[520px]">
        <MapContainer
          center={VIETNAM_DEFAULT_CENTER}
          zoom={VIETNAM_DEFAULT_ZOOM}
          minZoom={5}
          maxZoom={18}
          zoomControl={false}
          className="w-full h-full"
        >
          <ZoomControl position="bottomright" />

          {/* Lớp Bản Đồ Nền CartoDB Voyager Chuẩn */}
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            url="https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
            subdomains="abcd"
            maxZoom={20}
          />

          {/* Mốc 1: Leaflet Default Marker Đã Khắc Phục Lỗi (Tọa độ Đà Nẵng - Tâm Bản Đồ) */}
          <Marker position={VIETNAM_DEFAULT_CENTER}>
            <Popup>
              <div className="p-1 font-mono text-xs">
                <div className="text-[#e2ffcc] font-bold uppercase border-b border-[#2d3329] pb-1">
                  Leaflet Default Marker (Fixed)
                </div>
                <p className="text-[#dde2e4] mt-1.5">
                  Biểu tượng giọt nước mặc định đã được sửa lỗi Vite Bundler 404.
                </p>
                <div className="text-[10px] text-[#84907f] mt-1">
                  Tọa độ: {formatCoordinate(VIETNAM_DEFAULT_CENTER)}
                </div>
                <div className="mt-2 inline-block px-1.5 py-0.5 bg-[#e2ffcc]/10 border border-[#e2ffcc] text-[#e2ffcc] text-[9px] uppercase">
                  RSK-W1-03 Resolved
                </div>
              </div>
            </Popup>
          </Marker>

          {/* Mốc 2: Điểm Tâm Bão Thử Nghiệm (Biển Đông) */}
          <Marker position={[16.8, 112.5]} icon={createStormVortexIcon("BÃO SỐ 3 (YAGI)", 14)}>
            <Popup>
              <div className="p-1 font-mono text-xs">
                <div className="text-red-400 font-bold uppercase border-b border-[#2d3329] pb-1">
                  Tâm Bão Cấp 14 (Mẫu Kiểm Định)
                </div>
                <p className="text-[#dde2e4] mt-1.5">Vị trí: Vùng biển Bắc Quần đảo Hoàng Sa.</p>
                <div className="text-[10px] text-red-300 mt-1">
                  Gió giật: Cấp 17 (&gt; 200 km/h) — Tốc độ di chuyển: 15 km/h
                </div>
              </div>
            </Popup>
          </Marker>

          {/* Render Các Trạm Khí Tượng Theo Cấp Độ Cảnh Báo */}
          {filteredStations.map((station) => {
            const isSelected = selectedStation?.id === station.id;
            const distanceToCenter = calculateHaversineDistance(
              VIETNAM_DEFAULT_CENTER,
              station.coordinates,
            );

            return (
              <Marker
                key={station.id}
                position={station.coordinates}
                icon={createWeatherStationIcon(station.code, station.severity, isSelected)}
                eventHandlers={{
                  click: () => setSelectedStation(station),
                }}
              >
                <Popup>
                  <div className="p-1 font-mono text-xs min-w-[200px]">
                    <div className="flex items-center justify-between border-b border-[#2d3329] pb-1">
                      <span className="font-bold text-[#e2ffcc] uppercase">
                        {station.name} ({station.code})
                      </span>
                      <span
                        className="px-1 text-[9px] font-bold border"
                        style={{
                          backgroundColor: SEVERITY_CONFIG[station.severity].bgHex,
                          color: SEVERITY_CONFIG[station.severity].textHex,
                          borderColor: SEVERITY_CONFIG[station.severity].borderHex,
                        }}
                      >
                        {station.severity}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-1 mt-2 text-[11px] text-[#dde2e4]">
                      <div>Khu vực:</div>
                      <div className="text-right font-medium">{station.region}</div>

                      <div>Nhiệt độ:</div>
                      <div className="text-right font-medium text-amber-300">
                        {station.temperatureC}°C
                      </div>

                      <div>Lượng mưa:</div>
                      <div className="text-right font-medium text-cyan-300">
                        {station.rainfallMm} mm
                      </div>

                      <div>Tốc độ gió:</div>
                      <div className="text-right font-medium text-purple-300">
                        {station.windSpeedKmh} km/h ({station.windDirection})
                      </div>

                      <div>Khoảng cách:</div>
                      <div className="text-right text-[#84907f]">{distanceToCenter} km từ ĐN</div>
                    </div>

                    <div className="mt-2 pt-1 border-t border-[#2d3329] text-[10px] text-[#84907f] flex justify-between">
                      <span>{formatCoordinate(station.coordinates)}</span>
                      <span>{station.lastUpdated}</span>
                    </div>
                  </div>
                </Popup>
              </Marker>
            );
          })}
        </MapContainer>
      </div>

      {/* Chân Thẻ Chú Giải & Bảng Chỉ Số Nghiệp Vụ */}
      <div className="p-3 border-t border-[#2d3329] bg-[#1a2117] flex flex-wrap items-center justify-between gap-2 text-[11px] font-mono">
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-[#84907f] uppercase">Bảng màu cấp độ rủi ro:</span>
          {(["SAFE", "ADVISORY", "WARNING", "SEVERE", "EXTREME", "CATASTROPHIC"] as const).map(
            (sev) => (
              <div key={sev} className="flex items-center space-x-1">
                <span
                  className="w-3 h-3 border"
                  style={{
                    backgroundColor: SEVERITY_CONFIG[sev].bgHex,
                    borderColor: SEVERITY_CONFIG[sev].borderHex,
                  }}
                ></span>
                <span className="text-[#dde2e4] text-[10px]">{sev}</span>
              </div>
            ),
          )}
        </div>

        <div className="text-[#84907f] text-[10px]">
          Hiển thị {filteredStations.length}/{MOCK_STATIONS.length} trạm kiểm định
        </div>
      </div>
    </div>
  );
};

export default MarkerVerificationMap;
