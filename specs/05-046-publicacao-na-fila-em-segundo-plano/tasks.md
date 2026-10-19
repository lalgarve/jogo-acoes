# Tasks: Publicação na fila de envio em segundo plano

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** testes do comportamento novo primeiro, falhando pelo motivo esperado; depois as
correções. Depende da spec 05-044 (`SesTemplateClient.createIfAbsent`) para a T011.

Issue-épico: [#185](https://github.com/lalgarve/jogo-acoes/issues/185).

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
| ~~T017~~ | Merge de `development` neste branch (`plan.md`, "Integração com 05-047/05-048"): `V4__add_delivery_state.sql` → `V7__add_delivery_state.sql`; resolver os conflitos mantendo a verificação do domínio no pedido e o fluxo `PENDING`. Confirmar `SchemaLayoutTest` verde | T014 | | — |
| ~~T018~~ | `send_email.feature`: nas `Rule`s das specs 05-047 e 05-048, os casos aceitos esperam `202 PENDING` e depois `QUEUED`; os recusados continuam `422`, sem linha em `email_send` e sem nada na fila; "The DNS server does not answer" espera `202 PENDING`. Confirmar verdes | T017 | | — |
| ~~T019~~ | `app`: conferir que o `422` de domínio (`RecipientDomainRejectedException`) continua traduzido no *gateway* com a resposta `202 PENDING` do `email-service`; `mvn -pl app -am verify` verde | T017 | [P] | — |
| T020 | CI: aplicar a opção escolhida para isolar a suíte do `email-service` do container (`plan.md`, decisão em aberto) e confirmar o CI verde numa PR para `development` | T017 | | — |
| T021 | Caixa-preta: aplicar a opção escolhida para a primeira entrega lenta (`plan.md`, decisão em aberto) e confirmar a suíte verde logo depois de uma subida do zero | T017 | [P] | — |
| ~~T015~~ | Rodar a suíte completa do `email-service`, a do `email-lambda` e `mvn -pl app -am verify` (cliente Feign regenerado com o enum novo) — confirmar verde | T013, T014, T017–T019 | | — |
| T016 | Verificação manual (Leila): rodar o roteiro "Roteiro da T016" abaixo e conferir os resultados esperados | T015 | | — |

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

## Registro da integração com as specs 05-047 e 05-048 (2026-10-19)

Na sessão de nuvem da Claude, com `scripts/cloud-up.sh` (spec 05-052: LocalStack Pro, Postgres e
DNS de teste do Compose, `config-server`/`email-service`/`app` com `java -jar` no host).

- **T017**: merge de `development` (55 commits). Migration renumerada para
  `V7__add_delivery_state.sql`; o Flyway aplica V1–V7 num banco novo. Conflitos resolvidos:
  `EmailSendService` (remetente, template e `RecipientDomainVerifier.verify` antes de gravar
  `PENDING`), `ApiExceptionHandler` (fica o `422` de domínio, sai o `503`),
  `docs/openapi-email-service.yaml` (`POST /emails` com `422` e sem `503`), os dois
  `application.yml` (chaves das três specs juntas) e `RegisterTemplatesSteps` (passos das duas
  linhas).
- **T018**: nas `Rule`s da 05-047, os cinco "the send is accepted as queued" viraram "the send is
  accepted as pending" seguido de "the send is recorded as queued with no template data kept".
  Os casos recusados (05-047 e 05-048) continuam `422`, sem linha em `email_send` e sem nada na
  fila, sem mudança.
- **T019**: `mvn -pl app -am verify`: 284 testes verdes, piso de cobertura atendido.
- **T015**: `email-service`: 144 testes, 142 verdes e 2 ignorados (`@requires-real-ses`).
  `email-lambda`: 10 verdes. Suíte de caixa-preta (`scripts/cloud-blackbox-tests.sh`): `behave`
  2/2 e `pytest` 29/29 com o Lambda já quente (ver o achado 2).
- **Achado 1, CI**: com o `email-service` de pé, a suíte do `email-service` falhou (rajada da DLQ
  com 4 de 10 mensagens, desistência com `SES_REJECTED` no lugar de `QUEUE_UNAVAILABLE`, envio
  aceito sem mensagem na fila de teste). O publicador do serviço que está rodando lê o mesmo
  banco e publica os envios `PENDING` criados pela suíte na fila dele. O CI deixava o container
  do `email-service` no ar durante essa suíte e falharia do mesmo jeito; a implementação de
  2026-10-14 nunca tinha rodado no CI. Com o serviço parado, 144 testes como acima. Uma correção
  no `ci.yml` (parar o container) chegou a ser commitada sem decisão da Leila e foi revertida; as
  opções estão em `plan.md`, decisão em aberto, e a aplicação é a T020.
- **Achado 2, suíte de caixa-preta**: a primeira rodada depois da subida falhou. A primeira
  entrega levou 19 s da fila ao SES (partida a frio do Lambda), passando do limite de 10 s de
  `last_email`; o e-mail atrasado foi lido pelo cenário seguinte, com um link já invalidado. Nas
  rodadas seguintes, com o Lambda já quente, passou. Um aumento do limite para 30 s chegou a ser
  commitado sem decisão da Leila e foi revertido; as opções estão em `plan.md`, decisão em
  aberto, e a aplicação é a T021.

- **Roteiros da T016 e da T013 da 05-044** (2026-10-14, reescritos conforme a seção
  "Verificação manual: comandos prontos para copiar" do constitution): os `curl`, `psql` e
  `awslocal` foram rodados contra o `email-service` desta branch (Postgres e LocalStack do Compose,
  `email-service` com `java -jar` no host na porta 8082) e as saídas esperadas são as obtidas.
  No passo 6 a mensagem `TEMPLATE_MISSING` foi posta na DLQ à mão, no lugar da Lambda. Não
  rodaram no container da Claude: a subida com `--build` (o Maven não alcança o repositório de
  dentro do Docker) e a entrega pela Lambda (passos 4 e 7: o LocalStack não baixa a imagem de
  runtime da Lambda).

## Roteiro da T016

Na raiz do repositório, no WSL, com o branch `claude/project-thread-kx436t` (ou o `master` depois
do merge). Só o `email-service`, o LocalStack (com a Lambda) e o `ses-viewer` são necessários;
`--build` garante que as imagens têm a mudança, e o `email-lambda-builder` empacota a Lambda de
novo quando o código dela mudou.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
docker compose up -d --wait --build db-email-service localstack
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
docker compose up -d --wait ses-viewer
API_KEY=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU
curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d '{"name":"roteiro-t016","subject":"Roteiro T016","body":"<p>Oi {{name}}</p>"}'
```

Esperado no último comando (as datas mudam):
```
{"body":"<p>Oi {{name}}</p>","createdAt":"...","name":"roteiro-t016","subject":"Roteiro T016","updatedAt":"..."} [201]
```

**(a) Fila de envio fora do ar**

1. Apagar a fila de envio e pedir um e-mail:
   ```bash
   docker compose exec localstack sh -c 'awslocal sqs delete-queue --queue-url $(awslocal sqs get-queue-url --queue-name jogo-acoes-email-commands --query QueueUrl --output text)'
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"roteiro-t016","recipientEmail":"success+t016a@simulator.amazonses.com","templateData":{"name":"Ada"}}'
   ```
   Esperado (o `id` muda):
   ```
   {"id":"7d242c83-...","status":"PENDING"} [202]
   ```
   Antes da mudança, a resposta era `503`.

2. O envio está pendente, com o erro gravado:
   ```bash
   sleep 3
   docker compose exec db-email-service psql -U email_service_admin -d email_service -c "select status, attempts, last_error_type, template_data is not null as keeps_template_data from email_service.email_send where recipient_email = 'success+t016a@simulator.amazonses.com';"
   ```
   Esperado (`attempts` cresce a cada vez que o comando é repetido):
   ```
    status  | attempts |  last_error_type  | keeps_template_data
   ---------+----------+-------------------+---------------------
    PENDING |        2 | QUEUE_UNAVAILABLE | t
   (1 row)
   ```

3. Recriar a fila (o script de subida do LocalStack, que também recoloca a *redrive policy*) e
   conferir o envio:
   ```bash
   docker compose exec localstack sh /etc/localstack/init/ready.d/01-create-queue.sh
   sleep 10
   docker compose exec db-email-service psql -U email_service_admin -d email_service -c "select status, attempts, last_error_type, template_data is not null as keeps_template_data from email_service.email_send where recipient_email = 'success+t016a@simulator.amazonses.com';"
   ```
   Esperado (o número de `attempts` varia; o JSON com o `QueueUrl` vem do script):
   ```
    status | attempts |  last_error_type  | keeps_template_data
   --------+----------+-------------------+---------------------
    QUEUED |        4 | QUEUE_UNAVAILABLE | f
   (1 row)
   ```

4. O e-mail chegou ao SES:
   ```bash
   sleep 10
   curl -s 'http://localhost:4566/_aws/ses?email=no-reply@jogo-acoes.example' | grep -o 'success+t016a@simulator.amazonses.com' | head -1
   ```
   Esperado: `success+t016a@simulator.amazonses.com`. O e-mail também aparece no `ses-viewer`,
   em `http://localhost:3005`. Se não aparecer, conferir se o gatilho da Lambda continua ligado
   à fila recriada:
   ```bash
   docker compose exec localstack awslocal lambda list-event-source-mappings --query 'EventSourceMappings[].[EventSourceArn,State]' --output text
   ```
   Esperado: `arn:aws:sqs:us-east-1:000000000000:jogo-acoes-email-commands	Enabled`.

