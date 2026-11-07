package vn.weathergis.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.weathergis.alert.ai.AlertContentGuardrail;
import vn.weathergis.alert.ai.GeminiClient;
import vn.weathergis.alert.ai.GeminiGenerationResult;
import vn.weathergis.alert.ai.GeminiGenerationResult.GeminiAlertDraft;
import vn.weathergis.alert.ai.GeminiGenerationResult.Outcome;
import vn.weathergis.alert.config.AlertWorkerProperties;
import vn.weathergis.alert.domain.AlertJobStatus;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.dto.VerifiedAlertSubmission;

class AlertGenerationWorkerTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void acceptsOnlyVerifiedDrafts(boolean accepted) {
        UUID jobId = UUID.randomUUID();
        var job = mock(AlertGenerationJobView.class);
        when(job.jobId()).thenReturn(jobId);
        when(job.status()).thenReturn(AlertJobStatus.PENDING);
        var persistence = mock(AlertPersistenceService.class);
        when(persistence.findJobById(jobId)).thenReturn(Optional.of(job));
        var client = mock(GeminiClient.class);
        var draft = new GeminiAlertDraft("AI", "draft");
        when(client.generate(job))
                .thenReturn(
                        new GeminiGenerationResult(
                                job, draft, Outcome.DRAFT, null, "test", Duration.ZERO, null));
        var guard = mock(AlertContentGuardrail.class);
        when(guard.fallback(job)).thenReturn(new GeminiAlertDraft("fallback", "facts"));
        when(guard.accepts(job, draft)).thenReturn(accepted);
        var worker =
                new AlertGenerationWorker(
                        mock(JdbcTemplate.class),
                        mock(PlatformTransactionManager.class),
                        persistence,
                        client,
                        guard,
                        new AlertWorkerProperties());
        worker.process(new AlertGenerationWorker.Claim(jobId, UUID.randomUUID()));
        var submission = ArgumentCaptor.forClass(VerifiedAlertSubmission.class);
        verify(persistence).recordVerifiedContent(submission.capture());
        assertThat(submission.getValue().isFallback()).isEqualTo(!accepted);
        assertThat(submission.getValue().title()).isEqualTo(accepted ? "AI" : "fallback");
    }

    @ParameterizedTest
    @EnumSource(
            value = Outcome.class,
            names = {"DRAFT", "SKIPPED"},
            mode = EnumSource.Mode.EXCLUDE)
    void everyFailureOutcomeUsesFallback(Outcome outcome) {
        UUID jobId = UUID.randomUUID();
        var job = mock(AlertGenerationJobView.class);
        when(job.jobId()).thenReturn(jobId);
        when(job.status()).thenReturn(AlertJobStatus.PENDING);
        when(job.violationKeys()).thenReturn(List.of("key"));
        var persistence = mock(AlertPersistenceService.class);
        when(persistence.findJobById(jobId)).thenReturn(Optional.of(job));
        var client = mock(GeminiClient.class);
        when(client.generate(job))
                .thenReturn(
                        new GeminiGenerationResult(
                                job, null, outcome, null, "test", Duration.ZERO, null));
        var guard = mock(AlertContentGuardrail.class);
        when(guard.fallback(job)).thenReturn(new GeminiAlertDraft("title", "explanation"));
        var worker =
                new AlertGenerationWorker(
                        mock(JdbcTemplate.class),
                        mock(PlatformTransactionManager.class),
                        persistence,
                        client,
                        guard,
                        new AlertWorkerProperties());
        worker.process(new AlertGenerationWorker.Claim(jobId, UUID.randomUUID()));
        var submission = ArgumentCaptor.forClass(VerifiedAlertSubmission.class);
        verify(persistence).recordVerifiedContent(submission.capture());
        assertThat(submission.getValue().isFallback()).isTrue();
        assertThat(submission.getValue().aiExplanation()).isEqualTo("explanation");
        verify(persistence).publishReadyJob(jobId);
        verify(client, times(1)).generate(any());
    }
}
