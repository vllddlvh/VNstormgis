/**
 * @file AiChatDrawer.tsx
 * @description Ngăn kéo trợ lý AI đối thoại tác chiến thiên tai (AI Weather Copilot Drawer).
 * Nút kích hoạt nổi góc dưới bên phải (z-1000) và khung trượt bên phải (z-1200) chuẩn San Rita.
 * Hỗ trợ các câu lệnh truy vấn phân tích trắc địa thời tiết và điều hướng bản đồ trực tiếp.
 *
 * @author VNstormgis Team - Frontend & AI Specialist
 * @version 1.0.0
 */

import React, { useState, useEffect } from "react";
import { Bot, X, Send, Sparkles, Navigation, Terminal } from "lucide-react";
import { CoordinateTuple } from "@/types/map";

export interface AiChatMessage {
  id: string;
  sender: "user" | "copilot";
  content: string;
  timestamp: string;
  actionCoords?: CoordinateTuple;
  actionLabel?: string;
  zoom?: number;
}

export interface AiChatDrawerProps {
  /** Callback điều hướng bản đồ đến tọa độ được AI gợi ý */
  onNavigateToLocation: (coords: CoordinateTuple, zoom?: number) => void;
  /** Prompt kích hoạt từ bên ngoài (ví dụ click từ Marker Popup) */
  externalPrompt?: string | null;
  /** Callback giải phóng prompt bên ngoài sau khi đã xử lý */
  onClearExternalPrompt?: () => void;
}

