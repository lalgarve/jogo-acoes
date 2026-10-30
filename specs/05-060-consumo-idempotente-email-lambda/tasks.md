# Tasks: e-mail entregue duas vezes pela fila é mandado uma vez só

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** contratos e infraestrutura local primeiro; depois os testes do `email-lambda`, falhando
pelo motivo esperado; depois o código da Lambda e do `email-service`. Pode ser feita antes ou
depois da 05-059: as duas não mexem no mesmo código. Nenhuma task começa sem pedido explícito
(constitution, "Nunca começar a implementar sem pedido explícito").

As tarefas são acompanhadas como checklist na Issue
[#205](https://github.com/lalgarve/jogo-acoes/issues/205), junto com a spec 05-059.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Contratos: `contracts/queue-message.md` desta spec (campos da mensagem com `attempt` e `schemaVersion` 2, quem gera cada um); `SEND_IN_DOUBT` em `specs/05-046-publicacao-na-fila-em-segundo-plano/contracts/dlq-message.md` | — | [P] | #205 |
| T002 | Infraestrutura local: `dynamodb` em `SERVICES` (`docker-compose.yml`, `scripts/cloud-up.sh`); `docker/localstack/init/04-create-email-sends-table.sh` (tabela `jogo-acoes-email-sends`, chave `id`, TTL em `expiresAt`); variáveis do DynamoDB e `EMAIL_IDEMPOTENCY_TABLE` na função e `--function-response-types ReportBatchItemFailures` no mapeamento (`03-deploy-email-lambda.sh`). Subir o ambiente e conferir com `awslocal dynamodb describe-table` e `awslocal lambda list-event-source-mappings` | — | [P] | #205 |
| T003 | `email-lambda`: dependência `quarkus-amazon-dynamodb`, configuração em `application.properties` (principal e de teste) e campo `attempt` em `EmailMessage`, sem lógica nova. A suíte cria e apaga a tabela de teste `email-lambda-test-sends`. Confirmar `EmailSendHandlerTest` verde como está (com `attempt` nas mensagens de teste) | T002 | | #205 |
| T004 | `EmailSendHandlerTest`: casos da decisão "Testes" do `plan.md`. Rodar e confirmar que falham hoje (dois envios no SES para a mesma mensagem; lote com registro malformado falha inteiro). No caso do lote, conferir também que o LocalStack respeita `ReportBatchItemFailures` (risco do `plan.md`); se não respeitar, parar e registrar | T003 | | #205 |
| T005 | `SendRecordStore` e `EmailSendHandler` com `SQSBatchResponse`, os três estados e `SEND_IN_DOUBT`. Confirmar T004 verde | T004 | | #205 |
| T006 | `email-service`: `attempt` em `EmailQueueMessage` (`SCHEMA_VERSION` `"2"`) e em `EmailQueuePublisher.messageBody`; `SEND_IN_DOUBT` em `DeliveryErrorType`; `email.publisher.in-doubt-delay` (padrão `15m`) em `RetryPolicy`; `DeadLetterConsumer` descarta `attempt` antigo. Testes em `DeadLetterConsumerIntegrationTest` (espera de `SEND_IN_DOUBT` com o valor configurado no teste; `attempt` antigo descartado) e conferência do `attempt` publicado no teste do publicador. Escrever os testes antes e confirmar que falham | T001 | [P] | #205 |
| T007 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): classes criadas (esperado: nenhuma); caminhos de erro novos ou alterados no `EmailSendHandler` (falha do `PutItem`, falha do `UpdateItem` depois do SES, `DeleteItem` antes de reportar falha, mensagem sem `attempt`, cada erro classificado marcando `FAILED`) e no `email-service` (`SEND_IN_DOUBT`, `attempt` antigo); para cada um, o teste de T004/T006 que o exercita. O caso "o `DeleteItem` também falha" é difícil de provocar com o LocalStack real: justificar aqui se ficar sem teste e avisar ao abrir a PR. Registrar abaixo | T005, T006 | | #205 |
| T008 | Rodar as suítes do `email-lambda`, do `email-service` e do `app` (`mvn -pl email-lambda,email-service,app -am verify`) com o ambiente de pé e a chave de teste restaurada (`README.md`), e a suíte `blackbox-tests/` (o envio normal passa pela tabela). Se o container da sessão não rodar algum teste, registrar qual e fechar pelo CI da PR para `development` | T005, T006 | | #205 |
| T009 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T009)" abaixo e conferir os resultados esperados | T008 | | #205 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Verificação manual (T009)

