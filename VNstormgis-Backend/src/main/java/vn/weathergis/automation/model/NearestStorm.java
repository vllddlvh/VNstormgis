package vn.weathergis.automation.model;

import java.time.Instant;

/** Active storm distance in km; updatedAt is a database update time, not an observation time. */
public record NearestStorm(String stormId, String name, double distanceKm, Instant updatedAt) {}
