# Build context: repo root (telco-crm/).
# docker build -f microservices/discovery-server/Dockerfile -t discovery-server .
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY platform/ platform/
COPY microservices/pom.xml                microservices/pom.xml
COPY microservices/discovery-server/pom.xml          microservices/discovery-server/pom.xml
COPY microservices/discovery-server/src              microservices/discovery-server/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -f platform/pom.xml install -DskipTests --no-transfer-progress -q && \
    mvn -f microservices/pom.xml -pl discovery-server package -DskipTests --no-transfer-progress -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/microservices/discovery-server/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
