# W5-08 Nightly E2E & Chat Operations Runbook

## 1. Phạm vi và lịch chạy

Workflow `.github/workflows/week5-e2e-nightly.yml` chạy tự động hàng ngày lúc **02:40 UTC (09:40 giờ Việt Nam, UTC+7)** — cách 20 phút sau workflow Tuần 4 (`02:20 UTC`) để tránh tranh chấp tài nguyên — và hỗ trợ kích hoạt thủ công qua `workflow_dispatch` (kèm cờ `failure_probe`). Theo quyết định **D-W3-10-05**, workflow này **không** chạy trên `pull_request` và không phải là required check chặn merge PR.

Mỗi lần chạy khởi tạo môi trường Docker Compose cô lập (PostgreSQL 16 + PostGIS 3.4, Redis 7.2) với mật khẩu ngẫu nhiên, khởi chạy backend ở profile `dev` (`--weathergis.sync.enabled=false`, `--weathergis.alert.retry.enabled=false`, `--weathergis.chat.session.cleanup.enabled=false`, `GEMINI_ENABLED=false`) và gọi `.github/scripts/verify-week5-pipeline.sh` (hoặc wrapper cục bộ `scripts/verify-week5-pipeline.sh`).

## 2. Các cổng kiểm định (`G-01` → `G-05`)

1. **G-01 (Preflight & Health):** Kiểm tra công cụ (`bash`, `curl`, `jq`, `java` 21, `node`, `npm`, `docker`, `python3`, `git`), trạng thái Docker daemon và chờ `/actuator/health` trả `UP`.
2. **G-02 (Focused Chat IT):** Chạy `ChatIntegrationAndSecurityIT` (11 kịch bản đầu-cuối: cô lập phiên, validation/bbox, top mưa/gió khớp DB, trạm/bão active, snapshot alert bất biến & cắt 75-rule, DB rỗng, prompt injection, hallucination 5 intents, Gemini faults/timeout, rate limit Redis/concurrency/XFF/fail-closed, cursor history & rollback nguyên tử), `ChatRateLimitIT` (6 test) và `ChatGroundedReplyIT` (13 test).
3. **G-03 (Full Quality Gate & Coverage):** Chạy `./gradlew spotlessCheck test bootJar jacocoTestReport --no-daemon`, xác nhận 0 failure/error và độ phủ dòng lệnh JaCoCo của `vn/weathergis/chat` ≥ 80%; chạy `npm run type-check && npm run lint && npm run build` cho frontend.
4. **G-04 (Isolated Live HTTP Smoke):** Kiểm tra 4 endpoint GeoJSON bản đồ/cảnh báo, 6 nhánh lỗi RFC 7807 `CHT_*` (`401`, `404` phiên/alert, `400` validation/alert UUID/bbox), tạo phiên `201`, gửi 10 câu hỏi hợp lệ nhận `200` fallback tất định, câu hỏi thứ 11 nhận `429 CHT_429_RATE_LIMITED` kèm `Retry-After` khớp `retry_after_seconds`, và phân trang cursor `GET /messages` trả đủ 20 tin nhắn không lộ `session_token`/`token_hash`/`tokens_used`.
5. **G-05 (UI Contract & Map → Alert → Chat):** Kiểm tĩnh hợp đồng UI (`AiChatDrawer.tsx`, `AlertDetail.tsx`, `useChatSession.ts`, `chatService.ts`) và chạy tự động 2 bộ Playwright (`VNstormgis-Frontend/tests/test-w5-06-ui.mjs` + `scripts/test-w4-06-ui.mjs`) khi biến `PLAYWRIGHT_MODULE` và `FRONTEND_BASE_URL` khả dụng.

## 3. Vận hành Rate Limiter, Trusted Proxy và Redis Eviction (`D-W5-05-01/02`)

| Cấu hình / Metric | Giá trị mặc định | Hướng dẫn vận hành & xử lý sự cố |
| :--- | :--- | :--- |
| `CHAT_RATELIMIT_SESSION_LIMIT` / `CHAT_RATELIMIT_IP_LIMIT` / `CHAT_RATELIMIT_WINDOW` | `10` / `30` / `60s` | Hai bucket cố định 60s trên Redis (`weathergis:ratelimit:chat:s:{token_hash}` và `...:ip:{ip}`). Không đổi nếu chưa có quyết định PO/Tech Lead mới. |
| `CHAT_RATELIMIT_TRUSTED_PROXIES` | Rỗng (`""`) | Mặc định không tin `X-Forwarded-For` để chống giả mạo IP. Khi triển khai sau Nginx reverse proxy, **bắt buộc** cấu hình IP/CIDR của container/máy chủ Nginx để không gom toàn bộ người dùng vào một bucket IP proxy. |
| `weathergis.chat.ratelimit.outage` & `CHT_503_RATE_LIMITER_UNAVAILABLE` | Counter Micrometer | Khi Redis không phản hồi trong `spring.data.redis.timeout` (500ms), hệ thống **fail-closed** (`503 CHT_503_RATE_LIMITER_UNAVAILABLE`) để bảo vệ ngân sách Gemini token. Kiểm tra `docker inspect weathergis-redis`, kết nối mạng và bộ nhớ Redis; sau khi Redis sống lại, limiter tự phục hồi không cần khởi động lại backend. |
| Chính sách bộ nhớ Redis (`allkeys-lru` 128 MiB — `D-W5-05-02`) | `128mb` / `allkeys-lru` | Dưới áp lực bộ nhớ cao, Redis có thể evict khóa `weathergis:ratelimit:chat:*` trước khi hết TTL 61s (làm mới sớm cửa sổ quota). Giám sát `evicted_keys` của Redis; trước Go-Live (Tuần 6–7), Tech Lead + DevOps chọn: (a) tăng `maxmemory` + đổi sang `volatile-lru`, hoặc (b) tách DB/instance cho rate limit. |
| `weathergis.chat.guardrail.rejected` & `weathergis.chat.fallback` | Counter theo tag `(intent, reason)` | Tăng đột biến ở `reason=schema`/`number_mismatch`/`entity_unknown` báo hiệu model drift hoặc prompt injection; tăng ở `reason=ai_error` báo hiệu lỗi/timeout/quota phía Gemini. |

## 4. Cấu hình CI Nightly và đối soát W4-08

1. Cấu hình GitHub Actions secret `W5_FAILURE_WEBHOOK_URL` (và `W4_FAILURE_WEBHOOK_URL` cho workflow Tuần 4) trỏ tới HTTPS incoming webhook nhận payload JSON `{"text": "..."}`.
2. Kiểm tra chính sách lưu trữ artifact của repository/organization cho phép tối thiểu **30 ngày** (`retention-days: 30`).
3. Chạy thử `workflow_dispatch` với `failure_probe=false` (kỳ vọng xanh) và `failure_probe=true` (kỳ vọng đỏ tại `G-02` kèm thông báo webhook và artifact `summary.json`), sau đó ghi nhận 1 lần chạy `schedule` thực tế vào báo cáo nghiệm thu.
