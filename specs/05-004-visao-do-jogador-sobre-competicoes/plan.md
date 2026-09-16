# Plan: Visão do jogador sobre competições

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Módulo `competition` já existente (`CompetitionsController`/`CompetitionService`,
`EntryRequestsController`/`EntryRequestService`, `PlayersController`/`PlayerManagementService`,
`ParticipationRepository`, `CompetitionRepository`). `GET /competitions` já existe no
`docs/openapi.yaml` como `listPublicCompetitions`, mas **nunca foi implementado** — nenhum
`.feature`/teste o exercita e `CompetitionsController` não sobrescreve o método (confirmado
nesta sessão, `grep` em `app/src/`). Sem uso real, é seguro redesenhar o que esse caminho
significa.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| `GET /competitions` (o `listPublicCompetitions` já existente, nunca implementado) vira o quê? | Dois caminhos novos, nenhum deles reaproveita o nome antigo como estava: `GET /competitions/public` (descoberta, sem sessão) e `GET /competitions/mine` (lista do jogador, com sessão). `GET /competitions` sem sufixo fica livre para uso futuro (ex. lista administrativa) — não é definido nesta spec. | resolvida | Conversa da sessão: "pode criar o caminho para ver as competições públicas" — caminho **novo**, não reaproveitar o antigo `GET /competitions` pra dois significados diferentes (confundiria descoberta pública com lista pessoal). |
| Competição pública que o jogador nunca tocou é visível via `GET /competitions/{id}`? | Não — só aparece em `GET /competitions/public` (informação básica, sem nível de acesso). `GET /competitions/{id}` continua exigindo alguma relação (participante, convidado/pediu, ou administrador), conforme a spec. | resolvida | Mantém a regra original do cenário-base ("jogador não tem acesso a competição que não participa ou participou") — descoberta e detalhe são propósitos diferentes. |
| Formato da resposta ("informação básica") | Novo schema `CompetitionSummary` (`id`, `name`, `type`, `status`, `startDate`, `durationDays`) para as listas; `CompetitionDetail` (`CompetitionSummary` + `buyFee`/`sellFee`/`recurring` + `accessLevel`) para o detalhe. Não reaproveita o schema `Competition` já existente (que inclui `creatorId`, pensado pra resposta de criação, uso do administrador). | resolvida | "endpoint de competições devem retornar informações básicas" — schema dedicado deixa explícito o que é exposto pra descoberta/listagem vs. o que só a criação (administrador) devolve. |
| Formato de `GET /competitions/mine` | Objeto com três arrays: `participating`, `pastParticipations`, `pendingConfirmation` — cada item é `CompetitionSummary` mais o `participationStatus` daquela relação (não um array plano com um campo de categoria). | resolvida | Três categorias fixas e sempre presentes (mesmo vazias) é mais simples de consumir no front do que filtrar um array plano por categoria. |
| Onde vive o cálculo de nível de acesso (`accessLevel`) | Método novo `CompetitionAccessResolver.resolve(competition, participation, isAdministrator)` em `competition/`, puro (sem I/O), retornando `READ_WRITE`/`READ`/`DENIED` — usado tanto por `GET /competitions/{id}` quanto (indiretamente) pela categorização de `GET /competitions/mine`. | resolvida | Mesma regra aparece nos dois endpoints (a spec já define as mesmas três categorias/dois níveis); função pura facilita teste dedicado sem subir contexto Spring, mesmo padrão já usado em `LinkRouter`/`LinkService` nesta iteração. |
| Onde vive a lógica de consulta/agrupamento | `CompetitionViewService` novo, em `competition/`, injetando `ParticipationRepository`/`CompetitionRepository` — não reaproveita `CompetitionService` (hoje só criação/timing de convite, escopo de administrador) nem `EntryRequestService` (escopo de pedido de entrada). `CompetitionsController` ganha os dois métodos novos (`listPublicCompetitions`, `listMyCompetitions`, `getCompetitionDetail` — nomes de `operationId`). | resolvida | Separar leitura (`CompetitionViewService`) de escrita (`CompetitionService`) evita crescer uma classe com responsabilidades muito diferentes; `CompetitionsController` continua sendo o único controller do recurso `/competitions*`. |
| Consultas novas no `ParticipationRepository` | `findByUser_Id(Long userId)` — busca todas as participações do jogador de uma vez; agrupamento em memória por `status`/`competition.status` (volume esperado é baixo por jogador, não justifica três queries separadas). | resolvida | Simplicidade — uma consulta, lógica de categorização em Java, testável sem banco. |
| Consulta nova no `CompetitionRepository` | `findByTypeAndStatus(CompetitionType.PUBLIC, CompetitionStatus.OPEN)` — competições públicas só existem `OPEN` ou `CLOSED` (nunca `AWAITING_INVITES`, exclusivo de privada, ver `CompetitionService.create`), então filtrar por `OPEN` já exclui as encerradas sem precisar de um `NOT IN`. | resolvida | Direto, sem cláusula negativa. |
| Reaproveitar `POST /competitions/{id}/entry-requests` pra "confirmar a partir da tela de detalhe"? | Sim, sem mudança nenhuma — `EntryRequestsController.requestOrConfirmEntry` já chama `EntryRequestService.confirmEntry(competitionId)` quando autenticado, e `confirmEntry` já não restringe por `RequestType`/tipo de competição (só rejeita `PRIVATE` sem nenhuma `Participation` prévia) — cobre convite privado e pedido público igual. Confirmado lendo o código nesta sessão. | resolvida | Nenhum mecanismo novo de escrita necessário — só o front chama esse endpoint já existente a partir da nova tela de detalhe. |
| Anotação de papel por operação no OpenAPI | Extensão vendor `x-roles: [ADMINISTRATOR]` / `[PLAYER]` / `[]` (visitante) em cada operação, junto da `description` textual que já existe em parte das operações hoje. Aplicado retroativamente em todas as operações já existentes, não só as novas desta spec. | resolvida | Permite gerar depois uma tabela "quem pode chamar o quê" pro trabalho da disciplina sem manter duas fontes de verdade; `security`/OAuth scopes nativos do OpenAPI não se encaixam bem (autenticação aqui é sessão, não OAuth). |

## Estrutura de módulos/pacotes

Tudo em `competition/` (módulo já existente) — nenhum pacote novo:

- `CompetitionAccessResolver.java` (novo) — função pura de cálculo de nível de acesso.
- `CompetitionViewService.java` (novo) — consultas/agrupamento pra descoberta pública, "minhas
  competições" e detalhe.
- `CompetitionsController.java` (modificado) — ganha `listPublicCompetitions`,
  `listMyCompetitions`, `getCompetitionDetail`.
- `ParticipationRepository.java`/`CompetitionRepository.java` (modificados) — uma consulta nova
  cada, ver tabela acima.
- `docs/openapi.yaml` (modificado) — os três caminhos novos, os schemas
  `CompetitionSummary`/`CompetitionDetail`, e `x-roles` em toda operação do arquivo.

## Riscos e trade-offs

- **`x-roles` é só documentação, não aplica a regra em runtime** — a autorização de fato
  continua em `SecurityConfig` (rotas) + checagem de papel/participação dentro dos serviços,
  como já é hoje. Risco de as duas fontes divergirem com o tempo; mitigação é revisar
  `x-roles` sempre que `SecurityConfig` mudar (nenhuma automação prevista nesta spec).
- **`findByUser_Id` sem paginação** — aceitável para o volume atual do projeto (ver "Fora de
  escopo" em spec.md); revisar se o número de competições por jogador crescer muito.
