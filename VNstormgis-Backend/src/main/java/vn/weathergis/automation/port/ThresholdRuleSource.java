package vn.weathergis.automation.port;

import java.util.List;
import vn.weathergis.automation.model.ThresholdRule;

/** Automation owns the read contract so it never imports the downstream alert module. */
public interface ThresholdRuleSource {
    List<ThresholdRule> findEnabledThresholds();
}
