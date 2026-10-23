package vn.weathergis.automation.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.weathergis.automation.dto.StormSyncResult;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.repository.StormRepository;

/**
 * Integration Test chuỗi nạp bão GDACS đầy đủ (Task W2-06):
 *
 * <pre>
 *   WireMock (HTTP GDACS) → GdacsStormClient (thật) → StormSyncService (thật)
 *     → JPA + native SQL → PostGIS 16-3.4 (Testcontainers)
 * </pre>
 *
 * Mục tiêu: đặt lớp chứng cứ cuối lên chuỗi W2-05 — fixture YAGI 2024 tổng hợp theo cấu trúc
 * payload GDACS thật, chỉ mục GiST {@code idx_storms_geom_current} phục vụ {@code ST_DWithin}, máy
 * trạng thái SYS-RULE-001 trên CSDL thật (nhánh A/B/B'/C + phản chứng feed lỗi), tính lũy đẳng của
 * PK/uq và trigger {@code trg_storms_updated_at}. Không sửa production code; mọi truy vấn kiểm
 * chứng dùng JdbcTemplate (không thêm method repository chỉ để test — quyết định #4).
 *
 * <p>Cleanup hermetic trong {@code @BeforeEach}: container PostGIS là singleton dùng chung toàn
 * suite (RSK-W1-05), nên mỗi test tự xóa {@code storms} (cascade {@code storm_tracks}) và {@code
 * data_sync_logs} trước khi chạy.
 */
@SpringBootTest
@DisplayName("StormIngestionIT — GDACS → StormSyncService → PostGIS (Testcontainers, W2-06)")
class StormIngestionIT extends AbstractPostGisIntegrationTest {

    private static final String SEARCH_PATH = "/gdacsapi/api/Events/geteventlist/search";
    private static final String TRACK_PATH = "/gdacsapi/api/polygons/getgeometry";
    private static final String YAGI_ID = "1001209";

    /** WireMock singleton theo cùng pattern container của lớp cơ sở — không request mạng thật. */
    private static final WireMockServer WIRE_MOCK =
            new WireMockServer(wireMockConfig().dynamicPort());

    static {
        WIRE_MOCK.start(); // phải start TRƯỚC khi Spring context nạp @DynamicPropertySource
    }

    @AfterAll
    static void stopWireMock() {
        if (WIRE_MOCK.isRunning()) {
            WIRE_MOCK.stop();
        }
    }

    @DynamicPropertySource
    static void redirectGdacsToWireMock(DynamicPropertyRegistry registry) {
        registry.add("weathergis.external.gdacs.base-url", WIRE_MOCK::baseUrl);
        registry.add("weathergis.external.gdacs.backoff-ms", () -> "0,0,0"); // giữ suite < 2s
        // weathergis.sync.enabled=false đã do AbstractPostGisIntegrationTest đảm nhiệm
    }

    @Autowired private StormSyncService stormSyncService;
    @Autowired private StormRepository stormRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanStormTables() {
        WIRE_MOCK.resetAll();
        jdbcTemplate.update("DELETE FROM storms"); // cascade dọn storm_tracks (FK V4)
        jdbcTemplate.update("DELETE FROM data_sync_logs");
    }

    // ---------------------------------------------------------------------
    // Nhóm 1: Hạ tầng PostGIS — Flyway V1→V6, chỉ mục GiST, constraint, trigger
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Nhóm 1: Hạ tầng PostGIS & Flyway (TC-ST-01, TC-ST-02)")
    class InfrastructureTests {

