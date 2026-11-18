package vn.weathergis.subscription.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String maskedEmail,
        String scopeType,
        UUID locationId,
        String locationCode,
        String label,
        double longitude,
        double latitude,
        double radiusKm,
        String minSeverity,
        String status,
        String deliveryMode,
        OffsetDateTime verifyExpiresAt,
        OffsetDateTime verifiedAt,
        String unsubscribeToken) {}
