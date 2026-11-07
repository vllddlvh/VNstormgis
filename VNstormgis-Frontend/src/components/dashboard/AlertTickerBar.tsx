import { useEffect, useState } from "react";
import {
  ChevronLeft,
  ChevronRight,
  Navigation,
  Pause,
  Play,
} from "lucide-react";
import { formatAlertTime, getAlertCoordinates } from "@/lib/alertUtils";
import type { AlertFeature } from "@/types/alert";
import { AlertContentSource, AlertSeverityBadge } from "./AlertSeverityBadge";

export interface AlertTickerBarProps {
  alerts: AlertFeature[];
  isLoading: boolean;
  isRefreshing: boolean;
  hasLoaded: boolean;
  error: string | null;
  lastSuccessfulFetchAt: string | null;
  mapReady: boolean;
  onSelectAlert: (alert: AlertFeature) => void;
  onFlyTo: (alert: AlertFeature) => void;
  className?: string;
}

/** Rotates published alerts without substituting samples or changing API order. */
export function AlertTickerBar({
  alerts,
  isLoading,
  isRefreshing,
  hasLoaded,
  error,
  lastSuccessfulFetchAt,
  mapReady,
  onSelectAlert,
  onFlyTo,
  className = "",
}: AlertTickerBarProps) {
  const [currentId, setCurrentId] = useState<string | null>(null);
  const [manuallyPaused, setManuallyPaused] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(
    () => window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  );
  const currentIndex = Math.max(
    0,
    alerts.findIndex((alert) => alert.id === currentId),
  );
  const currentAlert = alerts[currentIndex];

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const onChange = () => setReducedMotion(query.matches);
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);
  useEffect(() => {
    setCurrentId((previous) =>
      alerts.some((alert) => alert.id === previous)
        ? previous
        : (alerts[0]?.id ?? null),
    );
  }, [alerts]);
  useEffect(() => {
    if (
      manuallyPaused ||
      hovered ||
      focused ||
      reducedMotion ||
      alerts.length <= 1
    )
      return;
    const timer = window.setInterval(() => {
      setCurrentId((previous) => {
        const index = Math.max(
          0,
          alerts.findIndex((alert) => alert.id === previous),
        );
        return alerts[(index + 1) % alerts.length].id;
      });
    }, 7000);
    return () => window.clearInterval(timer);
  }, [alerts, manuallyPaused, hovered, focused, reducedMotion]);

  const navigate = (direction: number) =>
    setCurrentId(
      alerts[(currentIndex + direction + alerts.length) % alerts.length].id,
    );
  const canLocate =
    currentAlert && mapReady && getAlertCoordinates(currentAlert) !== null;
  const status = isLoading
    ? "Đang tải cảnh báo…"
    : !hasLoaded
      ? "Không tải được cảnh báo"
      : error
        ? "Chưa cập nhật được · Kết quả lần tải trước"
        : isRefreshing
          ? "Đang cập nhật cảnh báo…"
          : null;

  return (
    <section
      aria-label="Bản tin cảnh báo"
      data-testid="alert-ticker"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onFocus={() => setFocused(true)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget))
          setFocused(false);
      }}
      className={`absolute bottom-20 left-3 right-3 sm:bottom-4 z-[1000] max-w-2xl border border-charcoal bg-carbon font-mono text-topo sm:left-4 sm:right-64 ${className}`}
    >
      {status && (
        <p
          role="status"
          className="border-b border-charcoal px-3 py-2 text-[10px] text-sage"
        >
          {status}
          {error && lastSuccessfulFetchAt
            ? ` · Tải thành công: ${formatAlertTime(lastSuccessfulFetchAt)}`
            : ""}
        </p>
      )}
      {!currentAlert ? (
        hasLoaded ? (
          <p className="p-3 text-xs text-sage">
            {error
              ? "Lần tải trước: không có cảnh báo đang hoạt động"
              : "Không có cảnh báo đang hoạt động"}
          </p>
        ) : null
      ) : (
        <div
          data-alert-id={currentAlert.id}
          data-severity={currentAlert.properties.severity_level}
          data-fallback={String(currentAlert.properties.is_fallback)}
        >
          <button
            type="button"
            onClick={() => onSelectAlert(currentAlert)}
            className="block w-full space-y-2 p-3 text-left hover:bg-gis-panel focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
          >
            <span className="flex flex-wrap items-center gap-2">
              <AlertSeverityBadge
                severity={currentAlert.properties.severity_level}
              />
              <AlertContentSource
                isFallback={currentAlert.properties.is_fallback}
              />
            </span>
            <span
              className="block truncate text-xs font-bold"
              title={currentAlert.properties.title || undefined}
            >
              {currentAlert.properties.title || "Chưa có tiêu đề"}
            </span>
            <span className="block truncate text-[10px] text-sage">
              {currentAlert.properties.location_name || "Chưa có tên trạm"} ·{" "}
              {formatAlertTime(currentAlert.properties.triggered_at)}
            </span>
          </button>
          <div className="flex flex-wrap items-center justify-between gap-1 border-t border-charcoal px-2 py-1">
            <button
              type="button"
              onClick={() => onFlyTo(currentAlert)}
              disabled={!canLocate}
              aria-label="Xem vị trí cảnh báo đang hiển thị"
              title={
                !mapReady
                  ? "Bản đồ chưa sẵn sàng"
                  : !canLocate
                    ? "Cảnh báo không có tọa độ hợp lệ"
                    : "Xem vị trí cảnh báo"
              }
              className="inline-flex min-h-9 items-center gap-1 px-1 text-[10px] font-bold text-mint focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Navigation aria-hidden="true" className="h-3 w-3" /> XEM VỊ TRÍ
            </button>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => navigate(-1)}
                disabled={alerts.length <= 1}
                aria-label="Bản tin trước"
                className="p-2 text-sage hover:text-mint focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint disabled:opacity-50"
              >
                <ChevronLeft aria-hidden="true" className="h-4 w-4" />
              </button>
              <span className="text-[10px] text-sage">
                {currentIndex + 1}/{alerts.length}
              </span>
              <button
                type="button"
                onClick={() => navigate(1)}
                disabled={alerts.length <= 1}
                aria-label="Bản tin kế tiếp"
                className="p-2 text-sage hover:text-mint focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint disabled:opacity-50"
              >
                <ChevronRight aria-hidden="true" className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={() => setManuallyPaused((previous) => !previous)}
                disabled={reducedMotion || alerts.length <= 1}
                aria-label={
                  manuallyPaused
                    ? "Chạy luân phiên bản tin"
                    : "Dừng luân phiên bản tin"
                }
                aria-pressed={manuallyPaused || reducedMotion}
                title={
                  reducedMotion
                    ? "Đã dừng theo tùy chọn giảm chuyển động"
                    : undefined
                }
                className="p-2 text-sage hover:text-mint focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint disabled:opacity-50"
              >
                {manuallyPaused || reducedMotion ? (
                  <Play aria-hidden="true" className="h-3 w-3" />
                ) : (
                  <Pause aria-hidden="true" className="h-3 w-3" />
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

export default AlertTickerBar;
