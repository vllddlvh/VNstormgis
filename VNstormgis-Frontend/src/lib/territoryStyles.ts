/**
 * @file territoryStyles.ts
 * @description Style dùng chung cho lớp viền lãnh thổ và legend trong LayerControlDrawer,
 * đảm bảo swatch chú giải khớp 100% tham số Leaflet đang render.
 */

import type { PathOptions } from "leaflet";

/** Đỏ cảnh báo San Rita (Crimson Extreme) — dùng chung cho viền lãnh thổ và biển. */
export const TERRITORY_RED = "#dc2626";

export const TERRITORY_MARITIME_DASH = "6 4";
export const TERRITORY_ARCHIPELAGO_DASH = "3 5";

/** Đất liền + hải đảo: nét liền 2px, tô nền rất nhạt để không che bản đồ nền. */
export const TERRITORY_LAND_STYLE: PathOptions = {
  color: TERRITORY_RED,
  weight: 2,
  opacity: 0.95,
  fillColor: TERRITORY_RED,
  fillOpacity: 0.06,
};

/** Vùng biển EEZ: nét đứt 1.5px. */
export const TERRITORY_MARITIME_STYLE: PathOptions = {
  color: TERRITORY_RED,
  weight: 1.5,
  opacity: 0.8,
  dashArray: TERRITORY_MARITIME_DASH,
  fillColor: TERRITORY_RED,
  fillOpacity: 0.03,
};

/** Vòng theo dõi quần đảo: nét đứt mảnh, nhạt hơn EEZ để không lẫn với vùng bão. */
export const TERRITORY_ARCHIPELAGO_STYLE: PathOptions = {
  ...TERRITORY_MARITIME_STYLE,
  weight: 1,
  opacity: 0.55,
  dashArray: TERRITORY_ARCHIPELAGO_DASH,
  fillOpacity: 0.02,
};
