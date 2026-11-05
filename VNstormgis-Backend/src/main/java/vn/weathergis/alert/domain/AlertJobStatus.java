package vn.weathergis.alert.domain;

/**
 * Lifecycle of an inbox job before and during alert publication (never exposed in alerts.status).
 */
public enum AlertJobStatus {
    PENDING,
    READY,
    COMPLETED
}
