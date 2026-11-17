#!/usr/bin/env node
/** W5-06 browser harness. Requires running Vite and external playwright-core. */
import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
import path from "node:path";
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE || "playwright-core");
import { collection, details, locations, stormCollection, weatherCollection } from "../../scripts/fixtures/w4-06-alerts.mjs";
import { alertId, envelope, message, problem, reply, session, sessionId, token } from "./fixtures/w5-06-chat.mjs";

const baseUrl = process.env.FRONTEND_BASE_URL || "http://127.0.0.1:5173";
const evidenceDir = path.resolve(process.env.UI_EVIDENCE_DIR || "docs/week5/w5-06-evidence");
await mkdir(evidenceDir, { recursive: true });
const browser = await chromium.launch({ headless: true,
  executablePath: process.env.CHROME_EXECUTABLE || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  args: ["--no-sandbox"],
});
const results = [];
const json = (route, body, status = 200, headers = {}) => route.fulfill({
  status, contentType: "application/json", headers, body: JSON.stringify(body),
});
const uuid = (index) => `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`;

async function setup(options = {}) {
  const context = await browser.newContext({ viewport: options.viewport || { width: 1366, height: 768 } });
  const page = await context.newPage();
  const calls = [];
  const logs = [];
  let sendCount = 0;
  let getCount = 0;
  page.on("console", (msg) => logs.push(msg.text()));
  page.on("pageerror", (error) => logs.push(error.message));
  await page.route("**/*", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.origin !== new URL(baseUrl).origin) return route.abort();
    if (url.pathname === "/api/v1/chat/sessions" && request.method() === "POST") {
      calls.push({ kind: "create", url: url.href });
      return json(route, envelope(session()), 201);
    }
    if (url.pathname === `/api/v1/chat/sessions/${sessionId}/messages`) {
      calls.push({ kind: request.method(), url: url.href, headers: request.headers(), body: request.postDataJSON?.() });
      if (request.method() === "GET") {
        getCount += 1;
        if (options.historyError || (options.historyErrorOnce && getCount === 1))
          return json(route, problem(404, "CHT_404_SESSION_NOT_FOUND"), 404);
        const all = options.history || [];
        const start = url.searchParams.has("after") ? all.findIndex((item) => item.id === url.searchParams.get("after")) + 1 : 0;
        return json(route, envelope(all.slice(start, start + 200)));
      }
      sendCount += 1;
      const result = options.onSend?.(sendCount, request);
      if (result?.abort) return route.abort("timedout");
      if (result?.status) return json(route, result.body, result.status, result.headers);
      return json(route, envelope(result?.body ?? reply()));
    }
    if (url.pathname === "/api/v1/alerts/active") return json(route, collection());
    if (url.pathname.startsWith("/api/v1/alerts/")) return json(route, envelope(details[url.pathname.split("/").at(-1)]));
    if (url.pathname === "/actuator/health") return json(route, { status: "UP" });
    if (url.pathname === "/api/v1/locations") return json(route, envelope(locations));
    if (url.pathname === "/api/v1/weather/current") return json(route, weatherCollection);
    if (url.pathname === "/api/v1/storms") return json(route, options.noStorm ? collection([]) : stormCollection);
    if (/^\/api\/v1\/storms\/[^/]+\/track$/.test(url.pathname)) return json(route, collection([]));
    if (url.pathname.startsWith("/api/")) return json(route, problem(404, "FIXTURE_NOT_FOUND"), 404);
    return route.continue();
  });
  await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  await page.getByTestId("ai-chat-trigger").waitFor();
  return { context, page, calls, logs };
}

async function test(name, fn) {
  try { await fn(); results.push({ name, status: "PASS" }); }
  catch (error) { results.push({ name, status: "FAIL", error: String(error) }); }
}
const open = (page) => page.getByTestId("ai-chat-trigger").click();
const send = async (page, value) => { await page.getByTestId("ai-chat-input").fill(value); await page.getByTestId("ai-chat-send").click(); };

