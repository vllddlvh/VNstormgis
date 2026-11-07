import { AlertTriangle, Info } from "lucide-react";
import { getAlertSeverityConfig } from "@/lib/alertUtils";

export function AlertSeverityBadge({ severity }: { severity: string }) {
  const config = getAlertSeverityConfig(severity);
  const Icon = severity === "INFO" ? Info : AlertTriangle;
  return (
    <span
      className={`inline-flex items-center gap-1.5 border px-2 py-1 text-[10px] font-bold uppercase ${config.className}`}
    >
      <Icon aria-hidden="true" className="h-3 w-3 shrink-0" />
      {config.label}
    </span>
  );
}

export function AlertContentSource({ isFallback }: { isFallback: boolean }) {
  return (
    <span
      className="text-[10px] text-sage"
      title={isFallback ? "Nội dung theo mẫu từ dữ kiện hệ thống" : undefined}
    >
      {isFallback ? "BẢN TIN DỰ PHÒNG" : "Nội dung AI"}
    </span>
  );
}