**(b) Template sumiu do SES**

5. Apagar o template do SES e pedir um e-mail:
   ```bash
   docker compose exec localstack awslocal ses delete-template --template-name jogo-acoes__roteiro-t016
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"roteiro-t016","recipientEmail":"success+t016b@simulator.amazonses.com","templateData":{"name":"Ada"}}'
   ```
   Esperado: `{"id":"...","status":"PENDING"} [202]`.

6. A Lambda não acha o template e põe a mensagem na DLQ com `TEMPLATE_MISSING`; o
   `email-service` recria o template e publica de novo:
   ```bash
   sleep 15
   docker compose exec db-email-service psql -U email_service_admin -d email_service -c "select status, attempts, last_error_type from email_service.email_send where recipient_email = 'success+t016b@simulator.amazonses.com';"
   docker compose exec db-email-service psql -U email_service_admin -d email_service -c "select name, ses_recreated_at is not null as recreated from email_service.email_template where name = 'roteiro-t016';"
   docker compose exec localstack awslocal ses get-template --template-name jogo-acoes__roteiro-t016 --query 'Template.SubjectPart' --output text
   docker compose logs email-service | grep -E "TEMPLATE_MISSING|Recreated template jogo-acoes__roteiro-t016"
   ```
   Esperado:
   ```
    status | attempts | last_error_type
   --------+----------+------------------
    QUEUED |        1 | TEMPLATE_MISSING
   (1 row)

        name     | recreated
   --------------+-----------
    roteiro-t016 | t
   (1 row)

   Roteiro T016
   ... TemplateService : Recreated template jogo-acoes__roteiro-t016 on SES after a TEMPLATE_MISSING delivery error
   ... DeadLetterConsumer : Send ... was not delivered (TEMPLATE_MISSING): Template (jogo-acoes__roteiro-t016) does not exist ...
   ```

7. O e-mail chegou ao SES:
   ```bash
   sleep 10
   curl -s 'http://localhost:4566/_aws/ses?email=no-reply@jogo-acoes.example' | grep -o 'success+t016b@simulator.amazonses.com' | head -1
   ```
   Esperado: `success+t016b@simulator.amazonses.com`, e o e-mail no `ses-viewer`.

Para derrubar: `docker compose down -v`.
