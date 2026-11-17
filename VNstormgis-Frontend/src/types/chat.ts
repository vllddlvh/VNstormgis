export interface ChatSession {
  session_id: string;
  session_token: string;
  title: string;
  created_at: string;
}

export interface ChatMessageView {
  id: string;
  sender_type: "user" | "assistant";
  content: string;
  referenced_alert_id: string | null;
  is_fallback: boolean;
  created_at: string;
}

export interface ReferencedEntities {
  stations: string[];
  storm_ids: string[];
  alert_ids: string[];
}

export interface SendMessageResponse {
  message_id: string;
  reply: string;
  is_fallback: boolean;
  referenced_entities: ReferencedEntities;
  created_at: string;
}

export interface ChatProblemDetail {
  status?: number;
  errorCode?: string;
  detail?: string;
  retry_after_seconds?: number;
  invalidParams?: { field: string; message: string }[];
}
