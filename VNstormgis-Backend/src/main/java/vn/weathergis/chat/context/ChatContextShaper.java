package vn.weathergis.chat.context;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.weathergis.chat.config.ChatContextProperties;

/**
 * Enforces the ≤ 8 KB context bound with a deterministic shrink ladder (W5-02 §4.7): stations
 * 5→3→1, then storm forecast detail removed, then storms 5→3→1, each step recording an omitted
 * count. The asked alert is never dropped or truncated; an oversize alert snapshot stays flagged
 * for the W5-04/DL-17 decision instead of being silently cut.
 */
@Component
public class ChatContextShaper {

    private final FactualContextSerializer serializer;
    private final ChatContextProperties properties;

    public ChatContextShaper(
            FactualContextSerializer serializer, ChatContextProperties properties) {
        this.serializer = serializer;
        this.properties = properties;
    }

    public FactualContext shape(FactualContext draft) {
        List<FactualContext.StationObservation> stations = draft.stations();
        List<FactualContext.StormSummary> storms = draft.storms();
        int originalStations = stations.size();
        int originalStorms = storms.size();

        while (stations.size() > 1 && overLimit(draft, stations, storms)) {
            stations = stations.subList(0, stations.size() > 3 ? 3 : 1);
        }
        if (!storms.isEmpty()
                && storms.stream().anyMatch(s -> s.nearestForecast() != null)
                && overLimit(draft, stations, storms)) {
            storms = storms.stream().map(FactualContext.StormSummary::withoutForecast).toList();
        }
        while (storms.size() > 1 && overLimit(draft, stations, storms)) {
            storms = storms.subList(0, storms.size() > 3 ? 3 : 1);
        }

        int omittedStations = originalStations - stations.size();
        int omittedStorms = originalStorms - storms.size();
        FactualContext.ReferencedEntities entities = entities(stations, storms, draft.alert());
        FactualContext.ContextMeta provisional =
                new FactualContext.ContextMeta(
                        properties.getStaleThreshold().toMinutes(),
                        omittedStations,
                        omittedStorms,
                        false);
        FactualContext shaped =
                new FactualContext(
                        draft.schemaVersion(),
                        draft.intent(),
                        draft.generatedAt(),
                        draft.outcome(),
                        draft.noDataReason(),
                        stations,
                        storms,
                        draft.alert(),
                        entities,
                        provisional,
                        0);
        boolean oversize = serializer.payloadBytes(shaped) > properties.getMaxContextBytes();
        FactualContext.ContextMeta meta =
                new FactualContext.ContextMeta(
                        properties.getStaleThreshold().toMinutes(),
                        omittedStations,
                        omittedStorms,
                        oversize);
        return new FactualContext(
                draft.schemaVersion(),
                draft.intent(),
                draft.generatedAt(),
                draft.outcome(),
                draft.noDataReason(),
                stations,
                storms,
                draft.alert(),
                entities,
                meta,
                0);
    }

    private boolean overLimit(
            FactualContext draft,
            List<FactualContext.StationObservation> stations,
            List<FactualContext.StormSummary> storms) {
        FactualContext probe =
                new FactualContext(
                        draft.schemaVersion(),
                        draft.intent(),
                        draft.generatedAt(),
                        draft.outcome(),
                        draft.noDataReason(),
                        stations,
                        storms,
                        draft.alert(),
                        draft.referencedEntities(),
                        draft.meta(),
                        0);
        return serializer.payloadBytes(probe) > properties.getMaxContextBytes();
    }

    private FactualContext.ReferencedEntities entities(
            List<FactualContext.StationObservation> stations,
            List<FactualContext.StormSummary> storms,
            FactualContext.AlertContext alert) {
        TreeSet<String> codes = new TreeSet<>();
        stations.forEach(station -> codes.add(station.code()));
        TreeSet<String> stormIds = new TreeSet<>();
        storms.forEach(storm -> stormIds.add(storm.stormId()));
        List<UUID> alertIds = new ArrayList<>();
        if (alert != null) {
            alertIds.add(alert.alertId());
            if (alert.snapshotPresent() && alert.factualSnapshot() != null) {
                if (alert.factualSnapshot().locationCode() != null) {
                    codes.add(alert.factualSnapshot().locationCode());
                }
                alert.factualSnapshot().violations().stream()
                        .map(v -> v.stormId())
                        .filter(id -> id != null)
                        .forEach(stormIds::add);
            }
        }
        return new FactualContext.ReferencedEntities(
                List.copyOf(codes), List.copyOf(stormIds), List.copyOf(alertIds));
    }
}
