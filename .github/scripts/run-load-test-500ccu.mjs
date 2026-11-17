#!/usr/bin/env node
/**
 * Bộ chạy kiểm thử tải 500 CCU tự thân (Zero External Binary Dependency) — Task W6-03
 * Mô phỏng 500 người dùng đồng thời (Concurrent Users) truy cập các API bản đồ GeoJSON,
 * Panel Cảnh báo và Trợ lý Chat AI dưới sự bảo vệ của Redis Cache + Bucket4j Rate Limiter.
 *
 * Tiêu chí nghiệm thu (Master Timeline §3.7 & §6.2):
 *   1. Số lượng người dùng đồng thời: 500 CCU
 *   2. Độ trễ phản hồi P95 của API thời tiết (/api/v1/weather/current) <= 100 ms
 *   3. Độ trễ phản hồi trung bình (Avg) của các API bản đồ < 50 ms
 *   4. Tỷ lệ lỗi máy chủ HTTP 5xx = 0.00%
 */

import http from "node:http";
import https from "node:https";
import fs from "node:fs";
import path from "node:path";

function parseArgs(argv) {
  const args = {
    baseUrl: process.env.BACKEND_BASE_URL || "http://127.0.0.1:8080",
    ccu: Number(process.env.LOAD_TEST_CCU || 500),
    rounds: Number(process.env.LOAD_TEST_ROUNDS || 8),
    thinkMs: Number(process.env.LOAD_TEST_THINK_MS || 250),
    rampUpMs: Number(process.env.LOAD_TEST_RAMP_UP_MS || 1000),
    chatUsers: Number(process.env.LOAD_TEST_CHAT_USERS || 40),
    chatBurstPerUser: Number(process.env.LOAD_TEST_CHAT_BURST || 12),
    p95ThresholdMs: Number(process.env.LOAD_TEST_P95_THRESHOLD_MS || 100),
    avgThresholdMs: Number(process.env.LOAD_TEST_AVG_THRESHOLD_MS || 50),
    outFile: process.env.LOAD_TEST_OUT || "",
  };

  for (let i = 2; i < argv.length; i++) {
    const token = argv[i];
    if (token === "--base-url" && argv[i + 1]) args.baseUrl = argv[++i];
    else if (token === "--ccu" && argv[i + 1]) args.ccu = Number(argv[++i]);
    else if (token === "--rounds" && argv[i + 1]) args.rounds = Number(argv[++i]);
    else if (token === "--think-ms" && argv[i + 1]) args.thinkMs = Number(argv[++i]);
    else if (token === "--ramp-up-ms" && argv[i + 1]) args.rampUpMs = Number(argv[++i]);
    else if (token === "--chat-users" && argv[i + 1]) args.chatUsers = Number(argv[++i]);
    else if (token === "--chat-burst" && argv[i + 1]) args.chatBurstPerUser = Number(argv[++i]);
    else if (token === "--out" && argv[i + 1]) args.outFile = argv[++i];
  }
  // Avoid libuv 4-thread DNS lookup contention on "localhost" under 500 CCU
  args.baseUrl = args.baseUrl.replace("://localhost", "://127.0.0.1");
  return args;
}

function createAgent(baseUrl, maxSockets) {
  const isHttps = baseUrl.startsWith("https:");
  const AgentClass = isHttps ? https.Agent : http.Agent;
  return new AgentClass({
    keepAlive: true,
    maxSockets: Math.max(maxSockets, 512),
    maxFreeSockets: 256,
    timeout: 15000,
  });
}

function requestRaw(baseUrl, agent, options) {
  return new Promise((resolve) => {
    const urlObj = new URL(options.path, baseUrl);
    const isHttps = urlObj.protocol === "https:";
    const transport = isHttps ? https : http;
    const bodyBuffer = options.body ? Buffer.from(options.body, "utf8") : null;
    const headers = {
      Accept: "application/json",
      ...(options.headers || {}),
    };
    if (bodyBuffer) {
      headers["Content-Type"] = "application/json";
      headers["Content-Length"] = String(bodyBuffer.byteLength);
    }

    const startNs = process.hrtime.bigint();
    const req = transport.request(
      urlObj,
      {
        method: options.method || "GET",
        headers,
        agent,
        timeout: 10000,
      },
      (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => {
          const durationMs =
            Number(process.hrtime.bigint() - startNs) / 1_000_000;
          const rawBody = Buffer.concat(chunks).toString("utf8");
          resolve({
            status: res.statusCode || 0,
            durationMs,
            body: rawBody,
            headers: res.headers,
            networkError: null,
          });
        });
      }
    );

    req.on("timeout", () => {
      req.destroy(new Error("REQUEST_TIMEOUT"));
    });

    req.on("error", (err) => {
      const durationMs = Number(process.hrtime.bigint() - startNs) / 1_000_000;
      resolve({
        status: 0,
        durationMs,
        body: "",
        headers: {},
        networkError: err.message || String(err),
      });
    });

    if (bodyBuffer) {
      req.write(bodyBuffer);
    }
    req.end();
  });
}