export const AiChatDrawer: React.FC<AiChatDrawerProps> = ({
  onNavigateToLocation,
  externalPrompt,
  onClearExternalPrompt,
}) => {
  const [isOpen, setIsOpen] = useState<boolean>(false);
  const [inputQuery, setInputQuery] = useState<string>("");
  const [messages, setMessages] = useState<AiChatMessage[]>([
    {
      id: "msg-1",
      sender: "copilot",
      content:
        "Hệ thống VN-AI Copilot đã sẵn sàng. Dữ liệu trạm khí tượng thủy văn WGS84 và lớp phản hồi Radar đã được đồng bộ. Đồng chí cần kiểm tra tình huống thiên tai tại khu vực nào?",
      timestamp: "17:00",
    },
    {
      id: "msg-2",
      sender: "copilot",
      content:
        "CẢNH BÁO TÂM ĐIỂM: Vùng biển Đà Nẵng đang ghi nhận lượng mưa 85.4mm kèm gió giật cấp 7. Quần đảo Trường Sa chịu sóng lớn do bão số 3.",
      timestamp: "17:05",
      actionCoords: [16.0544, 108.2022],
      actionLabel: "ĐỊNH VỊ TRẠM ĐÀ NẴNG",
      zoom: 11,
    },
  ]);

  // Tự động mở ngăn kéo và gửi prompt khi người dùng kích hoạt từ Popup Marker
  useEffect(() => {
    if (externalPrompt) {
      setIsOpen(true);
      const query = externalPrompt;
      onClearExternalPrompt?.();

      const userMsg: AiChatMessage = {
        id: `user-${Date.now()}`,
        sender: "user",
        content: query,
        timestamp: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }),
      };

      setMessages((prev) => [...prev, userMsg]);

      setTimeout(() => {
        const copilotMsg: AiChatMessage = {
          id: `copilot-${Date.now()}`,
          sender: "copilot",
          content: `[PHÂN TÍCH KHÍ TƯỢNG TỨC THỜI]: Đã nhận dữ liệu không gian thời gian thực cho yêu cầu "${query}". Thông số trạm quan trắc PostGIS đang được đối soát với trạm lân cận. Tình trạng hoạt động bình thường, không ghi nhận dị thường số liệu.`,
          timestamp: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }),
        };
        setMessages((prev) => [...prev, copilotMsg]);
      }, 500);
    }
  }, [externalPrompt, onClearExternalPrompt]);

  const handleSendMessage = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!inputQuery.trim()) return;

    const query = inputQuery.trim();
    const userMsg: AiChatMessage = {
      id: `user-${Date.now()}`,
      sender: "user",
      content: query,
      timestamp: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }),
    };

    setMessages((prev) => [...prev, userMsg]);
    setInputQuery("");

    // Mô phỏng phản hồi AI tác chiến thời tiết theo từ khóa
    setTimeout(() => {
      let replyContent = "";
      let replyCoords: CoordinateTuple | undefined;
      let replyLabel: string | undefined;
      let replyZoom: number | undefined;

      const lower = query.toLowerCase();
      if (lower.includes("đà nẵng") || lower.includes("dng")) {
        replyContent =
          "Trạm Khí tượng Đà Nẵng (DNG): Ghi nhận mưa to 85.4mm, gió Đông 54km/h. Cảnh báo nguy cơ ngập úng nội thị lưu vực sông Hàn.";
        replyCoords = [16.0544, 108.2022];
        replyLabel = "BAY TỚI TRẠM ĐÀ NẴNG";
        replyZoom = 11;
      } else if (lower.includes("bão") || lower.includes("yagi") || lower.includes("biển đông")) {
        replyContent =
          "Bão số 3 (YAGI) duy trì sức gió 165km/h (Cấp 14), áp suất tâm 935 hPa. Dự báo trong 48h tới di chuyển theo hướng Tây Tây Bắc tiến vào Vịnh Bắc Bộ.";
        replyCoords = [18.2, 114.5];
        replyLabel = "ĐỊNH VỊ TÂM BÃO BIỂN ĐÔNG";
        replyZoom = 7;
      } else if (
        lower.includes("sài gòn") ||
        lower.includes("hồ chí minh") ||
        lower.includes("sgn")
      ) {
        replyContent =
          "Trạm Tân Sơn Hòa (SGN): Mưa rất lớn 215mm kết hợp đỉnh triều cường sông Sài Gòn. Cấp độ rủi ro EXTREME (Cấp 5).";
        replyCoords = [10.7967, 106.6633];
        replyLabel = "BAY TỚI TP. HỒ CHÍ MINH";
        replyZoom = 12;
      } else {
        replyContent = `Đã ghi nhận yêu cầu: "${query}". Toàn quốc hiện có 68 trạm quan trắc PostGIS đang được giám sát liên tục. Trọng điểm thời tiết đang tập trung tại Trung Bộ và Biển Đông.`;
      }

      const copilotMsg: AiChatMessage = {
        id: `copilot-${Date.now()}`,
        sender: "copilot",
        content: replyContent,
        timestamp: new Date().toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }),
        actionCoords: replyCoords,
        actionLabel: replyLabel,
        zoom: replyZoom,
      };

      setMessages((prev) => [...prev, copilotMsg]);
    }, 600);
  };

  const handleQuickPrompt = (promptText: string) => {
    setInputQuery(promptText);
  };

  return (
    <>
      {/* 1. NÚT KÍCH HOẠT FLOATING BUTTON (GÓC DƯỚI PHẢI, Z-1000) */}
      <div className="absolute bottom-4 right-4 z-[1000] font-mono select-none">
        <button
          id="btn-ai-copilot-trigger"
          onClick={() => setIsOpen((prev) => !prev)}
          className="flex items-center gap-2 px-3 py-2 bg-[#161b13] border border-[#2d3329] hover:border-[#e2ffcc] text-[#dde2e4] hover:text-[#e2ffcc] transition-colors shadow-flat"
          title="Mở trợ lý AI Copilot"
        >
          <div className="relative">
            <Bot className="w-4 h-4 text-[#e2ffcc]" />
            <span className="absolute -top-0.5 -right-0.5 w-1.5 h-1.5 bg-[#e2ffcc] animate-ping" />
          </div>
          <span className="text-xs uppercase font-bold tracking-wider hidden sm:inline">
            AI COPILOT
          </span>
          <span className="text-[9px] px-1 py-0.2 border border-[#2d3329] bg-[#1a2117] text-[#84907f]">
            ONLINE
          </span>
        </button>
      </div>

      {/* 2. NGĂN KÉO ĐỐI THOẠI TRƯỢT BÊN PHẢI (Z-1200) */}
      {isOpen && (
        <div className="absolute top-0 right-0 w-80 sm:w-96 h-full bg-[#161b13] border-l border-[#2d3329] z-[1200] flex flex-col shadow-flat-lg font-mono text-[#dde2e4] select-none">
          {/* Header Drawer */}
          <div className="p-3 border-b border-[#2d3329] bg-[#1a2117] flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-[#e2ffcc]" />
              <div>
                <div className="text-xs uppercase font-bold text-[#e2ffcc] tracking-wider">
                  VN-AI WEATHER COPILOT
                </div>
                <div className="text-[9px] text-[#84907f]">MÔ HÌNH PHÂN TÍCH KHÍ TƯỢNG TỰ ĐỘNG</div>
              </div>
            </div>
            <button
              id="btn-ai-copilot-close"
              onClick={() => setIsOpen(false)}
              className="p-1 border border-[#2d3329] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors"
              title="Đóng ngăn kéo AI"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          {/* Quick Prompts Bar */}
          <div className="p-2 border-b border-[#2d3329] bg-[#161b13] flex gap-1.5 overflow-x-auto text-[9px]">
            <button
              id="btn-prompt-danang"
              onClick={() => handleQuickPrompt("Tình hình trạm Đà Nẵng?")}
              className="px-2 py-1 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#1a2117] text-[#84907f] hover:text-[#e2ffcc] whitespace-nowrap transition-colors"
            >
              ⚡ ĐÀ NẴNG
            </button>
            <button
              id="btn-prompt-yagi"
              onClick={() => handleQuickPrompt("Tâm bão số 3 YAGI ở đâu?")}
              className="px-2 py-1 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#1a2117] text-[#84907f] hover:text-[#e2ffcc] whitespace-nowrap transition-colors"
            >
              🌀 BÃO BIỂN ĐÔNG
            </button>
            <button
              id="btn-prompt-hcm"
              onClick={() => handleQuickPrompt("Ngập lụt tại TP. Hồ Chí Minh?")}
              className="px-2 py-1 border border-[#2d3329] hover:border-[#e2ffcc] bg-[#1a2117] text-[#84907f] hover:text-[#e2ffcc] whitespace-nowrap transition-colors"
            >
              🌊 TP.HCM
            </button>
          </div>

          {/* Message List */}
          <div className="flex-1 p-3 space-y-3 overflow-y-auto">
            {messages.map((msg) => (
              <div
                key={msg.id}
                className={`p-2.5 border text-xs space-y-1.5 ${
                  msg.sender === "user"
                    ? "border-[#2d3329] bg-[#1a2117] ml-6 text-right"
                    : "border-[#2d3329] bg-[#161b13] mr-4 text-left"
                }`}
              >
                <div className="flex items-center justify-between text-[9px] text-[#84907f]">
                  <span className="uppercase font-bold text-[#e2ffcc]">
                    {msg.sender === "user" ? "[KỸ SƯ VẬN HÀNH]" : "[AI COPILOT]"}
                  </span>
                  <span>{msg.timestamp}</span>
                </div>
                <div className="text-[#dde2e4] leading-relaxed text-[11px] whitespace-pre-wrap">
                  {msg.content}
                </div>
                {msg.actionCoords && msg.actionLabel && (
                  <button
                    onClick={() => onNavigateToLocation(msg.actionCoords!, msg.zoom || 11)}
                    className="w-full mt-2 py-1 px-2 border border-[#e2ffcc] bg-[#1a2117] hover:bg-[#e2ffcc] text-[#e2ffcc] hover:text-[#161b13] text-[9px] font-bold uppercase flex items-center justify-center gap-1.5 transition-colors"
                  >
                    <Navigation className="w-3 h-3" />
                    <span>{msg.actionLabel}</span>
                  </button>
                )}
              </div>
            ))}
          </div>

          {/* Terminal Input Form */}
          <form
            onSubmit={handleSendMessage}
            className="p-2 border-t border-[#2d3329] bg-[#1a2117] flex items-center gap-1.5"
          >
            <div className="flex items-center gap-1 text-[#84907f] pl-1">
              <Terminal className="w-3.5 h-3.5 text-[#e2ffcc]" />
              <span className="text-xs">&gt;</span>
            </div>
            <input
              id="input-ai-prompt"
              type="text"
              value={inputQuery}
              onChange={(e) => setInputQuery(e.target.value)}
              placeholder="Nhập lệnh truy vấn trắc địa..."
              className="flex-1 bg-transparent border-none outline-none text-xs text-[#dde2e4] placeholder-[#84907f]"
            />
            <button
              id="btn-ai-send"
              type="submit"
              className="p-1.5 border border-[#2d3329] hover:border-[#e2ffcc] text-[#84907f] hover:text-[#e2ffcc] transition-colors"
              title="Gửi truy vấn"
            >
              <Send className="w-3.5 h-3.5" />
            </button>
          </form>
        </div>
      )}
    </>
  );
};

export default AiChatDrawer;
