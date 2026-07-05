# syntax=docker/dockerfile:1
# Build context: repo root (telco-crm/).
# docker build -f microservices/billing-service/Dockerfile -t billing-service .
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

COPY platform/ platform/
COPY microservices/pom.xml                microservices/pom.xml
COPY --parents microservices/*/pom.xml ./
COPY microservices/billing-service/src              microservices/billing-service/src

# BuildKit cache mount keeps .m2 across rebuilds without baking deps into layers.
# Cache is busted only when platform source or service pom changes.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -f platform/pom.xml install -DskipTests -Dschema.registry.skip=true --no-transfer-progress -q && \
    mvn -f microservices/pom.xml -pl billing-service package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=builder /workspace/microservices/billing-service/target/*.jar app.jar
EXPOSE 9007
ENTRYPOINT ["java", "-jar", "app.jar"]
