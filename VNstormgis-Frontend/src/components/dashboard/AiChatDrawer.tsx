import { useCallback, useEffect, useRef, useState } from "react";
import { Bot, Send, Sparkles, X } from "lucide-react";
import { useChatSession } from "@/hooks/useChatSession";

export interface ChatRequest {
  question: string;
  referenced_alert_id?: string;
  alert_title?: string;
}

interface AiChatDrawerProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  externalRequest: ChatRequest | null;
  onClearExternalRequest: () => void;
}

const QUICK_PROMPTS = [
  "Khu vực nào đang mưa lớn nhất?",
  "Trạm nào đang có gió mạnh nhất?",
  "Tình hình bão hiện nay ra sao?",
  "Tình hình trạm Đà Nẵng?",
];
const formatTime = (value: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
  }).format(new Date(value));

export function AiChatDrawer({
  isOpen,
  onOpenChange,
  externalRequest,
  onClearExternalRequest,
}: AiChatDrawerProps) {
  const chat = useChatSession(isOpen);
  const [input, setInput] = useState("");
  const [alertReference, setAlertReference] = useState<{
    id: string;
    title: string;
  } | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const historyRef = useRef<HTMLDivElement>(null);
  const count = [...input.trim()].length;
  const canSend =
    count > 0 && count <= 500 && !chat.busy && chat.secondsLeft === 0;
  const state =
    chat.secondsLeft > 0
      ? "rate-limited"
      : (chat.busy ??
        (chat.error ? "error" : chat.session ? "ready" : "no-session"));

  const close = useCallback(() => {
    onOpenChange(false);
    triggerRef.current?.focus();
  }, [onOpenChange]);

  useEffect(() => {
    if (externalRequest) {
      setInput(externalRequest.question);
      setAlertReference(
        externalRequest.referenced_alert_id
          ? {
              id: externalRequest.referenced_alert_id,
              title: externalRequest.alert_title ?? "Cảnh báo",
            }
          : null,
      );
      onClearExternalRequest();
      inputRef.current?.focus();
    }
  }, [externalRequest, onClearExternalRequest]);

  useEffect(() => {
    if (isOpen) inputRef.current?.focus();
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        close();
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [isOpen, close]);

  useEffect(() => {
    if (isOpen && historyRef.current)
      historyRef.current.scrollTop = historyRef.current.scrollHeight;
  }, [isOpen, chat.messages.length, chat.busy]);

  useEffect(() => {
    if (
      chat.error?.code === "CHT_404_ALERT_NOT_FOUND" ||
      chat.error?.code === "CHT_400_INVALID_ALERT_ID"
    ) {
      setAlertReference(null);
    }
  }, [chat.error]);

  const submit = async () => {
    if (!canSend) return;
    const succeeded = await chat.send(input, alertReference?.id);
    if (succeeded) {
      setInput("");
      setAlertReference(null);
    }
  };

  return (
    <>
      <div className="absolute bottom-4 right-4 z-[1000] select-none font-mono">
        <button
          ref={triggerRef}
          id="btn-ai-copilot-trigger"
          data-testid="ai-chat-trigger"
          type="button"
          aria-controls="ai-chat-drawer"
          aria-expanded={isOpen}
          onClick={() => (isOpen ? close() : onOpenChange(true))}
          className="flex min-h-11 items-center gap-2 border border-charcoal bg-carbon px-3 py-2 text-topo shadow-flat hover:border-mint hover:text-mint focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
        >
          <Bot aria-hidden="true" className="h-4 w-4 text-mint" />
          <span className="hidden text-xs font-bold uppercase tracking-wider sm:inline">
            TRỢ LÝ AI
          </span>
        </button>
      </div>
      {isOpen && (
        <aside
          id="ai-chat-drawer"
          data-testid="ai-chat-drawer"
          data-chat-state={state}
          role="dialog"
          aria-modal="false"
          aria-label="Trợ lý AI thời tiết"
          className="absolute inset-x-0 bottom-0 z-[1200] flex h-[75vh] max-h-[75vh] flex-col border-t border-charcoal bg-carbon font-mono text-topo shadow-flat-lg md:inset-y-0 md:left-auto md:right-0 md:h-full md:max-h-full md:w-96 md:border-l md:border-t-0"
        >
          <header className="flex shrink-0 items-center justify-between border-b border-charcoal bg-gis-panel p-3">
            <div className="flex min-w-0 items-center gap-2">
              <Sparkles
                aria-hidden="true"
                className="h-4 w-4 shrink-0 text-mint"
              />
              <div>
                <h2 className="text-xs font-bold text-mint">
                  TRỢ LÝ AI THỜI TIẾT
                </h2>
                <p className="text-[10px] text-sage">
                  Trả lời dựa trên dữ liệu hệ thống
                </p>
              </div>
            </div>
            <button
              id="btn-ai-copilot-close"
              data-testid="ai-chat-close"
              type="button"
              onClick={close}
              aria-label="Đóng trợ lý AI"
              className="flex min-h-11 min-w-11 items-center justify-center border border-charcoal text-sage hover:text-mint focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
            >
              <X aria-hidden="true" className="h-4 w-4" />
            </button>
          </header>
          {chat.notice && (
            <p
              data-testid="ai-chat-session-notice"
              role="status"
              className="border-b border-sage p-2 text-xs text-sage"
            >
              {chat.notice}
            </p>
          )}
          {chat.error && (
            <div
              data-testid="ai-chat-error"
              data-error-code={chat.error.code}
              role="status"
              className="border-b border-weather-warning p-3 text-xs text-weather-warning"
            >
              <p>{chat.error.message}</p>
              <button
                type="button"
                onClick={() => {
                  if (
                    chat.messages.some((item) => item.status === "failed") ||
                    !chat.session
                  )
                    void submit();
                  else {
                    chat.clearError();
                    void chat.reload();
                  }
                }}
                className="mt-2 min-h-11 border border-weather-warning px-3"
              >
                THỬ LẠI
              </button>
            </div>
          )}
          <div
            ref={historyRef}
            data-testid="ai-chat-history"
            aria-live="polite"
            className="min-h-0 flex-1 space-y-3 overflow-y-auto p-3 pb-14 select-text"
          >
            {chat.busy === "loading-history" && (
              <p role="status" className="text-xs text-sage">
                Đang tải hội thoại…
              </p>
            )}
            {!chat.busy &&
              !chat.error &&
              chat.messages.length === 0 &&
              (chat.hasLoaded || !chat.session) && (
                <div
                  data-testid="ai-chat-empty"
                  className="space-y-4 text-xs leading-relaxed"
                >
                  <p>
                    Hỏi về mưa, gió, bão, trạm quan trắc hoặc cảnh báo đang hoạt
                    động.
                  </p>
                  <div
                    data-testid="ai-chat-quick-prompts"
                    className="space-y-2"
                  >
                    {QUICK_PROMPTS.map((prompt, index) => (
                      <button
                        key={prompt}
                        type="button"
                        data-prompt-index={index}
                        id={
                          index === 3
                            ? "btn-prompt-danang"
                            : index === 2
                              ? "btn-prompt-yagi"
                              : index === 0
                                ? "btn-prompt-hcm"
                                : undefined
                        }
                        disabled={chat.secondsLeft > 0}
                        onClick={() => {
                          setInput(prompt);
                          setAlertReference(null);
                          inputRef.current?.focus();
                        }}
                        className="block min-h-11 w-full border border-charcoal px-3 py-2 text-left text-mint hover:bg-charcoal disabled:opacity-50"
                      >
                        {prompt}
                      </button>
                    ))}
                  </div>
                  <p className="text-[10px] text-sage">
                    Nguồn: OpenWeatherMap / GDACS · Chỉ mang tính tham khảo cộng
                    đồng
                  </p>
                </div>
              )}
            {chat.messages.map((message) => (
              <article
                key={message.id}
                data-testid="ai-chat-message"
                data-sender={message.sender_type}
                data-message-id={message.id}
                data-fallback={String(message.is_fallback)}
                data-status={message.status ?? "sent"}
                className={`space-y-1.5 border border-charcoal p-3 text-xs ${message.sender_type === "user" ? "ml-6 bg-gis-panel" : "mr-4 bg-carbon"}`}
              >
                <div className="flex items-center justify-between gap-2 text-[10px] text-sage">
                  <strong className="text-mint">
                    {message.sender_type === "user" ? "[BẠN]" : "[TRỢ LÝ AI]"}
                  </strong>
                  <time>{formatTime(message.created_at)}</time>
                </div>
                <p className="whitespace-pre-wrap break-words leading-relaxed">
                  {message.content}
                </p>
                {message.status === "failed" && (
                  <div className="space-y-2 text-weather-warning">
                    <p>CHƯA GỬI</p>
                    <button
                      type="button"
                      disabled={Boolean(chat.busy) || chat.secondsLeft > 0}
                      onClick={() => {
                        setInput(message.content);
                        inputRef.current?.focus();
                        void chat
                          .send(
                            message.content,
                            chat.error?.code === "CHT_404_ALERT_NOT_FOUND" ||
                              chat.error?.code === "CHT_400_INVALID_ALERT_ID"
                              ? undefined
                              : (message.referenced_alert_id ?? undefined),
                          )
                          .then((succeeded) => {
                            if (succeeded) {
                              setInput("");
                              setAlertReference(null);
                            }
                          });
                      }}
                      className="min-h-11 border border-weather-warning px-3 disabled:opacity-50"
                    >
                      THỬ LẠI
                    </button>
                  </div>
                )}
                {message.is_fallback && (
                  <p
                    data-testid="ai-chat-fallback-badge"
                    className="text-[10px] text-sage"
                  >
                    Trả lời tự động từ dữ liệu hệ thống
                  </p>
                )}
              </article>
            ))}
            {chat.busy === "sending" && (
              <p
                data-testid="ai-chat-loading"
                role="status"
                className="mr-4 border border-charcoal p-3 text-xs text-sage"
              >
                AI đang suy nghĩ...
              </p>
            )}
            {chat.hasMore && (
              <div className="space-y-2 text-xs text-sage">
                <p>
                  Hội thoại dài — đang hiển thị {chat.messages.length} tin nhắn
                  đầu tiên.
                </p>
                <button
                  data-testid="ai-chat-load-more"
                  type="button"
                  disabled={Boolean(chat.busy)}
                  onClick={() => void chat.loadMore()}
                  className="min-h-11 border border-sage px-3 text-mint"
                >
                  TẢI THÊM
                </button>
              </div>
            )}
          </div>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void submit();
            }}
            className="shrink-0 space-y-2 border-t border-charcoal bg-gis-panel p-3"
          >
            {alertReference && (
              <div
                data-testid="ai-chat-alert-ref"
                data-alert-id={alertReference.id}
                className="flex items-center justify-between gap-2 border border-sage p-2 text-[10px]"
              >
                <span className="min-w-0 break-words">
                  Đang hỏi về cảnh báo: {alertReference.title}
                </span>
                <button
                  type="button"
                  aria-label="Bỏ cảnh báo tham chiếu"
                  onClick={() => setAlertReference(null)}
                  className="min-h-11 min-w-11 text-mint"
                >
                  ×
                </button>
              </div>
            )}
            {chat.secondsLeft > 0 && (
              <p
                data-testid="ai-chat-countdown"
                role="status"
                className="text-xs text-weather-warning"
              >
                Bạn đang gửi câu hỏi quá nhanh. Xin vui lòng chờ{" "}
                {chat.secondsLeft} giây trước khi tiếp tục.
              </p>
            )}
            <div className="flex items-end gap-2">
              <textarea
                ref={inputRef}
                id="input-ai-prompt"
                data-testid="ai-chat-input"
                rows={2}
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={(event) => {
                  if (
                    event.key === "Enter" &&
                    !event.shiftKey &&
                    !event.nativeEvent.isComposing
                  ) {
                    event.preventDefault();
                    void submit();
                  }
                }}
                placeholder="Nhập câu hỏi thời tiết…"
                aria-label="Câu hỏi thời tiết"
                className="min-h-11 max-h-24 min-w-0 flex-1 resize-none border border-charcoal bg-carbon p-2 text-xs text-topo placeholder:text-sage focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
              />
              <button
                id="btn-ai-send"
                data-testid="ai-chat-send"
                type="submit"
                disabled={!canSend}
                aria-label="Gửi câu hỏi"
                className="flex min-h-11 min-w-11 items-center justify-center border border-mint text-mint hover:bg-charcoal disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-mint"
              >
                <Send aria-hidden="true" className="h-4 w-4" />
              </button>
            </div>
            <p
              className={`text-right text-[10px] ${count > 500 ? "text-weather-warning" : "text-sage"}`}
            >
              {count}/500
            </p>
          </form>
        </aside>
      )}
    </>
  );
}
