/**
 * @file ActiveStormLayer.tsx
 * @description Lớp Bão Active (SR-STM-002) — một tâm bão xoáy SVG tại geom_current cho MỖI
 * cơn bão status=active (SYS-RULE-001, SR-STM-003). Popup San Rita theo AC-STM-002;
 * áp suất null hiển thị "--" (SYS-RULE-004); empty state do BaseMap render từ STORM_EMPTY_MESSAGE.
 * Component THUẦN RENDER — nhận storms từ UnifiedAppLayout (sole owner, quy ước W2-02), không tự fetch.
 *
 * @author VNstormgis Team - Frontend Engineer
 * @version 1.0.0
 */

import React, { useEffect, useMemo, useState } from "react";
import { Marker, Popup, useMap } from "react-leaflet";
import {
  StormCenter,
  formatKmh,
  formatMs,
  formatPressure,
  formatSyncTime,
} from "@/lib/stormUtils";
import {
  STORM_RISK_CONFIG,
  StormRiskLevel,
  createStormCenterIcon,
  getStormRiskLevel,
} from "./markerFactory";
import { STORM_WATCH_BBOX_LABEL, isWithinStormWatchBox } from "@/types/storm";
import { stormService } from "@/services/stormService";
import {
  WindBeaufortBand,
  WIND_BEAUFORT_CONFIG,
  getWindBeaufortBand,
} from "@/lib/windUtils";
import { formatCoordinate } from "@/lib/geoUtils";

export interface ActiveStormLayerProps {
  /** Tâm bão đã tách tọa độ (dẫn xuất từ FeatureCollection active — sole owner lo việc fetch). */
  storms: StormCenter[];
  /** Callback chọn bão (W3-06 dùng để fly tới quỹ đạo; W3-07 legend có thể highlight). */
  onStormSelect?: (storm: StormCenter) => void;
}

export const ActiveStormLayer: React.FC<ActiveStormLayerProps> = ({
  storms,
  onStormSelect,
}) => (
  <>
    {storms.map((storm) => (
      <StormMarker
        key={`storm-${storm.id}`}
        storm={storm}
        onStormSelect={onStormSelect}
      />
    ))}
  </>
);

const StormMarker: React.FC<{
  storm: StormCenter;
  onStormSelect?: (storm: StormCenter) => void;
}> = ({ storm, onStormSelect }) => {
  const level = getStormRiskLevel(storm.properties.category);
  const risk = STORM_RISK_CONFIG[level];
  const band =
    WIND_BEAUFORT_CONFIG[getWindBeaufortBand(storm.properties.max_wind_speed)];
  const inWatchBox = isWithinStormWatchBox(storm.latLng[0], storm.latLng[1]);
  // Icon phải ổn định giữa các render — DivIcon mới mỗi lần render khiến react-leaflet
  // gọi setIcon lặp ("Maximum update depth exceeded").
  const icon = useMemo(
    () => createStormCenterIcon(level, storm.label),
    [level, storm.label],
  );

  return (
    <Marker
      position={storm.latLng}
      icon={icon}
      // Tâm bão phải nằm trên icon trạm + mũi tên gió (RSK-W3-05) — WindLayer dùng 1000.
      zIndexOffset={2000}
      eventHandlers={{ click: () => onStormSelect?.(storm) }}
    >
      {/* Popup con tự giữ state chi tiết: Marker không re-render khi chi tiết đổi,
          tránh react-leaflet gọi instance.update() lặp khi popup đang mở.
          autoPan TẮT: bão ngoài maxBounds không thể đưa vào khung nhìn — autoPan sẽ
          pan lặp vô hạn (moveend → BaseMap re-render → update → pan) và làm treo UI. */}
      <Popup className="san-rita-popup" maxWidth={340} autoPan={false}>
        <StormPopupContent
          storm={storm}
          risk={risk}
          band={band}
          inWatchBox={inWatchBox}
        />
      </Popup>
    </Marker>
  );
};

interface DetailState {
  loading: boolean;
  error: string | null;
  text?: string;
}

interface StormPopupContentProps {
  storm: StormCenter;
  risk: (typeof STORM_RISK_CONFIG)[StormRiskLevel];
  band: (typeof WIND_BEAUFORT_CONFIG)[WindBeaufortBand];
  inWatchBox: boolean;
}