Na raiz do repositório, no WSL, com o branch da implementação. `BUILD=true` (padrão do
`docker-up.conf.example`) reconstrói o `function.zip` da Lambda.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
./scripts/docker-up.sh
```

Variáveis e funções usadas nos passos (copiar uma vez no mesmo terminal).

```bash
ADMIN=success+admin@simulator.amazonses.com
FILA=http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/jogo-acoes-email-commands
aws_ls() { docker compose exec -T localstack awslocal "$@"; }
sql_es() { docker compose exec -T db-email-service psql -U email_service_admin -d email_service -tAc "$1"; }
emails_ses() {
  curl -s http://localhost:4566/_aws/ses | python3 -c '
import json, sys
print(sum(1 for m in json.load(sys.stdin)["messages"] if sys.argv[1] in json.dumps(m.get("Destination"))), "e-mail(s) no SES")' "$ADMIN"
}
registro() {  # $1 = correlationId, $2 = attempt
  aws_ls dynamodb get-item --table-name jogo-acoes-email-sends --consistent-read \
    --key "{\"id\":{\"S\":\"$1#$2\"}}" --query 'Item.state.S' --output text
}
mensagem() {  # corpo da mensagem do envio $1, tentativa $2, montado a partir da linha do envio
  sql_es "SELECT json_build_object('schemaVersion','2','correlationId',id::text,'attempt',$2,
          'senderAddress',sender_address,'recipientEmail',recipient_email,
          'templateName',ses_template_name,'templateData','{}'::json)
          FROM email_service.email_send WHERE id = '$1'"
}
```

1. Um envio normal grava o registro como `SENT`:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/login-requests \
     -H 'Content-Type: application/json' -d "{\"email\":\"$ADMIN\"}"
   sleep 30
   ENVIO=$(sql_es "SELECT id FROM email_service.email_send WHERE recipient_email = '$ADMIN' ORDER BY created_at DESC LIMIT 1")
   registro $ENVIO 1; emails_ses
   ```
   Esperado: `202`, `SENT` e `1 e-mail(s) no SES`.
2. A mesma mensagem entregue de novo é descartada:
   ```bash
   aws_ls sqs send-message --queue-url $FILA --message-body "$(mensagem $ENVIO 1)" > /dev/null
   sleep 15; emails_ses; registro $ENVIO 1
   ```
   Esperado: ainda `1 e-mail(s) no SES` e `SENT`. Antes da implementação: `2 e-mail(s) no SES`.
3. Uma tentativa que ficou `IN_PROGRESS` vira `SEND_IN_DOUBT` e volta para o `email-service`:
   ```bash
   aws_ls dynamodb put-item --table-name jogo-acoes-email-sends \
     --item "{\"id\":{\"S\":\"$ENVIO#1\"},\"state\":{\"S\":\"IN_PROGRESS\"},\"expiresAt\":{\"N\":\"$(( $(date +%s) + 432000 ))\"}}"
   aws_ls sqs send-message --queue-url $FILA --message-body "$(mensagem $ENVIO 1)" > /dev/null
   sleep 20; emails_ses
   sql_es "SELECT status, last_error_type, attempts, round(extract(epoch FROM next_attempt_at - now()) / 60) || ' min'
           FROM email_service.email_send WHERE id = '$ENVIO'"
   ```
   Esperado: ainda `1 e-mail(s) no SES` e a linha `PENDING|SEND_IN_DOUBT|1|15 min` (ou `14 min`,
   conforme o tempo que passou). O envio sai de novo, com `attempt` 2, depois desses 15 minutos.

Para derrubar: `docker compose down -v`.

## Registro

<preencher conforme as tasks forem concluídas>
