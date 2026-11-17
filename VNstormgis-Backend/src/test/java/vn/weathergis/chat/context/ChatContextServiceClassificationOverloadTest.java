package vn.weathergis.chat.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.weathergis.alert.repository.AlertRepository;
import vn.weathergis.chat.config.ChatContextProperties;
import vn.weathergis.chat.intent.ChatIntent;
import vn.weathergis.chat.intent.IntentClassification;
import vn.weathergis.chat.intent.IntentClassifier;
import vn.weathergis.chat.intent.RuleBasedIntentClassifier;
import vn.weathergis.chat.repository.ChatContextQueryRepository;
import vn.weathergis.chat.repository.StationObservationRow;

/**
 * Additive W5-03 overload: the orchestrator classifies once and passes the same {@link
 * IntentClassification} to retrieval and the reply port, so classification never runs twice.
 */
@ExtendWith(MockitoExtension.class)
class ChatContextServiceClassificationOverloadTest {

    private static final Instant NOW = Instant.parse("2026-11-17T09:00:00Z");
    private static final String QUESTION = "Khu vực nào đang có mưa to nhất?";

    @Mock private IntentClassifier classifier;
    @Mock private ChatContextQueryRepository queryRepository;
    @Mock private AlertRepository alertRepository;

    private ChatContextServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        FactualContextSerializer serializer = new FactualContextSerializer(objectMapper);
        ChatContextProperties properties = new ChatContextProperties();
        properties.setStaleThreshold(Duration.ofMinutes(60));
        service =
                new ChatContextServiceImpl(
                        classifier,
                        queryRepository,
                        alertRepository,
                        objectMapper,
                        serializer,
                        new ChatContextShaper(serializer, properties),
                        properties,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private IntentClassification classification(ChatIntent intent) {
        return new IntentClassification(
                intent,
                "TEST_RULE",
                RuleBasedIntentClassifier.RULE_VERSION,
                null,
                null,
                null,
                null);
    }

    @Test
    void retrieveWithProvidedClassificationDoesNotReclassify() {
        ChatContextResult result =
                service.retrieve(
                        new ChatContextRequest(QUESTION, null, null),
                        classification(ChatIntent.OUT_OF_SCOPE));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.OUT_OF_SCOPE);
        verifyNoInteractions(classifier);
        verifyNoInteractions(queryRepository);
    }

    @Test
    void singleArgumentRetrieveStillClassifiesInternally() {
        when(classifier.classify(QUESTION, null)).thenReturn(classification(ChatIntent.RAIN_TOP));
        when(queryRepository.findLatestPerActiveStation(null))
                .thenReturn(
                        List.of(
                                new StationObservationRow(
                                        java.util.UUID.randomUUID(),
                                        "W503A",
                                        "Trạm W503A",
                                        108.0,
                                        16.0,
                                        true,
                                        NOW.minusSeconds(60),
                                        25.0,
                                        70.0,
                                        1000.0,
                                        5.0,
                                        8.0,
                                        90.0,
                                        80.0,
                                        120.0,
                                        "Rain",
                                        "mưa to")));

        ChatContextResult result = service.retrieve(new ChatContextRequest(QUESTION, null, null));

        assertThat(result.outcome()).isEqualTo(ChatContextOutcome.CONTEXT_READY);
        verify(classifier).classify(QUESTION, null);
    }
}
