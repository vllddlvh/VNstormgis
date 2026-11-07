/** Isolated browser fixtures; never sent to or seeded in the live backend. */
export const issuedAt = "2026-11-07T02:00:00Z";
export const ids = Array.from(
  { length: 7 },
  (_, index) =>
    `00000000-0000-4000-8000-${String(index + 1).padStart(12, "0")}`,
);
export const stationId = "00000000-0000-4000-8000-111111111111";
const severities = [
  "INFO",
  "WARNING",
  "DANGER",
  "CRITICAL",
  "UNEXPECTED",
  "DANGER",
  "WARNING",
];
export const alerts = ids.map((id, index) => ({
  type: "Feature",
  id,
  geometry:
    index === 4
      ? null
      : index === 5
        ? { type: "Point", coordinates: [181, 91] }
        : {
            type: "Point",
            coordinates: index === 6 ? [130.2, 32] : [108.2, 16],
          },
  properties: {
    alert_id: id,
    location_code: index === 4 ? null : "FIXTURE",
    location_name: index === 4 ? null : "Trạm kiểm thử Đà Nẵng",
    severity_level: severities[index],
    title:
      index === 4
        ? "Tiêu đề tiếng Việt dài cần xuống dòng ".repeat(8)
        : `Cảnh báo fixture ${index + 1}`,
    trigger_reason: `Nguyên nhân fixture ${index + 1}`,
    triggered_at: index === 4 ? null : issuedAt,
    is_fallback: index % 2 === 1,
  },
}));
export const collection = (features = alerts) => ({
  type: "FeatureCollection",
  features,
});
const violation = (
  parameter,
  measured_value,
  operator,
  threshold_value,
  unit,
  extras = {},
) => ({
  threshold_id: 12,
  parameter,
  measured_value,
  operator,
  threshold_value,
  unit,
  severity_level: "DANGER",
  source_timestamp: "2026-11-07T01:45:00Z",
  evaluation_window: "2026-11-07T01:00:00Z",
  violation_key: `fixture-${parameter}`,
  storm_id: null,
  storm_name: null,
  distance_km: null,
  ...extras,
});
export const details = Object.fromEntries(
  alerts.map((feature, index) => [
    feature.id,
    {
      id: feature.id,
      threshold_id: index === 4 ? null : 12,
      location_id: index === 4 ? null : stationId,
      storm_id: null,
      title: feature.properties.title,
      severity_level: feature.properties.severity_level,
      trigger_reason: feature.properties.trigger_reason,
      ai_explanation:
        index === 0
          ? 'Giải thích fixture A.\n<img src=x onerror="window.__alertXss=true"> <script>window.__alertXss=true</script>'
          : `Giải thích fixture ${index + 1}.`,
      is_fallback: feature.properties.is_fallback,
      status: "active",
      triggered_at: feature.properties.triggered_at,
      resolved_at: null,
      created_at: issuedAt,
      location:
        index === 4
          ? null
          : {
              id: stationId,
              code: "CURRENT",
              name: "Tên trạm hiện tại khác snapshot",
              region: "Trung Bộ",
              coordinates: [105, 20],
            },
      threshold:
        index === 4
          ? null
          : {
              id: 12,
              name: "Ngưỡng hiện tại đã đổi",
              parameter: "rain_1h",
              operator: ">=",
              threshold_value: 999,
              severity_level: "CRITICAL",
              description: null,
              is_enabled: true,
              updated_at: issuedAt,
            },
      storm: null,
      factual_snapshot:
        index === 4
          ? null
          : {
              location_id: stationId,
              location_code: "HISTORY",
              location_name: "Trạm lúc phát hiện",
              coordinates: [108.2, 16],
              evaluation_cycle_id: "00000000-0000-4000-8000-222222222222",
              evaluated_at: "2026-11-07T01:50:00Z",
              representative_threshold: {
                threshold_id: 12,
                parameter: "rain_1h",
                operator: ">",
                threshold_value: 50,
                severity_level: "DANGER",
                unit: "mm",
                violation_key: "fixture-rain_1h",
              },
              violations: [
                violation("rain_1h", 51.25, ">", 50, "mm"),
                violation("wind_speed", 17.5, ">=", 15, "m/s"),
                violation("storm_dist", 35, "<=", 40, "km", {
                  storm_id: "FIXTURE-STORM",
                  storm_name: "Bão fixture",
                  distance_km: 35,
                }),
              ],
            },
    },
  ]),
);
details[ids[3]].factual_snapshot.violations.push(
  ...["__proto__", "constructor"].map((parameter) =>
    violation(parameter, 2, ">", 1, "mm"),
  ),
);
export const envelope = (data) => ({
  success: true,
  message: "Fixture only",
  data,
  timestamp: issuedAt,
});
export const problem = (status = 500) => ({
  type: "about:blank",
  status,
  title: "Lỗi fixture",
  detail: "Không tải được dữ liệu fixture",
  instance: "/api/v1/alerts",
  errorCode: `ALR_${status}_FIXTURE`,
  timestamp: issuedAt,
});
export const stormCollection = {
  type: "FeatureCollection",
  features: [
    {
      type: "Feature",
      id: "FIXTURE-STORM",
      geometry: { type: "Point", coordinates: [108.3, 16.1] },
      properties: {
        id: "FIXTURE-STORM",
        name: "Tropical Cyclone FIXTURE",
        year: 2026,
        category: "Orange",
        max_wind_speed: 25,
        max_wind_speed_kmh: 90,
        min_pressure: null,
        status: "active",
        start_date: issuedAt,
        end_date: null,
        updated_at: issuedAt,
      },
    },
  ],
};
export const weatherCollection = {
  type: "FeatureCollection",
  features: [
    {
      type: "Feature",
      id: "FIXTURE",
      geometry: { type: "Point", coordinates: [108.2, 16] },
      properties: {
        location_id: stationId,
        code: "FIXTURE",
        name: "Trạm kiểm thử",
        region: "Trung Bộ",
        temperature: 25,
        feels_like: 26,
        humidity: 90,
        pressure: 1000,
        wind_speed: 5,
        wind_direction: 90,
        wind_gust: null,
        rain_1h: 2,
        rain_3h: 4,
        weather_condition: "Rain",
        weather_description: "Mưa fixture",
        severity_rain: "MODERATE",
        recorded_at: issuedAt,
      },
    },
  ],
};
export const locations = [
  {
    id: stationId,
    code: "FIXTURE",
    name: "Trạm kiểm thử",
    region: "Trung Bộ",
    latitude: 16,
    longitude: 108.2,
    elevation: 5,
    isActive: true,
  },
];