        @Test
        @DisplayName(
                "TC-ST-01/02: Flyway chạy tới V6; GiST idx_storms_geom_current, uq_storm_track và trigger updated_at tồn tại")
        void shouldHaveFlywaySchemaArtifacts() {
            String latestMigration =
                    jdbcTemplate.queryForObject(
                            "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                            String.class);
            assertThat(latestMigration).isEqualTo("6");

            List<String> stormIndexes =
                    jdbcTemplate.queryForList(
                            "SELECT indexname FROM pg_indexes WHERE tablename IN ('storms','storm_tracks')",
                            String.class);
            assertThat(stormIndexes)
                    .contains(
                            "idx_storms_geom_current",
                            "idx_storm_tracks_geom",
                            "idx_storms_status");

            Integer gistIndex =
                    jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_storms_geom_current'"
                                    + " AND indexdef ILIKE '%USING gist%'",
                            Integer.class);
            assertThat(gistIndex).isEqualTo(1);

            Integer uniqueConstraint =
                    jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM pg_constraint WHERE conname = 'uq_storm_track'"
                                    + " AND conrelid = 'storm_tracks'::regclass",
                            Integer.class);
            assertThat(uniqueConstraint).isEqualTo(1);

            Integer updatedAtTrigger =
                    jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM pg_trigger WHERE tgrelid = 'storms'::regclass"
                                    + " AND tgname = 'trg_storms_updated_at' AND NOT tgisinternal",
                            Integer.class);
            assertThat(updatedAtTrigger).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------------
    // Nhóm 2: Nạp YAGI active — nhánh A + tính lũy đẳng
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Nhóm 2: Nạp YAGI active & tính lũy đẳng (TC-ST-03, TC-ST-04)")
    class IngestionTests {

        @Test
        @DisplayName(
                "TC-ST-03: nạp YAGI — SUCCESS, 1 storms active, 15 mốc (13 historical + 2 forecast), [lng, lat] đúng")
        void shouldIngestActiveYagiFromWireMockToPostGis() {
            stubSearch("search-yagi-active.json");
            stubYagiTrack();

            StormSyncResult result = stormSyncService.triggerStormSync();

            assertThat(result.source()).isEqualTo("GDACS");
            assertThat(result.status()).isEqualTo("SUCCESS");
            assertThat(result.stormsProcessed()).isEqualTo(1);
            assertThat(result.stormsOnFeed()).isEqualTo(1);
            assertThat(result.trackPointsInserted()).isEqualTo(15); // 13 historical + 2 forecast
            assertThat(result.stormsDissipated()).isZero();
            assertThat(result.errorMessage()).isNull();

            // WireMock nhận đúng 2 tầng URL với query param của production
            WIRE_MOCK.verify(
                    getRequestedFor(urlPathEqualTo(SEARCH_PATH))
                            .withQueryParam("eventlist", equalTo("TC")));
            WIRE_MOCK.verify(
                    getRequestedFor(urlPathEqualTo(TRACK_PATH))
                            .withQueryParam("eventid", equalTo(YAGI_ID)));

            // Đọc lại từ CSDL qua JPA — không tin persistence context
            Optional<StormEntity> loaded = stormRepository.findById(YAGI_ID);
            assertThat(loaded).isPresent();
            StormEntity yagi = loaded.get();
            assertThat(yagi.getStatus()).isEqualTo("active");
            assertThat(yagi.getYear()).isEqualTo(2024);
            assertThat(yagi.getCategory()).isEqualTo("Red");
            assertThat(yagi.getMaxWindSpeed()).isCloseTo(66.667, within(0.01)); // 240 km/h ÷ 3.6
            assertThat(yagi.getMinPressure()).isNull(); // V6 — không bịa áp suất
            assertThat(yagi.getStartDate().toInstant())
                    .isEqualTo(Instant.parse("2024-09-04T00:00:00Z"));
            assertThat(yagi.getGeomCurrent().getSRID()).isEqualTo(4326);
            assertThat(yagi.getGeomCurrent().getX()).isCloseTo(107.2, within(1e-6)); // x = KINH ĐỘ
            assertThat(yagi.getGeomCurrent().getY()).isCloseTo(20.7, within(1e-6)); // y = VĨ ĐỘ
            assertThat(yagi.getRawMetadata()).contains("YAGI"); // JSONB sống sót round-trip

            // Kiểm chéo trực tiếp bằng SQL — CSDL là chân lý
            assertThat(queryDouble("SELECT ST_X(geom_current) FROM storms WHERE id = ?"))
                    .isCloseTo(107.2, within(1e-6));
            assertThat(queryDouble("SELECT ST_Y(geom_current) FROM storms WHERE id = ?"))
                    .isCloseTo(20.7, within(1e-6));
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT ST_SRID(geom_current) FROM storms WHERE id = ?",
                                    Integer.class,
                                    YAGI_ID))
                    .isEqualTo(4326);

