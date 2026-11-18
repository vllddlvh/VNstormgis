package vn.weathergis.subscription.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.weathergis.alert.event.AlertPublishedEvent;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.location.dto.LocationResponse;
import vn.weathergis.location.dto.SpatialPointContextResponse;
import vn.weathergis.location.service.LocationService;
import vn.weathergis.subscription.config.SubscriptionConfig.SubscriptionMailProperties;
import vn.weathergis.subscription.domain.EmailNotificationLogEntity;
import vn.weathergis.subscription.domain.EmailSubscriptionEntity;
import vn.weathergis.subscription.dto.CreateSubscriptionRequest;
import vn.weathergis.subscription.dto.SubscriptionResponse;
import vn.weathergis.subscription.repository.EmailNotificationLogRepository;
import vn.weathergis.subscription.repository.EmailSubscriptionRepository;
import vn.weathergis.subscription.service.EmailDispatchService;
import vn.weathergis.subscription.service.EmailDispatchService.DispatchOutcome;
import vn.weathergis.subscription.service.EmailSubscriptionService;
import vn.weathergis.subscription.service.SubscriptionTokenCodec;

@Service
public class EmailSubscriptionServiceImpl implements EmailSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(EmailSubscriptionServiceImpl.class);
    private static final GeometryFactory GEO_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);
    private static final Pattern STRICT_EMAIL_PATTERN =
            Pattern.compile("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$");
    private static final Set<String> VALID_SCOPES = Set.of("STATION", "COORDINATE");
    private static final Map<String, Integer> SEVERITY_RANKS =
            Map.of("INFO", 1, "WARNING", 2, "DANGER", 3, "CRITICAL", 4);

    private final EmailSubscriptionRepository subscriptionRepository;
    private final EmailNotificationLogRepository logRepository;
    private final LocationService locationService;
    private final EmailDispatchService emailDispatchService;
    private final SubscriptionMailProperties properties;
    private final Clock clock;
    private final TransactionTemplate requiresNewTx;

    public EmailSubscriptionServiceImpl(
            EmailSubscriptionRepository subscriptionRepository,
            EmailNotificationLogRepository logRepository,
            LocationService locationService,
            EmailDispatchService emailDispatchService,
            SubscriptionMailProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.subscriptionRepository = subscriptionRepository;
        this.logRepository = logRepository;
        this.locationService = locationService;
        this.emailDispatchService = emailDispatchService;
        this.properties = properties;
        this.clock = clock;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
        String email = SubscriptionTokenCodec.normalizeEmail(request.email());
        if (email.isBlank()
                || email.length() > 255
                || !STRICT_EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_VALIDATION, "Địa chỉ email không hợp lệ");
        }

        String scopeType =
                request.scopeType() == null
                        ? ""
                        : request.scopeType().trim().toUpperCase(Locale.ROOT);
        if (!VALID_SCOPES.contains(scopeType)) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_VALIDATION,
                    "Phạm vi đăng ký (scopeType) phải là STATION hoặc COORDINATE");
        }

        double radiusKm = request.radiusKm() != null ? request.radiusKm() : 50.0;
        if (!Double.isFinite(radiusKm) || radiusKm < 5.0 || radiusKm > 500.0) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_VALIDATION,
                    "Bán kính cảnh báo (radiusKm) phải nằm trong khoảng [5, 500] km");
        }

        String minSeverity =
                request.minSeverity() != null && !request.minSeverity().isBlank()
                        ? request.minSeverity().trim().toUpperCase(Locale.ROOT)
                        : "WARNING";
        if (!SEVERITY_RANKS.containsKey(minSeverity)) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_VALIDATION,
                    "Mức độ cảnh báo tối thiểu phải thuộc INFO, WARNING, DANGER, CRITICAL");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        long recentSends =
                logRepository.countRecentVerificationEmails(
                        email, now.minusMinutes(properties.rateLimitWindowMinutes()));
        if (recentSends >= properties.rateLimitMaxSends()) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_RATE_LIMITED,
                    "Email này đã yêu cầu mã xác thực vượt quá "
                            + properties.rateLimitMaxSends()
                            + " lần trong "
                            + properties.rateLimitWindowMinutes()
                            + " phút. Vui lòng thử lại sau.");
        }

        UUID resolvedLocationId = null;
        String resolvedLocationCode = null;
        double lng;
        double lat;
        String resolvedLabel;

        if ("STATION".equals(scopeType)) {
            LocationResponse loc;
            if (request.locationId() != null) {
                loc = locationService.getLocationById(request.locationId());
            } else if (request.locationCode() != null && !request.locationCode().isBlank()) {
                loc =
                        locationService.getLocationByCode(
                                request.locationCode().trim().toUpperCase(Locale.ROOT));
            } else {
                throw new InvalidParameterException(
                        ErrorCode.SUBSCRIPTION_VALIDATION,
                        "Đăng ký theo STATION bắt buộc cung cấp locationId hoặc locationCode");
            }
            resolvedLocationId = loc.id();
            resolvedLocationCode = loc.code();
            lng = loc.longitude();
            lat = loc.latitude();
            resolvedLabel =
                    request.label() != null && !request.label().isBlank()
                            ? request.label().trim()
                            : loc.name() + " (" + loc.code() + ")";
        } else {
            if (request.longitude() == null || request.latitude() == null) {
                throw new InvalidParameterException(
                        ErrorCode.SUBSCRIPTION_VALIDATION,
                        "Đăng ký theo COORDINATE bắt buộc cung cấp kinh độ (longitude) và vĩ độ (latitude)");
            }
            lng = request.longitude();
            lat = request.latitude();
            if (!Double.isFinite(lng) || lng < -180.0 || lng > 180.0) {
                throw new InvalidParameterException(
                        ErrorCode.SUBSCRIPTION_VALIDATION,
                        "Kinh độ (longitude) phải nằm trong khoảng [-180, 180]");
            }
            if (!Double.isFinite(lat) || lat < -90.0 || lat > 90.0) {
                throw new InvalidParameterException(
                        ErrorCode.SUBSCRIPTION_VALIDATION,
                        "Vĩ độ (latitude) phải nằm trong khoảng [-90, 90]");
            }
            resolvedLabel =
                    request.label() != null && !request.label().isBlank()
                            ? request.label().trim()
                            : String.format(Locale.ROOT, "Tọa độ %.4f°N, %.4f°E", lat, lng);
        }

        Point point = GEO_FACTORY.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);

        String rawOtp = SubscriptionTokenCodec.generateSixDigitOtp();
        String rawVerifyToken = SubscriptionTokenCodec.generateUrlSafeToken();

        EmailSubscriptionEntity entity =
                "STATION".equals(scopeType)
                        ? subscriptionRepository
                                .findFirstByEmailAndScopeTypeAndLocationIdAndStatusInOrderByCreatedAtDesc(
                                        email,
                                        "STATION",
                                        resolvedLocationId,
                                        List.of("PENDING", "ACTIVE"))
                                .orElseGet(EmailSubscriptionEntity::new)
                        : new EmailSubscriptionEntity();

        entity.setEmail(email);
        entity.setScopeType(scopeType);
        entity.setLocationId(resolvedLocationId);
        entity.setTargetGeom(point);
        entity.setRadiusKm(radiusKm);
        entity.setMinSeverity(minSeverity);
        entity.setLabel(resolvedLabel);
        entity.setStatus("PENDING");
        entity.setOtpHash(SubscriptionTokenCodec.sha256Hex(rawOtp));
        entity.setVerifyTokenHash(SubscriptionTokenCodec.sha256Hex(rawVerifyToken));
        entity.setVerifyExpiresAt(now.plusMinutes(properties.otpTtlMinutes()));
        if (entity.getUnsubscribeToken() == null || entity.getUnsubscribeToken().isBlank()) {
            entity.setUnsubscribeToken(SubscriptionTokenCodec.generateUrlSafeToken());
        }
        entity.setVerifiedAt(null);
        entity.setUnsubscribedAt(null);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        EmailSubscriptionEntity saved = subscriptionRepository.saveAndFlush(entity);

        DispatchOutcome outcome =
                emailDispatchService.sendVerificationEmail(saved, rawOtp, rawVerifyToken);
        recordLog(saved, null, "VERIFICATION", outcome, now);

        return toResponse(saved, resolvedLocationCode, outcome.deliveryStatus(), false);
    }

    @Override
    @Transactional
    public SubscriptionResponse verifyByOtp(UUID subscriptionId, String otp) {
        EmailSubscriptionEntity sub =
                subscriptionRepository
                        .findById(subscriptionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                ErrorCode.SUBSCRIPTION_NOT_FOUND,
                                                "Không tìm thấy đăng ký với ID: "
                                                        + subscriptionId));

        if ("ACTIVE".equals(sub.getStatus())) {
            return toResponse(sub, resolveLocationCode(sub), "ALREADY_ACTIVE", true);
        }
        if ("UNSUBSCRIBED".equals(sub.getStatus())) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_INVALID_TOKEN,
                    "Đăng ký này đã bị hủy trước đó, vui lòng tạo đăng ký mới");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (sub.getVerifyExpiresAt() == null || now.isAfter(sub.getVerifyExpiresAt())) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_INVALID_TOKEN,
                    "Mã xác thực OTP đã hết hạn, vui lòng gửi lại yêu cầu đăng ký");
        }

        String candidateHash = SubscriptionTokenCodec.sha256Hex(otp.trim());
        if (sub.getOtpHash() == null || !sub.getOtpHash().equals(candidateHash)) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_INVALID_TOKEN, "Mã xác thực OTP không chính xác");
        }

        return activateSubscription(sub, now);
    }

    @Override
    @Transactional
    public SubscriptionResponse verifyByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_INVALID_TOKEN, "Liên kết xác thực không hợp lệ");
        }
        String tokenHash = SubscriptionTokenCodec.sha256Hex(rawToken.trim());
        EmailSubscriptionEntity sub =
                subscriptionRepository
                        .findByVerifyTokenHash(tokenHash)
                        .orElseThrow(
                                () ->
                                        new InvalidParameterException(
                                                ErrorCode.SUBSCRIPTION_INVALID_TOKEN,
                                                "Liên kết xác thực không tồn tại hoặc đã được sử dụng"));

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (sub.getVerifyExpiresAt() == null || now.isAfter(sub.getVerifyExpiresAt())) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_INVALID_TOKEN,
                    "Liên kết xác thực đã hết hạn, vui lòng đăng ký lại");
        }

        return activateSubscription(sub, now);
    }

    @Override
    @Transactional
    public SubscriptionResponse unsubscribeByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_INVALID_TOKEN, "Liên kết hủy đăng ký không hợp lệ");
        }
        EmailSubscriptionEntity sub =
                subscriptionRepository
                        .findByUnsubscribeToken(rawToken.trim())
                        .orElseThrow(
                                () ->
                                        new InvalidParameterException(
                                                ErrorCode.SUBSCRIPTION_INVALID_TOKEN,
                                                "Liên kết hủy đăng ký không hợp lệ hoặc đã được xử lý"));

        OffsetDateTime now = OffsetDateTime.now(clock);
        sub.setStatus("UNSUBSCRIBED");
        sub.setUnsubscribedAt(now);
        sub.setOtpHash(null);
        sub.setVerifyTokenHash(null);
        sub.setVerifyExpiresAt(null);
        sub.setUnsubscribeToken(SubscriptionTokenCodec.generateUrlSafeToken());
        sub.setUpdatedAt(now);
        EmailSubscriptionEntity saved = subscriptionRepository.saveAndFlush(sub);

        log.info(
                "Đã hủy đăng ký nhận cảnh báo email id={} label={}",
                saved.getId(),
                saved.getLabel());
        return toResponse(saved, resolveLocationCode(saved), "UNSUBSCRIBED", false);
    }

    @Override
    @Transactional
    public SubscriptionResponse sendTestNotification(UUID subscriptionId, String unsubscribeToken) {
        EmailSubscriptionEntity sub =
                subscriptionRepository
                        .findById(subscriptionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                ErrorCode.SUBSCRIPTION_NOT_FOUND,
                                                "Không tìm thấy đăng ký với ID: "
                                                        + subscriptionId));

        if (!"ACTIVE".equals(sub.getStatus())) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_NOT_ACTIVE,
                    "Vui lòng xác thực email trước khi gửi bản tin kiểm tra kết nối");
        }
        if (unsubscribeToken == null
                || unsubscribeToken.isBlank()
                || !sub.getUnsubscribeToken().equals(unsubscribeToken.trim())) {
            throw new InvalidParameterException(
                    ErrorCode.SUBSCRIPTION_INVALID_TOKEN,
                    "Token xác nhận quyền sở hữu đăng ký không hợp lệ");
        }

        SpatialPointContextResponse spatialContext =
                locationService.getSpatialPointContext(
                        sub.getTargetGeom().getX(),
                        sub.getTargetGeom().getY(),
                        Math.min(Math.max(sub.getRadiusKm(), 10.0), 2000.0));

        OffsetDateTime now = OffsetDateTime.now(clock);
        DispatchOutcome outcome = emailDispatchService.sendTestConnectionEmail(sub, spatialContext);
        recordLog(sub, null, "TEST_CHECK", outcome, now);

        return toResponse(sub, resolveLocationCode(sub), outcome.deliveryStatus(), true);
    }

    @Override
    public int dispatchForPublishedAlert(AlertPublishedEvent event) {
        if (event == null || event.alertId() == null) {
            return 0;
        }
        int rank =
                SEVERITY_RANKS.getOrDefault(
                        event.severityLevel() == null
                                ? "WARNING"
                                : event.severityLevel().toUpperCase(Locale.ROOT),
                        2);

        List<EmailSubscriptionEntity> matches =
                subscriptionRepository.findMatchingActiveSubscriptions(
                        event.longitude(), event.latitude(), event.locationId(), rank);
        if (matches.isEmpty()) {
            return 0;
        }

        int dispatchedCount = 0;
        for (EmailSubscriptionEntity sub : matches) {
            try {
                Boolean sentThisTurn =
                        requiresNewTx.execute(
                                status -> {
                                    if (logRepository.existsBySubscriptionIdAndAlertId(
                                            sub.getId(), event.alertId())) {
                                        return false;
                                    }
                                    DispatchOutcome outcome =
                                            emailDispatchService.sendProactiveAlertEmail(
                                                    sub, event);
                                    int inserted =
                                            logRepository.insertAlertNotificationIdempotent(
                                                    sub.getId(),
                                                    event.alertId(),
                                                    sub.getEmail(),
                                                    outcome.subject(),
                                                    outcome.deliveryStatus(),
                                                    outcome.errorMessage());
                                    return inserted > 0;
                                });
                if (Boolean.TRUE.equals(sentThisTurn)) {
                    dispatchedCount++;
                }
            } catch (RuntimeException ex) {
                log.error(
                        "Lỗi gửi thông báo email cảnh báo alertId={} subscriptionId={}: {}",
                        event.alertId(),
                        sub.getId(),
                        ex.getMessage());
            }
        }
        return dispatchedCount;
    }

    private SubscriptionResponse activateSubscription(
            EmailSubscriptionEntity sub, OffsetDateTime now) {
        sub.setStatus("ACTIVE");
        sub.setVerifiedAt(now);
        sub.setOtpHash(null);
        sub.setVerifyTokenHash(null);
        sub.setVerifyExpiresAt(null);
        sub.setUpdatedAt(now);
        EmailSubscriptionEntity saved = subscriptionRepository.saveAndFlush(sub);

        DispatchOutcome welcomeOutcome = emailDispatchService.sendWelcomeEmail(saved);
        recordLog(saved, null, "WELCOME", welcomeOutcome, now);

        return toResponse(saved, resolveLocationCode(saved), welcomeOutcome.deliveryStatus(), true);
    }

    private void recordLog(
            EmailSubscriptionEntity sub,
            UUID alertId,
            String notificationType,
            DispatchOutcome outcome,
            OffsetDateTime now) {
        EmailNotificationLogEntity entry = new EmailNotificationLogEntity();
        entry.setSubscriptionId(sub.getId());
        entry.setAlertId(alertId);
        entry.setNotificationType(notificationType);
        entry.setRecipientEmail(sub.getEmail());
        entry.setSubject(outcome.subject());
        entry.setDeliveryStatus(outcome.deliveryStatus());
        entry.setErrorMessage(outcome.errorMessage());
        entry.setSentAt(now);
        logRepository.save(entry);
    }

    private String resolveLocationCode(EmailSubscriptionEntity sub) {
        if (sub.getLocationId() == null) {
            return null;
        }
        try {
            return locationService.getLocationById(sub.getLocationId()).code();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static SubscriptionResponse toResponse(
            EmailSubscriptionEntity entity,
            String locationCode,
            String deliveryMode,
            boolean includeUnsubscribeToken) {
        return new SubscriptionResponse(
                entity.getId(),
                SubscriptionTokenCodec.maskEmail(entity.getEmail()),
                entity.getScopeType(),
                entity.getLocationId(),
                locationCode,
                entity.getLabel(),
                entity.getTargetGeom().getX(),
                entity.getTargetGeom().getY(),
                entity.getRadiusKm(),
                entity.getMinSeverity(),
                entity.getStatus(),
                deliveryMode,
                entity.getVerifyExpiresAt(),
                entity.getVerifiedAt(),
                includeUnsubscribeToken ? entity.getUnsubscribeToken() : null);
    }
}
