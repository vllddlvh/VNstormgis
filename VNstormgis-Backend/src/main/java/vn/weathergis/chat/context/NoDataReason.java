package vn.weathergis.chat.context;

/** Honest no-data reasons carried to W5-04 templates (W5-02 §5). */
public enum NoDataReason {
    MISSING_RECORDS,
    UNKNOWN_STATION,
    EMPTY_BBOX,
    NO_ACTIVE_STORM
}
