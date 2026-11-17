package vn.weathergis.chat.intent;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.weathergis.location.domain.LocationEntity;
import vn.weathergis.location.repository.LocationRepository;
import vn.weathergis.storm.domain.StormEntity;
import vn.weathergis.storm.repository.StormRepository;

/**
 * Keyword-rule intent classifier, version {@value #RULE_VERSION} (W5-02 §3, contract §4.2).
 *
 * <p>Normalization: NFC, lowercase (Locale.ROOT), Unicode whitespace collapsed. Matching
 * additionally strips diacritics (NFD, remove combining marks, đ→d) so diacritic-free Vietnamese
 * matches; this is a documented rule-table choice, not an approximation.
 *
 * <p>Fixed priority: R0 referenced alert → R1 station entity/mention → R2 storm entity/keyword →
 * R3/R4 rain/wind keywords (earliest keyword position wins, rain precedence on exact tie) → R9
 * out-of-scope. Entities come only from {@code locations}/{@code storms}; the question text is
 * never executed or embedded into facts.
 */
@Component
public class RuleBasedIntentClassifier implements IntentClassifier {

    public static final String RULE_VERSION = "chat-intent-v1";

    private static final List<String> RAIN_KEYWORDS =
            List.of(
                    "mua to",
                    "mua lon",
                    "mua nhieu",
                    "luong mua",
                    "co mua",
                    "mua 1h",
                    "mua 3h",
                    "mua dang");
    private static final List<String> WIND_KEYWORDS =
            List.of("gio manh", "gio lon", "gio to", "toc do gio", "cuong do gio", "gio giat");
    private static final String TROPICAL_LOW_KEYWORD = "ap thap nhiet doi";

    /** "bao" as a standalone token, excluding notification/forecast/question-word compounds. */
    private static final Pattern STORM_TOKEN =
            Pattern.compile(
                    "(?<!thong )(?<!du )\\bbao\\b(?!(?: nhieu| nhiu| gio| lau| xa| trum| gom| quat| cao| boc))");

    private static final Pattern STATION_MENTION = Pattern.compile("\\btram\\s+(\\S+)");

    /** Tokens after "tram" that make it a generic plural/question, not a named station. */
    private static final Set<String> GENERIC_AFTER_TRAM =
            Set.of(
                    "nao", "dang", "co", "nay", "hien", "o", "tai", "thuoc", "cac", "nhung", "moi",
                    "trong", "ngoai", "gan", "xa");

    private final LocationRepository locationRepository;
    private final StormRepository stormRepository;

    public RuleBasedIntentClassifier(
            LocationRepository locationRepository, StormRepository stormRepository) {
        this.locationRepository = locationRepository;
        this.stormRepository = stormRepository;
    }

    @Override
    public IntentClassification classify(String message, UUID referencedAlertId) {
        if (referencedAlertId != null) {
            return result(
                    ChatIntent.ALERT_EXPLAIN, "R0_ALERT_REF", null, null, null, referencedAlertId);
        }
        String normalized = normalize(message == null ? "" : message);
        if (normalized.isEmpty()) {
            return result(ChatIntent.OUT_OF_SCOPE, "R9_OUT_OF_SCOPE", null, null, null, null);
        }
        String match = matchForm(normalized);
        List<LocationEntity> stations = locationRepository.findAll();
        List<StormEntity> storms = stormRepository.findAll();

        StationMatch stationMatch = matchStation(match, stations);
        if (stationMatch != null) {
            return result(
                    ChatIntent.STATION_CURRENT,
                    stationMatch.resolved() ? "R1_STATION_ENTITY" : "R1_STATION_MENTION",
                    stationMatch.resolved() ? stationMatch.id() : null,
                    stationMatch.resolved() ? stationMatch.code() : null,
                    null,
                    null);
        }
        if (hasStationMentionCue(match)) {
            return result(ChatIntent.STATION_CURRENT, "R1_STATION_MENTION", null, null, null, null);
        }

        String stormId = matchStormEntity(match, storms);
        if (stormId != null) {
            return result(ChatIntent.STORM_ACTIVE, "R2_STORM_ENTITY", null, null, stormId, null);
        }
        if (STORM_TOKEN.matcher(match).find() || match.contains(TROPICAL_LOW_KEYWORD)) {
            return result(ChatIntent.STORM_ACTIVE, "R2_STORM_KEYWORD", null, null, null, null);
        }

        int rainAt = earliestIndex(match, RAIN_KEYWORDS);
        int windAt = earliestIndex(match, WIND_KEYWORDS);
        if (rainAt >= 0 && (windAt < 0 || rainAt <= windAt)) {
            return result(ChatIntent.RAIN_TOP, "R3_RAIN_KEYWORD", null, null, null, null);
        }
        if (windAt >= 0) {
            return result(ChatIntent.WIND_TOP, "R4_WIND_KEYWORD", null, null, null, null);
        }
        return result(ChatIntent.OUT_OF_SCOPE, "R9_OUT_OF_SCOPE", null, null, null, null);
    }

