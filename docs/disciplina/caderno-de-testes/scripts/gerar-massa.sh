#!/usr/bin/env bash
# Caderno de testes (spec 05-040): gera de novo a massa de dados do caderno e grava o dump em
# ../dados/jogo-acoes-massa.sql. Só quem mantém o caderno precisa rodar; quem executa o caderno
# usa restaurar-massa. Passos, cada um escrito na tela:
#   1. apaga os volumes do Compose (banco vazio) -- pede confirmação;
#   2. sobe Postgres, LocalStack e email-service, e restaura a chave de API de teste;
#   3. sobe o app no ambiente blackbox com o relógio 30 dias no passado
#      (scripts/blackbox-clock-offset.sh);
#   4. roda o gerador Python (blackbox-tests, spec 05-018) com o mesmo deslocamento;
#   5. para o app e faz o dump do schema jogo_acoes, sem as tabelas de sessão;
#   6. confere a massa com ../dados/verificar-massa.sql.
#
# Uso: ./gerar-massa.sh
# Precisa do ambiente Python do blackbox-tests pronto (blackbox-tests/README.md); PYTHON aponta
# para outro interpretador (padrão: python3).
set -euo pipefail

DESLOCAMENTO=-30
SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CADERNO_DIR="$(cd "$SCRIPTS_DIR/.." && pwd)"
REPO="$(cd "$CADERNO_DIR/../../.." && pwd)"
DUMP="$CADERNO_DIR/dados/jogo-acoes-massa.sql"
API="${API_BASE_URL:-http://localhost:8080/api}"
PYTHON="${PYTHON:-python3}"
COMPOSE=(docker compose -f "$REPO/docker-compose.yml" -f "$REPO/docker-compose.blackbox.yml")
LOG_APP="$(mktemp)"

parar_app() {
  docker ps -q --filter label=com.docker.compose.service=app | xargs -r docker stop >/dev/null
}

echo "==> 1. Apagando os volumes do Compose para começar de um banco vazio"
read -r -p "    Isso apaga todos os dados dos bancos locais. Continuar? [s/N] " RESPOSTA
[ "$RESPOSTA" = "s" ] || { echo "    Cancelado."; exit 1; }
"${COMPOSE[@]}" down -v

echo "==> 2. Subindo Postgres, LocalStack e email-service e restaurando a chave de API de teste"
"${COMPOSE[@]}" up -d --wait db db-email-service localstack
"$REPO/scripts/test-api-key.sh" restore
"${COMPOSE[@]}" up -d --wait email-service
"$REPO/scripts/test-api-key.sh" restore

echo "==> 3. Subindo o app com o relógio $DESLOCAMENTO dias no passado (log em $LOG_APP)"
"$REPO/scripts/blackbox-clock-offset.sh" "$DESLOCAMENTO" > "$LOG_APP" 2>&1 &
for TENTATIVA in $(seq 1 120); do
  if curl -sf -o /dev/null "$API/competitions/public"; then
    echo "    app no ar"
    break
  fi
  [ "$TENTATIVA" = 120 ] && { echo "Erro: o app não subiu em 10 minutos, ver $LOG_APP" >&2; exit 1; }
  sleep 5
done

echo "==> 4. Rodando o gerador de dados (python -m seed --profile standard --clock-offset-days $DESLOCAMENTO)"
(cd "$REPO/blackbox-tests" && "$PYTHON" -m seed --profile standard --clock-offset-days "$DESLOCAMENTO")

echo "==> 5. Parando o app e gravando o dump em $DUMP"
parar_app
VERSAO=$(ls "$REPO/app/src/main/resources/db/migration-jogo-acoes" | sed -nE 's/^V([0-9]+)__.*/\1/p' | sort -n | tail -1)
{
  echo "-- Massa de dados do caderno de testes (spec 05-040), gerada por gerar-massa.sh."
  echo "-- data-geracao: $(date +%F)"
  echo "-- deslocamento-relogio-dias: $DESLOCAMENTO"
  echo "-- versao-flyway: $VERSAO"
  "${COMPOSE[@]}" exec -T db pg_dump -U jogo_acoes_admin -d jogo_acoes --schema=jogo_acoes \
    --exclude-table-data=jogo_acoes.spring_session \
    --exclude-table-data=jogo_acoes.spring_session_attributes \
    --exclude-table-data=jogo_acoes.login_session
} > "$DUMP"
echo "    dump gravado ($(wc -l < "$DUMP") linhas, migration V$VERSAO)"

echo "==> 6. Restaurando o dump e conferindo a massa"
"$SCRIPTS_DIR/restaurar-massa.sh"
