#!/usr/bin/env bash
# Caderno de testes do email-service: gera as duas chaves de API do caderno, uma para cada caso
# de acesso (spec 05-048, "Permissões"), com a CLI do api-key 1.1.0. Passos, cada um escrito na
# tela:
#   1. confere que o db-email-service está de pé e acha a rede do Compose;
#   2. baixa a CLI (api-key-1.1.0.jar) para ../.cli/, só na primeira vez;
#   3. gera a chave do cliente caderno-envio, só com email:send (enviar e-mail, mudar template);
#   4. gera a chave do cliente caderno-operacoes, só com disposable-email:admin (listas de
#      e-mail temporário);
#   5. define o remetente do caderno-envio (scripts/set-email-sender.sh), o mesmo endereço que o
#      LocalStack verifica para o jogo-acoes;
#   6. grava as chaves em ../chaves.env (fora do git) e mostra as chaves ativas com `list`.
#
# A CLI roda num container eclipse-temurin:21-jre na rede do Compose, contra o schema api_key do
# db-email-service, com o mesmo pepper e o mesmo catálogo de permissões do email-service.
# Rodar depois do `./scripts/test-api-key.sh restore`: o restore apaga o schema api_key e, com ele,
# as chaves geradas aqui.
#
# Uso:    ./gerar-chaves.sh
# Precisa de docker e curl.
set -euo pipefail

CADERNO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$CADERNO_DIR/../../.." && pwd)"
VERSAO_CLI="1.1.0"
JAR="$CADERNO_DIR/.cli/api-key-$VERSAO_CLI.jar"
URL_JAR="https://github.com/lalgarve/api-key/releases/download/v$VERSAO_CLI/api-key-$VERSAO_CLI.jar"
CHAVES="$CADERNO_DIR/chaves.env"
REMETENTE="no-reply@jogo-acoes.example"

echo "==> 1. Procurando o db-email-service"
CONTAINER=$(cd "$REPO_ROOT" && docker compose ps -q db-email-service)
[ -n "$CONTAINER" ] || { echo "Erro: db-email-service não está de pé (ver a seção 1 do caderno)." >&2; exit 1; }
REDE=$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{"\n"}}{{end}}' "$CONTAINER" | head -1)
echo "    container $CONTAINER, rede $REDE"

echo "==> 2. CLI do api-key $VERSAO_CLI"
if [ -f "$JAR" ]; then
  echo "    já baixada em $JAR"
else
  mkdir -p "$(dirname "$JAR")"
  echo "    baixando $URL_JAR"
  curl -fsSL --retry 3 -o "$JAR" "$URL_JAR"
fi

cli() {
  docker run --rm --network "$REDE" -v "$(dirname "$JAR"):/cli:ro" \
    -e API_KEY_HMAC_PEPPER=jogo-acoes-test-pepper-not-a-secret \
    -e SPRING_PROFILES_ACTIVE=docker \
    -e SPRING_DATASOURCE_URL='jdbc:postgresql://db-email-service:5432/email_service?currentSchema=api_key' \
    -e SPRING_DATASOURCE_USERNAME=email_service_admin \
    -e SPRING_DATASOURCE_PASSWORD=email_service_admin \
    -e SPRING_FLYWAY_SCHEMAS=api_key \
    -e API_KEY_PERMISSIONS_0_NAME=email:send \
    -e API_KEY_PERMISSIONS_0_DEFAULT=true \
    -e API_KEY_PERMISSIONS_1_NAME=disposable-email:admin \
    -e SPRING_MAIN_BANNER_MODE=off \
    -e LOGGING_LEVEL_ROOT=WARN \
    eclipse-temurin:21-jre java -jar "/cli/$(basename "$JAR")" "$@"
}

gerar() {
  local cliente="$1" permissao="$2" saida chave
  saida=$(cli generate --client "$cliente" --permissions "$permissao")
  echo "$saida" | sed 's/^/    /' >&2
  chave=$(echo "$saida" | grep -o 'dak_[A-Za-z0-9_-]*' | head -1)
  [ -n "$chave" ] || { echo "Erro: a CLI não devolveu a chave de $cliente." >&2; exit 1; }
  echo "$chave"
}

echo "==> 3. Gerando a chave de caderno-envio (generate --client caderno-envio --permissions email:send)"
CHAVE_ENVIO=$(gerar caderno-envio email:send)

echo "==> 4. Gerando a chave de caderno-operacoes (generate --client caderno-operacoes --permissions disposable-email:admin)"
CHAVE_OPERACOES=$(gerar caderno-operacoes disposable-email:admin)

echo "==> 5. Definindo o remetente de caderno-envio ($REMETENTE)"
"$REPO_ROOT/scripts/set-email-sender.sh" caderno-envio "$REMETENTE" | sed 's/^/    /'

echo "==> 6. Gravando as chaves em $CHAVES"
cat > "$CHAVES" <<CHAVES_ENV
# Gerado por gerar-chaves.sh em $(date -u +%Y-%m-%dT%H:%M:%SZ) -- vale até o próximo test-api-key.sh restore.
CHAVE_ENVIO=$CHAVE_ENVIO
CHAVE_OPERACOES=$CHAVE_OPERACOES
# Chave de teste do jogo-acoes (docker/postgres-email-service/test-data/README.md), anterior à
# 1.1.0: vale com as permissões padrão (email:send).
CHAVE_JOGO_ACOES=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU
CHAVES_ENV
echo "    CHAVE_ENVIO=$CHAVE_ENVIO"
echo "    CHAVE_OPERACOES=$CHAVE_OPERACOES"
echo "    chaves ativas (list):"
cli list | sed 's/^/    /'
