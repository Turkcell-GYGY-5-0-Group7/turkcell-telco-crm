# Build context: repo root (telco-crm/).
# docker build -f microservices/config-server/Dockerfile -t config-server .
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY platform/ platform/
COPY microservices/pom.xml                      microservices/pom.xml
COPY microservices/config-server/pom.xml        microservices/config-server/pom.xml
COPY microservices/config-server/src            microservices/config-server/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f platform/pom.xml install -DskipTests --no-transfer-progress -q && \
    mvn -f microservices/pom.xml -pl config-server package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Bake configs into the image for self-contained dev containers.
# Override CONFIG_DIR to a mounted volume path in staging/prod.
COPY --from=builder /workspace/microservices/configs /configs
COPY --from=builder /workspace/microservices/config-server/target/*.jar app.jar
ENV CONFIG_DIR=/configs
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
