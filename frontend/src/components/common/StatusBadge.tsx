import React from "react";
import { cn } from "@/lib/utils";
import { Activity, CheckCircle2, AlertTriangle, XCircle } from "lucide-react";

export type StatusType = "online" | "warning" | "error" | "loading";

interface StatusBadgeProps {
  status: StatusType;
  label: string;
  subLabel?: string;
  className?: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, label, subLabel, className }) => {
  const statusConfig = {
    online: {
      bg: "bg-[#1a2117] border-[#2d3329] text-[#e2ffcc]",
      dot: "bg-[#e2ffcc]",
      icon: CheckCircle2,
    },
    warning: {
      bg: "bg-[#1a2117] border-[#f59e0b]/60 text-[#f59e0b]",
      dot: "bg-[#f59e0b]",
      icon: AlertTriangle,
    },
    error: {
      bg: "bg-[#1a2117] border-[#dc2626]/60 text-[#dc2626]",
      dot: "bg-[#dc2626]",
      icon: XCircle,
    },
    loading: {
      bg: "bg-[#1a2117] border-[#2d3329] text-[#84907f]",
      dot: "bg-[#84907f] animate-pulse",
      icon: Activity,
    },
  };

  const current = statusConfig[status];
  const Icon = current.icon;

  return (
    <div
      className={cn(
        "inline-flex items-center gap-2 px-2.5 py-1 border font-mono text-[10px] uppercase tracking-wider select-none",
        current.bg,
        className,
      )}
    >
      <span className={cn("w-1.5 h-1.5", current.dot)} />
      <Icon className="w-3 h-3" />
      <span className="font-bold">{label}</span>
      {subLabel && <span className="text-[#84907f] font-normal">| {subLabel}</span>}
    </div>
  );
};
