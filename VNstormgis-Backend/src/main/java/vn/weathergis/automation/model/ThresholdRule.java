package vn.weathergis.automation.model;

/** Immutable rule snapshot supplied by the threshold module for one evaluation cycle. */
public record ThresholdRule(
        Integer id,
        String parameter,
        String operator,
        double thresholdValue,
        String severityLevel,
        boolean enabled) {}
