# Tasks: chave de idempotência no pedido de envio de e-mail

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** contrato primeiro, depois os testes do `email-service` falhando, o servidor, e por
último o `app`. Nenhuma task começa sem pedido explícito (constitution, "Nunca começar a
implementar sem pedido explícito").

As tarefas são acompanhadas como checklist na Issue
[#205](https://github.com/lalgarve/jogo-acoes/issues/205), junto com a spec 05-060.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `docs/openapi-email-service.yaml`: header `Idempotency-Key` obrigatório (1 a 255 caracteres) em `POST /emails`; descrição da repetição (mesma chave e corpo → mesmo `202`; corpo diferente → `422`; sem header → `400`). Regenerar os dois lados (`mvn -pl email-service,app -am generate-sources`) e ajustar o que deixar de compilar só com a assinatura nova (`EmailSendController`, `EmailServiceGateway`) passando a chave adiante, sem lógica ainda | — | | #205 |
| T002 | `EmailSendIdempotencyIntegrationTest` (pacote `send` do `email-service`), com os casos da decisão "Testes do `email-service`" do `plan.md`. Rodar e confirmar que falham hoje (segunda linha gravada, `400` sem corpo `Error`) | T001 | [P] | #205 |
| T003 | `EmailServiceClientIntegrationTest` (`app`): duas chamadas com a mesma chave devolvem o mesmo `id`, contra o `email-service` real. Rodar e confirmar que falha hoje | T001 | [P] | #205 |
| T004 | Migration `V8__add_idempotency_key_to_email_send.sql` e campos em `EmailSend`; `EmailSendRepository.findByClientIdAndIdempotencyKey` | T002 | | #205 |
| T005 | `EmailSendService.send(clientId, key, request)`: hash do pedido, checagem da chave antes das outras, gravação num `TransactionTemplate` com `saveAndFlush` e tradução da violação do índice. `IdempotencyKeyReusedException` e tratadores de `422`, header faltando e header inválido no `ApiExceptionHandler`. Confirmar T002 verde e a suíte do `email-service` verde | T004 | | #205 |
| T006 | `app`: `EmailServiceEmailSender` gera a chave por e-mail; `EmailServiceGateway.sendEmail` recebe a chave e passa a usar `retrying`; Javadoc e comentário do `422` atualizados. Confirmar T003 verde | T005 | | #205 |
| T007 | Documentação: tabela "Operações usadas" de `specs/05-034-fronteira-sincrona-app-email-service/contracts/feign-client.md`; `README.md` (exemplo `curl` com o header, parágrafo sobre repetir o pedido) | T006 | [P] | #205 |
| T008 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): classe criada `IdempotencyKeyReusedException` (lançada em T002, corpo diferente); caminhos novos: tradução da violação do índice (T002, pedidos simultâneos), `MissingRequestHeaderException` e `HandlerMethodValidationException` → `400` (T002), `retrying` em `sendEmail` (coberto pelo mecanismo comum; se não houver teste de `retrying` com `sendEmail`, acrescentar um em `EmailServiceClientIntegrationTest` com o `email-service` parado). Registrar abaixo | T006 | | #205 |
| T009 | Rodar as suítes do `email-service` e do `app` (`mvn -pl email-service,app -am verify`) com o ambiente de pé e a chave de teste restaurada (`README.md`), e a suíte `blackbox-tests/`. Se o container da sessão não rodar algum teste, registrar qual e fechar pelo CI da PR para `development` | T006 | | #205 |
| T010 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T010)" abaixo e conferir os resultados esperados | T009 | | #205 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Verificação manual (T010)

Na raiz do repositório, no WSL, com o branch da implementação. O `down -v` é obrigatório aqui: a
migration nova cria colunas `NOT NULL` e falha num banco com envios antigos.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
./scripts/docker-up.sh
```

Variáveis e funções usadas nos passos (copiar uma vez no mesmo terminal). A chave de API é a de
teste restaurada pelo `docker-up.sh` (`README.md`).

```bash
KEY_API=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU
ES=http://localhost:8082/api
PARA=success+0559@simulator.amazonses.com
enviar() {  # $1 = Idempotency-Key (vazio: sem header), $2 = nome no templateData
  curl -s -w ' [%{http_code}]\n' -X POST $ES/emails -H "X-API-Key: $KEY_API" -H 'Content-Type: application/json' \
    ${1:+-H "Idempotency-Key: $1"} \
    -d "{\"templateName\":\"welcome\",\"recipientEmail\":\"$PARA\",\"templateData\":{\"name\":\"$2\"}}"
}
linhas() {
  docker compose exec -T db-email-service psql -U email_service_admin -d email_service -tAc \
    "SELECT count(*) || ' envio(s) com a chave $1' FROM email_service.email_send WHERE idempotency_key = '$1'"
}
emails_ses() {
  curl -s http://localhost:4566/_aws/ses | python3 -c '
import json, sys
print(sum(1 for m in json.load(sys.stdin)["messages"] if sys.argv[1] in json.dumps(m.get("Destination"))), "e-mail(s) no SES")' "$PARA"
}
```

1. Cadastrar o template do exemplo do `README.md`:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' -X POST $ES/templates -H "X-API-Key: $KEY_API" -H 'Content-Type: application/json' \
     -d '{"name":"welcome","subject":"Bem-vindo!","body":"Olá {{name}}, bem-vindo!"}'
   ```
   Esperado: `201`.
2. O mesmo pedido duas vezes, com a mesma chave:
   ```bash
   enviar chave-0559-a Ada; enviar chave-0559-a Ada
   linhas chave-0559-a
   sleep 30; emails_ses
   ```
   Esperado: duas respostas ` [202]` com o mesmo `id`, `1 envio(s) com a chave chave-0559-a` e
   `1 e-mail(s) no SES`.
3. A mesma chave com outro corpo:
   ```bash
   enviar chave-0559-a Bia
   ```
   Esperado: `{"message":"Idempotency-Key already used with a different request"} [422]`.
4. Sem o header:
   ```bash
   enviar "" Ada
   ```
   Esperado: ` [400]` com um corpo `{"message": ...}` que cita o header `Idempotency-Key`.
5. O `app` continua mandando e-mail (agora com a chave):
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/login-requests \
     -H 'Content-Type: application/json' -d '{"email":"success+admin@simulator.amazonses.com"}'
   docker compose exec -T db-email-service psql -U email_service_admin -d email_service -tAc \
     "SELECT count(*) || ' envio(s) do app com chave' FROM email_service.email_send WHERE recipient_email = 'success+admin@simulator.amazonses.com' AND idempotency_key IS NOT NULL"
   ```
   Esperado: `202` e `1 envio(s) do app com chave`.

Para derrubar: `docker compose down -v`.

## Registro

<preencher conforme as tasks forem concluídas>
