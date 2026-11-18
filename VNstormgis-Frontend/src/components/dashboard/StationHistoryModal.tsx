/**
 * @file StationHistoryModal.tsx
 * @description Bảng biểu đồ diễn biến thời tiết lịch sử 24h / 72h / 7 ngày (SVG thuần chuẩn San Rita,
 * không dùng thư viện biểu đồ nặng nhằm giữ bundle < 1.5MB). Tiêu thụ trực tiếp
 * GET /api/v1/weather/history?location_code=...&hours=... (hợp đồng W2-07).
 */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  X,
  RefreshCw,
  BarChart3,
  CloudRain,
  Wind,
  Thermometer,
  Mail,
} from "lucide-react";
import { StationMarkerData } from "@/types/map";
import { WeatherHistoryData, WeatherReading } from "@/types/weather";
import { weatherService } from "@/services/weatherService";

export interface StationHistoryModalProps {
  station: StationMarkerData | null;
  onClose: () => void;
  onSubscribeStation?: (station: StationMarkerData) => void;
}

type WindowHours = 24 | 72 | 168;
type MetricTab = "rain" | "wind" | "temp";

const SVG_W = 680;
const SVG_H = 210;
const PAD_L = 44;
const PAD_R = 18;
const PAD_T = 18;
const PAD_B = 34;

