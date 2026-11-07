#!/usr/bin/env node
/**
 * W4-06 isolated runtime checks. Requires a running Vite frontend and playwright-core.
 * PLAYWRIGHT_MODULE=/absolute/path/to/playwright-core/index.mjs node scripts/test-w4-06-ui.mjs
 * Optional: FRONTEND_BASE_URL, ALERTS_BACKEND_BASE_URL, CHROME_EXECUTABLE, UI_EVIDENCE_DIR, UI_TEST_FILTER.
 * Browser routes contain fixtures only; this harness never mutates the backend.
 */
import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  alerts,
  ids,
  collection,
  details,
  envelope,
  problem,
  stormCollection,
  weatherCollection,
  locations,
} from "./fixtures/w4-06-alerts.mjs";

const { chromium } = await import(
  process.env.PLAYWRIGHT_MODULE || "playwright-core"
);
const baseUrl = process.env.FRONTEND_BASE_URL || "http://localhost:5173";
const evidenceDir = path.resolve(
  process.env.UI_EVIDENCE_DIR || "/private/tmp/vnstormgis-w406-ui-evidence",
);
const browser = await chromium.launch({
  headless: true,
  ...(process.env.CHROME_EXECUTABLE || process.platform === "darwin"
    ? {
        executablePath:
          process.env.CHROME_EXECUTABLE ||
          "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
      }
    : {}),
  args: ["--no-sandbox"],
});
await mkdir(evidenceDir, { recursive: true });
const reports = [];
const contexts = [];
const traces = [];
let currentScenario = null;
const transparentPng = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a4T8AAAAASUVORK5CYII=",
  "base64",
);
const delay = (milliseconds) =>
  new Promise((resolve) => setTimeout(resolve, milliseconds));
const json = (route, data, status = 200) =>
  route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(data),
  });
const panel = (page) => page.getByTestId("alert-panel");
const list = (page) => page.getByTestId("alert-list");
const item = (page, id) => list(page).locator(`[data-alert-id="${id}"]`);
const ticker = (page) => page.getByTestId("alert-ticker");
const openButton = (page) => page.locator("#btn-open-alerts");
const refresh = (page) => page.getByTestId("alert-refresh");
const detail = (page) => page.getByTestId("alert-detail");
async function shown(locator) {
  await locator.waitFor({ state: "visible", timeout: 8000 });
}
async function contains(locator, expected) {
  await locator
    .filter({ hasText: expected })
    .waitFor({ state: "visible", timeout: 8000 });
}
async function select(page, id) {
  await item(page, id).click();
  await shown(detail(page));
}
async function back(page) {
  await panel(page)
    .getByRole("button", { name: /QUAY LẠI/i })
    .click();
  await shown(list(page));
}
async function hitTarget(locator) {
  await locator.scrollIntoViewIfNeeded();
  assert.equal(
    await locator.evaluate((node) => {
      const box = node.getBoundingClientRect();
      const target = document.elementFromPoint(
        box.x + box.width / 2,
        box.y + box.height / 2,
      );
      return Boolean(target && (target === node || node.contains(target)));
    }),
    true,
    "control must receive pointer events at its center",
  );
}
async function open(page) {
  await openButton(page).click();
  await shown(panel(page));
}
async function changed(page) {
  await page.waitForFunction(
    () => !document.querySelector('[data-testid="alert-refresh"]')?.disabled,
  );
}
async function scenario(name, action) {
  if (
    process.env.UI_TEST_FILTER &&
    !new RegExp(process.env.UI_TEST_FILTER).test(name)
  )
    return;
  const initialContexts = contexts.length;
  currentScenario = name;
  try {
    await action();
    reports.push({ name, status: "PASS" });
    console.log(`PASS ${name}`);
  } catch (error) {
    reports.push({ name, status: "FAIL", error: error.stack || String(error) });
    console.error(`FAIL ${name}: ${error.message}`);
  } finally {
    while (contexts.length > initialContexts) await contexts.pop().close();
  }
}

