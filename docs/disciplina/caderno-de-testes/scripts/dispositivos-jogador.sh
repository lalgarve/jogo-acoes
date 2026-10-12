#!/usr/bin/env bash
# Caderno de testes (spec 05-040), roteiro 3: lista os dispositivos em que o jogador está
# logado (GET /sessions), usando o cookie que login-jogador gravou para o dispositivo <número>.
#
# Uso:    ./dispositivos-jogador.sh <número>
# Precisa de curl e jq. API_BASE_URL muda o endereço padrão.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Uso: $0 <número>" >&2
  exit 1
fi

NUMERO="$1"
CADERNO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COOKIE="$CADERNO_DIR/cookie$NUMERO"
API="${API_BASE_URL:-http://localhost:8080/api}"

[ -f "$COOKIE" ] || { echo "Erro: $COOKIE não existe -- rode antes login-jogador $NUMERO <email>." >&2; exit 1; }

echo "==> Listando os dispositivos com o cookie do dispositivo $NUMERO (GET $API/sessions)"
RESPOSTA="$(mktemp)"
STATUS=$(curl -s -o "$RESPOSTA" -w '%{http_code}' -H "Cookie: $(cat "$COOKIE")" "$API/sessions")
echo "    resposta: $STATUS"
if [ "$STATUS" != "200" ]; then
  cat "$RESPOSTA"; echo
  rm -f "$RESPOSTA"
  echo "    a sessão deste dispositivo não está mais ativa"
  exit 1
fi
echo "    logado em $(jq length "$RESPOSTA") dispositivo(s):"
jq -r '.[] | "    - sessão \(.id): \(.deviceLabel), desde \(.createdAt)\(if .current then " (este)" else "" end)"' "$RESPOSTA"
rm -f "$RESPOSTA"
