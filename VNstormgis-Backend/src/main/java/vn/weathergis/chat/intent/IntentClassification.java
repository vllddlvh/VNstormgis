package vn.weathergis.chat.intent;

import java.util.UUID;

/**
 * Typed classifier output: intent plus the matched rule and only entities recognized from the
 * database. Null station fields with {@link ChatIntent#STATION_CURRENT} mean an unresolved station
 * mention (downstream NO_DATA, never a substitute station).
 */
public record IntentClassification(
        ChatIntent intent,
        String ruleId,
        String ruleVersion,
        UUID stationId,
        String stationCode,
        String stormId,
        UUID referencedAlertId) {}