async function setup(options = {}) {
  const context = await browser.newContext({
    viewport: options.viewport || { width: 1366, height: 768 },
    reducedMotion: options.reducedMotion || "reduce",
  });
  contexts.push(context);
  const state = {
    active: collection(),
    activeStatus: 200,
    activeDelay: 0,
    detailDelay: {},
    detailStatus: {},
    activeRequests: [],
    detailRequests: [],
    consoleErrors: [],
    pageErrors: [],
    ...options.state,
  };
  traces.push({
    scenario: currentScenario,
    activeRequests: state.activeRequests,
    detailRequests: state.detailRequests,
    consoleErrors: state.consoleErrors,
    pageErrors: state.pageErrors,
  });
  const page = await context.newPage();
  if (options.clock) await page.clock.install();
  page.on("pageerror", (error) => state.pageErrors.push(error.message));
  page.on("console", (message) => {
    if (message.type() === "error") state.consoleErrors.push(message.text());
  });
  await page.route("**/*", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.origin !== new URL(baseUrl).origin)
      return route.fulfill({ contentType: "image/png", body: transparentPng });
    if (options.liveBackend && url.pathname.startsWith("/api/v1/alerts/")) {
      const response = await route.fetch({
        url: `${options.liveBackend}${url.pathname}${url.search}`,
      });
      return route.fulfill({ response });
    }
    if (url.pathname === "/api/v1/alerts/active") {
      state.activeRequests.push({
        method: request.method(),
        headers: request.headers(),
        at: Date.now(),
      });
      const response = structuredClone(state.active);
      const status = state.activeStatus;
      const ms = state.activeDelay;
      if (ms) await delay(ms);
      if (status === "abort") return route.abort("timedout");
      return json(route, response, status);
    }
    if (url.pathname.startsWith("/api/v1/alerts/")) {
      const id = decodeURIComponent(url.pathname.split("/").at(-1));
      state.detailRequests.push(id);
      const status = state.detailStatus[id] || 200;
      if (state.detailDelay[id]) await delay(state.detailDelay[id]);
      return json(
        route,
        status === 200 ? envelope(details[id]) : problem(status),
        status,
      );
    }
    if (url.pathname === "/actuator/health")
      return json(route, { status: "UP" });
    if (url.pathname === "/api/v1/locations")
      return json(route, envelope(locations));
    if (url.pathname === "/api/v1/weather/current")
      return json(route, weatherCollection);
    if (url.pathname === "/api/v1/storms") return json(route, stormCollection);
    if (/^\/api\/v1\/storms\/[^/]+\/track$/.test(url.pathname))
      return json(route, collection([]));
    if (url.pathname.startsWith("/api/")) return json(route, problem(404), 404);
    return route.continue();
  });
  await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  await shown(openButton(page));
  await page.evaluate(async () => {
    const source = performance
      .getEntriesByType("resource")
      .map((entry) => entry.name)
      .find((name) => /\/leaflet\.js(?:\?|$)/.test(name));
    if (!source) return;
    const module = await import(source);
    const leaflet = module.default || module;
    for (const method of ["flyTo", "setView", "setMaxBounds"]) {
      const original = leaflet.Map.prototype[method];
      leaflet.Map.prototype[method] = function (...args) {
        window.__w406Map = this;
        (window.__w406MapCalls ||= []).push({ method, args });
        return original.apply(this, args);
      };
    }
  });
  return { page, state, context };
}

