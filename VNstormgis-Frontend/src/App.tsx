/**
 * @file App.tsx
 * @description Thành phần gốc (Root Component) của ứng dụng WebGIS Giám sát Khí tượng & Bão lũ Việt Nam.
 * Tích hợp Bố cục Khung Hợp Nhất (UnifiedAppLayout) và Bảng Kiểm Định Hạ Tầng (DiagnosticsPanel).
 *
 * @author VNstormgis Team - Frontend & Software Architect
 * @version 1.0.0
 */

import { UnifiedAppLayout } from "@/components/layout/UnifiedAppLayout";
import { DiagnosticsPanel } from "@/components/dashboard/DiagnosticsPanel";

export function App() {
  return <UnifiedAppLayout diagnosticsContent={<DiagnosticsPanel />} />;
}

export default App;
