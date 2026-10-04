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
  (`SqsEmailSender`), mensagem com `subject`/`body` já renderizados — formato que esta spec
  substitui.
- `email-lambda` (Quarkus): `EmailSendHandler` lê `EmailMessage` (`schemaVersion`,
  `correlationId`, `recipientEmail`, `subject`, `body`) e chama `SesClient.sendEmail` do **SESv1**
  (`software.amazon.awssdk.services.ses`), com `correlationId` como `MessageTag`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Contrato HTTP | `POST /emails` → `202 { id, status: QUEUED }`; erros `400`/`401`/`404`/`409`/`503` | resolvida | Envio é assíncrono por natureza (fila + Lambda); `202` deixa claro que a resposta não é entrega. `409` quando operações ainda não definiu o remetente do cliente — o pedido está certo, o estado do cliente é que não permite. |
| Onde guardar o remetente | Tabela `client_sender` (`client_id` PK, `address`, `created_at`, `updated_at`) no schema próprio do `email-service` | resolvida | Um remetente por cliente (decisão de 2026-10-04); `client_id` é o mesmo identificador usado em `email_template`. |
| Como operações define o remetente | Script versionado `scripts/set-email-sender.sh <cliente> <endereço>`: valida o formato do e-mail e grava (ou troca) a linha de `client_sender` no banco do serviço | proposta — confirmar | Remetente é dado de operação, não do cliente (decisão de 2026-10-04) — mesmo papel que a CLI do `api-key` tem para as chaves. Sem verificação de identidade no SES: o endereço é considerado válido se tiver formato de e-mail. Em `docker`/`sandbox`, o mesmo script roda no restore dos dados de teste com o endereço já verificado por `02-verify-ses-sender.sh`. |
| SESv1 ou SESv2 (`iteracao-5.md`, 3.2) | SESv1 `SendTemplatedEmail` | proposta — confirmar | O `SesClient` da Lambda e do `email-service` já é o v1, e os templates da 05-025 são criados com `CreateTemplate` do v1 — mesmo "espaço" de templates. Trocar para v2 exigiria migrar a 05-025 junto. |
| Contrato da mensagem da fila | `schemaVersion`, `correlationId`, `senderAddress`, `recipientEmail`, `templateName` (nome no SES), `templateData` (objeto JSON); substitui `subject`/`body`, sem versão nova | resolvida (2026-10-04) | Mensagem pequena; a Lambda continua "burra" — repassa ao SES sem interpretar. Pré-produção: o contrato muda no lugar, a Lambda só aceita mensagem com template. O `email.sender-address` fixo da Lambda deixa de ser usado. |
| Mesma fila ou fila nova | Mesma fila `jogo-acoes-email-commands` | proposta — confirmar | Evita uma segunda fila/DLQ/event source mapping. O nome da fila fica ligado ao `jogo-acoes`, mas o serviço já está no mesmo reator e no mesmo compose; renomear fica para quando houver outro cliente de verdade. |
| `app/` enquanto não migra | O `SqsEmailSender` do `app/` (que publica `subject`/`body`) para de ser entregue assim que a Lambda nova entrar. A spec de migração do `app/` para o Serviço de E-mail é implementada logo em seguida, ou junto | proposta — confirmar | Consequência direta de a Lambda só aceitar template. Até a migração, os e-mails do `app/` (link mágico, convites) não saem em `docker`/CI — aceitável em pré-produção, mas quebra o fluxo de login de ponta a ponta nesse intervalo. |
| Publicação na fila pelo `email-service` | `spring-cloud-aws-starter-sqs` + `SqsTemplate`, mesmo padrão de `SqsEmailSender`; `email.queue-name` por perfil | resolvida | Já usado e testado no `app/` contra LocalStack. |
| Schema e Flyway | As tabelas novas ficam no schema próprio do `email-service`, nunca no `public`; migrations em `db/migration-email-service/` e histórico do Flyway com o nome do serviço, conforme a regra da [Issue #111](https://github.com/lalgarve/jogo-acoes/issues/111) | resolvida (2026-10-04) | Decisão da Leila na thread da 05-030: um schema por serviço, para poder rodar todos os serviços num único PostgreSQL quando o custo de memória pesar. A migração das tabelas atuais (`email_template`) para esse schema é da Issue #111, não desta spec. |
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
      EmailSendService.java        # busca template e remetente do cliente, grava email_send, publica
      ClientSender.java            # entidade (só leitura pelo serviço)
      ClientSenderRepository.java
      EmailSend.java               # entidade
      EmailSendRepository.java
      EmailQueueMessage.java       # record da mensagem com template
      EmailQueuePublishException.java  # → 503
      SenderNotConfiguredException.java  # → 409
  resources/
    db/migration-email-service/          # local definido pela Issue #111
      V<n>__create_client_sender_table.sql
      V<n+1>__create_email_send_table.sql
email-lambda/src/main/java/dev/leilaalgarve/jogoacoes/email/lambda/
  EmailMessage.java                # subject/body → senderAddress, templateName, templateData
  EmailSendHandler.java            # SendTemplatedEmail
scripts/set-email-sender.sh        # operações define o remetente de um cliente
```

Depende da Issue #111 ter movido as migrations atuais para `db/migration-email-service/` e
criado o schema do serviço; se ela ainda não tiver sido implementada, é feita antes da T005.

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
- **E-mail duplicado**: repetição do pedido pelo cliente e entrega repetida da fila podem gerar
  dois e-mails iguais. Aceito até a Etapa 4, que trata idempotência (`spec.md`, "Fora de
  escopo").
- **Remetente não verificado no SES**: o serviço só confere o formato. Se operações definir um
  endereço que não é identidade verificada no SES, o envio é aceito com `202` e falha na Lambda —
  visível só no log da Lambda até existir a consulta de estado do envio (Iteração 6).
- **E-mails do `app/` param até a migração** (ver decisão "`app/` enquanto não migra").
