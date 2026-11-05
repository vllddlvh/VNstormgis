/**
 * @file MarkerVerificationMap.tsx
 * @description Component bản đồ kiểm định sự cố Leaflet Icon và trực quan hóa các cấp độ cảnh báo thời tiết.
 * Phục vụ nghiệm thu định lượng Task W1-11.
 *
 * @author VNstormgis Team - Frontend & GIS Specialist
 * @version 1.0.0
 */

import React, { useEffect, useState } from "react";
import {
  MapContainer,
  TileLayer,
  Marker,
  Popup,
  ZoomControl,
} from "react-leaflet";
import "leaflet/dist/leaflet.css";

import { initLeafletIcons } from "./leafletConfig";
import { StormTrackLayer } from "./StormTrackLayer";
import {
  createWeatherStationIcon,
  createStormVortexIcon,
  createStormCenterIcon,
  getStormRiskLevel,
  STORM_RISK_CONFIG,
  SEVERITY_CONFIG,
} from "./markerFactory";
import { StormTrackData } from "@/lib/stormUtils";
import {
  DEFAULT_TILE_ID,
  TILE_PROVIDERS,
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

/**
 * Fixture kiểm định W3-05 — 3 bậc GDACS thật (Red/Orange/Green) + 1 chuỗi lạ (UNCLASSIFIED).
 * CHỈ sống ở tab Kiểm Định để soi màu/kết cấu icon; tab Tác chiến chỉ phản ánh API thật.
 */
interface StormLevelFixture {
  id: string;
  label: string;
  position: [number, number];
  category: string;
}

const STORM_LEVEL_FIXTURES: StormLevelFixture[] = [
  {
    id: "fixture-red",
    label: "RED-26",
    position: [15.2, 111.8],
    category: "Red",
  },
  {
    id: "fixture-orange",
    label: "ORANGE-26",
    position: [12.4, 114.2],
    category: "Orange",
  },
  {
    id: "fixture-green",
    label: "GREEN-26",
    position: [9.6, 110.8],
    category: "Green",
  },
  {
    id: "fixture-unknown",
    label: "UNKNOWN",
    position: [18.6, 116.0],
    category: "Unknown",
  },
];

/**
 * Fixture quỹ đạo W3-06 — dẫn xuất nguyên văn từ hợp đồng W3-02 Mục 3.5 (fixture YAGI đã
 * nghiệm thu W2-06/W3-02): 13 đỉnh historical [116.5,18.4] → [107.2,20.7], 3 đỉnh forecast
 * gồm ĐỈNH NỐI backend gắn sẵn, và 1 polygon hộp ví dụ 105–110°E/18–22°N (live GDACS không
 * bao giờ có impact_polygon). CHỈ sống ở tab Kiểm Định — cấm đưa vào tab Tác chiến
 * (SYS-RULE-004; tiền lệ fixture W3-05).
 */
const VERIFY_HISTORY_MILESTONES: Array<{
  latLng: [number, number];
  forecastTime: string;
  windSpeed?: number;
  windGust?: number;
  pressure?: number;
  category?: string;
}> = [
  { latLng: [18.4, 116.5], forecastTime: "2024-09-04T00:00:00Z" },
  { latLng: [18.6, 115.2], forecastTime: "2024-09-04T06:00:00Z" },
  { latLng: [18.7, 114.0], forecastTime: "2024-09-04T12:00:00Z" },
  { latLng: [18.9, 113.0], forecastTime: "2024-09-04T18:00:00Z" },
  { latLng: [19.0, 112.2], forecastTime: "2024-09-05T00:00:00Z" },
  { latLng: [19.2, 111.5], forecastTime: "2024-09-05T06:00:00Z" },
  { latLng: [19.4, 110.8], forecastTime: "2024-09-05T12:00:00Z" },
  { latLng: [19.6, 110.2], forecastTime: "2024-09-05T18:00:00Z" },
  { latLng: [19.8, 109.6], forecastTime: "2024-09-06T00:00:00Z" },
  { latLng: [20.1, 108.6], forecastTime: "2024-09-06T06:00:00Z" },
  {
    latLng: [20.4, 107.8],
    forecastTime: "2024-09-06T12:00:00Z",
    windSpeed: 52.4,
    windGust: 66.0,
    pressure: 970,
    category: "Red",
  },
  { latLng: [20.6, 107.5], forecastTime: "2024-09-06T18:00:00Z" },
  {
    latLng: [20.7, 107.2],
    forecastTime: "2024-09-07T00:00:00Z",
    windSpeed: 45.2,
    windGust: 58.0,
    pressure: 980,
    category: "Orange",
  },
];

const VERIFY_FORECAST_MILESTONES: Array<{
  latLng: [number, number];
  forecastTime: string;
  windSpeed?: number;
  windGust?: number;
  pressure?: number;
  category?: string;
}> = [
  {
    latLng: [20.9, 106.3],
    forecastTime: "2024-09-07T06:00:00Z",
    windSpeed: 38.6,
    windGust: 49.5,
    pressure: 992,
    category: "Orange",
  },
  {
    latLng: [21.1, 105.3],
    forecastTime: "2024-09-07T12:00:00Z",
    windSpeed: 28.0,
    windGust: 36.5,
    pressure: 998,
    category: "Green",
  },
];

const VERIFY_TRACK_FIXTURE: StormTrackData = {
  stormId: "verify-fixture",
  historyLatLngs: VERIFY_HISTORY_MILESTONES.map(
    (milestone) => milestone.latLng,
  ),
  // Đỉnh đầu = mốc historical cuối — ĐỈNH NỐI backend gắn sẵn (W3-02 #4), client không ghép thêm.
  forecastLatLngs: [
    VERIFY_HISTORY_MILESTONES[VERIFY_HISTORY_MILESTONES.length - 1].latLng,
    ...VERIFY_FORECAST_MILESTONES.map((milestone) => milestone.latLng),
  ],
  impactRings: [
    [
      [18.0, 105.0],
      [18.0, 110.0],
      [22.0, 110.0],
      [22.0, 105.0],
      [18.0, 105.0],
    ],
  ],
  impactRadiusKm: 150,
  points: [
    ...VERIFY_HISTORY_MILESTONES.map((milestone, index) => ({
      trackId: 9001 + index,
      latLng: milestone.latLng,
      trackType: "historical" as const,
      forecastTime: milestone.forecastTime,
      windSpeed: milestone.windSpeed ?? null,
      windGust: milestone.windGust ?? null,
      pressure: milestone.pressure ?? null,
      category: milestone.category ?? null,
    })),
    ...VERIFY_FORECAST_MILESTONES.map((milestone, index) => ({
      trackId: 9101 + index,
      latLng: milestone.latLng,
      trackType: "forecast" as const,
      forecastTime: milestone.forecastTime,
      windSpeed: milestone.windSpeed ?? null,
      windGust: milestone.windGust ?? null,
      pressure: milestone.pressure ?? null,
      category: milestone.category ?? null,
    })),
  ],
};

const VERIFY_TRACK_TRACKS: Record<string, StormTrackData> = {
  "verify-fixture": VERIFY_TRACK_FIXTURE,
};

export const MarkerVerificationMap: React.FC = () => {
  const [selectedStation, setSelectedStation] =
    useState<StationMarkerData | null>(null);
  const [filterSeverity, setFilterSeverity] = useState<
    WeatherSeverityLevel | "ALL"
  >("ALL");

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
            [
              "ALL",
              "SAFE",
              "ADVISORY",
              "WARNING",
              "SEVERE",
              "EXTREME",
              "CATASTROPHIC",
            ] as const
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

          {/* Lớp Bản Đồ Nền OSM Germany — keyless, tránh watermark CARTO (OBS-03) */}
          <TileLayer
            attribution={TILE_PROVIDERS[DEFAULT_TILE_ID].attribution}
            url={TILE_PROVIDERS[DEFAULT_TILE_ID].url}
            maxZoom={TILE_PROVIDERS[DEFAULT_TILE_ID].maxZoom}
          />

          {/* Mốc 1: Leaflet Default Marker Đã Khắc Phục Lỗi (Tọa độ Đà Nẵng - Tâm Bản Đồ) */}
          <Marker position={VIETNAM_DEFAULT_CENTER}>
            <Popup>
              <div className="p-1 font-mono text-xs">
                <div className="text-[#e2ffcc] font-bold uppercase border-b border-[#2d3329] pb-1">
                  Leaflet Default Marker (Fixed)
                </div>
                <p className="text-[#dde2e4] mt-1.5">
                  Biểu tượng giọt nước mặc định đã được sửa lỗi Vite Bundler
                  404.
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
          <Marker
            position={[16.8, 112.5]}
            icon={createStormVortexIcon("BÃO SỐ 3 (YAGI)", 14)}
          >
            <Popup>
              <div className="p-1 font-mono text-xs">
                <div className="text-red-400 font-bold uppercase border-b border-[#2d3329] pb-1">
                  Tâm Bão Cấp 14 (Mẫu Kiểm Định)
                </div>
                <p className="text-[#dde2e4] mt-1.5">
                  Vị trí: Vùng biển Bắc Quần đảo Hoàng Sa.
                </p>
                <div className="text-[10px] text-red-300 mt-1">
                  Gió giật: Cấp 17 (&gt; 200 km/h) — Tốc độ di chuyển: 15 km/h
                </div>
              </div>
            </Popup>
          </Marker>

          {/* W3-05: mẫu tâm bão Red/Orange/Green/Unknown — kiểm định màu + popup,
              KHÔNG phải luồng dữ liệu thật (dữ liệu thật chỉ ở tab Tác chiến). */}
          {STORM_LEVEL_FIXTURES.map((fixture) => {
            const level = getStormRiskLevel(fixture.category);
            const risk = STORM_RISK_CONFIG[level];
            return (
              <Marker
                key={fixture.id}
                position={fixture.position}
                icon={createStormCenterIcon(level, fixture.label)}
              >
                <Popup>
                  <div className="p-1 font-mono text-xs min-w-[200px]">
                    <div className="flex items-center justify-between border-b border-[#2d3329] pb-1">
                      <span className="font-bold text-[#e2ffcc] uppercase">
                        [{fixture.id} // {fixture.category.toUpperCase()}]
                      </span>
                      <span
                        className="px-1 text-[9px] font-bold border"
                        style={{
                          borderColor: risk.colorHex,
                          color: risk.colorHex,
                        }}
                      >
                        {risk.label}
                      </span>
                    </div>
                    <div className="mt-1.5 text-[10px] text-[#84907f]">
                      Mẫu kiểm định cấp GDACS W3-05 — không phải dữ liệu tác
                      chiến.
                    </div>
                    <div className="mt-1 text-[10px] text-[#dde2e4]">
                      {formatCoordinate(fixture.position, 4)}
                    </div>
                  </div>
                </Popup>
              </Marker>
            );
          })}

          {/* W3-06: mẫu quỹ đạo history/forecast/impact — kiểm định màu, dashArray và tooltip mốc,
              KHÔNG phải luồng dữ liệu thật (tiền lệ fixture W3-05). */}
          <StormTrackLayer tracks={VERIFY_TRACK_TRACKS} />

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
                icon={createWeatherStationIcon(
                  station.code,
                  station.severity,
                  isSelected,
                )}
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
                          backgroundColor:
                            SEVERITY_CONFIG[station.severity].bgHex,
                          color: SEVERITY_CONFIG[station.severity].textHex,
                          borderColor:
                            SEVERITY_CONFIG[station.severity].borderHex,
                        }}
                      >
                        {station.severity}
                      </span>
                    </div>

                    <div className="grid grid-cols-2 gap-1 mt-2 text-[11px] text-[#dde2e4]">
                      <div>Khu vực:</div>
                      <div className="text-right font-medium">
                        {station.region}
                      </div>

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
                      <div className="text-right text-[#84907f]">
                        {distanceToCenter} km từ ĐN
                      </div>
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
          <span className="text-[#84907f] uppercase">
            Bảng màu cấp độ rủi ro:
          </span>
          {(
            [
              "SAFE",
              "ADVISORY",
              "WARNING",
              "SEVERE",
              "EXTREME",
              "CATASTROPHIC",
            ] as const
          ).map((sev) => (
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
          ))}
        </div>

        <div className="text-[#84907f] text-[10px]">
          Hiển thị {filteredStations.length}/{MOCK_STATIONS.length} trạm kiểm
          định
        </div>
      </div>

      {/* Chú giải W3-06: quỹ đạo bão */}
      <div className="px-3 py-2 border-t border-[#2d3329] bg-[#161b13] flex flex-wrap items-center gap-4 text-[10px] font-mono text-[#84907f]">
        <span className="uppercase">Quỹ đạo W3-06:</span>
        <div className="flex items-center space-x-1">
          <span className="w-6 h-0.5 bg-[#dde2e4]" />
          <span className="text-[#dde2e4]">Lịch sử (liền)</span>
        </div>
        <div className="flex items-center space-x-1">
          <span className="w-6 h-0.5 bg-[#f59e0b]" />
          <span className="text-[#f59e0b]">Dự báo (đứt nét)</span>
        </div>
        <div className="flex items-center space-x-1">
          <span className="w-3 h-3 border border-[#dc2626] bg-[#dc2626]/15" />
          <span className="text-[#dc2626]">Vùng nguy hiểm</span>
        </div>
        <span className="text-[#84907f]">
          Zoom ≥ 8 hiện nhãn mốc thường trực (hover luôn có tooltip)
        </span>
      </div>
    </div>
  );
};

export default MarkerVerificationMap;
