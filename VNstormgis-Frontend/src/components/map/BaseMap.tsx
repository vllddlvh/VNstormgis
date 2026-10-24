/**
 * @file BaseMap.tsx
 * @description Bản đồ số trung tâm WebGIS định vị toàn vẹn lãnh thổ Việt Nam và Biển Đông.
 * Cấu hình hệ tọa độ EPSG:3857, ràng buộc MaxBounds, chống lỗi mảnh gạch xám bằng ResizeObserver,
 * và tích hợp đa tầng dữ liệu khí tượng chuẩn phong cách San Rita (alpine field guide).
 *
 * @author VNstormgis Team - Frontend & GIS Specialist
 * @version 1.0.0
 */

import React, { useEffect, useRef, useState } from "react";
import {
  MapContainer,
  TileLayer,
  Marker,
  Popup,
  Polyline,
  Circle,
  useMap,
  useMapEvents,
} from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";

import { initLeafletIcons } from "./leafletConfig";
import { createWeatherStationIcon, createStormVortexIcon, SEVERITY_CONFIG } from "./markerFactory";
import { RainfallLayer } from "./RainfallLayer";
import { WindLayer } from "./WindLayer";
import {
  VIETNAM_DEFAULT_CENTER,
  VIETNAM_DEFAULT_ZOOM,
  VIETNAM_MAX_BOUNDS,
  TILE_PROVIDERS,
  formatCoordinate,
} from "@/lib/geoUtils";
import {
  CoordinateTuple,
  StationMarkerData,
  LayerVisibilityState,
  ActiveCycloneData,
} from "@/types/map";
import { INITIAL_MOCK_STATIONS, SAMPLE_ACTIVE_CYCLONE } from "@/data/mockMapData";

// Đảm bảo Leaflet Icon luôn được khởi tạo an toàn
initLeafletIcons();

/**
 * Controller nội bộ điều phối các sự kiện Leaflet Map Instance,
 * quản lý chu kỳ kích thước (ResizeObserver) và phát tín hiệu Telemetry.
 */
interface MapControllerProps {
  onMapReady?: (map: L.Map) => void;
  onMouseMoveCoords?: (coords: CoordinateTuple | null) => void;
  onMapStateChange?: (state: { center: CoordinateTuple; zoom: number }) => void;
}

function MapViewportObserver({
  onMapReady,
  onMouseMoveCoords,
  onMapStateChange,
}: MapControllerProps) {
  const map = useMap();

  useEffect(() => {
    if (onMapReady) {
      onMapReady(map);
    }

    // 1. Khắc phục lỗi mảng gạch xám: ép buộc map.invalidateSize() sau khi mount DOM
    const initialTimer = setTimeout(() => {
      map.invalidateSize();
    }, 150);

    // 2. Theo dõi thay đổi kích thước DOM của container bằng ResizeObserver
    const container = map.getContainer();
    const resizeObserver = new ResizeObserver(() => {
      map.invalidateSize();
    });
    resizeObserver.observe(container);

    // 3. Theo dõi sự kiện resize của window
    const handleWindowResize = () => {
      map.invalidateSize();
    };
    window.addEventListener("resize", handleWindowResize);

    return () => {
      clearTimeout(initialTimer);
      resizeObserver.disconnect();
      window.removeEventListener("resize", handleWindowResize);
    };
  }, [map, onMapReady]);

  // Lắng nghe sự kiện di chuyển chuột và thay đổi khung nhìn
  useMapEvents({
    mousemove(e) {
      if (onMouseMoveCoords) {
        onMouseMoveCoords([e.latlng.lat, e.latlng.lng]);
      }
    },
    mouseout() {
      if (onMouseMoveCoords) {
        onMouseMoveCoords(null);
      }
    },
    moveend() {
      if (onMapStateChange) {
        const center = map.getCenter();
        onMapStateChange({
          center: [center.lat, center.lng],
          zoom: map.getZoom(),
        });
      }
    },
  });

  return null;
}

