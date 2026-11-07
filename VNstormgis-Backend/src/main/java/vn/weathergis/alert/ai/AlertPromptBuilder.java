package vn.weathergis.alert.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.weathergis.alert.dto.AlertFactualSnapshot;
import vn.weathergis.alert.dto.AlertFactualSnapshot.ViolationSnapshot;
import vn.weathergis.alert.dto.AlertGenerationJobView;

@Component
public class AlertPromptBuilder {
    public static final String VERSION = "proactive-alert-v1";
    private static final Map<String, String> UNITS =
            Map.of(
                    "rain_1h",
                    "mm",
                    "rain_3h",
                    "mm",
                    "wind_speed",
                    "m/s",
                    "wind_gust",
                    "m/s",
                    "storm_dist",
                    "km");
    private static final Set<String> OPS = Set.of(">=", ">", "<=", "<", "=");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "DANGER", "CRITICAL");
    private final ObjectMapper mapper;

    public AlertPromptBuilder(ObjectMapper mapper) {
        this.mapper = mapper.copy();
    }

    public String build(AlertGenerationJobView job) {
        if (job == null || job.jobId() == null || job.factualSnapshot() == null)
            throw new IllegalArgumentException("Invalid facts");
        AlertFactualSnapshot s = job.factualSnapshot();
        if (s.locationId() == null
                || !s.locationId().equals(job.locationId())
                || blank(s.locationCode())
                || blank(s.locationName())
                || s.evaluationCycleId() == null
                || !s.evaluationCycleId().equals(job.evaluationCycleId())
                || s.evaluatedAt() == null
                || s.coordinates() == null
                || s.coordinates().size() != 2
                || !Double.isFinite(s.coordinates().get(0))
                || !Double.isFinite(s.coordinates().get(1))
                || Math.abs(s.coordinates().get(0)) > 180
                || Math.abs(s.coordinates().get(1)) > 90
                || s.violations().isEmpty()
                || s.representativeThreshold() == null)
            throw new IllegalArgumentException("Invalid facts");
        Set<String> keys = new HashSet<>();
        for (ViolationSnapshot v : s.violations()) {
            if (v == null
                    || v.thresholdId() == null
                    || !UNITS.containsKey(v.parameter())
                    || !UNITS.get(v.parameter()).equals(v.unit())
                    || !OPS.contains(v.operator())
                    || !SEVERITIES.contains(v.severityLevel())
                    || !Double.isFinite(v.thresholdValue())
                    || !Double.isFinite(v.measuredValue())
                    || v.sourceTimestamp() == null
                    || v.evaluationWindow() == null
                    || blank(v.violationKey())
                    || !keys.add(v.violationKey())
                    || (v.distanceKm() != null
                            && (!Double.isFinite(v.distanceKm()) || v.distanceKm() < 0)))
                throw new IllegalArgumentException("Invalid facts");
        }
        var rep = s.representativeThreshold();
        boolean matches =
                s.violations().stream()
                        .anyMatch(
                                v ->
                                        v.thresholdId().equals(rep.thresholdId())
                                                && v.parameter().equals(rep.parameter())
                                                && v.operator().equals(rep.operator())
                                                && Double.compare(
                                                                v.thresholdValue(),
                                                                rep.thresholdValue())
                                                        == 0
                                                && v.severityLevel().equals(rep.severityLevel())
                                                && v.unit().equals(rep.unit())
                                                && v.violationKey().equals(rep.violationKey()));
        if (!matches
                || !rep.thresholdId().equals(job.representativeThresholdId())
                || job.violationKeys().size() != keys.size()
                || !keys.equals(new HashSet<>(job.violationKeys())))
            throw new IllegalArgumentException("Invalid facts");
        try {
            String json = escapeStringBrackets(mapper.writeValueAsString(s));
            return "Hãy viết một cảnh báo thống nhất cho các vi phạm trong dữ kiện dưới đây.\n[FACTUAL_SYSTEM_DATA]\n"
                    + json
                    + "\n[/FACTUAL_SYSTEM_DATA]\nChỉ trả JSON có hai trường title và ai_explanation theo system instruction.";
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid facts", e);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String escapeStringBrackets(String json) {
        StringBuilder out = new StringBuilder();
        boolean in = false, escaped = false;
        for (char c : json.toCharArray()) {
            if (in && !escaped && c == '[') out.append("\\u005b");
            else if (in && !escaped && c == ']') out.append("\\u005d");
            else out.append(c);
            if (c == '"' && !escaped) in = !in;
            if (c == '\\' && !escaped) escaped = true;
            else escaped = false;
        }
        return out.toString();
    }
}
