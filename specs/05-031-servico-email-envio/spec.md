# Spec: Serviço de E-mail — envio de e-mail

**Status:** rascunho
**Issue:** #<a criar>
**Iteração:** iteration-5

## Resumo

Um cliente do Serviço de E-mail (a começar por `jogo-acoes`) pede o envio de um e-mail a partir
de um template que ele mesmo cadastrou (spec [05-025](../05-025-servico-email-templates/spec.md)),
informando o destinatário e as variáveis do template. Cada cliente tem um remetente fixo, que
ele mesmo configura pelo menos uma vez antes do primeiro envio. O serviço confere o pedido, publica uma
mensagem na fila SQS de envio e responde na hora; a Lambda de e-mail (`email-lambda`) entrega via
SES `SendTemplatedEmail`, que renderiza o template no momento do envio.

## Motivação

Com a 05-025 o serviço sabe guardar templates e sincronizá-los com o SES, mas ninguém consegue
usá-los para mandar um e-mail: as duas specs anteriores deixam o envio explicitamente "para a
spec seguinte" (05-025 e 05-030, "Fora de escopo"). Sem ele, o `email-service` não tem uso real
e o `jogo-acoes` continua renderizando os próprios e-mails com Thymeleaf dentro do `app/`.

É também o pedaço que fecha o desenho da seção 3.2 de `docs/context/iteracao-5.md`: mensagem
da fila com `templateName`/`templateData` (bem menor que o HTML já renderizado) e correlação
do envio pelo `correlationId` como *message tag* do SES.

## Cenários (comportamento esperado)

- Contrato: [`docs/openapi-email-service.yaml`](../../docs/openapi-email-service.yaml) — rotas
  `POST /emails` (tag `emails`) e `GET/PUT /sender` (tag `sender`).
- `.feature` Gherkin: `email-service/src/test/resources/features/send_email.feature` — a ser
  escrito antes do código (ver `tasks.md`, T001). Rules previstas: configuração do remetente,
  envio sem remetente configurado, envio aceito e enfileirado, template inexistente/de outro
  cliente, pedido malformado, exigência de API-KEY, e a mensagem consumida pela Lambda chegando
  ao SES como envio por template, com o remetente do cliente.

## Requisitos funcionais

- `POST /emails` recebe `templateName`, `recipientEmail` e, opcionalmente, `templateData`
  (objeto de variáveis; ausente equivale a `{}`).
- O template é procurado entre os templates do próprio cliente que chamou (mesmo isolamento da
  05-025): um template de outro cliente responde `404`, igual a um inexistente.
- Um destinatário por pedido.
- **Remetente fixo por cliente.** `PUT /sender` define (ou troca) o endereço remetente do
  cliente que chamou; `GET /sender` devolve o atual, ou `404` se nunca foi definido. Todo e-mail
  do cliente sai com esse remetente; o pedido de envio não tem campo `from`.
- Enquanto o cliente não definir o remetente, `POST /emails` responde `409` e nada é
  enfileirado.
- Pedido aceito: o serviço gera um `id` (UUID), publica na fila de envio uma mensagem
  `schemaVersion: "2"` com `correlationId` = `id`, `senderAddress` (o remetente do cliente),
  `recipientEmail`, `templateName` (o nome
  namespaced no SES, `<cliente>__<nome>`) e `templateData`, e responde `202` com
  `{ id, status: "QUEUED" }`.
- O `202` significa "enfileirado", não "entregue". Falha posterior de entrega (bounce,
  complaint) não é devolvida por este endpoint.
- Pedido malformado (campo obrigatório ausente, e-mail sintaticamente inválido) responde `400`.
- Falha ao publicar na fila responde `503` e nada é enviado; o cliente pode repetir.
- Toda rota exige `X-API-Key`, validada conforme a spec
  [05-030](../05-030-validacao-api-key-servico-email/spec.md); sem chave válida, `401`.
- A Lambda de e-mail passa a aceitar a mensagem `schemaVersion: "2"` e envia por
  `SendTemplatedEmail`, com `senderAddress` da mensagem como remetente e o `correlationId` como
  *message tag*. Mensagens `schemaVersion: "1"`
  (as que o `app/` publica hoje, com `subject`/`body` já renderizados) continuam funcionando.

## Requisitos não-funcionais

- O serviço não renderiza o e-mail: quem renderiza é o SES no envio. O `email-service` só
  confere que o template existe para o cliente.
- O endpoint não espera a entrega: tempo de resposta limitado à consulta do template e à
  publicação na fila.
- Testes contra PostgreSQL real e LocalStack (SQS e SES), sem mock — mesmo padrão das specs
  05-025/05-028.

## Fora de escopo

- `app/` passar a enviar e-mail pelo Serviço de E-mail (cliente OpenFeign, migração dos 5
  templates Thymeleaf, destino do `SqsEmailSender`) — spec seguinte, ver
  `docs/context/iteracao-5.md`, seção 5.
- Anti-bounce (checagem de MX e de domínio descartável) e o job Spring Batch que importa a
  lista de domínios descartáveis — Etapa 4 (decidido em 2026-10-04). O contrato não reserva
  resposta para isso; a spec da Etapa 4 acrescenta a sua.
- Idempotência — tanto a repetição do pedido pelo cliente quanto a entrega repetida da fila
  (SQS entrega "pelo menos uma vez", então a Lambda pode enviar o mesmo e-mail duas vezes) —
  Etapa 4 (decidido em 2026-10-04).
- Consulta do estado de um envio (`GET /emails/{id}`) e tratamento dos eventos
  `Delivery`/`Bounce`/`Complaint` do SES — dependem do *Configuration Set* e do tópico SNS da
  Iteração 6.
- Vários destinatários, cópia/cópia oculta, anexos.
- Validação de `templateData` contra o `variablesSchema` do template.

## Decisões resolvidas

Resolvidas em 2026-10-04 (Leila, na revisão do rascunho):

- ~~Anti-bounce nesta spec ou na seguinte~~ — Etapa 4.
- ~~Idempotência para repetição pelo cliente~~ — Etapa 4, junto com a entrega repetida pela
  fila.
- ~~Remetente~~ — fixo por cliente, configurado pelo próprio cliente pelo menos uma vez antes do
  primeiro envio.

## Decisões em aberto

- Nenhuma de requisito. Como o endereço do cliente vira uma identidade verificada no SES é
  decisão técnica, em `plan.md`.
