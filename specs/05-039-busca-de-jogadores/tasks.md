# Tasks: Busca de jogadores com filtros e paginação

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (`POST /players/search`, paginação por página/tamanho com máximo 100,
filtros como `exists` correlacionados, SELECT da página reaproveitado da 05-038 mais um `count`
separado, validação gerada do contrato) já estão resolvidas em `spec.md`/`plan.md` — esta lista
só quebra a implementação em passos.

**Depende da spec [05-038](../05-038-consulta-de-jogador/tasks.md) implementada**: o schema
`PlayerSummary`, a tag `player-directory`, `PlayerDirectoryService`/`PlayerDirectoryController`,
o record de projeção, o SELECT em `ParticipationRepository` e os índices vêm de lá.

**Ordem: TDD + API-first**: cenários e passos primeiro, falhando de forma previsível; depois
contrato; depois implementação.

Nenhuma Issue criada ainda — a coluna "Issue" é preenchida quando a Issue-épico existir.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar `app/src/test/resources/features/search_players.feature` com o texto do `plan.md` e os passos correspondentes (tabela de jogadores com participações confirmadas por tipo; massa de 25 jogadores `bulk.test`; chamadas a `POST /players/search` em JSON cru). Resolver como o cenário do jogador convive com o `Background` (`Rule` ou arquivo próprio). Rodar e confirmar que falham agora | — | | — |
| T002 | Contrato: em `docs/openapi.yaml`, `POST /players/search` (`searchPlayers`, tag `player-directory`, `x-roles: [ADMINISTRATOR]`, corpo opcional, `200`/`400`/`401`/`403`); schemas `PlayerSearchRequest` (`competitionType` → `CompetitionType`; `emailDomain` com `minLength: 1` e `pattern: '^[^@\s]+$'`; `owner`; `page` `minimum: 0`, padrão 0; `size` `minimum: 1`, `maximum: 100`, padrão 20) e `PlayerPage` (`total`, `page`, `size`, `items: PlayerSummary[]`) | T001 | | — |
| T003 | `ParticipationRepository`: estender o SELECT da 05-038 com os filtros (`exists` de participação `IN_COMPETITION` no tipo; `exists`/`not exists` de competição com `creator = u`; `lower(u.email) like concat('%@', lower(:domain))`; filtro ausente não restringe), `order by u.name, u.id` e paginação; mais o `count(u)` com os mesmos filtros. Se o JPQL com parâmetros nulos ficar ilegível, montar com `Specification`/Criteria e registrar a escolha no `plan.md`. Teste de repositório contra o PostgreSQL real: cada filtro sozinho, filtros combinados, `owner=false`, domínio com maiúsculas, `acme.com` não casa `sub.acme.com`, página além do fim com `total` correto | T002 | | — |
| T004 | `PlayerDirectoryService.search(...)`: aplica os padrões (`page` 0, `size` 20, corpo ausente = sem filtros), chama as duas consultas e monta o resultado | T003 | | — |
| T005 | `PlayerDirectoryController.searchPlayers` → `PlayerPage` | T004 | | — |
| T006 | `CompetitionSecurityConfigContributor`: `POST /players/search` → `hasRole("ADMINISTRATOR")` | T005 | | — |
| T007 | Rodar `search_players.feature` — confirmar verde | T001, T006 | | — |
| T008 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os ramos de erro (`401`, `403`, e cada regra de `400`: `size` 0 e 500, `page` −1, domínio vazio, domínio com `@`, tipo fora do enum); para cada um, apontar o exemplo do `Scenario Outline` ou teste que o exercita. Registrar a lista aqui | T007 | | — |
| T009 | Rodar a suíte completa (`mvn test`) — confirmar verde, incluindo `view_player_profile.feature` da 05-038 (o SELECT compartilhado não pode mudar as contagens) | T008 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.
