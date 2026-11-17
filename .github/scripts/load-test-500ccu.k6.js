/**
 * Kịch bản kiểm thử tải k6 chuẩn cho 500 người dùng đồng thời (500 CCU) — Task W6-03
 * Tham chiếu: Master Timeline §3.7 & §6.2, SRS NFR-PER-001..003
 *
 * Cách chạy (khi máy có cài k6):
 *   k6 run -e BASE_URL=http://localhost:8080 .github/scripts/load-test-500ccu.k6.js
 */
import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

const weatherCurrentLatency = new Trend("weather_current_duration_ms", true);
const mapReadLatency = new Trend("map_read_duration_ms", true);
const serverErrorRate = new Rate("http_5xx_error_rate");

export const options = {
  stages: [
    { duration: "30s", target: 100 },
    { duration: "1m", target: 500 },
    { duration: "2m", target: 500 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_5xx_error_rate: ["rate==0"],
    weather_current_duration_ms: ["p(95)<=100", "avg<50"],
    map_read_duration_ms: ["p(95)<=100", "avg<50"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export default function () {
  const endpoints = [
    { name: "weather_current", url: `${BASE_URL}/api/v1/weather/current` },
    { name: "locations", url: `${BASE_URL}/api/v1/locations?format=geojson` },
    {
      name: "storms_active",
      url: `${BASE_URL}/api/v1/storms?status=active&format=geojson`,
    },
    { name: "alerts_active", url: `${BASE_URL}/api/v1/alerts/active` },
  ];

  for (const ep of endpoints) {
    const res = http.get(ep.url, {
      tags: { endpoint: ep.name },
      headers: { Accept: "application/json" },
    });

    serverErrorRate.add(res.status >= 500);
    mapReadLatency.add(res.timings.duration);
    if (ep.name === "weather_current") {
      weatherCurrentLatency.add(res.timings.duration);
    }

    check(res, {
      [`${ep.name} status is 200`]: (r) => r.status === 200,
      [`${ep.name} has FeatureCollection`]: (r) =>
        r.body && r.body.includes('"FeatureCollection"'),
    });
  }

  sleep(0.2);
}
