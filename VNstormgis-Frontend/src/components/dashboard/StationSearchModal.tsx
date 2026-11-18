/**
 * @file StationSearchModal.tsx
 * @description Hộp thoại tìm kiếm nhanh Tỉnh/Thành phố & Trạm quan trắc (Ctrl+K / Cmd+K)
 * chuẩn thiết kế San Rita. Hỗ trợ tìm kiếm tiếng Việt có dấu lẫn không dấu, hiển thị
 * chỉ số thời tiết trực tiếp và điều hướng tức thì tới trạm, biểu đồ 24h hoặc đăng ký Email.
 */

import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  Search,
  X,
  MapPin,
  BarChart3,
  Mail,
  Wind,
  CloudRain,
  Thermometer,
} from "lucide-react";
import { StationMarkerData } from "@/types/map";
import { SEVERITY_CONFIG } from "@/components/map/markerFactory";

export interface StationSearchModalProps {
  isOpen: boolean;
  stations: StationMarkerData[];
  onClose: () => void;
  onSelectStation: (station: StationMarkerData) => void;
  onOpenHistory: (station: StationMarkerData) => void;
  onOpenSubscribe: (station: StationMarkerData) => void;
}

function normalizeVietnamese(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .toLowerCase()
    .trim();
}

export const StationSearchModal: React.FC<StationSearchModalProps> = ({
  isOpen,
  stations,
  onClose,
  onSelectStation,
  onOpenHistory,
  onOpenSubscribe,
}) => {
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setQuery("");
      setActiveIndex(0);
      const timer = setTimeout(() => inputRef.current?.focus(), 40);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [isOpen]);

  const filtered = useMemo(() => {
    const q = normalizeVietnamese(query);
    if (!q) return stations;
    return stations.filter((st) => {
      const haystack = normalizeVietnamese(
        `${st.name} ${st.code} ${st.region}`,
      );
      return haystack.includes(q);
    });
  }, [stations, query]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query]);

  useEffect(() => {
    if (!isOpen) return undefined;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onClose();
      } else if (e.key === "ArrowDown") {
        e.preventDefault();
        setActiveIndex((prev) =>
          filtered.length === 0 ? 0 : (prev + 1) % filtered.length,
        );
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setActiveIndex((prev) =>
          filtered.length === 0
            ? 0
            : (prev - 1 + filtered.length) % filtered.length,
        );
      } else if (e.key === "Enter" && filtered[activeIndex]) {
        e.preventDefault();
        onSelectStation(filtered[activeIndex]);
        onClose();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, filtered, activeIndex, onClose, onSelectStation]);

  if (!isOpen) return null;

  return (
    <div
      id="station-search-modal"
      data-testid="station-search-modal"
      className="fixed inset-0 z-[1300] bg-black/75 flex items-start justify-center pt-16 px-4 font-mono"
      onClick={onClose}
    >
      <div
        className="w-full max-w-xl bg-[#161b13] border border-[#e2ffcc] shadow-flat text-[#dde2e4]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Ô nhập tìm kiếm */}
        <div className="flex items-center gap-2.5 px-3.5 py-2.5 border-b border-[#2d3329] bg-[#1a2117]">
          <Search className="w-4 h-4 text-[#e2ffcc] shrink-0" />
          <input
            ref={inputRef}
            id="input-station-search"
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Nhập tên tỉnh/thành phố, mã trạm (VD: Đà Nẵng, da nang, HAN, Trường Sa)..."
            className="flex-1 bg-transparent text-xs text-[#dde2e4] placeholder-[#84907f] focus:outline-none"
          />
          <span className="text-[9px] px-1.5 py-0.5 border border-[#2d3329] text-[#84907f] hidden sm:inline">
            ESC ĐÓNG
          </span>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng tìm kiếm trạm"
            className="p-1 text-[#84907f] hover:text-[#e2ffcc]"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Thống kê kết quả */}
        <div className="px-3.5 py-1.5 bg-[#161b13] border-b border-[#2d3329] flex items-center justify-between text-[10px] text-[#84907f]">
          <span>
            HIỂN THỊ <strong className="text-[#e2ffcc]">{filtered.length}</strong>{" "}
            / {stations.length} TRẠM QUAN TRẮC
          </span>
          <span className="hidden sm:inline">
            ↑↓ DI CHUYỂN · ENTER BAY TỚI TRẠM
          </span>
        </div>

        {/* Danh sách kết quả */}
        <div className="max-h-[60vh] overflow-y-auto divide-y divide-[#2d3329]">
          {filtered.length === 0 ? (
            <div className="p-6 text-center text-xs text-[#84907f]">
              Không tìm thấy tỉnh/thành hoặc trạm đo khớp từ khóa &ldquo;{query}
              &rdquo;.
            </div>
          ) : (
            filtered.map((station, idx) => {
              const sev = SEVERITY_CONFIG[station.severity];
              const isSelected = idx === activeIndex;
              return (
                <div
                  key={station.id}
                  onMouseEnter={() => setActiveIndex(idx)}
                  className={`px-3.5 py-2.5 flex flex-col sm:flex-row sm:items-center justify-between gap-2 transition-colors ${
                    isSelected ? "bg-[#1a2117]" : "bg-[#161b13]"
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => {
                      onSelectStation(station);
                      onClose();
                    }}
                    className="flex-1 text-left flex flex-col gap-1"
                  >
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-xs font-bold text-[#e2ffcc]">
                        {station.name}
                      </span>
                      <span className="text-[10px] px-1.5 py-0.2 border border-[#2d3329] text-[#84907f]">
                        {station.code} · {station.region}
                      </span>
                      <span
                        className="text-[9px] px-1.5 py-0.2 border font-bold uppercase"
                        style={{
                          borderColor: sev.borderHex,
                          backgroundColor: sev.bgHex,
                          color: sev.textHex,
                        }}
                      >
                        {sev.name}
                      </span>
                    </div>

                    {station.weather ? (
                      <div className="flex items-center gap-3 text-[10px] text-[#84907f]">
                        <span className="flex items-center gap-1">
                          <Thermometer className="w-3 h-3 text-[#f59e0b]" />
                          <strong className="text-[#dde2e4]">
                            {station.weather.temperature ?? "--"}°C
                          </strong>
                        </span>
                        <span className="flex items-center gap-1">
                          <CloudRain className="w-3 h-3 text-[#38bdf8]" />
                          <strong className="text-[#dde2e4]">
                            {station.weather.rain_1h ?? 0} mm
                          </strong>
                        </span>
                        <span className="flex items-center gap-1">
                          <Wind className="w-3 h-3 text-[#e2ffcc]" />
                          <strong className="text-[#dde2e4]">
                            {station.weather.wind_speed ?? "--"} m/s
                          </strong>
                        </span>
                      </div>
                    ) : (
                      <span className="text-[10px] text-[#84907f]">
                        Chưa có bản ghi thời tiết mới
                      </span>
                    )}
                  </button>

                  {/* Cụm phím thao tác nhanh */}
                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      onClick={() => {
                        onSelectStation(station);
                        onClose();
                      }}
                      className="px-2 py-1 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#161b13] text-[10px] text-[#dde2e4] hover:text-[#e2ffcc] flex items-center gap-1 uppercase"
                      title="Bay tới vị trí trạm trên bản đồ"
                    >
                      <MapPin className="w-3 h-3" />
                      <span>ĐỊNH VỊ</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        onClose();
                        onOpenHistory(station);
                      }}
                      className="px-2 py-1 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#161b13] text-[10px] text-[#38bdf8] hover:text-[#e2ffcc] flex items-center gap-1 uppercase"
                      title="Xem biểu đồ lịch sử thời tiết 24h"
                    >
                      <BarChart3 className="w-3 h-3" />
                      <span>24H</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        onClose();
                        onOpenSubscribe(station);
                      }}
                      className="px-2 py-1 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#161b13] text-[10px] text-[#e2ffcc] flex items-center gap-1 uppercase"
                      title="Đăng ký nhận cảnh báo qua Email cho trạm này"
                    >
                      <Mail className="w-3 h-3" />
                      <span>EMAIL</span>
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};

export default StationSearchModal;
