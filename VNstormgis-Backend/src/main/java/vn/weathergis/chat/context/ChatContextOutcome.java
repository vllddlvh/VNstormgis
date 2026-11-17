package vn.weathergis.chat.context;

/** Typed retrieval outcomes for W5-03 HTTP mapping and W5-04 fallback behaviour (contract §4.5). */
public enum ChatContextOutcome {
    CONTEXT_READY,
    NO_DATA,
    OUT_OF_SCOPE,
    ALERT_NOT_FOUND,
    NO_SNAPSHOT
}
