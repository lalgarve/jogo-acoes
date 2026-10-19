# Plan: Publicação na fila de envio em segundo plano

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `send/EmailSendService.send` (05-031): numa transação, confere remetente (`409`) e template
  (`TemplateService.reference`, `404`), grava `email_send` com `saveAndFlush`, publica com
  `SqsTemplate.send` e, se a publicação falhar, lança `EmailQueuePublishException` (`503`) e
  desfaz a gravação.
- `email_send` (V3): `id`, `client_id`, `template_id`, `recipient_email`, `created_at`. Sem
  status e sem `templateData`.
- `email-service` não tem `@EnableScheduling` nem `@SqsListener`; já tem o
  `spring-cloud-aws-starter-sqs`.
- `email-lambda/EmailSendHandler`: lê a mensagem, chama `SendTemplatedEmail` e deixa qualquer
  exceção subir; o comentário da classe conta com a redrive do SQS para a DLQ, mas nenhuma DLQ
  existe (`docker/localstack/init/01-create-queue.sh` cria só `jogo-acoes-email-commands`). O
  *event source mapping* usa `--batch-size 1`.
- O cenário "The send queue is unavailable" de `send_email.feature` apaga a fila no LocalStack
  (`SendQueue.delete()`); o hook recria a fila depois.
- Spec 05-044 acrescenta `SesTemplateClient.createIfAbsent`.
- No `development` (specs 05-047 e 05-048, mescladas depois deste branch): `EmailSendService.send`
  chama `RecipientDomainVerifier.verify(recipientEmail)` depois de remetente e template e antes de
  gravar. O verificador consulta `DisposableDomainChecker` (tabela `disposable_domain`), a lista
  branca (`approved_domain`) e `DnsMailDomainLookup`, e lança `RecipientDomainRejectedException`
  (`422`). As migrations do `email-service` lá já vão até a `V6`
  (`V4__create_approved_domain_table`, `V5__create_spring_batch_tables`,
  `V6__create_disposable_domain_tables`).
