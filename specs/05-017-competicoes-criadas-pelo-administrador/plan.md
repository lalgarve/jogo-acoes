# Plan: Administrador lista as competições que criou

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`Competition.creator` (`app/src/main/java/dev/leilaalgarve/jogoacoes/competition/Competition.java`)
já existe (`@ManyToOne`, `creator_id`, `nullable = false`) desde a criação da entidade — a coluna
sempre foi gravada (`CompetitionService.create` seta o criador), só nunca foi consultada de
volta. `CompetitionRepository` hoje só tem `findByTypeAndStatus`. `CompetitionViewService.
listMyCompetitions(Long userId)` monta os três grupos existentes a partir de uma única fonte
(`participationRepository.findByUser_Id`); acrescentar `created` é uma segunda consulta
independente, sem tocar a lógica dos três grupos já existentes.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde expor | Novo campo `created` em `MyCompetitions`/`GET /competitions/mine` | resolvida | Decidido em conversa — reaproveita o endpoint e a convenção "grupos sempre presentes" já estabelecida pela spec 05-004, em vez de uma rota nova só para administrador. |
| Consulta | `CompetitionRepository.findByCreator_Id(Long creatorId)` (novo método, Spring Data derivado — mesmo padrão de `findByTypeAndStatus`) | resolvida | Direto, sem JPQL customizado; `creator_id` já é uma FK indexada implicitamente (chave estrangeira). |
| Quais status entram em `created` | Todos (`AWAITING_INVITES`, `OPEN`, `CLOSED`) | resolvida | `created` não é sobre acompanhar participação (que é o que os outros três grupos distinguem por status) — é "toda competição que eu criei", ponto. Filtrar por status esconderia competições do próprio criador sem motivo. |
| Deduplicação entre grupos | Nenhuma — uma competição pode aparecer em `created` e em outro grupo ao mesmo tempo | resolvida | Não acontece em nenhum fluxo hoje (o criador nunca é participante da própria competição), então é uma decisão sem custo prático agora; simples e sem regra escondida. |
| Papel exigido | Nenhum específico — a consulta roda para qualquer `userId` chamando `GET /competitions/mine` (`x-roles` do endpoint não muda) | resolvida | Só administrador cria competição hoje (`x-roles: [ADMINISTRATOR]` em `POST /competitions`), então `created` já sai vazio para jogador sem precisar de uma checagem de papel a mais — mais simples que restringir e depois ter que destravar se um jogador algum dia puder criar. |

## Estrutura de módulos/pacotes

- `docs/openapi.yaml` (modificado) — `MyCompetitions` ganha `created: CompetitionSummary[]`;
  descrição do schema atualizada de "três grupos" para "quatro grupos".
- `app/src/main/java/dev/leilaalgarve/jogoacoes/competition/CompetitionRepository.java`
  (modificado) — `List<Competition> findByCreator_Id(Long creatorId)`.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/competition/CompetitionViewService.java`
  (modificado) — `listMyCompetitions` também popula `created` via `toSummary`, reaproveitando o
  helper privado já existente.
- `app/src/test/resources/features/view_my_competitions.feature` (modificado) — nova `Rule`:

  ```gherkin
  Rule: The administrator sees the competitions they created

    Background:
      Given the user is the system administrator
      And the user is logged into the system

    Scenario: Administrator views a competition they created, even without participating in it
      Given the administrator created a competition
      When they access their competitions list
      Then the system shows that competition under "created"
  ```

  `Given the user is the system administrator`/`the user is logged into the system` já existem
  em `common/steps/CommonSteps.java` (genéricos, reusados por várias features) — nada novo
  ali. Só um passo novo, em
  `competition/steps/ViewMyCompetitionsSteps.java`:
  `Given the administrator created a competition` (chama `CompetitionService.create` com
  `CompetitionMother.validPublicCompetition()`, autenticado como `world.getCurrentUser()` —
  mesmo padrão de `CreateCompetitionSteps`/`AuditLoggingIntegrationTest`). Também modificar,
  no mesmo arquivo: a regex de `the_system_shows_that_competition_under` (hoje só aceita
  `participating|participated in the past|invited, not confirmed`) para aceitar `created`
  também, o `bucketKey` (novo `case "created" -> "created"`) e a lista de buckets varridos
  em `the_system_shows_that_competition_under` (hoje `List.of("participating",
  "pastParticipations", "pendingConfirmation")`, precisa incluir `"created"`).
- Nenhuma migration nova — `creator_id` já existe na tabela `competition` desde sempre.

## Riscos e trade-offs

- **Campo aditivo no contrato**: qualquer cliente que já lê `MyCompetitions` (hoje só a própria
  suíte de testes, sem frontend real ainda) continua funcionando sem mudança —
  `OpenApiRoutesConsistencyTest`/`OpenApiRolesConsistencyTest` continuam passando (mesma rota,
  mesmo método, mesmos papéis — eles verificam rota+papel, não o formato do corpo da resposta),
  mas por isso mesmo nenhum teste automático força lembrar de atualizar a descrição do schema
  em `docs/openapi.yaml` — revisão manual no PR.
- **`findByCreator_Id` sem índice explícito**: `creator_id` é FK (`nullable = false`),
  Postgres não cria índice automático em FK — para o volume atual (dezenas de competições em
  teste, nenhum dado de produção real ainda) é irrelevante; motivo para não otimizar agora
  registrado aqui, não decidido por omissão.
