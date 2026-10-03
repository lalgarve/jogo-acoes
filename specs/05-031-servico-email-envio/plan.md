# Plan: Serviço de E-mail — envio de e-mail

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `email-service` (spec 05-025): Spring Boot 4.1.0, Java 21, PostgreSQL próprio
  (`db-email-service`), `spring-cloud-aws-starter-ses`, controllers gerados a partir de
  `docs/openapi-email-service.yaml` (`interfaceOnly`). Templates guardados em `email_template`
  com `ses_template_name` = `<cliente>__<nome>`, já sincronizados com o SES.
- Autenticação: `auth/ApiKeyAuthenticationFilter` + `auth/ClientIdentityResolver`; a spec
  05-030 troca o esqueleto pela validação real sem mudar o contrato. Esta spec só usa
  `ClientIdentityResolver.currentClientId()`, então não depende da ordem em que as duas forem
  implementadas.
- Fila de envio: `jogo-acoes-email-commands` (`email.queue-name` no `app/`), criada pelo
  `docker/localstack/init/01-create-queue.sh`. O `app/` publica nela com `SqsTemplate`
  (`SqsEmailSender`), mensagem `schemaVersion: "1"` com `subject`/`body` já renderizados.
- `email-lambda` (Quarkus): `EmailSendHandler` lê `EmailMessage` (`schemaVersion`,
  `correlationId`, `recipientEmail`, `subject`, `body`) e chama `SesClient.sendEmail` do **SESv1**
  (`software.amazon.awssdk.services.ses`), com `correlationId` como `MessageTag`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Contrato HTTP | `POST /emails` → `202 { id, status: QUEUED }`; erros `400`/`401`/`404`/`422`/`503` | resolvida (este PR) | Envio é assíncrono por natureza (fila + Lambda); `202` deixa claro que a resposta não é entrega. `422` reservado para o anti-bounce (decisão em aberto em `spec.md`). |
| SESv1 ou SESv2 (`iteracao-5.md`, 3.2) | SESv1 `SendTemplatedEmail` | proposta — confirmar | O `SesClient` da Lambda e do `email-service` já é o v1, e os templates da 05-025 são criados com `CreateTemplate` do v1 — mesmo "espaço" de templates. Trocar para v2 exigiria migrar a 05-025 junto. |
| Contrato da mensagem da fila | `schemaVersion: "2"`: `correlationId`, `recipientEmail`, `templateName` (nome no SES), `templateData` (objeto JSON) | resolvida (vem de `iteracao-5.md`, 3.2) | Mensagem pequena; a Lambda continua "burra" — repassa ao SES sem interpretar. |
| Mesma fila ou fila nova | Mesma fila `jogo-acoes-email-commands`; a Lambda distingue pelo `schemaVersion` | proposta — confirmar | Evita uma segunda fila/DLQ/event source mapping. O nome da fila fica ligado ao `jogo-acoes`, mas o serviço já está no mesmo reator e no mesmo compose; renomear fica para quando houver outro cliente de verdade. |
| Lambda e `schemaVersion` | `EmailMessage` ganha `templateName`/`templateData` opcionais; `"1"` → `SendEmail` (como hoje), `"2"` → `SendTemplatedEmail`; qualquer outro valor é rejeitado como mensagem malformada | proposta — confirmar | Um record só, sem hierarquia; o `app/` continua publicando `"1"` até a spec que o migrar. |
| Publicação na fila pelo `email-service` | `spring-cloud-aws-starter-sqs` + `SqsTemplate`, mesmo padrão de `SqsEmailSender`; `email.queue-name` por perfil | resolvida | Já usado e testado no `app/` contra LocalStack. |
| Registro do envio | Tabela `email_send` (`id` UUID = `correlationId`, `client_id`, `template_id`, `recipient_email`, `created_at`), gravada antes de publicar | proposta — confirmar | Mesmo raciocínio da decisão 9 da Iteração 4 (`sent_email` no `app/`): o `id` gerado vira o `correlationId`, e a tabela é onde os eventos do SES (Iteração 6) vão ser associados. Sem `templateData` (pode ter dado pessoal). |
| Falha ao publicar | Transação: grava `email_send`, publica; se a publicação falhar, rollback e `503` | proposta — confirmar | Não deixa registro de envio que nunca foi para a fila. O caso inverso (publicou e o commit falhou) gera um e-mail sem registro — aceito, raro. |
| Validação do pedido | Bean Validation das classes geradas (`@NotNull`, `@Email`) → `400` pelo `ApiExceptionHandler` | resolvida | O gerador já põe as anotações a partir do `required`/`format: email` do contrato. |

Decisões marcadas "proposta — confirmar" bloqueiam a implementação até serem confirmadas —
viram commit `decision:` quando resolvidas, atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

```
email-service/src/main/
  java/dev/leilaalgarve/jogoacoes/emailservice/
    send/
      EmailSendController.java     # implementa EmailsApi (gerada)
      EmailSendService.java        # busca template do cliente, grava email_send, publica
      EmailSend.java               # entidade
      EmailSendRepository.java
      EmailQueueMessage.java       # record do contrato schemaVersion "2"
      EmailQueuePublishException.java  # → 503
  resources/
    db/migration/V2__create_email_send_table.sql
email-lambda/src/main/java/dev/leilaalgarve/jogoacoes/email/lambda/
  EmailMessage.java                # + templateName, templateData
  EmailSendHandler.java            # despacha por schemaVersion
```

Se a spec 05-030 for implementada antes, as migrations do `email-service` estarão em
`db/email-service/migration/` — a `V2` vai para lá.

## Riscos e trade-offs

- **Template alterado entre o pedido e o envio**: o SES renderiza na hora do envio com a versão
  atual do template. Um `PUT /templates/{name}` logo depois de um `POST /emails` pode mudar o
  e-mail que ainda está na fila. Aceito: a janela é a latência da fila.
- **Variável faltando só aparece na entrega**: sem validação de `templateData` contra o
  `variablesSchema` (fora de escopo), um envio com variável faltando é aceito com `202` e falha
  (ou sai com o campo vazio) no SES. Mitigação disponível hoje: o cliente usa
  `POST /templates/{name}/preview` antes.
- **LocalStack e `SendTemplatedEmail`**: o LocalStack aceita a chamada mas não renderiza de
  verdade nem rejeita variável faltando — mesma limitação que levou aos cenários
  `@requires-real-ses` da 05-025 (Issue #104). Cenários que dependem disso recebem a mesma tag.
- **Repetição pelo cliente gera e-mail duplicado** (ver "Decisões em aberto" em `spec.md`).
