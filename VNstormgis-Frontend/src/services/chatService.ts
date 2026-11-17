import { apiClient } from "@/services/apiClient";
import type {
  ChatMessageView,
  ChatSession,
  SendMessageResponse,
} from "@/types/chat";

const object = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);
const uuid = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const timestamp = (value: unknown): value is string =>
  typeof value === "string" && !Number.isNaN(Date.parse(value));
const strings = (value: unknown) =>
  Array.isArray(value) && value.every((item) => typeof item === "string");
const uuids = (value: unknown) =>
  Array.isArray(value) && value.every(uuid);

export const isStoredSession = (
  value: unknown,
): value is Pick<ChatSession, "session_id" | "session_token"> =>
  object(value) &&
  uuid(value.session_id) &&
  typeof value.session_token === "string" &&
  /^[A-Za-z0-9_-]{43}$/.test(value.session_token);

function unwrap(value: unknown): unknown {
  if (
    !object(value) ||
    value.success !== true ||
    !Object.prototype.hasOwnProperty.call(value, "data")
  ) {
    throw new Error("Phản hồi từ máy chủ không đúng cấu trúc.");
  }
  return value.data;
}

function isMessage(value: unknown): value is ChatMessageView {
  return (
    object(value) &&
    uuid(value.id) &&
    (value.sender_type === "user" || value.sender_type === "assistant") &&
    typeof value.content === "string" &&
    (value.referenced_alert_id === null || uuid(value.referenced_alert_id)) &&
    typeof value.is_fallback === "boolean" &&
    timestamp(value.created_at)
  );
}

export const chatService = {
  async createSession(signal?: AbortSignal): Promise<ChatSession> {
    const value = unwrap(
      (
        await apiClient.post<unknown>("/v1/chat/sessions", undefined, {
          signal,
        })
      ).data,
    );
    if (
      !object(value) ||
      !isStoredSession(value) ||
      typeof (value as Record<string, unknown>).title !== "string" ||
      !timestamp((value as Record<string, unknown>).created_at)
    ) {
      throw new Error("Phản hồi từ máy chủ không đúng cấu trúc.");
    }
    return value as unknown as ChatSession;
  },

  async getMessages(
    session: Pick<ChatSession, "session_id" | "session_token">,
    after?: string,
    signal?: AbortSignal,
  ): Promise<ChatMessageView[]> {
    const value = unwrap(
      (
        await apiClient.get<unknown>(
          `/v1/chat/sessions/${session.session_id}/messages`,
          {
            headers: { "X-Session-Token": session.session_token },
            params: { limit: 200, ...(after ? { after } : {}) },
            signal,
          },
        )
      ).data,
    );
    if (!Array.isArray(value) || !value.every(isMessage)) {
      throw new Error("Phản hồi từ máy chủ không đúng cấu trúc.");
    }
    return value;
  },

  async sendMessage(
    session: Pick<ChatSession, "session_id" | "session_token">,
    message: string,
    referencedAlertId?: string,
    signal?: AbortSignal,
  ): Promise<SendMessageResponse> {
    const value = unwrap(
      (
        await apiClient.post<unknown>(
          `/v1/chat/sessions/${session.session_id}/messages`,
          {
            message,
            ...(referencedAlertId
              ? { referenced_alert_id: referencedAlertId }
              : {}),
          },
          { headers: { "X-Session-Token": session.session_token }, signal },
        )
      ).data,
    );
    if (
      !object(value) ||
      !uuid(value.message_id) ||
      typeof value.reply !== "string" ||
      typeof value.is_fallback !== "boolean" ||
      !timestamp(value.created_at) ||
      !object(value.referenced_entities) ||
      !strings(value.referenced_entities.stations) ||
      !strings(value.referenced_entities.storm_ids) ||
      !uuids(value.referenced_entities.alert_ids)
    ) {
      throw new Error("Phản hồi từ máy chủ không đúng cấu trúc.");
    }
    return value as unknown as SendMessageResponse;
  },
};