- `docs/context/iteracao-4.md`, decisão 4: retry pela Lambda com estado no DynamoDB. Não
  implementado; substituído por esta spec (`spec.md`, "Decisões resolvidas").

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde fica a fila de trabalho | Na própria `email_send`, com status. Sem tabela de *outbox* separada. | proposta | Um envio é uma linha; o status já é o que a spec pede. Nada de infraestrutura nova além da DLQ. |
| Migration | `V7__add_delivery_state.sql` (era `V4` antes da integração com o `development`, ver abaixo) em `db/migration-email-service`. Em `email_send`: `status VARCHAR(16) NOT NULL` (`PENDING`, `QUEUED`, `FAILED`), `sender_address VARCHAR(320) NOT NULL`, `ses_template_name VARCHAR(510) NOT NULL`, `template_data JSONB NULL`, `attempts INT NOT NULL DEFAULT 0`, `last_error_type VARCHAR(32) NULL` (`QUEUE_UNAVAILABLE`, `TEMPLATE_MISSING`, `SES_REJECTED`, `SES_UNAVAILABLE`, `UNKNOWN`), `last_error TEXT NULL`, `next_attempt_at TIMESTAMP NOT NULL`, `queued_at TIMESTAMP NULL`; linhas existentes viram `QUEUED`, com `sender_address`/`ses_template_name` preenchidos a partir de `client_sender`/`email_template`. Índice parcial `(next_attempt_at) WHERE status = 'PENDING'`. Em `email_template`: `ses_recreated_at TIMESTAMP NULL`. | proposta | Remetente e nome no SES gravados no pedido, para a publicação usar o que valia quando o pedido foi aceito. Sistema em pré-produção: sem compatibilidade a manter. |
| `POST /emails` | `EmailSendService.send` só confere (remetente, template e `RecipientDomainVerifier.verify`, nessa ordem), grava `PENDING` com `next_attempt_at = agora` e responde `202 { id, status: PENDING }`. Sai a publicação, sai `EmailQueuePublishException` e o `503` do handler e do contrato; o `422` de `RecipientDomainRejectedException` fica. | proposta | "A chamada não falha por causa da fila, do SES nem do DNS". O verificador já trata DNS sem resposta como aceito (05-047). |
| Integração com 05-047/05-048 | Merge de `development` neste branch. A migration deste branch passa de `V4__add_delivery_state.sql` para `V7__add_delivery_state.sql` (a `V4` do `development` é a lista branca); conteúdo igual. Conflitos esperados em `EmailSendService`, `ApiExceptionHandler`, `docs/openapi-email-service.yaml`, `send_email.feature` e `SendEmailSteps`: fica a verificação do domínio do `development` e o fluxo `PENDING` deste branch. `EmailQueuePublisher` não chama o verificador. | resolvida (Leila, 2026-10-19: a verificação fica no pedido) | O sistema está em pré-produção: renumerar a migration não afeta nenhum banco real. |
| CI: isolar a suíte do `email-service` do container | Opções: (A) parar o container do `email-service` depois dos testes do `app`; (B) rodar os testes do `email-service` antes de subir o container; (C) dois jobs no CI, um para o `app` (com o container) e outro para o `email-service` (sem ele); (D) um banco só para a suíte do `email-service`. Descartado: desligar o publicador por configuração (código de teste no módulo de produção, constitution). | em aberto (Leila) | O publicador do `email-service` em container lê o mesmo banco da suíte do `email-service` e publica na fila dele os envios `PENDING` criados pelos testes (achado na integração, `tasks.md`). Só a suíte do `app` precisa do serviço rodando. |
| Caixa-preta: primeira entrega lenta depois da subida | Opções: (A) `last_email` espera mais (ex.: 30 s, em vez de 10 s); (B) a subida do ambiente aquece o Lambda com um envio antes de liberar. | em aberto (Leila) | A primeira entrega depois da subida levou 19 s (partida a frio do Lambda no LocalStack), mais até 1 s do publicador. Com 10 s, o primeiro cenário falha e o seguinte lê o e-mail atrasado (achado na integração, `tasks.md`). |
| Thread de publicação | `send/EmailQueuePublisher`, `@Scheduled(fixedDelayString = "${email.publisher.poll-interval:1s}")` com `@EnableScheduling` numa configuração do `email-service`. Cada rodada pega até `email.publisher.batch-size` (padrão 20) linhas com `SELECT ... WHERE status = 'PENDING' AND next_attempt_at <= now() ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED` e trata cada uma na mesma transação da trava. Sucesso: `QUEUED`, `queued_at`, `template_data = NULL`. Falha: `QUEUE_UNAVAILABLE`, nova tentativa. | proposta | `SKIP LOCKED` cobre várias instâncias sem lock distribuído. `fixedDelay` evita rodadas sobrepostas na mesma instância. |
| DLQ | Fila nova `jogo-acoes-email-commands-dlq`, criada em `01-create-queue.sh`; a fila de envio ganha `RedrivePolicy` com `maxReceiveCount = 3` apontando para ela. Nome em `email.dlq-name` no `email-service` e em variável de ambiente na Lambda. | proposta | A redrive cobre o que a Lambda não classifica (exceção não prevista, tempo esgotado). |
| Classificação na Lambda | `EmailSendHandler` captura as exceções do SES: `TemplateDoesNotExistException` → `TEMPLATE_MISSING`; `MessageRejectedException`, `MailFromDomainNotVerifiedException`, `AccountSendingPausedException` → `SES_REJECTED`; `SdkClientException`, 5xx, `Throttling` → `SES_UNAVAILABLE`. Nesses casos publica a mensagem original na DLQ com os *message attributes* `errorType` e `errorMessage` e retorna sem exceção. Mensagem malformada e qualquer outra exceção continuam subindo (redrive). | resolvida (Leila, 2026-10-14) | Quem vê a resposta do SES é a Lambda; a DLQ pela redrive não carrega motivo. A Lambda continua sem banco (decisão 1 da Iteração 4) e sem regra de retry: ela só informa o tipo. |
| Consumidor da DLQ | `send/DeadLetterConsumer`, `@SqsListener("${email.dlq-name}")`. Para cada mensagem: acha `email_send` pelo `correlationId` (sem linha: `WARN` e descarta); `attempts + 1`, `last_error_type` (`UNKNOWN` sem atributo), `last_error`; aplica a regra de cada tipo (`spec.md`, "Erros na entrega"); grava `template_data` a partir do corpo da mensagem quando volta para `PENDING`. | proposta | O `email-service` é dono da decisão de tentar de novo. Uma só via de republicação: o envio volta para `PENDING` e o `EmailQueuePublisher` publica. |
| Recriação do template | `TemplateService.recreateOnSesUnlessRecent(templateId)`: se `ses_recreated_at` é de menos de `email.template.recreate-window` (1 h), não faz nada; senão chama `SesTemplateClient.createIfAbsent` e grava `ses_recreated_at = agora`. Falha do SES: o envio volta para `PENDING` com espera crescente e tipo `SES_UNAVAILABLE`. | resolvida (Leila, 2026-10-14: coluna no banco) | O pacote `send` usa o serviço do pacote `template`, sem falar com o SES direto (regra de módulos). |
| Espera e desistência | `next_attempt_at = agora + espera`, espera de `email.publisher.initial-delay` (1 s) dobrando por tentativa até `email.publisher.max-delay` (5 min). `TEMPLATE_MISSING` volta com `next_attempt_at = agora`. Antes de agendar, se `created_at + email.publisher.give-up-after` (24 h) já passou: `FAILED`, `template_data = NULL`, `ERROR` no log com `id` e `last_error_type`. | resolvida (Leila, 2026-10-14: 24 h) | Desistência por idade, não por número de tentativas: o que importa para quem pediu o e-mail é quanto tempo passou. |
| Publicou e não gravou `QUEUED` | A transação desfaz; a linha volta a ser `PENDING` e será publicada de novo. | proposta | "Pelo menos uma vez", aceito pela spec. |
| Contrato HTTP | `docs/openapi-email-service.yaml`: `POST /emails` sem `503`; descrição do `202` diz "aceito e gravado; publicado na fila em segundo plano"; `SendEmailResponse.status` com enum `[PENDING, QUEUED, FAILED]`. | proposta | Decisão em aberto 1 da spec. |
| Contrato da DLQ | `contracts/dlq-message.md`: corpo igual ao da fila de envio; atributos `errorType` (um dos tipos) e `errorMessage`; ausência dos atributos = redrive. | proposta | Fronteira entre Lambda e `email-service` por contrato de mensagem (constitution, "Fronteira entre módulos e serviços"). |
| `app` | Sem mudança de código. `EmailServiceGateway.sendEmail` lê só o `id`. | proposta | Conferir na implementação que o cliente Feign regenerado compila e que a suíte do `app` passa. |
| Spec 05-031 | Marcar no `spec.md` da 05-031 que "publica na fila e responde `202 QUEUED`" e o `503` foram substituídos pela 05-046. | proposta | A 05-031 é a referência do envio; não deixar dois comportamentos descritos. |

