/**
 * @file mockMapData.ts
 * @description Dữ liệu mẫu khởi tạo phục vụ kiểm thử và mô phỏng các lớp bản đồ WebGIS.
 * Bao gồm các trạm khí tượng thủy văn đại diện trên cả nước, cơn bão mô phỏng và các bản tin cảnh báo.
 *
 * @author VNstormgis Team - Frontend & GIS Specialist
 * @version 1.0.0
 */

import { StationMarkerData, WeatherAlertItem, ActiveCycloneData } from "@/types/map";

/**
 * Danh mục các trạm khí tượng tiêu biểu bao quát lãnh thổ đất liền và hải đảo Việt Nam.
 */
export const INITIAL_MOCK_STATIONS: StationMarkerData[] = [
  {
    id: "loc-han",
    code: "HAN",
    name: "Trạm Khí Tượng Láng - Hà Nội",
    region: "Bắc Bộ",
    coordinates: [21.0167, 105.8],
    severity: "SAFE",
    isActive: true,
    temperatureC: 28.5,
    rainfallMm: 4.2,
    windSpeedKmh: 14.0,
    windDirection: "Đông Bắc",
    lastUpdated: "17:00",
  },
  {
    id: "loc-hph",
    code: "HPH",
    name: "Trạm Khí Tượng Phù Liễn - Hải Phòng",
    region: "Duyên Hải Bắc Bộ",
    coordinates: [20.803, 106.632],
    severity: "ADVISORY",
    isActive: true,
    temperatureC: 27.0,
    rainfallMm: 28.5,
    windSpeedKmh: 32.0,
    windDirection: "Bắc",
    lastUpdated: "17:05",
  },
  {
    id: "loc-hsa",
    code: "HSA",
    name: "Trạm Khí Tượng Quần Đảo Hoàng Sa",
    region: "Biển Đông",
    coordinates: [16.5333, 111.6833],
    severity: "SEVERE",
    isActive: true,
    temperatureC: 29.2,
    rainfallMm: 120.0,
    windSpeedKmh: 85.0,
    windDirection: "Đông Nam",
    lastUpdated: "17:10",
  },
  {
    id: "loc-dng",
    code: "DNG",
    name: "Trạm Khí Tượng Đà Nẵng",
    region: "Trung Bộ",
    coordinates: [16.0544, 108.2022],
    severity: "WARNING",
    isActive: true,
    temperatureC: 25.8,
    rainfallMm: 85.4,
    windSpeedKmh: 54.0,
    windDirection: "Đông",
    lastUpdated: "17:15",
  },
  {
    id: "loc-bdh",
    code: "BDH",
    name: "Trạm Khí Tượng Quy Nhơn - Bình Định",
    region: "Nam Trung Bộ",
    coordinates: [13.7758, 109.2231],
    severity: "SEVERE",
    isActive: true,
    temperatureC: 24.5,
    rainfallMm: 155.0,
    windSpeedKmh: 72.0,
    windDirection: "Tây Bắc",
    lastUpdated: "17:20",
  },
  {
    id: "loc-sgn",
    code: "SGN",
    name: "Trạm Khí Tượng Tân Sơn Hòa - TP. Hồ Chí Minh",
    region: "Nam Bộ",
    coordinates: [10.7967, 106.6633],
    severity: "EXTREME",
    isActive: true,
    temperatureC: 26.2,
    rainfallMm: 215.0,
    windSpeedKmh: 68.0,
    windDirection: "Tây Nam",
    lastUpdated: "17:25",
  },
  {
    id: "loc-cma",
    code: "CMA",
    name: "Trạm Khí Tượng Cà Mau",
    region: "Tây Nam Bộ",
    coordinates: [9.1764, 105.1524],
    severity: "ADVISORY",
    isActive: true,
    temperatureC: 27.5,
    rainfallMm: 45.0,
    windSpeedKmh: 28.0,
    windDirection: "Tây Nam",
    lastUpdated: "17:30",
  },
  {
    id: "loc-tsa",
    code: "TSA",
    name: "Trạm Khí Tượng Quần Đảo Trường Sa",
    region: "Biển Đông",
    coordinates: [8.6456, 111.9197],
    severity: "CATASTROPHIC",
    isActive: true,
    temperatureC: 28.0,
    rainfallMm: 290.0,
    windSpeedKmh: 115.0,
    windDirection: "Nam",
    lastUpdated: "17:35",
  },
];

/**
 * Cơn bão nhiệt đới đang hoạt động trên khu vực Biển Đông phục vụ lớp "Active Cyclone Layer".
 */
