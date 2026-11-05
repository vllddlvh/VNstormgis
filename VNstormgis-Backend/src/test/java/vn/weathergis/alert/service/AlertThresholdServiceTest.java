package vn.weathergis.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.weathergis.alert.domain.AlertThresholdEntity;
import vn.weathergis.alert.dto.AlertThresholdRequest;
import vn.weathergis.alert.dto.AlertThresholdResponse;
import vn.weathergis.alert.repository.AlertThresholdRepository;
import vn.weathergis.alert.service.impl.AlertThresholdServiceImpl;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.common.exception.ResourceNotFoundException;

class AlertThresholdServiceTest {

    private final AlertThresholdRepository repository = mock(AlertThresholdRepository.class);
    private final AlertThresholdService service = new AlertThresholdServiceImpl(repository);

    @Test
    void createsWithTrimmedNameAndEnabledDefault() {
        when(repository.saveAndFlush(any()))
                .thenAnswer(
                        invocation -> {
                            AlertThresholdEntity entity = invocation.getArgument(0);
                            ReflectionTestUtils.setField(entity, "id", 42);
                            ReflectionTestUtils.setField(
                                    entity,
                                    "updatedAt",
                                    OffsetDateTime.parse("2026-11-05T00:00:00Z"));
                            return entity;
                        });

        AlertThresholdResponse created = service.create(request(null));

        assertThat(created.id()).isEqualTo(42);
        assertThat(created.name()).isEqualTo("Mưa rất to 1h");
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.thresholdValue()).isEqualTo(50.0);
        assertThat(created.updatedAt()).isEqualTo("2026-11-05T00:00:00Z");
    }

    @Test
    void replacesAllBusinessFieldsAndPreservesExplicitFalse() {
        AlertThresholdEntity entity = entity();
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);
        AlertThresholdRequest replacement =
                new AlertThresholdRequest(
                        "  Bão gần  ",
                        "storm_dist",
                        "<=",
                        200.0,
                        "CRITICAL",
                        "Phạm vi 200 km",
                        false);

        AlertThresholdResponse updated = service.update(42, replacement);

        assertThat(updated.name()).isEqualTo("Bão gần");
        assertThat(updated.parameter()).isEqualTo("storm_dist");
        assertThat(updated.operator()).isEqualTo("<=");
        assertThat(updated.thresholdValue()).isEqualTo(200.0);
        assertThat(updated.severityLevel()).isEqualTo("CRITICAL");
        assertThat(updated.description()).isEqualTo("Phạm vi 200 km");
        assertThat(updated.isEnabled()).isFalse();
    }

    @Test
    void toggleFlipsExactlyOnceOnEachCall() {
        AlertThresholdEntity entity = entity();
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(entity)).thenReturn(entity);

        assertThat(service.toggle(42).isEnabled()).isFalse();
        assertThat(service.toggle(42).isEnabled()).isTrue();
    }

    @Test
    void deletesTheExistingThreshold() {
        AlertThresholdEntity entity = entity();
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.of(entity));

        service.delete(42);

        verify(repository).delete(entity);
    }

    @Test
    void readsAndFiltersWithoutChangingValues() {
        AlertThresholdEntity entity = entity();
        when(repository.findByFilters("rain_1h", true)).thenReturn(List.of(entity));
        when(repository.findById(42)).thenReturn(Optional.of(entity));

        assertThat(service.findAll("rain_1h", true)).containsExactly(service.findById(42));
    }

    @Test
    void acceptsUnfilteredReads() {
        when(repository.findByFilters(null, null)).thenReturn(List.of());
        assertThat(service.findAll(null, null)).isEmpty();
    }

    @Test
    void missingThresholdHasModuleNotFoundCode() {
        when(repository.findById(42)).thenReturn(Optional.empty());
        when(repository.findByIdForUpdate(42)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(42))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALERT_THRESHOLD_NOT_FOUND);
        assertThatThrownBy(() -> service.update(42, request(true)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.toggle(42)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(42)).isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1})
    void rejectsNonpositiveIdsBeforeAccessingDatabase(Integer id) {
        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(InvalidParameterException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALERT_THRESHOLD_INVALID_PARAMETER);
        assertThatThrownBy(() -> service.update(id, request(true)))
                .isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.toggle(id)).isInstanceOf(InvalidParameterException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(InvalidParameterException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "storm_distance_km", "RAIN_1H", "rain_1h' OR 1=1"})
    void rejectsInvalidParameterEnums(String parameter) {
        AlertThresholdRequest invalid =
                new AlertThresholdRequest("Rain", parameter, ">=", 50.0, "DANGER", "Rain", true);
        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(InvalidParameterException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALERT_THRESHOLD_INVALID_PARAMETER);
        if (parameter != null) {
            assertThatThrownBy(() -> service.findAll(parameter, null))
                    .isInstanceOf(InvalidParameterException.class);
        }
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRulesBeforeWriting(AlertThresholdRequest invalid, ErrorCode expectedCode) {
        assertThatThrownBy(() -> service.create(invalid))
                .isInstanceOf(InvalidParameterException.class)
                .extracting("errorCode")
                .isEqualTo(expectedCode);
        verifyNoInteractions(repository);
    }

    @Test
    void putRequiresEnabledFlag() {
        assertThatThrownBy(() -> service.update(42, request(null)))
                .isInstanceOf(InvalidParameterException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALERT_THRESHOLD_VALIDATION);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @MethodSource("validRules")
    void acceptsEverySupportedParameterOperatorAndSeverity(
            String parameter, String operator, String severity) {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AlertThresholdResponse created =
                service.create(
                        new AlertThresholdRequest(
                                "x".repeat(128),
                                parameter,
                                operator,
                                1.0,
                                severity,
                                "Description",
                                false));
        assertThat(created.parameter()).isEqualTo(parameter);
        assertThat(created.operator()).isEqualTo(operator);
        assertThat(created.severityLevel()).isEqualTo(severity);
        assertThat(created.isEnabled()).isFalse();
    }

    private static Stream<Arguments> validRules() {
        return Stream.of(
                Arguments.of("rain_1h", ">=", "INFO"),
                Arguments.of("rain_3h", ">", "WARNING"),
                Arguments.of("wind_speed", "<=", "DANGER"),
                Arguments.of("wind_gust", "<", "CRITICAL"),
                Arguments.of("storm_dist", "=", "CRITICAL"));
    }

    private static Stream<Arguments> invalidRequests() {
        Stream<AlertThresholdRequest> invalidValidation =
                Stream.of(
                        null,
                        new AlertThresholdRequest(
                                null, "rain_1h", ">=", 50.0, "DANGER", "Rain", true),
                        new AlertThresholdRequest(
                                "  ", "rain_1h", ">=", 50.0, "DANGER", "Rain", true),
                        new AlertThresholdRequest(
                                "x".repeat(129), "rain_1h", ">=", 50.0, "DANGER", "Rain", true),
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", ">=", null, "DANGER", "Rain", true),
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", ">=", 0.0, "DANGER", "Rain", true),
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", ">=", -1.0, "DANGER", "Rain", true),
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", ">=", Double.NaN, "DANGER", "Rain", true),
                        new AlertThresholdRequest(
                                "Rain",
                                "rain_1h",
                                ">=",
                                Double.POSITIVE_INFINITY,
                                "DANGER",
                                "Rain",
                                true),
                        new AlertThresholdRequest(
                                "Rain",
                                "rain_1h",
                                ">=",
                                Double.NEGATIVE_INFINITY,
                                "DANGER",
                                "Rain",
                                true),
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", ">=", 50.0, "DANGER", null, true),
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", ">=", 50.0, "DANGER", " \n ", true));
        Stream<AlertThresholdRequest> invalidEnums =
                Stream.of(
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", null, 50.0, "DANGER", "Rain", true),
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", "!=", 50.0, "DANGER", "Rain", true),
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", ">=", 50.0, null, "Rain", true),
                        new AlertThresholdRequest(
                                "Rain", "rain_1h", ">=", 50.0, "danger", "Rain", true));
        return Stream.concat(
                invalidValidation.map(r -> Arguments.of(r, ErrorCode.ALERT_THRESHOLD_VALIDATION)),
                invalidEnums.map(
                        r -> Arguments.of(r, ErrorCode.ALERT_THRESHOLD_INVALID_PARAMETER)));
    }

    private static AlertThresholdRequest request(Boolean enabled) {
        return new AlertThresholdRequest(
                "  Mưa rất to 1h  ", "rain_1h", ">=", 50.0, "DANGER", "Lượng mưa 1 giờ", enabled);
    }

    private static AlertThresholdEntity entity() {
        AlertThresholdEntity entity = new AlertThresholdEntity();
        ReflectionTestUtils.setField(entity, "id", 42);
        entity.setName("Mưa rất to 1h");
        entity.setParameter("rain_1h");
        entity.setOperator(">=");
        entity.setThresholdValue(50.0);
        entity.setSeverityLevel("DANGER");
        entity.setDescription("Lượng mưa 1 giờ");
        return entity;
    }
}
