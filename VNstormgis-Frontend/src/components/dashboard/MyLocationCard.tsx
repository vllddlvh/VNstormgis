/**
 * @file MyLocationCard.tsx
 * @description Thẻ nổi "Vị Trí Hiện Tại Của Tôi — Phân Tích Không Gian PostGIS" chuẩn San Rita.
 * Hỗ trợ định vị GPS trình duyệt (navigator.geolocation) kèm chế độ nhập/chọn tọa độ nhanh
 * khi máy tính bàn không bật GPS, hiển thị trạm gần nhất, thời tiết thực tế, khoảng cách tâm bão
 * và cảnh báo lân cận từ GET /api/v1/locations/at-point.
 */

import React, { useState } from "react";
import {
  LocateFixed,
  X,
  RefreshCw,
  Mail,
  Bot,
  AlertTriangle,
  Navigation,
} from "lucide-react";
import { SpatialPointContext } from "@/types/location";
import { formatCoordinate } from "@/lib/geoUtils";

export interface UserGpsPosition {
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  source: "gps" | "manual";
}

export interface MyLocationCardProps {
  isOpen: boolean;
  isLocating: boolean;
  gpsError: string | null;
  position: UserGpsPosition | null;
  spatialContext: SpatialPointContext | null;
  onClose: () => void;
  onLocateGps: () => void;
  onApplyManualCoords: (lat: number, lng: number) => void;
  onFlyToMyLocation: () => void;
  onSubscribeHere: (lat: number, lng: number, label: string) => void;
  onAskAiHere: (context: SpatialPointContext | null, pos: UserGpsPosition) => void;
}

