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

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico [#150](https://github.com/lalgarve/jogo-acoes/issues/150).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Criar `app/src/test/resources/features/search_players.feature` com o texto do `plan.md` e os passos correspondentes (tabela de jogadores com participações confirmadas por tipo; massa de 25 jogadores `bulk.test`; chamadas a `POST /players/search` em JSON cru). Resolver como o cenário do jogador convive com o `Background` (`Rule` ou arquivo próprio). Rodar e confirmar que falham agora | — | | #150 |
| ~~T002~~ | Contrato: em `docs/openapi.yaml`, `POST /players/search` (`searchPlayers`, tag `player-directory`, `x-roles: [ADMINISTRATOR]`, corpo opcional, `200`/`400`/`401`/`403`); schemas `PlayerSearchRequest` (`competitionType` → `CompetitionType`; `emailDomain` com `minLength: 1` e `pattern: '^[^@\s]+$'`; `owner`; `page` `minimum: 0`, padrão 0; `size` `minimum: 1`, `maximum: 100`, padrão 20) e `PlayerPage` (`total`, `page`, `size`, `items: PlayerSummary[]`) | T001 | | #150 |
| ~~T003~~ | `ParticipationRepository`: estender o SELECT da 05-038 com os filtros (`exists` de participação `IN_COMPETITION` no tipo; `exists`/`not exists` de competição com `creator = u`; `lower(u.email) like concat('%@', lower(:domain))`; filtro ausente não restringe), `order by u.name, u.id` e paginação; mais o `count(u)` com os mesmos filtros. Se o JPQL com parâmetros nulos ficar ilegível, montar com `Specification`/Criteria e registrar a escolha no `plan.md`. Teste de repositório contra o PostgreSQL real: cada filtro sozinho, filtros combinados, `owner=false`, domínio com maiúsculas, `acme.com` não casa `sub.acme.com`, página além do fim com `total` correto | T002 | | #150 |
| ~~T004~~ | `PlayerDirectoryService.search(...)`: aplica os padrões (`page` 0, `size` 20, corpo ausente = sem filtros), chama as duas consultas e monta o resultado | T003 | | #150 |
| ~~T005~~ | `PlayerDirectoryController.searchPlayers` → `PlayerPage` | T004 | | #150 |
| ~~T006~~ | `CompetitionSecurityConfigContributor`: `POST /players/search` → `hasRole("ADMINISTRATOR")` | T005 | | #150 |
| ~~T007~~ | Rodar `search_players.feature` — confirmar verde | T001, T006 | | #150 |
| ~~T008~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os ramos de erro (`401`, `403`, e cada regra de `400`: `size` 0 e 500, `page` −1, domínio vazio, domínio com `@`, tipo fora do enum); para cada um, apontar o exemplo do `Scenario Outline` ou teste que o exercita. Registrar a lista aqui | T007 | | #150 |
| ~~T009~~ | Rodar a suíte completa (`mvn test`) — confirmar verde, incluindo `view_player_profile.feature` da 05-038 (o SELECT compartilhado não pode mudar as contagens) | T008 | | #150 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Registro da implementação (2026-10-12)

- **T001**: `search_players.feature` (17 cenários contando os exemplos, duas `Rule`s:
  administrador e acesso negado) e `SearchPlayersSteps`. Diferença necessária em relação ao
  texto do `plan.md`: o banco é compartilhado entre cenários e entre execuções, então os e-mails
  fixos (`ana@acme.com`) colidiriam na segunda execução, e filtros sem escopo (só tipo, só dono,
  sem filtro) devolvem também usuários de outros testes. Por isso: cada cenário usa domínios
  únicos ("main", "other", "bulk" são apelidos; "MAIN" é o mesmo domínio em maiúsculas); os
  passos "among this scenario's users, the result contains exactly ..." percorrem todas as
  páginas e olham só os usuários do cenário; "sem filtro" confere o `total` contra o número de
  usuários no banco; as competições do `Background` são criadas pelo administrador do cenário
  (é o que faz "owners only" devolver exatamente ele). Cenário novo para acesso anônimo.
  `UserMother.registeredPlayer(nome, e-mail)` novo. Antes da implementação, 16 dos 17 cenários
  falharam com `405` (`POST` caía em `/players/{userId}`, que só aceita `GET`); o anônimo já
  passava com `401`.
- **T002**: `POST /players/search` (`searchPlayers`) e schemas `PlayerSearchRequest`/`PlayerPage`
  no `docs/openapi.yaml`.
- **T003**: `ParticipationRepository.searchPlayerSummaries` — o `PLAYER_SUMMARY_SELECT` da 05-038
  mais `PLAYER_SEARCH_FILTERS` (compartilhado com o `countQuery`), `order by u.name, u.id`,
  paginação por `Pageable`. Filtros com JPQL de parâmetros nulos (não precisou de
  `Specification`). Domínio comparado por igualdade com a parte depois do `@` (sem `like`, então
  nenhum curinga). Achado: com o domínio nulo, o parâmetro chegava ao PostgreSQL sem tipo e
  `lower(...)` falhava como `lower(bytea)` → `cast(:emailDomain as String)` explícito.
  `PlayerSearchQueryIntegrationTest` (6 testes, PostgreSQL real, cada um com domínio próprio):
  sem filtro conta todos os usuários; tipo de competição só com entrada confirmada; domínio exato
  e sem diferenciar maiúsculas (subdomínio não casa); dono/não dono; ordem por nome e id com
  nomes repetidos e página além do fim vazia com `total` certo; itens com as mesmas contagens do
  resumo da 05-038.
- **T004–T006**: `PlayerDirectoryService.search`, `PlayerDirectoryController.searchPlayers`
  (corpo ausente = sem filtros, com os padrões do contrato), regra
  `POST /players/search` → `ADMINISTRATOR`. `docs/diagrams/modulos.md` atualizado.
- **T007**: `search_players.feature` verde (17 cenários). `ArchitectureTest`,
  `OpenApiRolesConsistencyTest`, `OpenApiRoutesConsistencyTest`, `RouteOwnershipTest` e
  `SchemaLayoutTest` verdes.
- **T008**: nenhuma classe de exceção criada nem handler novo. Ramos de erro: `401` → "Anonymous
  user tries to search players"; `403` → "Player tries to search players"; `400` por `size` 0 e
  500, `page` −1, domínio vazio e domínio com `@` (Bean Validation do corpo →
  `MethodArgumentNotValidException`, handler existente) e por tipo fora do enum
  (`HttpMessageNotReadableException`, tratamento padrão do Spring) → um exemplo cada no
  `Scenario Outline: Invalid search`.
- **T009**: `mvn -pl app test` — 261 testes (23 a mais: 17 cenários e 6 testes do SELECT),
  incluindo os 12 cenários de `view_player_profile.feature` da 05-038. As mesmas 38 falhas já
  registradas nas specs 05-037 e 05-038, todas porque o `email-service` não sobe neste ambiente;
  nenhuma nova.
