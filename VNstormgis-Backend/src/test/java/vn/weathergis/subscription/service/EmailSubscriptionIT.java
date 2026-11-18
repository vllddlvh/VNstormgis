package vn.weathergis.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.event.AlertPublishedEvent;
import vn.weathergis.alert.service.AlertGenerationWorker;
import vn.weathergis.alert.service.AlertPersistenceService;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.event.ThresholdViolatedEvent.Violation;
import vn.weathergis.common.AbstractPostGisIntegrationTest;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.location.domain.LocationEntity;
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.subscription.domain.EmailSubscriptionEntity;
import vn.weathergis.subscription.dto.CreateSubscriptionRequest;
import vn.weathergis.subscription.dto.SubscriptionResponse;
import vn.weathergis.subscription.repository.EmailNotificationLogRepository;
import vn.weathergis.subscription.repository.EmailSubscriptionRepository;

@SpringBootTest
@DisplayName("EmailSubscription PostGIS & Alert Event Integration Tests")
class EmailSubscriptionIT extends AbstractPostGisIntegrationTest {

    @Autowired private EmailSubscriptionService subscriptionService;
    @Autowired private EmailSubscriptionRepository subscriptionRepository;
    @Autowired private EmailNotificationLogRepository logRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private AlertPersistenceService alertPersistenceService;
    @Autowired private AlertGenerationWorker alertGenerationWorker;
    @Autowired private JdbcTemplate jdbcTemplate;

    @SpyBean private EmailDispatchService emailDispatchService;

    private final Map<UUID, String> capturedOtps = new ConcurrentHashMap<>();
    private final Map<UUID, String> capturedVerifyTokens = new ConcurrentHashMap<>();
    private UUID createdAlertId;

    @BeforeEach
    void captureVerificationSecrets() {
        capturedOtps.clear();
        capturedVerifyTokens.clear();
        doAnswer(
                        invocation -> {
                            EmailSubscriptionEntity sub = invocation.getArgument(0);
                            String otp = invocation.getArgument(1);
                            String token = invocation.getArgument(2);
                            capturedOtps.put(sub.getId(), otp);
                            capturedVerifyTokens.put(sub.getId(), token);
                            return invocation.callRealMethod();
                        })
                .when(emailDispatchService)
                .sendVerificationEmail(any(), anyString(), anyString());
    }

    @AfterEach
    void cleanUp() {
        if (createdAlertId != null) {
            jdbcTemplate.update(
                    "DELETE FROM alert_violation_keys WHERE alert_id = ?", createdAlertId);
            jdbcTemplate.update(
                    "DELETE FROM alert_generation_jobs WHERE alert_id = ?", createdAlertId);
            jdbcTemplate.update("DELETE FROM alerts WHERE id = ?", createdAlertId);
            createdAlertId = null;
        }
        logRepository.deleteAll();
        subscriptionRepository.deleteAll();
    }

    @Test
    @DisplayName(
            "SUB-IT-01: Flyway V10 tạo đủ bảng email_subscriptions, email_notification_logs và chỉ mục GiST")
    void flywayV10CreatesTablesAndGistIndex() {
        Integer v10Count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '10' AND success = TRUE",
                        Integer.class);
        assertThat(v10Count).isEqualTo(1);

