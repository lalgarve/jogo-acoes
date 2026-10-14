# Tasks: Publicação na fila de envio em segundo plano

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** testes do comportamento novo primeiro, falhando pelo motivo esperado; depois as
correções. Depende da spec 05-044 (`SesTemplateClient.createIfAbsent`) para a T011.

Issue-épico: a criar quando a spec for aprovada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `send_email.feature`: cenários de envio aceito esperam `202 PENDING`, depois a mensagem na fila e o envio `QUEUED` com `template_data` vazio. Confirmar que falham porque a resposta é `QUEUED` | — | [P] | — |
| T002 | `send_email.feature`: "The send queue is unavailable" passa a esperar `202 PENDING`, `QUEUE_UNAVAILABLE` e tentativa gravados e, com o passo `When the send queue is available again`, a mensagem na fila e `QUEUED`. Confirmar que falha com `503` | — | [P] | — |
| T003 | `send_email.feature`: `Rule` "email-service handles delivery errors from the dead-letter queue" com os casos de `plan.md`, "Verificações", item 3. Confirmar que falham (DLQ inexistente, nada lê dela) | — | [P] | — |
| T004 | `send_email.feature`: cenário "The service gives up on a send". Confirmar que falha com `503` | — | [P] | — |
| T005 | `EmailSendHandlerTest`: template inexistente → mensagem na DLQ com `errorType = TEMPLATE_MISSING`, sem exceção. Confirmar que falha (o handler lança); registrar se o LocalStack responde `TemplateDoesNotExist` | — | [P] | — |
| T006 | `EmailQueuePublisherConcurrencyTest`. Registrar que não compila antes do publicador | — | [P] | — |
| T007 | DLQ e `RedrivePolicy` em `01-create-queue.sh`; nome da DLQ na Lambda (`03-deploy-email-lambda.sh`) e em `email.dlq-name`. `contracts/dlq-message.md` | — | | — |
| T008 | Migration V4, `EmailSend` e `EmailTemplate` com os campos novos. Confirmar `SchemaLayoutTest` verde | — | | — |
| T009 | `EmailSendService.send` só grava `PENDING`; remove `EmailQueuePublishException` e o handler do `503`. Contrato: `POST /emails` sem `503`, enum de `SendEmailResponse.status` | T008 | | — |
| T010 | `EmailQueuePublisher` (`@EnableScheduling`, `FOR UPDATE SKIP LOCKED`, `QUEUED`/`QUEUE_UNAVAILABLE`, espera crescente, desistência, `template_data` apagado). Confirmar T001, T002, T004 e T006 verdes | T001, T002, T004, T006, T009 | | — |
| T011 | `TemplateService.recreateOnSesUnlessRecent` e `DeadLetterConsumer`. Confirmar T003 verde | T003, T007, T010, spec 05-044 | | — |
| T012 | `EmailSendHandler`: classifica as exceções do SES e publica na DLQ com `errorType`/`errorMessage`. Confirmar T005 verde | T005, T007 | | — |
| T013 | Documentação: `specs/05-031-servico-email-envio/spec.md` aponta o que a 05-046 substituiu; comentário de `EmailSendHandler` sobre a redrive; nota em `docs/context/iteracao-4.md`, decisão 4, de que foi substituída pela 05-046 | T009, T012 | [P] | — |
| T014 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas e removidas (esperado: nenhuma criada, `EmailQueuePublishException` removida) e os branches de exceção adicionados ou alterados (falha de publicação; cada tipo de erro da DLQ, inclusive sem atributo e sem linha para o `correlationId`; falha do SES na recriação; desistência; cada `catch` da Lambda; commit que falha depois de publicar); para cada um, apontar o teste que o exercita. O que não tiver teste é justificado em `plan.md` (já previsto: commit que falha depois de publicar) e avisado no chat ao abrir a PR. Registrar a lista aqui | T010–T012 | | — |
| T015 | Rodar a suíte completa do `email-service`, a do `email-lambda` e `mvn -pl app -am verify` (cliente Feign regenerado com o enum novo) — confirmar verde | T013, T014 | | — |
| T016 | Verificação manual (Leila): com `docker compose up`, (a) apagar a fila de envio no LocalStack, pedir um login, conferir o envio `PENDING` com `QUEUE_UNAVAILABLE`, recriar a fila e ver o e-mail chegar no `ses-viewer`; (b) apagar um template do SES, pedir um login, conferir `TEMPLATE_MISSING` e `attempts` no banco, o template de volta no SES e o e-mail no `ses-viewer` | T015 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico, ou uma Issue própria quando grande o
  suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de tipo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.
