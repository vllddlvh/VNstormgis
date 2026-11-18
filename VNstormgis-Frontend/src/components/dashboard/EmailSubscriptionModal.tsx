/**
 * @file EmailSubscriptionModal.tsx
 * @description Hộp thoại Đăng ký & Quản lý nhận thông báo cảnh báo qua Email chuẩn San Rita.
 * Hỗ trợ đăng ký theo Tỉnh/Trạm quan trắc hoặc Tọa độ vị trí người dùng, xác thực OTP 6 chữ số
 * ngay trên giao diện, gửi bản tin kiểm tra kết nối (số liệu thực PostGIS) và hủy đăng ký 1-click.
 */

import React, { useEffect, useState } from "react";
import { AxiosError } from "axios";
import {
  Mail,
  X,
  CheckCircle2,
  Send,
  Trash2,
  ShieldCheck,
  MapPin,
} from "lucide-react";
import { StationMarkerData } from "@/types/map";
import { ProblemDetail } from "@/types/api";
import {
  SavedLocalSubscription,
  SubscriptionData,
  SubscriptionScopeType,
  SubscriptionSeverity,
} from "@/types/subscription";
import { subscriptionService } from "@/services/subscriptionService";

export interface SubscriptionPrefill {
  scopeType: SubscriptionScopeType;
  locationCode?: string;
  latitude?: number;
  longitude?: number;
  label?: string;
}

export interface EmailSubscriptionModalProps {
  isOpen: boolean;
  stations: StationMarkerData[];
  prefill: SubscriptionPrefill | null;
  deepLinkBanner?: {
    type: "success" | "error";
    message: string;
  } | null;
  onClearDeepLinkBanner?: () => void;
  onClose: () => void;
}

