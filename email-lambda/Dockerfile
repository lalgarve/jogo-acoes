# Builds only the `email-lambda` module (Quarkus JVM mode -- native is manual/local only, see
# email-lambda/pom.xml's `native` profile). Build context is the repo root, same reason as the
# root Dockerfile: the reactor needs pom.xml + every module's pom.xml to resolve the build
# graph even though -pl below only packages email-lambda.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY app/pom.xml app/pom.xml
COPY blackbox-proxy/pom.xml blackbox-proxy/pom.xml
COPY email-lambda/pom.xml email-lambda/pom.xml
RUN mvn -B -pl email-lambda -am dependency:go-offline
COPY email-lambda/src email-lambda/src
RUN mvn -B -pl email-lambda -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# This module packages as a legacy thin jar (not the fast-jar layout app/'s Dockerfile uses) --
# `email-lambda-*-runner.jar`'s manifest Class-Path references `lib/...` relatively, so the jar
# and lib/ must land in the same directory, same as target/ itself.
COPY --from=build /build/email-lambda/target/email-lambda-*-runner.jar /app/app.jar
COPY --from=build /build/email-lambda/target/lib/ /app/lib/
ENTRYPOINT ["java", "-jar", "app.jar"]