## Estrutura de módulos/pacotes

- `docker/localstack/init/01-create-queue.sh`: DLQ e `RedrivePolicy`;
  `03-deploy-email-lambda.sh`: variável com o nome da DLQ.
- `email-lambda/.../EmailSendHandler.java`: classificação e publicação na DLQ.
- `email-service/src/main/resources/db/migration-email-service/V7__add_delivery_state.sql`.
- `send/EmailSend.java`: campos novos e métodos `markQueued()`, `recordFailure(type, message)`,
  `retryAt(...)`, `fail()`.
- `send/EmailSendRepository.java`: consulta com `FOR UPDATE SKIP LOCKED`.
- `send/EmailSendService.java`: só grava.
- `send/EmailQueuePublisher.java` (novo), `send/DeadLetterConsumer.java` (novo) e a configuração
  com `@EnableScheduling`.
- `send/EmailQueuePublishException.java`: removida (e seu handler).
- `template/EmailTemplate.java`: `sesRecreatedAt`; `template/TemplateService.java`:
  `recreateOnSesUnlessRecent`.
- `docs/openapi-email-service.yaml`, `specs/05-046-.../contracts/dlq-message.md`,
  `specs/05-031-servico-email-envio/spec.md`.

## Verificações (antes das correções, falhando)

