# Tasks: Serviço de E-mail — envio de e-mail

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Os testes de verificação
vêm primeiro: cada um é escrito e visto falhando antes do código que o faz passar.

O contrato (`docs/openapi-email-service.yaml`, `POST /emails`) já está escrito, junto com esta
spec. As linhas abaixo esperam as decisões "proposta — confirmar" de `plan.md`.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Escrever `email-service/src/test/resources/features/send_email.feature`: envio aceito (`202`, mensagem `schemaVersion "2"` na fila com o `correlationId` = `id` devolvido), template inexistente e template de outro cliente (`404`), pedido malformado (`400`), sem API-KEY (`401`), falha ao publicar (`503`, nada gravado) | — | [P] | |
| T002 | Step definitions de T001 (`SendEmailSteps.java`), via RestAssured contra o servidor real; a mensagem é lida da fila real do LocalStack. Rodar e ver todos os cenários falharem pelo motivo certo (rota inexistente), não por erro de configuração | T001 | | |
| T003 | Teste do `email-lambda`: mensagem `schemaVersion "2"` vira `SendTemplatedEmail` com `Template`, `TemplateData` e a tag `correlationId`; mensagem `"1"` continua como hoje; `schemaVersion` desconhecido é rejeitado. Contra o LocalStack, como os testes atuais do módulo. Ver falhar | — | [P] | |
| T004 | `email-service/pom.xml`: `spring-cloud-aws-starter-sqs`; `email.queue-name` em `application.yml` e endpoint SQS do LocalStack em `application-docker.yml`/`application-sandbox.yml` | — | [P] | |
| T005 | Migration `V2__create_email_send_table.sql` + entidade `EmailSend` + `EmailSendRepository` | — | [P] | |
| T006 | `EmailQueueMessage`, `EmailSendService` (busca template do cliente, grava `email_send`, publica; rollback e `EmailQueuePublishException` se a publicação falhar) e `EmailSendController` implementando `EmailsApi`; `ApiExceptionHandler` mapeia validação → `400` e falha de fila → `503` | T004, T005 | | |
| T007 | `email-lambda`: `EmailMessage` com `templateName`/`templateData`; `EmailSendHandler` despacha por `schemaVersion` | T003 | | |
| T008 | Rodar T002 e T003 até verde (`mvn -pl email-service,email-lambda -am verify` com `db-email-service` e `localstack` ativos) | T002, T006, T007 | | |
| T009 | `docker-compose.yml`: `email-service` com `EMAIL_QUEUE_NAME` e endpoint SQS do LocalStack; `docker compose config -q` | T004 | [P] | |
| T010 | `README.md`: exemplo de `curl` para `POST /emails`; atualizar `docs/context/iteracao-5.md` (3.2: SESv1 e contrato `"2"` decididos) | T008 | | |
| T011 | Rodar a suíte completa de `app` — confirmar verde; esta spec não toca `app/src` | T008 | [P] | |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
