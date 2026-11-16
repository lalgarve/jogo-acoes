# Tasks: convite e entrada em competição sem participação duplicada

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** contrato e testes primeiro, falhando pelo motivo esperado; depois a migration e o
código. Nenhuma task começa sem pedido explícito (constitution, "Nunca começar a implementar sem
pedido explícito").

As tarefas são acompanhadas como checklist na Issue
[#117](https://github.com/lalgarve/jogo-acoes/issues/117) (item 4), ou numa Issue própria se a
Leila preferir.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `docs/openapi.yaml`: resposta `400` (`Error`) em `invitePlayers`, descrevendo e-mail já na competição e e-mail repetido na lista (pedido inteiro recusado); em `createCompetition`, citar o e-mail repetido na descrição do `400` existente. Regenerar a API (`mvn -pl app generate-sources`) e confirmar que compila | — | [P] | #117 |
| T002 | Cenários de convite em `manage_competition_players.feature` (texto na `spec.md`): "Administrator invites an e-mail that is already in the competition" e "Administrator invites a list that repeats an e-mail". Passos novos em `ManageCompetitionPlayersSteps` (o e-mail já presente é o mesmo com outra grafia de maiúsculas, para cobrir a comparação). Rodar e confirmar que falham hoje (segunda participação criada) | T001 | | #117 |
| T003 | Cenário em `create_competition.feature`: criar competição privada com o mesmo e-mail duas vezes na lista dá erro e não cria a competição. Passos em `CreateCompetitionSteps`. Rodar e confirmar que falha hoje | — | [P] | #117 |
| T004 | Cenário "Registered player confirms entry into a competition they already joined" em `request_competition_entry.feature` (texto na `spec.md`). Passos em `RequestCompetitionEntrySteps`: guardar `joinedAt` e as entradas `PARTICIPATION_STATUS_CHANGED` da participação antes; conferir as duas depois. Rodar e confirmar que falha hoje | — | [P] | #117 |
| T005 | Testes de concorrência (decisão "Teste de concorrência" do `plan.md`): em `EntryRequestServiceIntegrationTest`, dois `requestEntry` simultâneos (um com o e-mail em maiúsculas) resultam numa participação só e nenhuma exceção; em `PlayerManagementServiceIntegrationTest`, dois `invitePlayers` simultâneos com o mesmo e-mail resultam num sucesso e num `PlayerValidationException`. Mais um teste em `EntryRequestServiceIntegrationTest`: jogador `IN_COMPETITION` pede entrada de novo, recebe o link e a participação continua única e `IN_COMPETITION`. Rodar e confirmar que falham hoje | — | [P] | #117 |
| T006 | Migration `V10__unique_participation_email_per_competition.sql` (índice único em `(competition_id, lower(email))`) e `ParticipationRepository`: `findByCompetition_IdAndEmailIgnoreCase`, `findEmailsAlreadyIn`, `insertIfAbsent`; remover `findByCompetition_IdAndEmailAndStatusNot`. Confirmar que `SchemaLayoutTest` continua verde | T002–T005 | | #117 |
| T007 | Código: `CompetitionService.validate` recusa e-mail repetido; `PlayerManagementService.invitePlayers` confere repetição e e-mails já presentes antes do laço e traduz a violação do índice; `EntryRequestService.requestEntry` usa a participação em qualquer status e não rebaixa `IN_COMPETITION`; `confirmEntry` não regrava participação já `IN_COMPETITION`; os dois usam `insertIfAbsent` para criar. Confirmar T002–T005 verdes e os cenários existentes de `manage_competition_players.feature`, `create_competition.feature`, `request_competition_entry.feature` e `login.feature` verdes | T006 | | #117 |
| T008 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): classes de exceção criadas (esperado: nenhuma); caminhos de erro novos: `PlayerValidationException` por e-mail já presente (T002), por lista repetida (T002) e pela violação do índice em convite simultâneo (T005); `CompetitionValidationException` por lista repetida (T003). Registrar abaixo | T007 | | #117 |
| T009 | Rodar `mvn -pl app -am verify` com o ambiente de pé e a chave de teste restaurada (`README.md`), e a suíte `blackbox-tests/` (`./scripts/cloud-blackbox-tests.sh` na nuvem). Corrigir testes ou dados de teste que convidem o mesmo e-mail duas vezes (risco do `plan.md`). Se o container da sessão não rodar algum teste, registrar qual e fechar pelo CI da PR para `development` | T007 | | #117 |
| T010 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T010)" abaixo e conferir os resultados esperados | T009 | | #117 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Verificação manual (T010)

