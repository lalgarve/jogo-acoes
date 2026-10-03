# Spec: Serviço de E-mail — envio de e-mail

**Status:** rascunho
**Issue:** #<a criar>
**Iteração:** iteration-5

## Resumo

Um cliente do Serviço de E-mail (a começar por `jogo-acoes`) pede o envio de um e-mail a partir
de um template que ele mesmo cadastrou (spec [05-025](../05-025-servico-email-templates/spec.md)),
informando o destinatário e as variáveis do template. O serviço confere o pedido, publica uma
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

- Contrato: [`docs/openapi-email-service.yaml`](../../docs/openapi-email-service.yaml) — rota
  `POST /emails` (tag `emails`).
- `.feature` Gherkin: `email-service/src/test/resources/features/send_email.feature` — a ser
  escrito antes do código (ver `tasks.md`, T001). Rules previstas: envio aceito e enfileirado,
  template inexistente/de outro cliente, pedido malformado, exigência de API-KEY, e a mensagem
  consumida pela Lambda chegando ao SES como envio por template.

## Requisitos funcionais

- `POST /emails` recebe `templateName`, `recipientEmail` e, opcionalmente, `templateData`
  (objeto de variáveis; ausente equivale a `{}`).
- O template é procurado entre os templates do próprio cliente que chamou (mesmo isolamento da
  05-025): um template de outro cliente responde `404`, igual a um inexistente.
- Um destinatário por pedido.
- Pedido aceito: o serviço gera um `id` (UUID), publica na fila de envio uma mensagem
  `schemaVersion: "2"` com `correlationId` = `id`, `recipientEmail`, `templateName` (o nome
  namespaced no SES, `<cliente>__<nome>`) e `templateData`, e responde `202` com
  `{ id, status: "QUEUED" }`.
- O `202` significa "enfileirado", não "entregue". Falha posterior de entrega (bounce,
  complaint) não é devolvida por este endpoint.
- Pedido malformado (campo obrigatório ausente, e-mail sintaticamente inválido) responde `400`.
- Falha ao publicar na fila responde `503` e nada é enviado; o cliente pode repetir.
- Toda rota exige `X-API-Key`, validada conforme a spec
  [05-030](../05-030-validacao-api-key-servico-email/spec.md); sem chave válida, `401`.
- A Lambda de e-mail passa a aceitar a mensagem `schemaVersion: "2"` e envia por
  `SendTemplatedEmail`, com o `correlationId` como *message tag*. Mensagens `schemaVersion: "1"`
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
- Job Spring Batch de importação da lista de domínios descartáveis (Etapa 4) — spec própria.
- Consulta do estado de um envio (`GET /emails/{id}`) e tratamento dos eventos
  `Delivery`/`Bounce`/`Complaint` do SES — dependem do *Configuration Set* e do tópico SNS da
  Iteração 6.
- Vários destinatários, cópia/cópia oculta, anexos.
- Validação de `templateData` contra o `variablesSchema` do template.

## Decisões em aberto

- **Anti-bounce nesta spec ou na seguinte?** O roadmap coloca a checagem de MX/domínio
  descartável dentro do envio, e o contrato já reserva o `422` para isso. Proposta: esta spec
  entrega só o envio; a checagem entra numa spec própria junto com o job Spring Batch da lista
  de domínios descartáveis, que é quem alimenta a checagem. Confirmar.
- **Idempotência para repetição pelo cliente.** Um cliente que repete o pedido depois de um
  timeout gera um segundo e-mail. Proposta: aceitar isso nesta spec (o `jogo-acoes` não repete
  automaticamente) e registrar como risco; um header `Idempotency-Key` fica para quando houver
  necessidade real. Confirmar.
- **Remetente.** Hoje o remetente é fixo na Lambda (`email.sender-address`). Proposta: manter
  fixo por ambiente, sem campo `from` no pedido. Confirmar.
