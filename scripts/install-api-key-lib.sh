#!/usr/bin/env bash
# Installs the api-key libraries (lalgarve/api-key, release v1.0.1) into the local Maven
# repository (~/.m2), so email-service can depend on dev.leilaalgarve.apikey:api-key-validation
# like any other artifact (spec 05-030, plan.md, "Como distribuir api-key-validation para o
# build").
#
# GitHub is only reached the first time on each machine: once the three artifacts are in ~/.m2,
# this script does nothing. The release is public, so no credential is needed -- unlike GitHub
# Packages, which requires a token even to read.
#
# Three artifacts are installed:
#   api-key-parent      the parent POM, from pom.xml at tag v1.0.1 -- the POMs embedded in the
#                       two jars inherit from it; without it Maven can't see their transitive
#                       dependencies (spring-boot-starter-data-jpa)
#   api-key-core        entity, repository, hasher (release asset, POM embedded in the jar)
#   api-key-validation  ApiKeyValidator (release asset, POM embedded in the jar)
#
# Usage: ./scripts/install-api-key-lib.sh
set -euo pipefail

VERSION="1.0.1"
GROUP_ID="dev.leilaalgarve.apikey"
REPO="lalgarve/api-key"
RELEASE_URL="https://github.com/$REPO/releases/download/v$VERSION"
PARENT_POM_URL="https://raw.githubusercontent.com/$REPO/v$VERSION/pom.xml"

M2_REPO="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"
GROUP_DIR="$M2_REPO/${GROUP_ID//.//}"

installed() {
  [ -f "$GROUP_DIR/$1/$VERSION/$1-$VERSION.$2" ]
}

if installed api-key-parent pom && installed api-key-core jar && installed api-key-validation jar; then
  echo "api-key $VERSION already in $M2_REPO -- nothing to do"
  exit 0
fi

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

download() {
  curl -fsSL --retry 3 -o "$WORK_DIR/$2" "$1"
}

mvn_install() {
  mvn -B -q install:install-file -Dmaven.repo.local="$M2_REPO" "$@"
}

download "$PARENT_POM_URL" api-key-parent.pom
download "$RELEASE_URL/api-key-core-$VERSION.jar" api-key-core.jar
download "$RELEASE_URL/api-key-validation-$VERSION.jar" api-key-validation.jar

mvn_install -Dfile="$WORK_DIR/api-key-parent.pom" -DpomFile="$WORK_DIR/api-key-parent.pom" \
  -Dpackaging=pom
# The jars carry their own pom.xml under META-INF/maven/, which install-file picks up -- that is
# what keeps api-key-validation -> api-key-core -> spring-boot-starter-data-jpa resolvable.
mvn_install -Dfile="$WORK_DIR/api-key-core.jar"
mvn_install -Dfile="$WORK_DIR/api-key-validation.jar"

echo "Installed api-key $VERSION into $M2_REPO"
