#!/usr/bin/env bash
# Caderno de testes do email-service: chama uma rota do email-service com uma das chaves do
# caderno. O corpo JSON de POST/PUT, quando há, vem da entrada padrão (o bloco <<'JSON' ... JSON
# do caderno); um POST sem corpo, fora do terminal, recebe < /dev/null.
# Passos, cada um escrito na tela (na saída de erro):
#   1. escolhe a chave pelo nome (lida de ../chaves.env, gravado por gerar-chaves.sh);
#   2. mostra a operação, o endereço e o corpo enviado;
#   3. mostra o status HTTP da resposta.
# A saída padrão leva só o corpo da resposta (formatado pelo jq quando é JSON), para que o
# caderno possa guardar um campo dela numa variável:
#   SOURCE_ID=$(chamar.sh operacoes POST /disposable-domain-sources <<'JSON' | jq -r .id
#
# Chaves:  envio       caderno-envio, só email:send
#          operacoes   caderno-operacoes, só disposable-email:admin
#          jogo-acoes  chave de teste do jogo-acoes (anterior à 1.1.0: email:send)
#          invalida    uma chave no formato certo que não existe
#          sem-chave   sem o cabeçalho X-API-Key
#
# Uso:     ./chamar.sh <chave> <MÉTODO> <caminho>
# Exemplo: ./chamar.sh envio GET /templates
# Precisa de curl e jq. EMAIL_SERVICE_URL muda o endereço padrão (http://localhost:8082/api).
set -euo pipefail

if [ $# -ne 3 ]; then
  echo "Uso: $0 <envio|operacoes|jogo-acoes|invalida|sem-chave> <MÉTODO> <caminho>" >&2
  exit 1
fi

NOME_CHAVE="$1"
METODO="$2"
CAMINHO="$3"
CADERNO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHAVES="$CADERNO_DIR/chaves.env"
URL="${EMAIL_SERVICE_URL:-http://localhost:8082/api}$CAMINHO"

command -v jq >/dev/null || { echo "Erro: jq não encontrado (sudo apt install jq)." >&2; exit 1; }

chave_do_arquivo() {
  [ -f "$CHAVES" ] || { echo "Erro: $CHAVES não existe; rodar gerar-chaves.sh antes." >&2; exit 1; }
  # shellcheck disable=SC1090
  . "$CHAVES"
  eval "echo \"\${$1:-}\""
}

case "$NOME_CHAVE" in
  envio)      CHAVE=$(chave_do_arquivo CHAVE_ENVIO);      DESCRICAO="caderno-envio (email:send)" ;;
  operacoes)  CHAVE=$(chave_do_arquivo CHAVE_OPERACOES);  DESCRICAO="caderno-operacoes (disposable-email:admin)" ;;
  jogo-acoes) CHAVE=$(chave_do_arquivo CHAVE_JOGO_ACOES); DESCRICAO="jogo-acoes (permissões padrão: email:send)" ;;
  invalida)   CHAVE="dak_ChaveQueNaoExisteNoBancoXXXXXXXXXXXXXXXXXXX"; DESCRICAO="chave que não existe" ;;
  sem-chave)  CHAVE="";                                   DESCRICAO="sem X-API-Key" ;;
  *) echo "Erro: chave desconhecida '$NOME_CHAVE'." >&2; exit 1 ;;
esac

CORPO_SAIDA=$(mktemp)
trap 'rm -f "$CORPO_SAIDA"' EXIT
ARGS=(-s -X "$METODO" -o "$CORPO_SAIDA" -w '%{http_code}')
[ -n "$CHAVE" ] && ARGS+=(-H "X-API-Key: $CHAVE")

echo "==> $METODO $URL" >&2
echo "    chave: $DESCRICAO" >&2
if [ ! -t 0 ] && [[ "$METODO" =~ ^(POST|PUT|PATCH)$ ]]; then
  CORPO=$(cat)
  if [ -n "$CORPO" ]; then
    echo "    corpo: $(echo "$CORPO" | jq -c . 2>/dev/null || echo "$CORPO")" >&2
    ARGS+=(-H 'Content-Type: application/json' --data-binary "$CORPO")
  fi
fi

STATUS=$(curl "${ARGS[@]}" "$URL")
echo "<== $STATUS" >&2
if [ -s "$CORPO_SAIDA" ]; then
  jq . "$CORPO_SAIDA" 2>/dev/null || { cat "$CORPO_SAIDA"; echo; }
fi