export interface BaseMapProps {
  /** Danh sách trạm quan trắc (nếu không truyền sẽ dùng INITIAL_MOCK_STATIONS) */
  stations?: StationMarkerData[];
  /** Cơn bão nhiệt đới đang hoạt động trên biển Đông */
  activeCyclone?: ActiveCycloneData;
  /** ID của trạm đang được lựa chọn tâm điểm */
  selectedStationId?: string | null;
  /** Callback khi người dùng chọn trạm */
  onStationSelect?: (station: StationMarkerData) => void;
  /** Nguồn bản đồ nền hiện tại: 'voyager' | 'dark' | 'osm' */
  activeTileId?: string;
  /** Trạng thái bật/tắt các lớp dữ liệu không gian */
  layerVisibility?: LayerVisibilityState;
  /** Mức độ trong suốt (opacity) cho các lớp phủ (0.0 đến 1.0) */
  layerOpacity?: { [layerId: string]: number };
  /** Callback trả về tọa độ con trỏ chuột thời gian thực */
  onMouseMoveCoords?: (coords: CoordinateTuple | null) => void;
  /** Callback trả về tâm bản đồ và mức zoom khi người dùng pan/zoom */
  onMapStateChange?: (state: { center: CoordinateTuple; zoom: number }) => void;
  /** Hook chuyển giao instance Leaflet Map ra ngoài */
  onMapReady?: (map: L.Map) => void;
  /** Callback khi người dùng nhấn nút Phóng to trên popup trạm */
  onFlyToStation?: (station: StationMarkerData) => void;
  /** Callback khi người dùng nhấn nút Hỏi AI về trạm trên popup */
  onAskAi?: (station: StationMarkerData) => void;
  /** Lớp CSS bổ sung */
  className?: string;
}

const DEFAULT_LAYER_VISIBILITY: LayerVisibilityState = {
  stations: true,
  rainfall: true,
  radar: false,
  wind: false,
  clouds: false,
  cyclones: true,
};

