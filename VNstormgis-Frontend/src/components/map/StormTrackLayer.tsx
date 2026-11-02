/**
 * @file StormTrackLayer.tsx
 * @description Lớp Quỹ đạo Bão (SR-STM-003) — đường đi lịch sử liền nét (#dde2e4), đoạn dự báo
 * đứt đoạn (#f59e0b, đỉnh nối backend gắn sẵn — W3-02 #4), vùng ảnh hưởng nguy hiểm polygon bán
 * trong suốt (CHỈ khi payload có feature kind=impact — không bao giờ buffer từ impact_radius_km),
 * và tooltip mốc theo ngưỡng zoom (hover mọi zoom, permanent khi zoom ≥ 8 — quyết định #6).
 * Component THUẦN RENDER — nhận tracks từ UnifiedAppLayout (sole owner, quy ước W2-02), không tự fetch.
 *
 * @author VNstormgis Team - Frontend & GIS Engineer
 * @version 1.0.0
 */

import React, { useMemo, useState } from "react";
import {
  CircleMarker,
  Polygon,
  Polyline,
  Tooltip,
  useMap,
  useMapEvents,
} from "react-leaflet";
import {
  StormTrackData,
  StormTrackPointDatum,
  TRACK_FORECAST_COLOR,
  TRACK_FORECAST_DASH_ARRAY,
  TRACK_HISTORY_COLOR,
  TRACK_IMPACT_COLOR,
  TRACK_IMPACT_FILL_OPACITY,
  TRACK_TOOLTIP_ZOOM_THRESHOLD,
  formatNullableMs,
  formatPressure,
  formatSyncTime,
} from "@/lib/stormUtils";

export interface StormTrackLayerProps {
  /** Quỹ đạo đã parse theo storm id — sole owner lo việc fetch (Mục 3.5). Bão vắng entry = không có quỹ đạo (lỗi/sinh trễ). */
  tracks: Record<string, StormTrackData>;
}

/** Nội dung tooltip mốc — 9 khóa rút gọn, null → "--" (SYS-RULE-004 / quyết định #9). */
const trackPointTooltip = (label: string, point: StormTrackPointDatum) => (
  <span className="font-mono">
    [{label} // {formatSyncTime(point.forecastTime)}]
    <br />
    GIÓ: {formatNullableMs(point.windSpeed)} · GIẬT:{" "}
    {formatNullableMs(point.windGust)}
    <br />
    ÁP SUẤT: {formatPressure(point.pressure)} · NGUỒN: GDACS
  </span>
);

export const StormTrackLayer: React.FC<StormTrackLayerProps> = ({ tracks }) => {
  const map = useMap();
  // Ngưỡng zoom điều khiển nhãn permanent (quyết định #6) — khởi tạo từ zoom thật, cập nhật qua zoomend.
  const [zoom, setZoom] = useState<number>(() => map.getZoom());
  useMapEvents({ zoomend: () => setZoom(map.getZoom()) });
  const permanentLabels = zoom >= TRACK_TOOLTIP_ZOOM_THRESHOLD;

  const entries = useMemo(() => Object.values(tracks), [tracks]);

  return (
    <>
      {entries.map((track) => (
        <StormTrack
          key={`track-${track.stormId}`}
          track={track}
          permanentLabels={permanentLabels}
        />
      ))}
    </>
  );
};

const StormTrack: React.FC<{
  track: StormTrackData;
  permanentLabels: boolean;
}> = ({ track, permanentLabels }) => {
  const historyCount = track.points.filter(
    (point) => point.trackType === "historical",
  ).length;
  const forecastCount = track.points.filter(
    (point) => point.trackType === "forecast",
  ).length;

  return (
    <>
      {/* Đường lịch sử — liền nét trắng xám San Rita (SR-STM-003, quyết định #3) */}
      {track.historyLatLngs.length >= 2 && (
        <Polyline
          positions={track.historyLatLngs}
          pathOptions={{
            color: TRACK_HISTORY_COLOR,
            weight: 2,
            opacity: 0.9,
          }}
        >
          <Tooltip
            className="san-rita-tooltip"
            sticky
            direction="top"
            offset={[0, -4]}
          >
            <span className="font-mono">
              [QUỸ ĐẠO LỊCH SỬ · {historyCount} MỐC]
            </span>
          </Tooltip>
        </Polyline>
      )}

      {/* Đoạn dự báo — đứt đoạn cam cảnh báo; đỉnh nối ĐÃ GẮN SẴN trong geometry (W3-02 #4) */}
      {track.forecastLatLngs.length >= 2 && (
        <Polyline
          positions={track.forecastLatLngs}
          pathOptions={{
            color: TRACK_FORECAST_COLOR,
            weight: 2,
            opacity: 0.95,
            dashArray: TRACK_FORECAST_DASH_ARRAY,
          }}
        >
          <Tooltip
            className="san-rita-tooltip"
            sticky
            direction="top"
            offset={[0, -4]}
          >
            <span className="font-mono">
              [DỰ BÁO DI CHUYỂN · {forecastCount} MỐC]
            </span>
          </Tooltip>
        </Polyline>
      )}

      {/* Vùng ảnh hưởng nguy hiểm — CHỈ khi payload có polygon thật (quyết định #5);
          KHÔNG render bất kỳ hình thay thế nào khi track.impactRings === null */}
      {track.impactRings && (
        <Polygon
          positions={track.impactRings}
          pathOptions={{
            color: TRACK_IMPACT_COLOR,
            weight: 1,
            fillColor: TRACK_IMPACT_COLOR,
            fillOpacity: TRACK_IMPACT_FILL_OPACITY,
          }}
        >
          <Tooltip className="san-rita-tooltip" sticky>
            <span className="font-mono">
              [VÙNG ẢNH HƯỞNG NGUY HIỂM]
              <br />
              BÁN KÍNH:{" "}
              {track.impactRadiusKm === null
                ? "--"
                : `${Math.round(track.impactRadiusKm)} km`}{" "}
              · NGUỒN: GDACS
            </span>
          </Tooltip>
        </Polygon>
      )}

      {/* Mốc quỹ đạo — CircleMarker nhỏ, màu theo nhánh; tooltip hover mọi zoom,
          permanent chỉ khi zoom ≥ 8. key đổi theo cờ để force remount Tooltip
          (đổi prop permanent sau mount KHÔNG tự rebind listener của Leaflet — Runbook 7.5). */}
      {track.points.map((point) => {
        const isForecast = point.trackType === "forecast";
        const label = isForecast ? "MỐC DỰ BÁO" : "MỐC LỊCH SỬ";
        const color = isForecast ? TRACK_FORECAST_COLOR : TRACK_HISTORY_COLOR;
        return (
          <CircleMarker
            key={`track-point-${point.trackId}`}
            center={point.latLng}
            radius={isForecast ? 3 : 2}
            pathOptions={{
              color,
              weight: 1,
              fillColor: color,
              fillOpacity: 0.9,
            }}
          >
            <Tooltip
              key={`tt-${permanentLabels}`} // force remount khi cờ đổi (Runbook 7.5)
              className="san-rita-tooltip"
              direction="top"
              offset={[0, -4]}
              permanent={permanentLabels}
            >
              {trackPointTooltip(label, point)}
            </Tooltip>
          </CircleMarker>
        );
      })}
    </>
  );
};

export default StormTrackLayer;
