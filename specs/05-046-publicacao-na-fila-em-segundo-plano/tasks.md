# Tasks: Publicação na fila de envio em segundo plano

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** testes do comportamento novo primeiro, falhando pelo motivo esperado; depois as
correções. Depende da spec 05-044 (`SesTemplateClient.createIfAbsent`) para a T011.

Issue-épico: a criar quando a spec for aprovada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `send_email.feature`: cenários de envio aceito esperam `202 PENDING`, depois a mensagem na fila e o envio `QUEUED` com `template_data` vazio. Confirmar que falham porque a resposta é `QUEUED` | — | [P] | — |
| ~~T002~~ | `send_email.feature`: "The send queue is unavailable" passa a esperar `202 PENDING`, `QUEUE_UNAVAILABLE` e tentativa gravados e, com o passo `When the send queue is available again`, a mensagem na fila e `QUEUED`. Confirmar que falha com `503` | — | [P] | — |
| ~~T003~~ | `send_email.feature`: `Rule` "email-service handles delivery errors from the dead-letter queue" com os casos de `plan.md`, "Verificações", item 3. Confirmar que falham (DLQ inexistente, nada lê dela) | — | [P] | — |
| ~~T004~~ | `send_email.feature`: cenário "The service gives up on a send". Confirmar que falha com `503` | — | [P] | — |
| ~~T005~~ | `EmailSendHandlerTest`: template inexistente → mensagem na DLQ com `errorType = TEMPLATE_MISSING`, sem exceção. Confirmar que falha (o handler lança); registrar se o LocalStack responde `TemplateDoesNotExist` | — | [P] | — |
| ~~T006~~ | `EmailQueuePublisherConcurrencyTest`. Registrar que não compila antes do publicador | — | [P] | — |
| ~~T007~~ | DLQ e `RedrivePolicy` em `01-create-queue.sh`; nome da DLQ na Lambda (`03-deploy-email-lambda.sh`) e em `email.dlq-name`. `contracts/dlq-message.md` | — | | — |
| ~~T008~~ | Migration V4, `EmailSend` e `EmailTemplate` com os campos novos. Confirmar `SchemaLayoutTest` verde | — | | — |
| ~~T009~~ | `EmailSendService.send` só grava `PENDING`; remove `EmailQueuePublishException` e o handler do `503`. Contrato: `POST /emails` sem `503`, enum de `SendEmailResponse.status` | T008 | | — |
| ~~T010~~ | `EmailQueuePublisher` (`@EnableScheduling`, `FOR UPDATE SKIP LOCKED`, `QUEUED`/`QUEUE_UNAVAILABLE`, espera crescente, desistência, `template_data` apagado). Confirmar T001, T002, T004 e T006 verdes | T001, T002, T004, T006, T009 | | — |
| ~~T011~~ | `TemplateService.recreateOnSesUnlessRecent` e `DeadLetterConsumer`. Confirmar T003 verde | T003, T007, T010, spec 05-044 | | — |
| ~~T012~~ | `EmailSendHandler`: classifica as exceções do SES e publica na DLQ com `errorType`/`errorMessage`. Confirmar T005 verde | T005, T007 | | — |
| ~~T013~~ | Documentação: `specs/05-031-servico-email-envio/spec.md` aponta o que a 05-046 substituiu; comentário de `EmailSendHandler` sobre a redrive; nota em `docs/context/iteracao-4.md`, decisão 4, de que foi substituída pela 05-046 | T009, T012 | [P] | — |
| ~~T014~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas e removidas (esperado: nenhuma criada, `EmailQueuePublishException` removida) e os branches de exceção adicionados ou alterados (falha de publicação; cada tipo de erro da DLQ, inclusive sem atributo e sem linha para o `correlationId`; falha do SES na recriação; desistência; cada `catch` da Lambda; commit que falha depois de publicar); para cada um, apontar o teste que o exercita. O que não tiver teste é justificado em `plan.md` (já previsto: commit que falha depois de publicar) e avisado no chat ao abrir a PR. Registrar a lista aqui | T010–T012 | | — |
| T015 | Rodar a suíte completa do `email-service`, a do `email-lambda` e `mvn -pl app -am verify` (cliente Feign regenerado com o enum novo) — confirmar verde | T013, T014 | | — |
| T016 | Verificação manual (Leila): com `docker compose up`, (a) apagar a fila de envio no LocalStack, pedir um login, conferir o envio `PENDING` com `QUEUE_UNAVAILABLE`, recriar a fila e ver o e-mail chegar no `ses-viewer`; (b) apagar um template do SES, pedir um login, conferir `TEMPLATE_MISSING` e `attempts` no banco, o template de volta no SES e o e-mail no `ses-viewer` | T015 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico, ou uma Issue própria quando grande o
  suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de tipo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Registro da implementação (2026-10-14)

Infraestrutura no container da Claude: `db`, `db-email-service` e `localstack` do Compose; o
`function.zip` da Lambda construído no host e copiado para `email-lambda/dist/` (o
`email-lambda-builder` não alcança o repositório Maven de dentro do Docker); `config-server` e,
para a suíte do `app`, o `email-service` rodando no host (porta 8082) com o perfil `docker`.

