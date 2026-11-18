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
import { AlertTriangle, Bell, RefreshCw } from "lucide-react";
import { HeaderNavbar } from "./HeaderNavbar";
import { DisclaimerFooter } from "./DisclaimerFooter";
import { BaseMap } from "@/components/map/BaseMap";
import { LayerControlDrawer } from "@/components/dashboard/LayerControlDrawer";
import { MapNavigationControls } from "@/components/dashboard/MapNavigationControls";
import { AlertTickerBar } from "@/components/dashboard/AlertTickerBar";
import { AlertPanel } from "@/components/dashboard/AlertPanel";
import { useAlerts } from "@/hooks/useAlerts";
import { ALERT_FOCUS_ZOOM, getAlertCoordinates } from "@/lib/alertUtils";
import type { AlertFeature } from "@/types/alert";
import {
  AiChatDrawer,
  type ChatRequest,
} from "@/components/dashboard/AiChatDrawer";
import { StationSearchModal } from "@/components/dashboard/StationSearchModal";
import { StationHistoryModal } from "@/components/dashboard/StationHistoryModal";
import {
  MyLocationCard,
  type UserGpsPosition,
} from "@/components/dashboard/MyLocationCard";
import {
  EmailSubscriptionModal,
  type SubscriptionPrefill,
} from "@/components/dashboard/EmailSubscriptionModal";
import {
  CoordinateTuple,
  LayerVisibilityState,
  WeatherSeverityLevel,
  StationMarkerData,
} from "@/types/map";
import { SpatialPointContext } from "@/types/location";
import { RainSeverityLevel } from "@/types/weather";
import { StatusType } from "@/components/common/StatusBadge";
import {
  DEFAULT_TILE_ID,
  VIETNAM_DEFAULT_CENTER,
  VIETNAM_DEFAULT_ZOOM,
  VIETNAM_MAX_BOUNDS,
  calculateHaversineDistance,
  isWithinVietnamBounds,
} from "@/lib/geoUtils";
import { locationService } from "@/services/locationService";
import { weatherService } from "@/services/weatherService";
import { stormService } from "@/services/stormService";
import { subscriptionService } from "@/services/subscriptionService";
import {
  StormCenter,
  StormTrackData,
  latestStormUpdatedAt,
  toStormCenters,
  toStormTrackData,
} from "@/lib/stormUtils";
import { STORM_REFRESH_INTERVAL_MS } from "@/types/storm";
import { INITIAL_MOCK_STATIONS } from "@/data/mockMapData";

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
  const [mapReady, setMapReady] = useState(false);
  const alerts = useAlerts();
  const refreshAlerts = alerts.refresh;
  const [rightDrawer, setRightDrawer] = useState<
    "layers" | "alerts" | "chat" | null
  >(() => (window.matchMedia("(min-width: 768px)").matches ? "layers" : null));
  const [selectedAlertId, setSelectedAlertId] = useState<string | null>(null);
  const [alertNotice, setAlertNotice] = useState<string | null>(null);
  const alertTriggerRef = useRef<HTMLButtonElement>(null);
  const selectedAlert =
    alerts.alerts.find((alert) => alert.id === selectedAlertId) ?? null;

  useEffect(() => {
    if (
      selectedAlertId &&
      alerts.hasLoaded &&
      !alerts.isRefreshing &&
      !alerts.error &&
      !selectedAlert
    ) {
      setSelectedAlertId(null);
      setAlertNotice("Cảnh báo không còn trong danh sách đang hoạt động");
    }
  }, [
    selectedAlertId,
    selectedAlert,
    alerts.hasLoaded,
    alerts.isRefreshing,
    alerts.error,
  ]);

  const handleLayersOpenChange = useCallback((open: boolean) => {
    setRightDrawer(open ? "layers" : null);
  }, []);
  const handleChatOpenChange = useCallback((open: boolean) => {
    setRightDrawer(open ? "chat" : null);
  }, []);
  const handleCloseAlerts = useCallback(() => {
    setRightDrawer(null);
    setSelectedAlertId(null);
    alertTriggerRef.current?.focus();
  }, []);
  const handleSelectAlert = useCallback((alert: AlertFeature) => {
    setAlertNotice(null);
    setSelectedAlertId(alert.id);
    setRightDrawer("alerts");
  }, []);

  // 2. Trạng thái kết nối Backend Spring Boot & Dữ liệu trạm PostGIS
  const [backendStatus, setBackendStatus] = useState<StatusType>("loading");
  const [stationCount, setStationCount] = useState<number | null>(null);
  const [stations, setStations] = useState<StationMarkerData[]>(
    INITIAL_MOCK_STATIONS,
  );
  const [selectedRegion, setSelectedRegion] = useState<string>("ALL");
  const [chatRequest, setChatRequest] = useState<ChatRequest | null>(null);
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
  const [activeTileId, setActiveTileId] = useState<string>(DEFAULT_TILE_ID);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);

  // 5. Quản lý Trạng thái các lớp chuyên đề
  const [layerVisibility, setLayerVisibility] = useState<LayerVisibilityState>({
    territory: true,
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

  // Storm and alert focus share unrestricted bounds; only VỀ TOÀN CẢNH restores them.
  const [mapFocusMode, setMapFocusMode] = useState<
    "default" | "storm" | "alert"
  >("default");

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

  // 6. Các hộp thoại & tiện ích mở rộng (Tìm kiếm nhanh, Lịch sử 24h, Vị trí của tôi, Đăng ký Email)
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [historyStation, setHistoryStation] =
    useState<StationMarkerData | null>(null);
  const [isSubscriptionOpen, setIsSubscriptionOpen] = useState(false);
  const [subscriptionPrefill, setSubscriptionPrefill] =
    useState<SubscriptionPrefill | null>(null);
  const [deepLinkBanner, setDeepLinkBanner] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  const [isMyLocationOpen, setIsMyLocationOpen] = useState(false);
  const [isLocatingMe, setIsLocatingMe] = useState(false);
  const [gpsError, setGpsError] = useState<string | null>(null);
  const [userGpsPosition, setUserGpsPosition] =
    useState<UserGpsPosition | null>(null);
  const [mySpatialContext, setMySpatialContext] =
    useState<SpatialPointContext | null>(null);

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
    void refreshAlerts();
    const coreLoaded = await loadCoreData();
    if (coreLoaded) void refreshStorms();
  }, [loadCoreData, refreshStorms, refreshAlerts]);

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
  const handleFlyTo = useCallback((coords: CoordinateTuple, zoom = 11) => {
    if (mapRef.current) {
      mapRef.current.flyTo(coords, zoom, {
        duration: 1.5,
      });
    }
  }, []);

  const handleStationSelect = useCallback(
    (station: StationMarkerData) => {
      setSelectedStationId(station.id);
      handleFlyTo(station.coordinates, 12);
    },
    [handleFlyTo],
  );

  const handleFlyToStation = useCallback(
    (station: StationMarkerData) => {
      handleFlyTo(station.coordinates, 12);
    },
    [handleFlyTo],
  );

  const handleAskAi = useCallback((station: StationMarkerData) => {
    setChatRequest({
      question: `Kiểm tra tình hình thời tiết và cảnh báo tại trạm [${station.code}] ${station.name}`,
    });
    setRightDrawer("chat");
  }, []);

  const handleOpenStationHistory = useCallback((station: StationMarkerData) => {
    setHistoryStation(station);
  }, []);

  const handleOpenSubscribeStation = useCallback(
    (station: StationMarkerData) => {
      setSubscriptionPrefill({
        scopeType: "STATION",
        locationCode: station.code,
        label: `${station.name} (${station.code})`,
      });
      setIsSubscriptionOpen(true);
    },
    [],
  );

  const handleOpenSubscribeHere = useCallback(
    (lat: number, lng: number, label: string) => {
      setSubscriptionPrefill({
        scopeType: "COORDINATE",
        latitude: lat,
        longitude: lng,
        label,
      });
      setIsSubscriptionOpen(true);
    },
    [],
  );

  const buildFallbackSpatialContext = useCallback(
    (lat: number, lng: number): SpatialPointContext => {
      let nearest: StationMarkerData | null = null;
      let minDist = Number.POSITIVE_INFINITY;
      for (const st of stations) {
        const d = calculateHaversineDistance([lat, lng], st.coordinates);
        if (d < minDist) {
          minDist = d;
          nearest = st;
        }
      }
      return {
        longitude: lng,
        latitude: lat,
        radiusKm: 100,
        withinVietnamBounds: isWithinVietnamBounds(lat, lng),
        nearestStation: nearest
          ? {
              id: nearest.id,
              code: nearest.code,
              name: nearest.name,
              region: nearest.region,
              longitude: nearest.coordinates[1],
              latitude: nearest.coordinates[0],
              distanceKm: Math.round(minDist * 10) / 10,
            }
          : null,
        currentWeather: nearest?.weather
          ? {
              recordedAt: nearest.weather.recorded_at,
              temperature: nearest.weather.temperature ?? undefined,
              feelsLike: nearest.weather.feels_like ?? undefined,
              humidity: nearest.weather.humidity ?? undefined,
              pressure: nearest.weather.pressure ?? undefined,
              windSpeed: nearest.weather.wind_speed ?? undefined,
              windGust: nearest.weather.wind_gust ?? undefined,
              windDirection: nearest.weather.wind_direction ?? undefined,
              rain1h: nearest.weather.rain_1h ?? undefined,
              rain3h: nearest.weather.rain_3h ?? undefined,
              weatherCondition: nearest.weather.weather_condition ?? undefined,
              weatherDescription:
                nearest.weather.weather_description ?? undefined,
            }
          : null,
        nearestStorm: null,
        nearbyAlerts: [],
      };
    },
    [stations],
  );

  const resolveAndFlyToUserCoords = useCallback(
    async (
      lat: number,
      lng: number,
      accuracyMeters: number | null,
      source: "gps" | "manual",
    ) => {
      setUserGpsPosition({ latitude: lat, longitude: lng, accuracyMeters, source });
      setGpsError(null);
      if (mapRef.current) {
        if (!isWithinVietnamBounds(lat, lng)) {
          mapRef.current.setMaxBounds(undefined);
        }
        mapRef.current.flyTo([lat, lng], 11, { duration: 1.5 });
      }
      try {
        const ctx = await locationService.getSpatialPointContext(lng, lat, 100);
        setMySpatialContext(ctx);
      } catch {
        setMySpatialContext(buildFallbackSpatialContext(lat, lng));
      }
    },
    [buildFallbackSpatialContext],
  );

  const handleLocateMe = useCallback(() => {
    setIsMyLocationOpen(true);
    if (!("geolocation" in navigator)) {
      setGpsError(
        "Trình duyệt hiện tại không hỗ trợ định vị GPS. Bạn có thể nhập tọa độ thủ công bên dưới.",
      );
      return;
    }
    setIsLocatingMe(true);
    setGpsError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setIsLocatingMe(false);
        void resolveAndFlyToUserCoords(
          pos.coords.latitude,
          pos.coords.longitude,
          pos.coords.accuracy ?? null,
          "gps",
        );
      },
      (err) => {
        setIsLocatingMe(false);
        const msg =
          err.code === 1
            ? "Quyền truy cập vị trí bị từ chối. Hãy cấp quyền Location trên trình duyệt hoặc nhập tọa độ thủ công."
            : "Không lấy được tín hiệu GPS từ thiết bị. Bạn có thể nhập tọa độ thủ công bên dưới.";
        setGpsError(msg);
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    );
  }, [resolveAndFlyToUserCoords]);

  const handleApplyManualCoords = useCallback(
    (lat: number, lng: number) => {
      void resolveAndFlyToUserCoords(lat, lng, null, "manual");
    },
    [resolveAndFlyToUserCoords],
  );

  const handleFlyToMyLocation = useCallback(() => {
    if (!userGpsPosition || !mapRef.current) return;
    if (!isWithinVietnamBounds(userGpsPosition.latitude, userGpsPosition.longitude)) {
      mapRef.current.setMaxBounds(undefined);
    }
    mapRef.current.flyTo(
      [userGpsPosition.latitude, userGpsPosition.longitude],
      11,
      { duration: 1.4 },
    );
  }, [userGpsPosition]);

  const handleAskAiHere = useCallback(
    (ctx: SpatialPointContext | null, pos: UserGpsPosition) => {
      const stationHint = ctx?.nearestStation
        ? `trạm [${ctx.nearestStation.code}] ${ctx.nearestStation.name}`
        : `tọa độ ${pos.latitude.toFixed(4)}°N, ${pos.longitude.toFixed(4)}°E`;
      setChatRequest({
        question: `Kiểm tra thời tiết, bão và cảnh báo tại ${stationHint}`,
      });
      setRightDrawer("chat");
    },
    [],
  );

  // Phím tắt toàn cục Ctrl+K / Cmd+K mở nhanh tìm kiếm tỉnh/trạm
  useEffect(() => {
    const onGlobalKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setIsSearchOpen((prev) => !prev);
      }
    };
    window.addEventListener("keydown", onGlobalKeyDown);
    return () => window.removeEventListener("keydown", onGlobalKeyDown);
  }, []);

  // Xử lý liên kết 1-click từ Email (?sub_action=verify|unsubscribe&token=...) chỉ khi có query param
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const subAction = params.get("sub_action");
    const token = params.get("token");
    if (!subAction || !token) return;

    const cleanUrl = window.location.pathname + window.location.hash;
    window.history.replaceState({}, document.title, cleanUrl);
    setIsSubscriptionOpen(true);

    if (subAction === "verify") {
      subscriptionService
        .verifyByToken(token)
        .then((res) => {
          subscriptionService.saveVerifiedSubscription(res.data);
          setDeepLinkBanner({
            type: "success",
            message: `Đã kích hoạt nhận cảnh báo Email cho khu vực ${res.data.label}!`,
          });
        })
        .catch(() => {
          setDeepLinkBanner({
            type: "error",
            message:
              "Liên kết xác thực không hợp lệ hoặc đã hết hạn. Vui lòng đăng ký lại.",
          });
        });
    } else if (subAction === "unsubscribe") {
      subscriptionService
        .unsubscribeByToken(token)
        .then((res) => {
          subscriptionService.removeSavedSubscription(token);
          setDeepLinkBanner({
            type: "success",
            message: `Đã hủy đăng ký nhận cảnh báo Email cho khu vực ${res.data.label}.`,
          });
        })
        .catch(() => {
          setDeepLinkBanner({
            type: "error",
            message: "Liên kết hủy đăng ký không hợp lệ hoặc đã được xử lý trước đó.",
          });
        });
    }
  }, []);

  const handleAskAiAboutAlert = useCallback((alert: AlertFeature) => {
    const name = alert.properties.location_name;
    setChatRequest({
      question: name
        ? `Vì sao trạm ${name} lại phát cảnh báo này?`
        : `Giải thích cảnh báo "${alert.properties.title || "Chưa có tiêu đề"}".`,
      referenced_alert_id: alert.id,
      alert_title: alert.properties.title || "Chưa có tiêu đề",
    });
    setRightDrawer("chat");
  }, []);

  const handleMapStateChange = useCallback(
    ({ center, zoom }: { center: CoordinateTuple; zoom: number }) => {
      setMapCenter(center);
      setMapZoom(zoom);
    },
    [],
  );

  const handleMapReady = useCallback((map: L.Map) => {
    mapRef.current = map;
    setMapReady(true);
  }, []);

  const handleTabChange = useCallback(
    (tab: "operations" | "diagnostics") => {
      if (tab === activeTab) return;
      mapRef.current = null;
      setMapReady(false);
      setRightDrawer(null);
      setMapFocusMode("default");
      setActiveTab(tab);
    },
    [activeTab],
  );

  const handleFlyToAlert = useCallback((alert: AlertFeature) => {
    const map = mapRef.current;
    const coordinates = getAlertCoordinates(alert);
    if (!map || !coordinates || !map.getContainer().isConnected) return;
    map.stop();
    map.setMaxBounds(undefined);
    setMapFocusMode("alert");
    map.flyTo(coordinates, ALERT_FOCUS_ZOOM, {
      duration: 1.5,
      animate: !window.matchMedia("(prefers-reduced-motion: reduce)").matches,
    });
  }, []);

  // Đưa bản đồ về toàn cảnh Việt Nam và khôi phục bounds sau khi định vị bão/cảnh báo.
  const handleResetCenter = () => {
    if (mapRef.current) {
      // Khôi phục bounds TRƯỚC khi bay — set sau flyTo sẽ bị bỏ qua/ghi đè giữa chuyến (Runbook 7.5).
      mapRef.current.setMaxBounds(VIETNAM_MAX_BOUNDS);
      setMapFocusMode("default");
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
    setMapFocusMode("storm");
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
        isRefreshing={isRefreshing || alerts.isRefreshing}
        onRefreshHealth={checkHealthAndLoadData}
        onResetView={handleResetCenter}
        activeTab={activeTab}
        onTabChange={handleTabChange}
        isFullscreen={isFullscreen}
        onToggleFullscreen={handleToggleFullscreen}
        onOpenSearch={() => setIsSearchOpen(true)}
        onLocateMe={handleLocateMe}
        isLocatingMe={isLocatingMe}
        onOpenSubscribe={() => {
          setSubscriptionPrefill(null);
          setIsSubscriptionOpen(true);
        }}
      />

      {/* 2. KHU VỰC THÂN TRUNG TÂM */}
      {activeTab === "operations" ? (
        /* CHẾ ĐỘ 01: BẢN ĐỒ TÁC CHIẾN TRÀN VIỀN (FULL-BLEED SPATIAL CANVAS) */
        <main
          data-storm-ops-mode={mapFocusMode === "storm" ? "on" : "off"}
          data-map-focus-mode={mapFocusMode}
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
                <strong className="uppercase">[MẤT KẾT NỐI MÁY CHỦ]:</strong>{" "}
                Đang hiển thị dữ liệu mẫu tạm thời. Hãy kiểm tra lại dịch vụ máy
                chủ dữ liệu!
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

          {/* Thanh Tiến Trình Đang Tải Dữ Liệu Thời Gian Thực */}
          {isRefreshing && (
            <div
              id="scanline-indicator"
              className="absolute top-3 left-4 z-[1050] bg-[#161b13] border border-[#e2ffcc] px-2.5 py-1 text-[10px] text-[#e2ffcc] flex items-center gap-2 shadow-flat font-mono animate-pulse"
            >
              <span className="w-2 h-2 bg-[#e2ffcc] animate-ping inline-block" />
              <span>Đang tải dữ liệu thời tiết mới nhất...</span>
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
            onStationSelect={handleStationSelect}
            onFlyToStation={handleFlyToStation}
            onAskAi={handleAskAi}
            onViewHistory={handleOpenStationHistory}
            onSubscribeStation={handleOpenSubscribeStation}
            userLocation={
              userGpsPosition
                ? {
                    coordinates: [
                      userGpsPosition.latitude,
                      userGpsPosition.longitude,
                    ],
                    accuracyMeters: userGpsPosition.accuracyMeters,
                    label: mySpatialContext?.nearestStation
                      ? `Vị trí của tôi (gần ${mySpatialContext.nearestStation.name})`
                      : "Vị trí hiện tại của tôi",
                  }
                : null
            }
            activeTileId={activeTileId}
            layerVisibility={layerVisibility}
            layerOpacity={layerOpacity}
            onMouseMoveCoords={setCursorCoords}
            onMapStateChange={handleMapStateChange}
            onMapReady={handleMapReady}
            className="w-full h-full"
          />

          {/* Bảng Điều Khiển Lớp Nổi (Floating Layer Control, Top Right, z-1000) */}
          <button
            ref={alertTriggerRef}
            id="btn-open-alerts"
            aria-controls="alert-panel"
            aria-expanded={rightDrawer === "alerts"}
            onClick={() => {
              if (rightDrawer === "alerts") handleCloseAlerts();
              else setRightDrawer("alerts");
            }}
            className="absolute top-4 left-4 z-[1060] flex items-center gap-2 border border-charcoal bg-carbon px-3 py-2 text-xs text-mint focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
          >
            <Bell className="h-4 w-4" aria-hidden="true" />
            CẢNH BÁO
            {alerts.hasLoaded && (
              <span
                aria-label={
                  alerts.error ? "Số lượng ở lần tải cuối" : "Số cảnh báo"
                }
              >
                {alerts.alerts.length}
                {alerts.error ? " · CŨ" : ""}
              </span>
            )}
          </button>
          <div hidden={rightDrawer === "alerts" || rightDrawer === "chat"}>
            <LayerControlDrawer
              isOpen={rightDrawer === "layers"}
              onOpenChange={handleLayersOpenChange}
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
          </div>

          {rightDrawer === "alerts" && (
            <AlertPanel
              {...alerts}
              selectedAlert={selectedAlert}
              notice={alertNotice}
              mapReady={mapReady}
              onSelectAlert={handleSelectAlert}
              onBack={() => setSelectedAlertId(null)}
              onRefresh={alerts.refresh}
              onClose={handleCloseAlerts}
              onFlyTo={handleFlyToAlert}
              onAskAi={handleAskAiAboutAlert}
            />
          )}

          {/* Cụm Phím Thu Phóng Nổi (Floating Navigation Controls, Right Edge Centered, z-1000) */}
          <MapNavigationControls
            onZoomIn={handleZoomIn}
            onZoomOut={handleZoomOut}
            onResetCenter={handleResetCenter}
            onLocateMe={handleLocateMe}
            isLocatingMe={isLocatingMe}
            isFullscreen={isFullscreen}
            onToggleFullscreen={handleToggleFullscreen}
          />

          {/* Thẻ Phân Tích Vị Trí Hiện Tại Của Tôi (GPS + PostGIS) */}
          <MyLocationCard
            isOpen={isMyLocationOpen}
            isLocating={isLocatingMe}
            gpsError={gpsError}
            position={userGpsPosition}
            spatialContext={mySpatialContext}
            onClose={() => setIsMyLocationOpen(false)}
            onLocateGps={handleLocateMe}
            onApplyManualCoords={handleApplyManualCoords}
            onFlyToMyLocation={handleFlyToMyLocation}
            onSubscribeHere={handleOpenSubscribeHere}
            onAskAiHere={handleAskAiHere}
          />

          {/* Thanh Cuộn Bản Tin Cảnh Báo Nổi (Floating Alert Ticker, Bottom Left-Center, z-1000) */}
          {rightDrawer !== "alerts" && (
            <AlertTickerBar
              {...alerts}
              mapReady={mapReady}
              onSelectAlert={handleSelectAlert}
              onFlyTo={handleFlyToAlert}
            />
          )}

          {/* Trợ Lý AI Khí Tượng Nổi & Ngăn Kéo Đối Thoại (AiChatDrawer, Bottom Right, z-1000 / z-1200) */}
          <AiChatDrawer
            isOpen={rightDrawer === "chat"}
            onOpenChange={handleChatOpenChange}
            externalRequest={chatRequest}
            onClearExternalRequest={() => setChatRequest(null)}
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

      {/* 4. HỘP THOẠI TÌM KIẾM NHANH TỈNH/TRẠM (Ctrl+K) */}
      <StationSearchModal
        isOpen={isSearchOpen}
        stations={stations}
        onClose={() => setIsSearchOpen(false)}
        onSelectStation={(station) => {
          if (activeTab !== "operations") {
            setActiveTab("operations");
          }
          handleStationSelect(station);
        }}
        onOpenHistory={handleOpenStationHistory}
        onOpenSubscribe={handleOpenSubscribeStation}
      />

      {/* 5. BIỂU ĐỒ DIỄN BIẾN THỜI TIẾT LỊCH SỬ 24H / 72H / 7 NGÀY */}
      <StationHistoryModal
        station={historyStation}
        onClose={() => setHistoryStation(null)}
        onSubscribeStation={handleOpenSubscribeStation}
      />

      {/* 6. HỘP THOẠI ĐĂNG KÝ & QUẢN LÝ NHẬN CẢNH BÁO QUA EMAIL */}
      <EmailSubscriptionModal
        isOpen={isSubscriptionOpen}
        stations={stations}
        prefill={subscriptionPrefill}
        deepLinkBanner={deepLinkBanner}
        onClearDeepLinkBanner={() => setDeepLinkBanner(null)}
        onClose={() => setIsSubscriptionOpen(false)}
      />
    </div>
  );
};

export default UnifiedAppLayout;
