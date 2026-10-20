# Contrato: mensagem da DLQ de envio

Fila `jogo-acoes-email-commands-dlq` (`email.dlq-name` no `email-service`, `EMAIL_DLQ_NAME` na
Lambda). Quem escreve: a `email-lambda` e a *redrive policy* da fila de envio. Quem lê: o
`email-service` (`send/DeadLetterConsumer`).

## Corpo

Igual ao da mensagem da fila de envio (spec
[05-031](../../05-031-servico-email-envio/spec.md), `EmailQueueMessage`), sem alteração:

```json
{
  "schemaVersion": "1",
  "correlationId": "1b0e8c9e-0f4e-4f63-9a3c-6d1f0a2b7c11",
  "senderAddress": "no-reply@jogo-acoes.example",
  "recipientEmail": "player@example.com",
  "templateName": "jogo-acoes__login",
  "templateData": { "link": "https://..." }
}
```

O `correlationId` é o `id` da linha em `email_send`. O `templateData` volta para a linha quando o
envio é republicado (a linha não guarda mais os dados depois de `QUEUED`).

## Atributos (*message attributes*, tipo `String`)

| Atributo | Valor |
|---|---|
| `errorType` | `TEMPLATE_MISSING`, `SES_REJECTED` ou `SES_UNAVAILABLE` |
| `errorMessage` | Mensagem da exceção do SES, para o `last_error` da linha |

Classificação feita pela Lambda (`EmailSendHandler`):

| Exceção do SES | `errorType` |
|---|---|
| `TemplateDoesNotExistException` | `TEMPLATE_MISSING` |
| `MessageRejectedException`, `MailFromDomainNotVerifiedException`, `AccountSendingPausedException` | `SES_REJECTED` |
| `SdkClientException` (sem resposta), status 5xx, *throttling* | `SES_UNAVAILABLE` |

Qualquer outra exceção, mensagem malformada ou falha ao publicar na DLQ faz a invocação falhar;
depois de `maxReceiveCount = 3` entregas, a *redrive policy* move a mensagem para a DLQ **sem**
atributos. Sem `errorType` (ou com um valor desconhecido), o `email-service` trata como `UNKNOWN`.

## O que o `email-service` faz

| Tipo | Ação |
|---|---|
| `TEMPLATE_MISSING` | Recria o template no SES a partir do banco (só se não recriou na última hora) e republica na hora. SES fora do ar na recriação: registra `SES_UNAVAILABLE` e tenta mais tarde. |
| `SES_REJECTED` | `FAILED`, sem nova tentativa. |
| `SES_UNAVAILABLE`, `UNKNOWN` | Volta para `PENDING` com espera crescente. |

Em todos os casos: `attempts + 1`, `last_error_type` e `last_error` gravados; passadas 24 horas do
pedido, `FAILED`. Mensagem sem linha para o `correlationId`, ou para um envio que não está
`QUEUED` (cópia repetida do mesmo erro), gera `WARN` e é descartada.
