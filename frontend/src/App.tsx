import { useState, useEffect } from "react";
import {
  CloudLightning,
  Database,
  Radio,
  Server,
  Layers,
  RefreshCw,
  ExternalLink,
  MapPin,
} from "lucide-react";
import { StatusBadge, StatusType } from "@/components/common/StatusBadge";
import { locationService } from "@/services/locationService";
import { LocationItem } from "@/types/location";

export function App() {
  const [backendStatus, setBackendStatus] = useState<StatusType>("loading");
  const [stationCount, setStationCount] = useState<number | null>(null);
  const [sampleStations, setSampleStations] = useState<LocationItem[]>([]);
  const [lastChecked, setLastChecked] = useState<string>("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  const verifyConnectivity = async () => {
    setIsRefreshing(true);
    setErrorMessage(null);
    try {
      // 1. Kiểm tra Actuator Health qua Vite Reverse Proxy
      await locationService.checkBackendHealth();

      // 2. Thử nghiệm lấy 68 trạm quan trắc từ PostgreSQL/PostGIS
      const locations = await locationService.getAllLocations();
      setStationCount(locations.length);
      setSampleStations(locations.slice(0, 5));
      setBackendStatus("online");
      setLastChecked(new Date().toLocaleTimeString("vi-VN"));
    } catch (err: unknown) {
      setBackendStatus("error");
      const msg = err instanceof Error ? err.message : "Không rõ lỗi";
      setErrorMessage(
        `Lỗi proxy kết nối Backend: ${msg}. Đảm bảo container Docker và Backend Spring Boot đang chạy!`,
      );
      setLastChecked(new Date().toLocaleTimeString("vi-VN"));
    } finally {
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    verifyConnectivity();
  }, []);

  return (
    <div className="flex flex-col h-screen w-screen bg-gis-bg text-slate-100 select-none">
      {/* 1. Header Thanh Điều Hướng Trên Cùng */}
      <header className="h-16 px-6 bg-gis-panel border-b border-gis-border flex items-center justify-between z-20">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-gradient-to-tr from-sky-500 to-indigo-600 text-white shadow-lg shadow-sky-500/20">
            <CloudLightning className="w-6 h-6 animate-pulse" />
          </div>
          <div>
            <h1 className="text-base font-bold tracking-tight text-white flex items-center gap-2">
              VNstormgis
              <span className="text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-sky-500/20 text-sky-400 border border-sky-500/30">
                Sprint 1 Foundation
              </span>
            </h1>
            <p className="text-xs text-slate-400">
              Hệ Thống Giám Sát Mưa Gió Bão Tích Hợp AI Agent Cảnh Báo
            </p>
          </div>
        </div>

        {/* Cụm Huy Hiệu Trạng Thái */}
        <div className="flex items-center gap-3">
          <StatusBadge
            status={backendStatus}
            label={backendStatus === "online" ? "Spring Boot 3.3 Active" : "Backend Disconnected"}
            subLabel={stationCount ? `${stationCount} Trạm PostGIS` : undefined}
          />
          <button
            onClick={verifyConnectivity}
            disabled={isRefreshing}
            className="p-2 rounded-lg bg-slate-800 hover:bg-gis-hover text-slate-300 hover:text-white border border-gis-border transition-all disabled:opacity-50"
            title="Làm mới kiểm tra kết nối"
          >
            <RefreshCw className={`w-4 h-4 ${isRefreshing ? "animate-spin text-sky-400" : ""}`} />
          </button>
        </div>
      </header>

      {/* 2. Thân Ứng Dụng: Bảng Điều Khiển Kiểm Định Bootstrap (Verification Dashboard) */}
      <main className="flex-1 p-6 overflow-y-auto">
        <div className="max-w-6xl mx-auto space-y-6">
          {/* Thông Báo Trạng Thái */}
          {errorMessage && (
            <div className="p-4 rounded-xl bg-rose-950/50 border border-rose-500/50 text-rose-200 text-sm flex items-start gap-3">
              <span className="font-semibold text-rose-400">Cảnh báo:</span>
              <div className="flex-1">{errorMessage}</div>
            </div>
          )}

          {/* Ma Trận Kiểm Định Khởi Tạo Công Nghệ */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Card 1: Khung Ứng Dụng Frontend */}
            <div className="p-5 rounded-2xl bg-gis-panel border border-gis-border space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase text-sky-400 tracking-wider">
                  Frontend Core
                </span>
                <Radio className="w-4 h-4 text-sky-400" />
              </div>
              <div className="text-lg font-bold text-white">React 18 + Vite 5 + TS 5</div>
              <p className="text-xs text-slate-400 leading-relaxed">
                Đã nạp thành công bộ khung TypeScript nghiêm ngặt, Hot Module Replacement (HMR) cực
                nhanh, và Path Alias <code className="text-sky-300 font-mono">@/*</code>.
              </p>
              <div className="pt-2 flex items-center gap-2 text-[11px] text-emerald-400 font-mono">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Vite Dev Server :5173 OK
              </div>
            </div>

            {/* Card 2: Reverse Proxy & CORS */}
            <div className="p-5 rounded-2xl bg-gis-panel border border-gis-border space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase text-amber-400 tracking-wider">
                  Proxy & CORS
                </span>
                <Server className="w-4 h-4 text-amber-400" />
              </div>
              <div className="text-lg font-bold text-white">Reverse Proxy (/api ➔ :8080)</div>
              <p className="text-xs text-slate-400 leading-relaxed">
                Hóa giải rủi ro RSK-W1-04 bằng cơ chế forward nội bộ, ngăn chặn lỗi trình duyệt chặn
                CORS khi gọi sang Spring Boot Backend.
              </p>
              <div className="pt-2 flex items-center gap-2 text-[11px] text-emerald-400 font-mono">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Proxy HTTP Loopback Active
              </div>
            </div>

            {/* Card 3: Design System & Styling */}
            <div className="p-5 rounded-2xl bg-gis-panel border border-gis-border space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase text-indigo-400 tracking-wider">
                  Design System
                </span>
                <Layers className="w-4 h-4 text-indigo-400" />
              </div>
              <div className="text-lg font-bold text-white">Tailwind CSS 3.4 & Lucide</div>
              <p className="text-xs text-slate-400 leading-relaxed">
                Tích hợp sẵn bảng màu thời tiết đa cấp độ (
                <span className="text-weather-safe">Safe</span>,{" "}
                <span className="text-weather-warning">Warning</span>,{" "}
                <span className="text-weather-extreme">Extreme</span>) và Lucide Icons.
              </p>
              <div className="pt-2 flex items-center gap-2 text-[11px] text-emerald-400 font-mono">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                Tailwind JIT Engine OK
              </div>
            </div>
          </div>

          {/* Khu Vực Kiểm Tra Dữ Liệu Thực Tế Từ PostGIS */}
          <div className="p-6 rounded-2xl bg-gis-panel border border-gis-border space-y-4">
            <div className="flex items-center justify-between border-b border-gis-border pb-4">
              <div className="flex items-center gap-2">
                <Database className="w-5 h-5 text-sky-400" />
                <h2 className="text-sm font-bold text-white uppercase tracking-wider">
                  Dữ Liệu Trắc Địa PostGIS Nạp Qua API (
                  {stationCount !== null ? `${stationCount} Trạm` : "Đang tải..."})
                </h2>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-xs text-slate-400">
                  Kiểm tra lúc: {lastChecked || "Chưa kiểm tra"}
                </span>
                <a
                  href="/swagger-ui.html"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 text-xs text-sky-400 hover:text-sky-300 transition-colors"
                >
                  Swagger UI <ExternalLink className="w-3.5 h-3.5" />
                </a>
              </div>
            </div>

            {sampleStations.length > 0 ? (
              <div className="space-y-3">
                <p className="text-xs text-slate-400">
                  Dưới đây là 5 trạm quan trắc tiêu biểu phản hồi từ Backend qua endpoint{" "}
                  <code className="text-sky-300 font-mono">GET /api/v1/locations</code>:
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
                  {sampleStations.map((station) => (
                    <div
                      key={station.id}
                      className="p-3.5 rounded-xl bg-slate-900/60 border border-slate-700/60 hover:border-sky-500/40 transition-all space-y-2"
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-mono font-bold text-sky-400">
                          {station.code}
                        </span>
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-300">
                          {station.region}
                        </span>
                      </div>
                      <div className="text-sm font-semibold text-white truncate">
                        {station.name}
                      </div>
                      <div className="text-[11px] text-slate-400 flex items-center gap-1">
                        <MapPin className="w-3 h-3 text-slate-500" />
                        <span>
                          {station.latitude.toFixed(4)}, {station.longitude.toFixed(4)}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="py-8 text-center text-slate-400 text-xs">
                {backendStatus === "loading"
                  ? "Đang tiến hành kết nối kiểm tra dữ liệu qua Reverse Proxy..."
                  : "Chưa thể kết nối tới Backend. Hãy khởi động Spring Boot và thử lại!"}
              </div>
            )}
          </div>

          {/* Vùng Placeholder Sẵn Sàng Cho Task W1-11 & W1-12 */}
          <div className="p-8 rounded-2xl border-2 border-dashed border-slate-700/70 bg-slate-900/30 text-center space-y-3">
            <div className="inline-flex p-3 rounded-full bg-slate-800 text-slate-400 mb-1">
              <Layers className="w-6 h-6" />
            </div>
            <h3 className="text-sm font-bold text-slate-200">
              Khu Vực Bản Đồ Nền Leaflet Việt Nam (BaseMap Container Placeholder)
            </h3>
            <p className="text-xs text-slate-400 max-w-lg mx-auto leading-relaxed">
              Vùng hiển thị này sẽ được tích hợp component{" "}
              <code className="text-sky-300 font-mono">BaseMap.tsx</code> tại{" "}
              <strong>Task W1-11 (Leaflet 1.9 & Fix Icon Bug)</strong> và{" "}
              <strong>Task W1-12 (Center VN, Zoom 6, App Layout)</strong>.
            </p>
          </div>
        </div>
      </main>

      {/* 3. Footer Trạng Thái Kỹ Thuật */}
      <footer className="h-9 px-6 bg-gis-panel border-t border-gis-border flex items-center justify-between text-[11px] text-slate-400">
        <div>VNstormgis © 2026 - Nền tảng Giám Sát Thời Tiết Thông Minh</div>
        <div className="flex items-center gap-4">
          <span>React 18.3</span>
          <span>•</span>
          <span>Vite 5.3</span>
          <span>•</span>
          <span>Tailwind 3.4</span>
          <span>•</span>
          <span>Leaflet 1.9 Ready</span>
        </div>
      </footer>
    </div>
  );
}

export default App;
