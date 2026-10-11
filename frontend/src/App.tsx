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
  Compass,
} from "lucide-react";
import { StatusBadge, StatusType } from "@/components/common/StatusBadge";
import { locationService } from "@/services/locationService";
import { LocationItem } from "@/types/location";
import { MarkerVerificationMap } from "@/components/map/MarkerVerificationMap";

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
    <div className="flex flex-col h-screen w-screen bg-[#161b13] text-[#dde2e4] select-none font-mono">
      {/* 1. Header Thanh Điều Hướng Chuẩn San Rita */}
      <header className="h-16 px-6 bg-[#161b13] border-b border-[#2d3329] flex items-center justify-between z-20">
        <div className="flex items-center gap-3">
          <div className="p-2 border border-[#2d3329] bg-[#1a2117] text-[#e2ffcc]">
            <CloudLightning className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-display text-2xl uppercase tracking-wider text-[#e2ffcc] leading-none">
                VNstormgis
              </span>
              <span className="font-mono text-[9px] uppercase px-1.5 py-0.5 border border-[#2d3329] bg-[#1a2117] text-[#84907f]">
                Sprint 1 Foundation
              </span>
            </div>
            <p className="font-mono text-[10px] text-[#84907f] uppercase tracking-tight mt-0.5">
              Hệ Thống Giám Sát WebGIS Khí Tượng & Bão Lũ Việt Nam
            </p>
          </div>
        </div>

        {/* Cụm Nút Điều Khiển & Huy Hiệu Trạng Thái */}
        <div className="flex items-center gap-3">
          <StatusBadge
            status={backendStatus}
            label={backendStatus === "online" ? "SPRING BOOT 3.3 ACTIVE" : "BACKEND DISCONNECTED"}
            subLabel={stationCount ? `${stationCount} TRẠM POSTGIS` : undefined}
          />
          <button
            onClick={verifyConnectivity}
            disabled={isRefreshing}
            className="p-1.5 border border-[#2d3329] bg-[#1a2117] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors disabled:opacity-50"
            title="Làm mới kiểm tra kết nối"
          >
            <RefreshCw
              className={`w-3.5 h-3.5 ${isRefreshing ? "animate-spin text-[#e2ffcc]" : ""}`}
            />
          </button>
        </div>
      </header>

      {/* 2. Thân Ứng Dụng: Bảng Điều Khiển San Rita Alpine Field Guide */}
      <main className="flex-1 p-6 overflow-y-auto">
        <div className="max-w-6xl mx-auto space-y-6">
          {/* Thông Báo Cảnh Báo Nếu Có */}
          {errorMessage && (
            <div className="p-3 border border-[#dc2626] bg-[#161b13] text-[#dc2626] text-xs flex items-start gap-2">
              <span className="font-bold uppercase tracking-wider">[CẢNH BÁO]:</span>
              <div className="flex-1">{errorMessage}</div>
            </div>
          )}

          {/* Ma Trận Khởi Tạo Công Nghệ Chuẩn San Rita Tokens */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Card 1: Frontend Core */}
            <div className="p-4 border border-[#2d3329] bg-[#1a2117] space-y-2.5">
              <div className="flex items-center justify-between text-[10px] text-[#84907f] uppercase">
                <span>[01 // FRONTEND CORE]</span>
                <Radio className="w-3.5 h-3.5 text-[#e2ffcc]" />
              </div>
              <div className="font-display text-xl uppercase tracking-wide text-[#dde2e4]">
                REACT 18 + VITE 5 + TS 5
              </div>
              <p className="text-[11px] text-[#84907f] leading-relaxed">
                Đã nạp thành công bộ khung TypeScript nghiêm ngặt, Hot Module Replacement (HMR) cực
                nhanh, và Path Alias <code className="text-[#e2ffcc]">@/*</code>.
              </p>
              <div className="pt-1 flex items-center gap-2 text-[10px] text-[#e2ffcc] uppercase">
                <span className="w-1.5 h-1.5 bg-[#e2ffcc]" />
                VITE DEV SERVER :5173 // OK
              </div>
            </div>

            {/* Card 2: Reverse Proxy & CORS */}
            <div className="p-4 border border-[#2d3329] bg-[#1a2117] space-y-2.5">
              <div className="flex items-center justify-between text-[10px] text-[#84907f] uppercase">
                <span>[02 // PROXY & CORS]</span>
                <Server className="w-3.5 h-3.5 text-[#f59e0b]" />
              </div>
              <div className="font-display text-xl uppercase tracking-wide text-[#dde2e4]">
                REVERSE PROXY (/api ➔ :8080)
              </div>
              <p className="text-[11px] text-[#84907f] leading-relaxed">
                Hóa giải rủi ro RSK-W1-04 bằng cơ chế loopback nội bộ, ngăn chặn lỗi trình duyệt
                chặn CORS khi gọi sang Spring Boot Backend.
              </p>
              <div className="pt-1 flex items-center gap-2 text-[10px] text-[#e2ffcc] uppercase">
                <span className="w-1.5 h-1.5 bg-[#e2ffcc]" />
                PROXY HTTP LOOPBACK // ACTIVE
              </div>
            </div>

            {/* Card 3: Design System & Styling */}
            <div className="p-4 border border-[#2d3329] bg-[#1a2117] space-y-2.5">
              <div className="flex items-center justify-between text-[10px] text-[#84907f] uppercase">
                <span>[03 // SAN RITA DESIGN]</span>
                <Layers className="w-3.5 h-3.5 text-[#e2ffcc]" />
              </div>
              <div className="font-display text-xl uppercase tracking-wide text-[#dde2e4]">
                ALPINE FIELD GUIDE TOKENS
              </div>
              <p className="text-[11px] text-[#84907f] leading-relaxed">
                Bảng màu tương phản Highlighter Mint, Sage Stone, Carbon Ink. Góc nhọn 0px,
                typography utilitarian mono và không bóng mờ.
              </p>
              <div className="pt-1 flex items-center gap-2 text-[10px] text-[#e2ffcc] uppercase">
                <span className="w-1.5 h-1.5 bg-[#e2ffcc]" />
                SAN RITA DESIGN SYSTEM // ACTIVE
              </div>
            </div>
          </div>

          {/* Khu Vực Kiểm Tra Dữ Liệu Thực Tế Từ PostGIS */}
          <div className="p-4 border border-[#2d3329] bg-[#1a2117] space-y-3">
            <div className="flex items-center justify-between border-b border-[#2d3329] pb-3">
              <div className="flex items-center gap-2">
                <Database className="w-4 h-4 text-[#e2ffcc]" />
                <h2 className="text-xs font-bold text-[#dde2e4] uppercase tracking-wider">
                  DỮ LIỆU TRẮC ĐỊA POSTGIS QUA API (
                  {stationCount !== null ? `${stationCount} TRẠM` : "ĐANG TẢI..."})
                </h2>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-[10px] text-[#84907f]">
                  CẬP NHẬT: {lastChecked || "CHƯA KIỂM TRA"}
                </span>
                <a
                  href="/swagger-ui.html"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1.5 text-[10px] text-[#84907f] hover:text-[#e2ffcc] border border-[#2d3329] hover:border-[#e2ffcc] px-2 py-0.5 uppercase transition-colors"
                >
                  SWAGGER UI <ExternalLink className="w-3 h-3" />
                </a>
              </div>
            </div>

            {sampleStations.length > 0 ? (
              <div className="space-y-2.5">
                <p className="text-[11px] text-[#84907f]">
                  5 trạm quan trắc tiêu biểu phản hồi từ Backend qua endpoint{" "}
                  <code className="text-[#e2ffcc]">GET /api/v1/locations</code>:
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2.5">
                  {sampleStations.map((station) => (
                    <div
                      key={station.id}
                      className="p-3 bg-[#161b13] border border-[#2d3329] hover:border-[#e2ffcc] transition-colors space-y-1.5"
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-[#e2ffcc]">{station.code}</span>
                        <span className="text-[9px] px-1 py-0.2 border border-[#2d3329] text-[#84907f] uppercase">
                          {station.region}
                        </span>
                      </div>
                      <div className="text-xs font-medium text-[#dde2e4] truncate">
                        {station.name}
                      </div>
                      <div className="text-[10px] text-[#84907f] flex items-center gap-1">
                        <MapPin className="w-3 h-3 text-[#84907f]" />
                        <span>
                          {station.latitude.toFixed(4)}, {station.longitude.toFixed(4)}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="py-6 text-center text-[#84907f] text-xs">
                {backendStatus === "loading"
                  ? "Đang kết nối kiểm tra dữ liệu qua Reverse Proxy..."
                  : "Chưa thể kết nối tới Backend. Hãy khởi động Spring Boot và thử lại!"}
              </div>
            )}
          </div>

          {/* Component Kiểm Định Bản Đồ & Markers Chuẩn San Rita (W1-11) */}
          <section className="space-y-2">
            <div className="flex items-center justify-between border-b border-[#2d3329] pb-2">
              <div className="flex items-center space-x-2">
                <Compass className="w-4 h-4 text-[#e2ffcc]" />
                <h2 className="text-xs font-bold uppercase tracking-wider text-[#dde2e4]">
                  BẢN ĐỒ SỐ WEBGIS &amp; KIỂM ĐỊNH LEAFLET MARKER ICONS (W1-11)
                </h2>
              </div>
              <span className="text-[10px] text-[#84907f]">TÂM: 16.047°N, 108.206°E | ZOOM: 6</span>
            </div>

            <MarkerVerificationMap />
          </section>
        </div>
      </main>

      {/* 3. Footer Chuẩn San Rita Utilitarian Style */}
      <footer className="h-9 px-6 bg-[#161b13] border-t border-[#2d3329] flex items-center justify-between text-[10px] text-[#84907f] uppercase">
        <div>VNstormgis © 2026 // NỀN TẢNG GIÁM SÁT THỜI TIẾT THÔNG MINH</div>
        <div className="flex items-center gap-3">
          <span>REACT 18.3</span>
          <span>•</span>
          <span>VITE 5.3</span>
          <span>•</span>
          <span className="text-[#e2ffcc]">SAN RITA DESIGN</span>
          <span>•</span>
          <span>LEAFLET 1.9</span>
        </div>
      </footer>
    </div>
  );
}

export default App;