1. `send_email.feature`, cenários de envio aceito: passam a esperar `202` com `PENDING`, depois
   (Awaitility) a mensagem na fila e o envio `QUEUED` com `template_data` vazio. Hoje: `QUEUED`
   na resposta.
2. `send_email.feature`, "The send queue is unavailable": fila apagada → `202 PENDING`, envio
   gravado `PENDING` com `attempts` ≥ 1 e `QUEUE_UNAVAILABLE`; o passo novo `When the send queue
   is available again` recria a fila → a mensagem chega e o envio vira `QUEUED`. Hoje: `503`.
3. `send_email.feature`, `Rule` nova "email-service handles delivery errors from the dead-letter
   queue" (o teste põe a mensagem na DLQ do LocalStack com o atributo, no lugar da Lambda):
   - `TEMPLATE_MISSING` com o template apagado do SES → template de volta no SES,
     `ses_recreated_at` gravado, `attempts` = 1, `last_error_type` = `TEMPLATE_MISSING`, mensagem
     de novo na fila de envio;
   - dez mensagens `TEMPLATE_MISSING` do mesmo template → `ses_recreated_at` gravado uma vez só
     (a hora não muda depois da primeira) e as dez mensagens de novo na fila;
   - `SES_REJECTED` → `FAILED`, nada na fila;
   - `SES_UNAVAILABLE` e mensagem sem atributo → `PENDING` com espera, tipo gravado
     (`UNKNOWN` para a sem atributo).
   Hoje: a DLQ não existe e nada lê dela.
4. Cenário "The service gives up on a send": `email.publisher.give-up-after` curto no contexto de
   teste, fila apagada → o envio vira `FAILED`, `template_data` vazio, nada na fila. Hoje: `503`.
5. `EmailSendHandlerTest` (`email-lambda`): `SendTemplatedEmail` com template inexistente no
   LocalStack → mensagem na DLQ com `errorType = TEMPLATE_MISSING` e o handler não lança. Hoje:
   lança `TemplateDoesNotExistException`. Confirmar na implementação se o LocalStack responde
   `TemplateDoesNotExist` no `SendTemplatedEmail`; se não responder, registrar e cobrir esse caso
   só na verificação manual.
6. `EmailQueuePublisherConcurrencyTest`: dois contextos contra o mesmo PostgreSQL e LocalStack e
   50 envios `PENDING` → 50 mensagens na fila, uma por `id`. Hoje não compila (o publicador não
   existe); registrar.
7. `SchemaLayoutTest` continua verde com a V4.

## Riscos e trade-offs

- **Atraso de até `poll-interval`** entre o `202` e a publicação.
- **Template recriado e de novo ausente** dentro da janela de 1 h (alguém apagou de novo): as
  mensagens voltam para a fila sem recriar até a janela passar, e podem chegar à desistência.
- **Publicação repetida** num caso raro (publicou e o commit falhou). Fica para a spec de
  repetidos.
- **`FAILED` só no banco e no log:** o cliente não fica sabendo enquanto não existir
  `GET /emails/{id}`.
- **A Lambda precisa de permissão para publicar na DLQ** em staging/produção (IAM), além de ler a
  fila de envio.
- **Exceção sem teste possível:** falha do commit depois de publicar (precisaria derrubar o
  PostgreSQL no meio da transação). Justificada aqui.
- **Branches sem teste, descobertos na implementação (2026-10-14):**
  - `EmailQueuePublisher`: `SendMessage` falhando depois de `GetQueueUrl` dar certo (a fila some
    entre as duas chamadas) e a interrupção da thread no meio da espera. O teste derruba a fila
    antes da rodada, e cai no `catch` do `GetQueueUrl`; os dois `catch` levam ao mesmo
    `queueUnavailable`.
  - `EmailSendHandler`: `SesException` com status 5xx ou *throttling* (o LocalStack não responde
    assim), `SesException` não classificada (sobe para a redrive) e falha ao publicar na DLQ
    (sobe para a redrive). O caminho de SES sem resposta (`SdkClientException`) e os de template
    ausente e e-mail recusado têm teste.
