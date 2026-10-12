#!/usr/bin/env bash
# Caderno de testes (spec 05-040), roteiro 3: faz o login de um jogador como se fosse o
# dispositivo <número>, sem navegador. Passos, cada um escrito na tela:
#   1. lê os cabeçalhos Sec-CH-UA* de ../dispositivos/<número>.json;
#   2. pede o link de login (POST /login-requests);
#   3. lê o e-mail no SES do LocalStack (GET /_aws/ses) -- se ainda não chegou, tenta de novo a
#      cada 2 s, até 5 vezes;
#   4. faz o login com o token do link (GET /login-links/{token}) mandando os cabeçalhos do
#      dispositivo, e grava o cookie de sessão em ../cookie<número>.
#
# Uso:    ./login-jogador.sh <número> <email>
# Exemplo: ./login-jogador.sh 1 success+seed-c6-jogador-1@simulator.amazonses.com
# Precisa de curl e jq. API_BASE_URL e LOCALSTACK_URL mudam os endereços padrão.
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Uso: $0 <número> <email>" >&2
  exit 1
fi

NUMERO="$1"
EMAIL="$2"
CADERNO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DISPOSITIVO="$CADERNO_DIR/dispositivos/$NUMERO.json"
COOKIE="$CADERNO_DIR/cookie$NUMERO"
API="${API_BASE_URL:-http://localhost:8080/api}"
LOCALSTACK="${LOCALSTACK_URL:-http://localhost:4566}"

command -v jq >/dev/null || { echo "Erro: jq não encontrado (sudo apt install jq)." >&2; exit 1; }
[ -f "$DISPOSITIVO" ] || { echo "Erro: dispositivo $NUMERO não existe ($DISPOSITIVO)." >&2; exit 1; }

echo "==> 1. Lendo os cabeçalhos do dispositivo $NUMERO ($DISPOSITIVO)"
CABECALHOS=()
while IFS= read -r linha; do
  echo "    $linha"
  CABECALHOS+=(-H "$linha")
done < <(jq -r 'to_entries[] | "\(.key): \(.value)"' "$DISPOSITIVO")

ids_dos_emails() {
  curl -sf "$LOCALSTACK/_aws/ses" | jq -r --arg e "$EMAIL" \
    '.messages[] | select(.Destination.ToAddresses | index($e)) | .Id'
}
ANTERIORES="$(ids_dos_emails)"

echo "==> 2. Pedindo o link de login de $EMAIL (POST $API/login-requests)"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg e "$EMAIL" '{email: $e}')" "$API/login-requests")
echo "    resposta: $STATUS"
[ "$STATUS" = "202" ] || { echo "Erro: esperava 202." >&2; exit 1; }

echo "==> 3. Lendo o e-mail no LocalStack (GET $LOCALSTACK/_aws/ses)"
LINK=""
for TENTATIVA in 1 2 3 4 5; do
  LINK=$(curl -sf "$LOCALSTACK/_aws/ses" | jq -r --arg e "$EMAIL" --arg antes "$ANTERIORES" '
    [.messages[] | select(.Destination.ToAddresses | index($e))
                 | select(.Id as $id | ($antes | split("\n") | index($id)) | not)]
    | last | if . == null then empty else (.TemplateData // "{}" | fromjson | .link // empty) end')
  if [ -n "$LINK" ]; then
    echo "    tentativa $TENTATIVA: e-mail encontrado, link $LINK"
    break
  fi
  echo "    tentativa $TENTATIVA: o e-mail ainda não chegou, tentando de novo em 2 s"
  sleep 2
done
[ -n "$LINK" ] || { echo "Erro: nenhum e-mail novo para $EMAIL depois de 5 tentativas." >&2; exit 1; }
TOKEN="${LINK##*/login-links/}"
TOKEN="${TOKEN%%/*}"

echo "==> 4. Fazendo o login como dispositivo $NUMERO (GET $API/login-links/$TOKEN)"
CABECALHOS_RESPOSTA="$(mktemp)"
CORPO=$(curl -s -D "$CABECALHOS_RESPOSTA" "${CABECALHOS[@]}" "$API/login-links/$TOKEN")
STATUS=$(head -1 "$CABECALHOS_RESPOSTA" | awk '{print $2}')
SESSAO=$(grep -i '^set-cookie: SESSION=' "$CABECALHOS_RESPOSTA" | sed -E 's/^[Ss]et-[Cc]ookie: (SESSION=[^;]*).*/\1/' | tr -d '\r' | tail -1)
rm -f "$CABECALHOS_RESPOSTA"
echo "    resposta: $STATUS $CORPO"
[ "$STATUS" = "200" ] && [ -n "$SESSAO" ] || { echo "Erro: o login não abriu sessão." >&2; exit 1; }
echo "$SESSAO" > "$COOKIE"
echo "    cookie de sessão gravado em $COOKIE"
