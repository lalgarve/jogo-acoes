# Tasks: Administrador lista as competições que criou

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Testes de verificação primeiro.** T001 nasce vermelho para provar que o novo grupo `created`
é exigido pela especificação de comportamento.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `app/src/test/resources/features/view_my_competitions.feature`: adicionar a nova `Rule` e cenário do administrador vendo competição criada no grupo `created`; executar a suíte relacionada e registrar falha inicial | — | [P] | |
| ~~T002~~ | `docs/openapi.yaml`: adicionar `created` em `MyCompetitions` como `CompetitionSummary[]` sempre presente e atualizar a descrição do schema para quatro grupos | — | [P] | |
| ~~T003~~ | `CompetitionRepository`: criar `findByCreator_Id(Long creatorId)` para listar competições criadas pelo usuário logado | — | [P] | |
| ~~T004~~ | `CompetitionViewService.listMyCompetitions`: consultar `findByCreator_Id`, mapear para `CompetitionSummary` e preencher o novo bucket `created` sem deduplicar com os outros grupos | T003 | | |
| ~~T005~~ | `competition/steps/ViewMyCompetitionsSteps.java`: adicionar o passo `Given the administrator created a competition`; aceitar `created` no mapeamento do passo `Then the system shows that competition under` e incluir o bucket novo na verificação | T001, T004 | | |
| ~~T006~~ | Executar validação da feature e contrato (`view_my_competitions.feature`, testes de consistência OpenAPI e suíte relevante de `app`) com perfil de teste já usado pelo projeto | T002, T004, T005 | | |
| ~~T007~~ | Revisão final: garantir que `created` aparece de forma consistente em contrato, serviço e passos de teste, sem alterar comportamento dos três grupos existentes | T006 | | |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`/`test`/`docs`, conforme o caso).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Conferência contra o `master` (2026-10-06)

As tasks foram implementadas nos commits `4431ecb` e `d12f9c5` (PR #127), mas a tabela não foi
marcada naquela PR. Marcadas agora, depois de conferir o código e rodar a verificação abaixo.

- **T001**: regra "The administrator sees the competitions they created" em
  `view_my_competitions.feature`. Falha inicial reproduzida nesta conferência: sem o laço de
  `findByCreator_Id` em `CompetitionViewService`, o cenário falha com
  `[competition present under 'created'] expected: true`.
- **T002**: `created` em `MyCompetitions` no `docs/openapi.yaml`, com a descrição falando em
  quatro grupos.
- **T003/T004**: `CompetitionRepository.findByCreator_Id` e o preenchimento do grupo `created`
  em `CompetitionViewService.listMyCompetitions`, sem deduplicar com os outros grupos.
- **T005**: passo `Given the administrator created a competition` (cria a competição por
  `POST /competitions` autenticado) e o grupo `created` no passo de verificação.
- **T006**: `mvn -pl app -am verify` com o perfil `docker` e a infraestrutura do Compose de pé:
  165 testes, 0 falhas, 0 erros, 0 pulados, incluindo o cenário novo e os testes de
  consistência do OpenAPI.
- **T007**: contrato, serviço e passos usam o mesmo nome `created`; os três grupos antigos não
  mudaram.
