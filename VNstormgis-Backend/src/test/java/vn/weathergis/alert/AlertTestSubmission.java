package vn.weathergis.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.weathergis.alert.ai.AlertContentGuardrail;
import vn.weathergis.alert.ai.AlertPromptBuilder;
import vn.weathergis.alert.dto.AlertGenerationJobView;
import vn.weathergis.alert.dto.VerifiedAlertSubmission;

/** Test fixture claims a specific job so tests remain independent of scheduler ordering. */
public final class AlertTestSubmission {
    private AlertTestSubmission() {}

    public static VerifiedAlertSubmission claim(
            AlertGenerationJobView job, boolean fallback, JdbcTemplate jdbc, ObjectMapper mapper) {
        UUID token = UUID.randomUUID();
        int changed =
                jdbc.update(
                        """
                update alert_generation_jobs set claim_token = ?,
                    lease_until = clock_timestamp() + interval '5 minutes'
                where id = ? and status = 'PENDING'
                """,
                        token,
                        job.jobId());
        if (changed != 1) throw new IllegalStateException("Job could not be claimed");
        var content = new AlertContentGuardrail(new AlertPromptBuilder(mapper)).fallback(job);
        return new VerifiedAlertSubmission(
                job.jobId(),
                content.title(),
                content.aiExplanation(),
                fallback,
                job.violationKeys(),
                job.factualSnapshot(),
                token);
    }
}
