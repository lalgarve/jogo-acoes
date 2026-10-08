# Java 21 is the project's supported baseline; changing the LTS requires an explicit project
# decision across Maven, CI, and all runtime images.
# Builds only the `email-service` module (Spring Boot) -- same pattern as the root Dockerfile
# for `app`. Build context is the repo root (not email-service/) so the reactor's root pom.xml
# is available; see docker-compose.yml.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY email-service/pom.xml email-service/pom.xml
# The reactor pom.xml lists app and email-lambda as modules too -- Maven needs
# to read their pom.xml even though -pl email-service below only builds/packages this module.
COPY app/pom.xml app/pom.xml
COPY email-lambda/pom.xml email-lambda/pom.xml
# api-key-validation (spec 05-030) is not on any registry this build reads: the script installs it
# into ~/.m2 from the GitHub release. The BuildKit cache mount keeps ~/.m2 across builds, so the
# release is downloaded once, like any Maven dependency -- every RUN that needs ~/.m2 mounts it.
COPY scripts/install-api-key-lib.sh scripts/install-api-key-lib.sh
RUN --mount=type=cache,target=/root/.m2 ./scripts/install-api-key-lib.sh
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl email-service -am dependency:go-offline
# email-service/pom.xml's openapi-generator-maven-plugin reads ../docs/openapi-email-service.yaml
# (relative to this module) during `package` -- without this, the build fails as soon as it
# reaches generate-sources.
COPY docs docs
COPY email-service/src email-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl email-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/email-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