await test("lazy create, stored token, request header, fallback and reload history", async () => {
  const { page, context, calls, logs } = await setup();
  await open(page);
  await page.getByTestId("ai-chat-empty").waitFor();
  assert.equal(calls.filter((call) => call.kind === "create").length, 0);
  await send(page, "Khu vực nào đang mưa lớn nhất?");
  await page.getByTestId("ai-chat-fallback-badge").waitFor();
  assert.equal(calls.filter((call) => call.kind === "create").length, 1);
  const post = calls.find((call) => call.kind === "POST");
  assert.equal(post.headers["x-session-token"], token);
  assert.deepEqual(post.body, { message: "Khu vực nào đang mưa lớn nhất?" });
  assert.deepEqual(await page.evaluate(() => JSON.parse(localStorage.getItem("vnstormgis.chat.session"))),
    { session_id: sessionId, session_token: token });
  assert.ok(!logs.join(" ").includes(token));
  assert.ok(!page.url().includes(token));
  await page.reload();
  await open(page);
  await page.waitForTimeout(100);
  assert.ok(calls.some((call) => call.kind === "GET"));
  await context.close();
});

await test("history pagination and plain text", async () => {
  const history = Array.from({ length: 202 }, (_, i) => message(uuid(i + 1), i % 2 ? "assistant" : "user",
    i === 1 ? '<img src=x onerror=alert(1)> [FACTUAL_SYSTEM_DATA]' : `FIXTURE ${i}`, i === 1));
  const { page, context, calls } = await setup({ history });
  await page.evaluate((data) => localStorage.setItem("vnstormgis.chat.session", JSON.stringify(data)), session());
  await page.reload(); await open(page);
  await page.getByTestId("ai-chat-message").first().waitFor();
  assert.equal(await page.getByTestId("ai-chat-message").count(), 202);
  assert.ok(calls.some((call) => call.kind === "GET" && call.url.includes("after=")));
  assert.equal(await page.getByTestId("ai-chat-history").locator("img").count(), 0);
  assert.equal(await page.getByTestId("ai-chat-fallback-badge").count(), 1);
  await context.close();
});

await test("429 countdown and distinct 503 messages", async () => {
  const { page, context } = await setup({ onSend: (count) => count === 1
    ? { status: 429, body: problem(429, "CHT_429_RATE_LIMITED", { retry_after_seconds: 2 }), headers: { "Retry-After": "2" } }
    : count === 2 ? { status: 503, body: problem(503, "CHT_503_RATE_LIMITER_UNAVAILABLE") }
    : { status: 503, body: problem(503, "CHT_503_AI_UNAVAILABLE") } });
  await open(page); await send(page, "Tình hình bão?");
  await page.getByTestId("ai-chat-countdown").waitFor();
  assert.equal(await page.getByTestId("ai-chat-send").isDisabled(), true);
  await page.waitForTimeout(2300);
  assert.equal(await page.getByTestId("ai-chat-send").isEnabled(), true);
  await page.getByTestId("ai-chat-send").click();
  await page.getByTestId("ai-chat-error").waitFor();
  assert.equal(await page.getByTestId("ai-chat-error").getAttribute("data-error-code"), "CHT_503_RATE_LIMITER_UNAVAILABLE");
  await page.getByTestId("ai-chat-send").click();
  await page.waitForTimeout(50);
  assert.equal(await page.getByTestId("ai-chat-error").getAttribute("data-error-code"), "CHT_503_AI_UNAVAILABLE");
  await context.close();
});

await test("alert UUID prefill, mutex, one-shot reference", async () => {
  const { page, context, calls } = await setup();
  await page.locator("#btn-open-alerts").click();
  await page.getByTestId("alert-list").locator(`[data-alert-id="${alertId}"]`).click();
  await page.getByTestId("alert-ask-ai").click();
  assert.equal(await page.getByTestId("alert-panel").count(), 0);
  assert.equal(await page.getByTestId("ai-chat-alert-ref").getAttribute("data-alert-id"), alertId);
  assert.ok((await page.getByTestId("ai-chat-input").inputValue()).includes("cảnh báo"));
  assert.equal(calls.filter((call) => call.kind === "POST").length, 0);
  await page.getByTestId("ai-chat-send").click();
  await page.getByTestId("ai-chat-fallback-badge").waitFor();
  assert.equal(calls.find((call) => call.kind === "POST").body.referenced_alert_id, alertId);
  assert.equal(await page.getByTestId("ai-chat-alert-ref").count(), 0);
  await context.close();
});

