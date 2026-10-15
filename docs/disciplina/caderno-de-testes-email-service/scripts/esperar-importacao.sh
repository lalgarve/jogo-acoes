#!/usr/bin/env bash
# Caderno de testes do email-service: espera uma execução do lote de importação (spec 05-048)
# terminar. Passos, cada um escrito na tela:
#   1. consulta a execução (GET /disposable-domain-imports/{executionId}) com a chave de
#      operações -- se ainda está STARTING ou STARTED, tenta de novo a cada 2 s, até 10 vezes;
#   2. mostra a execução final (situação, linhas lidas, incluídos, removidos, ignorados, motivo).
#
# Uso:     ./esperar-importacao.sh <executionId>
# Exemplo: ./esperar-importacao.sh 1
# Precisa de curl e jq. EMAIL_SERVICE_URL muda o endereço padrão (http://localhost:8082/api).
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Uso: $0 <executionId>" >&2
  exit 1
fi

EXECUCAO="$1"
CADERNO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
URL="${EMAIL_SERVICE_URL:-http://localhost:8082/api}/disposable-domain-imports/$EXECUCAO"
[ -f "$CADERNO_DIR/chaves.env" ] || { echo "Erro: rodar gerar-chaves.sh antes." >&2; exit 1; }
# shellcheck disable=SC1091
. "$CADERNO_DIR/chaves.env"

echo "==> 1. Consultando a execução $EXECUCAO (GET $URL)"
for TENTATIVA in 1 2 3 4 5 6 7 8 9 10; do
  RESPOSTA=$(curl -sf -H "X-API-Key: $CHAVE_OPERACOES" "$URL")
  STATUS=$(echo "$RESPOSTA" | jq -r .status)
  echo "    tentativa $TENTATIVA: $STATUS"
  case "$STATUS" in
    STARTING|STARTED) sleep 2 ;;
    *) break ;;
  esac
done

echo "==> 2. Execução $EXECUCAO"
echo "$RESPOSTA" | jq .
