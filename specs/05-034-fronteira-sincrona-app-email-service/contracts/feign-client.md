# Contrato de interface: `app` como cliente do `email-service`

Escrito a partir de `templates/contracts-template.md`. O contrato HTTP em si é
[`docs/openapi-email-service.yaml`](../../../docs/openapi-email-service.yaml) — este arquivo não
o repete. Ele registra **como o `app` consome** esse contrato: o que chama, com que
configuração, o que repete e como traduz cada resposta.

## Cliente

| Item | Valor |
|---|---|
| Biblioteca | Spring Cloud OpenFeign (`spring-cloud-dependencies` 2025.1.3) |
| Interface | Gerada de `docs/openapi-email-service.yaml` pelo `openapi-generator-maven-plugin` (gerador `spring`, `library: spring-cloud`), pacote `dev.leilaalgarve.jogoacoes.email.client.api` |
| Único usuário da interface | `EmailServiceGateway` (regra no `ArchitectureTest`) |
| Ativação | Só com `email-service.base-url` definido (variável `EMAIL_SERVICE_BASE_URL`); sem ela, nenhum bean do cliente existe |
| URL | `email-service.base-url`, já com o prefixo `/api` do contrato (ex.: `http://email-service:8080/api`) |
| Autenticação | Header `X-API-Key` com `email-service.api-key` (variável `EMAIL_SERVICE_API_KEY`), posto por um `RequestInterceptor`; o `app` não sobe se a URL estiver definida e a chave não |
| Tempo limite | `connect-timeout` 2 s, `read-timeout` 5 s (`spring.cloud.openfeign.client.config.default`) |

## Operações usadas

| Operação | Quando | Idempotente? | Repetida pelo `app`? |
|---|---|---|---|
| `GET /templates/{name}` | Conferir um template | sim | sim |
| `PUT /templates/{name}` | Sincronização ao subir (primeira tentativa do upsert) | sim | sim |
| `POST /templates` | Sincronização, quando o `PUT` responde `404` | não (`409` na segunda vez) | não; um `409` vira um novo `PUT` |
| `POST /templates/{name}/preview` | Pré-visualização | sim (não muda estado) | sim |
| `POST /emails` | Envio (spec 05-031, a implementar) | não, até a Etapa 4 | **não** |

"Repetida" quer dizer: até 3 tentativas, 200 ms entre elas, só quando o `email-service` não foi
alcançado ou respondeu `5xx`.

## Sincronização dos templates (upsert)

Ao subir (`ApplicationReadyEvent`), para cada um dos 5 templates (`invite`,
`registration-link`, `login-link`, `login-link-invite`, `login-link-request`):

1. `PUT /templates/{name}` com assunto e corpo.
2. Se `404`: `POST /templates`.
3. Se esse `POST` der `409` (outra instância criou no meio): `PUT` de novo.

Rodar duas vezes, ou duas instâncias ao mesmo tempo, termina no mesmo estado. Qualquer falha
impede o `app` de ficar pronto.

As fontes ficam em `app/src/main/resources/email-templates/`: `layout.html` (cabeçalho e rodapé
comuns, com o marcador `{{!-- content --}}`), `<name>.html` (o conteúdo colado no marcador) e
`<name>.subject.txt`. As variáveis (`{{name}}`, `{{competitionName}}`, `{{link}}`) ficam para o
SES substituir no envio.

## Tradução de respostas

| Resposta do `email-service` | O que o `app` vê |
|---|---|
| `2xx` | Tipos do próprio `app` (`EmailServiceTemplate`, `TemplatePreview`) |
| `404` em `GET`/`PUT` | `Optional.empty()` / "não existe" (usado pelo upsert) |
| `409` em `POST /templates` | Novo `PUT` (upsert) |
| `401` | `EmailServiceAuthenticationException` — erro de configuração, nunca repetido; a mensagem não contém a chave |
| `400`, `422` e outros `4xx` | `EmailServiceRejectedException`, com a mensagem do `email-service` |
| Sem conexão, tempo esgotado ou `5xx` | `EmailServiceUnavailableException` (depois das tentativas, quando a operação é repetível) |

Nenhuma classe do `email-service` nem `FeignException` passa do `EmailServiceGateway` para o
resto do `app`.
