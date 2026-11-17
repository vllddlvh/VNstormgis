import { Navigation, RefreshCw } from "lucide-react";
import { useAlertDetail } from "@/hooks/useAlerts";
import { formatAlertTime, getAlertCoordinates } from "@/lib/alertUtils";
import type { AlertFeature } from "@/types/alert";
import { AlertContentSource, AlertSeverityBadge } from "./AlertSeverityBadge";

const PARAMETER_LABELS = new Map<string, string>([
  ["rain_1h", "Mưa 1 giờ"],
  ["rain_3h", "Mưa 3 giờ"],
  ["wind_speed", "Tốc độ gió"],
  ["wind_gust", "Gió giật"],
  ["storm_dist", "Khoảng cách bão"],
]);

interface AlertDetailProps {
  alert: AlertFeature;
  mapReady: boolean;
  onFlyTo: (alert: AlertFeature) => void;
  onAskAi: (alert: AlertFeature) => void;
}

export function AlertDetail({
  alert,
  mapReady,
  onFlyTo,
  onAskAi,
}: AlertDetailProps) {
  const { detail, isLoading, error, retry } = useAlertDetail(alert.id);
  const canLocate = mapReady && getAlertCoordinates(alert) !== null;
  const locationReason = !mapReady
    ? "Bản đồ chưa sẵn sàng"
    : !canLocate
      ? "Cảnh báo không có tọa độ hợp lệ"
      : null;
  const properties = alert.properties;
  const snapshot = detail?.factual_snapshot;

  return (
    <section
      data-testid="alert-detail"
      data-alert-id={alert.id}
      className="space-y-5 text-sm leading-relaxed"
    >
      <div className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <AlertSeverityBadge
            severity={detail?.severity_level ?? properties.severity_level}
          />
          <AlertContentSource
            isFallback={detail?.is_fallback ?? properties.is_fallback}
          />
        </div>
        <h3 className="break-words text-base font-bold text-topo">
          {detail?.title || properties.title || "Chưa có tiêu đề"}
        </h3>
        <p className="break-words text-xs text-sage">
          {properties.location_name || "Chưa có tên trạm"}
          {properties.location_code ? ` [${properties.location_code}]` : ""}
        </p>
        <p className="text-[11px] text-sage">
          Phát:{" "}
          {formatAlertTime(
            detail ? detail.triggered_at : properties.triggered_at,
          )}
        </p>
        <p className="break-all text-[10px] text-sage">ID: {alert.id}</p>
        <button
          type="button"
          data-testid="alert-flyto"
          onClick={() => onFlyTo(alert)}
          disabled={!canLocate}
          aria-describedby={
            locationReason ? "alert-location-reason" : undefined
          }
          className="inline-flex min-h-10 items-center gap-2 border border-mint px-3 py-2 text-xs font-bold text-mint hover:bg-charcoal focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Navigation aria-hidden="true" className="h-4 w-4" /> XEM VỊ TRÍ
        </button>
        <button
          type="button"
          data-testid="alert-ask-ai"
          data-alert-id={alert.id}
          onClick={() => onAskAi(alert)}
          className="ml-2 inline-flex min-h-11 items-center border border-mint px-3 py-2 text-xs font-bold text-mint hover:bg-charcoal focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
        >
          HỎI AI VỀ CẢNH BÁO NÀY
        </button>
        {locationReason && (
          <p id="alert-location-reason" className="text-xs text-sage">
            {locationReason}
          </p>
        )}
      </div>
      {isLoading && (
        <p role="status" className="text-xs text-sage">
          Đang tải chi tiết cảnh báo…
        </p>
      )}
      {error && (
        <div
          data-testid="alert-detail-error"
          className="space-y-3 border border-weather-warning p-3 text-xs"
        >
          <p role="status" className="text-weather-warning">
            Không tải được chi tiết cảnh báo
          </p>
          <p className="break-words text-sage">{error}</p>
          <button
            type="button"
            onClick={() => void retry()}
            className="inline-flex items-center gap-2 border border-sage px-3 py-2 text-topo hover:bg-charcoal focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
          >
            <RefreshCw aria-hidden="true" className="h-3 w-3" /> THỬ LẠI
          </button>
        </div>
      )}
      {detail && !isLoading && !error && (
        <>
          {detail.is_fallback && (
            <p className="border-l-2 border-sage pl-3 text-xs text-sage">
              Nội dung theo mẫu từ dữ kiện hệ thống
            </p>
          )}
          <section className="space-y-2 border-t border-charcoal pt-4">
            <h4 className="text-[11px] font-bold uppercase text-mint">
              Nguyên nhân kích hoạt
            </h4>
            <p className="whitespace-pre-wrap break-words">
              {detail.trigger_reason || "Chưa có nguyên nhân được lưu"}
            </p>
          </section>
          <section className="space-y-2 border-t border-charcoal pt-4">
            <h4 className="text-[11px] font-bold uppercase text-mint">
              Nội dung cảnh báo
            </h4>
            <p
              data-testid="alert-explanation"
              className="whitespace-pre-wrap break-words"
            >
              {detail.ai_explanation || "Chưa có nội dung cảnh báo được lưu"}
            </p>
          </section>
          <section
            className="space-y-3 border-t border-charcoal pt-4"
            data-testid="alert-factual-snapshot"
          >
            <h4 className="text-[11px] font-bold uppercase text-mint">
              Dữ kiện lúc phát hiện
            </h4>
            {snapshot ? (
              <>
                <div className="space-y-1 text-xs">
                  <p className="break-words">
                    Trạm: {snapshot.location_name || "Chưa có tên trạm"}
                    {snapshot.location_code
                      ? ` [${snapshot.location_code}]`
                      : ""}
                  </p>
                  <p className="text-sage">
                    Đánh giá: {formatAlertTime(snapshot.evaluated_at)}
                  </p>
                </div>
                {snapshot.violations.length === 0 && (
                  <p className="text-xs text-sage">
                    Không có dữ kiện vi phạm được lưu
                  </p>
                )}
                <ol className="space-y-3">
                  {snapshot.violations.map((violation, index) => (
                    <li
                      key={`${violation.violation_key}-${index}`}
                      className="space-y-2 border border-charcoal bg-gis-panel p-3 text-xs"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <h5 className="break-words font-bold">
                          {(violation.parameter &&
                            PARAMETER_LABELS.get(violation.parameter)) ||
                            violation.parameter ||
                            "Chưa có tham số"}
                        </h5>
                        <AlertSeverityBadge
                          severity={violation.severity_level ?? ""}
                        />
                      </div>
                      <p
                        className="break-words text-sm text-topo"
                        data-testid="alert-violation-values"
                      >
                        {violation.measured_value}{" "}
                        {violation.unit || "Chưa có đơn vị"}{" "}
                        {violation.operator || "Chưa có toán tử"}{" "}
                        {violation.threshold_value}{" "}
                        {violation.unit || "Chưa có đơn vị"}
                      </p>
                      <p className="text-sage">
                        Số đo · toán tử · ngưỡng tại thời điểm phát hiện
                      </p>
                      <p className="text-sage">
                        Thời điểm nguồn:{" "}
                        {formatAlertTime(violation.source_timestamp)}
                      </p>
                      {(violation.storm_name || violation.storm_id) && (
                        <p className="break-words">
                          Bão: {violation.storm_name || violation.storm_id}
                        </p>
                      )}
                      {violation.distance_km !== null && (
                        <p>Khoảng cách: {violation.distance_km} km</p>
                      )}
                    </li>
                  ))}
                </ol>
              </>
            ) : (
              <p className="text-xs text-sage">Không có dữ kiện gốc</p>
            )}
          </section>
          <details className="border-t border-charcoal pt-4 text-xs">
            <summary className="cursor-pointer py-2 text-sage focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint">
              Liên kết hiện tại (có thể đã thay đổi)
            </summary>
            <div className="space-y-2 pt-2">
              <p className="break-words">
                Trạm:{" "}
                {detail.location
                  ? `${detail.location.name || "Chưa có tên"} [${detail.location.code || "Chưa có mã"}]`
                  : "Không còn liên kết"}
              </p>
              <p className="break-words">
                Ngưỡng:{" "}
                {detail.threshold
                  ? `${detail.threshold.name || detail.threshold.parameter || "Chưa có tên"}: ${detail.threshold.operator || "Chưa có toán tử"} ${detail.threshold.threshold_value ?? "Chưa có giá trị ngưỡng"}`
                  : "Không còn liên kết"}
              </p>
              <p className="break-words">
                Bão:{" "}
                {detail.storm
                  ? detail.storm.name || detail.storm.id
                  : "Không còn liên kết"}
              </p>
              <p className="text-sage">
                Các liên kết hiện tại không thay thế dữ kiện lúc phát hiện ở
                trên.
              </p>
            </div>
          </details>
        </>
      )}
      <p className="border-t border-charcoal pt-4 text-[10px] text-sage">
        Nguồn dữ liệu hệ thống: OpenWeatherMap / GDACS. Cảnh báo mang tính tham
        khảo; theo dõi hướng dẫn của cơ quan chức năng.
      </p>
    </section>
  );
}
