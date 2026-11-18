import { apiClient } from "./apiClient";
import { ApiResponse } from "@/types/api";
import {
  CreateSubscriptionPayload,
  SavedLocalSubscription,
  SubscriptionData,
} from "@/types/subscription";

const STORAGE_KEY = "vnstormgis.email.subscriptions";

export const subscriptionService = {
  async createSubscription(
    payload: CreateSubscriptionPayload,
  ): Promise<ApiResponse<SubscriptionData>> {
    const response = await apiClient.post<ApiResponse<SubscriptionData>>(
      "/v1/subscriptions",
      payload,
    );
    return response.data;
  },

  async verifyByOtp(
    subscriptionId: string,
    otp: string,
  ): Promise<ApiResponse<SubscriptionData>> {
    const response = await apiClient.post<ApiResponse<SubscriptionData>>(
      `/v1/subscriptions/${subscriptionId}/verify`,
      { otp },
    );
    return response.data;
  },

  async verifyByToken(token: string): Promise<ApiResponse<SubscriptionData>> {
    const response = await apiClient.post<ApiResponse<SubscriptionData>>(
      "/v1/subscriptions/verify-token",
      { token },
    );
    return response.data;
  },

  async unsubscribeByToken(
    token: string,
  ): Promise<ApiResponse<SubscriptionData>> {
    const response = await apiClient.post<ApiResponse<SubscriptionData>>(
      "/v1/subscriptions/unsubscribe",
      { token },
    );
    return response.data;
  },

  async sendTestNotification(
    subscriptionId: string,
    unsubscribeToken: string,
  ): Promise<ApiResponse<SubscriptionData>> {
    const response = await apiClient.post<ApiResponse<SubscriptionData>>(
      `/v1/subscriptions/${subscriptionId}/test-notification`,
      { token: unsubscribeToken },
    );
    return response.data;
  },

  loadSavedSubscriptions(): SavedLocalSubscription[] {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  },

  saveVerifiedSubscription(sub: SubscriptionData): SavedLocalSubscription[] {
    if (!sub.unsubscribeToken || sub.status !== "ACTIVE") {
      return this.loadSavedSubscriptions();
    }
    const existing = this.loadSavedSubscriptions().filter(
      (item) => item.id !== sub.id,
    );
    const entry: SavedLocalSubscription = {
      id: sub.id,
      maskedEmail: sub.maskedEmail,
      scopeType: sub.scopeType,
      locationCode: sub.locationCode,
      label: sub.label,
      longitude: sub.longitude,
      latitude: sub.latitude,
      radiusKm: sub.radiusKm,
      minSeverity: sub.minSeverity,
      verifiedAt: sub.verifiedAt || new Date().toISOString(),
      unsubscribeToken: sub.unsubscribeToken,
    };
    const next = [entry, ...existing].slice(0, 10);
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      // Ignore storage quota issues
    }
    return next;
  },

  removeSavedSubscription(idOrToken: string): SavedLocalSubscription[] {
    const next = this.loadSavedSubscriptions().filter(
      (item) => item.id !== idOrToken && item.unsubscribeToken !== idOrToken,
    );
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      // Ignore storage errors
    }
    return next;
  },
};