- **T001–T004** (vermelhos antes da correção): envio aceito → `expected: "PENDING" but was:
  "QUEUED"`; fila indisponível e desistência → `expected: 202 but was: 503`; casos da DLQ →
  `ConditionTimeoutException` (nada lia a DLQ). O passo `SES has the template named ... again`
  passou a esperar com Awaitility, porque depois da DLQ o template volta em segundo plano.
- **T005**: `EmailSendHandlerTest` com template ausente, SES sem resposta e remetente não
  verificado. Antes da correção os dois primeiros lançaram (o terceiro foi escrito junto com a
  T014). O LocalStack responde `TemplateDoesNotExistException` no `SendTemplatedEmail` e
  `MessageRejectedException` para remetente não verificado.
- **T006**: `EmailQueuePublisherConcurrencyTest` compilava antes do publicador e passava, porque o
  `SqsTemplate` recria sozinho a fila apagada. Ganhou a conferência de `status: PENDING` na
  resposta e ficou vermelho; o publicador usa o `SqsAsyncClient` direto, sem criar fila.
  `DeadLetterBurstIntegrationTest` (10 erros `TEMPLATE_MISSING` do mesmo template): antes,
  `ConditionTimeoutException`; depois, os 10 voltam para a fila e o log mostra uma recriação só.
- **T007**: DLQ `jogo-acoes-email-commands-dlq` e `RedrivePolicy` (`maxReceiveCount = 3`) em
  `01-create-queue.sh` (conferido no LocalStack com `get-queue-attributes`); cliente SQS e
  `EMAIL_DLQ_NAME` da Lambda em `03-deploy-email-lambda.sh`; `EMAIL_DLQ_NAME` no
  `email-service` do `docker-compose.yml`; `email.dlq-name` nos dois serviços;
  `contracts/dlq-message.md`.
- **T008**: `V4__add_delivery_state.sql`; `EmailSend` com status, remetente, nome do template no
  SES, `template_data`, tentativas, último erro, próxima tentativa e `queued_at`;
  `EmailTemplate.sesRecreatedAt`. `SchemaLayoutTest` verde.
- **T009**: `EmailSendService.send` só grava `PENDING`; `EmailQueuePublishException` e o handler
  do `503` removidos; contrato sem `503` em `POST /emails`, enum `PENDING`/`QUEUED`/`FAILED`.
- **T010**: `EmailQueuePublisher` (`@Scheduled`, lote `FOR UPDATE SKIP LOCKED`) e `RetryPolicy`
  (espera de `initial-delay` dobrando até `max-delay`, desistência em `give-up-after`, `ERROR` no
  log). Na suíte: 200 ms, 1 s, e a desistência testada recuando o `created_at` em 25 h.
- **T011**: `TemplateService.recreateOnSesUnlessRecent` (trava `PESSIMISTIC_WRITE` na linha do
  template, janela `email.template.recreate-window`, 1 h) e `DeadLetterConsumer`.
- **T012**: `EmailSendHandler` classifica e publica na DLQ com `errorType`/`errorMessage`.
- **T013**: notas na spec 05-031, no comentário do `EmailSendHandler` e na decisão 4 de
  `docs/context/iteracao-4.md`.
- **T014** — classes de exceção e branches:
  - Criadas: nenhuma. Removida: `EmailQueuePublishException` (e o handler do `503`).
  - Falha de publicação (`GetQueueUrl`) → cenários "The send queue is unavailable" e "The
    service gives up on a send", e `EmailQueuePublisherConcurrencyTest`.
  - DLQ `TEMPLATE_MISSING`, `SES_REJECTED`, `SES_UNAVAILABLE`, sem atributo → os quatro cenários da
    `Rule` da DLQ; rajada → `DeadLetterBurstIntegrationTest`.
  - SES fora do ar na recriação, mensagem sem linha, envio que não está `QUEUED`, mensagem
    malformada → `DeadLetterConsumerIntegrationTest` (novo, com DLQ própria e SES numa porta
    fechada).
  - Lambda: `TemplateDoesNotExistException`, `MessageRejectedException`, `SdkClientException` →
    `EmailSendHandlerTest`.
  - Sem teste, justificados em `plan.md` ("Riscos e trade-offs"): commit que falha depois de
    publicar; `SendMessage` falhando depois do `GetQueueUrl` e interrupção no publicador;
    `SesException` 5xx/*throttling*, `SesException` não classificada e falha ao publicar na DLQ
    na Lambda.
- **T015** (parcial): `email-service` com `mvn -pl email-service verify`: 68 testes, 66 verdes, 2
  ignorados (`@requires-real-ses`). `email-lambda`: 10 testes verdes.
  Suíte do `app` (`mvn -pl app -am verify`, com o `email-service` desta branch rodando no host
  na porta 8082): 278 testes, 272 verdes. As 6 falhas são de `EmailServiceClientIntegrationTest`:
  5 casos de `sendingRecordsEmailServiceIdAndReachesSesWithTheTemplate` (o e-mail não chega ao
  SES porque o LocalStack deste container não consegue baixar a imagem de runtime da Lambda,
  `public.ecr.aws/lambda/java:21`: a rede bloqueia o download, então a função fica `Failed` e
  nada é entregue) e 1 de `previewWithIncompleteDataIsRejectedWithoutRetrying` (0,30 s contra o
  limite de 0,2 s, com a máquina carregada; rodado sozinho, passou). Falta a confirmação num
  ambiente em que a Lambda roda (CI ou máquina local).