export const EmailSubscriptionModal: React.FC<EmailSubscriptionModalProps> = ({
  isOpen,
  stations,
  prefill,
  deepLinkBanner,
  onClearDeepLinkBanner,
  onClose,
}) => {
  const [activeTab, setActiveTab] = useState<"register" | "saved">("register");
  const [email, setEmail] = useState("");
  const [scopeType, setScopeType] = useState<SubscriptionScopeType>("STATION");
  const [selectedCode, setSelectedCode] = useState<string>("HAN");
  const [coordLat, setCoordLat] = useState<string>("16.0544");
  const [coordLng, setCoordLng] = useState<string>("108.2022");
  const [customLabel, setCustomLabel] = useState<string>("");
  const [radiusKm, setRadiusKm] = useState<number>(50);
  const [minSeverity, setMinSeverity] =
    useState<SubscriptionSeverity>("WARNING");

  const [pendingSub, setPendingSub] = useState<SubscriptionData | null>(null);
  const [verifiedSub, setVerifiedSub] = useState<SubscriptionData | null>(null);
  const [otpInput, setOtpInput] = useState("");
  const [savedList, setSavedList] = useState<SavedLocalSubscription[]>(() =>
    subscriptionService.loadSavedSubscriptions(),
  );

  const [submitting, setSubmitting] = useState(false);
  const [statusMsg, setStatusMsg] = useState<{
    type: "info" | "success" | "error";
    text: string;
  } | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    setSavedList(subscriptionService.loadSavedSubscriptions());
    setPendingSub(null);
    setVerifiedSub(null);
    setOtpInput("");
    setStatusMsg(null);

    if (prefill) {
      setActiveTab("register");
      setScopeType(prefill.scopeType);
      if (prefill.locationCode) {
        setSelectedCode(prefill.locationCode);
      }
      if (typeof prefill.latitude === "number") {
        setCoordLat(prefill.latitude.toFixed(4));
      }
      if (typeof prefill.longitude === "number") {
        setCoordLng(prefill.longitude.toFixed(4));
      }
      setCustomLabel(prefill.label ?? "");
    } else if (stations.length > 0 && !selectedCode) {
      setSelectedCode(stations[0].code);
    }
  }, [isOpen, prefill, stations, selectedCode]);

  useEffect(() => {
    if (!isOpen) return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const extractError = (err: unknown, fallback: string): string => {
    const axiosErr = err as AxiosError<ProblemDetail>;
    return axiosErr.response?.data?.detail || fallback;
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setStatusMsg(null);
    try {
      const payload =
        scopeType === "STATION"
          ? {
              email: email.trim(),
              scopeType: "STATION" as const,
              locationCode: selectedCode,
              radiusKm,
              minSeverity,
            }
          : {
              email: email.trim(),
              scopeType: "COORDINATE" as const,
              latitude: Number(coordLat),
              longitude: Number(coordLng),
              label: customLabel.trim() || undefined,
              radiusKm,
              minSeverity,
            };

      const res = await subscriptionService.createSubscription(payload);
      setPendingSub(res.data);
      setVerifiedSub(null);
      setStatusMsg({
        type: "info",
        text:
          res.data.deliveryMode === "SENT"
            ? `Đã gửi mã OTP 6 chữ số tới ${res.data.maskedEmail}. (Môi trường Local có thể mở http://localhost:8025 Mailpit để xem email ngay).`
            : `Đã tạo yêu cầu xác thực cho ${res.data.maskedEmail} (chế độ Outbox Local — kiểm tra log backend hoặc khởi chạy container weathergis-mailpit).`,
      });
    } catch (err) {
      setStatusMsg({
        type: "error",
        text: extractError(
          err,
          "Không thể đăng ký nhận thông báo. Vui lòng kiểm tra lại thông tin.",
        ),
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pendingSub) return;
    setSubmitting(true);
    setStatusMsg(null);
    try {
      const res = await subscriptionService.verifyByOtp(
        pendingSub.id,
        otpInput.trim(),
      );
      setVerifiedSub(res.data);
      setPendingSub(null);
      const updated = subscriptionService.saveVerifiedSubscription(res.data);
      setSavedList(updated);
      setStatusMsg({
        type: "success",
        text: `Kích hoạt thành công! Hệ thống sẽ tự động gửi email cảnh báo khu vực ${res.data.label}.`,
      });
    } catch (err) {
      setStatusMsg({
        type: "error",
        text: extractError(err, "Mã OTP không chính xác hoặc đã hết hạn."),
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleSendTest = async (id: string, unsubscribeToken?: string) => {
    if (!unsubscribeToken) return;
    setSubmitting(true);
    setStatusMsg(null);
    try {
      const res = await subscriptionService.sendTestNotification(
        id,
        unsubscribeToken,
      );
      setStatusMsg({
        type: "success",
        text: `Đã gửi bản tin kiểm tra kết nối (kèm số liệu thời tiết thực tế PostGIS) tới ${res.data.maskedEmail}!`,
      });
    } catch (err) {
      setStatusMsg({
        type: "error",
        text: extractError(err, "Không gửi được bản tin kiểm tra."),
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleUnsubscribeSaved = async (item: SavedLocalSubscription) => {
    setSubmitting(true);
    setStatusMsg(null);
    try {
      await subscriptionService.unsubscribeByToken(item.unsubscribeToken);
      const updated = subscriptionService.removeSavedSubscription(item.id);
      setSavedList(updated);
      setStatusMsg({
        type: "success",
        text: `Đã hủy đăng ký nhận cảnh báo khu vực ${item.label}.`,
      });
    } catch {
      const updated = subscriptionService.removeSavedSubscription(item.id);
      setSavedList(updated);
      setStatusMsg({
        type: "info",
        text: `Đã gỡ mục đăng ký ${item.label} khỏi danh sách lưu cục bộ.`,
      });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      id="email-subscription-modal"
      data-testid="email-subscription-modal"
      className="fixed inset-0 z-[1300] bg-black/75 flex items-center justify-center p-4 font-mono"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg bg-[#161b13] border border-[#e2ffcc] shadow-flat text-[#dde2e4] max-h-[90vh] overflow-y-auto"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="px-4 py-3 border-b border-[#2d3329] bg-[#1a2117] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Mail className="w-4 h-4 text-[#e2ffcc]" />
            <div>
              <h2 className="text-xs font-bold uppercase text-[#e2ffcc]">
                ĐĂNG KÝ NHẬN CẢNH BÁO THIÊN TAI QUA EMAIL
              </h2>
              <p className="text-[10px] text-[#84907f]">
                Cảnh báo tự động theo không gian PostGIS &amp; xác thực OTP 2 lớp
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng hộp thoại đăng ký email"
            className="p-1.5 border border-[#2d3329] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc]"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* Chuyển Tab Đăng ký mới / Đã lưu */}
        <div className="grid grid-cols-2 border-b border-[#2d3329] bg-[#161b13] text-[10px] font-bold uppercase">
          <button
            type="button"
            onClick={() => setActiveTab("register")}
            className={`py-2.5 border-r border-[#2d3329] ${
              activeTab === "register"
                ? "bg-[#e2ffcc] text-[#161b13]"
                : "text-[#84907f] hover:text-[#dde2e4]"
            }`}
          >
            ĐĂNG KÝ MỚI
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("saved")}
            className={`py-2.5 ${
              activeTab === "saved"
                ? "bg-[#e2ffcc] text-[#161b13]"
                : "text-[#84907f] hover:text-[#dde2e4]"
            }`}
          >
            ĐĂNG KÝ ĐÃ KÍCH HOẠT ({savedList.length})
          </button>
        </div>

        <div className="p-4 space-y-3 text-xs">
          {/* Thông báo từ liên kết xác thực/hủy trong email */}
          {deepLinkBanner && (
            <div
              className={`p-3 border flex items-start justify-between gap-2 text-[11px] ${
                deepLinkBanner.type === "success"
                  ? "border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc]"
                  : "border-[#dc2626] bg-[#1a2117] text-[#dc2626]"
              }`}
            >
              <span>{deepLinkBanner.message}</span>
              {onClearDeepLinkBanner && (
                <button
                  type="button"
                  onClick={onClearDeepLinkBanner}
                  className="text-[#84907f] hover:text-[#dde2e4]"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          )}

          {/* Thông báo trạng thái thao tác */}
          {statusMsg && (
            <div
              className={`p-2.5 border text-[11px] leading-relaxed ${
                statusMsg.type === "error"
                  ? "border-[#dc2626] bg-[#1a2117] text-[#dc2626]"
                  : statusMsg.type === "success"
                    ? "border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc]"
                    : "border-[#38bdf8] bg-[#1a2117] text-[#38bdf8]"
              }`}
            >
              {statusMsg.text}
            </div>
          )}

          {activeTab === "register" ? (
            verifiedSub ? (
              /* Màn hình sau khi đã xác thực thành công */
              <div className="p-4 border border-[#e2ffcc] bg-[#1a2117] space-y-3">
                <div className="flex items-center gap-2 text-[#e2ffcc] font-bold">
                  <CheckCircle2 className="w-5 h-5" />
                  <span>ĐÃ KÍCH HOẠT NHẬN CẢNH BÁO EMAIL</span>
                </div>
                <div className="text-[11px] text-[#dde2e4] space-y-1">
                  <div>
                    • Hộp thư nhận tin:{" "}
                    <strong className="text-[#e2ffcc]">
                      {verifiedSub.maskedEmail}
                    </strong>
                  </div>
                  <div>
                    • Khu vực theo dõi: <strong>{verifiedSub.label}</strong> (bán
                    kính {verifiedSub.radiusKm} km)
                  </div>
                  <div>
                    • Mức cảnh báo tối thiểu:{" "}
                    <strong>{verifiedSub.minSeverity}</strong>
                  </div>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 pt-2">
                  <button
                    id="btn-send-test-email"
                    type="button"
                    disabled={submitting}
                    onClick={() =>
                      void handleSendTest(
                        verifiedSub.id,
                        verifiedSub.unsubscribeToken,
                      )
                    }
                    className="py-2 px-3 bg-[#e2ffcc] text-[#161b13] font-bold uppercase text-[10px] flex items-center justify-center gap-1.5 disabled:opacity-50"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>GỬI EMAIL KIỂM TRA NGAY</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setVerifiedSub(null);
                      setStatusMsg(null);
                    }}
                    className="py-2 px-3 border border-[#2d3329] hover:border-[#e2ffcc] text-[#dde2e4] font-bold uppercase text-[10px]"
                  >
                    ĐĂNG KÝ THÊM KHU VỰC KHÁC
                  </button>
                </div>
              </div>
            ) : pendingSub ? (
              /* Bước 2: Nhập mã OTP 6 chữ số */
              <form onSubmit={handleVerifyOtp} className="space-y-3">
                <div className="p-3 border border-[#2d3329] bg-[#1a2117] space-y-1 text-[11px]">
                  <div className="text-[#e2ffcc] font-bold flex items-center gap-1.5">
                    <ShieldCheck className="w-4 h-4" />
                    <span>XÁC THỰC HỘP THƯ: {pendingSub.maskedEmail}</span>
                  </div>
                  <p className="text-[#84907f]">
                    Khu vực: <strong className="text-[#dde2e4]">{pendingSub.label}</strong>{" "}
                    · Bán kính {pendingSub.radiusKm} km · Mức{" "}
                    {pendingSub.minSeverity}+
                  </p>
                </div>

                <div>
                  <label
                    htmlFor="input-subscription-otp"
                    className="block text-[10px] uppercase text-[#84907f] mb-1 font-bold"
                  >
                    NHẬP MÃ OTP 6 CHỮ SỐ TRONG EMAIL
                  </label>
                  <input
                    id="input-subscription-otp"
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    required
                    value={otpInput}
                    onChange={(e) =>
                      setOtpInput(e.target.value.replace(/\D/g, ""))
                    }
                    placeholder="Ví dụ: 482910"
                    className="w-full px-3 py-2.5 bg-[#1a2117] border border-[#e2ffcc] text-center text-lg tracking-[0.4em] font-bold text-[#e2ffcc] focus:outline-none"
                  />
                </div>

                <div className="flex items-center gap-2">
                  <button
                    id="btn-verify-otp"
                    type="submit"
                    disabled={submitting || otpInput.length !== 6}
                    className="flex-1 py-2.5 bg-[#e2ffcc] text-[#161b13] font-bold uppercase text-[11px] disabled:opacity-50"
                  >
                    {submitting ? "ĐANG XÁC THỰC..." : "XÁC NHẬN KÍCH HOẠT"}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setPendingSub(null);
                      setStatusMsg(null);
                    }}
                    className="px-3 py-2.5 border border-[#2d3329] text-[#84907f] hover:text-[#dde2e4] text-[10px] uppercase"
                  >
                    QUAY LẠI
                  </button>
                </div>
              </form>
            ) : (
              /* Bước 1: Biểu mẫu đăng ký */
              <form onSubmit={handleRegisterSubmit} className="space-y-3">
                <div>
                  <label
                    htmlFor="input-subscription-email"
                    className="block text-[10px] uppercase text-[#84907f] mb-1 font-bold"
                  >
                    ĐỊA CHỈ EMAIL NHẬN THÔNG BÁO (*)
                  </label>
                  <input
                    id="input-subscription-email"
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="ten.ban@gmail.com"
                    className="w-full px-3 py-2 bg-[#1a2117] border border-[#2d3329] focus:border-[#e2ffcc] text-[#dde2e4] text-xs focus:outline-none"
                  />
                </div>

                {/* Chọn phạm vi: Theo trạm/tỉnh hoặc theo tọa độ */}
                <div>
                  <span className="block text-[10px] uppercase text-[#84907f] mb-1 font-bold">
                    PHƯƠNG THỨC GIÁM SÁT KHÔNG GIAN
                  </span>
                  <div className="grid grid-cols-2 gap-1.5">
                    <button
                      type="button"
                      onClick={() => setScopeType("STATION")}
                      className={`py-2 px-2 border text-[10px] font-bold uppercase ${
                        scopeType === "STATION"
                          ? "border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc]"
                          : "border-[#2d3329] bg-[#161b13] text-[#84907f]"
                      }`}
                    >
                      THEO TỈNH / TRẠM ĐO
                    </button>
                    <button
                      type="button"
                      onClick={() => setScopeType("COORDINATE")}
                      className={`py-2 px-2 border text-[10px] font-bold uppercase ${
                        scopeType === "COORDINATE"
                          ? "border-[#e2ffcc] bg-[#1a2117] text-[#e2ffcc]"
                          : "border-[#2d3329] bg-[#161b13] text-[#84907f]"
                      }`}
                    >
                      THEO TỌA ĐỘ GPS
                    </button>
                  </div>
                </div>

                {scopeType === "STATION" ? (
                  <div>
                    <label
                      htmlFor="select-subscription-station"
                      className="block text-[10px] uppercase text-[#84907f] mb-1 font-bold"
                    >
                      CHỌN TỈNH / THÀNH PHỐ HOẶC HẢI ĐẢO
                    </label>
                    <select
                      id="select-subscription-station"
                      value={selectedCode}
                      onChange={(e) => setSelectedCode(e.target.value)}
                      className="w-full px-3 py-2 bg-[#1a2117] border border-[#2d3329] focus:border-[#e2ffcc] text-[#dde2e4] text-xs focus:outline-none"
                    >
                      {stations.map((st) => (
                        <option key={st.id} value={st.code}>
                          {st.name} ({st.code}) — {st.region}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : (
                  <div className="space-y-2 p-2.5 border border-[#2d3329] bg-[#1a2117]">
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <label className="block text-[9px] uppercase text-[#84907f] mb-1">
                          VĨ ĐỘ (LAT)
                        </label>
                        <input
                          type="text"
                          required
                          value={coordLat}
                          onChange={(e) => setCoordLat(e.target.value)}
                          className="w-full px-2 py-1.5 bg-[#161b13] border border-[#2d3329] text-xs text-[#dde2e4]"
                        />
                      </div>
                      <div>
                        <label className="block text-[9px] uppercase text-[#84907f] mb-1">
                          KINH ĐỘ (LNG)
                        </label>
                        <input
                          type="text"
                          required
                          value={coordLng}
                          onChange={(e) => setCoordLng(e.target.value)}
                          className="w-full px-2 py-1.5 bg-[#161b13] border border-[#2d3329] text-xs text-[#dde2e4]"
                        />
                      </div>
                    </div>
                    <div>
                      <label className="block text-[9px] uppercase text-[#84907f] mb-1">
                        NHÃN GỢI NHỚ (TÙY CHỌN)
                      </label>
                      <input
                        type="text"
                        value={customLabel}
                        onChange={(e) => setCustomLabel(e.target.value)}
                        placeholder="Ví dụ: Nhà riêng / Cơ quan của tôi"
                        className="w-full px-2 py-1.5 bg-[#161b13] border border-[#2d3329] text-xs text-[#dde2e4]"
                      />
                    </div>
                  </div>
                )}

                {/* Bán kính & mức độ */}
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label
                      htmlFor="select-subscription-radius"
                      className="block text-[10px] uppercase text-[#84907f] mb-1 font-bold"
                    >
                      BÁN KÍNH QUÉT CẢNH BÁO
                    </label>
                    <select
                      id="select-subscription-radius"
                      value={radiusKm}
                      onChange={(e) => setRadiusKm(Number(e.target.value))}
                      className="w-full px-2.5 py-2 bg-[#1a2117] border border-[#2d3329] text-xs text-[#dde2e4]"
                    >
                      <option value={25}>25 km (Cục bộ)</option>
                      <option value={50}>50 km (Khuyên dùng)</option>
                      <option value={100}>100 km (Liên tỉnh)</option>
                      <option value={200}>200 km (Vùng bão rộng)</option>
                    </select>
                  </div>

                  <div>
                    <label
                      htmlFor="select-subscription-severity"
                      className="block text-[10px] uppercase text-[#84907f] mb-1 font-bold"
                    >
                      MỨC CẢNH BÁO TỐI THIỂU
                    </label>
                    <select
                      id="select-subscription-severity"
                      value={minSeverity}
                      onChange={(e) =>
                        setMinSeverity(e.target.value as SubscriptionSeverity)
                      }
                      className="w-full px-2.5 py-2 bg-[#1a2117] border border-[#2d3329] text-xs text-[#dde2e4]"
                    >
                      <option value="INFO">INFO (Mọi bản tin)</option>
                      <option value="WARNING">WARNING (Cảnh báo sớm)</option>
                      <option value="DANGER">DANGER (Nguy hiểm)</option>
                      <option value="CRITICAL">CRITICAL (Đặc biệt khẩn)</option>
                    </select>
                  </div>
                </div>

                <button
                  id="btn-submit-subscription"
                  type="submit"
                  disabled={submitting}
                  className="w-full py-2.5 bg-[#e2ffcc] text-[#161b13] font-bold uppercase text-xs hover:opacity-90 disabled:opacity-50"
                >
                  {submitting
                    ? "ĐANG GỬI MÃ XÁC THỰC..."
                    : "GỬI MÃ XÁC THỰC QUA EMAIL"}
                </button>
              </form>
            )
          ) : (
            /* Tab 2: Danh sách đăng ký đang hoạt động trên trình duyệt */
            <div className="space-y-2">
              {savedList.length === 0 ? (
                <div className="p-6 text-center text-[#84907f] border border-[#2d3329] bg-[#1a2117] text-[11px]">
                  Chưa có đăng ký email nào được kích hoạt trên trình duyệt này.
                </div>
              ) : (
                savedList.map((item) => (
                  <div
                    key={item.id}
                    className="p-3 border border-[#2d3329] bg-[#1a2117] space-y-2"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <div className="font-bold text-[#e2ffcc] flex items-center gap-1.5">
                          <MapPin className="w-3.5 h-3.5" />
                          <span>{item.label}</span>
                        </div>
                        <div className="text-[10px] text-[#84907f] mt-0.5">
                          Email: <strong className="text-[#dde2e4]">{item.maskedEmail}</strong>{" "}
                          · Bán kính {item.radiusKm} km · Mức {item.minSeverity}+
                        </div>
                      </div>
                      <span className="text-[9px] px-1.5 py-0.5 border border-[#e2ffcc] text-[#e2ffcc] font-bold">
                        ACTIVE
                      </span>
                    </div>

                    <div className="flex items-center gap-2 pt-1">
                      <button
                        type="button"
                        disabled={submitting}
                        onClick={() =>
                          void handleSendTest(item.id, item.unsubscribeToken)
                        }
                        className="flex-1 py-1.5 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#161b13] text-[10px] text-[#e2ffcc] font-bold uppercase flex items-center justify-center gap-1"
                      >
                        <Send className="w-3 h-3" />
                        <span>GỬI TIN KIỂM TRA</span>
                      </button>
                      <button
                        type="button"
                        disabled={submitting}
                        onClick={() => void handleUnsubscribeSaved(item)}
                        className="py-1.5 px-2.5 border border-[#dc2626] hover:bg-[#dc2626] text-[10px] text-[#dc2626] hover:text-[#161b13] font-bold uppercase flex items-center gap-1"
                      >
                        <Trash2 className="w-3 h-3" />
                        <span>HỦY ĐĂNG KÝ</span>
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default EmailSubscriptionModal;
