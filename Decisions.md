# Các quyết định thiết kế

## 2026-10-11: Khắc phục sự cố Leaflet Marker Icon 404 (RSK-W1-03) & Chuẩn hóa bộ tiện ích trắc địa WGS84 (W1-11)

- **Lý do:** Leaflet sử dụng cơ chế nội suy đường dẫn tương đối runtime qua `_getIconUrl` và `_detectIconPath()` từ class CSS, gây lỗi HTTP 404 khi đóng gói bằng Vite/Rollup do cơ chế phân giải tĩnh ES Modules và Content Hashing. Ngoài ra, cần chuẩn hóa quy ước thứ tự tọa độ trắc địa `[Lat, Lng]` (Leaflet) và `[Lng, Lat]` (PostGIS / GeoJSON RFC 7946) để tránh sai lệch không gian.
- **Phương án lựa chọn:** Xây dựng module `frontend/src/components/map/leafletConfig.ts` nhập trực tiếp ảnh nhị phân qua ESM tĩnh của Vite, xóa bỏ `_getIconUrl` trên prototype và cấu hình tường minh thông qua `L.Icon.Default.mergeOptions`. Đồng thời tạo `geoUtils.ts` xử lý chuyển đổi hai chiều và xác thực Bounding Box Việt Nam.
- **Phương án bị loại:**
  - _Sao chép thủ công tệp ảnh sang `frontend/public/`:_ Kém tin cậy, không được băm tên tệp (Content Hash / Cache Busting), dễ quên khi nâng cấp thư viện.
  - _Sử dụng plugin npm bên thứ ba `vite-plugin-leaflet`:_ Tăng phụ thuộc bên ngoài không cần thiết, rủi ro không tương thích các phiên bản Vite 5+.
- **Ràng buộc:** Tuân thủ SYS-RULE-001 về quy ước trục tọa độ, Design System San Rita (bảng màu Highlighter Mint / Sage Stone / Carbon Ink, góc nhọn 0px, font utilitarian mono).
