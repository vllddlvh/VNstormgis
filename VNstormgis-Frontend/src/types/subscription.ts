export type SubscriptionScopeType = "STATION" | "COORDINATE";

export type SubscriptionSeverity = "INFO" | "WARNING" | "DANGER" | "CRITICAL";

export interface CreateSubscriptionPayload {
  email: string;
  scopeType: SubscriptionScopeType;
  locationId?: string;
  locationCode?: string;
  longitude?: number;
  latitude?: number;
  label?: string;
  radiusKm: number;
  minSeverity: SubscriptionSeverity;
}

export interface SubscriptionData {
  id: string;
  maskedEmail: string;
  scopeType: SubscriptionScopeType;
  locationId?: string;
  locationCode?: string;
  label: string;
  longitude: number;
  latitude: number;
  radiusKm: number;
  minSeverity: SubscriptionSeverity;
  status: "PENDING" | "ACTIVE" | "UNSUBSCRIBED";
  deliveryMode?: string;
  verifyExpiresAt?: string;
  verifiedAt?: string;
  unsubscribeToken?: string;
}

export interface SavedLocalSubscription {
  id: string;
  maskedEmail: string;
  scopeType: SubscriptionScopeType;
  locationCode?: string;
  label: string;
  longitude: number;
  latitude: number;
  radiusKm: number;
  minSeverity: SubscriptionSeverity;
  verifiedAt: string;
  unsubscribeToken: string;
}
