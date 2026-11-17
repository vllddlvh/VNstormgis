package vn.weathergis.chat.intent;

import com.fasterxml.jackson.annotation.JsonValue;

/** Closed chat intent taxonomy (W5-01 contract §4.2); no LLM classification. */
public enum ChatIntent {
    RAIN_TOP("rain_top"),
    WIND_TOP("wind_top"),
    STATION_CURRENT("station_current"),
    STORM_ACTIVE("storm_active"),
    ALERT_EXPLAIN("alert_explain"),
    OUT_OF_SCOPE("out_of_scope");

    private final String wireName;

    ChatIntent(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }
}
