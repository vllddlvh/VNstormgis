/**
 * @file leafletConfig.ts
 * @description Module khởi tạo và cấu hình biểu tượng mốc mặc định của thư viện Leaflet.
 * Khắc phục triệt để sự cố Leaflet Marker Icon 404 trong môi trường đóng gói Vite (RSK-W1-03).
 *
 * @author VNstormgis Team - Frontend & GIS Specialist
 * @version 1.0.0
 */

import L from "leaflet";

// Nhập trực tiếp tài nguyên hình ảnh nhị phân từ gói thư viện Leaflet thông qua Vite Asset Import Pipeline
import markerIcon2xUrl from "leaflet/dist/images/marker-icon-2x.png";
import markerIconUrl from "leaflet/dist/images/marker-icon.png";
import markerShadowUrl from "leaflet/dist/images/marker-shadow.png";

/**
 * Cờ theo dõi trạng thái khởi tạo nhằm bảo đảm tính lũy đẳng (Idempotence).
 * Ngăn chặn việc gọi ghi đè nhiều lần trong React 18 StrictMode.
 */
let isLeafletIconsInitialized = false;

/**
 * Khởi tạo và thiết lập lại cấu hình Icon mặc định của Leaflet.
 *
 * Phương thức này thực hiện:
 * 1. Xóa bỏ phương thức nội suy cổ điển `_getIconUrl` trên `L.Icon.Default.prototype`.
 * 2. Gán lại đường dẫn tài nguyên tĩnh đã được Vite resolved (kèm Content Hash khi build).
 * 3. Chuẩn hóa kích thước hình học và điểm neo (anchor points) trắc địa.
 *
 * @returns {void}
 */
export function initLeafletIcons(): void {
  if (isLeafletIconsInitialized) {
    return;
  }

  // 1. Loại bỏ phương thức đoán đường dẫn tự động vốn không tương thích với bundler Vite
  delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: unknown })._getIconUrl;

  // 2. Tái cấu hình toàn diện đối tượng biểu tượng mặc định với các thông số trắc địa chuẩn xác
  L.Icon.Default.mergeOptions({
    iconUrl: markerIconUrl,
    iconRetinaUrl: markerIcon2xUrl,
    shadowUrl: markerShadowUrl,
    iconSize: [25, 41],
    iconAnchor: [12, 41], // Điểm mũi kim chạm đúng tọa độ địa lý (chính giữa theo chiều ngang, đáy cùng theo chiều dọc)
    popupAnchor: [1, -34], // Đỉnh cửa sổ popup nổi cách mũi kim 7px về phía trên
    shadowSize: [41, 41],
    shadowAnchor: [12, 41],
  });

  isLeafletIconsInitialized = true;
}

/**
 * Kiểm tra trạng thái đã khởi tạo cấu hình Leaflet Icon hay chưa.
 *
 * @returns {boolean} True nếu cấu hình đã được áp dụng an toàn.
 */
export function isIconsConfigured(): boolean {
  return isLeafletIconsInitialized;
}
