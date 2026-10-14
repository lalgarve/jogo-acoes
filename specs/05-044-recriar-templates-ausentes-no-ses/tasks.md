# Tasks: Recriar no SES os templates que só existem no banco do email-service

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** testes que reproduzem o bug primeiro, falhando pelo motivo esperado; depois as
correções.

Todas as tarefas abaixo são acompanhadas como checklist na Issue
[#144](https://github.com/lalgarve/jogo-acoes/issues/144).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `register_templates.feature`: `Rule` "email-service recreates on SES the templates SES lost" com o cenário de atualizar um template que o SES perdeu (passo `Given SES lost the template named "welcome"`, com `DeleteTemplate` direto no LocalStack). Rodar e confirmar que falha com 404 | — | [P] | #144 |
| ~~T002~~ | Mesma `Rule`: cenário de preview de um template que o SES perdeu. Confirmar que falha com 404 | T001 (passo compartilhado) | | #144 |
| ~~T003~~ | Mesma `Rule`: cenário de cadastrar `welcome` com `client-a__welcome` já no SES e sem linha no banco (passo `Given SES already has a template named "welcome" with no record`, com `CreateTemplate` direto). Confirmar que falha com 422 | T001 (passos compartilhados) | | #144 |
| ~~T004~~ | `SesTemplateReconcilerIntegrationTest` (ver `plan.md`, "Verificações", item 2): template ausente; 11 templates ausentes (mais de uma página de `ListTemplates`; registrar se o LocalStack pagina); template só no SES fica e gera `WARN`; SES inacessível na subida (encaminhador TCP fechado): o contexto sobe, e o template aparece no SES depois que o encaminhador abre. Confirmar que falham porque o SES continua sem os templates | — | [P] | #144 |
| ~~T005~~ | `SesUnavailableIntegrationTest` (ver `plan.md`, "Verificações", item 3): com o SES numa porta fechada, `POST /templates`, `PUT` e preview respondem `503` e nada é gravado. Confirmar que falha com `422` | — | [P] | #144 |
| ~~T006~~ | `SesTemplateClient.putTemplate`, `createIfAbsent` e `updateTemplate` com recriação; `TemplateService.create` passa a usar `putTemplate`. Confirmar T001 e T003 verdes | T001, T003 | | #144 |
| ~~T007~~ | `TemplateService.preview`: recria a partir da linha e renderiza de novo quando o SES não tem o template. Confirmar T002 verde | T002, T006 | | #144 |
| ~~T008~~ | `SesUnavailableException` (lançada pelo `SesTemplateClient` com `SdkClientException` ou 5xx do SES) e o `503` no `ApiExceptionHandler`. Confirmar T005 verde | T005, T006 | | #144 |
| ~~T009~~ | `SesTemplateReconciler`: começa no `ApplicationReadyEvent` pelo `TaskScheduler`, `findAll()` × `ListTemplates` paginado, `createIfAbsent` para os ausentes, `WARN` para os órfãos e para as falhas, nova rodada com espera de 1 s dobrando até 5 min (`email.ses.reconciliation.initial-delay`/`max-delay`), para depois da primeira rodada sem falha. Confirmar T004 verde | T004, T006, T008 | | #144 |
| ~~T010~~ | Documentação: `503` e descrições de `POST /templates`, `PUT /templates/{name}` e `POST /templates/{name}/preview` em `docs/openapi-email-service.yaml`; invariante novo em `specs/05-025-servico-email-templates/data-model.md` | T006, T007, T008 | [P] | #144 |
| ~~T011~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: `SesUnavailableException`) e os branches de exceção adicionados ou alterados (`AlreadyExistsException` em `putTemplate` e em `createIfAbsent`, `TemplateDoesNotExistException` em `updateTemplate`, a nova tentativa do preview, `SdkClientException`/5xx no `SesTemplateClient`, o handler do `503`, falha de `ListTemplates` e de `createIfAbsent` na reconciliação e a nova rodada); para cada um, apontar o teste que o exercita. O que não tiver teste é justificado em `plan.md` (já previsto: `SesException` genérica não 5xx com o SES no ar) e avisado no chat ao abrir a PR. Registrar a lista aqui | T006–T009 | | #144 |
| T012 | Rodar a suíte completa do `email-service` e `mvn -pl app -am verify` — confirmar verde | T010, T011 | | #144 |
| T013 | Verificação manual (Leila), comandos em "Roteiro da T013" abaixo: passos 1–4 da Issue #144 (`docker compose restart localstack` entre duas execuções da suíte do `app`); o `app` sobe na segunda sem `down -v` | T012 | | #144 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue #144, ou uma Issue própria quando grande o
  suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de tipo (`bug`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Registro da implementação (2026-10-14)

Infraestrutura no container da Claude: `db-email-service` e `localstack` do Compose; o
`function.zip` da Lambda construído no host (o `email-lambda-builder` não alcança o repositório
Maven de dentro do Docker) e o `config-server` rodando no host com o `config-repo/`. Linha de base
antes de qualquer mudança: `mvn -pl email-service -am verify`, 46 testes verdes (2 ignorados,
`@requires-real-ses`).

- **T001–T003**: `Rule` "email-service recreates on SES the templates SES lost" em
  `register_templates.feature`, com os passos `SES lost the template named`, `SES already has a
  template named ... with no record` e as conferências direto no SES (`GetTemplate`). Antes da
  correção: atualizar → `expected: 200 but was: 404`; preview → `expected: 200 but was: 404`;
  cadastrar com sobra no SES → `expected: 201 but was: 422`.
- **T004**: `SesTemplateReconcilerIntegrationTest` (4 casos, segundo contexto com
  `SpringApplicationBuilder`). Antes da correção os 4 falharam com `ConditionTimeoutException`: o
  SES continuou sem os templates. O LocalStack **não pagina** `ListTemplates` (12 templates
  voltam numa página só, com ou sem `--max-items`, conferido com `awslocal`); o caso de 11
  templates fica, e o laço por `NextToken` não é exercitado contra o LocalStack. O caso de SES fora
  do ar usa `TcpForwarder` (suporte de teste novo): porta fechada na subida, aberta para o
  LocalStack depois.
- **T005**: `SesUnavailableIntegrationTest` (3 casos). Antes da correção os 3 responderam `500`,
  e não `422` como a spec supunha: a `SdkClientException` do SDK não é `SesException` e não era
  traduzida.
- **T006–T009**: `SesTemplateClient.putTemplate`, `createIfAbsent`, `updateTemplate` com
  recriação, `listTemplateNames` e a tradução única de exceções do SDK; `TemplateService.create`
  com `putTemplate` e `preview` com recriação; `SesUnavailableException` e o `503` no
  `ApiExceptionHandler`; `SesTemplateReconciler` (`ApplicationReadyEvent`, `TaskScheduler`,
  `email.ses.reconciliation.initial-delay`/`max-delay`, padrões 1 s e 5 min) e
  `common/SchedulingConfiguration` (`@EnableScheduling`, usado também pela 05-046). T001–T005
  verdes.
- **T010**: `docs/openapi-email-service.yaml` com `503` em `POST /templates`,
  `PUT /templates/{name}` e preview, e as descrições da recriação; invariante novo em
  `specs/05-025-servico-email-templates/data-model.md`.
- **T011** — classes de exceção e branches:
  - `SesUnavailableException` (nova): lançada por `SesTemplateClient` com `SdkClientException`
    → `SesUnavailableIntegrationTest` (os 3 casos) e o caso de SES fora do ar de
    `SesTemplateReconcilerIntegrationTest`. Handler do `503` → `SesUnavailableIntegrationTest`.
  - `AlreadyExistsException` em `putTemplate` → cenário "Client registers a template whose name
    was left on SES without a record".
  - `AlreadyExistsException` em `createIfAbsent` → `SesTemplateClientIntegrationTest`.
  - `TemplateDoesNotExistException` em `updateTemplate` → cenário "Client updates a template SES
    no longer has".
  - Nova tentativa do preview → cenário "Client previews a template SES no longer has".
  - Falha de `ListTemplates` na reconciliação e a rodada seguinte → caso de SES fora do ar de
    `SesTemplateReconcilerIntegrationTest` (log `SES template reconciliation failed`).
  - Sem teste, justificados: `SesException` com 5xx ou *throttling* (o LocalStack não responde
    assim), `SdkException` que não é cliente nem serviço, `SesException` de rejeição (o LocalStack
    não valida a sintaxe, cenários `@requires-real-ses`) e falha de `createIfAbsent` dentro de uma
    rodada com `ListTemplates` bem-sucedido (precisaria o SES cair entre as duas chamadas).
- **T012** (parcial): `mvn -pl email-service -am verify`: 57 testes, 55 verdes, 2 ignorados
  (`@requires-real-ses`). A suíte do `app` rodou junto com a da 05-046 (registro na T015 de
  `specs/05-046-publicacao-na-fila-em-segundo-plano/tasks.md`): o `app` subiu e sincronizou os
  templates contra o `email-service` desta branch; as falhas são da entrega pela Lambda, que não
  roda no container da Claude.

## Roteiro da T013

A partir da raiz do repositório. Sobe tudo com as imagens desta branch (o `rm -rf` força a
Lambda a ser empacotada de novo):

```bash
rm -rf email-lambda/dist
docker compose up -d --wait db db-email-service localstack
./scripts/test-api-key.sh restore
docker compose up -d --build --wait email-service
./scripts/test-api-key.sh restore
```

Primeira execução da suíte do `app` (cadastra os templates no `email-service` e no SES):

```bash
mvn -pl app -am verify
```

Reiniciar só o LocalStack (o SES perde os templates; o banco do `email-service` continua com
eles):

```bash
docker compose restart localstack
docker compose exec localstack awslocal ses list-templates
```

Esperado: `list-templates` sem os templates `jogo-acoes__...`.

Segunda execução da suíte do `app`, sem `docker compose down -v`:

```bash
mvn -pl app -am verify
```

Esperado: o `app` sobe (sem `404` no `PUT` nem `409` no `POST` dos templates, os erros da Issue
#144).

Opcional, para ver a recriação na subida do `email-service` (ela só roda quando o serviço sobe):
repetir o `docker compose restart localstack` e então

```bash
docker compose restart email-service
docker compose logs email-service | grep -E "Recreated template|reconciliation failed"
docker compose exec localstack awslocal ses list-templates
```

Esperado: uma linha `Recreated template jogo-acoes__... on SES from the database` por template
e todos de volta no `list-templates`.

