import axios, { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import { ProblemDetail } from "@/types/api";

/**
 * Tạo Axios Singleton Instance với cấu hình mặc định.
 * BaseURL trỏ tới '/api' để được Vite Reverse Proxy chuyển tiếp sang ':8080'.
 */
export const apiClient = axios.create({
  baseURL: "/api",
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json",
  },
});

// Request Interceptor: Ghi nhật ký yêu cầu trong chế độ phát triển
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (import.meta.env.DEV) {
      console.debug(`[HTTP Request] ${config.method?.toUpperCase()} ${config.url}`);
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// Response Interceptor: Bóc tách dữ liệu và chuẩn hóa thông báo lỗi RFC 7807
apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  (error: AxiosError<ProblemDetail>) => {
    if (error.response?.data) {
      const problem = error.response.data;
      console.error(
        `[HTTP Error ${problem.status || error.response.status}] ${problem.title}: ${
          problem.detail || "Không có mô tả"
        }`,
      );
    } else {
      console.error(`[Network Error] Không thể kết nối tới máy chủ Backend: ${error.message}`);
    }
    return Promise.reject(error);
  },
);
