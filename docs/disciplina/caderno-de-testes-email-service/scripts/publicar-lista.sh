#!/usr/bin/env bash
# Caderno de testes do email-service: publica uma lista de domínios de e-mail temporário no S3
# do LocalStack, para o lote da spec 05-048 baixar por HTTP, sem internet. Passos, cada um
# escrito na tela:
#   1. cria o bucket disposable-lists, se ainda não existe;
#   2. copia o arquivo local para s3://disposable-lists/<nome> (substitui o que estava lá);
#   3. baixa o arquivo pelo endereço que o email-service usa no Compose
#      (http://localstack:4566/disposable-lists/<nome>) e mostra o número de linhas e as dez
#      primeiras.
#
# Uso:     ./publicar-lista.sh <arquivo> [<nome no bucket>]
# Exemplo: ./publicar-lista.sh ../listas/lista-a-v2.conf lista-a.conf
# Sem <nome no bucket>, usa o nome do arquivo. Precisa de docker (o LocalStack do Compose de pé).
set -euo pipefail

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
  echo "Uso: $0 <arquivo> [<nome no bucket>]" >&2
  exit 1
fi

ARQUIVO="$1"
NOME="${2:-$(basename "$ARQUIVO")}"
CADERNO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$CADERNO_DIR/../../.." && pwd)"
BUCKET="disposable-lists"
[ -f "$ARQUIVO" ] || { echo "Erro: $ARQUIVO não existe." >&2; exit 1; }

localstack() {
  (cd "$REPO_ROOT" && docker compose exec -T localstack "$@")
}

echo "==> 1. Bucket s3://$BUCKET"
if localstack awslocal s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1; then
  echo "    já existe"
else
  localstack awslocal s3 mb "s3://$BUCKET" | sed 's/^/    /'
fi

echo "==> 2. Copiando $ARQUIVO para s3://$BUCKET/$NOME"
localstack awslocal s3 cp - "s3://$BUCKET/$NOME" < "$ARQUIVO" | sed 's/^/    /'

echo "==> 3. Conteúdo servido em http://localstack:4566/$BUCKET/$NOME"
SERVIDO=$(localstack curl -sf "http://localhost:4566/$BUCKET/$NOME")
echo "    $(echo "$SERVIDO" | wc -l) linhas"
head -10 <<< "$SERVIDO" | sed 's/^/    | /'
