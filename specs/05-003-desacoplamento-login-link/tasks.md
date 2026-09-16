# Tasks: Desacoplar o módulo `link` dos seus consumidores

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões de
arquitetura referenciadas abaixo (formato do DTO, colisão de chave, persistência de
`userId`/`email`, mapeamento de handlers, consumo em duas fases) já estão resolvidas em
`plan.md` ("Decisões de arquitetura") — esta lista só quebra a implementação em passos.

**Pré-requisito de ordem**: pressupõe que a modularização inicial (spec 05-002, Issue
[#46](https://github.com/lalgarve/jogo-acoes/issues/46)) já está aplicada — o módulo `link`
precisa existir fisicamente antes de ser redesenhado por dentro.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#47](https://github.com/lalgarve/jogo-acoes/issues/47) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

**Concluído (commit(s) na branch `claude/jogo-acoes-iteracao-5-5hloak`, sessão 2026-09-16)** —
todas as tarefas abaixo, T001–T019, com três desvios em relação ao desenho original,
detalhados em `plan.md` ("Achados feitos durante a implementação"):

- T004 (`LinkHandler`) ganhou um **terceiro método**, `alreadyAuthenticated`, não previsto no
  desenho original — necessário para preservar o atalho "usuário já logado neste dispositivo"
  de `login.feature`.
- T007 (`LinkController`) **não existe como classe separada** — `LoginController` (já
  existente, em `login/`) passou a delegar `consumeLoginLink`/`completeRegistration` para
  `LinkService`; `requestLoginLink` ficou como lógica própria de `login/` (ver plan.md, achado
  5). `LinkService.create` também passou a devolver `LinkCreationResult(id, token)`, não só o
  token, para dar aos três call sites o id numérico que a auditoria precisa.
- Escopo adicional descoberto durante a implementação, fora da lista original: `LoginSession`
  (também em `link/`, alocada lá desde a 05-002) tinha uma FK Java direta pra `login.User`,
  violando o mesmo requisito de direção de dependência que motivou o redesenho de
  `LinkRecord` — corrigida trocando `User user` por `Long userId` (sem FK), com a
  implementação de `LinkSessionService` (`LoginLinkSessionService`, em `login/`) resolvendo o
  usuário quando precisa.

Suíte completa verde (`.feature` de login/competição sem nenhuma alteração de texto Gherkin,
mais os 4 testes dedicados novos: `LinkRouterKeyUniquenessTest`, `LoginLinkHandlerTest`,
`CompetitionLinkHandlerTest`, `LinkServiceTest`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Criar `LinkPayload` (record: `Long userId`, `String email`, `Map<String,String> extra`) em `{base}.link.dto` — sem sufixo "Dto" no nome, seguindo a convenção de sub-pacotes da spec 05-002 | — | [P] | #47 |
| ~~T002~~ | Criar a entidade JPA `LinkRecord` (`id`, `token`, `serviceKey`, `userId`, `email`, `extraJson`, `expiresAt`, `usedAt`) e seu repositório, em `{base}.link` | — | [P] | #47 |
| ~~T003~~ | Criar `LinkOutcome` (resultado: autenticado com dado de redirecionamento, ou "registro pendente"), em `{base}.link` | — | [P] | #47 |
| ~~T004~~ | Criar a interface `LinkHandler` em `{base}.link` (`String key()`; `LinkOutcome consume(LinkPayload payload)`; `LinkOutcome alreadyAuthenticated(Long, LinkPayload)`; `default LinkOutcome complete(LinkPayload payload, Map<String,String> extra)` lançando `UnsupportedOperationException`) | T001, T003 | | #47 |
| ~~T005~~ | Criar `LinkRouter` (injeta `List<LinkHandler>`, monta `Map<String,LinkHandler>` por `key()`, falha ao subir o contexto Spring em colisão de chave) | T004 | | #47 |
| ~~T006~~ | Criar `LinkService` (`create(serviceKey, payload)`, `consume(token)`, `complete(token, extra)`), usando Jackson só para serializar/desserializar `extra` de/para `extraJson` — `userId`/`email` vão direto para as colunas | T001, T002, T005 | | #47 |
| ~~T007~~ | ~~Criar `LinkController`~~ Adaptar `LoginController` (login/) para delegar `consumeLoginLink`/`completeRegistration` a `LinkService` | T006 | | #47 |
| ~~T008~~ | Criar `LoginLinkHandler` em `{base}.login` (`key() = "login"`, só `consume(LinkPayload payload)`/`alreadyAuthenticated`, importando `{base}.link.dto.LinkPayload`), cobrindo o caso de login avulso hoje em `LoginService.consumeLoginLink` | T004 | [P] | #47 |
| ~~T009~~ | Criar `CompetitionLinkHandler` em `{base}.competition` (`key() = "competition-entry"`; `consume(payload)` autentica se `payload.userId() != null`, senão devolve "pendente"; `complete(payload, extra)` cria `User`, vincula `Participation` via `extra["participationId"]`, status `IN_COMPETITION`) | T004 | [P] | #47 |
| ~~T010~~ | Criar `LinkRouterKeyUniquenessTest`: monta `LinkRouter` com as implementações reais de `LinkHandler` e verifica chave não-nula e não-duplicada, sem subir o contexto Spring completo | T005, T008, T009 | | #47 |
| ~~T011~~ | Teste de verificação dedicado de `LoginLinkHandler` (`LoginLinkHandlerTest`) — caso feliz e `userId` inválido/ausente | T008 | [P] | #47 |
| ~~T012~~ | Teste de verificação dedicado de `CompetitionLinkHandler` (`CompetitionLinkHandlerTest`) — caso feliz de `consume`/`complete`, e `participationId` inválido/ausente dentro de `extra` | T009 | [P] | #47 |
| ~~T013~~ | Migrar as três call sites que hoje criam `LoginLink` ligado a uma `Participation` diretamente (`EntryRequestService.requestEntry`, `PlayerManagementService.sendInviteEmail`, `CompetitionService.decideInviteEmailTiming`) para chamar `LinkService.create("competition-entry", payload)` | T006, T009 | | #47 |
| ~~T014~~ | Migrar `LoginService.requestLoginLink` (login avulso) para chamar `LinkService.create("login", payload)` | T006, T008 | | #47 |
| ~~T015~~ | Atualizar as rotas expostas ao cliente para que a URL do link carregue só o token (sem outros parâmetros manipuláveis), delegando para `LoginController`/`LinkService` | T007 | | #47 |
| ~~T016~~ | Teste explícito de que `complete(token, extra)` sem um `consume` "pendente" anterior no mesmo token falha de forma controlada (`LinkServiceTest`) | T007 | [P] | #47 |
| ~~T017~~ | Verificar (grep) que nenhum import de `{base}.login.*`/`{base}.competition.*` existe dentro de `{base}.link.*` — direção de dependência exigida pela spec (achado extra: incluiu corrigir `LoginSession.user` também) | T008, T009 | [P] | #47 |
| ~~T018~~ | Remover o código antigo tornado morto: `LoginService` inteiro (`consumeLoginLink`/`completeRegistration`/`requestLoginLink` redistribuídos), `LoginLink`/`LoginLinkRepository` (substituídos por `LinkRecord`/`LinkRecordRepository`) | T013, T014, T015 | | #47 |
| ~~T019~~ | Rodar a suíte completa (`.feature` de login/competição + os testes novos de T010–T012/T016) e confirmar que os cenários existentes continuam passando sem alteração de texto Gherkin, usando o novo mecanismo por baixo | T017, T018 | | #47 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`refactor`). Todas as tarefas desta spec ficam como checklist na Issue
  [#47](https://github.com/lalgarve/jogo-acoes/issues/47).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
