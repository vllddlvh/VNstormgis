package vn.weathergis.alert.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.weathergis.alert.ai.AlertContentGuardrail;
import vn.weathergis.alert.ai.GeminiClient;
import vn.weathergis.alert.ai.GeminiGenerationResult.Outcome;
import vn.weathergis.alert.config.AlertWorkerProperties;
import vn.weathergis.alert.domain.AlertJobStatus;
import vn.weathergis.alert.dto.VerifiedAlertSubmission;

/** Claims one job per short transaction; no database lock is held during model calls. */
@Component
public class AlertGenerationWorker {
    private static final Logger LOG = LoggerFactory.getLogger(AlertGenerationWorker.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AlertPersistenceService persistence;
    private final GeminiClient gemini;
    private final AlertContentGuardrail guardrail;
    private final AlertWorkerProperties config;

    public AlertGenerationWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            AlertPersistenceService persistence,
            GeminiClient gemini,
            AlertContentGuardrail guardrail,
            AlertWorkerProperties config) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.persistence = persistence;
        this.gemini = gemini;
        this.guardrail = guardrail;
        this.config = config;
    }

    public record Claim(UUID jobId, UUID token) {}

    public Claim claim() {
        UUID token = UUID.randomUUID();
        return tx.execute(
                status -> {
                    List<UUID> ids =
                            jdbc.query(
                                    """
                    with candidate as (
                        select id from alert_generation_jobs
                        where status = 'PENDING'
                          and (lease_until is null or lease_until <= clock_timestamp())
                          and (quarantine_until is null or quarantine_until <= clock_timestamp())
                        order by created_at, id for update skip locked limit 1
                    )
                    update alert_generation_jobs j
                       set claim_token = ?, lease_until = clock_timestamp() + (? * interval '1 millisecond')
                      from candidate c where j.id = c.id returning j.id
                    """,
                                    (rs, row) -> rs.getObject(1, UUID.class),
                                    token,
                                    config.getLeaseMs());
                    return ids.isEmpty() ? null : new Claim(ids.get(0), token);
                });
    }

    @Scheduled(
            fixedDelayString = "${weathergis.alert.worker.fixed-delay-ms:30000}",
            initialDelayString = "${weathergis.alert.worker.initial-delay-ms:60000}")
    public void scheduled() {
        if (config.isEnabled()) runBatch();
    }

    public void runBatch() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            int claimed = 0;
            boolean empty = false;
            while (!empty && claimed < config.getBatchSize()) {
                List<Future<?>> running = new ArrayList<>();
                while (running.size() < config.getConcurrency()
                        && claimed < config.getBatchSize()) {
                    Claim claim;
                    try {
                        claim = claim();
                    } catch (RuntimeException e) {
                        LOG.error("alert_claim_failed error={}", e.getClass().getSimpleName());
                        empty = true;
                        break;
                    }
                    if (claim == null) {
                        empty = true;
                        break;
                    }
                    claimed++;
                    running.add(executor.submit(() -> process(claim)));
                }
                for (Future<?> future : running) {
                    try {
                        future.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } catch (java.util.concurrent.ExecutionException e) {
                        LOG.error("alert_worker_failed error={}", e.getClass().getSimpleName());
                    }
                }
            }
        }
    }

    public void process(Claim claim) {
        long start = System.nanoTime();
        String failureReason = "GENERATION_FAILED";
        try {
            var job = persistence.findJobById(claim.jobId()).orElseThrow();
            if (job.status() != AlertJobStatus.PENDING) return;
            failureReason = "INVALID_FACTUAL_DATA";
            var fallback = guardrail.fallback(job); // validates facts even when Gemini is disabled
            failureReason = "GENERATION_FAILED";
            vn.weathergis.alert.ai.GeminiGenerationResult result;
            try {
                result = gemini.generate(job);
            } catch (RuntimeException ex) {
                result = null;
            }
            if (result != null && result.outcome() == Outcome.SKIPPED) {
                var current = persistence.findJobById(claim.jobId());
                if (current.isEmpty() || current.get().status() != AlertJobStatus.PENDING) return;
                throw new IllegalStateException("Skipped pending job");
            }
            boolean accepted =
                    result != null
                            && result.outcome() == Outcome.DRAFT
                            && guardrail.accepts(job, result.draft());
            var content = accepted ? result.draft() : fallback;
            persistence.recordVerifiedContent(
                    new VerifiedAlertSubmission(
                            job.jobId(),
                            content.title(),
                            content.aiExplanation(),
                            !accepted,
                            job.violationKeys(),
                            job.factualSnapshot(),
                            claim.token()));
            persistence.publishReadyJob(job.jobId());
            LOG.info(
                    "alert_generation job={} outcome={} fallback={} result=published durationMs={}",
                    job.jobId(),
                    result == null ? "CLIENT_EXCEPTION" : result.outcome(),
                    !accepted,
                    (System.nanoTime() - start) / 1000000);
        } catch (RuntimeException ex) {
            LOG.warn(
                    "alert_generation job={} result=failed error={} durationMs={}",
                    claim.jobId(),
                    ex.getClass().getSimpleName(),
                    (System.nanoTime() - start) / 1000000);
            try {
                String reason =
                        ex instanceof vn.weathergis.alert.exception.InvalidFactualSnapshotException
                                ? "INVALID_FACTUAL_DATA"
                                : failureReason;
                tx.executeWithoutResult(
                        status ->
                                jdbc.update(
                                        """
                        update alert_generation_jobs set claim_token = null, lease_until = null,
                                    quarantine_until = clock_timestamp() + (? * interval '1 millisecond'),
                                    last_error = ?
                        where id = ? and status = 'PENDING' and claim_token = ?
                          and lease_until > clock_timestamp()
                        """,
                                        config.getQuarantineMs(),
                                        reason,
                                        claim.jobId(),
                                        claim.token()));
            } catch (RuntimeException dbError) {
                LOG.error(
                        "alert_quarantine_failed job={} error={}",
                        claim.jobId(),
                        dbError.getClass().getSimpleName());
            }
        }
    }
}
