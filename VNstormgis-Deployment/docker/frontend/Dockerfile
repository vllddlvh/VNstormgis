# ==========================================
# STAGE 1: Build Frontend SPA
# ==========================================
FROM node:20-alpine AS builder

WORKDIR /app

COPY VNstormgis-Frontend/package.json VNstormgis-Frontend/package-lock.json ./
RUN npm ci --silent

COPY VNstormgis-Frontend/ ./
RUN npm run build

# ==========================================
# STAGE 2: Nginx Static Server & Reverse Proxy
# ==========================================
FROM nginx:1.26-alpine

RUN rm -f /etc/nginx/conf.d/default.conf

COPY VNstormgis-Deployment/nginx/nginx.conf /etc/nginx/nginx.conf
COPY VNstormgis-Deployment/nginx/conf.d/weathergis.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD wget -q -O - http://127.0.0.1/healthz >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
