# Tasks: Serviço de E-mail — envio de e-mail

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Os testes de verificação
vêm primeiro: cada um é escrito e visto falhando antes do código que o faz passar.

O contrato (`docs/openapi-email-service.yaml`, `POST /emails`) já está escrito, junto com esta
spec. Todas as decisões de `plan.md` estão resolvidas
(2026-10-05); a implementação começa quando for pedida explicitamente.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Escrever `email-service/src/test/resources/features/send_email.feature`: envio sem remetente definido (`409`, nada na fila), envio aceito (`202`, mensagem com template na fila com o `correlationId` = `id` devolvido e o `senderAddress` do cliente), template inexistente e template de outro cliente (`404`), pedido malformado (`400`), sem API-KEY (`401`), falha ao publicar (`503`, nada gravado) | — | [P] | |
| T002 | Step definitions de T001 (`SendEmailSteps.java`), via RestAssured contra o servidor real; a mensagem é lida da fila real do LocalStack. Rodar e ver todos os cenários falharem pelo motivo certo (rota inexistente), não por erro de configuração | T001 | | |
| T003 | Teste do `email-lambda`: mensagem com template vira `SendTemplatedEmail` com `Source` = `senderAddress`, `Template`, `TemplateData` e a tag `correlationId`; mensagem sem `templateName`/`senderAddress` é rejeitada como malformada. Contra o LocalStack, como os testes atuais do módulo. Ver falhar | — | [P] | |
| T004 | `email-service/pom.xml`: `spring-cloud-aws-starter-sqs`; `email.queue-name` em `application.yml` e endpoint SQS do LocalStack em `application-docker.yml`/`application-sandbox.yml` | — | [P] | |
| T005 | Migrations `create_client_sender_table` e `create_email_send_table` no schema do `email-service`, em `db/migration-email-service/` (Issue #111; numeração seguindo a última migration existente), + entidades `ClientSender`/`EmailSend` e repositórios | — | [P] | |
| T006 | `scripts/set-email-sender.sh <cliente> <endereço>`: valida o formato e grava/troca `client_sender`; chamado no restore dos dados de teste de `docker`/`sandbox` com o remetente já verificado no LocalStack | T005 | | |
| T007 | `EmailQueueMessage`, `EmailSendService` (busca template e remetente do cliente, grava `email_send`, publica; rollback e `EmailQueuePublishException` se a publicação falhar) e `EmailSendController` implementando `EmailsApi`; `ApiExceptionHandler` mapeia validação → `400`, remetente ausente → `409` e falha de fila → `503` | T004, T005 | | |
| T008 | `email-lambda`: `EmailMessage` troca `subject`/`body` por `senderAddress`/`templateName`/`templateData`; `EmailSendHandler` usa `SendTemplatedEmail`; remover `email.sender-address` | T003 | | |
| T009 | Rodar T002 e T003 até verde (`mvn -pl email-service,email-lambda -am verify` com `db-email-service` e `localstack` ativos) | T002, T006, T007, T008 | | |
| T010 | `docker-compose.yml`: `email-service` com `EMAIL_QUEUE_NAME` e endpoint SQS do LocalStack; `docker compose config -q` | T004 | [P] | |
| T011 | `README.md`: como operações define o remetente e exemplo de `curl` para `POST /emails`; atualizar `docs/context/iteracao-5.md` (3.2: SESv1 e mensagem só com template decididos; anti-bounce e idempotência na Etapa 4) | T009 | | |
| T012 | Rodar a suíte completa de `app` — confirmar verde; esta spec não toca `app/src` | T009 | [P] | |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
