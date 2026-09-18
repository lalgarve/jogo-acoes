# Tasks: Identificação de dispositivo via User-Agent Client Hints + Swagger UI interativa

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (quais Client Hints ler, formato do rótulo, quando mandar `Accept-CH`,
como servir o Swagger UI) já estão resolvidas em `plan.md` — esta lista só quebra a
implementação em passos.

**Ordem: TDD + API-first.** Os passos Cucumber vêm primeiro, contra o comportamento esperado
descrito em `plan.md` — chamada HTTP direta (RestAssured), lendo o rótulo gravado via
`LoginSessionRepository` (não há endpoint que o exponha ainda; isso só chega com a spec 05-010),
e devem rodar e falhar de forma previsível (o rótulo gravado hoje é sempre o `User-Agent` cru)
antes de qualquer outra tarefa. O contrato OpenAPI (os headers novos) vem em seguida,
formalizando o que os passos já esperavam, e só depois disso a implementação.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#59](https://github.com/lalgarve/jogo-acoes/issues/59) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Passos Cucumber para `device_identification.feature` — três cenários (Client Hints completos → rótulo rico; só `User-Agent` → fallback; nenhum dos dois → `"unknown-device"`); chamada direta a `POST /login-requests` + `GET /login-links/{token}` com os headers variando por cenário, lendo o `device_id` gravado via `LoginSessionRepository` (leitura direta em teste, não pela API); rodar e confirmar que falha agora (o rótulo gravado hoje é sempre o `User-Agent` cru, nunca o formato "rico") | — | [P] | #59 |
| T002 | Contrato: em `docs/openapi.yaml`, adicionar `Sec-CH-UA`, `Sec-CH-UA-Platform`, `Sec-CH-UA-Platform-Version`, `Sec-CH-UA-Mobile` como `parameters` de header opcionais em `consumeLoginLink` e `completeRegistration` | T001 | | #59 |
| T003 | Criar `login/DeviceLabelResolver.java` (função pura) + `DeviceLabelResolverTest` dedicado: Client Hints completos → rótulo formatado; só `Sec-CH-UA-Platform` presente → ainda monta algo; só `User-Agent` → fallback; nenhum → `"unknown-device"`; parsing de `Sec-CH-UA` ignora a entrada "greased"/fake brand obrigatória pelo spec | — | [P] | #59 |
| T004 | Atualizar `LoginLinkSessionService.deviceLabel()` para usar `DeviceLabelResolver` em vez de ler `User-Agent` direto | T002, T003 | | #59 |
| T005 | Criar `login/AcceptChFilter.java` (`OncePerRequestFilter`) que adiciona o header `Accept-CH` em toda resposta; registrar no `SecurityFilterChain` | — | [P] | #59 |
| T006 | Rodar `device_identification.feature` de novo — confirmar verde | T001, T004 | | #59 |
| T007 | Adicionar `springdoc-openapi-starter-webmvc-ui` ao `app/pom.xml`; configurar `springdoc.api-docs.enabled: false` e `springdoc.swagger-ui.url` apontando pro `docs/openapi.yaml` (servido a partir de `src/main/resources/static/` ou mecanismo equivalente); confirmar manualmente que o Swagger UI sobe (considerando `context-path: /api`) e mostra os headers da T002 como campo preenchível | — | [P] | #59 |
| T008 | Corrigir a nota desatualizada sobre `LOGIN_SESSION.device_id` em `docs/diagrams/der.md` (hoje afirma que participa da checagem de "mesmo dispositivo"; não participa — só rótulo de exibição) | — | [P] | #59 |
| T009 | Rodar a suíte completa (`mvn test`) — confirmar verde, nenhum cenário/teste existente alterado além dos novos | T005, T006, T007, T008 | | #59 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
