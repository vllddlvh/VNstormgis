package vn.weathergis.alert.service.impl;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.weathergis.alert.domain.AlertThresholdEntity;
import vn.weathergis.alert.dto.AlertThresholdRequest;
import vn.weathergis.alert.dto.AlertThresholdResponse;
import vn.weathergis.alert.repository.AlertThresholdRepository;
import vn.weathergis.alert.service.AlertThresholdService;
import vn.weathergis.common.exception.ErrorCode;
import vn.weathergis.common.exception.InvalidParameterException;
import vn.weathergis.common.exception.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class AlertThresholdServiceImpl implements AlertThresholdService {

    private static final Set<String> PARAMETERS =
            Set.of("rain_1h", "rain_3h", "wind_speed", "wind_gust", "storm_dist");
    private static final Set<String> OPERATORS = Set.of(">=", ">", "<=", "<", "=");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "DANGER", "CRITICAL");

    private final AlertThresholdRepository repository;

    public AlertThresholdServiceImpl(AlertThresholdRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AlertThresholdResponse> findAll(String parameter, Boolean isEnabled) {
        if (parameter != null) {
            requireAllowed(PARAMETERS, parameter, "parameter");
        }
        return repository.findByFilters(parameter, isEnabled).stream()
                .map(AlertThresholdResponse::fromEntity)
                .toList();
    }

    @Override
    public AlertThresholdResponse findById(Integer id) {
        requirePositiveId(id);
        return AlertThresholdResponse.fromEntity(
                repository.findById(id).orElseThrow(() -> notFound(id)));
    }

    @Override
    @Transactional
    public AlertThresholdResponse create(AlertThresholdRequest request) {
        validate(request, false);
        AlertThresholdEntity entity = new AlertThresholdEntity();
        apply(entity, request);
        return AlertThresholdResponse.fromEntity(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public AlertThresholdResponse update(Integer id, AlertThresholdRequest request) {
        requirePositiveId(id);
        validate(request, true);
        AlertThresholdEntity entity = findForUpdate(id);
        apply(entity, request);
        return AlertThresholdResponse.fromEntity(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public AlertThresholdResponse toggle(Integer id) {
        requirePositiveId(id);
        AlertThresholdEntity entity = findForUpdate(id);
        entity.setIsEnabled(!entity.getIsEnabled());
        return AlertThresholdResponse.fromEntity(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        requirePositiveId(id);
        repository.delete(findForUpdate(id));
    }

    private AlertThresholdEntity findForUpdate(Integer id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Integer id) {
        return new ResourceNotFoundException(
                ErrorCode.ALERT_THRESHOLD_NOT_FOUND, "Không tìm thấy ngưỡng cảnh báo: " + id);
    }

    private static void requirePositiveId(Integer id) {
        if (id == null || id <= 0) {
            throw new InvalidParameterException(
                    ErrorCode.ALERT_THRESHOLD_INVALID_PARAMETER, "id phải là số nguyên dương");
        }
    }

    private static void validate(AlertThresholdRequest request, boolean replacing) {
        if (request == null) {
            throw validation("Nội dung ngưỡng cảnh báo là bắt buộc");
        }
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank() || name.length() > 128) {
            throw validation("name phải có từ 1 đến 128 ký tự sau khi trim");
        }
        requireAllowed(PARAMETERS, request.parameter(), "parameter");
        requireAllowed(OPERATORS, request.operator(), "operator");
        requireAllowed(SEVERITIES, request.severityLevel(), "severity_level");
        if (request.thresholdValue() == null
                || !Double.isFinite(request.thresholdValue())
                || request.thresholdValue() <= 0) {
            throw validation("threshold_value phải là số hữu hạn lớn hơn 0");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw validation("description là bắt buộc và không được rỗng");
        }
        if (replacing && request.isEnabled() == null) {
            throw validation("is_enabled là bắt buộc khi thay thế ngưỡng bằng PUT");
        }
    }

    private static void requireAllowed(Set<String> allowed, String value, String field) {
        if (value == null || !allowed.contains(value)) {
            throw new InvalidParameterException(
                    ErrorCode.ALERT_THRESHOLD_INVALID_PARAMETER, field + " không hợp lệ");
        }
    }

    private static InvalidParameterException validation(String detail) {
        return new InvalidParameterException(ErrorCode.ALERT_THRESHOLD_VALIDATION, detail);
    }

    private static void apply(AlertThresholdEntity entity, AlertThresholdRequest request) {
        entity.setName(request.name().trim());
        entity.setParameter(request.parameter());
        entity.setOperator(request.operator());
        entity.setThresholdValue(request.thresholdValue());
        entity.setSeverityLevel(request.severityLevel());
        entity.setDescription(request.description());
        entity.setIsEnabled(request.isEnabled() == null || request.isEnabled());
    }
}
