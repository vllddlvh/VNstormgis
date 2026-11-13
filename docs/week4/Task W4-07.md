# Task W4-07 — WireMock Gemini faults và threshold violation IT

- **Vai trò:** QA/DevOps và AI/Backend thực hiện; Tech Lead nghiệm thu theo [RACI Tuần 4](./README.md#3-raci). **Ước lượng:** 3 SP đề xuất.
- **Trạng thái:** Đã triển khai và kiểm chứng trong commit `b2eebce`; xem [báo cáo W4-07](./W4-07%20-%20Verification%20report.md). Tech Lead chưa nghiệm thu; W4-05 vẫn REVIEW do giới hạn explanation 4000 ký tự.
- **Nguồn:** [kế hoạch Tuần 4](./README.md), [W4-02](./Task%20W4-02.md), [W4-03](./Task%20W4-03.md), [W4-04](./Task%20W4-04.md), [W4-05](./Task%20W4-05.md), [báo cáo W4-05](./W4-05%20-%20Verification%20report.md), [SRS](../The%20plans%20of%20project/4.%20SRS.md) SR-ALT-002, SR-AGT-001/002/003, SYS-RULE-003/004, AC-ALT-002, AC-AGT-PRO-001/002/003; [BRD](../The%20plans%20of%20project/3.%20BRD.md) BR-RULE-004; [ERD](../The%20plans%20of%20project/1.%20ERD.md); [API docs](../The%20plans%20of%20project/2.%20API%20docs.md).

## 1. Mục tiêu và điều kiện bắt đầu

Chứng minh bằng integration test rằng dữ liệu PostGIS thật đi qua **threshold evaluation → `ThresholdViolatedEvent` → inbox `PENDING` → Gemini/guardrail hoặc fallback → `READY` → alert đã công bố → hai GET API**. Test phải kiểm tra số đo, nguồn, quan hệ violation keys, nội dung và cờ `is_fallback` ở cả DB lẫn response. Unit test `GeminiClient` hoặc seed trực tiếp một alert hoàn chỉnh không thay thế bằng chứng này.

W4-07 chỉ bắt đầu nghiệm thu khi W4-02→05 chạy được tại cùng SHA, đặc biệt worker claim/lease/fencing, fallback và READY retry của W4-05. [Báo cáo W4-05](./W4-05%20-%20Verification%20report.md) hiện còn **REVIEW**: snapshot có quá nhiều vi phạm có thể vượt giới hạn explanation 4000 ký tự và bị cách ly. Ghi test cùng kết quả thực tế cho biên này; không dùng W4-07 để tự tuyên bố W4-05 DONE. Nếu chưa có cách xử lý được duyệt, kết luận nghiệm thu toàn pipeline phải ghi giới hạn/blocker và owner.

Chạy offline bằng Docker/Testcontainers PostgreSQL + PostGIS (và Redis nếu Spring context cần), WireMock cổng động cho Gemini. Không cần key/quota Gemini thật; mọi outbound AI request phải đi vào WireMock. Dữ liệu test không được dùng DB dev hoặc cảnh báo thời tiết thật. Đối chiếu hiện trạng trước khi viết test: `AbstractPostGisIntegrationTest`, `AlertEventIntakeIT`, `AlertPublicationIT`, `AlertApiIT`, `GeminiClientTest` và `AlertGenerationWorkerTest`; tái dùng case đã có nếu nó thật sự đi qua layer cần chứng minh.

## 2. Thiết kế test và fixture

1. Thêm lớp tích hợp trong package `vn.weathergis.alert` (tên dự kiến `AlertPipelineIT` và `AlertGeminiFaultIT`). Test path chuẩn phải gọi evaluator/service thật từ `weather_records`, threshold, location và storm đã lưu; listener tạo job thật; worker chạy qua cổng production; đọc GET bằng HTTP/MockMvc với Spring context thật. Chỉ stub HTTP Gemini và nguồn dữ liệu ngoài phạm vi. Test GIS dùng PostGIS, không mock kết quả `ST_DistanceSphere`.
2. Fixture tách theo test: một location có tọa độ `[lng, lat]`, bản ghi thời tiết có source timestamp cố định, threshold enabled/disabled, storm trong/ngoài bán kính và response Gemini hợp lệ theo ngữ pháp validator W4-05. Dùng số đo khác nhau để phát hiện tráo `rain_1h`/`rain_3h`, gió/gió giật, giá trị đo/ngưỡng và đơn vị. Ghi expected violation keys.
3. Cấu hình worker chủ động hoặc lịch ngắn được kiểm soát. Chặn scheduler nền gây race với test; khởi chạy claim/publish qua entry point production. Reset WireMock mappings, request journal, DB rows và state giữa các case. Dùng polling có timeout hữu hạn cho trạng thái bất đồng bộ; dùng latch/barrier cho race.
4. Response WireMock gồm draft hợp lệ, timeout vượt deadline, phản hồi nhỏ giọt, 429, 500/503, connection reset, JSON lỗi, thiếu trường, safety block, draft sai số/ngữ nghĩa và nội dung quá dài. Verify request có `[FACTUAL_SYSTEM_DATA]`, số đo/ngưỡng gốc, model cấu hình, `temperature=0.2`; không lưu key hoặc prompt chứa bí mật vào evidence. Kiểm số lần gọi để bắt HTTP retry ngầm.
5. Với mọi case có công bố, assert đồng thời: job `COMPLETED`, đúng một alert, title/explanation không rỗng, `trigger_reason` và `factual_snapshot` đúng, mọi key thuộc đúng alert, severity/threshold đại diện đúng, `is_fallback` đúng, `GET /api/v1/alerts/active` và `GET /api/v1/alerts/{id}` nhất quán. Với draft bị loại, xác nhận DB và hai GET không chứa draft đó.

## 3. Ma trận kiểm thử bắt buộc

| ID | Đầu vào / fault | Bằng chứng cần có |
| :--- | :--- | :--- |
| IT-01 | Dưới ngưỡng, threshold disabled, số đo bắt buộc null | Không tạo violation/job/alert giả; Gemini không được gọi. |
| IT-02 | Mưa và gió tại hai phía của `>`, `>=`, `<`, `<=`, `=` | Đúng chỉ số, giá trị, đơn vị, toán tử và keys; draft hợp lệ thành `is_fallback=false`. |
| IT-03 | Storm trùng vị trí trạm, trong và ngoài bán kính | PostGIS thật tính `ST_DistanceSphere / 1000.0`; 0 km và biên đúng; không đảo tọa độ. |
| IT-04 | Một trạm có nhiều vi phạm cùng thời điểm | Một job/alert cho keys mới; max severity và tie-break đúng; đủ dữ kiện trong snapshot/explanation. |
| IT-05 | Gemini vượt deadline 5 s, kể cả body nhỏ giọt | Client dừng trong tolerance công bố; fallback được công bố/GET; không retry HTTP ngầm. Đo riêng thời gian AI và chờ worker. |
| IT-06 | 429, 500, 503 và connection reset | Mỗi fault có case riêng; fallback dùng snapshot; job sau vẫn được xử lý. |
| IT-07 | JSON sai, thiếu field, safety block, output quá dài | Draft không được lưu/công bố; fallback đầy đủ hoặc job cách ly an toàn khi không thể render đủ căn cứ. |
| IT-08 | Sai số, tham số, đo/ngưỡng, đơn vị, trạm/bão; dự báo bịa | Guardrail bác title và explanation; bản công khai chỉ chứa fallback đã xác minh. |
| IT-09 | Gemini disabled hoặc thiếu cấu hình | Không gọi mạng; snapshot hợp lệ sinh fallback; weather sync không crash vì AI. |
| IT-10 | Snapshot thiếu/sai dữ kiện; nhiều vi phạm vượt 4000 ký tự | Không điền `0` hay cắt căn cứ; job có trạng thái/lý do/cách ly chẩn đoán được; ghi giới hạn W4-05. |
| IT-11 | Replay `[A,B]`, rồi `[A,B,C]`, sau resolved/dismissed | A/B không nhân bản; job mới chỉ có C; kiểm DB và quan hệ keys. |
| IT-12 | Hai worker claim; lease hết hạn, response cũ về sau reclaim | Fencing chặn worker cũ ghi READY; một chủ sở hữu/key, một alert; không hứa đúng một lần gọi Gemini. |
| IT-13 | Crash/lỗi trước READY, sau READY, giữa insert alert/gắn keys | Lease/retry phục hồi; rollback nguyên tử; READY retry không gọi lại Gemini. |
| IT-14 | Đổi/xóa threshold, location, storm sau intake | Snapshot và căn cứ bất biến; FK nullable không làm mất alert/key hay thay số lịch sử. |
| IT-15 | Active/detail, empty, legacy geometry null, UUID sai/không tồn tại | GeoJSON `[lng, lat]`, detail và public read đúng hợp đồng; lỗi RFC 7807 với `ALR_*`. |

Ma trận là yêu cầu về **hành vi**, không bắt buộc mỗi dòng đúng một method. Trong báo cáo ghi `ID → class#method → dữ liệu/fault → assert DB/API → kết quả` và chỉ tham chiếu test cũ khi test đó chạy tại SHA nghiệm thu. Các test W4-04/05 vẫn có phạm vi nghiệm thu riêng.

## 4. Ranh giới giao nhận và cách đọc kết quả

- Inbox bền vững chỉ bắt đầu **sau intake commit**. Inject lỗi DB trước commit, xác nhận `AI_MONITOR = FAILED` theo luồng hiện có và dữ liệu ingestion đã commit không bị rollback. Kiểm re-evaluation cùng source timestamp cho key `v1:` ổn định. Đây không phải bằng chứng phục hồi snapshot cũ nếu nguồn đã đổi; ghi owner/phương án replay hoặc outbox để W4-08/W4-10 quyết định.
- Phân biệt **timeout Gemini 5 s** với độ trễ toàn chuỗi; worker polling, hàng đợi và DB làm tổng thời gian dài hơn. Không dùng assertion “pipeline < 5 s” cho AC-AGT-PRO-003. Ghi tolerance và phép đo timeout client.
- “100% alert qua guardrail” nghĩa là mọi **đường ghi đang kiểm** xác minh draft hoặc dùng fallback; fixture không chứng minh mọi văn bản tự do của model đều an toàn. Ngữ pháp đóng W4-05 có thể từ chối prose tự nhiên nêu trong SRS; ghi sai khác sản phẩm, không gọi một draft hợp lệ giả lập là chất lượng Gemini live.
- Fault test không phát sinh request mạng thật, không log API key và không mở endpoint production để bơm lỗi. CI nightly, notification và lưu log thuộc W4-08.

## 5. Lệnh kiểm chứng và tiêu chí hoàn thành

Chạy từ `VNstormgis-Backend/`, với Docker đang hoạt động:

```bash
./gradlew test --tests 'vn.weathergis.alert.AlertPipelineIT' --tests 'vn.weathergis.alert.AlertGeminiFaultIT'
./gradlew spotlessCheck test bootJar
```

Điều chỉnh tên lớp theo implementation; không để lệnh focused chọn 0 test. Lưu command, exit code, số test/fail/error/skip và XML/HTML report path. Chạy lặp lại nhóm concurrency/recovery để phát hiện flaky; sửa nguyên nhân thay vì che bằng retry. Live Gemini smoke là opt-in, ghi riêng và không là điều kiện PASS của suite offline.

- [ ] IT-01→15 có mapping/evidence, gồm normal và fallback từ evaluation đến GET.
- [ ] PostgreSQL/PostGIS, worker, guardrail, persistence và controller thật được thực thi ở các test end-to-end; external Gemini call dùng WireMock.
- [ ] Focused suite và `spotlessCheck test bootJar` PASS tại cùng SHA; ghi mọi skip.
- [ ] Concurrency, recovery, replay, null data, giới hạn 4000 ký tự và khoảng trước inbox commit có kết luận đúng mức bằng chứng.
- [ ] Coverage ≥80% theo DoD chỉ đánh dấu khi có công cụ, phạm vi và báo cáo đo thực; nếu chưa có thì ghi thiếu.
- [ ] Báo cáo ghi blocker W4-05, sai khác với SRS, owner và bước xử lý; Tech Lead nghiệm thu sau khi xem evidence.

**Bàn giao:** `docs/week4/W4-07 - Verification report.md`, test/fixture WireMock, lệnh tái chạy và bảng coverage. Commit triển khai dự kiến: `test(alert): cover threshold pipeline and Gemini failures [W4-07]`. Ngoài phạm vi: chatbot, UI, đổi model, load test 500 CCU, CI nightly và tuyên bố zero event loss khi chưa có outbox.
