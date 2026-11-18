#!/usr/bin/env bash
# ==============================================================================
# Khởi chạy nhanh đường hầm HTTPS công khai (Cloudflare Quick Tunnel — 0đ,
# không cần tài khoản) để gửi link demo trực tiếp cho Giảng viên / Hội đồng.
# Tự động ưu tiên Production Nginx (:8088 / :80), nếu không có sẽ dùng Vite (:5173).
# ==============================================================================
set -euo pipefail

if ! command -v cloudflared >/dev/null 2>&1; then
  echo "Chưa cài đặt 'cloudflared'. Cài nhanh trên macOS bằng lệnh:"
  echo "  brew install cloudflared"
  exit 1
fi

TARGET_URL="${FRONTEND_URL:-}"
if [[ -z "$TARGET_URL" ]]; then
  if curl -fsS "http://127.0.0.1:8088/healthz" >/dev/null 2>&1; then
    TARGET_URL="http://127.0.0.1:8088"
  elif curl -fsS "http://127.0.0.1:5173" >/dev/null 2>&1; then
    TARGET_URL="http://127.0.0.1:5173"
  elif curl -fsS "http://127.0.0.1:80/healthz" >/dev/null 2>&1; then
    TARGET_URL="http://127.0.0.1:80"
  else
    echo "[LỖI] Không tìm thấy giao diện VNstormgis đang chạy ở cổng 8088 (Production) hoặc 5173 (Dev)."
    echo "Hãy khởi chạy Production stack trước:"
    echo "  docker compose --env-file VNstormgis-Deployment/env/.env.prod -f VNstormgis-Deployment/docker-compose.prod.yml up -d"
    exit 1
  fi
fi

echo "======================================================================"
echo " Đang mở link HTTPS công khai cho VNstormgis ($TARGET_URL)..."
echo " Hãy copy đường dẫn https://....trycloudflare.com bên dưới gửi Thầy/Cô!"
echo " (Giữ nguyên cửa sổ này đang mở trong suốt thời gian Thầy/Cô xem bài)"
echo "======================================================================"

exec cloudflared tunnel --url "$TARGET_URL"
