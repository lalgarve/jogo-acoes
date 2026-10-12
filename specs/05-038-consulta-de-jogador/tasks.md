# Tasks: Consulta de jogador (resumo por id e por e-mail)

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (rotas `/players/...`, tag `player-directory`, SELECT único com
subconsultas correlacionadas em `ParticipationRepository`, índices, contagens só de
`IN_COMPETITION`, `lower(...)` no e-mail) já estão resolvidas em `spec.md`/`plan.md` — esta
lista só quebra a implementação em passos.

**Ordem: TDD + API-first**: cenários e passos primeiro, falhando de forma previsível (`404` — os
caminhos ainda não existem); depois contrato; depois implementação.

**Base para a spec 05-039**: o SELECT criado aqui (T005) é o mesmo que a busca estende com
filtros e paginação. Implementar esta spec antes da 05-039.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico [#149](https://github.com/lalgarve/jogo-acoes/issues/149).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Criar `app/src/test/resources/features/view_player_profile.feature` com o texto do `plan.md` e os passos correspondentes (preparação de competições públicas/privadas, participações confirmadas e pendentes, competições criadas pelo administrador; chamadas a `GET /players/{userId}` e `GET /players?email=` em JSON cru). Rodar e confirmar que falham agora | — | | #149 |
| ~~T002~~ | Contrato: em `docs/openapi.yaml`, tag nova `player-directory` no fim da lista de tags; `GET /players/{userId}` (`getPlayer`, `x-roles: [PLAYER, ADMINISTRATOR]`, `200`/`401`/`404`); `GET /players` com query `email` obrigatório e `format: email` (`findPlayerByEmail`, `x-roles: [ADMINISTRATOR]`, `200`/`400`/`401`/`403`/`404`); schema `PlayerSummary` (`userId`, `name`, `email`, `ownedCount`, `publicCount`, `privateCount`). Caminhos na posição certa da ordem do arquivo (por tag, depois por caminho) | T001 | | #149 |
| ~~T003~~ | Migration Flyway nova em `db/migration-jogo-acoes/` (próxima versão livre, hoje `V9`): `CREATE INDEX` em `participation (user_id)` e `competition (creator_id)`; atualizar `docs/diagrams/der.md` com os índices | — | [P] | #149 |
| ~~T004~~ | Record de projeção do resumo no módulo `competition` (`userId`, `name`, `email`, `ownedCount`, `publicCount`, `privateCount`) | T002 | [P] | #149 |
| ~~T005~~ | `ParticipationRepository`: o SELECT JPQL único a partir de `User`, com as três contagens como subconsultas correlacionadas (participações `IN_COMPETITION` por tipo; competições com `creator = u`), em duas variantes de filtro — `u.id = :id` e `lower(u.email) = lower(:email)` — projetando no record de T004. Teste de repositório contra o PostgreSQL real cobrindo: participação pendente não conta, competição encerrada conta, usuário sem nada devolve zeros, e-mail com maiúsculas diferentes encontra o usuário | T003, T004 | | #149 |
| ~~T006~~ | `competition/PlayerDirectoryService`: `getPlayer(userId)` aplica a regra "jogador só vê o próprio" (outro id → mesmo `Optional.empty()` de inexistente, usando `CurrentUserService`) e `findByEmail(email)` | T005 | | #149 |
| ~~T007~~ | `competition/PlayerDirectoryController` implementando `PlayerDirectoryApi` (gerada): mapeia o record para `PlayerSummary`; `Optional` vazio → `404` | T006 | | #149 |
| ~~T008~~ | `CompetitionSecurityConfigContributor`: `GET /players` (sem id) → `hasRole("ADMINISTRATOR")`; `/players/*` → autenticado | T007 | | #149 |
| ~~T009~~ | Acrescentar o caso "repositório de `competition` com JPQL que referencia a entidade `User`" à seção correspondente de `docs/diagrams/modulos.md`, e o controller/serviço novos ao diagrama de módulos | T007 | [P] | #149 |
| ~~T010~~ | Rodar `view_player_profile.feature` — confirmar verde | T001, T008 | | #149 |
| ~~T011~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os ramos de erro (`401`, `403`, `404` por inexistente, `404` por ser de outro jogador, `400` por e-mail malformado ou ausente); para cada um, apontar o cenário ou teste que o exercita. Registrar a lista aqui | T010 | | #149 |
| ~~T012~~ | Rodar a suíte completa (`mvn test`) — confirmar verde, incluindo `ArchitectureTest` (regra da spec 05-029) | T011 | | #149 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Registro da implementação (2026-10-12)

- **T001**: `view_player_profile.feature` (12 cenários, três `Rule`s: jogador, administrador,
  anônimo) e `ViewPlayerProfileSteps`. Diferenças em relação ao texto do `plan.md`, todas para
  reaproveitar passos existentes ou por causa do banco compartilhado entre cenários: "the user is
  the system administrator" em vez de "the user is the administrator"; e-mails únicos
  (`TestEmails`) em vez de `ana@example.com`/`nobody@example.com` fixos, e a busca por e-mail
  usa o e-mail do jogador em maiúsculas (cobre a comparação sem diferenciar maiúsculas); cenário
  novo "Administrator looks up a player without giving an e-mail" (o `spec.md` pede `400` para
  e-mail ausente, o `plan.md` não tinha cenário). Passos genéricos novos em `CommonSteps`: "the
  system denies access" e "the system rejects the request as invalid" (a 05-039 também usa).
  `CompetitionFixtures.createdBy(...)` novo, porque as outras fábricas sempre criam um
  administrador novo. Antes da implementação, 7 dos 11 cenários falharam com `404` (caminhos
  inexistentes); os outros 4 esperavam `404`/`401` e passavam por coincidência.
- **T002**: tag `player-directory`, `GET /players` (`findPlayerByEmail`), `GET /players/{userId}`
  (`getPlayer`) e schema `PlayerSummary` no `docs/openapi.yaml`.
- **T003**: `V9__index_participation_user_and_competition_creator.sql`; nota no
  `docs/diagrams/der.md`. Índices conferidos no schema `jogo_acoes`.
- **T004/T005**: record `PlayerSummaryView`; `ParticipationRepository.findPlayerSummaryById` e
  `findPlayerSummaryByEmail`, que compartilham o mesmo texto de SELECT
  (`PLAYER_SUMMARY_SELECT`). `PlayerSummaryQueryIntegrationTest` (5 testes, PostgreSQL real):
  usuário sem nada tem zeros; só `IN_COMPETITION` conta, inclusive em competição encerrada, e
  `LINK_CLICKED`/`EMAIL_NOT_SENT`/`EMAIL_SENT` não; competições criadas contam e linhas de outro
  usuário não entram; e-mail em maiúsculas encontra o usuário; id/e-mail inexistentes não acham
  nada.
- **T006–T008**: `PlayerDirectoryService`, `PlayerDirectoryController`, regra
  `GET /players` → `ADMINISTRATOR` em `CompetitionSecurityConfigContributor`. Achado na
  implementação: o e-mail malformado lança `ConstraintViolationException` (validação gerada a
  partir de `format: email` no parâmetro), que o `ApiExceptionHandler` não tratava e virava
  `500`. Novo `@ExceptionHandler(ConstraintViolationException.class)` → `400` com mensagem
  única "Invalid request" (sem distinguir o campo, para não deixar um ramo que nenhuma operação
  alcança hoje).
- **T009**: `docs/diagrams/modulos.md` com as classes novas e um parágrafo sobre a consulta JPQL
  que parte da entidade `User`.
- **T010**: `view_player_profile.feature` verde (12 cenários). `ArchitectureTest`,
  `OpenApiRolesConsistencyTest`, `OpenApiRoutesConsistencyTest`, `RouteOwnershipTest` e
  `SchemaLayoutTest` verdes.
- **T011**: nenhuma classe de exceção criada. Ramos de erro: `401` sem sessão → "Anonymous user
  tries to view a profile"; `403` jogador buscando por e-mail → "Player tries to look up a player
  by e-mail"; `404` id inexistente → "Administrator views a profile that does not exist"; `404`
  perfil de outro jogador → "Player tries to view another player's profile"; `404` e-mail sem
  usuário → "Administrator looks up an e-mail that belongs to no player"; `400` e-mail malformado
  (handler novo de `ConstraintViolationException`) → "Administrator looks up a malformed
  e-mail"; `400` e-mail ausente (tratamento padrão do Spring) → "Administrator looks up a player
  without giving an e-mail".
- **T012**: `mvn -pl app test` — 238 testes (17 a mais que antes: 12 cenários e 5 testes do
  SELECT). As mesmas 38 falhas já registradas na spec 05-037, todas porque o `email-service` não
  sobe neste ambiente; nenhuma nova.