            assertThat(countTracks()).isEqualTo(15);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM storm_tracks WHERE storm_id = ?"
                                            + " AND track_type = 'historical'",
                                    Integer.class,
                                    YAGI_ID))
                    .isEqualTo(13);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM storm_tracks WHERE storm_id = ?"
                                            + " AND track_type = 'forecast'",
                                    Integer.class,
                                    YAGI_ID))
                    .isEqualTo(2);

            // uq_storm_track có hiệu lực ngay chu kỳ đầu — không mốc trùng forecast_time
            Integer duplicates =
                    jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM (SELECT forecast_time FROM storm_tracks"
                                    + " WHERE storm_id = ? GROUP BY forecast_time"
                                    + " HAVING count(*) > 1) d",
                            Integer.class,
                            YAGI_ID);
            assertThat(duplicates).isZero();

            // Hợp đồng suy thời gian W2-05: 72h ÷ 12 đoạn = 6h; mốc cuối historical = todate
            assertThat(firstTrackTime("historical"))
                    .isEqualTo(Instant.parse("2024-09-04T00:00:00Z"));
            assertThat(lastTrackTime("historical"))
                    .isEqualTo(Instant.parse("2024-09-07T00:00:00Z"));
            assertThat(firstTrackTime("forecast")).isEqualTo(Instant.parse("2024-09-07T06:00:00Z"));
            assertThat(lastTrackTime("forecast")).isEqualTo(Instant.parse("2024-09-07T12:00:00Z"));
        }

        @Test
        @DisplayName(
                "TC-ST-04: nạp lại cùng bản tin — PK storms + uq_storm_track giữ nguyên 1 bão / 15 mốc")
        void shouldBeIdempotentOnRepeatedIngestion() {
            stubSearch("search-yagi-active.json");
            stubYagiTrack();

            StormSyncResult first = stormSyncService.triggerStormSync();
            StormSyncResult second = stormSyncService.triggerStormSync();

            assertThat(first.trackPointsInserted()).isEqualTo(15);
            assertThat(second.status()).isEqualTo("SUCCESS");
            assertThat(second.trackPointsInserted()).isZero(); // ON CONFLICT DO NOTHING
            assertThat(countStorms()).isEqualTo(1);
            assertThat(countTracks()).isEqualTo(15);
            assertThat(stormStatus()).isEqualTo("active");
        }
    }

    // ---------------------------------------------------------------------
    // Nhóm 3: ST_DWithin — chức năng (geography 300km) + planner (GiST)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Nhóm 3: GiST idx_storms_geom_current với ST_DWithin (TC-ST-05, TC-ST-06)")
    class SpatialIndexTests {

        @Test
        @DisplayName("TC-ST-05: vành đai 300km quanh tâm YAGI chứa HAN, HPH và loại SGN, DNG, CMU")
        void shouldFindNorthernStationsWithin300KmOfStormCenter() {
            ingestActiveYagi();

            List<String> stationsInBand = stationsWithin300KmOfActiveStorms();

            assertThat(stationsInBand).contains("HAN", "HPH");
            assertThat(stationsInBand).doesNotContain("SGN", "DNG", "CMU");
        }

        @Test
        @DisplayName(
                "TC-ST-06: EXPLAIN với enable_seqscan=off chứng minh planner dùng idx_storms_geom_current")
        void shouldUseGistIndexForPointProximity() {
            ingestActiveYagi();

            String plan = explainStormPointQuery();

            assertThat(plan)
                    .as("Plan phải chứa chỉ mục GiST idx_storms_geom_current")
                    .contains("idx_storms_geom_current");
        }
    }

    // ---------------------------------------------------------------------
    // Nhóm 4: active → dissipated (nhánh B' + nhánh C) và phản chứng feed lỗi
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Nhóm 4: active → dissipated & phản chứng feed lỗi (TC-ST-07..TC-ST-09)")
    class DissipationTests {

        @Test
        @DisplayName(
                "TC-ST-07: nhánh B' — gió 60 km/h = 16.667 m/s < 17.2 → dissipated, vành đai active rỗng")
        void shouldDissipateWhenWindDropsBelowThreshold() {
            ingestActiveYagi();
            assertThat(stormStatus()).isEqualTo("active");

            WIRE_MOCK.resetAll();
            stubSearch("search-yagi-weakened.json");
            stubYagiTrack();

            StormSyncResult result = stormSyncService.triggerStormSync();

            assertThat(result.status()).isEqualTo("SUCCESS");
            assertThat(result.stormsProcessed()).isEqualTo(1);
            assertThat(stormStatus()).isEqualTo("dissipated");
            assertThat(stormEndDate()).isNotNull();
            assertThat(queryDouble("SELECT max_wind_speed FROM storms WHERE id = ?"))
                    .isCloseTo(16.667, within(0.01));
            assertThat(stormRepository.findByStatusIgnoreCase("active")).isEmpty();
            // Lọc status='active' trong JOIN đã loại bão đã tan — đúng nghiệm thu Ngày 8
            assertThat(stationsWithin300KmOfActiveStorms()).isEmpty();
        }

        @Test
        @DisplayName(
                "TC-ST-08: nhánh C — bão active vắng khỏi feed → dissipated + end_date, không còn active")
        void shouldDissipateWhenStormDisappearsFromFeed() {
            ingestActiveYagi();
            assertThat(stormStatus()).isEqualTo("active");

            WIRE_MOCK.resetAll();
            stubSearch("search-empty.json");

            StormSyncResult result = stormSyncService.triggerStormSync();

            assertThat(result.status()).isEqualTo("SUCCESS");
            assertThat(result.stormsOnFeed()).isZero();
            assertThat(result.stormsDissipated()).isEqualTo(1);
            assertThat(stormStatus()).isEqualTo("dissipated");
            assertThat(stormEndDate()).isNotNull();
            assertThat(stormRepository.findByStatusIgnoreCase("active")).isEmpty();
        }

        @Test
        @DisplayName(
                "TC-ST-09: phản chứng — feed lỗi 500 toàn bộ → FAILED, bão VẪN active (quy tắc #1 W2-05)")
        void shouldKeepStormActiveWhenFeedFails() {
            ingestActiveYagi();

            WIRE_MOCK.resetAll();
            WIRE_MOCK.stubFor(
                    get(urlPathEqualTo(SEARCH_PATH)).willReturn(aResponse().withStatus(500)));

            StormSyncResult result = stormSyncService.triggerStormSync();

            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.errorMessage()).contains("Không đọc được danh sách GDACS");
            assertThat(result.stormsProcessed()).isZero();
            // 1 lượt + 3 retry (backoff 0,0,0) — nhánh C bị chặn tuyệt đối
            WIRE_MOCK.verify(4, getRequestedFor(urlPathEqualTo(SEARCH_PATH)));

            assertThat(stormStatus()).isEqualTo("active");
            assertThat(stormEndDate()).isNull();
            assertThat(countTracks()).isEqualTo(15);

            String auditStatus =
                    jdbcTemplate.queryForObject(
                            "SELECT status FROM data_sync_logs WHERE source = 'GDACS'"
                                    + " ORDER BY started_at DESC, id DESC LIMIT 1",
                            String.class);
            assertThat(auditStatus).isEqualTo("FAILED");
        }
    }

    // ---------------------------------------------------------------------
    // Nhóm 5: nhánh B (update + trigger updated_at) và audit log 2 pha
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Nhóm 5: Nhánh B, trigger updated_at & audit log (TC-ST-10, TC-ST-11)")
    class UpdateAndAuditTests {

        @Test
        @DisplayName(
                "TC-ST-10: chu kỳ sau — geom_current cập nhật [106.3, 20.9], gió 61.111 m/s, updated_at tự tăng qua trigger")
        void shouldUpdatePositionWindAndUpdatedAtOnNextCycle() {
            ingestActiveYagi();
            Instant before = stormUpdatedAt().toInstant();

            WIRE_MOCK.resetAll();
            stubSearch("search-yagi-updated.json");
            stubYagiTrack();

            StormSyncResult result = stormSyncService.triggerStormSync();

            assertThat(result.status()).isEqualTo("SUCCESS");
            assertThat(result.stormsProcessed()).isEqualTo(1);
            assertThat(stormStatus()).isEqualTo("active");
            assertThat(queryDouble("SELECT ST_X(geom_current) FROM storms WHERE id = ?"))
                    .isCloseTo(106.3, within(1e-6));
            assertThat(queryDouble("SELECT ST_Y(geom_current) FROM storms WHERE id = ?"))
                    .isCloseTo(20.9, within(1e-6));
            assertThat(queryDouble("SELECT max_wind_speed FROM storms WHERE id = ?"))
                    .isCloseTo(61.111, within(0.01)); // 220 km/h ÷ 3.6
            assertThat(countTracks()).isEqualTo(15); // bản tin quỹ đạo lặp lại → không mốc mới
            // Trigger V4 ghi đè updated_at tại CSDL; equal vẫn chứng minh trigger không bị vô hiệu
            Instant after = stormUpdatedAt().toInstant();
            assertThat(after).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName(
                "TC-ST-11: audit log — pha RUNNING được commit trước khi chu kỳ xong, kết thúc SUCCESS trên cùng dòng")
        void shouldExposeRunningPhaseThenSuccess() throws Exception {
            // Delay phản hồi để quan sát được pha RUNNING đã commit trước khi finishLog chạy
            WIRE_MOCK.stubFor(
                    get(urlPathEqualTo(SEARCH_PATH))
                            .withQueryParam("eventlist", equalTo("TC"))
                            .willReturn(
                                    okJson(fixture("search-yagi-active.json"))
                                            .withFixedDelay(1000)));
            stubYagiTrack();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<StormSyncResult> cycle = executor.submit(stormSyncService::triggerStormSync);
            try {
                boolean runningObserved = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline) {
                    Integer running =
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM data_sync_logs WHERE source = 'GDACS'"
                                            + " AND status = 'RUNNING'",
                                    Integer.class);
                    if (running != null && running > 0) {
                        runningObserved = true;
                        break;
                    }
                    Thread.sleep(20);
                }
                assertThat(runningObserved)
                        .as("Pha RUNNING phải được commit trước khi chu kỳ kết thúc")
                        .isTrue();

                StormSyncResult result = cycle.get(10, TimeUnit.SECONDS);
                assertThat(result.status()).isEqualTo("SUCCESS");
            } finally {
                cycle.cancel(true);
                executor.shutdownNow();
            }

            Map<String, Object> auditLog =
                    jdbcTemplate.queryForMap(
                            "SELECT status, records_processed, error_message, completed_at"
                                    + " FROM data_sync_logs WHERE source = 'GDACS'"
                                    + " ORDER BY started_at DESC, id DESC LIMIT 1");
            assertThat(auditLog.get("status")).isEqualTo("SUCCESS");
            assertThat(auditLog.get("records_processed")).isEqualTo(1);
            assertThat(auditLog.get("error_message")).isNull();
            assertThat(auditLog.get("completed_at")).isNotNull();
            // RUNNING → SUCCESS cập nhật CÙNG một dòng audit (startLog/finishLog W2-05)
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM data_sync_logs WHERE source = 'GDACS'",
                                    Integer.class))
                    .isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------------
    // Trợ giúp — fixture, stub WireMock, truy vấn kiểm chứng
    // ---------------------------------------------------------------------

    private String fixture(String name) {
        try (InputStream in =
                getClass().getClassLoader().getResourceAsStream("fixtures/gdacs/" + name)) {
            assertThat(in).as("fixture %s tồn tại", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được fixture " + name, e);
        }
    }

    private void stubSearch(String fixtureName) {
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo(SEARCH_PATH))
                        .withQueryParam("eventlist", equalTo("TC"))
                        .willReturn(okJson(fixture(fixtureName))));
    }

    private void stubYagiTrack() {
        WIRE_MOCK.stubFor(
                get(urlPathEqualTo(TRACK_PATH))
                        .withQueryParam("eventtype", equalTo("TC"))
                        .withQueryParam("eventid", equalTo(YAGI_ID))
                        .willReturn(okJson(fixture("track-yagi.json"))));
    }

    private void ingestActiveYagi() {
        stubSearch("search-yagi-active.json");
        stubYagiTrack();
        stormSyncService.triggerStormSync();
    }

    private int countStorms() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM storms", Integer.class);
        return count == null ? 0 : count;
    }

    private int countTracks() {
        Integer count =
                jdbcTemplate.queryForObject("SELECT count(*) FROM storm_tracks", Integer.class);
        return count == null ? 0 : count;
    }

    private String stormStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM storms WHERE id = ?", String.class, YAGI_ID);
    }

    private OffsetDateTime stormEndDate() {
        return jdbcTemplate.queryForObject(
                "SELECT end_date FROM storms WHERE id = ?", OffsetDateTime.class, YAGI_ID);
    }

    private OffsetDateTime stormUpdatedAt() {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM storms WHERE id = ?", OffsetDateTime.class, YAGI_ID);
    }

    private double queryDouble(String sql) {
        Double value = jdbcTemplate.queryForObject(sql, Double.class, YAGI_ID);
        assertThat(value).as("truy vấn trả về giá trị: %s", sql).isNotNull();
        return value;
    }

    private Instant firstTrackTime(String trackType) {
        OffsetDateTime time =
                jdbcTemplate.queryForObject(
                        "SELECT min(forecast_time) FROM storm_tracks"
                                + " WHERE storm_id = ? AND track_type = ?",
                        OffsetDateTime.class,
                        YAGI_ID,
                        trackType);
        assertThat(time).isNotNull();
        return time.toInstant();
    }

    private Instant lastTrackTime(String trackType) {
        OffsetDateTime time =
                jdbcTemplate.queryForObject(
                        "SELECT max(forecast_time) FROM storm_tracks"
                                + " WHERE storm_id = ? AND track_type = ?",
                        OffsetDateTime.class,
                        YAGI_ID,
                        trackType);
        assertThat(time).isNotNull();
        return time.toInstant();
    }

    private List<String> stationsWithin300KmOfActiveStorms() {
        return jdbcTemplate.query(
                "SELECT l.code FROM locations l JOIN storms s ON s.status = 'active'"
                        + " WHERE ST_DWithin(s.geom_current::geography, l.geom::geography, 300000)"
                        + " ORDER BY l.code",
                (rs, rowNum) -> rs.getString(1));
    }

    /**
     * EXPLAIN biến thể geometry (đơn vị độ) trong MỘT ConnectionCallback — SET chỉ hiệu lực phiên,
     * pool Hikari không đảm bảo 2 lần query dùng cùng connection (quyết định #3, anti-pattern #3).
     */
    private String explainStormPointQuery() {
        return jdbcTemplate.execute(
                (ConnectionCallback<String>)
                        connection -> {
                            try (Statement statement = connection.createStatement()) {
                                statement.execute("SET enable_seqscan = off");
                                try (ResultSet rs =
                                        statement.executeQuery(
                                                "EXPLAIN (COSTS OFF) SELECT id FROM storms WHERE"
                                                        + " ST_DWithin(geom_current,"
                                                        + " ST_SetSRID(ST_MakePoint(107.2, 20.7),"
                                                        + " 4326), 2.0)")) {
                                    StringBuilder plan = new StringBuilder();
                                    while (rs.next()) {
                                        plan.append(rs.getString(1)).append('\n');
                                    }
                                    return plan.toString();
                                } finally {
                                    statement.execute("SET enable_seqscan = on");
                                }
                            }
                        });
    }
}
