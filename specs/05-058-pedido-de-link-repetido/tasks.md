# Tasks: pedido de link de login ou de entrada repetido

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** a spec 05-056 mesclada em `development` antes de tudo; depois contrato e testes,
falhando pelo motivo esperado; depois o código. Nenhuma task começa sem pedido explícito
(constitution, "Nunca começar a implementar sem pedido explícito").

As tarefas são acompanhadas como checklist na Issue
[#117](https://github.com/lalgarve/jogo-acoes/issues/117) (item 4), ou numa Issue própria se a
Leila preferir.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Pré-requisito: spec 05-056 (Issue #135) mesclada em `development`, com `LoginLinkRequestService`. Trazer `development` para o branch desta spec | — | | #117 |
| T002 | `docs/openapi.yaml`: descrições de `requestLoginLink`, `consumeLoginLink` e `requestOrConfirmEntry` (decisão "Contrato" do `plan.md`). Regenerar e compilar | T001 | [P] | #117 |
| T003 | `LinkServiceTest`: casos de `findActive` (mesmo `extra` acha; `extra` diferente, link usado, invalidado ou expirado não acham; e-mail com outra grafia acha; com dois candidatos, o mais recente) e de `markResent` (só muda `emailSentAt`). Não compila até T005; escrever primeiro | T001 | [P] | #117 |
| T004 | `login.feature`: substituir "Requesting a new login link invalidates the previous one" pelos dois cenários da `spec.md`; acrescentar um cenário com `returnTo` diferente (link novo, o anterior deixa de valer). `request_competition_entry.feature`: cenário "Player requests entry twice and uses the first link". Passos em `LoginSteps` e `RequestCompetitionEntrySteps` (o token de cada e-mail vem do `StubEmailSender`). Rodar e confirmar que falham hoje por o token ser diferente | T001 | [P] | #117 |
| T005 | `LinkRecordRepository` (consulta dos candidatos) e `LinkService.findActive`/`markResent`. Confirmar T003 verde | T003 | | #117 |
| T006 | `LoginLinkRequestService.requestLoginLink` e `EntryRequestService.requestEntry` com o ramo do reenvio e a auditoria `(resent)`. Confirmar T004 verde e verdes também o resto de `login.feature`, `redirect_after_login.feature`, `request_competition_entry.feature` e `AuditLoggingIntegrationTest` | T004, T005 | | #117 |
| T007 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): classes criadas (esperado: nenhuma); caminhos de erro alterados: falha de envio no reenvio desfaz `markResent` e auditoria (coberto pelo cenário de `503` da 05-056, que passa a reenviar o link do `Given`; conferir que ele continua verde e, se não cobrir o reenvio, acrescentar o caso); link invalidado pelo pedido com `returnTo` diferente responde `LoginLinkInvalidException` (T004). Registrar abaixo | T006 | | #117 |
| T008 | Rodar `mvn -pl app -am verify` com o ambiente de pé e a chave de teste restaurada (`README.md`), e a suíte `blackbox-tests/`. Se o container da sessão não rodar algum teste, registrar qual e fechar pelo CI da PR para `development` | T006 | | #117 |
| T009 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T009)" abaixo e conferir os resultados esperados | T008 | | #117 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Verificação manual (T009)

Na raiz do repositório, no WSL, com o branch da implementação. O overlay `blackbox` (padrão do
`docker-up.conf.example`) cria o administrador `success+admin@simulator.amazonses.com`, usado no
roteiro.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
./scripts/docker-up.sh
```

Funções usadas nos passos (copiar uma vez no mesmo terminal). `tokens` lista, em ordem, o token
de cada e-mail mandado a um endereço no SES do LocalStack, esperando até 40 s até haver `$2`
e-mails (o envio passa pela fila).

```bash
ADMIN=success+admin@simulator.amazonses.com
API=http://localhost:8080/api
tokens() {
  for i in $(seq 40); do
    out=$(curl -s http://localhost:4566/_aws/ses | python3 -c '
import json, re, sys
addr = sys.argv[1]
msgs = [m for m in json.load(sys.stdin)["messages"] if addr in json.dumps(m.get("Destination"))]
for m in msgs:
    print(re.findall(r"login-links/([0-9a-f-]{36})", json.dumps(m))[0])' "$1")
    [ "$(echo "$out" | grep -c .)" -ge "$2" ] && { echo "$out"; return; }
    sleep 1
  done
  echo "menos de $2 e-mail(s) para $1" >&2; echo "$out"
}
pedir_link() {  # $1 = returnTo (opcional)
  body="{\"email\":\"$ADMIN\"${1:+,\"returnTo\":\"$1\"}}"
  curl -s -o /dev/null -w '%{http_code}\n' -X POST $API/login-requests -H 'Content-Type: application/json' -d "$body"
}
entrar() {  # $1 = token
  curl -s -o /dev/null -w '%{http_code}\n' -c /tmp/sessao.cookies "$API/login-links/$1"
}
```

1. Pedir o link de login duas vezes seguidas:
   ```bash
   pedir_link; pedir_link
   tokens $ADMIN 2
   ```
   Esperado: `202` duas vezes e duas linhas iguais (o mesmo token nos dois e-mails). Antes da
   implementação: dois tokens diferentes.
2. Entrar pelo link do **primeiro** e-mail:
   ```bash
   entrar $(tokens $ADMIN 2 | head -1)
   ```
   Esperado: `200`. Antes da implementação: `400` (o primeiro link já tinha sido invalidado).
3. Depois de usado, um novo pedido gera um link novo:
   ```bash
   pedir_link
   tokens $ADMIN 3 | tail -1
   ```
   Esperado: um token diferente dos dois anteriores.
4. Um pedido com `returnTo` diferente gera um link novo e invalida o anterior:
   ```bash
   pedir_link /competitions/mine
   T_ANTERIOR=$(tokens $ADMIN 4 | sed -n 3p); T_NOVO=$(tokens $ADMIN 4 | sed -n 4p)
   [ "$T_ANTERIOR" != "$T_NOVO" ] && echo "token novo"
   entrar $T_ANTERIOR; entrar $T_NOVO
   ```
   Esperado: `token novo`, depois `400` para o link anterior e `200` para o novo.

Para derrubar: `docker compose down -v`.

## Registro

<preencher conforme as tasks forem concluídas>
