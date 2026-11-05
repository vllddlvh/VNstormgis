package vn.weathergis.automation.model;

import java.util.UUID;

/** One active station with independently optional latest weather and nearest active storm. */
public record StationSnapshot(
        UUID locationId,
        String code,
        String name,
        double longitude,
        double latitude,
        WeatherObservation weather,
        NearestStorm storm) {}
