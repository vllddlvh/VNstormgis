package vn.weathergis.alert.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.weathergis.alert.dto.AlertThresholdRequest;
import vn.weathergis.alert.dto.AlertThresholdResponse;
import vn.weathergis.alert.service.AlertThresholdService;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.common.exception.ResourceNotFoundException;

/** Real V5 schema, generated timestamps, filters and alert FK semantics on isolated PostGIS. */
@SpringBootTest
class AlertThresholdRepositoryIT extends AbstractPostGisIntegrationTest {

    @Autowired private AlertThresholdRepository repository;
    @Autowired private AlertThresholdService service;
    @Autowired private JdbcTemplate jdbc;

    private final List<Integer> thresholdIds = new ArrayList<>();
    private final List<UUID> alertIds = new ArrayList<>();

    @AfterEach
    void removeFixtures() {
        alertIds.forEach(id -> jdbc.update("delete from alerts where id = ?", id));
        thresholdIds.forEach(id -> jdbc.update("delete from alert_thresholds where id = ?", id));
    }

    @Test
    void seedRulesAndOptionalFiltersAreAvailable() {
        assertThat(repository.findByFilters(null, null))
                .extracting(rule -> rule.getParameter())
                .contains("rain_1h", "wind_speed", "storm_dist");
        assertThat(repository.findByFilters("storm_dist", true))
                .isNotEmpty()
                .allSatisfy(
                        rule -> {
                            assertThat(rule.getParameter()).isEqualTo("storm_dist");
                            assertThat(rule.getIsEnabled()).isTrue();
                        });
        AlertThresholdResponse disabled = create(false);
        assertThat(service.findAll(null, false))
                .extracting(AlertThresholdResponse::id)
                .contains(disabled.id());
        assertThat(service.findAll("rain_3h", false))
                .extracting(AlertThresholdResponse::id)
                .contains(disabled.id());
    }

    @Test
    void crudPersistsAndReturnsDatabaseTriggerTimestamps() {
        AlertThresholdResponse created = create(null);
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.updatedAt()).isNotNull();
        assertThat(service.findById(created.id())).isEqualTo(created);

        AlertThresholdResponse updated =
                service.update(
                        created.id(),
                        new AlertThresholdRequest(
                                "Updated W4-01",
                                "wind_gust",
                                ">",
                                25.0,
                                "CRITICAL",
                                "Gió giật",
                                false));
        assertThat(updated.name()).isEqualTo("Updated W4-01");
        assertThat(updated.parameter()).isEqualTo("wind_gust");
        assertThat(updated.operator()).isEqualTo(">");
        assertThat(updated.thresholdValue()).isEqualTo(25.0);
        assertThat(updated.severityLevel()).isEqualTo("CRITICAL");
        assertThat(updated.description()).isEqualTo("Gió giật");
        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.updatedAt()).isAfter(created.updatedAt());
        assertThat(service.findById(created.id())).isEqualTo(updated);

        AlertThresholdResponse toggled = service.toggle(created.id());
        assertThat(toggled.isEnabled()).isTrue();
        assertThat(toggled.updatedAt()).isAfter(updated.updatedAt());
        assertThat(service.findById(created.id())).isEqualTo(toggled);
        assertThat(service.toggle(created.id()).isEnabled()).isFalse();

        service.delete(created.id());
        assertThat(repository.findById(created.id())).isEmpty();
        assertThatThrownBy(() -> service.findById(created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void hardDeletePreservesHistoricalAlertAndSetsThresholdLinkToNull() {
        AlertThresholdResponse created = create(true);
        UUID alertId = UUID.randomUUID();
        alertIds.add(alertId);
        jdbc.update(
                """
                insert into alerts (id, threshold_id, title, severity_level, trigger_reason, ai_explanation)
                values (?, ?, ?, ?, ?, ?)
                """,
                alertId,
                created.id(),
                "W4-01 test alert",
                "WARNING",
                "Fixture",
                "Fixture");

        service.delete(created.id());

        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from alerts where id = ?", Integer.class, alertId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select threshold_id from alerts where id = ?",
                                Integer.class,
                                alertId))
                .isNull();
    }

    @Test
    void concurrentTogglesEachFlipTheLatestCommittedValue() throws Exception {
        AlertThresholdResponse created = create(true);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AlertThresholdResponse>> toggles = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (int i = 0; i < 4; i++) {
                toggles.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return service.toggle(created.id());
                                }));
            }
            start.countDown();
            List<Boolean> enabledValues = new ArrayList<>();
            for (Future<AlertThresholdResponse> toggle : toggles) {
                enabledValues.add(toggle.get(10, TimeUnit.SECONDS).isEnabled());
            }
            assertThat(enabledValues).containsExactlyInAnyOrder(false, true, false, true);
        }
        assertThat(service.findById(created.id()).isEnabled()).isTrue();
    }

    private AlertThresholdResponse create(Boolean enabled) {
        AlertThresholdResponse created =
                service.create(
                        new AlertThresholdRequest(
                                "W4-01 integration",
                                "rain_3h",
                                ">=",
                                100.0,
                                "WARNING",
                                "Fixture",
                                enabled));
        thresholdIds.add(created.id());
        return created;
    }
}
