# ==========================================
# STAGE 1: Build Application Artifact
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Gradle wrapper and configuration from VNstormgis-Backend
COPY VNstormgis-Backend/gradlew ./gradlew
COPY VNstormgis-Backend/gradle ./gradle
COPY VNstormgis-Backend/build.gradle ./build.gradle
COPY VNstormgis-Backend/settings.gradle ./settings.gradle
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# Copy backend source and build Spring Boot executable JAR
COPY VNstormgis-Backend/src ./src
RUN ./gradlew bootJar -x test --no-daemon

# Extract Spring Boot Layered JAR for optimal Docker layer caching
WORKDIR /build/build/libs
RUN java -Djarmode=layertools -jar *.jar extract

# ==========================================
# STAGE 2: Lightweight Production Runtime
# ==========================================
FROM bellsoft/liberica-openjre-alpine:21.0.3

# Create non-root user and group (Master Timeline §3.7 & Deployment §7.1)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Install curl for container healthcheck and tzdata for Vietnam timezone
RUN apk add --no-cache curl tzdata
ENV TZ=Asia/Ho_Chi_Minh

# Copy extracted layers from builder stage
COPY --from=builder /build/build/libs/dependencies/ ./
COPY --from=builder /build/build/libs/spring-boot-loader/ ./
COPY --from=builder /build/build/libs/snapshot-dependencies/ ./
COPY --from=builder /build/build/libs/application/ ./

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC \
                       -XX:MaxRAMPercentage=75.0 \
                       -XX:+ExitOnOutOfMemoryError \
                       -Dfile.encoding=UTF-8 \
                       -Duser.timezone=Asia/Ho_Chi_Minh"

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness || curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
