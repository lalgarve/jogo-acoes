# Tasks: email-service recusa template com nome vazio ou em branco

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** os testes que reproduzem o bug vêm primeiro, falhando pelo motivo esperado; depois a
correção.

Todas as tarefas abaixo são acompanhadas como checklist na Issue
[#133](https://github.com/lalgarve/jogo-acoes/issues/133).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `register_templates.feature`, regra "A client registers a new template": cenário `Client registers a template with an empty name` (nome `""`) e passos novos `Then the system rejects the registration because the name is blank` (400, corpo `Error`, mensagem começando por `name`) e `And no template with that name is created` (`GET /templates` não lista o nome; o SES do LocalStack não tem `<cliente>__`). Rodar e confirmar que falha hoje com 201 | — | [P] | #133 |
| ~~T002~~ | Mesmo arquivo: cenário `Client registers a template with a whitespace-only name` (nome `" "`), com os passos de T001. Rodar e confirmar que falha hoje (201, ou 422 se o SES recusar o nome; registrar qual dos dois) | T001 | | #133 |
| ~~T003~~ | `docs/openapi-email-service.yaml`: `EmailTemplateCreateRequest.name` ganha `minLength: 1` e `pattern: '^[\s\S]*\S[\s\S]*$'`, com `description` dizendo que o nome não pode ser vazio nem só espaço em branco. Conferir no DTO gerado (`target/generated-sources`) que vieram `@Size(min = 1)` e `@Pattern`. Confirmar T001 e T002 verdes | T001, T002 | | #133 |
| ~~T004~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os caminhos de erro novos (400 por `MethodArgumentNotValidException` para nome vazio e para nome em branco); apontar T001/T002 como os testes que os exercitam. Registrar abaixo | T003 | | #133 |
| ~~T005~~ | Rodar a suíte do email-service (`mvn -pl email-service verify`) e a do `app` (`mvn -pl app -am verify`, que regenera o cliente Feign do mesmo contrato) e confirmar verde | T003 | | #133 |
| ~~T006~~ | Verificação manual (Leila): rodar o roteiro "Verificação manual (T006)" abaixo e conferir os resultados esperados | T005 | | #133 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- A Issue #133 leva o label `iteration-5`, além do label de tipo (`bug`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Verificação manual (T006)

Na raiz do repositório, no WSL, com o branch da PR #168 (ou o `master` depois do merge). Só o
`email-service` é necessário; `--build` garante que a imagem tem a correção.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose up -d --wait --build db-email-service localstack
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
API_KEY=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU
```

1. Nome vazio:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"name":"","subject":"s","body":"b"}'
   ```
   Esperado:
   ```
   {"message":"name must match \"^[\\s\\S]*\\S[\\s\\S]*$\"; name size must be between 1 and 2147483647"} [400]
   ```
2. Nome só com espaço:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"name":" ","subject":"s","body":"b"}'
   ```
   Esperado:
   ```
   {"message":"name must match \"^[\\s\\S]*\\S[\\s\\S]*$\""} [400]
   ```
3. Nenhum dos dois foi criado:
   ```bash
   curl -s -H "X-API-Key: $API_KEY" http://localhost:8082/api/templates | grep -o '"name":"[^"]*"'
   ```
   Esperado: uma linha por template, nenhuma igual a `"name":""` ou `"name":" "`. Num volume
   novo, sem o `app` ter subido, a saída é vazia; com o `app`, aparecem os templates dele
   (`"name":"login-link"`, `"name":"invite"`, ...).

Para derrubar: `docker compose down -v`.

## Registro da implementação (2026-10-14)

- **T001/T002**: dois cenários novos em `register_templates.feature` ("Client registers a
  template with an empty name" e "... with a whitespace-only name") e os passos
  `the system rejects the registration because the name is blank` e
  `no template with that name is created` em `RegisterTemplatesSteps` (confere o banco,
  `GET /templates` e a lista de templates do SES no LocalStack). Antes da correção os dois
  falharam com `expected: 400 but was: 201`: o nome só com espaços também era criado, sem 422 do
  SES.
- **T003**: `EmailTemplateCreateRequest.name` com `minLength: 1` e
  `pattern: '^[\s\S]*\S[\s\S]*$'`. O DTO gerado ficou com
  `@NotNull @Pattern(regexp = "^[\\s\\S]*\\S[\\s\\S]*$") @Size(min = 1)`. Os dois cenários
  passaram. Conferência à parte contra o email-service rodando (porta 8082, perfil `docker`):
  `""` → 400 `name must match "^[\s\S]*\S[\s\S]*$"; name size must be between 1 and
  2147483647`; `" "` → 400 `name must match ...`; `null` → 400 `name must not be null`; nome
  normal → 201.
- **T004**: nenhuma classe de exceção criada. Caminhos de erro novos: 400 por
  `MethodArgumentNotValidException` (handler já existente) para nome vazio e para nome só com
  espaços, exercitados pelos cenários de T001 e T002.
- **T005**: `mvn -pl email-service verify` com o perfil `docker`: 48 testes, 46 verdes e 2
  pulados (`@requires-real-ses`, como antes). `mvn -pl app -am verify`: 278 testes, 273 verdes.
  Os 5 que falham são `EmailServiceClientIntegrationTest.sendingRecordsEmailServiceIdAndReachesSesWithTheTemplate`
  ("no e-mail ... reached SES on LocalStack"): no container da Claude o LocalStack não consegue
  baixar a imagem de runtime da Lambda, então a `EmailLambda` fica no estado `Failed` e nenhum
  e-mail chega ao SES. É a mesma limitação registrada na 05-041 (T010) e não tem relação com esta
  mudança. Fechada pelo CI da PR #168 (job `test`, run 38518538056, commit `6b129cb`), que sobe o
  Compose completo e rodou verde as suítes do `app` e do email-service.
- **Roteiro da T006** (2026-10-14): os três `curl` foram rodados contra o email-service com a
  correção (Postgres e LocalStack do Compose, email-service com `java -jar` no host na porta
  8082) e deram as saídas esperadas. A parte de subida (`docker compose up --build`) não rodou no
  container da Claude, que não constrói as imagens (Maven dentro do Docker sem acesso ao
  repositório); é a mesma sequência do caderno de testes.
- **T006**: verificação manual feita pela Leila em 2026-10-14, com o roteiro acima: os testes
  passaram.
