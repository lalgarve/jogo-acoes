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
| ~~T013~~ | Verificação manual (Leila), rodar o roteiro "Roteiro da T013" abaixo e conferir os resultados esperados | T012 | | #144 |

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

- **Roteiro da T013** (2026-10-14, reescrito conforme a seção "Verificação manual: comandos
  prontos para copiar" do constitution): os passos 2, 4 e 5 foram rodados contra o
  `email-service` desta branch (Postgres e LocalStack do Compose, `email-service` com `java -jar`
  no host) e as saídas esperadas são as obtidas. Os passos 1 e 3 (o `app` em container) e a
  subida com `--build` não rodaram no container da Claude, que não constrói as imagens.

## Roteiro da T013

Na raiz do repositório, no WSL, com o branch `claude/project-thread-kx436t` (ou o `master` depois
do merge). Começa de volume limpo; `--build` garante que as imagens têm a correção.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
docker compose up -d --wait --build db db-email-service localstack
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
docker compose up -d --wait --build app
API_KEY=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU
```

1. O `app` subiu e cadastrou os templates dele no SES:
   ```bash
   docker compose logs app | grep -c "Started JogoAcoesApplication"
   docker compose exec localstack awslocal ses list-templates --query 'TemplatesMetadata[].Name' --output text
   ```
   Esperado: `1` e, na linha seguinte, os nomes `jogo-acoes__invite`, `jogo-acoes__login-link`,
   ... separados por tabulação.

2. Reiniciar só o LocalStack. O SES perde os templates; o banco do `email-service` continua com
   eles:
   ```bash
   docker compose restart localstack
   docker compose exec localstack awslocal ses list-templates --query 'TemplatesMetadata[].Name' --output text
   ```
   Esperado: uma linha vazia (nenhum template no SES).

3. Reiniciar o `app`, que sincroniza os templates de novo (o passo que falhava na Issue #144 com
   `PUT` 404 e `POST` 409):
   ```bash
   docker compose restart app
   sleep 30
   docker compose logs app --since 2m | grep -E "Started JogoAcoesApplication|answered 409"
   docker compose exec localstack awslocal ses list-templates --query 'TemplatesMetadata[].Name' --output text
   ```
   Esperado: uma linha com `Started JogoAcoesApplication` e nenhuma com `answered 409`; os
   templates `jogo-acoes__...` de volta no SES. Se ainda não aparecer o `Started`, esperar mais
   uns segundos e repetir o `logs`.

4. Atualizar e pré-visualizar um template que o SES perdeu, direto no `email-service`:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"name":"roteiro-t013","subject":"Roteiro T013","body":"<p>Oi {{name}}</p>"}'
   docker compose exec localstack awslocal ses delete-template --template-name jogo-acoes__roteiro-t013
   curl -s -w ' [%{http_code}]\n' -X PUT http://localhost:8082/api/templates/roteiro-t013 \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"subject":"Roteiro T013 v2","body":"<p>Oi {{name}}</p>"}'
   docker compose exec localstack awslocal ses delete-template --template-name jogo-acoes__roteiro-t013
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates/roteiro-t013/preview \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"variables":{"name":"Ada"}}'
   ```
   Esperado (as datas mudam):
   ```
   {"body":"<p>Oi {{name}}</p>","createdAt":"...","name":"roteiro-t013","subject":"Roteiro T013","updatedAt":"..."} [201]
   {"body":"<p>Oi {{name}}</p>","createdAt":"...","name":"roteiro-t013","subject":"Roteiro T013 v2","updatedAt":"..."} [200]
   {"body":"<p>Oi Ada</p>","subject":"Roteiro T013 v2"} [200]
   ```
   Antes da correção, o `PUT` e o preview respondiam `404`.

5. Recriação na subida do `email-service`:
   ```bash
   docker compose restart localstack
   docker compose restart email-service
   sleep 20
   docker compose logs email-service --since 1m | grep "Recreated template"
   docker compose exec localstack awslocal ses list-templates --query 'TemplatesMetadata[].Name' --output text
   ```
   Esperado: uma linha `... SesTemplateReconciler : Recreated template jogo-acoes__<nome> on SES
   from the database` para cada template (os do `app` e o `roteiro-t013`), e todos eles no
   `list-templates`. Se aparecer `SES template reconciliation failed, trying again in ...`, o
   LocalStack ainda estava subindo: esperar uns segundos e repetir os dois últimos comandos.

Para derrubar: `docker compose down -v`.

## Verificação manual no WSL (2026-10-22)

Feita pela Leila com o roteiro único [`docs/verificacao-manual-wsl.md`](../../docs/verificacao-manual-wsl.md), Bloco 1, passo 9, depois dos passos que criam templates de outros roteiros.

| Item | Resultado |
|---|---|
| 1. `app` de pé e templates no SES | `1`; os 5 templates do `app` e os 4 dos passos anteriores |
| 2. Reiniciar o LocalStack | `list-templates` vazio |
| 3. Reiniciar o `app` | `Started JogoAcoesApplication`, nenhum `answered 409`; os 5 templates do `app` de volta no SES |
| 4. Atualizar e pré-visualizar um template que o SES perdeu | `201`, `200` com `Roteiro T013 v2` e `{"body":"<p>Oi Ada</p>","subject":"Roteiro T013 v2"} [200]` |
| 5. Recriação na subida do `email-service` | 10 linhas `SesTemplateReconciler : Recreated template jogo-acoes__<nome> on SES from the database`, os 10 templates no `list-templates` |
