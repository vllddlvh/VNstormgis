import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Hợp nhất các lớp CSS Tailwind một cách an toàn và loại bỏ các lớp trùng lặp/xung đột.
 * @param inputs Danh sách các lớp CSS hoặc biểu thức điều kiện.
 * @returns Chuỗi lớp CSS hoàn chỉnh đã được tinh giản.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