export const BaseMap: React.FC<BaseMapProps> = ({
  stations = INITIAL_MOCK_STATIONS,
  activeCyclone = SAMPLE_ACTIVE_CYCLONE,
  selectedStationId,
  onStationSelect,
  activeTileId = "voyager",
  layerVisibility = DEFAULT_LAYER_VISIBILITY,
  layerOpacity = { radar: 0.65, wind: 0.7, clouds: 0.5 },
  onMouseMoveCoords,
  onMapStateChange,
  onMapReady,
  onFlyToStation,
  onAskAi,
  className = "",
}) => {
  const mapRef = useRef<L.Map | null>(null);
  const [internalTileId, setInternalTileId] = useState<string>(activeTileId);

  // Đồng bộ nguồn bản đồ nếu prop thay đổi
  useEffect(() => {
    setInternalTileId(activeTileId);
  }, [activeTileId]);

  const tileConfig = TILE_PROVIDERS[internalTileId] || TILE_PROVIDERS.voyager;

  const handleInternalMapReady = (map: L.Map) => {
    mapRef.current = map;
    if (onMapReady) {
      onMapReady(map);
    }
  };

  return (
    <div className={`relative w-full h-full bg-[#161b13] overflow-hidden ${className}`}>
      <MapContainer
        center={VIETNAM_DEFAULT_CENTER}
        zoom={VIETNAM_DEFAULT_ZOOM}
        minZoom={5}
        maxZoom={18}
        maxBounds={VIETNAM_MAX_BOUNDS}
        maxBoundsViscosity={0.8}
        zoomControl={false}
        attributionControl={false}
        className="w-full h-full z-0 font-mono"
      >
        {/* Nạp TileLayer nền tác chiến (CartoDB Voyager / Dark Matter / OSM) */}
        <TileLayer
          key={tileConfig.id}
          url={tileConfig.url}
          attribution={tileConfig.attribution}
          subdomains={tileConfig.subdomains}
          maxZoom={tileConfig.maxZoom}
        />

        {/* Bộ quan sát khung nhìn và xử lý Resize / Telemetry */}
        <MapViewportObserver
          onMapReady={handleInternalMapReady}
          onMouseMoveCoords={onMouseMoveCoords}
          onMapStateChange={onMapStateChange}
        />

        {/* LỚP 01: VÙNG MƯA RADAR PHẢN HỒI (SIMULATED RADAR CELLS) */}
        {layerVisibility.radar && (
          <>
            {/* Phản hồi mưa lớn khu vực Đà Nẵng - Thừa Thiên Huế */}
            <Circle
              center={[16.15, 108.1]}
              radius={75000}
              pathOptions={{
                color: "#ea580c",
                weight: 1,
                fillColor: "#ea580c",
                fillOpacity: layerOpacity.radar || 0.65,
                dashArray: "4, 4",
              }}
            />
            <Circle
              center={[16.05, 108.2]}
              radius={35000}
              pathOptions={{
                color: "#dc2626",
                weight: 1.5,
                fillColor: "#dc2626",
                fillOpacity: (layerOpacity.radar || 0.65) + 0.15,
              }}
            />

            {/* Phản hồi mưa khu vực TP. Hồ Chí Minh - Đồng Nai */}
            <Circle
              center={[10.78, 106.7]}
              radius={60000}
              pathOptions={{
                color: "#ea580c",
                weight: 1,
                fillColor: "#ea580c",
                fillOpacity: layerOpacity.radar || 0.65,
              }}
            />
          </>
        )}

        {/* LỚP 02: LƯỚI MÂY VỆ TINH (SATELLITE CLOUD COVER) */}
        {layerVisibility.clouds && (
          <Circle
            center={[17.0, 112.5]}
            radius={320000}
            pathOptions={{
              color: "#dde2e4",
              weight: 1,
              fillColor: "#ffffff",
              fillOpacity: layerOpacity.clouds || 0.45,
              dashArray: "6, 6",
            }}
          />
        )}

        {/* LỚP 04: CƠN BÃO NHIỆT ĐỚI ĐANG HOẠT ĐỘNG TRÊN BIỂN ĐÔNG (ACTIVE CYCLONE) */}
        {layerVisibility.cyclones && activeCyclone && (
          <>
            {/* Đường dự báo quỹ đạo di chuyển của tâm bão */}
            <Polyline
              positions={activeCyclone.forecastTrack}
              pathOptions={{
                color: "#dc2626",
                weight: 2.5,
                dashArray: "6, 8",
                opacity: 0.9,
              }}
            />

            {/* Vùng bán kính gió mạnh nguy hiểm (R30/R50) */}
            <Circle
              center={activeCyclone.coordinates}
              radius={180000}
              pathOptions={{
                color: "#dc2626",
                weight: 1,
                fillColor: "#dc2626",
                fillOpacity: 0.15,
                dashArray: "4, 4",
              }}
            />

            {/* Marker Tâm bão với hiệu ứng lốc xoáy San Rita */}
            <Marker
              position={activeCyclone.coordinates}
              icon={createStormVortexIcon(activeCyclone.name, activeCyclone.category)}
            >
              <Popup className="san-rita-popup">
                <div className="p-3 bg-[#161b13] text-[#dde2e4] space-y-2 select-none min-w-[220px]">
                  <div className="flex items-center justify-between border-b border-[#2d3329] pb-1.5">
                    <span className="text-[10px] text-[#dc2626] font-bold uppercase tracking-wider">
                      [TÂM BÃO KHẨN CẤP]
                    </span>
                    <span className="text-[9px] px-1 py-0.5 border border-[#dc2626] text-[#e2ffcc] bg-[#1a2117]">
                      CẤP {activeCyclone.category}
                    </span>
                  </div>
                  <div className="font-display text-lg text-[#e2ffcc] tracking-wide uppercase">
                    {activeCyclone.name}
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-[10px]">
                    <div className="border border-[#2d3329] p-1.5 bg-[#1a2117]">
                      <span className="text-[#84907f] block">KHÍ ÁP TÂM</span>
                      <span className="font-bold text-[#dde2e4]">
                        {activeCyclone.pressureHpa} hPa
                      </span>
                    </div>
                    <div className="border border-[#2d3329] p-1.5 bg-[#1a2117]">
                      <span className="text-[#84907f] block">SỨC GIÓ</span>
                      <span className="font-bold text-[#ea580c]">
                        {activeCyclone.windSpeedKmh} km/h
                      </span>
                    </div>
                  </div>
                  <div className="text-[9px] text-[#84907f] pt-1 border-t border-[#2d3329] flex justify-between">
                    <span>TỌA ĐỘ TÂM:</span>
                    <span className="text-[#dde2e4]">
                      {formatCoordinate(activeCyclone.coordinates, 2)}
                    </span>
                  </div>
                </div>
              </Popup>
            </Marker>
          </>
        )}

        {/* LỚP 04B: LƯỢNG MƯA THỜI GIAN THỰC (RAINFALL BUBBLES — SR-MAP-002, W2-08)
            Render TRƯỚC khối marker trạm để icon trạm + popup luôn nhận click (RSK-W2-04). */}
        {layerVisibility.rainfall && <RainfallLayer stations={stations} />}

        {/* LỚP 04C: TRƯỜNG VECTOR GIÓ THỜI GIAN THỰC (WIND VECTORS — SR-MAP-003, W2-09)
            Mũi tên SVG xoay theo chiều dòng gió (FROM + 180°), mã màu Beaufort 4 bậc.
            Render sau RainfallLayer để nằm trên vòng tròn mưa; WindLayer tự nâng z-index
            để đầu mũi tên không bị icon trạm che, trong khi thân icon trạm vẫn nhận click. */}
        {layerVisibility.wind && (
          <WindLayer stations={stations} opacity={layerOpacity.wind ?? 0.7} />
        )}

        {/* LỚP 05: DANH MỤC TRẠM QUAN TRẮC KHÍ TƯỢNG THỦY VĂN (STATION MARKERS) */}
        {layerVisibility.stations &&
          stations.map((station) => {
            const isSelected = selectedStationId === station.id;
            const severityInfo = SEVERITY_CONFIG[station.severity];

            return (
              <Marker
                key={station.id}
                position={station.coordinates}
                icon={createWeatherStationIcon(station.code, station.severity, isSelected)}
                eventHandlers={{
                  click: () => {
                    if (onStationSelect) {
                      onStationSelect(station);
                    }
                  },
                }}
              >
                <Popup className="san-rita-popup">
                  <div className="p-3 bg-[#161b13] text-[#dde2e4] space-y-2.5 select-none min-w-[260px] max-w-[320px]">
                    {/* Header Popup: [CODE // TÊN TRẠM] */}
                    <div className="flex items-center justify-between border-b border-[#2d3329] pb-1.5">
                      <div className="flex items-center gap-1.5 truncate">
                        <span className="font-bold text-[#e2ffcc] text-xs">
                          [{station.code} // {station.name.toUpperCase()}]
                        </span>
                      </div>
                      <span
                        className="text-[9px] px-1.5 py-0.5 border font-bold uppercase shrink-0"
                        style={{
                          borderColor: severityInfo.borderHex,
                          backgroundColor: severityInfo.bgHex,
                          color: severityInfo.textHex,
                        }}
                      >
                        {severityInfo.name}
                      </span>
                    </div>

                    {/* Vùng địa lý & Trạng thái trắc địa */}
                    <div className="flex items-center justify-between text-[10px] text-[#84907f]">
                      <span className="uppercase">
                        VÙNG: <strong className="text-[#dde2e4]">{station.region}</strong>
                      </span>
                      <span className="text-[#e2ffcc] flex items-center gap-1">
                        <span className="w-1.5 h-1.5 bg-[#e2ffcc] inline-block" />
                        HOẠT ĐỘNG
                      </span>
                    </div>

                    {/* Thông số đo đạc khí tượng thủy văn TỪ API /weather/current (W2-08) */}
                    {station.weather ? (
                      <>
                        <div className="text-[9px] text-[#84907f] uppercase pb-1">
                          {station.weather.weather_description}
                        </div>
                        <div className="grid grid-cols-2 gap-1.5 text-[10px]">
                          <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
                            <span className="text-[8px] text-[#84907f] block uppercase">
                              NHIỆT ĐỘ
                            </span>
                            <span className="font-bold text-[#dde2e4]">
                              {station.weather.temperature !== null
                                ? `${station.weather.temperature}°C`
                                : "--"}
                              {station.weather.feels_like !== null && (
                                <span className="text-[8px] font-normal text-[#84907f]">
                                  {" "}
                                  (cảm nhận {station.weather.feels_like}°C)
                                </span>
                              )}
                            </span>
                          </div>
                          <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
                            <span className="text-[8px] text-[#84907f] block uppercase">
                              MƯA 1H / 3H
                            </span>
                            <span
                              className={`font-bold ${
                                (station.weather.rain_1h ?? 0) >= 50
                                  ? "text-[#e53e3e]"
                                  : "text-[#dde2e4]"
                              }`}
                            >
                              {station.weather.rain_1h !== null
                                ? `${station.weather.rain_1h} mm`
                                : "--"}
                              {" / "}
                              {station.weather.rain_3h !== null
                                ? `${station.weather.rain_3h} mm`
                                : "--"}
                            </span>
                          </div>
                          <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
                            <span className="text-[8px] text-[#84907f] block uppercase">
                              GIÓ / GIẬT
                            </span>
                            <span className="font-bold text-[#dde2e4]">
                              {station.weather.wind_speed !== null
                                ? `${station.weather.wind_speed} m/s`
                                : "--"}
                              {station.weather.wind_gust !== null && (
                                <span className="text-[8px] font-normal text-[#84907f]">
                                  {" "}
                                  (giật {station.weather.wind_gust} m/s)
                                </span>
                              )}
                            </span>
                          </div>
                          <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
                            <span className="text-[8px] text-[#84907f] block uppercase">
                              HƯỚNG GIÓ
                            </span>
                            <span className="font-bold text-[#dde2e4]">
                              {station.weather.wind_direction !== null
                                ? `${station.weather.wind_direction}°`
                                : "--"}
                            </span>
                          </div>
                          <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
                            <span className="text-[8px] text-[#84907f] block uppercase">
                              ĐỘ ẨM
                            </span>
                            <span className="font-bold text-[#dde2e4]">
                              {station.weather.humidity !== null
                                ? `${station.weather.humidity}%`
                                : "--"}
                            </span>
                          </div>
                          <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
                            <span className="text-[8px] text-[#84907f] block uppercase">
                              ÁP SUẤT
                            </span>
                            <span className="font-bold text-[#dde2e4]">
                              {station.weather.pressure !== null
                                ? `${station.weather.pressure} hPa`
                                : "--"}
                            </span>
                          </div>
                          <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
                            <span className="text-[8px] text-[#84907f] block uppercase">
                              ĐỘ CAO ĐỊA HÌNH
                            </span>
                            <span className="font-bold text-[#dde2e4]">
                              {station.elevationM !== undefined ? `${station.elevationM} m` : "--"}
                            </span>
                          </div>
                        </div>

                        {/* Thời điểm đo + nguồn cấp dữ liệu (BR-RULE-006) */}
                        <div className="pt-1.5 border-t border-[#2d3329] flex justify-between text-[9px] text-[#84907f]">
                          <span>
                            ĐO LƯỜNG:{" "}
                            <strong className="text-[#e2ffcc]">
                              {new Date(station.weather.recorded_at).toLocaleTimeString("vi-VN", {
                                hour: "2-digit",
                                minute: "2-digit",
                                hour12: false,
                              })}
                            </strong>
                          </span>
                          <span>
                            NGUỒN: <strong className="text-[#dde2e4]">OpenWeatherMap</strong>
                          </span>
                        </div>
                      </>
                    ) : (
                      <>
                        <div className="text-[10px] text-[#84907f] border border-[#2d3329] bg-[#1a2117] p-1.5">
                          [CHƯA CÓ BẢN GHI THỜI TIẾT] — trạm chờ chu kỳ nạp OWM kế tiếp.
                        </div>
                        <div className="pt-1.5 border-t border-[#2d3329] flex items-center justify-between text-[9px] text-[#84907f]">
                          <span>ĐỘ CAO ĐỊA HÌNH:</span>
                          <span className="text-[#dde2e4]">
                            {station.elevationM !== undefined ? `${station.elevationM} m` : "--"}
                          </span>
                        </div>
                      </>
                    )}

                    {/* Tọa độ trắc địa chuẩn WGS84 */}
                    <div className="pt-1.5 border-t border-[#2d3329] flex items-center justify-between text-[9px] text-[#84907f]">
                      <span>TỌA ĐỘ WGS84:</span>
                      <span className="text-[#e2ffcc] font-mono">
                        {formatCoordinate(station.coordinates, 4)}
                      </span>
                    </div>

                    {/* QuickActions: Phóng to khu vực & Hỏi AI về trạm này */}
                    <div className="pt-2 border-t border-[#2d3329] grid grid-cols-2 gap-1.5">
                      <button
                        id={`btn-flyto-${station.code.toLowerCase()}`}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (onFlyToStation) {
                            onFlyToStation(station);
                          }
                        }}
                        className="py-1 px-1.5 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#1a2117] text-[9px] text-[#dde2e4] hover:text-[#e2ffcc] flex items-center justify-center gap-1 transition-colors uppercase font-bold"
                        title={`Phóng to khu vực trạm ${station.name}`}
                      >
                        <span>🔍 PHÓNG TO</span>
                      </button>
                      <button
                        id={`btn-askai-${station.code.toLowerCase()}`}
                        onClick={(e) => {
                          e.stopPropagation();
                          if (onAskAi) {
                            onAskAi(station);
                          }
                        }}
                        className="py-1 px-1.5 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#1a2117] text-[9px] text-[#e2ffcc] flex items-center justify-center gap-1 transition-colors uppercase font-bold"
                        title={`Hỏi trợ lý AI phân tích trạm ${station.name}`}
                      >
                        <span>🤖 HỎI AI</span>
                      </button>
                    </div>
                  </div>
                </Popup>
              </Marker>
            );
          })}
      </MapContainer>
    </div>
  );
};

export default BaseMap;
