# Plan: chave de idempotência no pedido de envio de e-mail

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

Esta spec e a 05-060 cobrem juntas a [Issue #205](https://github.com/lalgarve/jogo-acoes/issues/205)
(repetição pelo cliente aqui, entrega repetida pela fila na 05-060).

## Contexto técnico

- `email-service`:
  - `EmailSendController.sendEmail` (implementa `EmailsApi`, gerado de
    `docs/openapi-email-service.yaml`) resolve o cliente pelo `X-API-Key`
    (`ClientIdentityResolver`) e chama `EmailSendService.send(clientId, request)`.
  - `EmailSendService.send` (`@Transactional`): acha o remetente do cliente
    (`SenderNotConfiguredException`, `409`), o template (`404`), verifica o domínio do
    destinatário (`RecipientDomainRejectedException`, `422`) e grava `EmailSend` como `PENDING`.
    O `EmailQueuePublisher` publica depois, em segundo plano (spec 05-046).
  - `EmailSend.id` é um UUID gerado pelo JPA e é o `correlationId` da mensagem da fila.
    `template_data` é apagado quando o envio vai para `QUEUED`.
  - `ApiExceptionHandler` não trata header faltando (`MissingRequestHeaderException`) nem
    validação de parâmetro (`HandlerMethodValidationException`): hoje esses casos saem com a
    resposta padrão do Spring, sem corpo `Error`.
  - Última migration: `V7__add_delivery_state.sql`, em `db/migration-email-service`.
- `app`:
  - `EmailServiceEmailSender.send` chama `EmailServiceGateway.sendEmail(templateName, email,
    templateData)` e grava `sent_email` com o id devolvido.
  - `EmailServiceGateway.sendEmail` não repete; trata todo `422` como domínio recusado
    (`RecipientDomainRejectedException`) e o resto por `translate` (`5xx` e falha de conexão viram
    `EmailServiceUnavailableException`). `retrying` repete até 3 vezes, 200 ms, só
    `EmailServiceUnavailableException`.
  - Testes: `EmailServiceClientIntegrationTest`, contra o `email-service` real.
- Outros clientes de `POST /emails`: o exemplo `curl` do `README.md` (seção do serviço de
  e-mail), que ainda diz "idempotência fica para a Etapa 4".
- Pré-produção: o contrato muda sem manter a forma antiga, e a coluna nova nasce `NOT NULL`
  (constitution, "Status do sistema: pré-produção").

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde guardar a chave | Duas colunas em `email_send`: `idempotency_key VARCHAR(255) NOT NULL` e `request_hash CHAR(64) NOT NULL`, com `UNIQUE (client_id, idempotency_key)`. Migration `V8__add_idempotency_key_to_email_send.sql`. | proposta | A linha do envio já existe para cada pedido aceito e nunca é apagada, então guardar ali cumpre as 24 horas mínimas da `spec.md` sem tabela nova nem limpeza. Uma tabela própria só valeria se a chave precisasse expirar antes do envio, o que a `spec.md` não pede. |
| Por quanto tempo a chave vale | Enquanto a linha existir (hoje, para sempre). | proposta | Não há limpeza de `email_send`. Se uma for criada (Issue #117, item 5, histórico só de inclusão), a chave vai junto. |
| Como saber se o corpo é o mesmo | `request_hash` = SHA-256, em hexadecimal, do JSON de `{templateName, recipientEmail, templateData}` serializado pelo `ObjectMapper` com `ORDER_MAP_ENTRIES_BY_KEYS`. | proposta | `template_data` é apagado depois do `QUEUED`, então comparar o corpo inteiro não daria. O hash não guarda dado pessoal. Ordenar as chaves faz o mesmo `templateData` dar o mesmo hash, venha na ordem que vier. |
| Ordem das checagens | A chave primeiro: se (cliente, chave) já existe, compara o hash e responde (`202` com o mesmo `id`, ou `422`) sem refazer remetente, template e domínio. Só um pedido novo passa pelas checagens e é gravado. | proposta | Uma repetição tem de receber a resposta do primeiro pedido, mesmo que algo tenha mudado no meio (o template foi apagado, o domínio saiu da lista). Pedidos recusados não gravam linha, então não guardam a chave (`spec.md`). |
| Dois pedidos simultâneos com a mesma chave | `EmailSendService.send` deixa de ser um `@Transactional` só: a gravação roda num `TransactionTemplate` e usa `saveAndFlush`. Se o índice único recusar (`DataIntegrityViolationException`), o serviço lê a linha do outro pedido numa nova transação e responde como uma repetição. | proposta | O segundo `INSERT` espera o primeiro no índice. Se o primeiro gravou, o segundo falha, lê e devolve o mesmo `id`. Se o primeiro foi desfeito, o segundo grava normalmente. A `spec.md` dizia "ou com o erro do primeiro, se ele falhar": como um pedido recusado não grava a chave, o segundo é avaliado por conta própria e chega ao mesmo erro, porque o corpo é o mesmo. A frase da `spec.md` é ajustada junto com este plano. |
| Header faltando ou inválido | `@ExceptionHandler` para `MissingRequestHeaderException` e `HandlerMethodValidationException` no `ApiExceptionHandler` do `email-service`, devolvendo `400` com `Error`. O contrato declara o header `required: true`, `maxLength: 255`, `minLength: 1`. | proposta | `spec.md`: sem o header, `400` com corpo `Error`. Os tratadores valem para todo o serviço, que hoje devolve a resposta padrão do Spring nesses casos. |
| Chave com corpo diferente | `422` com `Error` (`Idempotency-Key already used with a different request`). Exceção nova `IdempotencyKeyReusedException` no pacote `send`. | resolvida | Decidido na `spec.md`. |
| `422` ambíguo no `app` | O gateway continua tratando `422` de `POST /emails` como domínio recusado. O comentário passa a dizer que o `422` de chave reaproveitada não acontece porque o gateway manda o mesmo corpo em todas as tentativas da mesma chave. | proposta | Distinguir os dois `422` exigiria um código de erro no corpo `Error`, que hoje só tem `message`. Não vale um campo novo para um caso que o próprio `app` não produz. |
| Quem gera a chave no `app` | `EmailServiceEmailSender.send` gera `UUID.randomUUID()` uma vez por e-mail e passa para `EmailServiceGateway.sendEmail(key, ...)`. | proposta | Uma chave por e-mail que o `app` quer mandar (`spec.md`). Gerar no sender, fora do laço de repetição, garante a mesma chave em todas as tentativas. |
| Repetição no `app` | `sendEmail` passa a usar `retrying`, como as outras operações. A tabela "Operações usadas" de `specs/05-034-fronteira-sincrona-app-email-service/contracts/feign-client.md` e o Javadoc do gateway mudam. | proposta | É o objetivo da spec. A política (3 tentativas, 200 ms, só sem resposta ou `5xx`) é a mesma das outras operações. |
| Teste da resposta perdida | `EmailServiceClientIntegrationTest`: chamar o `email-service` real duas vezes com a mesma chave pelo cliente Feign (a segunda simula a repetição depois de uma resposta perdida) e conferir o mesmo `id` e uma linha só. A repetição em si (o gateway tentar de novo depois de uma falha) já é coberta pelos testes de `retrying` das outras operações. | proposta | Derrubar a conexão depois de o `email-service` gravar exigiria um proxy no meio. O que importa é o servidor responder igual à repetição, e isso se testa com o serviço real (constitution, "Testes"). |
| Testes do `email-service` | Teste de integração novo `EmailSendIdempotencyIntegrationTest` (pacote `send`): mesma chave e mesmo corpo → mesmo `id`, uma linha, uma mensagem na fila de teste; corpo diferente → `422`; outro cliente com a mesma chave → envio novo; sem header → `400`; pedido recusado (`404` de template) seguido de um válido com a mesma chave → `202`; dois pedidos simultâneos → um `id`. | proposta | Contra o PostgreSQL e o LocalStack que a suíte já usa. |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

- `docs/openapi-email-service.yaml`: parâmetro `Idempotency-Key` em `POST /emails`, respostas
  `400` e `422` com a descrição nova, e o texto da operação.
- `email-service/src/main/resources/db/migration-email-service/V8__add_idempotency_key_to_email_send.sql` (novo).
- `email-service/.../send/EmailSend.java`: os dois campos.
- `email-service/.../send/EmailSendRepository.java`: `findByClientIdAndIdempotencyKey`.
- `email-service/.../send/EmailSendService.java`: checagem da chave, hash, gravação com tradução da
  violação.
- `email-service/.../send/IdempotencyKeyReusedException.java` (novo) e o tratador no
  `common/ApiExceptionHandler.java`, junto com os de header faltando e inválido.
- `email-service/.../send/EmailSendController.java`: passa a chave ao serviço.
- `app/.../email/client/EmailServiceGateway.java` e `app/.../email/EmailServiceEmailSender.java`.
- `specs/05-034-fronteira-sincrona-app-email-service/contracts/feign-client.md`: tabela de
  operações.
- `README.md`: exemplo `curl` com o header e o parágrafo sobre repetição.

O `ArchitectureTest` dos dois serviços não é afetado: nenhuma dependência nova entre módulos.

## Riscos e trade-offs

- **Linhas antigas sem chave.** A coluna nasce `NOT NULL`, e a migration falha num banco local com
  envios já gravados. Pela pré-produção, o ambiente local é recriado (`docker compose down -v`);
  o CI começa de banco vazio.
- **Transação mais longa no `app`.** Com até 3 tentativas e 5 s de leitura cada, o pedido de
  convite ou de link pode segurar a conexão do banco do `app` por até cerca de 15 s antes de
  responder `503`. Hoje o pior caso é 5 s. Aceitável para um caso de falha; o tempo limite fica
  como está.
- **E-mail de um link desfeito.** Continua possível (05-056, "Riscos"): a chave evita o segundo
  e-mail numa repetição, não o primeiro e-mail de um pedido que o `app` desfez depois.

## Decisões em aberto

Nenhuma.
