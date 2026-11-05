package vn.weathergis.alert.service;

import java.util.List;
import vn.weathergis.alert.dto.AlertThresholdRequest;
import vn.weathergis.alert.dto.AlertThresholdResponse;

public interface AlertThresholdService {

    List<AlertThresholdResponse> findAll(String parameter, Boolean isEnabled);

    AlertThresholdResponse findById(Integer id);

    AlertThresholdResponse create(AlertThresholdRequest request);

    AlertThresholdResponse update(Integer id, AlertThresholdRequest request);

    AlertThresholdResponse toggle(Integer id);

    void delete(Integer id);
}
