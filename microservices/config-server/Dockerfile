# syntax=docker/dockerfile:1
# Build context: repo root (telco-crm/).
# docker build -f microservices/config-server/Dockerfile -t config-server .
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

COPY platform/ platform/
COPY microservices/pom.xml                      microservices/pom.xml
COPY --parents microservices/*/pom.xml ./
COPY microservices/configs                      microservices/configs
COPY microservices/config-server/src            microservices/config-server/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f platform/pom.xml install -DskipTests -Dschema.registry.skip=true --no-transfer-progress -q && \
    mvn -f microservices/pom.xml -pl config-server package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
# Bake configs into the image for self-contained dev containers.
# Override CONFIG_DIR to a mounted volume path in staging/prod.
COPY --from=builder /workspace/microservices/configs /configs
COPY --from=builder /workspace/microservices/config-server/target/*.jar app.jar
ENV CONFIG_DIR=/configs
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
