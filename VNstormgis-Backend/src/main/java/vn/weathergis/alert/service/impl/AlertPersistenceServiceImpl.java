package vn.weathergis.alert.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.weathergis.alert.config.AlertRetryProperties;
import vn.weathergis.alert.domain.AlertEntity;
import vn.weathergis.alert.domain.AlertGenerationJobEntity;
import vn.weathergis.alert.domain.AlertJobStatus;
import vn.weathergis.alert.domain.AlertViolationKeyEntity;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.RepresentativeThresholdSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.dto.VerifiedAlertSubmission;
import vn.weathergis.alert.exception.AlertJobConflictException;
import vn.weathergis.alert.repository.AlertGenerationJobRepository;
import vn.weathergis.alert.repository.AlertRepository;
import vn.weathergis.alert.repository.AlertThresholdRepository;
import vn.weathergis.alert.repository.AlertViolationKeyRepository;
import vn.weathergis.alert.service.AlertPersistenceService;
import vn.weathergis.automation.event.ThresholdViolatedEvent;
import vn.weathergis.automation.event.ThresholdViolatedEvent.Violation;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.ResourceNotFoundException;
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.storm.repository.StormRepository;

@Service
public class AlertPersistenceServiceImpl implements AlertPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AlertPersistenceServiceImpl.class);

    private static final GeometryFactory GEO_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private static final Map<String, String> EXPECTED_UNITS =
            Map.of(
                    "rain_1h", "mm",
                    "rain_3h", "mm",
                    "wind_speed", "m/s",
                    "wind_gust", "m/s",
                    "storm_dist", "km");

    private static final Set<String> VALID_OPERATORS = Set.of(">=", ">", "<=", "<", "=");

    private static final Map<String, Integer> SEVERITY_RANKS =
            Map.of(
                    "INFO", 1,
                    "WARNING", 2,
                    "DANGER", 3,
                    "CRITICAL", 4);

    private static final Comparator<Violation> REPRESENTATIVE_COMPARATOR =
            Comparator.comparingInt((Violation v) -> SEVERITY_RANKS.get(v.severityLevel()))
                    .reversed()
                    .thenComparingInt(Violation::thresholdId)
                    .thenComparing(Violation::violationKey);

    private final AlertRepository alertRepository;
    private final AlertGenerationJobRepository jobRepository;
    private final AlertViolationKeyRepository keyRepository;
    private final AlertThresholdRepository thresholdRepository;
    private final LocationRepository locationRepository;
    private final StormRepository stormRepository;
    private final ObjectMapper objectMapper;
    private final AlertRetryProperties retryProperties;
    private final Clock clock;
    private final TransactionTemplate requiredTx;
    private final TransactionTemplate requiresNewTx;

    public AlertPersistenceServiceImpl(
            AlertRepository alertRepository,
            AlertGenerationJobRepository jobRepository,
            AlertViolationKeyRepository keyRepository,
            AlertThresholdRepository thresholdRepository,
            LocationRepository locationRepository,
            StormRepository stormRepository,
            ObjectMapper objectMapper,
            AlertRetryProperties retryProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.jobRepository = jobRepository;
        this.keyRepository = keyRepository;
        this.thresholdRepository = thresholdRepository;
        this.locationRepository = locationRepository;
        this.stormRepository = stormRepository;
        this.objectMapper = objectMapper;
        this.retryProperties = retryProperties;
        this.clock = clock;
        this.requiredTx = new TransactionTemplate(transactionManager);
        this.requiredTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public Optional<AlertGenerationJobView> intake(ThresholdViolatedEvent event) {
        List<Violation> deduplicatedViolations = validateAndNormalizeEvent(event);
        return requiredTx.execute(status -> intakeInTx(event, deduplicatedViolations));
    }

    private Optional<AlertGenerationJobView> intakeInTx(
            ThresholdViolatedEvent event, List<Violation> deduplicatedViolations) {
        jobRepository.acquireStationLock(stationLockKey(event.locationId()));

        List<String> candidateKeys =
                deduplicatedViolations.stream().map(Violation::violationKey).toList();
        Set<String> existingKeys = keyRepository.findExistingKeys(candidateKeys);

        List<Violation> unclaimedViolations =
                deduplicatedViolations.stream()
                        .filter(v -> !existingKeys.contains(v.violationKey()))
                        .toList();
        if (unclaimedViolations.isEmpty()) {
            return Optional.empty();
        }

        Violation representative =
                unclaimedViolations.stream().min(REPRESENTATIVE_COMPARATOR).orElseThrow();
        String stormId =
                unclaimedViolations.stream()
                        .sorted(REPRESENTATIVE_COMPARATOR)
                        .map(Violation::stormId)
                        .filter(id -> id != null && !id.isBlank())
                        .findFirst()
                        .orElse(null);
        String triggerReason = buildTriggerReason(unclaimedViolations);
        AlertFactualSnapshot snapshot = buildSnapshot(event, representative, unclaimedViolations);

        OffsetDateTime now = nowUtc();
        AlertGenerationJobEntity job = new AlertGenerationJobEntity();
        job.setLocationId(event.locationId());
        job.setEvaluationCycleId(event.evaluationCycleId());
        job.setRepresentativeThresholdId(representative.thresholdId());
        job.setStormId(stormId);
        job.setSeverityLevel(representative.severityLevel());
        job.setTriggerReason(triggerReason);
        job.setFactualSnapshot(serializeSnapshot(snapshot));
        job.setStatus(AlertJobStatus.PENDING);
        job.setAttemptCount(0);
        job.setTriggeredAt(OffsetDateTime.ofInstant(event.evaluatedAt(), ZoneOffset.UTC));
        job.setCreatedAt(now);

        AlertGenerationJobEntity savedJob = jobRepository.saveAndFlush(job);

        List<AlertViolationKeyEntity> keyEntities =
                unclaimedViolations.stream()
                        .map(
                                v ->
                                        new AlertViolationKeyEntity(
                                                v.violationKey(), savedJob.getId(), now))
                        .toList();
        keyRepository.saveAllAndFlush(keyEntities);

        List<String> savedKeys =
                keyEntities.stream()
                        .map(AlertViolationKeyEntity::getViolationKey)
                        .sorted()
                        .toList();
        return Optional.of(toJobView(savedJob, snapshot, savedKeys));
    }

    @Override
    public AlertGenerationJobView recordVerifiedContent(VerifiedAlertSubmission submission) {
        validateSubmissionFields(submission);
        return requiresNewTx.execute(status -> recordVerifiedContentInTx(submission));
    }

    @Override
    public UUID publishReadyJob(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId là bắt buộc");
        }
        try {
            return requiresNewTx.execute(status -> publishInTx(jobId, false));
        } catch (RuntimeException ex) {
            handlePublishFailure(jobId, ex);
            throw ex;
        }
    }

    @Override
    public UUID submitAndPublish(VerifiedAlertSubmission submission) {
        AlertGenerationJobView readyOrCompleted = recordVerifiedContent(submission);
        if (readyOrCompleted.status() == AlertJobStatus.COMPLETED
                && readyOrCompleted.alertId() != null) {
            return readyOrCompleted.alertId();
        }
        return publishReadyJob(readyOrCompleted.jobId());
    }

    @Override
    public int retryDueReadyJobs() {
        OffsetDateTime now = nowUtc();
        int batchSize = Math.max(1, retryProperties.getBatchSize());
        List<UUID> dueJobIds = jobRepository.findDueReadyJobIds(now, PageRequest.of(0, batchSize));
        int publishedCount = 0;
        for (UUID jobId : dueJobIds) {
            try {
                UUID alertId = requiresNewTx.execute(status -> publishInTx(jobId, true));
                if (alertId != null) {
                    publishedCount++;
                }
            } catch (RuntimeException ex) {
                handlePublishFailure(jobId, ex);
            }
        }
        return publishedCount;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AlertGenerationJobView> findJobById(UUID jobId) {
        if (jobId == null) {
            return Optional.empty();
        }
        return jobRepository.findById(jobId).map(this::toJobView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertGenerationJobView> findPendingJobs() {
        return jobRepository.findByStatusOrderByCreatedAtAscIdAsc(AlertJobStatus.PENDING).stream()
                .map(this::toJobView)
                .toList();
    }

    private AlertGenerationJobView recordVerifiedContentInTx(VerifiedAlertSubmission submission) {
        AlertGenerationJobEntity job =
                jobRepository
                        .findByIdForUpdate(submission.jobId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                ErrorCode.ALERT_NOT_FOUND,
                                                "Không tìm thấy công việc cảnh báo: "
                                                        + submission.jobId()));

        List<String> ownedKeys =
                keyRepository.findByJobIdOrderByViolationKeyAsc(job.getId()).stream()
                        .map(AlertViolationKeyEntity::getViolationKey)
                        .toList();
        AlertFactualSnapshot storedSnapshot = deserializeSnapshot(job.getFactualSnapshot());

        Set<String> submittedKeySet = new LinkedHashSet<>(submission.violationKeys());
        Set<String> ownedKeySet = new LinkedHashSet<>(ownedKeys);
        if (submittedKeySet.size() != submission.violationKeys().size()
                || !submittedKeySet.equals(ownedKeySet)) {
            logConflict(job, "ViolationKeysMismatch");
            throw new AlertJobConflictException(
                    "Tập violationKeys không khớp với công việc: " + job.getId());
        }
        if (!submission.factualSnapshot().equals(storedSnapshot)) {
            logConflict(job, "FactualSnapshotMismatch");
            throw new AlertJobConflictException(
                    "Bản chụp dữ kiện không khớp với công việc: " + job.getId());
        }

        String trimmedTitle = submission.title().trim();
        String trimmedExplanation = submission.aiExplanation().trim();
        Boolean fallbackFlag = submission.isFallback();

        if (job.getStatus() == AlertJobStatus.PENDING) {
            OffsetDateTime now = nowUtc();
            job.setStatus(AlertJobStatus.READY);
            job.setVerifiedTitle(trimmedTitle);
            job.setVerifiedAiExplanation(trimmedExplanation);
            job.setIsFallback(fallbackFlag);
            job.setReadyAt(now);
            job.setNextAttemptAt(now);
            job.setLastError(null);
            AlertGenerationJobEntity saved = jobRepository.saveAndFlush(job);
            return toJobView(saved, storedSnapshot, ownedKeys);
        }

        if (trimmedTitle.equals(job.getVerifiedTitle())
                && trimmedExplanation.equals(job.getVerifiedAiExplanation())
                && fallbackFlag.equals(job.getIsFallback())) {
            return toJobView(job, storedSnapshot, ownedKeys);
        }

        logConflict(job, "VerifiedContentConflict");
        throw new AlertJobConflictException(
                "Nội dung kiểm chứng mâu thuẫn với trạng thái đã ghi nhận của công việc: "
                        + job.getId());
    }

    private UUID publishInTx(UUID jobId, boolean skipLocked) {
        Optional<AlertGenerationJobEntity> locked =
                skipLocked
                        ? jobRepository.findByIdForUpdateSkipLocked(jobId)
                        : jobRepository.findByIdForUpdate(jobId);
        if (locked.isEmpty()) {
            if (skipLocked) {
                return null;
            }
            throw new ResourceNotFoundException(
                    ErrorCode.ALERT_NOT_FOUND, "Không tìm thấy công việc cảnh báo: " + jobId);
        }

        AlertGenerationJobEntity job = locked.get();
        if (job.getStatus() == AlertJobStatus.COMPLETED) {
            if (job.getAlertId() == null) {
                throw new IllegalStateException(
                        "Công việc COMPLETED nhưng thiếu alert_id: " + jobId);
            }
            return job.getAlertId();
        }
        if (job.getStatus() != AlertJobStatus.READY) {
            if (skipLocked) {
                return null;
            }
            throw new IllegalStateException(
                    "Chỉ xuất bản được công việc ở trạng thái READY, nhận: " + job.getStatus());
        }
        if (skipLocked
                && job.getNextAttemptAt() != null
                && job.getNextAttemptAt().isAfter(nowUtc())) {
            return null;
        }

        validateReadyJobForPublication(job);
        AlertFactualSnapshot snapshot = deserializeSnapshot(job.getFactualSnapshot());
        validateSnapshotForPublication(snapshot);

        List<AlertViolationKeyEntity> ownedKeys =
                keyRepository.findByJobIdOrderByViolationKeyAsc(jobId);
        if (ownedKeys.isEmpty()) {
            throw new IllegalStateException("Công việc không có violation_key: " + jobId);
        }
        for (AlertViolationKeyEntity keyEntity : ownedKeys) {
            if (keyEntity.getAlertId() != null) {
                throw new IllegalStateException(
                        "Khóa vi phạm đã được gắn với cảnh báo khác: "
                                + keyEntity.getViolationKey());
            }
        }

        Integer thresholdId =
                job.getRepresentativeThresholdId() != null
                                && thresholdRepository.existsById(
                                        job.getRepresentativeThresholdId())
                        ? job.getRepresentativeThresholdId()
                        : null;
        UUID locationId =
                job.getLocationId() != null && locationRepository.existsById(job.getLocationId())
                        ? job.getLocationId()
                        : null;
        String stormId =
                job.getStormId() != null && stormRepository.existsById(job.getStormId())
                        ? job.getStormId()
                        : null;

        double longitude = snapshot.coordinates().get(0);
        double latitude = snapshot.coordinates().get(1);
        Point affectedPoint = GEO_FACTORY.createPoint(new Coordinate(longitude, latitude));
        affectedPoint.setSRID(4326);

        OffsetDateTime now = nowUtc();
        AlertEntity alert = new AlertEntity();
        alert.setThresholdId(thresholdId);
        alert.setLocationId(locationId);
        alert.setStormId(stormId);
        alert.setTitle(job.getVerifiedTitle());
        alert.setSeverityLevel(job.getSeverityLevel());
        alert.setTriggerReason(job.getTriggerReason());
        alert.setAiExplanation(job.getVerifiedAiExplanation());
        alert.setIsFallback(job.getIsFallback());
        alert.setFactualSnapshot(job.getFactualSnapshot());
        alert.setAffectedGeom(affectedPoint);
        alert.setStatus("active");
        alert.setTriggeredAt(job.getTriggeredAt());
        alert.setResolvedAt(null);
        alert.setCreatedAt(now);

        AlertEntity savedAlert = alertRepository.saveAndFlush(alert);

        int linkedCount = keyRepository.linkKeysToAlert(jobId, savedAlert.getId());
        if (linkedCount != ownedKeys.size()) {
            throw new IllegalStateException(
                    "Không thể gắn đầy đủ khóa vi phạm cho cảnh báo: expected="
                            + ownedKeys.size()
                            + ", actual="
                            + linkedCount);
        }

        job.setStatus(AlertJobStatus.COMPLETED);
        job.setAlertId(savedAlert.getId());
        job.setCompletedAt(now);
        job.setNextAttemptAt(null);
        job.setLastError(null);
        jobRepository.saveAndFlush(job);

        return savedAlert.getId();
    }

    private void handlePublishFailure(UUID jobId, RuntimeException ex) {
        String errorType = ex.getClass().getSimpleName();
        try {
            requiresNewTx.executeWithoutResult(
                    status ->
                            jobRepository
                                    .findByIdForUpdate(jobId)
                                    .ifPresent(
                                            job -> {
                                                log.error(
                                                        "alert_publication_failed jobId={} cycleId={} locationId={} status={} error={}",
                                                        job.getId(),
                                                        job.getEvaluationCycleId(),
                                                        job.getLocationId(),
                                                        job.getStatus(),
                                                        errorType);
                                                if (job.getStatus() == AlertJobStatus.READY) {
                                                    int nextAttempt = job.getAttemptCount() + 1;
                                                    long backoffMs =
                                                            retryProperties.computeBackoffMs(
                                                                    nextAttempt);
                                                    job.setAttemptCount(nextAttempt);
                                                    job.setLastError(errorType);
                                                    job.setNextAttemptAt(
                                                            nowUtc().plus(
                                                                            Duration.ofMillis(
                                                                                    backoffMs)));
                                                    jobRepository.saveAndFlush(job);
                                                }
                                            }));
        } catch (RuntimeException retryUpdateEx) {
            log.error(
                    "alert_publication_retry_update_failed jobId={} primaryError={} retryUpdateError={}",
                    jobId,
                    errorType,
                    retryUpdateEx.getClass().getSimpleName());
        }
    }

    private void logConflict(AlertGenerationJobEntity job, String conflictReason) {
        log.error(
                "alert_submission_conflict jobId={} cycleId={} locationId={} status={} error={}",
                job.getId(),
                job.getEvaluationCycleId(),
                job.getLocationId(),
                job.getStatus(),
                conflictReason);
    }

    private static List<Violation> validateAndNormalizeEvent(ThresholdViolatedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("ThresholdViolatedEvent không được null");
        }
        if (event.locationId() == null) {
            throw new IllegalArgumentException("locationId là bắt buộc");
        }
        if (event.locationCode() == null || event.locationCode().isBlank()) {
            throw new IllegalArgumentException("locationCode là bắt buộc");
        }
        if (event.locationName() == null || event.locationName().isBlank()) {
            throw new IllegalArgumentException("locationName là bắt buộc");
        }
        if (!Double.isFinite(event.longitude())
                || !Double.isFinite(event.latitude())
                || Math.abs(event.longitude()) > 180.0
                || Math.abs(event.latitude()) > 90.0) {
            throw new IllegalArgumentException("Tọa độ trạm không hợp lệ");
        }
        if (event.evaluationCycleId() == null) {
            throw new IllegalArgumentException("evaluationCycleId là bắt buộc");
        }
        if (event.evaluatedAt() == null) {
            throw new IllegalArgumentException("evaluatedAt là bắt buộc");
        }
        if (event.violations() == null || event.violations().isEmpty()) {
            throw new IllegalArgumentException("Danh sách vi phạm không được rỗng");
        }

        Map<String, Violation> deduplicated = new LinkedHashMap<>();
        for (Violation v : event.violations()) {
            validateViolation(v);
            deduplicated.putIfAbsent(v.violationKey(), v);
        }
        return new ArrayList<>(deduplicated.values());
    }

    private static void validateViolation(Violation v) {
        if (v == null) {
            throw new IllegalArgumentException("Violation không được null");
        }
        if (v.thresholdId() == null || v.thresholdId() <= 0) {
            throw new IllegalArgumentException("thresholdId phải là số nguyên dương");
        }
        String expectedUnit = v.parameter() == null ? null : EXPECTED_UNITS.get(v.parameter());
        if (expectedUnit == null) {
            throw new IllegalArgumentException("parameter không hợp lệ: " + v.parameter());
        }
        if (v.operator() == null || !VALID_OPERATORS.contains(v.operator())) {
            throw new IllegalArgumentException("operator không hợp lệ: " + v.operator());
        }
        if (v.severityLevel() == null || !SEVERITY_RANKS.containsKey(v.severityLevel())) {
            throw new IllegalArgumentException("severityLevel không hợp lệ: " + v.severityLevel());
        }
        if (!Double.isFinite(v.thresholdValue()) || v.thresholdValue() < 0.0) {
            throw new IllegalArgumentException("thresholdValue phải là số hữu hạn không âm");
        }
        if (!Double.isFinite(v.measuredValue())) {
            throw new IllegalArgumentException("measuredValue phải là số hữu hạn");
        }
        if (!expectedUnit.equals(v.unit())) {
            throw new IllegalArgumentException("unit không khớp với parameter: " + v.unit());
        }
        if (v.sourceTimestamp() == null || v.evaluationWindow() == null) {
            throw new IllegalArgumentException("sourceTimestamp và evaluationWindow là bắt buộc");
        }
        if (v.violationKey() == null
                || v.violationKey().isBlank()
                || v.violationKey().length() > 255) {
            throw new IllegalArgumentException("violationKey không hợp lệ");
        }
        if ("storm_dist".equals(v.parameter())) {
            if (v.stormId() == null
                    || v.stormId().isBlank()
                    || v.stormName() == null
                    || v.stormName().isBlank()
                    || v.distanceKm() == null
                    || !Double.isFinite(v.distanceKm())
                    || v.distanceKm() < 0.0) {
                throw new IllegalArgumentException("Vi phạm storm_dist thiếu thông tin bão hợp lệ");
            }
        }
    }

    private static void validateSubmissionFields(VerifiedAlertSubmission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("VerifiedAlertSubmission không được null");
        }
        if (submission.jobId() == null) {
            throw new IllegalArgumentException("jobId là bắt buộc");
        }
        if (submission.title() == null || submission.title().trim().isEmpty()) {
            throw new IllegalArgumentException("title không được để trống");
        }
        if (submission.title().trim().length() > 255) {
            throw new IllegalArgumentException("title không được vượt quá 255 ký tự");
        }
        if (submission.aiExplanation() == null || submission.aiExplanation().trim().isEmpty()) {
            throw new IllegalArgumentException("ai_explanation không được để trống");
        }
        if (submission.isFallback() == null) {
            throw new IllegalArgumentException("is_fallback phải được truyền tường minh");
        }
        if (submission.violationKeys() == null || submission.violationKeys().isEmpty()) {
            throw new IllegalArgumentException("violationKeys không được rỗng");
        }
        if (submission.factualSnapshot() == null) {
            throw new IllegalArgumentException("factualSnapshot là bắt buộc");
        }
    }

    private static void validateReadyJobForPublication(AlertGenerationJobEntity job) {
        if (job.getVerifiedTitle() == null
                || job.getVerifiedTitle().isBlank()
                || job.getVerifiedTitle().length() > 255) {
            throw new IllegalStateException("verified_title không hợp lệ");
        }
        if (job.getVerifiedAiExplanation() == null || job.getVerifiedAiExplanation().isBlank()) {
            throw new IllegalStateException("verified_ai_explanation không hợp lệ");
        }
        if (job.getIsFallback() == null) {
            throw new IllegalStateException("is_fallback chưa được xác định");
        }
        if (job.getTriggerReason() == null || job.getTriggerReason().isBlank()) {
            throw new IllegalStateException("trigger_reason không hợp lệ");
        }
        if (job.getSeverityLevel() == null || !SEVERITY_RANKS.containsKey(job.getSeverityLevel())) {
            throw new IllegalStateException("severity_level không hợp lệ");
        }
    }

    private static void validateSnapshotForPublication(AlertFactualSnapshot snapshot) {
        if (snapshot == null
                || snapshot.coordinates() == null
                || snapshot.coordinates().size() != 2
                || snapshot.coordinates().get(0) == null
                || snapshot.coordinates().get(1) == null
                || !Double.isFinite(snapshot.coordinates().get(0))
                || !Double.isFinite(snapshot.coordinates().get(1))
                || Math.abs(snapshot.coordinates().get(0)) > 180.0
                || Math.abs(snapshot.coordinates().get(1)) > 90.0
                || snapshot.violations() == null
                || snapshot.violations().isEmpty()) {
            throw new IllegalStateException("factual_snapshot không hợp lệ để xuất bản");
        }
    }

    public static String buildTriggerReason(List<Violation> violations) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < violations.size(); i++) {
            Violation v = violations.get(i);
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(v.parameter())
                    .append(" đo được ")
                    .append(v.measuredValue())
                    .append(' ')
                    .append(v.unit())
                    .append(", đạt điều kiện ")
                    .append(v.operator())
                    .append(' ')
                    .append(v.thresholdValue())
                    .append(' ')
                    .append(v.unit())
                    .append('.');
        }
        return sb.toString();
    }

    private static AlertFactualSnapshot buildSnapshot(
            ThresholdViolatedEvent event,
            Violation representative,
            List<Violation> ownedViolations) {
        RepresentativeThresholdSnapshot repSnapshot =
                new RepresentativeThresholdSnapshot(
                        representative.thresholdId(),
                        representative.parameter(),
                        representative.operator(),
                        representative.thresholdValue(),
                        representative.severityLevel(),
                        representative.unit(),
                        representative.violationKey());
        List<ViolationSnapshot> violationSnapshots =
                ownedViolations.stream()
                        .map(
                                v ->
                                        new ViolationSnapshot(
                                                v.thresholdId(),
                                                v.parameter(),
                                                v.operator(),
                                                v.thresholdValue(),
                                                v.severityLevel(),
                                                v.measuredValue(),
                                                v.unit(),
                                                v.sourceTimestamp(),
                                                v.evaluationWindow(),
                                                v.violationKey(),
                                                v.stormId(),
                                                v.stormName(),
                                                v.distanceKm()))
                        .toList();
        return new AlertFactualSnapshot(
                event.locationId(),
                event.locationCode(),
                event.locationName(),
                List.of(event.longitude(), event.latitude()),
                event.evaluationCycleId(),
                event.evaluatedAt(),
                repSnapshot,
                violationSnapshots);
    }

    private String serializeSnapshot(AlertFactualSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể serialize factual_snapshot", ex);
        }
    }

    private AlertFactualSnapshot deserializeSnapshot(String json) {
        try {
            return objectMapper.readValue(json, AlertFactualSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể đọc factual_snapshot từ CSDL", ex);
        }
    }

    private AlertGenerationJobView toJobView(AlertGenerationJobEntity job) {
        List<String> keys =
                keyRepository.findByJobIdOrderByViolationKeyAsc(job.getId()).stream()
                        .map(AlertViolationKeyEntity::getViolationKey)
                        .toList();
        AlertFactualSnapshot snapshot = deserializeSnapshot(job.getFactualSnapshot());
        return toJobView(job, snapshot, keys);
    }

    private static AlertGenerationJobView toJobView(
            AlertGenerationJobEntity job,
            AlertFactualSnapshot snapshot,
            List<String> violationKeys) {
        return new AlertGenerationJobView(
                job.getId(),
                job.getStatus(),
                job.getLocationId(),
                job.getEvaluationCycleId(),
                job.getRepresentativeThresholdId(),
                job.getStormId(),
                job.getSeverityLevel(),
                job.getTriggerReason(),
                job.getTriggeredAt().toInstant(),
                snapshot,
                violationKeys,
                job.getVerifiedTitle(),
                job.getVerifiedAiExplanation(),
                job.getIsFallback(),
                job.getAttemptCount(),
                job.getNextAttemptAt() == null ? null : job.getNextAttemptAt().toInstant(),
                job.getLastError(),
                job.getAlertId());
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    }

    private static long stationLockKey(UUID locationId) {
        return locationId.getMostSignificantBits() ^ locationId.getLeastSignificantBits();
    }
}
