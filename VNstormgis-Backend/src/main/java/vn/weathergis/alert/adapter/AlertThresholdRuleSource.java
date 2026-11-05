package vn.weathergis.alert.adapter;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.weathergis.alert.repository.AlertThresholdRepository;
import vn.weathergis.automation.model.ThresholdRule;
import vn.weathergis.automation.port.ThresholdRuleSource;

/** Reads current enabled W4-01 rules on every scan without caching administrative changes. */
@Component
public class AlertThresholdRuleSource implements ThresholdRuleSource {

    private final AlertThresholdRepository repository;

    public AlertThresholdRuleSource(AlertThresholdRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ThresholdRule> findEnabledThresholds() {
        return repository.findByFilters(null, true).stream()
                .map(
                        rule ->
                                new ThresholdRule(
                                        rule.getId(),
                                        rule.getParameter(),
                                        rule.getOperator(),
                                        rule.getThresholdValue(),
                                        rule.getSeverityLevel(),
                                        Boolean.TRUE.equals(rule.getIsEnabled())))
                .toList();
    }
}
