# Builds only the `app` module (Spring Boot) -- `email-lambda` (Quarkus) has its own,
# separate build/deploy story and doesn't belong in this image. Build context is the repo
# root (not app/) so the reactor's root pom.xml is available; see docker-compose.yml.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY app/pom.xml app/pom.xml
# The reactor pom.xml lists email-lambda as a module too -- Maven needs to read its pom.xml
# to build the reactor graph even though -pl app below only builds/packages the app module.
COPY email-lambda/pom.xml email-lambda/pom.xml
RUN mvn -B -pl app -am dependency:go-offline
COPY app/src app/src
RUN mvn -B -pl app -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
