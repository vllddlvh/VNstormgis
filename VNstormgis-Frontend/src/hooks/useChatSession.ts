import { useCallback, useEffect, useRef, useState } from "react";
import axios from "axios";
import { chatService, isStoredSession } from "@/services/chatService";
import type {
  ChatMessageView,
  ChatProblemDetail,
  ChatSession,
} from "@/types/chat";

const STORAGE_KEY = "vnstormgis.chat.session";
type SessionKey = Pick<ChatSession, "session_id" | "session_token">;
export type DisplayMessage = ChatMessageView & {
  status?: "sending" | "failed";
};
interface ChatError {
  code: string;
  message: string;
}

function readSession(): SessionKey | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (isStoredSession(parsed)) return parsed;
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* unavailable storage */
    }
  }
  return null;
}

function problem(error: unknown): ChatProblemDetail | null {
  if (!axios.isAxiosError(error)) return null;
  const value: unknown = error.response?.data;
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as ChatProblemDetail)
    : null;
}

function isExpired(error: unknown): boolean {
  const code = problem(error)?.errorCode;
  return (
    code === "CHT_404_SESSION_NOT_FOUND" ||
    code === "CHT_401_SESSION_TOKEN_MISSING"
  );
}

function describe(error: unknown): ChatError {
  const details = problem(error);
  const code =
    details?.errorCode ?? (axios.isAxiosError(error) ? "network" : "shape");
  const validation = details?.invalidParams?.find(
    (item) => item.field === "message",
  )?.message;
  const fallback: Record<string, string> = {
    network:
      "Không kết nối được máy chủ. Vui lòng kiểm tra kết nối và thử lại.",
    shape: "Phản hồi từ máy chủ không đúng cấu trúc.",
    COM_500_INTERNAL_ERROR: "Lỗi máy chủ khi xử lý câu hỏi. Vui lòng thử lại.",
    CHT_503_AI_UNAVAILABLE:
      "Hệ thống trợ lý AI đang tạm thời gián đoạn kết nối. Vui lòng thử lại sau ít phút.",
    CHT_503_RATE_LIMITER_UNAVAILABLE:
      "Hệ thống hạn mức tạm thời gián đoạn. Vui lòng thử lại sau ít phút.",
    CHT_400_INVALID_ALERT_ID: "Mã cảnh báo không hợp lệ.",
    CHT_404_ALERT_NOT_FOUND:
      "Không tìm thấy cảnh báo được yêu cầu (có thể đã bị xóa hoặc không còn tồn tại).",
  };
  return {
    code,
    message:
      validation ??
      (typeof details?.detail === "string" && details.detail
        ? details.detail
        : (fallback[code] ?? fallback.network)),
  };
}

function retrySeconds(error: unknown): number {
  const extension = problem(error)?.retry_after_seconds;
  const header = axios.isAxiosError(error)
    ? Number(error.response?.headers?.["retry-after"])
    : NaN;
  const seconds = typeof extension === "number" ? extension : header;
  return Number.isInteger(seconds) && seconds >= 1 ? seconds : 60;
}

