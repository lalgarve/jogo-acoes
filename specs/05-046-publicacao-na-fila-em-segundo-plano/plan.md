# Plan: Publicação na fila de envio em segundo plano

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `send/EmailSendService.send` (05-031): numa transação, confere remetente (`409`) e template
  (`TemplateService.reference`, `404`), grava `email_send` com `saveAndFlush`, publica com
  `SqsTemplate.send` e, se a publicação falhar, lança `EmailQueuePublishException` (`503`) e
  desfaz a gravação.
- `email_send` (V3): `id`, `client_id`, `template_id`, `recipient_email`, `created_at`. Sem
  status e sem `templateData`.
- `email-service` não tem `@EnableScheduling` nem `TaskScheduler` configurado.
- O cenário "The send queue is unavailable" de `send_email.feature` deixa a fila indisponível
  apagando-a no LocalStack (`SendQueue.delete()`); o hook recria a fila depois.
- Spec 05-044 acrescenta `SesTemplateClient.createIfAbsent` e a reconciliação dos templates.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde fica a fila de trabalho | Na própria `email_send`, com status. Sem tabela de *outbox* separada. | proposta | Um envio é uma linha; o status já é o que a spec pede. Nada de infraestrutura nova. |
| Migration | `V4__add_publication_state_to_email_send.sql` em `db/migration-email-service`: `status VARCHAR(16) NOT NULL` (`PENDING`, `QUEUED`, `FAILED`), `sender_address VARCHAR(320) NOT NULL`, `ses_template_name VARCHAR(510) NOT NULL`, `template_data JSONB NULL`, `attempts INT NOT NULL DEFAULT 0`, `next_attempt_at TIMESTAMP NOT NULL`, `queued_at TIMESTAMP NULL`, `last_error TEXT NULL`; linhas existentes viram `QUEUED`, com `sender_address`/`ses_template_name` preenchidos a partir de `client_sender`/`email_template`. Índice parcial `(next_attempt_at) WHERE status = 'PENDING'`. | proposta | Remetente e nome no SES gravados no pedido, para a publicação usar o que valia quando o pedido foi aceito. Sistema em pré-produção: sem compatibilidade a manter. |
| `POST /emails` | `EmailSendService.send` só confere, grava `PENDING` com `next_attempt_at = agora` e responde `202 { id, status: PENDING }`. Sai a publicação, sai `EmailQueuePublishException` e o `503` do handler e do contrato. | proposta | Requisito "A chamada não falha por causa da fila nem do SES". |
| Thread de publicação | `send/EmailQueuePublisher`, `@Scheduled(fixedDelayString = "${email.publisher.poll-interval:1s}")` com `@EnableScheduling` numa configuração do `email-service`. Cada rodada pega até `email.publisher.batch-size` (padrão 20) linhas com `SELECT ... WHERE status = 'PENDING' AND next_attempt_at <= now() ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED` e trata cada uma na mesma transação da trava. | proposta | `SKIP LOCKED` cobre várias instâncias sem lock distribuído. `fixedDelay` evita rodadas sobrepostas na mesma instância. |
| Conferir o template no SES (decisão em aberto 1) | Antes de publicar, `TemplateService.ensureOnSes(templateId)`: `GetTemplate`; se o SES não tiver, `SesTemplateClient.createIfAbsent` com o conteúdo do banco (05-044). Falha do SES conta como falha de publicação. | proposta | O pacote `send` continua sem falar com o SES direto; usa o serviço do pacote `template` (regra de módulos). `GetTemplate` antes, porque as cotas do SES para criar template são bem menores que as de leitura. |
| Sucesso | `status = QUEUED`, `queued_at = agora`, `template_data = NULL`, `last_error = NULL`. | proposta | Requisito do `templateData`. |
| Falha | `attempts + 1`, `last_error` com a mensagem, `next_attempt_at = agora + espera`, espera de `email.publisher.initial-delay` (1 s) dobrando até `email.publisher.max-delay` (5 min). Se `created_at + email.publisher.give-up-after` (24 h) já passou: `status = FAILED`, `template_data = NULL`, `ERROR` no log com o `id`. | proposta | Decisão em aberto 2 da spec. Desistência por idade, não por número de tentativas: o que importa para quem pediu o e-mail é quanto tempo passou. |
| Publicou e não gravou `QUEUED` | A transação desfaz; a linha volta a ser `PENDING` e será publicada de novo. | proposta | "Pelo menos uma vez", aceito pela spec. |
| Contrato | `docs/openapi-email-service.yaml`: `POST /emails` sem `503`; descrição do `202` diz "aceito e gravado; publicado na fila em segundo plano"; `SendEmailResponse.status` com enum `[PENDING, QUEUED, FAILED]`. | proposta | Decisão em aberto 3 da spec. |
| `app` | Sem mudança de código. `EmailServiceGateway.sendEmail` lê só o `id`; o cliente gerado aceita o enum novo ao regenerar. | proposta | Conferir na implementação que o cliente Feign regenerado compila e que a suíte do `app` passa. |
| Spec 05-031 | Marcar no `spec.md` da 05-031 que "publica na fila e responde `202 QUEUED`" e o `503` foram substituídos pela 05-046. | proposta | A 05-031 é a referência do envio; não deixar dois comportamentos descritos. |