        Integer gistIdx =
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM pg_indexes
                        WHERE tablename = 'email_subscriptions'
                          AND indexname = 'idx_email_subscriptions_target_geom'
                          AND indexdef ILIKE '%USING gist%'
                        """,
                        Integer.class);
        assertThat(gistIdx).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "SUB-IT-02: Luồng đầy đủ Đăng ký -> OTP/Token -> Xuất bản cảnh báo PostGIS ST_DWithin -> Chống gửi trùng -> Hủy đăng ký")
    void fullSubscriptionLifecycleAndIdempotentSpatialAlertDispatch() {
        LocationEntity danang = locationRepository.findByCode("DNG").orElseThrow();

        // 1. Đăng ký theo trạm Đà Nẵng (xác thực qua OTP)
        SubscriptionResponse stationSub =
                subscriptionService.createSubscription(
                        new CreateSubscriptionRequest(
                                "danang.user@example.com",
                                "STATION",
                                null,
                                "DNG",
                                null,
                                null,
                                null,
                                50.0,
                                "WARNING"));
        assertThat(stationSub.status()).isEqualTo("PENDING");
        assertThat(stationSub.unsubscribeToken()).isNull();

        String dngOtp = capturedOtps.get(stationSub.id());
        assertThat(dngOtp).matches("^\\d{6}$");

        // OTP sai phải bị từ chối
        String wrongOtp = "000000".equals(dngOtp) ? "999999" : "000000";
        assertThatThrownBy(() -> subscriptionService.verifyByOtp(stationSub.id(), wrongOtp))
                .isInstanceOf(InvalidParameterException.class);

        SubscriptionResponse activeStationSub =
                subscriptionService.verifyByOtp(stationSub.id(), dngOtp);
        assertThat(activeStationSub.status()).isEqualTo("ACTIVE");
        assertThat(activeStationSub.unsubscribeToken()).isNotBlank();

        // 2. Đăng ký theo tọa độ GPS cách Đà Nẵng ~2 km (xác thực qua liên kết 1-click)
        SubscriptionResponse gpsSub =
                subscriptionService.createSubscription(
                        new CreateSubscriptionRequest(
                                "gps.user@example.com",
                                "COORDINATE",
                                null,
                                null,
                                108.2150,
                                16.0620,
                                "Vị trí hiện tại Hải Châu",
                                30.0,
                                "DANGER"));
        String gpsVerifyToken = capturedVerifyTokens.get(gpsSub.id());
        SubscriptionResponse activeGpsSub = subscriptionService.verifyByToken(gpsVerifyToken);
        assertThat(activeGpsSub.status()).isEqualTo("ACTIVE");

        // 3. Đăng ký ở Hà Nội (cách > 600 km) -> KHÔNG được nhận cảnh báo Đà Nẵng
        SubscriptionResponse hanoiSub =
                subscriptionService.createSubscription(
                        new CreateSubscriptionRequest(
                                "hanoi.user@example.com",
                                "STATION",
                                null,
                                "HAN",
                                null,
                                null,
                                null,
                                50.0,
                                "INFO"));
        subscriptionService.verifyByOtp(hanoiSub.id(), capturedOtps.get(hanoiSub.id()));

        // 4. Gửi bản tin kiểm tra kết nối tại vị trí GPS
        SubscriptionResponse testSent =
                subscriptionService.sendTestNotification(
                        activeGpsSub.id(), activeGpsSub.unsubscribeToken());
        assertThat(testSent.deliveryMode()).isIn("SIMULATED", "SENT");

        // 5. Xuất bản một cảnh báo DANGER thực tế tại Đà Nẵng qua AlertPersistenceService
        Integer thresholdId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM alert_thresholds ORDER BY id ASC LIMIT 1", Integer.class);
        Instant evalTime = Instant.parse("2026-11-18T03:00:00Z");
        String vKey = "v1:sub-it:" + UUID.randomUUID();
        ThresholdViolatedEvent violationEvent =
                new ThresholdViolatedEvent(
                        danang.getId(),
                        "DNG",
                        "Đà Nẵng",
                        danang.getGeom().getX(),
                        danang.getGeom().getY(),
                        UUID.randomUUID(),
                        evalTime,
                        List.of(
                                new Violation(
                                        thresholdId,
                                        "rain_1h",
                                        ">=",
                                        50.0,
                                        "DANGER",
                                        72.4,
                                        "mm",
                                        evalTime,
                                        null,
                                        null,
                                        null,
                                        evalTime,
                                        vKey)));

        AlertGenerationJobView job = alertPersistenceService.intake(violationEvent).orElseThrow();
        alertGenerationWorker.runBatch();
        createdAlertId = alertPersistenceService.findJobById(job.jobId()).orElseThrow().alertId();
        assertThat(createdAlertId).isNotNull();

        // Kiểm tra cả 2 người đăng ký vùng Đà Nẵng (STATION + COORDINATE) đều nhận thông báo, Hà
        // Nội = 0
        Integer alertEmailsCount =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM email_notification_logs WHERE alert_id = ? AND notification_type = 'PROACTIVE_ALERT'",
                        Integer.class,
                        createdAlertId);
        assertThat(alertEmailsCount).isEqualTo(2);

        // 6. Phát lặp cùng sự kiện cảnh báo -> Idempotency đảm bảo 0 bản tin trùng lặp
        AlertFactualSnapshot snap =
                new AlertFactualSnapshot(
                        danang.getId(),
                        "DNG",
                        "Đà Nẵng",
                        List.of(108.2022, 16.0544),
                        UUID.randomUUID(),
                        evalTime,
                        new RepresentativeThresholdSnapshot(
                                thresholdId, "rain_1h", ">=", 50.0, "DANGER", "mm", vKey),
                        List.of(
                                new ViolationSnapshot(
                                        thresholdId,
                                        "rain_1h",
                                        ">=",
                                        50.0,
                                        "DANGER",
                                        72.4,
                                        "mm",
                                        evalTime,
                                        evalTime,
                                        vKey,
                                        null,
                                        null,
                                        null)));
        int duplicateDispatched =
                subscriptionService.dispatchForPublishedAlert(
                        new AlertPublishedEvent(
                                createdAlertId,
                                danang.getId(),
                                null,
                                "Cảnh báo mưa lớn tại Đà Nẵng",
                                "DANGER",
                                "rain_1h >= 50",
                                "Ghi nhận mưa 72.4 mm.",
                                true,
                                108.2022,
                                16.0544,
                                OffsetDateTime.now(),
                                snap));
        assertThat(duplicateDispatched).isZero();

        // 7. Hủy đăng ký 1-click -> chuyển sang UNSUBSCRIBED & token cũ hết hiệu lực
        SubscriptionResponse unsub =
                subscriptionService.unsubscribeByToken(activeStationSub.unsubscribeToken());
        assertThat(unsub.status()).isEqualTo("UNSUBSCRIBED");
        assertThatThrownBy(
                        () ->
                                subscriptionService.unsubscribeByToken(
                                        activeStationSub.unsubscribeToken()))
                .isInstanceOf(InvalidParameterException.class);
    }
}