export const SAMPLE_ACTIVE_CYCLONE: ActiveCycloneData = {
  id: "storm-yagi-03",
  name: "BÃO SỐ 3 (YAGI)",
  category: 14,
  coordinates: [18.2, 114.5], // Tọa độ tâm bão trên Biển Đông
  pressureHpa: 935,
  windSpeedKmh: 165,
  forecastTrack: [
    [17.5, 116.8],
    [18.2, 114.5], // Vị trí hiện tại
    [19.0, 111.5], // Dự báo 24h
    [20.2, 108.5], // Dự báo 48h (Vịnh Bắc Bộ)
    [21.2, 106.2], // Dự báo 72h (Đổ bộ đất liền)
  ],
  lastUpdated: "17:00 11/10/2026",
};

/**
 * Danh mục các bản tin cảnh báo khẩn cấp phục vụ thanh AlertTickerBar tương tác.
 */
export const SAMPLE_WEATHER_ALERTS: WeatherAlertItem[] = [
  {
    id: "alt-01",
    code: "TSA",
    title: "CẢNH BÁO THẢM HỌA: BÃO MẠNH & SÓNG LỚN",
    stationName: "Trạm Quần Đảo Trường Sa",
    region: "Biển Đông",
    severity: "CATASTROPHIC",
    coordinates: [8.6456, 111.9197],
    headline: "Gió giật cấp 14, biển động dữ dội, sóng cao 6-8m trên vùng biển Trường Sa",
    detail:
      "Tàu thuyền tuyệt đối không ra khơi. Toàn bộ các âu tàu thực hiện neo đậu chống bão khẩn cấp.",
    windSpeedKmh: 115.0,
    rainfallMm: 290.0,
    time: "17:35",
  },
  {
    id: "alt-02",
    code: "SGN",
    title: "CẢNH BÁO CỰC KỲ NGUY HIỂM: NGẬP LỤT ĐÔ THỊ",
    stationName: "Trạm Tân Sơn Hòa - TP.HCM",
    region: "Nam Bộ",
    severity: "EXTREME",
    coordinates: [10.7967, 106.6633],
    headline: "Mưa vũ bão kết hợp triều cường đạt 215mm, ngập sâu nhiều tuyến đường",
    detail:
      "Cảnh báo nguy cơ quá tải hệ thống thoát nước lưu vực kênh Nhiêu Lộc - Thị Nghè và sông Sài Gòn.",
    rainfallMm: 215.0,
    time: "17:25",
  },
  {
    id: "alt-03",
    code: "BDH",
    title: "CẢNH BÁO NGUY HIỂM: LŨ QUÉT & SẠT LỞ ĐẤT",
    stationName: "Trạm Quy Nhơn - Bình Định",
    region: "Nam Trung Bộ",
    severity: "SEVERE",
    coordinates: [13.7758, 109.2231],
    headline: "Mưa rất to kéo dài đạt 155mm, nguy cơ sạt lở sườn dốc đèo Cù Mông",
    detail: "Các lực lượng xung kích sẵn sàng di dời dân cư vùng trũng thấp ven sông Kôn.",
    rainfallMm: 155.0,
    time: "17:20",
  },
  {
    id: "alt-04",
    code: "DNG",
    title: "CẢNH BÁO MƯA LỚN DIỆN RỘNG MIỀN TRUNG",
    stationName: "Trạm Khí Tượng Đà Nẵng",
    region: "Trung Bộ",
    severity: "WARNING",
    coordinates: [16.0544, 108.2022],
    headline: "Lượng mưa đo được 85.4mm trong 6 giờ qua, gió cấp 6 giật cấp 7",
    detail: "Đề phòng ngập úng cục bộ khu vực nội thị Hải Châu và vùng ven sông Hàn.",
    rainfallMm: 85.4,
    time: "17:15",
  },
  {
    id: "alt-05",
    code: "HSA",
    title: "CẢNH BÁO GIÓ MẠNH VÙNG BIỂN HOÀNG SA",
    stationName: "Trạm Quần Đảo Hoàng Sa",
    region: "Biển Đông",
    severity: "SEVERE",
    coordinates: [16.5333, 111.6833],
    headline: "Vùng biển Hoàng Sa chịu ảnh hưởng dải hội tụ nhiệt đới, gió giật 85km/h",
    detail: "Khuyến cáo các tàu cá duy trì liên lạc thường xuyên qua hệ thống đài canh duyên hải.",
    windSpeedKmh: 85.0,
    time: "17:10",
  },
];
