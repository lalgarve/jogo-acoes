#!/usr/bin/env bash
# Caderno de testes (spec 05-040): carrega a massa de dados do caderno
# (../dados/jogo-acoes-massa.sql) no banco do app. Passos, cada um escrito na tela:
#   1. confere se o dump foi gerado com a última migration do repositório -- se o repositório
#      tem migration mais nova, recusa e manda regenerar (gerar-massa.sh);
#   2. para o app (o Flyway e as sessões não podem estar usando o schema);
#   3. apaga o schema jogo_acoes e carrega o dump;
#   4. confere a massa com ../dados/verificar-massa.sql.
# Depois, subir o app com:
#   docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up -d app
#
# Uso: ./restaurar-massa.sh
set -euo pipefail

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CADERNO_DIR="$(cd "$SCRIPTS_DIR/.." && pwd)"
REPO="$(cd "$CADERNO_DIR/../../.." && pwd)"
DUMP="$CADERNO_DIR/dados/jogo-acoes-massa.sql"
VERIFICACAO="$CADERNO_DIR/dados/verificar-massa.sql"
MIGRATIONS="${MIGRATIONS_DIR:-$REPO/app/src/main/resources/db/migration-jogo-acoes}"
COMPOSE=(docker compose -f "$REPO/docker-compose.yml" -f "$REPO/docker-compose.blackbox.yml")
PSQL=("${COMPOSE[@]}" exec -T db psql -U jogo_acoes_admin -d jogo_acoes -v ON_ERROR_STOP=1 -q)

echo "==> 1. Conferindo a versão do dump ($DUMP)"
VERSAO_DUMP=$(sed -nE 's/^-- versao-flyway: ([0-9]+)$/\1/p' "$DUMP")
VERSAO_REPO=$(ls "$MIGRATIONS" | sed -nE 's/^V([0-9]+)__.*/\1/p' | sort -n | tail -1)
echo "    dump gerado na migration V$VERSAO_DUMP ($(sed -nE 's/^-- data-geracao: //p' "$DUMP")); repositório na V$VERSAO_REPO"
if [ "$VERSAO_DUMP" != "$VERSAO_REPO" ]; then
  echo "Erro: o dump é da migration V$VERSAO_DUMP, mas o repositório já está na V$VERSAO_REPO." >&2
  echo "      Gere a massa de novo com $SCRIPTS_DIR/gerar-massa.sh." >&2
  exit 1
fi

echo "==> 2. Parando o app"
docker ps -q --filter label=com.docker.compose.service=app | xargs -r docker stop >/dev/null
"${COMPOSE[@]}" up -d --wait db

echo "==> 3. Apagando o schema jogo_acoes e carregando o dump"
"${PSQL[@]}" -c "SET client_min_messages = warning; DROP SCHEMA IF EXISTS jogo_acoes CASCADE"
"${PSQL[@]}" -f - < "$DUMP" > /dev/null
echo "    massa carregada"

echo "==> 4. Conferindo a massa ($VERIFICACAO)"
"${PSQL[@]}" -f - < "$VERIFICACAO"
echo "Pronto. Suba o app com:"
echo "  docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up -d app"