try {
  await scenario(
    "TC-01/02/03 ordered list, separate severities, nullable/unknown, fallback and no credentials",
    async () => {
      const { page, state } = await setup();
      await open(page);
      await shown(list(page));
      assert.equal(
        await list(page).locator("[data-alert-id]").count(),
        alerts.length,
      );
      assert.deepEqual(
        await list(page)
          .locator("[data-alert-id]")
          .evaluateAll((nodes) => nodes.map((node) => node.dataset.alertId)),
        ids,
      );
      for (const [id, severity] of ids.map((id, index) => [
        id,
        alerts[index].properties.severity_level,
      ]))
        assert.equal(
          await item(page, id).getAttribute("data-severity"),
          severity,
        );
      assert.match(await item(page, ids[4]).innerText(), /Chưa xác định/i);
      assert.match(await item(page, ids[1]).innerText(), /BẢN TIN DỰ PHÒNG/i);
      for (const request of state.activeRequests) {
        assert.equal(request.method, "GET");
        assert.ok(!request.headers["x-admin-api-key"]);
        assert.ok(!request.headers.authorization);
        assert.ok(!request.headers["session_token"]);
      }
      assert.equal(
        state.detailRequests.length,
        0,
        "list must not eagerly fetch detail",
      );
      assert.deepEqual(state.pageErrors, []);
      await page.screenshot({
        path: path.join(evidenceDir, "1366x768-list.png"),
      });
    },
  );
  await scenario(
    "TC-04/13 historical values/operators/units, raw text and explicit locate",
    async () => {
      const { page } = await setup();
      await open(page);
      await select(page, ids[0]);
      await contains(detail(page), "Giải thích fixture A.");
      const text = await detail(page).innerText();
      for (const expected of [
        "Trạm lúc phát hiện",
        "51.25",
        "50",
        "17.5",
        "15",
        "m/s",
        "35",
        "40",
        "km",
        "Bão fixture",
        ">",
        ">=",
        "<=",
      ])
        assert.ok(
          text.includes(expected),
          `missing historical value ${expected}`,
        );
      assert.ok(text.includes("<img src=x"));
      assert.equal(await detail(page).locator("img,script").count(), 0);
      assert.equal(await page.evaluate(() => window.__alertXss), undefined);
      assert.equal(
        await detail(page).evaluate(
          (node) => getComputedStyle(node).userSelect,
        ),
        "text",
      );
      assert.equal(
        await page.evaluate(
          () =>
            (window.__w406MapCalls || []).filter(
              (call) => call.method === "flyTo",
            ).length,
        ),
        0,
        "opening detail must not navigate",
      );
      await page.getByTestId("alert-flyto").click();
      await page.waitForFunction(() => window.__w406Map?.getZoom() === 11);
      const position = await page.evaluate(() => {
        const map = window.__w406Map;
        return {
          center: map.getCenter(),
          zoom: map.getZoom(),
          calls: window.__w406MapCalls,
        };
      });
      assert.ok(Math.abs(position.center.lat - 16) < 0.001);
      assert.ok(Math.abs(position.center.lng - 108.2) < 0.001);
      assert.equal(position.zoom, 11);
      const move = position.calls.findLast(
        (call) => call.method === "flyTo" || call.method === "setView",
      );
      assert.equal(
        move.args[2]?.animate,
        false,
        "reduced motion must disable animation",
      );
      await page.screenshot({
        path: path.join(evidenceDir, "1366x768-detail.png"),
      });
    },
  );
  await scenario(
    "TC-03/13 unfamiliar parameter names cannot access inherited object properties",
    async () => {
      const { page, state } = await setup();
      await open(page);
      await select(page, ids[3]);
      await contains(detail(page), "Giải thích fixture 4.");
      const text = await detail(page).innerText();
      assert.ok(text.includes("__proto__"));
      assert.ok(text.includes("constructor"));
      assert.deepEqual(state.pageErrors, []);
    },
  );
  await scenario("TC-05 loading and empty remain distinct", async () => {
    const { page } = await setup({
      state: { active: collection([]), activeDelay: 500 },
    });
    await open(page);
    await contains(panel(page), /Đang tải cảnh báo/i);
    assert.equal(await page.getByTestId("alert-empty").count(), 0);
    await shown(page.getByTestId("alert-empty"));
    assert.match(
      await panel(page).innerText(),
      /Không có cảnh báo đang hoạt động/i,
    );
    assert.equal(await list(page).locator("[data-alert-id]").count(), 0);
  });
  for (const [name, active, activeStatus] of [
    ["500", problem(), 500],
    ["network failure", {}, "abort"],
    [
      "malformed collection",
      { type: "FeatureCollection", features: null },
      200,
    ],
  ]) {
    await scenario(
      `TC-05 initial ${name} is an error, never empty/sample`,
      async () => {
        const { page } = await setup({ state: { active, activeStatus } });
        await open(page);
        await shown(page.getByTestId("alert-error"));
        assert.equal(await page.getByTestId("alert-empty").count(), 0);
        assert.equal(await list(page).locator("[data-alert-id]").count(), 0);
        assert.match(await panel(page).innerText(), /Không tải được cảnh báo/i);
      },
    );
  }
  await scenario(
    "TC-05 Axios 10-second request timeout is an error",
    async () => {
      const { page } = await setup({ state: { activeDelay: 11_000 } });
      await open(page);
      await page
        .getByTestId("alert-error")
        .waitFor({ state: "visible", timeout: 12_000 });
      assert.equal(await page.getByTestId("alert-empty").count(), 0);
      assert.equal(await list(page).locator("[data-alert-id]").count(), 0);
    },
  );
  for (const features of [alerts, []]) {
    await scenario(
      `TC-06 stale ${features.length ? "populated" : "empty"} retains last successful data/time`,
      async () => {
        const { page, state } = await setup({
          state: { active: collection(features) },
        });
        await open(page);
        if (features.length) await shown(list(page));
        else await shown(page.getByTestId("alert-empty"));
        const before = await panel(page).innerText();
        state.activeStatus = 500;
        state.active = problem();
        await refresh(page).click();
        await contains(panel(page), /Chưa cập nhật được/i);
        assert.equal(
          await panel(page).locator("[data-alert-id]").count(),
          features.length,
        );
        if (features.length)
          assert.ok(
            (await panel(page).innerText()).includes("Cảnh báo fixture 1"),
          );
        assert.ok(before.match(/UTC\+7|Asia\/Ho_Chi_Minh/));
        assert.equal(await page.getByTestId("alert-error").count(), 1);
      },
    );
  }
  await scenario(
    "TC-07 detail A arriving after B cannot replace B; repeated refresh dedupes",
    async () => {
      const { page, state } = await setup({
        state: { detailDelay: { [ids[0]]: 700 } },
      });
      await open(page);
      await item(page, ids[0]).click();
      await shown(detail(page));
      await back(page);
      await select(page, ids[1]);
      await contains(detail(page), "Giải thích fixture 2.");
      await delay(850);
      assert.ok(
        !(await detail(page).innerText()).includes("Giải thích fixture A."),
      );
      await back(page);
      state.activeDelay = 500;
      const count = state.activeRequests.length;
      await refresh(page).click();
      assert.equal(await refresh(page).isDisabled(), true);
      await page.evaluate(() => {
        const button = document.querySelector('[data-testid="alert-refresh"]');
        button.click();
        button.click();
      });
      await changed(page);
      assert.equal(state.activeRequests.length - count, 1);
      assert.deepEqual(state.pageErrors, []);
    },
  );
  await scenario(
    "TC-08 mounted ticker safely shrinks to one then zero; TC-12 disappearing selection",
    async () => {
      const { page, state } = await setup();
      await shown(ticker(page));
      const next = ticker(page).getByRole("button", { name: /tiếp|sau/i });
      for (let index = 0; index < alerts.length - 1; index++)
        await next.click();
      assert.equal(
        await ticker(page)
          .locator("[data-alert-id]")
          .getAttribute("data-alert-id"),
        ids[6],
      );
      state.active = collection([alerts[0]]);
      await Promise.all([
        page.waitForRequest((request) =>
          request.url().endsWith("/api/v1/alerts/active"),
        ),
        page.locator("#btn-header-refresh").click(),
      ]);
      await contains(ticker(page), "Cảnh báo fixture 1");
      assert.equal(
        await ticker(page)
          .locator("[data-alert-id]")
          .getAttribute("data-alert-id"),
        ids[0],
      );
      await page.waitForFunction(
        () => !document.querySelector("#btn-header-refresh")?.disabled,
      );
      state.active = collection([]);
      await Promise.all([
        page.waitForRequest((request) =>
          request.url().endsWith("/api/v1/alerts/active"),
        ),
        page.locator("#btn-header-refresh").click(),
      ]);
      await contains(ticker(page), /Không có cảnh báo đang hoạt động/i);
      assert.equal(await ticker(page).locator("[data-alert-id]").count(), 0);
      state.active = collection();
      await open(page);
      await refresh(page).click();
      await select(page, ids[6]);
      state.active = collection([alerts[0]]);
      await refresh(page).click();
      await contains(
        panel(page),
        /Cảnh báo không còn trong danh sách đang hoạt động/i,
      );
      await shown(list(page));
      assert.equal(await list(page).locator("[data-alert-id]").count(), 1);
      assert.deepEqual(state.pageErrors, []);
    },
  );
  await scenario(
    "TC-09/11/12 legacy null fields and invalid geometry stay readable; detail retry/back",
    async () => {
      const { page, state } = await setup();
      await open(page);
      await select(page, ids[4]);
      await contains(detail(page), /Không có dữ kiện gốc/i);
      assert.match(await detail(page).innerText(), /Chưa có thời điểm/i);
      assert.equal(await page.getByTestId("alert-flyto").isDisabled(), true);
      await back(page);
      await select(page, ids[5]);
      await contains(detail(page), "Giải thích fixture 6.");
      assert.equal(await page.getByTestId("alert-flyto").isDisabled(), true);
      await back(page);
      for (const status of [400, 404, 500]) {
        state.detailStatus[ids[2]] = status;
        await select(page, ids[2]);
        await contains(
          detail(page),
          /Không tải được|Không tìm thấy|Lỗi fixture/i,
        );
        await back(page);
        assert.equal(
          await list(page).locator("[data-alert-id]").count(),
          alerts.length,
        );
      }
      state.detailStatus[ids[2]] = 200;
      await select(page, ids[2]);
      await contains(detail(page), "Giải thích fixture 3.");
    },
  );
  await scenario(
    "TC-09 adapter boundaries and map-not-ready component disable navigation",
    async () => {
      const { page, state } = await setup();
      const results = await page.evaluate(async (alert) => {
        const { getAlertCoordinates, formatAlertTime } =
          await import("/src/lib/alertUtils.ts");
        const geometries = [
          null,
          { type: "LineString", coordinates: [[108.2, 16]] },
          { type: "Point", coordinates: [181, 16] },
          { type: "Point", coordinates: [108.2, 91] },
          { type: "Point", coordinates: [Infinity, 16] },
          { type: "Point", coordinates: [108.2, NaN] },
          { type: "Point", coordinates: ["108.2", 16] },
          { type: "Point", coordinates: [] },
        ];
        const resource = (part) =>
          performance
            .getEntriesByType("resource")
            .map((entry) => entry.name)
            .find((name) =>
              name.includes(`/node_modules/.vite/deps/${part}.js`),
            );
        const React = (await import(resource("react"))).default;
        const domModule = await import(resource("react-dom_client"));
        const { createRoot } = domModule.default || domModule;
        const { AlertDetail } =
          await import("/src/components/dashboard/AlertDetail.tsx");
        const host = document.createElement("div");
        host.id = "w406-component-check";
        host.style.cssText =
          "position:fixed;inset:60px 12px 30px;background:#161b13;z-index:9999;overflow:auto";
        document.body.appendChild(host);
        window.__w406ComponentRoot = createRoot(host);
        window.__w406ComponentRoot.render(
          React.createElement(AlertDetail, {
            alert,
            mapReady: false,
            onFlyTo: () => {
              window.__w406UnexpectedFly = true;
            },
          }),
        );
        return {
          invalid: geometries.map((geometry) =>
            getAlertCoordinates({ ...alert, geometry }),
          ),
          zero: getAlertCoordinates({
            ...alert,
            geometry: { type: "Point", coordinates: [0, 0] },
          }),
          converted: getAlertCoordinates(alert),
          invalidTime: formatAlertTime("not-a-date"),
        };
      }, alerts[0]);
      assert.deepEqual(results.invalid, Array(8).fill(null));
      assert.deepEqual(results.zero, [0, 0]);
      assert.deepEqual(results.converted, [16, 108.2]);
      assert.equal(results.invalidTime, "Chưa có thời điểm");
      const host = page.locator("#w406-component-check");
      await contains(host, "Bản đồ chưa sẵn sàng");
      await contains(host, "Giải thích fixture A.");
      assert.equal(await host.getByTestId("alert-flyto").isDisabled(), true);
      assert.equal(
        await page.evaluate(() => window.__w406UnexpectedFly),
        undefined,
      );
      assert.deepEqual(state.pageErrors, []);
      await page.evaluate(() => {
        window.__w406ComponentRoot.unmount();
        document.querySelector("#w406-component-check").remove();
      });
    },
  );
  await scenario(
    "TC-10 keyboard, focus return, reduced ticker motion and three viewport screenshots",
    async () => {
      for (const viewport of [
        { width: 1366, height: 768 },
        { width: 1440, height: 900 },
        { width: 390, height: 844 },
      ]) {
        const { page } = await setup({ viewport, clock: true });
        await openButton(page).focus();
        await page.keyboard.press("Enter");
        await shown(panel(page));
        assert.equal(
          await openButton(page).getAttribute("aria-expanded"),
          "true",
        );
        await page.screenshot({
          path: path.join(
            evidenceDir,
            `${viewport.width}x${viewport.height}-list.png`,
          ),
        });
        const box = await panel(page).boundingBox();
        assert.ok(box.x >= 0 && box.x + box.width <= viewport.width + 1);
        assert.ok(box.y >= 0 && box.y + box.height <= viewport.height - 20);
        assert.equal(
          await page.evaluate(
            () => document.documentElement.scrollWidth <= window.innerWidth,
          ),
          true,
        );
        await select(page, ids[0]);
        await contains(detail(page), "Giải thích fixture A.");
        await hitTarget(page.getByTestId("alert-flyto"));
        await hitTarget(refresh(page));
        const close = panel(page).getByRole("button", {
          name: "Đóng bảng cảnh báo",
        });
        await hitTarget(close);
        assert.equal(
          await page.locator("#btn-close-layers").isVisible(),
          false,
        );
        assert.equal(
          await page.locator("#btn-ai-copilot-close").isVisible(),
          false,
        );
        await detail(page)
          .locator('[data-testid="alert-factual-snapshot"]')
          .scrollIntoViewIfNeeded();
        await page.screenshot({
          path: path.join(
            evidenceDir,
            `${viewport.width}x${viewport.height}-detail.png`,
          ),
        });
        await page.keyboard.press("Escape");
        assert.equal(
          await openButton(page).evaluate(
            (node) => document.activeElement === node,
          ),
          true,
        );
        await shown(ticker(page));
        const before = await ticker(page).innerText();
        await page.clock.fastForward(7100);
        assert.equal(await ticker(page).innerText(), before);
        await page.locator("#btn-ai-copilot-trigger").click();
        await shown(page.locator("#btn-ai-copilot-close"));
        assert.equal(await panel(page).count(), 0);
        assert.equal(
          await page.locator("#btn-close-layers").isVisible(),
          false,
        );
        await page.locator("#btn-ai-copilot-close").click();
        await page.locator("#btn-open-layers").click();
        await shown(page.locator("#btn-close-layers"));
        await open(page);
        assert.equal(
          await page.locator("#btn-close-layers").isVisible(),
          false,
        );
        assert.equal(
          await page.locator("#btn-ai-copilot-close").isVisible(),
          false,
        );
        await panel(page)
          .getByRole("button", { name: "Đóng bảng cảnh báo" })
          .click();
        assert.equal(await panel(page).count(), 0);
      }
    },
  );
  await scenario(
    "TC-10 ticker pauses on hover/focus and manual pause",
    async () => {
      const { page } = await setup({
        reducedMotion: "no-preference",
        clock: true,
      });
      await contains(ticker(page), "Cảnh báo fixture 1");
      await page.clock.fastForward(7100);
      await contains(ticker(page), "Cảnh báo fixture 2");
      const next = ticker(page).getByRole("button", { name: /tiếp|sau/i });
      await next.focus();
      const focused = await ticker(page).innerText();
      await page.clock.fastForward(7100);
      assert.equal(await ticker(page).innerText(), focused);
      await page.locator("#btn-header-refresh").focus();
      await ticker(page).hover();
      const hovered = await ticker(page).innerText();
      await page.clock.fastForward(7100);
      assert.equal(await ticker(page).innerText(), hovered);
      await page.mouse.move(10, 10);
      await ticker(page)
        .getByRole("button", { name: /dừng|tạm dừng/i })
        .click();
      await page.locator("#btn-header-refresh").focus();
      const paused = await ticker(page).innerText();
      await page.clock.fastForward(7100);
      assert.equal(await ticker(page).innerText(), paused);
    },
  );
  await scenario(
    "TC-14 outside bounds, reset, map remount, weather/wind/storm persist",
    async () => {
      const { page } = await setup();
      await page.locator(".storm-center-marker-container").click();
      await page.waitForFunction(() => window.__w406Map?.getZoom() === 7);
      await page.locator("#btn-tab-operations").click();
      await open(page);
      await select(page, ids[6]);
      await contains(detail(page), "Giải thích fixture 7.");
      await page.getByTestId("alert-flyto").click();
      await page.waitForFunction(
        () => Math.abs(window.__w406Map?.getCenter().lng - 130.2) < 0.001,
      );
      assert.equal(
        await page.evaluate(() =>
          Boolean(window.__w406Map.options.maxBounds?.isValid?.()),
        ),
        false,
      );
      await page.locator("#btn-header-recenter").click();
      await page.waitForFunction(() => window.__w406Map?.getZoom() === 6);
      assert.ok(
        await page.evaluate(() => window.__w406Map.options.maxBounds.isValid()),
      );
      await page.keyboard.press("Escape");
      await page.locator("#btn-tab-diagnostics").click();
      await page.locator("#btn-tab-operations").click();
      await shown(openButton(page));
      await open(page);
      await select(page, ids[0]);
      await contains(detail(page), "Giải thích fixture A.");
      await page.getByTestId("alert-flyto").click();
      await page.waitForFunction(() => window.__w406Map?.getZoom() === 11);
      assert.equal(await page.locator(".leaflet-container").count(), 1);
      assert.equal(await page.locator(".wind-arrow-marker").count(), 1);
      assert.ok(
        (await page.locator(".storm-center-marker-container").count()) >= 1,
      );
      assert.deepEqual(
        (await page.evaluate(() => window.__w406Map.getCenter().lat)).toFixed(
          3,
        ),
        "16.000",
      );
    },
  );
  await scenario(
    "TC-07 polling fires once at 60 seconds without detail request fanout",
    async () => {
      const { page, state } = await setup({ clock: true });
      await open(page);
      await shown(list(page));
      const count = state.activeRequests.length;
      await Promise.all([
        page.waitForRequest((request) =>
          request.url().endsWith("/api/v1/alerts/active"),
        ),
        page.clock.fastForward(60_001),
      ]);
      await page.waitForFunction(
        () =>
          !document.querySelector('[data-testid="alert-refresh"]')?.disabled,
      );
      assert.equal(state.activeRequests.length - count, 1);
      assert.equal(state.detailRequests.length, 0);
    },
  );
  const backendBase =
    process.env.ALERTS_BACKEND_BASE_URL || "http://localhost:8080";
  let liveCollection;
  const includeLiveSmoke =
    !process.env.UI_TEST_FILTER ||
    new RegExp(process.env.UI_TEST_FILTER).test("TC-15");
  if (includeLiveSmoke)
    try {
      const response = await fetch(`${backendBase}/api/v1/alerts/active`, {
        signal: AbortSignal.timeout(5000),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      liveCollection = await response.json();
      assert.equal(liveCollection.type, "FeatureCollection");
      assert.ok(Array.isArray(liveCollection.features));
    } catch (error) {
      if (process.env.ALERTS_BACKEND_BASE_URL)
        reports.push({
          name: "TC-15 current backend smoke",
          status: "FAIL",
          error: error.message,
        });
      else
        reports.push({
          name: "TC-15 current backend smoke",
          status: "SKIP",
          reason: `No current Alerts API at ${backendBase}: ${error.message}`,
        });
    }
  if (liveCollection)
    await scenario(
      "TC-15 real backend active/detail read contract (isolated published fixture; no generation)",
      async () => {
        const { page, state } = await setup({ liveBackend: backendBase });
        await open(page);
        if (!liveCollection.features.length) {
          await shown(page.getByTestId("alert-empty"));
          reports.push({
            name: "TC-15 backend detail with active data",
            status: "SKIP",
            reason:
              "The backend has no active alerts; only the empty collection was verified.",
          });
          return;
        }
        await shown(list(page));
        const feature = liveCollection.features[0];
        assert.equal(
          await list(page).locator("[data-alert-id]").count(),
          liveCollection.features.length,
        );
        await select(page, feature.id);
        const response = await fetch(
          `${backendBase}/api/v1/alerts/${encodeURIComponent(feature.id)}`,
          { signal: AbortSignal.timeout(5000) },
        );
        assert.equal(response.status, 200);
        const payload = await response.json();
        const liveDetail = payload.data;
        await contains(detail(page), liveDetail.title);
        await contains(detail(page), liveDetail.ai_explanation);
        const text = await detail(page).innerText();
        assert.ok(text.includes(feature.id));
        for (const violation of liveDetail.factual_snapshot?.violations || []) {
          for (const value of [
            violation.measured_value,
            violation.operator,
            violation.threshold_value,
            violation.unit,
          ])
            assert.ok(text.includes(String(value)));
        }
        assert.deepEqual(state.pageErrors, []);
        await writeFile(
          path.join(evidenceDir, "live-active.json"),
          JSON.stringify(liveCollection, null, 2),
        );
        await writeFile(
          path.join(evidenceDir, "live-detail.json"),
          JSON.stringify(payload, null, 2),
        );
        await page.screenshot({
          path: path.join(evidenceDir, "backend-smoke-detail.png"),
        });
      },
    );
} finally {
  await writeFile(
    path.join(evidenceDir, "network-console.json"),
    JSON.stringify(traces, null, 2),
  );
  await writeFile(
    path.join(evidenceDir, "results.json"),
    JSON.stringify(
      {
        checkedAt: new Date().toISOString(),
        frontend: baseUrl,
        fixtureRoutes: true,
        alertsBackend: process.env.ALERTS_BACKEND_BASE_URL || null,
        script: fileURLToPath(import.meta.url),
        reports,
      },
      null,
      2,
    ),
  );
  await Promise.all(contexts.map((context) => context.close()));
  await browser.close();
}
const failed = reports.filter((report) => report.status === "FAIL").length;
const skipped = reports.filter((report) => report.status === "SKIP").length;
console.log(
  `${reports.length - failed - skipped} PASS, ${failed} FAIL, ${skipped} SKIP; evidence ${evidenceDir}`,
);
process.exitCode = failed || reports.length === 0 ? 1 : 0;