const StormPopupContent: React.FC<StormPopupContentProps> = ({
  storm,
  risk,
  band,
  inWatchBox,
}) => {
  const map = useMap();
  const [detail, setDetail] = useState<DetailState>({
    loading: false,
    error: null,
  });

  useEffect(() => setDetail({ loading: false, error: null }), [storm.id]);

  const focusCenter = () => {
    // maxBounds có thể kẹp tâm với bão ngoài hộp Việt Nam (Mục 2 #10) — ghi nhận hành vi thật.
    map.flyTo(storm.latLng, Math.max(map.getZoom(), 6), { duration: 1.2 });
  };

  const loadDetail = async () => {
    setDetail({ loading: true, error: null });
    try {
      const stormDetail = await stormService.getStormDetail(storm.id);
      setDetail({
        loading: false,
        error: null,
        text:
          `Quỹ đạo ${stormDetail.totalTrackPoints} mốc · hình thành ${formatSyncTime(stormDetail.startDate)}` +
          (stormDetail.endDate
            ? ` · tan ${formatSyncTime(stormDetail.endDate)}`
            : ""),
      });
    } catch {
      // 404/400/500 đều là RFC 7807 — interceptor đã log; UI báo lỗi trung thực, không bịa số.
      setDetail({
        loading: false,
        error: "KHÔNG LOAD ĐƯỢC CHI TIẾT (kiểm tra console / RFC 7807)",
      });
    }
  };

  return (
    <div className="p-3 bg-[#161b13] text-[#dde2e4] space-y-2 select-none min-w-[280px]">
      {/* Header [MÃ BÃO // TÊN BÃO] + badge cấp */}
      <div className="flex items-start justify-between gap-2 border-b border-[#2d3329] pb-1.5">
        <div className="font-bold text-[#e2ffcc] text-xs uppercase leading-tight">
          [{storm.id} // {storm.properties.name}]
        </div>
        <span
          className="text-[9px] px-1.5 py-0.5 border shrink-0 font-bold uppercase"
          style={{
            borderColor: risk.colorHex,
            color: risk.colorHex,
            backgroundColor: "#1a2117",
          }}
        >
          {risk.label}
        </span>
      </div>

      {/* AC-STM-002: gió km/h + m/s, áp suất hPa, cấp, trạng thái, giờ cập nhật */}
      <div className="grid grid-cols-2 gap-1.5 text-[10px]">
        <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
          <span className="text-[8px] text-[#84907f] block uppercase">
            SỨC GIÓ MAX
          </span>
          <span className="font-bold text-[#ea580c]">
            {formatKmh(storm.properties.max_wind_speed_kmh)}
          </span>
          <span className="text-[8px] text-[#84907f] block">
            {formatMs(storm.properties.max_wind_speed)}
          </span>
        </div>
        <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
          <span className="text-[8px] text-[#84907f] block uppercase">
            ÁP SUẤT TÂM
          </span>
          <span className="font-bold text-[#dde2e4]">
            {formatPressure(storm.properties.min_pressure)}
          </span>
          <span className="text-[8px] text-[#84907f] block">
            GDACS {storm.properties.category}
          </span>
        </div>
        <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
          <span className="text-[8px] text-[#84907f] block uppercase">
            BẬC GIÓ (BEAUFORT)
          </span>
          <span className="font-bold" style={{ color: band.color }}>
            {band.label}
          </span>
        </div>
        <div className="p-1.5 border border-[#2d3329] bg-[#1a2117]">
          <span className="text-[8px] text-[#84907f] block uppercase">
            TRẠNG THÁI
          </span>
          <span className="font-bold text-[#dde2e4] uppercase">
            {storm.properties.status}
          </span>
          {storm.properties.year && (
            <span className="text-[8px] text-[#84907f] block">
              MÙA {storm.properties.year}
            </span>
          )}
        </div>
      </div>

      {!inWatchBox && (
        <div className="text-[9px] text-[#f59e0b] border border-[#f59e0b] bg-[#1a2117] p-1.5 uppercase">
          Tâm bão NGOÀI hộp theo dõi Biển Đông ({STORM_WATCH_BBOX_LABEL}) — vẫn
          giữ hiển thị (quyết định #4)
        </div>
      )}

      <div className="pt-1 border-t border-[#2d3329] flex justify-between text-[9px] text-[#84907f]">
        <span>TỌA ĐỘ WGS84:</span>
        <span className="text-[#e2ffcc]">
          {formatCoordinate(storm.latLng, 4)}
        </span>
      </div>
      <div className="flex justify-between text-[9px] text-[#84907f]">
        <span>
          CẬP NHẬT:{" "}
          <strong className="text-[#e2ffcc]">
            {formatSyncTime(storm.properties.updated_at)}
          </strong>
        </span>
        <span>
          NGUỒN: <strong className="text-[#dde2e4]">GDACS</strong>
        </span>
      </div>

      {/* Nút thao tác — pattern popup trạm W2-08 */}
      <div className="pt-2 border-t border-[#2d3329] grid grid-cols-2 gap-1.5">
        <button
          id={`storm-focus-${storm.id}`}
          onClick={(event) => {
            event.stopPropagation();
            focusCenter();
          }}
          className="py-1 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#1a2117] text-[9px] uppercase font-bold"
        >
          🎯 TÂM BÃO
        </button>
        <button
          id={`storm-detail-${storm.id}`}
          onClick={(event) => {
            event.stopPropagation();
            void loadDetail();
          }}
          disabled={detail.loading}
          className="py-1 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#1a2117] text-[9px] text-[#e2ffcc] uppercase font-bold disabled:opacity-50"
        >
          {detail.loading ? "ĐANG LOAD…" : "📡 CHI TIẾT"}
        </button>
      </div>
      {(detail.text || detail.error) && (
        <div
          className={`text-[9px] p-1.5 border ${
            detail.error
              ? "border-[#dc2626] text-[#dc2626]"
              : "border-[#2d3329] text-[#84907f]"
          }`}
        >
          {detail.error ?? detail.text}
        </div>
      )}
    </div>
  );
};

export default ActiveStormLayer;
