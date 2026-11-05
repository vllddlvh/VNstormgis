package vn.weathergis.alert.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.dto.VerifiedAlertSubmission;
import vn.weathergis.automation.event.ThresholdViolatedEvent;

/**
 * Internal persistence contract for threshold violation intake, verified content recording, and
 * atomic alert publication.
 */
public interface AlertPersistenceService {

    /**
     * Persists a new {@code PENDING} job and all previously unclaimed violation keys for a station
     * within one transaction. Returns {@link Optional#empty()} when all keys in the event have
     * already been claimed.
     */
    Optional<AlertGenerationJobView> intake(ThresholdViolatedEvent event);

    /**
     * Validates a verified content submission against the persisted job's snapshot and owned keys,
     * transitioning {@code PENDING -> READY} in its own transaction.
     */
    AlertGenerationJobView recordVerifiedContent(VerifiedAlertSubmission submission);

    /**
     * Publishes a {@code READY} job into {@code alerts}, links all owned keys, and transitions the
     * job to {@code COMPLETED} in one short transaction. On failure, rolls back the publication
     * transaction and schedules a bounded-backoff retry while keeping the job in {@code READY}.
     */
    UUID publishReadyJob(UUID jobId);

    /**
     * Convenience entrypoint for W4-05: commits {@code PENDING -> READY} first, then executes
     * {@code READY -> COMPLETED} publication in a separate transaction.
     */
    UUID submitAndPublish(VerifiedAlertSubmission submission);

    /**
     * Retries due {@code READY} jobs using non-blocking row locking so concurrent instances never
     * publish the same job twice.
     */
    int retryDueReadyJobs();

    Optional<AlertGenerationJobView> findJobById(UUID jobId);

    List<AlertGenerationJobView> findPendingJobs();
}