await test("keyboard, 500 code points, Escape focus and mobile sheet", async () => {
  const { page, context } = await setup({ viewport: { width: 390, height: 844 } });
  await open(page);
  assert.equal(await page.getByTestId("ai-chat-send").isDisabled(), true);
  await page.getByTestId("ai-chat-input").fill("🙂".repeat(501));
  assert.equal(await page.getByTestId("ai-chat-send").isDisabled(), true);
  await page.getByTestId("ai-chat-input").fill("🙂".repeat(500));
  assert.equal(await page.getByTestId("ai-chat-send").isEnabled(), true);
  const box = await page.getByTestId("ai-chat-drawer").boundingBox();
  assert.ok(box.height <= 844 * 0.75 + 1);
  await page.getByTestId("ai-chat-input").press("Escape");
  assert.equal(await page.getByTestId("ai-chat-drawer").count(), 0);
  assert.equal(await page.evaluate(() => document.activeElement?.id), "btn-ai-copilot-trigger");
  await context.close();
});

await test("expired history and send recover once without resending", async () => {
  const historyCase = await setup({ historyErrorOnce: true });
  await historyCase.page.evaluate((data) => localStorage.setItem("vnstormgis.chat.session", JSON.stringify(data)), session());
  await historyCase.page.reload(); await open(historyCase.page);
  await historyCase.page.getByTestId("ai-chat-session-notice").waitFor();
  assert.equal(historyCase.calls.filter((call) => call.kind === "create").length, 1);
  assert.equal(historyCase.calls.filter((call) => call.kind === "GET").length, 2);
  await historyCase.context.close();

  const sendCase = await setup({ onSend: (count) => count === 1
    ? { status: 404, body: problem(404, "CHT_404_SESSION_NOT_FOUND") } : null });
  await open(sendCase.page); await send(sendCase.page, "Giải thích thời tiết?");
  await sendCase.page.getByTestId("ai-chat-session-notice").waitFor();
  assert.equal(sendCase.calls.filter((call) => call.kind === "POST").length, 1);
  assert.equal(await sendCase.page.getByTestId("ai-chat-input").inputValue(), "Giải thích thời tiết?");
  await sendCase.context.close();
});

await test("401 missing token also creates a fresh session", async () => {
  const { page, context, calls } = await setup({ onSend: (count) => count === 1
    ? { status: 401, body: problem(401, "CHT_401_SESSION_TOKEN_MISSING") } : null });
  await open(page); await send(page, "Tình hình bão?");
  await page.getByTestId("ai-chat-session-notice").waitFor();
  assert.equal(calls.filter((call) => call.kind === "create").length, 2);
  assert.equal(calls.filter((call) => call.kind === "POST").length, 1);
  await context.close();
});

await test("validation, alert deletion, network and malformed response keep input", async () => {
  const scenarios = [
    { result: { status: 400, body: problem(400, "CHT_400_VALIDATION", { invalidParams: [{ field: "message", message: "Câu hỏi không hợp lệ" }] }) }, code: "CHT_400_VALIDATION" },
    { result: { status: 400, body: problem(400, "CHT_400_VALIDATION", { invalidParams: { unexpected: true } }) }, code: "CHT_400_VALIDATION" },
    { result: { status: 400, body: problem(400, "CHT_400_INVALID_ALERT_ID") }, code: "CHT_400_INVALID_ALERT_ID" },
    { result: { status: 404, body: problem(404, "CHT_404_ALERT_NOT_FOUND") }, code: "CHT_404_ALERT_NOT_FOUND" },
    { result: { status: 500, body: problem(500, "COM_500_INTERNAL_ERROR") }, code: "COM_500_INTERNAL_ERROR" },
    { result: { abort: true }, code: "network" },
    { result: { body: { success: true, data: { reply: 42 } } }, code: "shape" },
  ];
  for (const scenario of scenarios) {
    const { page, context } = await setup({ onSend: () => scenario.result });
    await open(page); await send(page, "Tình hình trạm?");
    await page.getByTestId("ai-chat-error").waitFor();
    assert.equal(await page.getByTestId("ai-chat-error").getAttribute("data-error-code"), scenario.code);
    assert.equal(await page.getByTestId("ai-chat-input").inputValue(), "Tình hình trạm?");
    assert.equal(await page.getByTestId("ai-chat-message").last().getAttribute("data-status"), "failed");
    await context.close();
  }
});