function computeStats(samples) {
  if (!samples.length) {
    return {
      count: 0,
      minMs: 0,
      avgMs: 0,
      p50Ms: 0,
      p95Ms: 0,
      p99Ms: 0,
      maxMs: 0,
      statusCounts: {},
      errors5xx: 0,
      networkErrors: 0,
    };
  }
  const durations = samples.map((s) => s.durationMs).sort((a, b) => a - b);
  const sum = durations.reduce((acc, v) => acc + v, 0);
  const pick = (p) => {
    const idx = Math.min(
      durations.length - 1,
      Math.max(0, Math.ceil((p / 100) * durations.length) - 1)
    );
    return Number(durations[idx].toFixed(3));
  };
  const statusCounts = {};
  let errors5xx = 0;
  let networkErrors = 0;
  for (const s of samples) {
    const key = String(s.status);
    statusCounts[key] = (statusCounts[key] || 0) + 1;
    if (s.status >= 500) errors5xx++;
    if (s.status === 0 || s.networkError) networkErrors++;
  }
  return {
    count: samples.length,
    minMs: Number(durations[0].toFixed(3)),
    avgMs: Number((sum / durations.length).toFixed(3)),
    p50Ms: pick(50),
    p95Ms: pick(95),
    p99Ms: pick(99),
    maxMs: Number(durations[durations.length - 1].toFixed(3)),
    statusCounts,
    errors5xx,
    networkErrors,
  };
}

async function readActuatorMetric(baseUrl, agent, metricName) {
  const res = await requestRaw(baseUrl, agent, {
    path: `/actuator/metrics/${encodeURIComponent(metricName)}`,
  });
  if (res.status !== 200) return null;
  try {
    const parsed = JSON.parse(res.body);
    const m = (parsed.measurements || [])[0];
    return m ? m.value : null;
  } catch {
    return null;
  }
}