    private IntentClassification result(
            ChatIntent intent,
            String ruleId,
            UUID stationId,
            String stationCode,
            String stormId,
            UUID referencedAlertId) {
        return new IntentClassification(
                intent, ruleId, RULE_VERSION, stationId, stationCode, stormId, referencedAlertId);
    }

    private StationMatch matchStation(String match, List<LocationEntity> stations) {
        List<StationMatch> candidates = new ArrayList<>();
        for (LocationEntity station : stations) {
            String nameForm = matchForm(normalize(station.getName()));
            String codeForm = station.getCode().toLowerCase(Locale.ROOT);
            if (!nameForm.isEmpty() && match.contains(nameForm)) {
                candidates.add(
                        new StationMatch(station.getId(), station.getCode(), nameForm.length()));
            } else if (Pattern.compile("\\b" + Pattern.quote(codeForm) + "\\b")
                    .matcher(match)
                    .find()) {
                candidates.add(
                        new StationMatch(station.getId(), station.getCode(), codeForm.length()));
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        int longest = candidates.stream().mapToInt(StationMatch::length).max().orElseThrow();
        List<StationMatch> best =
                candidates.stream()
                        .filter(candidate -> candidate.length() == longest)
                        .sorted(Comparator.comparing(StationMatch::code))
                        .toList();
        long distinct = best.stream().map(StationMatch::id).distinct().count();
        if (distinct == 1) {
            return best.get(0);
        }
        return new StationMatch(null, null, longest);
    }

    private boolean hasStationMentionCue(String match) {
        Matcher matcher = STATION_MENTION.matcher(match);
        while (matcher.find()) {
            String token = matcher.group(1).replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
            if (!token.isEmpty() && !GENERIC_AFTER_TRAM.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String matchStormEntity(String match, List<StormEntity> storms) {
        record Candidate(String id, int length) {}
        List<Candidate> candidates = new ArrayList<>();
        for (StormEntity storm : storms) {
            String nameForm = matchForm(normalize(storm.getName()));
            String idForm = storm.getId().toLowerCase(Locale.ROOT);
            if (!nameForm.isEmpty() && match.contains(nameForm)) {
                candidates.add(new Candidate(storm.getId(), nameForm.length()));
            } else if (Pattern.compile("\\b" + Pattern.quote(idForm) + "\\b")
                    .matcher(match)
                    .find()) {
                candidates.add(new Candidate(storm.getId(), idForm.length()));
            }
        }
        return candidates.stream()
                .sorted(
                        Comparator.comparingInt(Candidate::length)
                                .reversed()
                                .thenComparing(Candidate::id))
                .map(Candidate::id)
                .findFirst()
                .orElse(null);
    }

    private static int earliestIndex(String match, List<String> keywords) {
        int earliest = -1;
        for (String keyword : keywords) {
            int index = match.indexOf(keyword);
            if (index >= 0 && (earliest < 0 || index < earliest)) {
                earliest = index;
            }
        }
        return earliest;
    }

    static String normalize(String input) {
        String nfc = Normalizer.normalize(input, Normalizer.Form.NFC);
        return nfc.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }

    static String matchForm(String normalized) {
        String nfd = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{M}+", "").replace('đ', 'd');
    }

    private record StationMatch(UUID id, String code, int length) {
        boolean resolved() {
            return id != null;
        }
    }
}
