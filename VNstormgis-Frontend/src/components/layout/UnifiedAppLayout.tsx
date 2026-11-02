/**
 * @file UnifiedAppLayout.tsx
 * @description Khung bố cục dashboard hợp nhất (Unified Dashboard Layout) chuẩn San Rita Alpine Field Guide.
 * Tích hợp Header Chrome, Canvas bản đồ BaseMap.tsx toàn màn hình, các bảng điều khiển dạng nổi
 * (LayerControl, MapNavigationControls, AlertTickerBar, AiChatDrawer) và Sub-Footer trắc địa WGS84.
 *
 * @author VNstormgis Team - Frontend & Software Architect
 * @version 1.0.0
 */

import React, {
  useState,
  useRef,
  useEffect,
  useCallback,
  useMemo,
} from "react";
import L from "leaflet";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { HeaderNavbar } from "./HeaderNavbar";
import { DisclaimerFooter } from "./DisclaimerFooter";
import { BaseMap } from "@/components/map/BaseMap";
import { LayerControlDrawer } from "@/components/dashboard/LayerControlDrawer";
import { MapNavigationControls } from "@/components/dashboard/MapNavigationControls";
import { AlertTickerBar } from "@/components/dashboard/AlertTickerBar";
import { AiChatDrawer } from "@/components/dashboard/AiChatDrawer";
import {
  CoordinateTuple,
  LayerVisibilityState,
  WeatherAlertItem,
  WeatherSeverityLevel,
  StationMarkerData,
} from "@/types/map";
import { RainSeverityLevel } from "@/types/weather";
import { StatusType } from "@/components/common/StatusBadge";
import {
  VIETNAM_DEFAULT_CENTER,
  VIETNAM_DEFAULT_ZOOM,
  VIETNAM_MAX_BOUNDS,
} from "@/lib/geoUtils";
import { locationService } from "@/services/locationService";
import { weatherService } from "@/services/weatherService";
import { stormService } from "@/services/stormService";
import {
  StormCenter,
  StormTrackData,
  latestStormUpdatedAt,
  toStormCenters,
  toStormTrackData,
} from "@/lib/stormUtils";
import { STORM_REFRESH_INTERVAL_MS } from "@/types/storm";
import {
  INITIAL_MOCK_STATIONS,
  SAMPLE_WEATHER_ALERTS,
} from "@/data/mockMapData";

/** Cầu nối thang 4 bậc mưa (backend) → thang 6 bậc icon trạm (markerFactory). */
const RAIN_SEVERITY_TO_MARKER: Record<RainSeverityLevel, WeatherSeverityLevel> =
  {
    NONE: "SAFE",
    MODERATE: "ADVISORY",
    WARNING: "WARNING",
    DANGER: "SEVERE",
  };

/** Zoom khi bay tới tâm bão — đủ thấy quỹ đạo lân cận; người dùng tự zoom thêm để mở nhãn mốc (≥ 8). */
const STORM_FOCUS_ZOOM = 7;

export interface UnifiedAppLayoutProps {
  /** Component con hiển thị khi chuyển sang tab Kiểm Định (Diagnostics) */
  diagnosticsContent?: React.ReactNode;
}

