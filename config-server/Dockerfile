# Java 21 is the project's supported baseline; changing the LTS requires an explicit project
# decision across Maven, CI, and all runtime images.
# Builds only the `config-server` module (spec 05-042) -- same pattern as email-service/Dockerfile.
# Build context is the repo root (not config-server/) so the reactor's root pom.xml is available;
# see docker-compose.yml. The configuration it serves (config-repo/) is not baked into the image:
# docker-compose.yml mounts it, and each environment provides its own CONFIG_REPO_LOCATION.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY config-server/pom.xml config-server/pom.xml
# The reactor pom.xml lists the other modules too -- Maven needs to read their pom.xml even
# though -pl config-server below only builds/packages this module.
COPY app/pom.xml app/pom.xml
COPY email-lambda/pom.xml email-lambda/pom.xml
COPY email-service/pom.xml email-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl config-server -am dependency:go-offline
COPY config-server/src config-server/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl config-server -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/config-server/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "app.jar"]
