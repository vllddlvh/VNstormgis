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
      bg: "bg-emerald-950/80 border-emerald-500/50 text-emerald-300",
      dot: "bg-emerald-400 animate-pulse",
      icon: CheckCircle2,
    },
    warning: {
      bg: "bg-amber-950/80 border-amber-500/50 text-amber-300",
      dot: "bg-amber-400",
      icon: AlertTriangle,
    },
    error: {
      bg: "bg-rose-950/80 border-rose-500/50 text-rose-300",
      dot: "bg-rose-400",
      icon: XCircle,
    },
    loading: {
      bg: "bg-slate-800/80 border-slate-600/50 text-slate-300",
      dot: "bg-sky-400 animate-ping",
      icon: Activity,
    },
  };

  const current = statusConfig[status];
  const Icon = current.icon;

  return (
    <div
      className={cn(
        "inline-flex items-center gap-2 px-3 py-1.5 rounded-full border text-xs font-medium backdrop-blur-sm shadow-sm transition-all",
        current.bg,
        className,
      )}
    >
      <span className={cn("w-2 h-2 rounded-full", current.dot)} />
      <Icon className="w-3.5 h-3.5" />
      <span>{label}</span>
      {subLabel && <span className="text-slate-400 font-normal">| {subLabel}</span>}
    </div>
  );
};
