# Tasks: Publicação na fila de envio em segundo plano

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** testes do comportamento novo primeiro, falhando pelo motivo esperado; depois as
correções. Depende da spec 05-044 (`SesTemplateClient.createIfAbsent`) para a T009.

Issue-épico: a criar quando a spec for aprovada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `send_email.feature`: cenários de envio aceito esperam `202 PENDING`, depois a mensagem na fila e o envio `QUEUED` com `template_data` vazio. Confirmar que falham porque a resposta é `QUEUED` | — | [P] | — |
| T002 | `send_email.feature`: "The send queue is unavailable" passa a esperar `202 PENDING`, tentativa registrada e, com o passo `When the send queue is available again`, a mensagem na fila e `QUEUED`. Confirmar que falha com `503` | — | [P] | — |
| T003 | `send_email.feature`: cenário "The service gives up on a send" (`give-up-after` curto no teste). Confirmar que falha com `503` | — | [P] | — |
| T004 | `send_email.feature`: cenário "The template is missing on SES when the send is published". Confirmar que falha porque o template não volta ao SES | — | [P] | — |
| T005 | `EmailQueuePublisherConcurrencyTest` (dois contextos, 50 envios, uma mensagem por `id`). Registrar que não compila antes do publicador | — | [P] | — |
| T006 | Migration V4 e `EmailSend` com os campos novos. Confirmar `SchemaLayoutTest` verde | — | | — |
| T007 | `EmailSendService.send` só grava `PENDING`; remove `EmailQueuePublishException` e o handler do `503`. Contrato: `POST /emails` sem `503`, enum de `SendEmailResponse.status` | T006 | | — |
| T008 | `EmailQueuePublisher` (`@EnableScheduling`, `FOR UPDATE SKIP LOCKED`, `QUEUED`/nova tentativa/`FAILED`, `template_data` apagado). Confirmar T001, T002, T003 e T005 verdes | T001–T003, T005, T007 | | — |
| T009 | `TemplateService.ensureOnSes` e `SesTemplateClient.templateExists`, chamados pelo publicador antes de publicar. Confirmar T004 verde | T004, T008, spec 05-044 | | — |
| T010 | Documentação: `specs/05-031-servico-email-envio/spec.md` aponta o que a 05-046 substituiu | T007 | [P] | — |
| T011 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas e removidas (esperado: nenhuma criada, `EmailQueuePublishException` removida) e os branches de exceção adicionados ou alterados (falha de publicação, falha do SES em `ensureOnSes`, desistência, commit que falha depois de publicar); para cada um, apontar o teste que o exercita. O que não tiver teste é justificado em `plan.md` (já previsto: commit que falha depois de publicar) e avisado no chat ao abrir a PR. Registrar a lista aqui | T008, T009 | | — |
| T012 | Rodar a suíte completa do `email-service` e `mvn -pl app -am verify` (cliente Feign regenerado com o enum novo) — confirmar verde | T010, T011 | | — |
| T013 | Verificação manual (Leila): com `docker compose up`, apagar a fila de envio no LocalStack, pedir um login, conferir o envio `PENDING` no banco do `email-service`, recriar a fila e ver o e-mail chegar no `ses-viewer` | T012 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico, ou uma Issue própria quando grande o
  suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de tipo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.
