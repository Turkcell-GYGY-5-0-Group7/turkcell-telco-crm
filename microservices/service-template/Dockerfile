# syntax=docker/dockerfile:1
# Build the service jar with the Spring Boot plugin, then run on a slim JRE.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -q -pl microservices/service-template -am -DskipTests package || \
    mvn -q -pl microservices/service-template -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/microservices/service-template/target/*.jar app.jar
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "app.jar"]
