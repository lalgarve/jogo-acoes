# Java 21 is the project's supported baseline; changing the LTS requires an explicit project
# decision across Maven, CI, and all runtime images.
# Builds only the `app` module (Spring Boot) -- `email-lambda` (Quarkus) has its own,
# separate build/deploy story and doesn't belong in this image. Build context is the repo
# root (not app/) so the reactor's root pom.xml is available; see docker-compose.yml.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY app/pom.xml app/pom.xml
# The reactor pom.xml lists email-lambda, blackbox-proxy and email-service as modules too --
# Maven needs to read their pom.xml even though -pl app below only builds/packages the app
# module.
COPY email-lambda/pom.xml email-lambda/pom.xml
COPY blackbox-proxy/pom.xml blackbox-proxy/pom.xml
COPY email-service/pom.xml email-service/pom.xml
RUN mvn -B -pl app -am dependency:go-offline
# app/pom.xml's openapi-generator-maven-plugin and maven-resources-plugin both read
# ../docs/openapi.yaml (relative to the app module) during `package` -- without this, the
# build fails as soon as it reaches generate-sources, in every profile, not just blackbox.
COPY docs docs
COPY app/src app/src
RUN mvn -B -pl app -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/app/target/*.jar app.jar
# JaCoCo's runtime agent + CLI (spec 05-014) -- present in every image, but inert: only
# docker-compose.blackbox.yml actually attaches the agent (via JAVA_TOOL_OPTIONS), so this
# doesn't change anything about how the image runs outside that overlay.
COPY --from=build /build/app/target/jacoco/jacocoagent.jar /app/jacocoagent.jar
COPY --from=build /build/app/target/jacoco/jacococli.jar /app/jacococli.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