Na raiz do repositório, no WSL, com o branch da implementação. O `down -v` começa de um banco
vazio, para que as contagens saiam exatas. O `docker-up.sh` sobe tudo na ordem certa, com o
overlay `blackbox` (captcha sempre aceito, administrador `success+admin@simulator.amazonses.com`
criado) e `--build`, pelos padrões de `docker-up.conf.example`.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
./scripts/docker-up.sh
```

Funções usadas nos passos (copiar uma vez no mesmo terminal). `ultimo_token` lê o último e-mail
mandado a um endereço no SES do LocalStack, esperando até 40 s (o envio passa pela fila).

```bash
ADMIN=success+admin@simulator.amazonses.com
API=http://localhost:8080/api
ultimo_token() {
  for i in $(seq 40); do
    t=$(curl -s http://localhost:4566/_aws/ses | python3 -c '
import json, re, sys
addr = sys.argv[1]
msgs = [m for m in json.load(sys.stdin)["messages"] if addr in json.dumps(m.get("Destination"))]
found = re.findall(r"login-links/([0-9a-f-]{36})", json.dumps(msgs[-1])) if msgs else []
print(found[0] if found else "")' "$1")
    [ -n "$t" ] && { echo "$t"; return; }
    sleep 1
  done
  echo "nenhum e-mail para $1" >&2
}
entrar_admin() {
  curl -s -o /dev/null -X POST $API/login-requests -H 'Content-Type: application/json' -d "{\"email\":\"$ADMIN\"}"
  sleep 2
  curl -s -o /dev/null -c /tmp/admin.cookies "$API/login-links/$(ultimo_token $ADMIN)"
}
participacoes() {
  docker compose exec -T db psql -U jogo_acoes_admin -d jogo_acoes -tAc \
    "SELECT count(*) || ' participação(ões) de ' || lower('$2') FROM jogo_acoes.participation WHERE competition_id = $1 AND lower(email) = lower('$2')"
}
criar_competicao() {  # $1 = PUBLIC ou PRIVATE, $2 = lista JSON de e-mails
  curl -s -b /tmp/admin.cookies -X POST $API/competitions -H 'Content-Type: application/json' \
    -d "{\"name\":\"Verificação 05-057 $RANDOM\",\"type\":\"$1\",\"startDate\":\"$(date -d tomorrow +%F)\",\"durationDays\":5,\"buyFee\":0,\"sellFee\":0,\"emails\":$2}" \
    -w ' [%{http_code}]\n'
}
```

1. Entrar como administrador e criar uma competição privada com um convidado:
   ```bash
   entrar_admin
   criar_competicao PRIVATE '["ana+0557@simulator.amazonses.com"]'
   ```
   Esperado: corpo JSON da competição criada e ` [201]`. Guardar o `id`:
   ```bash
   PRIV=<id do passo 1>
   participacoes $PRIV ana+0557@simulator.amazonses.com
   ```
   Esperado: `1 participação(ões) de ana+0557@simulator.amazonses.com`.
2. Convidar o mesmo e-mail de novo, com outra grafia:
   ```bash
   curl -s -b /tmp/admin.cookies -X POST $API/competitions/$PRIV/players -H 'Content-Type: application/json' \
     -d '{"emails":["bia+0557@simulator.amazonses.com","ANA+0557@simulator.amazonses.com"]}' -w ' [%{http_code}]\n'
   participacoes $PRIV ana+0557@simulator.amazonses.com
   participacoes $PRIV bia+0557@simulator.amazonses.com
   ```
   Esperado:
   ```
   {"message":"E-mail(s) already in this competition: ana+0557@simulator.amazonses.com"} [400]
   1 participação(ões) de ana+0557@simulator.amazonses.com
   0 participação(ões) de bia+0557@simulator.amazonses.com
   ```
   Antes da implementação: ` [202]`, 2 participações da Ana e 1 da Bia.
3. Lista com e-mail repetido, no convite e na criação:
   ```bash
   curl -s -b /tmp/admin.cookies -X POST $API/competitions/$PRIV/players -H 'Content-Type: application/json' \
     -d '{"emails":["caio+0557@simulator.amazonses.com","Caio+0557@simulator.amazonses.com"]}' -w ' [%{http_code}]\n'
   criar_competicao PRIVATE '["davi+0557@simulator.amazonses.com","davi+0557@simulator.amazonses.com"]'
   ```
   Esperado: duas respostas ` [400]`, com `Repeated e-mail(s) in the list: ...` no corpo.
4. Pedido de entrada simultâneo numa competição pública (o `&` dispara os dois juntos):
   ```bash
   criar_competicao PUBLIC '[]'
   PUB=<id do passo 4>
   for e in eva+0557@simulator.amazonses.com EVA+0557@simulator.amazonses.com; do
     curl -s -o /dev/null -w '%{http_code}\n' -X POST $API/competitions/$PUB/entry-requests \
       -H 'Content-Type: application/json' -d "{\"email\":\"$e\",\"captchaToken\":\"\"}" &
   done; wait
   participacoes $PUB eva+0557@simulator.amazonses.com
   ```
   Esperado: `202` duas vezes e `1 participação(ões) de eva+0557@simulator.amazonses.com`.
5. Confirmação de entrada repetida pelo administrador, já logado:
   ```bash
   for i in 1 2; do
     curl -s -b /tmp/admin.cookies -X POST $API/competitions/$PUB/entry-requests -H 'Content-Type: application/json' -d '{}' ; echo
   done
   docker compose exec -T db psql -U jogo_acoes_admin -d jogo_acoes -tAc \
     "SELECT count(*) || ' mudança(s) de status' FROM jogo_acoes.log l JOIN jogo_acoes.participation p ON p.id = l.related_object_id WHERE l.log_type = 'PARTICIPATION_STATUS_CHANGED' AND p.competition_id = $PUB AND lower(p.email) = lower('$ADMIN')"
   ```
   Esperado: as duas respostas com a mesma participação (mesmo `id` e mesma data de entrada) e
   `1 mudança(s) de status`. Antes da implementação: `2 mudança(s) de status`.

Para derrubar: `docker compose down -v`.

## Registro

<preencher conforme as tasks forem concluídas>
