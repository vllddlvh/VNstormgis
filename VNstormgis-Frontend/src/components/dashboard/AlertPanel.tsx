import { useEffect, useRef } from "react";
import { ArrowLeft, Bell, RefreshCw, X } from "lucide-react";
import { formatAlertTime } from "@/lib/alertUtils";
import type { AlertFeature } from "@/types/alert";
import { AlertDetail } from "./AlertDetail";
import { AlertContentSource, AlertSeverityBadge } from "./AlertSeverityBadge";

export interface AlertPanelProps {
  alerts: AlertFeature[];
  isLoading: boolean;
  isRefreshing: boolean;
  hasLoaded: boolean;
  error: string | null;
  lastSuccessfulFetchAt: string | null;
  selectedAlert: AlertFeature | null;
  notice: string | null;
  mapReady: boolean;
  onSelectAlert: (alert: AlertFeature) => void;
  onBack: () => void;
  onRefresh: () => void;
  onClose: () => void;
  onFlyTo: (alert: AlertFeature) => void;
  onAskAi: (alert: AlertFeature) => void;
}

export function AlertPanel({
  alerts,
  isLoading,
  isRefreshing,
  hasLoaded,
  error,
  lastSuccessfulFetchAt,
  selectedAlert,
  notice,
  mapReady,
  onSelectAlert,
  onBack,
  onRefresh,
  onClose,
  onFlyTo,
  onAskAi,
}: AlertPanelProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    closeButtonRef.current?.focus();
  }, []);
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);
  useEffect(() => {
    if (contentRef.current) contentRef.current.scrollTop = 0;
  }, [selectedAlert?.id]);

  return (
    <aside
      id="alert-panel"
      data-testid="alert-panel"
      role="dialog"
      aria-modal="false"
      aria-labelledby="alert-panel-title"
      className="absolute bottom-3 right-3 top-3 z-[1070] flex w-[400px] max-w-[calc(100%-24px)] select-text flex-col border border-charcoal bg-carbon font-mono text-topo"
    >
      <header className="shrink-0 border-b border-charcoal p-4">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <h2
              id="alert-panel-title"
              className="flex items-center gap-2 text-sm font-bold text-mint"
            >
              <Bell aria-hidden="true" className="h-4 w-4" /> CẢNH BÁO{" "}
              {hasLoaded ? `(${alerts.length})` : ""}
            </h2>
            <p className="mt-2 text-[10px] text-sage">
              Danh sách đang hoạt động · Kiểm tra mỗi 60 giây
            </p>
          </div>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label="Đóng bảng cảnh báo"
            className="shrink-0 border border-charcoal p-2 text-sage hover:bg-charcoal hover:text-mint focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
          >
            <X aria-hidden="true" className="h-4 w-4" />
          </button>
        </div>
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
          {selectedAlert ? (
            <button
              type="button"
              onClick={onBack}
              className="inline-flex min-h-9 items-center gap-1 text-[11px] text-mint hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
            >
              <ArrowLeft aria-hidden="true" className="h-4 w-4" /> QUAY LẠI
            </button>
          ) : (
            <span className="text-[10px] text-sage">
              Giờ Việt Nam (UTC+7)
            </span>
          )}
          <button
            type="button"
            data-testid="alert-refresh"
            onClick={onRefresh}
            disabled={isLoading || isRefreshing}
            className="inline-flex min-h-9 items-center gap-1.5 border border-charcoal px-2 py-1 text-[10px] text-mint hover:bg-charcoal focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint disabled:opacity-50"
          >
            <RefreshCw
              aria-hidden="true"
              className={`h-3 w-3 ${isRefreshing ? "motion-safe:animate-spin" : ""}`}
            />{" "}
            {isRefreshing ? "ĐANG LÀM MỚI…" : "LÀM MỚI"}
          </button>
        </div>
      </header>
      <div
        ref={contentRef}
        className="min-h-0 flex-1 overflow-y-auto p-4 pb-24"
      >
        <div
          role="status"
          aria-live="polite"
          className="space-y-2 text-xs leading-relaxed"
        >
          {isLoading && <p className="pb-3 text-sage">Đang tải cảnh báo…</p>}
          {notice && (
            <p className="mb-3 border border-sage p-3 text-sage">{notice}</p>
          )}
          {error && (
            <div
              data-testid="alert-error"
              className="mb-4 space-y-2 border border-weather-warning p-3"
            >
              <p className="font-bold text-weather-warning">
                {hasLoaded
                  ? "Chưa cập nhật được cảnh báo"
                  : "Không tải được cảnh báo"}
              </p>
              <p className="break-words text-sage">{error}</p>
              {hasLoaded && (
                <p className="text-sage">
                  Đang giữ kết quả lần tải trước; chưa xác nhận tình trạng hiện
                  tại.
                </p>
              )}
              {!hasLoaded && (
                <button
                  type="button"
                  onClick={onRefresh}
                  disabled={isLoading || isRefreshing}
                  className="border border-sage px-3 py-2 text-topo hover:bg-charcoal focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
                >
                  THỬ LẠI
                </button>
              )}
            </div>
          )}
          {lastSuccessfulFetchAt && (
            <p className="mb-4 text-[10px] text-sage">
              Cập nhật gần nhất lúc:{" "}
              {formatAlertTime(lastSuccessfulFetchAt)}
            </p>
          )}
        </div>
        {selectedAlert ? (
          <AlertDetail
            key={selectedAlert.id}
            alert={selectedAlert}
            mapReady={mapReady}
            onFlyTo={onFlyTo}
            onAskAi={onAskAi}
          />
        ) : (
          <div data-testid="alert-list" className="space-y-3">
            {hasLoaded && alerts.length === 0 && (
              <p
                data-testid="alert-empty"
                className="border border-charcoal p-4 text-xs leading-relaxed text-sage"
              >
                {error
                  ? "Kết quả lần tải trước: không có cảnh báo đang hoạt động"
                  : "Không có cảnh báo đang hoạt động"}
              </p>
            )}
            {alerts.map((alert) => (
              <button
                type="button"
                key={alert.id}
                data-alert-id={alert.id}
                data-severity={alert.properties.severity_level}
                data-fallback={String(alert.properties.is_fallback)}
                onClick={() => onSelectAlert(alert)}
                className="block w-full space-y-3 border border-charcoal bg-gis-panel p-3 text-left hover:border-sage focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
              >
                <span className="flex flex-wrap items-center gap-2">
                  <AlertSeverityBadge
                    severity={alert.properties.severity_level}
                  />
                  <AlertContentSource
                    isFallback={alert.properties.is_fallback}
                  />
                </span>
                <span className="block break-words text-sm font-bold leading-relaxed">
                  {alert.properties.title || "Chưa có tiêu đề"}
                </span>
                <span className="block break-words text-xs text-sage">
                  {alert.properties.location_name || "Chưa có tên trạm"}
                  {alert.properties.location_code
                    ? ` [${alert.properties.location_code}]`
                    : ""}
                </span>
                <span className="block text-[10px] text-sage">
                  {formatAlertTime(alert.properties.triggered_at)}
                </span>
                <span className="block text-[10px] text-mint">
                  ĐỌC CHI TIẾT →
                </span>
              </button>
            ))}
          </div>
        )}
      </div>
    </aside>
  );
}