export const StationHistoryModal: React.FC<StationHistoryModalProps> = ({
  station,
  onClose,
  onSubscribeStation,
}) => {
  const [hours, setHours] = useState<WindowHours>(24);
  const [metric, setMetric] = useState<MetricTab>("rain");
  const [history, setHistory] = useState<WeatherHistoryData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hoverIdx, setHoverIdx] = useState<number | null>(null);

  const loadHistory = useCallback(
    async (code: string, targetHours: WindowHours) => {
      setLoading(true);
      setError(null);
      setHoverIdx(null);
      try {
        const data = await weatherService.getWeatherHistory(code, targetHours);
        setHistory(data);
      } catch {
        setError(
          "Không tải được dữ liệu lịch sử thời tiết từ máy chủ. Vui lòng thử lại.",
        );
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    if (!station) return;
    void loadHistory(station.code, hours);
  }, [station, hours, loadHistory]);

  useEffect(() => {
    if (!station) return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [station, onClose]);

  const readings: WeatherReading[] = useMemo(
    () => history?.readings ?? [],
    [history],
  );

  const stats = useMemo(() => {
    if (readings.length === 0) {
      return {
        maxRain1h: 0,
        totalRain1h: 0,
        maxWind: 0,
        maxGust: 0,
        minTemp: null as number | null,
        maxTemp: null as number | null,
      };
    }
    let maxRain1h = 0;
    let totalRain1h = 0;
    let maxWind = 0;
    let maxGust = 0;
    let minTemp: number | null = null;
    let maxTemp: number | null = null;

    for (const r of readings) {
      const rain = typeof r.rain1h === "number" ? r.rain1h : 0;
      if (rain > maxRain1h) maxRain1h = rain;
      totalRain1h += rain;

      const wind = typeof r.windSpeed === "number" ? r.windSpeed : 0;
      if (wind > maxWind) maxWind = wind;

      const gust = typeof r.windGust === "number" ? r.windGust : 0;
      if (gust > maxGust) maxGust = gust;

      if (typeof r.temperature === "number") {
        if (minTemp === null || r.temperature < minTemp)
          minTemp = r.temperature;
        if (maxTemp === null || r.temperature > maxTemp)
          maxTemp = r.temperature;
      }
    }
    return {
      maxRain1h: Math.round(maxRain1h * 10) / 10,
      totalRain1h: Math.round(totalRain1h * 10) / 10,
      maxWind: Math.round(maxWind * 10) / 10,
      maxGust: Math.round(maxGust * 10) / 10,
      minTemp: minTemp !== null ? Math.round(minTemp * 10) / 10 : null,
      maxTemp: maxTemp !== null ? Math.round(maxTemp * 10) / 10 : null,
    };
  }, [readings]);

  if (!station) return null;

  const plotW = SVG_W - PAD_L - PAD_R;
  const plotH = SVG_H - PAD_T - PAD_B;

  const values = readings.map((r) => {
    if (metric === "rain") return typeof r.rain1h === "number" ? r.rain1h : 0;
    if (metric === "wind")
      return typeof r.windSpeed === "number" ? r.windSpeed : 0;
    return typeof r.temperature === "number" ? r.temperature : 0;
  });

  const secondaryValues =
    metric === "wind"
      ? readings.map((r) => (typeof r.windGust === "number" ? r.windGust : 0))
      : [];

  const minVal =
    metric === "temp" && values.length > 0
      ? Math.floor(Math.min(...values) - 2)
      : 0;
  const rawMax = Math.max(
    ...(values.length > 0 ? values : [1]),
    ...(secondaryValues.length > 0 ? secondaryValues : [0]),
  );
  const maxVal = Math.max(minVal + 4, Math.ceil(rawMax * 1.15));
  const span = Math.max(1, maxVal - minVal);

  const xAt = (idx: number) =>
    readings.length <= 1
      ? PAD_L + plotW / 2
      : PAD_L + (idx / (readings.length - 1)) * plotW;
  const yAt = (v: number) => PAD_T + plotH - ((v - minVal) / span) * plotH;

  const linePoints = values
    .map((v, idx) => `${xAt(idx).toFixed(1)},${yAt(v).toFixed(1)}`)
    .join(" ");

  const secondaryPoints =
    secondaryValues.length > 0
      ? secondaryValues
          .map((v, idx) => `${xAt(idx).toFixed(1)},${yAt(v).toFixed(1)}`)
          .join(" ")
      : "";

  const activeReading =
    hoverIdx !== null && readings[hoverIdx]
      ? readings[hoverIdx]
      : readings.length > 0
        ? readings[readings.length - 1]
        : null;

  const formatTimeLabel = (iso: string) => {
    const d = new Date(iso);
    return d.toLocaleString("vi-VN", {
      hour: "2-digit",
      minute: "2-digit",
      day: "2-digit",
      month: "2-digit",
      hour12: false,
    });
  };

  return (
    <div
      id="station-history-modal"
      data-testid="station-history-modal"
      className="fixed inset-0 z-[1300] bg-black/75 flex items-center justify-center p-4 font-mono"
      onClick={onClose}
    >
      <div
        className="w-full max-w-3xl bg-[#161b13] border border-[#e2ffcc] shadow-flat text-[#dde2e4] max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="px-4 py-3 border-b border-[#2d3329] bg-[#1a2117] flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <BarChart3 className="w-4 h-4 text-[#e2ffcc]" />
            <div>
              <h2 className="text-xs font-bold uppercase text-[#e2ffcc]">
                DIỄN BIẾN KHÍ TƯỢNG LỊCH SỬ — {station.name} ({station.code})
              </h2>
              <p className="text-[10px] text-[#84907f]">
                Khu vực: {station.region} · Dữ liệu thực đo từ CSDL chuỗi thời
                gian PostGIS
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            {onSubscribeStation && (
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onSubscribeStation(station);
                }}
                className="px-2.5 py-1 border border-[#e2ffcc] bg-[#161b13] hover:bg-[#e2ffcc] text-[10px] text-[#e2ffcc] hover:text-[#161b13] font-bold uppercase flex items-center gap-1"
              >
                <Mail className="w-3 h-3" />
                <span>NHẬN CẢNH BÁO EMAIL</span>
              </button>
            )}
            <button
              type="button"
              onClick={() => void loadHistory(station.code, hours)}
              disabled={loading}
              className="p-1.5 border border-[#2d3329] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc]"
              title="Tải lại chuỗi thời gian"
            >
              <RefreshCw
                className={`w-3.5 h-3.5 ${loading ? "animate-spin text-[#e2ffcc]" : ""}`}
              />
            </button>
            <button
              type="button"
              onClick={onClose}
              aria-label="Đóng biểu đồ lịch sử"
              className="p-1.5 border border-[#2d3329] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc]"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>

        {/* Bộ chọn khung giờ & chỉ số */}
        <div className="px-4 py-2.5 border-b border-[#2d3329] flex flex-wrap items-center justify-between gap-2 bg-[#161b13]">
          <div className="flex items-center gap-1">
            {(
              [
                { id: "rain", label: "LƯỢNG MƯA 1H (mm)", icon: CloudRain },
                { id: "wind", label: "GIÓ & GIÓ GIẬT (m/s)", icon: Wind },
                { id: "temp", label: "NHIỆT ĐỘ (°C)", icon: Thermometer },
              ] as const
            ).map((item) => {
              const Icon = item.icon;
              const active = metric === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setMetric(item.id)}
                  className={`px-2.5 py-1 text-[10px] border uppercase font-bold flex items-center gap-1.5 transition-colors ${
                    active
                      ? "border-[#e2ffcc] bg-[#e2ffcc] text-[#161b13]"
                      : "border-[#2d3329] bg-[#1a2117] text-[#84907f] hover:text-[#dde2e4]"
                  }`}
                >
                  <Icon className="w-3 h-3" />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </div>

          <div className="flex items-center gap-1">
            {(
              [
                { h: 24, label: "24 GIỜ" },
                { h: 72, label: "72 GIỜ" },
                { h: 168, label: "7 NGÀY" },
              ] as const
            ).map((w) => (
              <button
                key={w.h}
                type="button"
                onClick={() => setHours(w.h)}
                className={`px-2.5 py-1 text-[10px] border uppercase font-bold ${
                  hours === w.h
                    ? "border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc]"
                    : "border-[#2d3329] bg-[#161b13] text-[#84907f] hover:text-[#dde2e4]"
                }`}
              >
                {w.label}
              </button>
            ))}
          </div>
        </div>

        {/* Nội dung chính */}
        <div className="p-4 space-y-3">
          {/* Thẻ thống kê tóm tắt */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[10px]">
            <div className="p-2.5 border border-[#2d3329] bg-[#1a2117]">
              <span className="text-[#84907f] block uppercase text-[9px]">
                MƯA 1H CAO NHẤT
              </span>
              <span className="text-sm font-bold text-[#38bdf8]">
                {stats.maxRain1h} mm
              </span>
            </div>
            <div className="p-2.5 border border-[#2d3329] bg-[#1a2117]">
              <span className="text-[#84907f] block uppercase text-[9px]">
                GIÓ / GIẬT ĐỈNH
              </span>
              <span className="text-sm font-bold text-[#e2ffcc]">
                {stats.maxWind} / {stats.maxGust} m/s
              </span>
            </div>
            <div className="p-2.5 border border-[#2d3329] bg-[#1a2117]">
              <span className="text-[#84907f] block uppercase text-[9px]">
                BIÊN ĐỘ NHIỆT
              </span>
              <span className="text-sm font-bold text-[#f59e0b]">
                {stats.minTemp !== null && stats.maxTemp !== null
                  ? `${stats.minTemp}°C – ${stats.maxTemp}°C`
                  : "--"}
              </span>
            </div>
            <div className="p-2.5 border border-[#2d3329] bg-[#1a2117]">
              <span className="text-[#84907f] block uppercase text-[9px]">
                SỐ LƯỢT ĐO TRONG CỬA SỔ
              </span>
              <span className="text-sm font-bold text-[#dde2e4]">
                {readings.length} bản ghi
              </span>
            </div>
          </div>

          {/* Khung đồ thị SVG */}
          {loading ? (
            <div className="h-52 border border-[#2d3329] bg-[#1a2117] flex items-center justify-center text-xs text-[#e2ffcc]">
              Đang tải chuỗi thời gian khí tượng từ PostGIS...
            </div>
          ) : error ? (
            <div className="h-52 border border-[#dc2626] bg-[#1a2117] flex items-center justify-center text-xs text-[#dc2626] px-4 text-center">
              {error}
            </div>
          ) : readings.length === 0 ? (
            <div className="h-52 border border-[#2d3329] bg-[#1a2117] flex items-center justify-center text-xs text-[#84907f] px-4 text-center">
              Chưa có bản ghi lịch sử nào trong {hours} giờ gần nhất cho trạm{" "}
              {station.name} ({station.code}).
            </div>
          ) : (
            <div className="border border-[#2d3329] bg-[#1a2117] p-3">
              <svg
                viewBox={`0 0 ${SVG_W} ${SVG_H}`}
                className="w-full h-52 overflow-visible select-none"
                onMouseLeave={() => setHoverIdx(null)}
              >
                {/* Lưới ngang 4 bậc */}
                {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
                  const val = minVal + span * ratio;
                  const y = yAt(val);
                  return (
                    <g key={ratio}>
                      <line
                        x1={PAD_L}
                        y1={y}
                        x2={SVG_W - PAD_R}
                        y2={y}
                        stroke="#2d3329"
                        strokeDasharray="3,3"
                      />
                      <text
                        x={PAD_L - 6}
                        y={y + 3}
                        textAnchor="end"
                        fill="#84907f"
                        fontSize="9"
                      >
                        {val.toFixed(0)}
                      </text>
                    </g>
                  );
                })}

                {/* Cột mưa hoặc đường biểu diễn */}
                {metric === "rain" ? (
                  values.map((v, idx) => {
                    const barW = Math.max(
                      3,
                      Math.min(18, plotW / Math.max(readings.length * 1.5, 1)),
                    );
                    const x = xAt(idx) - barW / 2;
                    const y = yAt(v);
                    const h = Math.max(1, PAD_T + plotH - y);
                    return (
                      <rect
                        key={readings[idx].recordedAt}
                        x={x}
                        y={y}
                        width={barW}
                        height={h}
                        fill={v >= 50 ? "#dc2626" : "#38bdf8"}
                        opacity={hoverIdx === idx ? 1 : 0.82}
                      />
                    );
                  })
                ) : (
                  <>
                    {secondaryPoints && (
                      <polyline
                        fill="none"
                        stroke="#f97316"
                        strokeWidth="1.5"
                        strokeDasharray="4,3"
                        points={secondaryPoints}
                      />
                    )}
                    <polyline
                      fill="none"
                      stroke={metric === "wind" ? "#e2ffcc" : "#f59e0b"}
                      strokeWidth="2"
                      points={linePoints}
                    />
                    {values.map((v, idx) => (
                      <circle
                        key={readings[idx].recordedAt}
                        cx={xAt(idx)}
                        cy={yAt(v)}
                        r={hoverIdx === idx ? 4 : 2.2}
                        fill={metric === "wind" ? "#e2ffcc" : "#f59e0b"}
                      />
                    ))}
                  </>
                )}

                {/* Đường gióng khi rê chuột */}
                {hoverIdx !== null && readings[hoverIdx] && (
                  <line
                    x1={xAt(hoverIdx)}
                    y1={PAD_T}
                    x2={xAt(hoverIdx)}
                    y2={PAD_T + plotH}
                    stroke="#e2ffcc"
                    strokeWidth="1"
                  />
                )}

                {/* Nhãn trục thời gian đầu - giữa - cuối */}
                <text
                  x={PAD_L}
                  y={SVG_H - 10}
                  textAnchor="start"
                  fill="#84907f"
                  fontSize="9"
                >
                  {formatTimeLabel(readings[0].recordedAt)}
                </text>
                {readings.length > 2 && (
                  <text
                    x={PAD_L + plotW / 2}
                    y={SVG_H - 10}
                    textAnchor="middle"
                    fill="#84907f"
                    fontSize="9"
                  >
                    {formatTimeLabel(
                      readings[Math.floor(readings.length / 2)].recordedAt,
                    )}
                  </text>
                )}
                {readings.length > 1 && (
                  <text
                    x={SVG_W - PAD_R}
                    y={SVG_H - 10}
                    textAnchor="end"
                    fill="#84907f"
                    fontSize="9"
                  >
                    {formatTimeLabel(
                      readings[readings.length - 1].recordedAt,
                    )}
                  </text>
                )}

                {/* Vùng bắt sự kiện rê chuột */}
                {readings.map((r, idx) => {
                  const sliceW = plotW / Math.max(readings.length, 1);
                  return (
                    <rect
                      key={`hit-${r.recordedAt}`}
                      x={xAt(idx) - sliceW / 2}
                      y={PAD_T}
                      width={Math.max(sliceW, 6)}
                      height={plotH}
                      fill="transparent"
                      onMouseEnter={() => setHoverIdx(idx)}
                    />
                  );
                })}
              </svg>

              {/* Thanh chi tiết điểm đang chọn */}
              {activeReading && (
                <div className="mt-2 pt-2 border-t border-[#2d3329] flex flex-wrap items-center justify-between gap-2 text-[10px]">
                  <span className="text-[#84907f]">
                    MỐC ĐO:{" "}
                    <strong className="text-[#e2ffcc]">
                      {formatTimeLabel(activeReading.recordedAt)}
                    </strong>
                  </span>
                  <div className="flex flex-wrap items-center gap-3">
                    <span>
                      Mưa 1h:{" "}
                      <strong className="text-[#38bdf8]">
                        {activeReading.rain1h ?? 0} mm
                      </strong>
                    </span>
                    <span>
                      Gió:{" "}
                      <strong className="text-[#e2ffcc]">
                        {activeReading.windSpeed ?? "--"} m/s
                      </strong>
                      {typeof activeReading.windGust === "number" && (
                        <span className="text-[#f97316]">
                          {" "}
                          (giật {activeReading.windGust} m/s)
                        </span>
                      )}
                    </span>
                    <span>
                      Nhiệt độ:{" "}
                      <strong className="text-[#f59e0b]">
                        {activeReading.temperature ?? "--"}°C
                      </strong>
                    </span>
                    <span>
                      Áp suất:{" "}
                      <strong className="text-[#dde2e4]">
                        {activeReading.pressure ?? "--"} hPa
                      </strong>
                    </span>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default StationHistoryModal;