## Estrutura de módulos/pacotes

- `email-service/src/main/resources/db/migration-email-service/V4__add_publication_state_to_email_send.sql`.
- `send/EmailSend.java`: campos novos e métodos `markQueued()`/`recordFailure(...)`.
- `send/EmailSendRepository.java`: consulta com `FOR UPDATE SKIP LOCKED`.
- `send/EmailSendService.java`: só grava.
- `send/EmailQueuePublisher.java` (novo) e a configuração com `@EnableScheduling`.
- `send/EmailQueuePublishException.java`: removida (e seu handler).
- `template/TemplateService.java`: `ensureOnSes(templateId)`; `template/SesTemplateClient.java`:
  `templateExists(sesName)` (`GetTemplate`).
- `docs/openapi-email-service.yaml`, `specs/05-031-servico-email-envio/spec.md`.

## Verificações (antes das correções, falhando)

1. `send_email.feature`, cenários de envio aceito: passam a esperar `202` com `PENDING`, depois
   (Awaitility) a mensagem na fila e o envio `QUEUED` com `template_data` vazio. Hoje: `QUEUED`
   na resposta.
2. `send_email.feature`, "The send queue is unavailable": fila apagada → `202 PENDING`, envio
   gravado `PENDING` com uma tentativa a mais e `last_error`; o passo novo `When the send queue is
   available again` recria a fila → a mensagem chega e o envio vira `QUEUED`. Hoje: `503`.
3. Cenário novo "The service gives up on a send": `email.publisher.give-up-after` curto no
   contexto de teste, fila apagada → o envio vira `FAILED`, `template_data` vazio, nada na fila.
   Hoje: `503`.
4. Cenário novo "The template is missing on SES when the send is published": `DeleteTemplate`
   direto no LocalStack depois do cadastro → a mensagem chega à fila e o template volta a existir
   no SES. Hoje: a mensagem vai para a fila com o template ausente.
5. `EmailQueuePublisherConcurrencyTest`: duas instâncias do publicador (dois contextos contra o
   mesmo PostgreSQL e LocalStack) e 50 envios `PENDING` → 50 mensagens na fila, uma por `id`.
   Hoje não compila (o publicador não existe); registrar.
6. `SchemaLayoutTest` continua verde com a V4.

## Riscos e trade-offs

- **Atraso de até `poll-interval`** entre o `202` e a publicação.
- **Uma chamada `GetTemplate` por e-mail.** Se as cotas do SES apertarem, trocar por um cache
  curto em memória (fora desta spec).
- **Publicação repetida** num caso raro (publicou e o commit falhou). Fica para a spec de
  repetidos.
- **`FAILED` só no banco e no log:** o cliente não fica sabendo enquanto não existir
  `GET /emails/{id}`.
- **Exceção sem teste possível:** falha do commit depois de publicar (precisaria derrubar o
  PostgreSQL no meio da transação). Justificada aqui.
