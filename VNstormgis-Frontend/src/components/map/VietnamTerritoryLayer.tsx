/**
 * @file VietnamTerritoryLayer.tsx
 * @description Lớp viền đỏ lãnh thổ Việt Nam: đất liền + hải đảo (nét liền), vùng biển
 * chủ quyền (nét đứt) và vòng theo dõi hai quần đảo Hoàng Sa/Trường Sa. Lớp phi tương tác —
 * không chặn hover/click của trạm và bão.
 *
 * Vùng biển dùng EEZ của Marine Regions; EEZ này không bao phủ quần đảo Hoàng Sa do
 * tranh chấp, nên bổ sung vòng theo dõi cho cả Hoàng Sa và Trường Sa.
 *
 * @author VNstormgis Team - Frontend & GIS Engineer
 * @version 1.1.0
 */

import React from "react";
import { Circle, GeoJSON } from "react-leaflet";
import {
  TERRITORY_ARCHIPELAGO_STYLE,
  TERRITORY_LAND_STYLE,
  TERRITORY_MARITIME_STYLE,
} from "@/lib/territoryStyles";
import {
  VIETNAM_LAND_GEOJSON,
  VIETNAM_MARITIME_GEOJSON,
} from "@/data/vietnamTerritory";

/**
 * Vòng theo dõi hai quần đảo — tâm lấy đúng tọa độ trạm HSA/TSA từ `/api/v1/locations`,
 * bán kính là vùng hiển thị nghiệp vụ (không phải đường ranh giới pháp lý).
 */
const ARCHIPELAGO_TRACKING_ZONES: Array<{
  id: string;
  center: [number, number];
  radiusM: number;
}> = [
  { id: "hoang-sa", center: [16.5, 111.6], radiusM: 120000 },
  { id: "truong-sa", center: [8.65, 114.3667], radiusM: 300000 },
];

export const VietnamTerritoryLayer: React.FC = () => (
  <>
    <GeoJSON
      data={VIETNAM_MARITIME_GEOJSON}
      style={TERRITORY_MARITIME_STYLE}
      interactive={false}
    />
    <GeoJSON
      data={VIETNAM_LAND_GEOJSON}
      style={TERRITORY_LAND_STYLE}
      interactive={false}
    />
    {ARCHIPELAGO_TRACKING_ZONES.map((zone) => (
      <Circle
        key={`archipelago-zone-${zone.id}`}
        center={zone.center}
        radius={zone.radiusM}
        interactive={false}
        pathOptions={TERRITORY_ARCHIPELAGO_STYLE}
      />
    ))}
  </>
);

export default React.memo(VietnamTerritoryLayer);
