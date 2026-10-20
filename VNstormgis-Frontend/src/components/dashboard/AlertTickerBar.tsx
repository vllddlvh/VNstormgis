/**
 * @file AlertTickerBar.tsx
 * @description Thanh băng chuyền tin tức cảnh báo khẩn cấp nổi (Floating Alert Ticker Bar) chuẩn San Rita.
 * Hiển thị các bản tin thiên tai, bão lũ thời gian thực từ Trung tâm KTTV Quốc gia.
 * Hỗ trợ tương tác trực tiếp: click vào bản tin cảnh báo để tự động điều hướng (flyTo) bản đồ đến vị trí trạm.
 *
 * @author VNstormgis Team - Frontend & Design Specialist
 * @version 1.0.0
 */

import React, { useState, useEffect } from "react";
import { AlertTriangle, ChevronLeft, ChevronRight, Navigation } from "lucide-react";
import { WeatherAlertItem } from "@/types/map";
import { SEVERITY_CONFIG } from "@/components/map/markerFactory";
import { SAMPLE_WEATHER_ALERTS } from "@/data/mockMapData";

export interface AlertTickerBarProps {
  /** Danh sách các bản tin cảnh báo thiên tai */
  alerts?: WeatherAlertItem[];
  /** Callback khi người dùng click vào bản tin để bay đến vị trí */
  onSelectAlert: (alert: WeatherAlertItem) => void;
}

export const AlertTickerBar: React.FC<AlertTickerBarProps> = ({
  alerts = SAMPLE_WEATHER_ALERTS,
  onSelectAlert,
}) => {
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const [isPaused, setIsPaused] = useState<boolean>(false);

  // Tự động chuyển bản tin sau mỗi 7 giây nếu không hover
  useEffect(() => {
    if (isPaused || alerts.length <= 1) return;

    const interval = setInterval(() => {
      setCurrentIndex((prev) => (prev + 1) % alerts.length);
    }, 7000);

    return () => clearInterval(interval);
  }, [isPaused, alerts.length]);

  if (!alerts || alerts.length === 0) return null;

  const currentAlert = alerts[currentIndex];
  const severityInfo = SEVERITY_CONFIG[currentAlert.severity];

  const handlePrev = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCurrentIndex((prev) => (prev - 1 + alerts.length) % alerts.length);
  };

  const handleNext = (e: React.MouseEvent) => {
    e.stopPropagation();
    setCurrentIndex((prev) => (prev + 1) % alerts.length);
  };

  return (
    <div
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      className="absolute bottom-4 left-4 right-20 md:right-auto md:max-w-2xl z-[1000] font-mono select-none"
    >
      <div className="bg-[#161b13] border border-[#2d3329] shadow-flat flex items-stretch">
        {/* Khối Nhãn Cảnh Báo Khẩn */}
        <div
          className="px-3 py-2 flex items-center gap-1.5 border-r border-[#2d3329]"
          style={{ backgroundColor: `${severityInfo.bgHex}15` }}
        >
          <AlertTriangle className="w-4 h-4 animate-pulse" style={{ color: severityInfo.bgHex }} />
          <span
            className="text-[10px] font-bold uppercase tracking-wider hidden sm:inline"
            style={{ color: severityInfo.bgHex }}
          >
            {severityInfo.name}
          </span>
        </div>

        {/* Khối Nội Dung Bản Tin */}
        <div
          onClick={() => onSelectAlert(currentAlert)}
          className="flex-1 px-3 py-1.5 flex flex-col justify-center cursor-pointer hover:bg-[#1a2117] transition-colors overflow-hidden"
          title="Click để định vị bản đồ đến tâm điểm trạm cảnh báo"
        >
          <div className="flex items-center gap-2 text-[10px]">
            <span className="text-[#e2ffcc] font-bold">[{currentAlert.code}]</span>
            <span className="text-[#84907f] uppercase">{currentAlert.stationName}</span>
            <span className="text-[#2d3329]">•</span>
            <span className="text-[#84907f] text-[9px]">{currentAlert.time}</span>
          </div>
          <div className="text-xs text-[#dde2e4] font-medium truncate tracking-tight">
            {currentAlert.headline}
          </div>
        </div>

        {/* Khối Nút Điều Hướng FlyTo & Next/Prev */}
        <div className="flex items-center border-l border-[#2d3329] bg-[#1a2117]">
          {/* Nút FlyTo */}
          <button
            id="btn-alert-flyto"
            onClick={() => onSelectAlert(currentAlert)}
            className="h-full px-2.5 text-[9px] uppercase font-bold text-[#e2ffcc] hover:bg-[#242c20] border-r border-[#2d3329] flex items-center gap-1 transition-colors"
            title="Định vị đến vị trí trạm này"
          >
            <Navigation className="w-3 h-3 text-[#e2ffcc]" />
            <span className="hidden lg:inline">XEM VỊ TRÍ</span>
          </button>

          {/* Bộ điều khiển chuyển bản tin */}
          <div className="flex items-center">
            <button
              id="btn-alert-prev"
              onClick={handlePrev}
              className="p-1.5 text-[#84907f] hover:text-[#e2ffcc] transition-colors"
              title="Bản tin trước"
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            <span className="text-[9px] text-[#84907f] px-1">
              {currentIndex + 1}/{alerts.length}
            </span>
            <button
              id="btn-alert-next"
              onClick={handleNext}
              className="p-1.5 text-[#84907f] hover:text-[#e2ffcc] transition-colors"
              title="Bản tin kế tiếp"
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AlertTickerBar;
