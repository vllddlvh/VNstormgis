/**
 * Chuẩn bao bọc phản hồi API thành công từ Spring Boot Backend.
 */
export interface ApiResponse<T> {
  success: boolean;
  code?: number;
  message: string;
  data: T;
  timestamp: string;
}

/**
 * Định dạng phản hồi lỗi chuẩn công nghiệp Problem Details (RFC 7807).
 * Tương thích 100% với GlobalExceptionHandler tại Backend (Task W1-08).
 */
export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  invalidParams?: Record<string, string>;
}
