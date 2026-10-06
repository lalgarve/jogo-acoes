# Tasks: Serviço de E-mail — envio de e-mail

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Os testes de verificação
vêm primeiro: cada um é escrito e visto falhando antes do código que o faz passar.

O contrato (`docs/openapi-email-service.yaml`, `POST /emails`) já está escrito, junto com esta
spec. Todas as decisões de `plan.md` estão resolvidas
(2026-10-05). Implementada em 2026-10-06, Issue [#130](https://github.com/lalgarve/jogo-acoes/issues/130)
(ver "Resultado da implementação" abaixo).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Escrever `email-service/src/test/resources/features/send_email.feature`: envio sem remetente definido (`409`, nada na fila), envio aceito (`202`, mensagem com template na fila com o `correlationId` = `id` devolvido e o `senderAddress` do cliente), template inexistente e template de outro cliente (`404`), pedido malformado (`400`), sem API-KEY (`401`), falha ao publicar (`503`, nada gravado) | — | [P] | #130 |
| ~~T002~~ | Step definitions de T001 (`SendEmailSteps.java`), via RestAssured contra o servidor real; a mensagem é lida da fila real do LocalStack. Rodar e ver todos os cenários falharem pelo motivo certo (rota inexistente), não por erro de configuração | T001 | #130 |
| ~~T003~~ | Teste do `email-lambda`: mensagem com template vira `SendTemplatedEmail` com `Source` = `senderAddress`, `Template`, `TemplateData` e a tag `correlationId`; mensagem sem `templateName`/`senderAddress` é rejeitada como malformada. Contra o LocalStack, como os testes atuais do módulo. Ver falhar | — | [P] | #130 |
| ~~T004~~ | `email-service/pom.xml`: `spring-cloud-aws-starter-sqs`; `email.queue-name` em `application.yml` e endpoint SQS do LocalStack em `application-docker.yml` (o perfil `sandbox` sai na spec 05-035) | — | [P] | #130 |
| ~~T005~~ | Migrations `V2__create_client_sender_table.sql` e `V3__create_email_send_table.sql` no schema `email_service`, em `db/migration-email-service/` (convenção da spec 05-032), + entidades `ClientSender`/`EmailSend` e repositórios | — | [P] | #130 |
| ~~T006~~ | `scripts/set-email-sender.sh <cliente> <endereço>`: valida o formato e grava/troca `client_sender`; chamado no restore dos dados de teste do `docker` com o remetente já verificado no LocalStack | T005 | #130 |
| ~~T007~~ | `EmailQueueMessage`, `EmailSendService` (busca template e remetente do cliente, grava `email_send`, publica; rollback e `EmailQueuePublishException` se a publicação falhar) e `EmailSendController` implementando `EmailsApi`; `ApiExceptionHandler` mapeia validação → `400`, remetente ausente → `409` e falha de fila → `503` | T004, T005 | #130 |
| ~~T008~~ | `email-lambda`: `EmailMessage` troca `subject`/`body` por `senderAddress`/`templateName`/`templateData`; `EmailSendHandler` usa `SendTemplatedEmail`; remover `email.sender-address` | T003 | #130 |
| ~~T009~~ | Rodar T002 e T003 até verde (`mvn -pl email-service,email-lambda -am verify` com `db-email-service` e `localstack` ativos) | T002, T006, T007, T008 | #130 |
| ~~T010~~ | `docker-compose.yml`: `email-service` com `EMAIL_QUEUE_NAME` e endpoint SQS do LocalStack; `docker compose config -q` | T004 | [P] | #130 |
| ~~T011~~ | `README.md`: como operações define o remetente e exemplo de `curl` para `POST /emails`; atualizar `docs/context/iteracao-5.md` (3.2: SESv1 e mensagem só com template decididos; anti-bounce e idempotência na Etapa 4) | T009 | #130 |
| ~~T012~~ | Rodar a suíte completa de `app` — confirmar verde; esta spec não toca `app/src` | T009 | [P] | #130 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Resultado da implementação (2026-10-06)

Verificado num container de nuvem com Docker (`docker compose up -d --wait db db-email-service
localstack`, `./scripts/install-api-key-lib.sh`, `./scripts/test-api-key.sh restore`).

- **T001/T002**: `send_email.feature` com 11 cenários. Antes do controller, os sete cenários que
  esperam outra coisa falharam todos com `404` (rota inexistente), não por configuração. Os de
  template inexistente/de outro cliente passavam por acaso (também `404`) e continuam valendo
  depois. A suíte publica numa fila própria do LocalStack (`email-service-send-test`, criada e
  esvaziada pelo `SendQueue`/`SendQueueHooks`), não na `jogo-acoes-email-commands`: essa está
  ligada à Lambda no LocalStack, que consumiria a mensagem antes do cenário lê-la. O cenário de
  `503` apaga essa fila de verdade e o `@After` a recria.
- **T003**: vermelho com a Lambda antiga (`MessageRejectedException`: mandava `subject`/`body` do
  remetente fixo). Verde depois do T008: o que o LocalStack recebeu é lido do próprio registro de
  SES dele (`GET /_aws/ses`): remetente, destinatário, `Template`, `TemplateData`. A tag
  `correlationId` não aparece nesse registro, então um `ExecutionInterceptor` de teste
  (`SentRequestRecorder`, via `quarkus.ses.interceptors`) registra o pedido real na saída. O teste
  reescrito não tem mais `assumeTrue` (ver "Testes exigem a infraestrutura de pé").
- **T005**: `SchemaLayoutTest` confere `client_sender` e `email_send` no schema `email_service`.
  `email_send.template_id` tem FK para `email_template`; o `TemplateCleanupHooks` apaga os envios
  antes dos templates.
- **T006**: `set-email-sender.sh` recusa `not-an-email`, troca o endereço numa segunda chamada
  (`updated_at` muda) e grava literalmente um cliente/endereço com aspas (variáveis do `psql`, sem
  interpolação). `test-api-key.sh restore` define `jogo-acoes` → `no-reply@jogo-acoes.example`
  quando a tabela já existe; senão avisa para rodar de novo depois de subir o `email-service`.
- **T007**: teste manual com o jar no perfil `docker`: `POST /templates` e `POST /emails` com a
  chave de teste devolveram `202 {id, status: QUEUED}`, e a mensagem na
  `jogo-acoes-email-commands` saiu como
  `{"schemaVersion":"1","correlationId":"<id>","senderAddress":"no-reply@jogo-acoes.example","recipientEmail":"…","templateName":"jogo-acoes__welcome","templateData":{"name":"Ada"}}`.
- **T009**: `SPRING_PROFILES_ACTIVE=docker mvn -pl email-service,email-lambda -am verify` verde
  (email-lambda 6 testes, email-service 39, dois `@requires-real-ses` fora do filtro, como antes) e
  `mvn -pl email-service verify` verde de novo logo em seguida (repetibilidade dos hooks).
- **T010**: `docker compose config -q` ok.
- **T012**: `mvn -pl app -am verify` verde (177 testes). Na primeira rodada o
  `SqsEmailSenderDockerIntegrationTest` falhou por um resto do teste manual do T007 na fila
  compartilhada (uma mensagem do `email-service`); com a fila limpa, verde. No CI isso não
  acontece: a suíte do `email-service` usa a fila própria.
- **Ponta a ponta (fila → Lambda → SES)**: não deu para rodar no container de nuvem (a imagem
  `public.ecr.aws/lambda/java:21` não baixa lá). Leila rodou em 2026-10-06 numa máquina com
  Docker, no commit `ffa11c6`: a `EmailLambda` ficou `Active` no LocalStack, as três suítes
  passaram (email-service duas vezes), e um `POST /emails` com a chave de teste devolveu `202`.
  Cerca de 12 s depois, o registro de SES do LocalStack mostrava a mensagem com
  `Source` = `no-reply@jogo-acoes.example`, `Template` = `jogo-acoes__welcome` e
  `TemplateData` = `{"name":"Ada"}`. Os casos `404`/`400`/`401` e a recusa de endereço
  inválido pelo `set-email-sender.sh` também se confirmaram.