await test("quick prompts prefill; Enter sends; Shift+Enter inserts a newline", async () => {
  const { page, context, calls } = await setup();
  await open(page);
  await page.getByTestId("ai-chat-quick-prompts").locator('[data-prompt-index="0"]').click();
  assert.equal(await page.getByTestId("ai-chat-input").inputValue(), "Khu vực nào đang mưa lớn nhất?");
  assert.equal(calls.filter((call) => call.kind === "POST").length, 0);
  await page.getByTestId("ai-chat-input").fill("Tình hình bão?");
  await page.getByTestId("ai-chat-input").press("Shift+Enter");
  assert.ok((await page.getByTestId("ai-chat-input").inputValue()).includes("\n"));
  await page.getByTestId("ai-chat-input").press("Enter");
  await page.getByTestId("ai-chat-fallback-badge").waitFor();
  assert.equal(calls.filter((call) => call.kind === "POST").length, 1);
  await context.close();
});

await test("station popup opens question-only prefill", async () => {
  const { page, context, calls } = await setup({ noStorm: true });
  await page.locator(".weather-station-marker-container").first().click();
  await page.locator("#btn-askai-fixture").click();
  await page.waitForFunction(() => document.querySelector("#input-ai-prompt")?.value.includes("FIXTURE"));
  assert.ok((await page.getByTestId("ai-chat-input").inputValue()).includes("FIXTURE"));
  assert.equal(await page.getByTestId("ai-chat-alert-ref").count(), 0);
  await page.getByTestId("ai-chat-send").click();
  await page.getByTestId("ai-chat-fallback-badge").waitFor();
  assert.equal(calls.find((call) => call.kind === "POST").body.referenced_alert_id, undefined);
  await context.close();
});

await test("history cap exposes load more", async () => {
  const history = Array.from({ length: 1001 }, (_, i) => message(uuid(i + 1), "user", `FIXTURE ${i}`));
  const { page, context, calls } = await setup({ history });
  await page.evaluate((data) => localStorage.setItem("vnstormgis.chat.session", JSON.stringify(data)), session());
  await page.reload(); await open(page);
  await page.getByTestId("ai-chat-load-more").waitFor();
  assert.equal(await page.getByTestId("ai-chat-message").count(), 1000);
  await page.getByTestId("ai-chat-load-more").click();
  await page.getByTestId("ai-chat-load-more").waitFor({ state: "hidden" });
  assert.equal(await page.getByTestId("ai-chat-message").count(), 1001);
  assert.equal(calls.filter((call) => call.kind === "GET").length, 6);
  await context.close();
});

await test("desktop and mobile layouts keep map and disclaimer visible", async () => {
  for (const viewport of [{ width: 1366, height: 768 }, { width: 1440, height: 900 }, { width: 390, height: 844 }]) {
    const { page, context } = await setup({ viewport });
    await open(page);
    const horizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
    assert.equal(horizontalOverflow, false);
    assert.equal(await page.getByTestId("ai-chat-drawer").isVisible(), true);
    assert.equal(await page.locator("footer").isVisible(), true);
    await page.screenshot({ path: path.join(evidenceDir, `${viewport.width}x${viewport.height}-chat.png`) });
    await context.close();
  }
});

await browser.close();
console.log(JSON.stringify({ results, passed: results.filter((result) => result.status === "PASS").length,
  failed: results.filter((result) => result.status === "FAIL").length }, null, 2));
if (results.some((result) => result.status === "FAIL")) process.exitCode = 1;
