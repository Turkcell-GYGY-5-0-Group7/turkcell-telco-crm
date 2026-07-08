# syntax=docker/dockerfile:1
# Build context: repo root (telco-crm/).
# docker build -f microservices/api-gateway/Dockerfile -t api-gateway .
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

COPY platform/ platform/
COPY microservices/pom.xml                microservices/pom.xml
COPY --parents microservices/*/pom.xml ./
COPY microservices/api-gateway/src              microservices/api-gateway/src

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -f platform/pom.xml install -DskipTests -Dschema.registry.skip=true --no-transfer-progress -q && \
    mvn -f microservices/pom.xml -pl api-gateway package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
COPY --from=builder /workspace/microservices/api-gateway/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=12 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q UP || exit 1
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