export const MyLocationCard: React.FC<MyLocationCardProps> = ({
  isOpen,
  isLocating,
  gpsError,
  position,
  spatialContext,
  onClose,
  onLocateGps,
  onApplyManualCoords,
  onFlyToMyLocation,
  onSubscribeHere,
  onAskAiHere,
}) => {
  const [showManual, setShowManual] = useState(false);
  const [manualLat, setManualLat] = useState("16.0544");
  const [manualLng, setManualLng] = useState("108.2022");
  const [manualError, setManualError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleManualSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const lat = Number(manualLat);
    const lng = Number(manualLng);
    if (
      !Number.isFinite(lat) ||
      lat < -90 ||
      lat > 90 ||
      !Number.isFinite(lng) ||
      lng < -180 ||
      lng > 180
    ) {
      setManualError("Tọa độ không hợp lệ (Vĩ độ [-90,90], Kinh độ [-180,180])");
      return;
    }
    setManualError(null);
    setShowManual(false);
    onApplyManualCoords(lat, lng);
  };

  const nearestStation = spatialContext?.nearestStation ?? null;
  const weather = spatialContext?.currentWeather ?? null;
  const storm = spatialContext?.nearestStorm ?? null;
  const alerts = spatialContext?.nearbyAlerts ?? [];

  const locationLabel = nearestStation
    ? `Gần ${nearestStation.name} (${nearestStation.distanceKm} km)`
    : position
      ? `Vị trí của tôi (${position.latitude.toFixed(4)}°N, ${position.longitude.toFixed(4)}°E)`
      : "Vị trí của tôi";

  return (
    <section
      id="my-location-card"
      data-testid="my-location-card"
      aria-label="Phân tích vị trí hiện tại của tôi"
      className="absolute top-16 left-4 z-[1065] w-[calc(100vw-2rem)] sm:w-[350px] max-h-[calc(100vh-140px)] overflow-y-auto bg-[#161b13] border border-[#e2ffcc] shadow-flat font-mono text-[#dde2e4]"
    >
      {/* Header */}
      <div className="px-3 py-2 border-b border-[#2d3329] bg-[#1a2117] flex items-center justify-between gap-2">
        <div className="flex items-center gap-1.5">
          <LocateFixed className="w-3.5 h-3.5 text-[#e2ffcc]" />
          <span className="text-[11px] font-bold uppercase text-[#e2ffcc]">
            VỊ TRÍ HIỆN TẠI CỦA TÔI
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={onLocateGps}
            disabled={isLocating}
            className="p-1 border border-[#2d3329] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc]"
            title="Định vị lại bằng GPS trình duyệt"
          >
            <RefreshCw
              className={`w-3 h-3 ${isLocating ? "animate-spin text-[#e2ffcc]" : ""}`}
            />
          </button>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng thẻ vị trí của tôi"
            className="p-1 border border-[#2d3329] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc]"
          >
            <X className="w-3 h-3" />
          </button>
        </div>
      </div>

      <div className="p-3 space-y-2.5 text-[10px]">
        {/* Trạng thái đang định vị */}
        {isLocating && (
          <div className="p-2.5 border border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc] flex items-center gap-2">
            <span className="w-2 h-2 bg-[#e2ffcc] animate-ping inline-block" />
            <span>Đang xác định tọa độ GPS và truy vấn không gian PostGIS...</span>
          </div>
        )}

        {/* Thông báo lỗi GPS nếu bị chặn quyền */}
        {gpsError && (
          <div className="p-2 border border-[#f59e0b] bg-[#1a2117] text-[#f59e0b] space-y-1.5">
            <div className="flex items-start gap-1.5">
              <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
              <span>{gpsError}</span>
            </div>
            <button
              type="button"
              onClick={() => setShowManual((v) => !v)}
              className="w-full py-1 border border-[#f59e0b] text-[#f59e0b] hover:bg-[#f59e0b] hover:text-[#161b13] font-bold uppercase"
            >
              NHẬP TỌA ĐỘ THỦ CÔNG ĐỂ KIỂM TRA
            </button>
          </div>
        )}

        {/* Tùy chọn nhập tọa độ thủ công */}
        {showManual && (
          <form
            onSubmit={handleManualSubmit}
            className="p-2 border border-[#2d3329] bg-[#1a2117] space-y-2"
          >
            <div className="text-[9px] text-[#84907f] uppercase font-bold">
              NHẬP TỌA ĐỘ WGS84 (VĨ ĐỘ / KINH ĐỘ)
            </div>
            <div className="grid grid-cols-2 gap-1.5">
              <input
                type="text"
                value={manualLat}
                onChange={(e) => setManualLat(e.target.value)}
                placeholder="Vĩ độ (VD: 16.0544)"
                className="px-2 py-1 bg-[#161b13] border border-[#2d3329] text-[#dde2e4] text-[10px] focus:border-[#e2ffcc] focus:outline-none"
              />
              <input
                type="text"
                value={manualLng}
                onChange={(e) => setManualLng(e.target.value)}
                placeholder="Kinh độ (VD: 108.2022)"
                className="px-2 py-1 bg-[#161b13] border border-[#2d3329] text-[#dde2e4] text-[10px] focus:border-[#e2ffcc] focus:outline-none"
              />
            </div>
            {manualError && (
              <div className="text-[9px] text-[#dc2626]">{manualError}</div>
            )}
            <button
              type="submit"
              className="w-full py-1 bg-[#e2ffcc] text-[#161b13] font-bold uppercase text-[10px]"
            >
              ÁP DỤNG &amp; PHÂN TÍCH POSTGIS
            </button>
          </form>
        )}

        {/* Dữ liệu tọa độ và kết quả phân tích PostGIS */}
        {position && (
          <>
            <div className="p-2 border border-[#2d3329] bg-[#1a2117] flex items-center justify-between">
              <div>
                <span className="text-[9px] text-[#84907f] block uppercase">
                  TỌA ĐỘ {position.source === "gps" ? "GPS THIẾT BỊ" : "TÙY CHỌN"}
                </span>
                <span className="font-bold text-[#e2ffcc]">
                  {formatCoordinate([position.latitude, position.longitude], 4)}
                </span>
                {position.accuracyMeters !== null && (
                  <span className="text-[9px] text-[#84907f] ml-1.5">
                    (±{Math.round(position.accuracyMeters)}m)
                  </span>
                )}
              </div>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={() => setShowManual((v) => !v)}
                  className="px-1.5 py-1 border border-[#2d3329] hover:border-[#e2ffcc] text-[9px] text-[#84907f] hover:text-[#e2ffcc]"
                  title="Đổi sang tọa độ tùy chỉnh"
                >
                  ĐỔI
                </button>
                <button
                  type="button"
                  onClick={onFlyToMyLocation}
                  className="px-2 py-1 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#161b13] text-[#e2ffcc] flex items-center gap-1"
                  title="Đưa tâm bản đồ tới vị trí của tôi"
                >
                  <Navigation className="w-3 h-3" />
                  <span>BAY TỚI</span>
                </button>
              </div>
            </div>

            {spatialContext && !spatialContext.withinVietnamBounds && (
              <div className="p-1.5 border border-[#84907f] bg-[#1a2117] text-[9px] text-[#84907f]">
                Vị trí hiện nằm ngoài khung lãnh thổ Việt Nam — hệ thống vẫn đối
                chiếu trạm quan trắc Việt Nam gần nhất theo PostGIS KNN.
              </div>
            )}

            {/* Trạm gần nhất & thời tiết */}
            {nearestStation && (
              <div className="p-2 border border-[#2d3329] bg-[#1a2117] space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className="text-[9px] text-[#84907f] uppercase">
                    TRẠM QUAN TRẮC GẦN NHẤT
                  </span>
                  <span className="text-[#e2ffcc] font-bold">
                    cách {nearestStation.distanceKm} km
                  </span>
                </div>
                <div className="font-bold text-xs text-[#dde2e4]">
                  {nearestStation.name} ({nearestStation.code}) ·{" "}
                  <span className="text-[#84907f] font-normal">
                    {nearestStation.region}
                  </span>
                </div>
                {weather ? (
                  <div className="grid grid-cols-2 gap-1.5 pt-1">
                    <div className="p-1.5 bg-[#161b13] border border-[#2d3329]">
                      <span className="text-[8px] text-[#84907f] block">
                        NHIỆT ĐỘ / ĐỘ ẨM
                      </span>
                      <span className="font-bold text-[#dde2e4]">
                        {weather.temperature ?? "--"}°C ·{" "}
                        {weather.humidity ?? "--"}%
                      </span>
                    </div>
                    <div className="p-1.5 bg-[#161b13] border border-[#2d3329]">
                      <span className="text-[8px] text-[#84907f] block">
                        MƯA 1H / GIÓ
                      </span>
                      <span className="font-bold text-[#38bdf8]">
                        {weather.rain1h ?? 0} mm ·{" "}
                        <span className="text-[#e2ffcc]">
                          {weather.windSpeed ?? "--"} m/s
                        </span>
                      </span>
                    </div>
                  </div>
                ) : (
                  <div className="text-[9px] text-[#84907f]">
                    Chưa có số liệu quan trắc mới tại trạm này.
                  </div>
                )}
              </div>
            )}

            {/* Khoảng cách tâm bão active */}
            <div className="p-2 border border-[#2d3329] bg-[#1a2117] flex items-center justify-between">
              <span className="text-[9px] text-[#84907f] uppercase">
                BÃO ĐANG HOẠT ĐỘNG:
              </span>
              {storm ? (
                <span className="font-bold text-[#f97316]">
                  {storm.name} — cách {storm.distanceKm} km
                </span>
              ) : (
                <span className="text-[#e2ffcc]">Không có bão active</span>
              )}
            </div>

            {/* Cảnh báo lân cận trong 100km */}
            <div className="p-2 border border-[#2d3329] bg-[#1a2117] space-y-1">
              <div className="flex items-center justify-between">
                <span className="text-[9px] text-[#84907f] uppercase">
                  CẢNH BÁO TRONG BÁN KÍNH {spatialContext?.radiusKm ?? 100} KM:
                </span>
                <span
                  className={`font-bold ${alerts.length > 0 ? "text-[#dc2626]" : "text-[#e2ffcc]"}`}
                >
                  {alerts.length} cảnh báo
                </span>
              </div>
              {alerts.slice(0, 2).map((a) => (
                <div
                  key={a.id}
                  className="p-1.5 bg-[#161b13] border border-[#dc2626]/60 text-[9px]"
                >
                  <strong className="text-[#f97316]">[{a.severityLevel}]</strong>{" "}
                  {a.title} ({a.distanceKm} km)
                </div>
              ))}
            </div>

            {/* Nút hành động nhanh */}
            <div className="grid grid-cols-1 gap-1.5 pt-1">
              <button
                id="btn-myloc-subscribe"
                type="button"
                onClick={() =>
                  onSubscribeHere(
                    position.latitude,
                    position.longitude,
                    locationLabel,
                  )
                }
                className="w-full py-1.5 px-2 bg-[#e2ffcc] text-[#161b13] font-bold uppercase text-[10px] flex items-center justify-center gap-1.5 hover:opacity-90"
              >
                <Mail className="w-3.5 h-3.5" />
                <span>NHẬN CẢNH BÁO EMAIL TẠI VỊ TRÍ NÀY</span>
              </button>
              <button
                id="btn-myloc-askai"
                type="button"
                onClick={() => onAskAiHere(spatialContext, position)}
                className="w-full py-1.5 px-2 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc] font-bold uppercase text-[10px] flex items-center justify-center gap-1.5"
              >
                <Bot className="w-3.5 h-3.5" />
                <span>HỎI TRỢ LÝ AI VỀ KHU VỰC CỦA TÔI</span>
              </button>
            </div>
          </>
        )}
      </div>
    </section>
  );
};

export default MyLocationCard;