export function useChatSession(isOpen: boolean) {
  const [session, setSession] = useState<SessionKey | null>(readSession);
  const sessionRef = useRef(session);
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [busy, setBusy] = useState<
    "loading-history" | "creating" | "sending" | null
  >(null);
  const [error, setError] = useState<ChatError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const deadline = useRef(0);
  const inFlight = useRef(false);
  const active = useRef<AbortController | null>(null);

  const saveSession = useCallback((next: SessionKey | null) => {
    const key = next
      ? { session_id: next.session_id, session_token: next.session_token }
      : null;
    sessionRef.current = key;
    setSession(key);
    try {
      if (key) localStorage.setItem(STORAGE_KEY, JSON.stringify(key));
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      if (next)
        setNotice("Không lưu được phiên — hội thoại sẽ mất khi tải lại trang");
    }
  }, []);

  const recover = useCallback(
    async (signal: AbortSignal) => {
      saveSession(null);
      const next = await chatService.createSession(signal);
      saveSession(next);
      const history = await chatService.getMessages(next, undefined, signal);
      setMessages(history);
      setHasLoaded(true);
      setHasMore(false);
      setNotice(
        "Phiên hội thoại đã hết hạn hoặc không còn tồn tại. Hệ thống đã tạo phiên mới.",
      );
      return next;
    },
    [saveSession],
  );

  const loadHistory = useCallback(
    async (loadMore = false) => {
      const current = sessionRef.current;
      if (!current || inFlight.current) return;
      inFlight.current = true;
      const controller = new AbortController();
      active.current = controller;
      setBusy("loading-history");
      setError(null);
      try {
        let after = loadMore
          ? messages.filter((item) => !item.status).at(-1)?.id
          : undefined;
        const loaded: ChatMessageView[] = [];
        let page: ChatMessageView[] = [];
        for (let index = 0; index < (loadMore ? 1 : 5); index += 1) {
          page = await chatService.getMessages(
            current,
            after,
            controller.signal,
          );
          loaded.push(...page);
          if (page.length < 200) break;
          const next = page.at(-1)?.id;
          if (!next || next === after)
            throw new Error("Phản hồi từ máy chủ không đúng cấu trúc.");
          after = next;
        }
        setMessages((previous) =>
          loadMore ? [...previous, ...loaded] : loaded,
        );
        setHasMore(page.length === 200);
        setHasLoaded(true);
      } catch (cause) {
        if (!controller.signal.aborted) {
          if (isExpired(cause)) {
            try {
              await recover(controller.signal);
            } catch (recoveryError) {
              setError(describe(recoveryError));
            }
          } else setError(describe(cause));
        }
      } finally {
        inFlight.current = false;
        active.current = null;
        setBusy(null);
      }
    },
    [messages, recover],
  );

  useEffect(() => {
    if (isOpen && session && !hasLoaded && !inFlight.current)
      void loadHistory();
  }, [isOpen, session, hasLoaded, loadHistory]);

  const wasOpen = useRef(isOpen);
  useEffect(() => {
    if (isOpen && !wasOpen.current && session && hasLoaded && !inFlight.current)
      void loadHistory();
    wasOpen.current = isOpen;
  }, [isOpen, session, hasLoaded, loadHistory]);

  useEffect(() => () => active.current?.abort(), []);

  useEffect(() => {
    if (!deadline.current) return;
    const tick = () => {
      const remaining = Math.max(
        0,
        Math.ceil((deadline.current - Date.now()) / 1000),
      );
      setSecondsLeft(remaining);
      if (!remaining) deadline.current = 0;
    };
    const timer = window.setInterval(tick, 250);
    return () => window.clearInterval(timer);
  }, [secondsLeft]);

  const send = useCallback(
    async (question: string, referencedAlertId?: string) => {
      const content = question.trim();
      if (
        !content ||
        [...content].length > 500 ||
        inFlight.current ||
        deadline.current > Date.now()
      )
        return false;
      inFlight.current = true;
      const controller = new AbortController();
      active.current = controller;
      setError(null);
      const optimisticId = `pending-${crypto.randomUUID()}`;
      const optimistic: DisplayMessage = {
        id: optimisticId,
        sender_type: "user",
        content,
        referenced_alert_id: referencedAlertId ?? null,
        is_fallback: false,
        created_at: new Date().toISOString(),
        status: "sending",
      };
      setMessages((previous) => [
        ...previous.filter((item) => item.status !== "failed"),
        optimistic,
      ]);
      try {
        let current = sessionRef.current;
        if (!current) {
          setBusy("creating");
          current = await chatService.createSession(controller.signal);
          saveSession(current);
          setHasLoaded(true);
        }
        setBusy("sending");
        const reply = await chatService.sendMessage(
          current,
          content,
          referencedAlertId,
          controller.signal,
        );
        setMessages((previous) =>
          previous
            .map((item) =>
              item.id === optimisticId ? { ...item, status: undefined } : item,
            )
            .concat({
              id: reply.message_id,
              sender_type: "assistant",
              content: reply.reply,
              referenced_alert_id: referencedAlertId ?? null,
              is_fallback: reply.is_fallback,
              created_at: reply.created_at,
            }),
        );
        return true;
      } catch (cause) {
        if (!controller.signal.aborted) {
          if (isExpired(cause)) {
            try {
              await recover(controller.signal);
            } catch (recoveryError) {
              setError(describe(recoveryError));
            }
          } else {
            const failure = describe(cause);
            setError(failure);
            if (failure.code === "CHT_429_RATE_LIMITED") {
              const seconds = retrySeconds(cause);
              deadline.current = Date.now() + seconds * 1000;
              setSecondsLeft(seconds);
            }
          }
          if (isExpired(cause)) setMessages([]);
          else
            setMessages((previous) =>
              previous.map((item) =>
                item.id === optimisticId ? { ...item, status: "failed" } : item,
              ),
            );
        }
        return false;
      } finally {
        inFlight.current = false;
        active.current = null;
        setBusy(null);
      }
    },
    [recover, saveSession],
  );

  return {
    messages,
    session,
    hasLoaded,
    busy,
    error,
    notice,
    hasMore,
    secondsLeft,
    send,
    reload: () => loadHistory(false),
    loadMore: () => loadHistory(true),
    clearError: () => setError(null),
  };
}
