# Build context: repo root (telco-crm/).
# docker build -f microservices/web-bff/Dockerfile -t web-bff .
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY platform/ platform/
COPY microservices/pom.xml                microservices/pom.xml
COPY microservices/web-bff/pom.xml          microservices/web-bff/pom.xml
COPY microservices/web-bff/src              microservices/web-bff/src

# BuildKit cache mount keeps .m2 across rebuilds without baking deps into layers.
# Cache is busted only when platform source or service pom changes.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f platform/pom.xml install -DskipTests --no-transfer-progress -q && \
    mvn -f microservices/pom.xml -pl web-bff package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/microservices/web-bff/target/*.jar app.jar
EXPOSE 9020
ENTRYPOINT ["java", "-jar", "app.jar"]