async function main() {
  const cfg = parseArgs(process.argv);
  const agent = createAgent(cfg.baseUrl, cfg.ccu);

  console.log(
    `[W6-03] Starting 500-CCU Load Test against ${cfg.baseUrl} (ccu=${cfg.ccu}, rounds=${cfg.rounds})`
  );

  // 1. Warm-up & Cache Prime
  const mapEndpoints = [
    { name: "weather_current", path: "/api/v1/weather/current" },
    { name: "locations_geojson", path: "/api/v1/locations?format=geojson" },
    {
      name: "storms_active",
      path: "/api/v1/storms?status=active&format=geojson",
    },
    { name: "alerts_active", path: "/api/v1/alerts/active" },
  ];

  for (const ep of mapEndpoints) {
    const prime = await requestRaw(cfg.baseUrl, agent, { path: ep.path });
    if (prime.status !== 200) {
      console.error(
        `[W6-03] Warm-up failed on ${ep.path}: status=${prime.status} err=${prime.networkError}`
      );
      process.exit(1);
    }
  }

  // Pre-warm keep-alive socket pool & JIT compiler so measurement reflects steady-state 500-CCU
  // server response time rather than initial TCP handshake / JIT compilation burst
  for (let w = 0; w < 3; w++) {
    await Promise.all(
      Array.from({ length: Math.min(cfg.ccu, 250) }, (_, i) =>
        requestRaw(cfg.baseUrl, agent, {
          path: mapEndpoints[i % mapEndpoints.length].path,
        })
      )
    );
  }

  const fallbackBefore =
    (await readActuatorMetric(
      cfg.baseUrl,
      agent,
      "weathergis.cache.fallback"
    )) ?? 0;

  // 2. Execute 500-CCU Concurrent Map & Alert Workload
  // Realistic WebGIS user session mix: each VU loads the 3 map layers (weather_current,
  // locations_geojson, storms_active), toggles/refreshes map layers, and polls active alerts once.
  const vuSchedule = [
    mapEndpoints[0], // weather_current
    mapEndpoints[1], // locations_geojson
    mapEndpoints[2], // storms_active
    mapEndpoints[0], // weather_current
    mapEndpoints[2], // storms_active
    mapEndpoints[1], // locations_geojson
    mapEndpoints[0], // weather_current
    mapEndpoints[3], // alerts_active (60s poll in UI)
  ];

  const mapSamplesByEndpoint = {
    weather_current: [],
    locations_geojson: [],
    storms_active: [],
    alerts_active: [],
  };
  const allMapSamples = [];

  let peakHikariActive = 0;
  let peakHikariPending = 0;
  let pollerStopped = false;

  const metricPoller = (async () => {
    while (!pollerStopped) {
      const [active, pending] = await Promise.all([
        readActuatorMetric(cfg.baseUrl, agent, "hikaricp.connections.active"),
        readActuatorMetric(cfg.baseUrl, agent, "hikaricp.connections.pending"),
      ]);
      if (typeof active === "number" && active > peakHikariActive) {
        peakHikariActive = active;
      }
      if (typeof pending === "number" && pending > peakHikariPending) {
        peakHikariPending = pending;
      }
      await new Promise((r) => setTimeout(r, 40));
    }
  })();

  const mapStartNs = process.hrtime.bigint();
  const virtualUsers = Array.from({ length: cfg.ccu }, (_, vuId) =>
    (async () => {
      // Smooth ramp-up across rampUpMs so all 500 VUs are concurrently active in steady state
      if (cfg.rampUpMs > 0) {
        const delay = Math.floor((vuId / cfg.ccu) * cfg.rampUpMs);
        if (delay > 0) {
          await new Promise((r) => setTimeout(r, delay));
        }
      }
      for (let round = 0; round < cfg.rounds; round++) {
        const ep = vuSchedule[(vuId + round) % vuSchedule.length];
        const res = await requestRaw(cfg.baseUrl, agent, { path: ep.path });
        mapSamplesByEndpoint[ep.name].push(res);
        allMapSamples.push(res);
        if (cfg.thinkMs > 0 && round < cfg.rounds - 1) {
          const jitter = (vuId % 19) * 3;
          await new Promise((r) => setTimeout(r, cfg.thinkMs + jitter));
        }
      }
    })()
  );

  await Promise.all(virtualUsers);
  const mapWallTimeSec =
    Number(process.hrtime.bigint() - mapStartNs) / 1_000_000_000;
  pollerStopped = true;
  await metricPoller;

  // 3. Execute Concurrent Chat + Rate-Limiter Stress Workload
  // Each virtual chat user creates an anonymous session and sends `chatBurstPerUser` messages
  // (e.g. 12 messages -> first 10 return 200 OK, messages 11..12 return 429 CHT_429_RATE_LIMITED)
  const chatSamples = [];
  const sessionCreateSamples = [];
  const chatStartNs = process.hrtime.bigint();

  const chatWorkers = Array.from({ length: cfg.chatUsers }, (_, idx) =>
    (async () => {
      const clientIp = `198.51.100.${(idx % 200) + 10}`;
      const createRes = await requestRaw(cfg.baseUrl, agent, {
        method: "POST",
        path: "/api/v1/chat/sessions",
        headers: { "X-Forwarded-For": clientIp },
        body: JSON.stringify({ title: `W603_LOAD_${idx}` }),
      });
      sessionCreateSamples.push(createRes);
      if (createRes.status !== 201) return;

      let sessionId = "";
      let sessionToken = "";
      try {
        const parsed = JSON.parse(createRes.body);
        sessionId = parsed.data.session_id;
        sessionToken = parsed.data.session_token;
      } catch {
        return;
      }

      for (let m = 0; m < cfg.chatBurstPerUser; m++) {
        const msgRes = await requestRaw(cfg.baseUrl, agent, {
          method: "POST",
          path: `/api/v1/chat/sessions/${sessionId}/messages`,
          headers: {
            "X-Session-Token": sessionToken,
            "X-Forwarded-For": clientIp,
          },
          body: JSON.stringify({
            message: "Khu vực nào đang có mưa lớn nhất hiện nay?",
          }),
        });
        chatSamples.push(msgRes);
      }
    })()
  );

  await Promise.all(chatWorkers);
  const chatWallTimeSec =
    Number(process.hrtime.bigint() - chatStartNs) / 1_000_000_000;

  const fallbackAfter =
    (await readActuatorMetric(
      cfg.baseUrl,
      agent,
      "weathergis.cache.fallback"
    )) ?? 0;
  const jvmMemoryBytes = await readActuatorMetric(
    cfg.baseUrl,
    agent,
    "jvm.memory.used"
  );
  const hikariActiveAfter = await readActuatorMetric(
    cfg.baseUrl,
    agent,
    "hikaricp.connections.active"
  );

  agent.destroy();

  const endpointStats = {};
  for (const [name, list] of Object.entries(mapSamplesByEndpoint)) {
    endpointStats[name] = computeStats(list);
  }
  const mapAggregate = computeStats(allMapSamples);
  const chatAggregate = computeStats(chatSamples);
  const sessionCreateAggregate = computeStats(sessionCreateSamples);

  const totalRequests =
    allMapSamples.length + sessionCreateSamples.length + chatSamples.length;
  const total5xx =
    mapAggregate.errors5xx +
    sessionCreateAggregate.errors5xx +
    chatAggregate.errors5xx;
  const totalNetworkErrors =
    mapAggregate.networkErrors +
    sessionCreateAggregate.networkErrors +
    chatAggregate.networkErrors;

  const weatherP95Pass =
    endpointStats.weather_current.p95Ms <= cfg.p95ThresholdMs;
  const mapAvgPass = mapAggregate.avgMs < cfg.avgThresholdMs;
  const zero5xxPass = total5xx === 0 && totalNetworkErrors === 0;
  const chatRateLimiterPass =
    (chatAggregate.statusCounts["200"] || 0) > 0 &&
    (chatAggregate.statusCounts["429"] || 0) > 0 &&
    chatAggregate.errors5xx === 0;

  const overallPass =
    weatherP95Pass && mapAvgPass && zero5xxPass && chatRateLimiterPass;

  const summary = {
    task: "W6-03",
    timestamp: new Date().toISOString(),
    baseUrl: cfg.baseUrl,
    config: {
      ccu: cfg.ccu,
      rounds: cfg.rounds,
      chatUsers: cfg.chatUsers,
      chatBurstPerUser: cfg.chatBurstPerUser,
      thresholds: {
        weatherCurrentP95MaxMs: cfg.p95ThresholdMs,
        mapAggregateAvgMaxMs: cfg.avgThresholdMs,
        http5xxRate: 0,
      },
    },
    overallResult: overallPass ? "PASS" : "FAIL",
    assertions: {
      weatherCurrentP95Pass: weatherP95Pass,
      mapAggregateAvgPass: mapAvgPass,
      zero5xxAndNetworkErrorsPass: zero5xxPass,
      chatRateLimiterProtectionPass: chatRateLimiterPass,
    },
    totals: {
      totalRequests,
      mapRequests: allMapSamples.length,
      chatSessionCreates: sessionCreateSamples.length,
      chatMessages: chatSamples.length,
      total5xx,
      totalNetworkErrors,
      mapWallTimeSec: Number(mapWallTimeSec.toFixed(3)),
      mapThroughputRps: Number(
        (allMapSamples.length / Math.max(mapWallTimeSec, 0.001)).toFixed(2)
      ),
      chatWallTimeSec: Number(chatWallTimeSec.toFixed(3)),
      chatThroughputRps: Number(
        (chatSamples.length / Math.max(chatWallTimeSec, 0.001)).toFixed(2)
      ),
    },
    mapAggregate,
    endpoints: endpointStats,
    chatSessionCreate: sessionCreateAggregate,
    chatMessages: chatAggregate,
    actuator: {
      peakHikariActiveConnections: peakHikariActive,
      peakHikariPendingConnections: peakHikariPending,
      postRunHikariActiveConnections: hikariActiveAfter,
      cacheFallbackDelta: fallbackAfter - fallbackBefore,
      jvmMemoryUsedMiB:
        typeof jvmMemoryBytes === "number"
          ? Number((jvmMemoryBytes / (1024 * 1024)).toFixed(2))
          : null,
    },
  };

  if (cfg.outFile) {
    fs.mkdirSync(path.dirname(path.resolve(cfg.outFile)), { recursive: true });
    fs.writeFileSync(cfg.outFile, JSON.stringify(summary, null, 2) + "\n");
  }

  console.log(JSON.stringify(summary, null, 2));

  if (!overallPass) {
    console.error("[W6-03] Load test thresholds FAILED!");
    process.exit(1);
  }
}

main().catch((err) => {
  console.error("[W6-03] Fatal error:", err);
  process.exit(1);
});
