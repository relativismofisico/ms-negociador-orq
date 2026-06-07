# ─────────────────────────────────────────────
# Stage 1: build
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace

COPY gradlew .
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew --version

COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon --quiet || true

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test -x checkstyleMain -x checkstyleTest \
    -x pmdMain -x pmdTest -x spotbugsMain -x spotbugsTest \
    -x architectureTest -x automatedTest

# ─────────────────────────────────────────────
# Stage 2: runtime
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build --chown=appuser:appgroup \
    /workspace/build/libs/*.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]