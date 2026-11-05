package vn.weathergis.alert.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.weathergis.alert.domain.AlertViolationKeyEntity;
import vn.weathergis.alert.dto.AlertDetailResponse;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.dto.VerifiedAlertSubmission;
import vn.weathergis.alert.service.AlertPersistenceService;
import vn.weathergis.alert.service.AlertService;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.event.ThresholdViolatedEvent.Violation;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.common.api.GeoJsonFeatureCollection;

@SpringBootTest
class AlertRepositoryIT extends AbstractPostGisIntegrationTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-11-05T06:00:00Z");
    private static final Instant WINDOW_AT = Instant.parse("2026-11-05T05:45:00Z");

    @Autowired private AlertPersistenceService persistenceService;
    @Autowired private AlertService alertService;
    @Autowired private AlertViolationKeyRepository keyRepository;
    @Autowired private JdbcTemplate jdbc;

    private final List<UUID> alertIds = new ArrayList<>();
    private final List<UUID> jobIds = new ArrayList<>();
    private final List<UUID> stationIds = new ArrayList<>();
    private final List<Integer> thresholdIds = new ArrayList<>();
    private final List<String> stormIds = new ArrayList<>();

    @AfterEach
    void cleanFixtures() {
        for (UUID jobId : jobIds) {
            jdbc.update("delete from alert_violation_keys where job_id = ?", jobId);
            jdbc.update("update alert_generation_jobs set alert_id = null where id = ?", jobId);
            jdbc.update("delete from alert_generation_jobs where id = ?", jobId);
        }
        alertIds.forEach(id -> jdbc.update("delete from alerts where id = ?", id));
        stationIds.forEach(id -> jdbc.update("delete from locations where id = ?", id));
        thresholdIds.forEach(id -> jdbc.update("delete from alert_thresholds where id = ?", id));
        stormIds.forEach(id -> jdbc.update("delete from storms where id = ?", id));
    }

    @Test
    void migrationV7PreservesExistingIndexesAndAddsInboxTablesSnapshotAndFallbackColumn() {
        Integer v7Success =
                jdbc.queryForObject(
                        """
                        select count(*) from flyway_schema_history
                        where version = '7' and success = true
                        """,
                        Integer.class);
        assertThat(v7Success).isEqualTo(1);

        List<String> alertColumns =
                jdbc.queryForList(
                        """
                        select column_name from information_schema.columns
                        where table_name = 'alerts'
                        """,
                        String.class);
        assertThat(alertColumns).contains("is_fallback", "factual_snapshot", "affected_geom");

        List<String> indexes =
                jdbc.queryForList(
                        """
                        select indexname from pg_indexes
                        where tablename in ('alerts', 'alert_generation_jobs', 'alert_violation_keys')
                        """,
                        String.class);
        assertThat(indexes)
                .contains(
                        "idx_alerts_affected_geom",
                        "idx_alerts_status_time",
                        "idx_alert_jobs_status_next_attempt",
                        "idx_alert_jobs_location_id",
                        "idx_alert_violation_keys_job_id",
                        "idx_alert_violation_keys_alert_id");
    }

    @Test
    void spatialGeometryUsesSrid4326LngLatAndViolationKeysEnforceGlobalUniqueness() {
        UUID stationId = createStation("W403_REPO", "Trạm Repo", 108.2123, 16.0543);
        int ruleId = createThreshold("rain_1h", ">=", 50.0, "DANGER");
        Violation v = violation(stationId, ruleId, "rain_1h", ">=", 50.0, "DANGER", 64.0, "mm");

        AlertGenerationJobView pending =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        stationId,
                                        "W403_REPO",
                                        "Trạm Repo",
                                        108.2123,
                                        16.0543,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(v)))
                        .orElseThrow();
        jobIds.add(pending.jobId());

        UUID alertId =
                persistenceService.submitAndPublish(
                        new VerifiedAlertSubmission(
                                pending.jobId(),
                                "Cảnh báo mưa lớn tại Trạm Repo",
                                "Lượng mưa 1h tại Trạm Repo đạt 64.0 mm, vượt ngưỡng >= 50.0 mm.",
                                false,
                                pending.violationKeys(),
                                pending.factualSnapshot()));
        alertIds.add(alertId);

        var spatial =
                jdbc.queryForMap(
                        """
                        select ST_SRID(affected_geom) as srid,
                               ST_GeometryType(affected_geom) as geom_type,
                               ST_X(affected_geom) as lng,
                               ST_Y(affected_geom) as lat
                        from alerts where id = ?
                        """,
                        alertId);
        assertThat(spatial.get("srid")).isEqualTo(4326);
        assertThat(spatial.get("geom_type")).isEqualTo("ST_Point");
        assertThat((Double) spatial.get("lng")).isEqualTo(108.2123);
        assertThat((Double) spatial.get("lat")).isEqualTo(16.0543);

        // Attempting to insert a duplicate violation_key directly throws
        // DataIntegrityViolationException
        assertThatThrownBy(
                        () ->
                                keyRepository.saveAndFlush(
                                        new AlertViolationKeyEntity(
                                                v.violationKey(),
                                                pending.jobId(),
                                                OffsetDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void
            editingOrDeletingThresholdLocationOrStormSetsForeignKeysNullWhilePreservingSnapshotAndKeys() {
        UUID stationId = createStation("W403_FK", "Trạm Liên Kết", 107.6, 16.4);
        int rainRule = createThreshold("rain_1h", ">=", 50.0, "DANGER");
        int stormRule = createThreshold("storm_dist", "<=", 300.0, "CRITICAL");
        String stormId = createStorm("W403_STORM_FK", "Bão Kiểm Chứng FK", 110.0, 16.4);

        Violation rainV =
                violation(stationId, rainRule, "rain_1h", ">=", 50.0, "DANGER", 72.0, "mm");
        Violation stormV =
                new Violation(
                        stormRule,
                        "storm_dist",
                        "<=",
                        300.0,
                        "CRITICAL",
                        150.0,
                        "km",
                        WINDOW_AT,
                        stormId,
                        "Bão Kiểm Chứng FK",
                        150.0,
                        WINDOW_AT,
                        "v1:" + stationId + ":" + stormRule + ":" + WINDOW_AT + ":" + stormId);

        AlertGenerationJobView pending =
                persistenceService
                        .intake(
                                new ThresholdViolatedEvent(
                                        stationId,
                                        "W403_FK",
                                        "Trạm Liên Kết",
                                        107.6,
                                        16.4,
                                        UUID.randomUUID(),
                                        EVALUATED_AT,
                                        List.of(rainV, stormV)))
                        .orElseThrow();
        jobIds.add(pending.jobId());

        UUID alertId =
                persistenceService.submitAndPublish(
                        new VerifiedAlertSubmission(
                                pending.jobId(),
                                "Cảnh báo bão gần và mưa lớn tại Trạm Liên Kết",
                                "Tâm bão Kiểm Chứng FK cách 150.0 km (<= 300.0 km), mưa 1h đạt 72.0 mm.",
                                false,
                                pending.violationKeys(),
                                pending.factualSnapshot()));
        alertIds.add(alertId);

        // Edit threshold and station first -> snapshot and trigger_reason remain historical
        jdbc.update("update alert_thresholds set threshold_value = 999.0 where id = ?", stormRule);
        jdbc.update("update locations set name = 'Trạm Đổi Tên' where id = ?", stationId);

        AlertDetailResponse afterEdit = alertService.getAlertById(alertId);
        assertThat(afterEdit.location().name()).isEqualTo("Trạm Đổi Tên");
        assertThat(afterEdit.threshold().thresholdValue()).isEqualTo(999.0);
        assertThat(afterEdit.factualSnapshot().locationName()).isEqualTo("Trạm Liên Kết");
        assertThat(afterEdit.factualSnapshot().representativeThreshold().thresholdValue())
                .isEqualTo(300.0);

        // Delete threshold, station, and storm -> ON DELETE SET NULL on alerts, snapshot & keys
        // intact
        jdbc.update("delete from alert_thresholds where id = ?", stormRule);
        jdbc.update("delete from alert_thresholds where id = ?", rainRule);
        thresholdIds.clear();
        jdbc.update("delete from locations where id = ?", stationId);
        stationIds.clear();
        jdbc.update("delete from storms where id = ?", stormId);
        stormIds.clear();

        AlertDetailResponse afterDelete = alertService.getAlertById(alertId);
        assertThat(afterDelete.thresholdId()).isNull();
        assertThat(afterDelete.locationId()).isNull();
        assertThat(afterDelete.stormId()).isNull();
        assertThat(afterDelete.threshold()).isNull();
        assertThat(afterDelete.location()).isNull();
        assertThat(afterDelete.storm()).isNull();

        assertThat(afterDelete.factualSnapshot()).isNotNull();
        assertThat(afterDelete.factualSnapshot().locationId()).isEqualTo(stationId);
        assertThat(afterDelete.factualSnapshot().locationCode()).isEqualTo("W403_FK");
        assertThat(afterDelete.factualSnapshot().locationName()).isEqualTo("Trạm Liên Kết");
        assertThat(afterDelete.factualSnapshot().coordinates()).containsExactly(107.6, 16.4);
        assertThat(afterDelete.factualSnapshot().representativeThreshold().thresholdId())
                .isEqualTo(stormRule);
        assertThat(afterDelete.factualSnapshot().violations()).hasSize(2);

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alert_violation_keys where alert_id = ?",
                                Integer.class,
                                alertId))
                .isEqualTo(2);
    }

    @Test
    void legacyAlertWithoutSnapshotOrGeometryRemainsReadableWithExplicitNulls() {
        UUID legacyAlertId = UUID.randomUUID();
        alertIds.add(legacyAlertId);
        jdbc.update(
                """
                insert into alerts
                    (id, title, severity_level, trigger_reason, ai_explanation, affected_geom, status, triggered_at)
                values (?, 'Cảnh báo legacy', 'WARNING', 'Lý do legacy', 'Giải thích legacy', null, 'active', ?)
                """,
                legacyAlertId,
                OffsetDateTime.parse("2026-11-05T01:00:00Z"));

        AlertDetailResponse detail = alertService.getAlertById(legacyAlertId);
        assertThat(detail.id()).isEqualTo(legacyAlertId);
        assertThat(detail.isFallback()).isFalse();
        assertThat(detail.factualSnapshot()).isNull();
        assertThat(detail.location()).isNull();
        assertThat(detail.threshold()).isNull();
        assertThat(detail.storm()).isNull();

        GeoJsonFeatureCollection activeCollection = alertService.getActiveAlertsAsGeoJson();
        var legacyFeature =
                activeCollection.getFeatures().stream()
                        .filter(f -> legacyAlertId.toString().equals(f.getId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(legacyFeature.getGeometry()).isNull();
        assertThat(legacyFeature.getProperties().get("is_fallback")).isEqualTo(false);
    }

    private UUID createStation(String code, String name, double lng, double lat) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into locations (id, code, name, region, geom, is_active)
                values (?, ?, ?, 'Trung Bộ', ST_SetSRID(ST_MakePoint(?, ?), 4326), true)
                """,
                id,
                code,
                name,
                lng,
                lat);
        stationIds.add(id);
        return id;
    }

    private int createThreshold(String parameter, String operator, double value, String severity) {
        Integer id =
                jdbc.queryForObject(
                        """
                        insert into alert_thresholds
                            (name, parameter, operator, threshold_value, severity_level, description, is_enabled)
                        values ('W4-03 repo fixture', ?, ?, ?, ?, 'Repo test fixture', true)
                        returning id
                        """,
                        Integer.class,
                        parameter,
                        operator,
                        value,
                        severity);
        thresholdIds.add(id);
        return id;
    }

    private String createStorm(String id, String name, double lng, double lat) {
        OffsetDateTime now = OffsetDateTime.parse("2026-11-05T05:45:00Z");
        jdbc.update(
                """
                insert into storms
                    (id, name, year, category, max_wind_speed, status, start_date, geom_current, updated_at)
                values (?, ?, 2026, 'Typhoon', 40.0, 'active', ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                """,
                id,
                name,
                now.minusDays(1),
                lng,
                lat,
                now);
        stormIds.add(id);
        return id;
    }

    private static Violation violation(
            UUID stationId,
            int thresholdId,
            String parameter,
            String operator,
            double thresholdValue,
            String severity,
            double measuredValue,
            String unit) {
        String key = "v1:" + stationId + ":" + thresholdId + ":" + WINDOW_AT;
        return new Violation(
                thresholdId,
                parameter,
                operator,
                thresholdValue,
                severity,
                measuredValue,
                unit,
                WINDOW_AT,
                null,
                null,
                null,
                WINDOW_AT,
                key);
    }
}
