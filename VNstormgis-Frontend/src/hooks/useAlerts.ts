import { useCallback, useEffect, useRef, useState } from "react";
import axios from "axios";
import { alertService } from "@/services/alertService";
import { ALERT_REFRESH_INTERVAL_MS } from "@/types/alert";
import type { AlertDetail, AlertFeature } from "@/types/alert";
import type { ProblemDetail } from "@/types/api";

function errorMessage(error: unknown, detail = false): string {
  if (axios.isAxiosError<ProblemDetail>(error)) {
    if (detail && error.response?.status === 404) return "Không tìm thấy cảnh báo (ALR_404_NOT_FOUND)";
    if (detail && error.response?.status === 400) return "Mã cảnh báo không hợp lệ (ALR_400_INVALID_ID)";
    if (error.code === "ECONNABORTED") return "Máy chủ phản hồi quá chậm. Vui lòng thử lại.";
  }
  return detail ? "Không tải được chi tiết cảnh báo" : "Không tải được cảnh báo";
}

interface AlertsState {
  alerts: AlertFeature[];
  hasLoaded: boolean;
  isRefreshing: boolean;
  error: string | null;
  lastSuccessfulFetchAt: string | null;
}

/** One collection owner for panel, ticker, polling and dashboard refresh. */
export function useAlerts() {
  const [state, setState] = useState<AlertsState>({
    alerts: [], hasLoaded: false, isRefreshing: true, error: null, lastSuccessfulFetchAt: null,
  });
  const mounted = useRef(false);
  const request = useRef<AbortController | null>(null);
  const refresh = useCallback(async () => {
    if (!mounted.current || request.current) return;
    const controller = new AbortController();
    request.current = controller;
    setState((previous) => ({ ...previous, isRefreshing: true }));
    try {
      const collection = await alertService.getActiveAlerts(controller.signal);
      if (mounted.current && request.current === controller && !controller.signal.aborted) {
        setState({ alerts: collection.features, hasLoaded: true, isRefreshing: false,
          error: null, lastSuccessfulFetchAt: new Date().toISOString() });
      }
    } catch (error) {
      if (mounted.current && request.current === controller && !controller.signal.aborted) {
        setState((previous) => ({ ...previous, isRefreshing: false, error: errorMessage(error) }));
      }
    } finally {
      if (request.current === controller) request.current = null;
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    void refresh();
    const timer = window.setInterval(() => { void refresh(); }, ALERT_REFRESH_INTERVAL_MS);
    return () => {
      mounted.current = false;
      window.clearInterval(timer);
      request.current?.abort();
      request.current = null;
    };
  }, [refresh]);

  return { ...state, isLoading: !state.hasLoaded && state.isRefreshing, refresh };
}

interface DetailState {
  id: string | null;
  detail: AlertDetail | null;
  isLoading: boolean;
  error: string | null;
}

/** A keyed result prevents A's text rendering under B even before effects run. */
export function useAlertDetail(id: string | null) {
  const [state, setState] = useState<DetailState>({ id: null, detail: null, isLoading: false, error: null });
  const [attempt, setAttempt] = useState(0);
  const retry = useCallback(() => setAttempt((value) => value + 1), []);

  useEffect(() => {
    if (!id) {
      setState({ id: null, detail: null, isLoading: false, error: null });
      return;
    }
    const controller = new AbortController();
    setState({ id, detail: null, isLoading: true, error: null });
    void alertService.getAlertDetail(id, controller.signal).then((detail) => {
      if (!controller.signal.aborted) setState({ id, detail, isLoading: false, error: null });
    }).catch((error: unknown) => {
      if (!controller.signal.aborted) setState({ id, detail: null, isLoading: false, error: errorMessage(error, true) });
    });
    return () => controller.abort();
  }, [id, attempt]);

  return {
    detail: state.id === id ? state.detail : null,
    isLoading: Boolean(id) && (state.id !== id || state.isLoading),
    error: state.id === id ? state.error : null,
    retry,
  };
}