export const UnifiedAppLayout: React.FC<UnifiedAppLayoutProps> = ({
  diagnosticsContent,
}) => {
  // 1. Quản lý Leaflet Map Instance
  const mapRef = useRef<L.Map | null>(null);

  // 2. Trạng thái kết nối Backend Spring Boot & Dữ liệu trạm PostGIS
  const [backendStatus, setBackendStatus] = useState<StatusType>("loading");
  const [stationCount, setStationCount] = useState<number | null>(null);
  const [stations, setStations] = useState<StationMarkerData[]>(
    INITIAL_MOCK_STATIONS,
  );
  const [selectedRegion, setSelectedRegion] = useState<string>("ALL");
  const [aiPrompt, setAiPrompt] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  // 3. Quản lý Telemetry tọa độ và mức zoom
  const [mapCenter, setMapCenter] = useState<CoordinateTuple>(
    VIETNAM_DEFAULT_CENTER,
  );
  const [mapZoom, setMapZoom] = useState<number>(VIETNAM_DEFAULT_ZOOM);
  const [cursorCoords, setCursorCoords] = useState<CoordinateTuple | null>(
    null,
  );

  // 4. Quản lý Chế độ hiển thị & Nguồn bản đồ nền
  const [activeTab, setActiveTab] = useState<"operations" | "diagnostics">(
    "operations",
  );
  const [activeTileId, setActiveTileId] = useState<string>("voyager");
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);

  // 5. Quản lý Trạng thái các lớp chuyên đề
  const [layerVisibility, setLayerVisibility] = useState<LayerVisibilityState>({
    stations: true,
    rainfall: true,
    radar: false,
    wind: true,
    clouds: false,
    cyclones: true,
  });

  // Thời điểm đồng bộ dữ liệu thành công gần nhất (ISO 8601) — hiển thị trên huy hiệu Last Sync
  const [lastSyncedAt, setLastSyncedAt] = useState<string | null>(null);

  // 5b. Dữ liệu bão active (SR-STM-002 — W3-05) — nhánh độc lập, lỗi storm không đổi backendStatus.
  const [stormCenters, setStormCenters] = useState<StormCenter[]>([]);
  const [stormState, setStormState] = useState<
    "idle" | "loading" | "ready" | "error"
  >("idle");
  const [lastStormSyncedAt, setLastStormSyncedAt] = useState<string | null>(
    null,
  );

  // 5d. Chế độ tác nghiệp bão (W3-07 quyết định #7) — thả VIETNAM_MAX_BOUNDS tạm thời khi bay tới bão,
  // khôi phục duy nhất tại VỀ TOÀN CẢNH. Lộ trạng thái qua data-storm-ops-mode để kiểm thử.
  const [stormOpsMode, setStormOpsMode] = useState<boolean>(false);

  // 5c. Quỹ đạo bão active (SR-STM-003 — W3-06) — nạp EAGER cho mọi bão active trong cùng chu kỳ.
  const [stormTracks, setStormTracks] = useState<
    Record<string, StormTrackData>
  >({});
  // Chặn 2 lượt refresh chồng nhau (StrictMode double-mount / bấm LÀM MỚI giữa chu kỳ) —
  // collection dedupe của service không dedupe được N request /track phát sinh sau đó.
  const stormRefreshInFlight = useRef<boolean>(false);

  const [layerOpacity, setLayerOpacity] = useState<{ [key: string]: number }>({
    radar: 0.65,
    wind: 0.7,
    clouds: 0.5,
  });

  const [selectedStationId, setSelectedStationId] = useState<string | null>(
    null,
  );

  // Lọc danh sách trạm theo Vùng Miền lựa chọn
  const filteredStations = useMemo(() => {
    if (selectedRegion === "ALL") return stations;
    if (selectedRegion === "BẮC BỘ")
      return stations.filter((s) => s.region === "Bắc Bộ");
    if (selectedRegion === "TRUNG BỘ")
      return stations.filter(
        (s) =>
          s.region === "Trung Bộ" ||
          s.region === "Bắc Trung Bộ" ||
          s.region === "Nam Trung Bộ",
      );
    if (selectedRegion === "TÂY NGUYÊN")
      return stations.filter((s) => s.region === "Tây Nguyên");
    if (selectedRegion === "NAM BỘ")
      return stations.filter((s) => s.region === "Nam Bộ");
    if (selectedRegion === "BIỂN ĐẢO")
      return stations.filter((s) => s.region.startsWith("Biển"));
    return stations;
  }, [stations, selectedRegion]);

  // Nhịp bão độc lập với pipeline lõi: lỗi storm chỉ đổi stormState, không đổi backendStatus.
  const refreshStorms = useCallback(async () => {
    if (stormRefreshInFlight.current) return;
    stormRefreshInFlight.current = true;
    setStormState("loading");
    try {
      const collection = await stormService.getActiveStormsGeoJson();
      const centers = toStormCenters(collection.features);
      setStormCenters(centers);
      // Mốc ĐO thật = max(updated_at); rỗng → bám mốc fetch (badge không rỗng), đối xứng W2-08
      setLastStormSyncedAt(
        latestStormUpdatedAt(collection.features) ?? new Date().toISOString(),
      );
      setStormState("ready");
      // W3-06: allSettled — 1 bão lỗi (404/timeout RFC 7807) KHÔNG kéo sập cả nhóm (quyết định #7).
      // /track KHÔNG nằm trong vùng cache W3-03 → mỗi chu kỳ chạm PostGIS trực tiếp (p95 ~15ms) — chấp nhận ở nhịp 30 phút.
      const results = await Promise.allSettled(
        centers.map((center) => stormService.getStormTrack(center.id)),
      );
      const nextTracks: Record<string, StormTrackData> = {};
      results.forEach((result, index) => {
        const stormId = centers[index].id;
        if (result.status === "fulfilled") {
          const track = toStormTrackData(stormId, result.value);
          // parse 0 feature → vắng entry (tâm bão vẫn render)
          if (track) nextTracks[stormId] = track;
        } else {
          // 404/400/500 đều là RFC 7807 — interceptor đã log; suy giảm cục bộ trung thực (quyết định #11)
          console.warn(
            `[stormTrack] Không nạp được quỹ đạo bão ${stormId}:`,
            result.reason,
          );
        }
      });
      setStormTracks(nextTracks);
    } catch {
      // Lỗi COLLECTION ≠ lỗi từng track: giữ frame cuối cả tâm bão lẫn quỹ đạo (đối xứng W3-05 #8)
      setStormState("error");
    } finally {
      stormRefreshInFlight.current = false;
    }
  }, []);

  // Kiểm tra kết nối Backend & Lấy dữ liệu trạm sống từ PostgreSQL/PostGIS
  const loadCoreData = useCallback(async (): Promise<boolean> => {
    setIsRefreshing(true);
    try {
      await locationService.checkBackendHealth();
      // Fail-fast: pipeline OWM → PostGIS → API chưa lành thì không báo "online" (W2-08 mục 3.4)
      const [locations, weatherGeoJson] = await Promise.all([
        locationService.getAllLocations(),
        weatherService.getCurrentWeatherGeoJson(),
      ]);
      setStationCount(locations.length);
      setBackendStatus("online");

      // Chỉ mục tra cứu theo MÃ TRẠM (khóa thân thiện, UNIQUE — W2-07 quyết định #3)
      const weatherByCode = new Map(
        weatherGeoJson.features.map((feature) => [
          feature.properties.code,
          feature.properties,
        ]),
      );

      const mappedStations: StationMarkerData[] = locations.map((loc) => {
        const weather = weatherByCode.get(loc.code);
        return {
          id: String(loc.id),
          code: loc.code,
          name: loc.name,
          region: loc.region,
          coordinates: [loc.latitude, loc.longitude],
          severity: weather
            ? RAIN_SEVERITY_TO_MARKER[weather.severity_rain]
            : "SAFE",
          isActive: loc.isActive,
          elevationM: loc.elevation,
          weather,
        };
      });
      setStations(mappedStations);

      // Last Sync = thời điểm ĐO ĐẠC thật mới nhất (W2-02 quyết định #6)
      let latestRecordedAt: string | null = null;
      let latestTimestamp = Number.NEGATIVE_INFINITY;
      for (const feature of weatherGeoJson.features) {
        const recordedAt = feature.properties.recorded_at;
        const timestamp = Date.parse(recordedAt);
        if (!Number.isNaN(timestamp) && timestamp > latestTimestamp) {
          latestTimestamp = timestamp;
          latestRecordedAt = recordedAt;
        }
      }
      // GeoJSON rỗng (chưa từng sync) — bám mốc fetch để badge không rỗng
      setLastSyncedAt(latestRecordedAt ?? new Date().toISOString());
      return true;
    } catch {
      // Khi Backend chưa online, kích hoạt Banner cảnh báo lỗi kết nối San Rita
      // Không xóa lastSyncedAt: giữ mốc sync cuối để vận hành viên biết dữ liệu bản đồ cũ đến đâu
      setBackendStatus("error");
      setStationCount(INITIAL_MOCK_STATIONS.length);
      setStations(INITIAL_MOCK_STATIONS);
      return false;
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  // LÀM MỚI: nạp lõi thành công rồi mới kéo nhịp bão (Mục 3.7).
  const checkHealthAndLoadData = useCallback(async () => {
    const coreLoaded = await loadCoreData();
    if (coreLoaded) void refreshStorms();
  }, [loadCoreData, refreshStorms]);

  useEffect(() => {
    void loadCoreData();
  }, [loadCoreData]);

  // Nhịp bão 30 phút khớp chu kỳ GDACS + TTL active_storms (W3-03); cleanup khi unmount.
  useEffect(() => {
    void refreshStorms();
    const timer = setInterval(() => {
      void refreshStorms();
    }, STORM_REFRESH_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [refreshStorms]);

  // Điều phối chuyển động bay (FlyTo) của bản đồ Leaflet
  const handleFlyTo = (coords: CoordinateTuple, zoom = 11) => {
    if (mapRef.current) {
      mapRef.current.flyTo(coords, zoom, {
        duration: 1.5,
      });
    }
  };

  // Đưa bản đồ về trung tâm toàn cảnh Việt Nam — đồng thời là lối ra duy nhất của chế độ bão.
  const handleResetCenter = () => {
    if (mapRef.current) {
      // Khôi phục bounds TRƯỚC khi bay — set sau flyTo sẽ bị bỏ qua/ghi đè giữa chuyến (Runbook 7.5).
      mapRef.current.setMaxBounds(VIETNAM_MAX_BOUNDS);
      setStormOpsMode(false);
      mapRef.current.flyTo(VIETNAM_DEFAULT_CENTER, VIETNAM_DEFAULT_ZOOM, {
        duration: 1.5,
      });
    }
  };

  // STORM_OPS_BOUNDS: thả ràng buộc pan để đưa bão ngoài hộp vào tâm (W3-05 #10, quyết định #7).
  // KHÔNG khôi phục sau moveend — race làm Leaflet kẹp giữa chuyến bay (bài học đối xứng D-5).
  const handleStormSelect = useCallback((storm: StormCenter) => {
    const map = mapRef.current;
    if (!map) return;
    // Leaflet coi bounds rỗng là null và gỡ luôn listener kẹp pan — đúng ngữ nghĩa "thả ràng buộc".
    map.setMaxBounds(undefined);
    setStormOpsMode(true);
    map.flyTo(storm.latLng, STORM_FOCUS_ZOOM, { duration: 1.5 });
  }, []);

  // Zoom In / Zoom Out
  const handleZoomIn = () => {
    if (mapRef.current) {
      mapRef.current.zoomIn();
    }
  };

  const handleZoomOut = () => {
    if (mapRef.current) {
      mapRef.current.zoomOut();
    }
  };

  // Bật / Tắt lớp dữ liệu
  const handleToggleLayer = (layerKey: keyof LayerVisibilityState) => {
    setLayerVisibility((prev) => ({
      ...prev,
      [layerKey]: !prev[layerKey],
    }));
  };

  // Thay đổi độ trong suốt lớp phủ
  const handleOpacityChange = (layerKey: string, value: number) => {
    setLayerOpacity((prev) => ({
      ...prev,
      [layerKey]: value,
    }));
  };

  // Click vào bản tin cảnh báo
  const handleSelectAlert = (alert: WeatherAlertItem) => {
    setSelectedStationId(alert.id);
    handleFlyTo(alert.coordinates, 11);
  };

  // Toàn màn hình
  const handleToggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {});
      setIsFullscreen(true);
    } else {
      if (document.exitFullscreen) {
        document.exitFullscreen().catch(() => {});
        setIsFullscreen(false);
      }
    }
  };

  return (
    <div className="flex flex-col h-screen w-screen bg-[#161b13] text-[#dde2e4] select-none font-mono overflow-hidden">
      {/* 1. HEADER CHROME (h-14, z-[1100]) */}
      <HeaderNavbar
        backendStatus={backendStatus}
        stationCount={stationCount}
        mapCenter={mapCenter}
        mapZoom={mapZoom}
        cursorCoords={cursorCoords}
        isRefreshing={isRefreshing}
        onRefreshHealth={checkHealthAndLoadData}
        onResetView={handleResetCenter}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        isFullscreen={isFullscreen}
        onToggleFullscreen={handleToggleFullscreen}
      />

      {/* 2. KHU VỰC THÂN TRUNG TÂM */}
      {activeTab === "operations" ? (
        /* CHẾ ĐỘ 01: BẢN ĐỒ TÁC CHIẾN TRÀN VIỀN (FULL-BLEED SPATIAL CANVAS) */
        <main
          data-storm-ops-mode={stormOpsMode ? "on" : "off"}
          className="flex-1 relative w-full h-[calc(100vh-56px-24px)] overflow-hidden"
        >
          {/* Banner Cảnh Báo Mất Kết Nối Backend Spring Boot (San Rita Alert Banner) */}
          {backendStatus === "error" && (
            <div
              id="banner-backend-error"
              className="absolute top-3 left-1/2 -translate-x-1/2 z-[1050] bg-[#161b13] border border-[#dc2626] p-2 px-4 shadow-flat flex items-center gap-3 text-xs text-[#dc2626] font-mono animate-pulse-subtle"
            >
              <AlertTriangle className="w-4 h-4 text-[#dc2626] shrink-0" />
              <span>
                <strong className="uppercase">[LỖI HỆ THỐNG]:</strong> Mất kết
                nối tới máy chủ Spring Boot. Hãy kiểm tra container Docker!
              </span>
              <button
                id="btn-retry-connection"
                onClick={checkHealthAndLoadData}
                disabled={isRefreshing}
                className="px-2 py-0.5 border border-[#dc2626] hover:bg-[#dc2626] hover:text-[#161b13] bg-[#1a2117] text-[#dde2e4] text-[10px] uppercase font-bold transition-colors disabled:opacity-50 flex items-center gap-1.5"
              >
                <RefreshCw
                  className={`w-3 h-3 ${isRefreshing ? "animate-spin" : ""}`}
                />
                LÀM MỚI KẾT NỐI
              </button>
            </div>
          )}

          {/* Thanh Tiến Trình Quét Radar Dữ Liệu Thời Gian Thực (Scanline) */}
          {isRefreshing && (
            <div
              id="scanline-indicator"
              className="absolute top-3 left-4 z-[1050] bg-[#161b13] border border-[#e2ffcc] px-2.5 py-1 text-[10px] text-[#e2ffcc] flex items-center gap-2 shadow-flat font-mono animate-pulse"
            >
              <span className="w-2 h-2 bg-[#e2ffcc] animate-ping inline-block" />
              <span>[ĐANG QUÉT DỮ LIỆU RADAR TRẮC ĐỊA...]</span>
            </div>
          )}

          {/* Lớp Bản Đồ BaseMap.tsx Cốt Lõi */}
          <BaseMap
            stations={filteredStations}
            stormCenters={stormCenters}
            stormTracks={stormTracks}
            stormState={stormState}
            selectedStationId={selectedStationId}
            onStormSelect={handleStormSelect}
            onStationSelect={(station) => {
              setSelectedStationId(station.id);
              handleFlyTo(station.coordinates, 12);
            }}
            onFlyToStation={(station) => handleFlyTo(station.coordinates, 12)}
            onAskAi={(station) =>
              setAiPrompt(
                `Kiểm tra tình hình thời tiết và cảnh báo tại trạm [${station.code}] ${station.name}`,
              )
            }
            activeTileId={activeTileId}
            layerVisibility={layerVisibility}
            layerOpacity={layerOpacity}
            onMouseMoveCoords={setCursorCoords}
            onMapStateChange={({ center, zoom }) => {
              setMapCenter(center);
              setMapZoom(zoom);
            }}
            onMapReady={(map) => {
              mapRef.current = map;
            }}
            className="w-full h-full"
          />

          {/* Bảng Điều Khiển Lớp Nổi (Floating Layer Control, Top Right, z-1000) */}
          <LayerControlDrawer
            activeTileId={activeTileId}
            onTileChange={setActiveTileId}
            layerVisibility={layerVisibility}
            onToggleLayer={handleToggleLayer}
            layerOpacity={layerOpacity}
            onOpacityChange={handleOpacityChange}
            stationCount={filteredStations.length}
            totalStationCount={stations.length}
            stormCount={
              stormState === "idle" || stormState === "loading"
                ? undefined
                : stormCenters.length
            }
            stormState={stormState}
            stormLastSyncedAt={lastStormSyncedAt}
            selectedRegion={selectedRegion}
            onRegionChange={setSelectedRegion}
            lastSyncedAt={lastSyncedAt}
            isSyncing={isRefreshing}
          />

          {/* Cụm Phím Thu Phóng Nổi (Floating Navigation Controls, Right Edge Centered, z-1000) */}
          <MapNavigationControls
            onZoomIn={handleZoomIn}
            onZoomOut={handleZoomOut}
            onResetCenter={handleResetCenter}
            isFullscreen={isFullscreen}
            onToggleFullscreen={handleToggleFullscreen}
          />

          {/* Thanh Cuộn Bản Tin Cảnh Báo Nổi (Floating Alert Ticker, Bottom Left-Center, z-1000) */}
          <AlertTickerBar
            alerts={SAMPLE_WEATHER_ALERTS}
            onSelectAlert={handleSelectAlert}
          />

          {/* Trợ Lý AI Khí Tượng Nổi & Ngăn Kéo Đối Thoại (AiChatDrawer, Bottom Right, z-1000 / z-1200) */}
          <AiChatDrawer
            onNavigateToLocation={(coords, zoom) =>
              handleFlyTo(coords, zoom || 11)
            }
            externalPrompt={aiPrompt}
            onClearExternalPrompt={() => setAiPrompt(null)}
          />
        </main>
      ) : (
        /* CHẾ ĐỘ 02: BẢNG KIỂM ĐỊNH HẠ TẦNG & TRẮC ĐỊA (DIAGNOSTICS & VERIFICATION) */
        <main className="flex-1 p-6 overflow-y-auto bg-[#161b13]">
          {diagnosticsContent}
        </main>
      )}

      {/* 3. FOOTER CHROME (h-6, z-[1100]) */}
      <DisclaimerFooter
        cursorCoords={cursorCoords}
        activeTileId={activeTileId}
      />
    </div>
  );
};

export default UnifiedAppLayout;
