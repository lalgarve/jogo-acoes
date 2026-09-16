# Tasks: Desacoplar o módulo `link` dos seus consumidores

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões de
arquitetura referenciadas abaixo (formato do DTO, colisão de chave, persistência de
`userId`/`email`, mapeamento de handlers, consumo em duas fases) já estão resolvidas em
`plan.md` ("Decisões de arquitetura") — esta lista só quebra a implementação em passos.

**Pré-requisito de ordem**: pressupõe que a modularização inicial (spec 05-002, Issue
[#46](https://github.com/lalgarve/jogo-acoes/issues/46)) já está aplicada — o módulo `link`
precisa existir fisicamente antes de ser redesenhado por dentro.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar `LinkPayload` (record: `Long userId`, `String email`, `Map<String,String> extra`) em `{base}.link.dto` — sem sufixo "Dto" no nome, seguindo a convenção de sub-pacotes da spec 05-002 | — | [P] | — |
| T002 | Criar a entidade JPA `LinkRecord` (`id`, `token`, `serviceKey`, `userId`, `email`, `extraJson`, `expiresAt`, `usedAt`) e seu repositório, em `{base}.link` | — | [P] | — |
| T003 | Criar `LinkOutcome` (resultado: autenticado com dado de redirecionamento, ou "registro pendente"), em `{base}.link` | — | [P] | — |
| T004 | Criar a interface `LinkHandler` em `{base}.link` (`String key()`; `LinkOutcome consume(LinkPayload payload)`; `default LinkOutcome complete(LinkPayload payload, Map<String,String> extra)` lançando `UnsupportedOperationException`) | T001, T003 | | — |
| T005 | Criar `LinkRouter` (injeta `List<LinkHandler>`, monta `Map<String,LinkHandler>` por `key()`, falha ao subir o contexto Spring em colisão de chave) | T004 | | — |
| T006 | Criar `LinkService` (`create(serviceKey, payload)`, `consume(token)`, `complete(token, extra)`), usando Jackson só para serializar/desserializar `extra` de/para `extraJson` — `userId`/`email` vão direto para as colunas | T001, T002, T005 | | — |
| T007 | Criar `LinkController` (endpoint de consumo recebendo só o token; endpoint de conclusão de registro pendente) | T006 | | — |
| T008 | Criar `LoginLinkHandler` em `{base}.login` (`key() = "login"`, só `consume(LinkPayload payload)`, importando `{base}.link.dto.LinkPayload`), cobrindo o caso de login avulso hoje em `LoginService.consumeLoginLink` | T004 | [P] | — |
| T009 | Criar `CompetitionLinkHandler` em `{base}.competition` (`key() = "competition-entry"`; `consume(payload)` autentica se `payload.userId() != null`, senão devolve "pendente"; `complete(payload, extra)` cria `User`, vincula `Participation` via `extra["participationId"]`, status `IN_COMPETITION`) | T004 | [P] | — |
| T010 | Criar `LinkRouterKeyUniquenessTest` (ou nome equivalente): monta `LinkRouter` com as implementações reais de `LinkHandler` e verifica chave não-nula e não-duplicada, sem subir o contexto Spring completo | T005, T008, T009 | | — |
| T011 | Teste de verificação dedicado de `LoginLinkHandler` (requisito da spec: toda implementação tem teste próprio) — caso feliz e `userId`/`email` inválidos | T008 | [P] | — |
| T012 | Teste de verificação dedicado de `CompetitionLinkHandler` — caso feliz de `consume`/`complete`, e `participationId` inválido/ausente dentro de `extra` (risco "perda de integridade referencial parcial" do `plan.md`) | T009 | [P] | — |
| T013 | Migrar as três call sites que hoje criam `LoginLink` ligado a uma `Participation` diretamente (`EntryRequestService.requestEntry`, `PlayerManagementService.sendInviteEmail`, `CompetitionService.decideInviteEmailTiming`) para chamar `LinkService.create("competition-entry", payload)` | T006, T009 | | — |
| T014 | Migrar `LoginService.requestLoginLink` (login avulso) para chamar `LinkService.create("login", payload)` | T006, T008 | | — |
| T015 | Atualizar as rotas expostas ao cliente para que a URL do link carregue só o token (sem outros parâmetros manipuláveis), delegando para `LinkController`/`LinkService` | T007 | | — |
| T016 | Teste explícito de que `complete(token, extra)` sem um `consume` "pendente" anterior no mesmo token falha de forma controlada (risco do `plan.md`) | T007 | [P] | — |
| T017 | Verificar (manualmente ou com teste) que nenhum import de `{base}.login.*`/`{base}.competition.*` existe dentro de `{base}.link.*` — direção de dependência exigida pela spec | T008, T009 | [P] | — |
| T018 | Remover o código antigo tornado morto: `LoginService.consumeLoginLink`/`completeRegistration`, a FK direta `LoginLink.participation`, e a entidade `LoginLink` em si, se totalmente substituída por `LinkRecord` (confirmar se `LoginLink` ainda serve a algo fora do mecanismo de link antes de remover) | T013, T014, T015 | | — |
| T019 | Rodar a suíte completa (`.feature` de login/competição + os testes novos de T010–T012/T016) e confirmar que os cenários existentes continuam passando sem alteração de texto Gherkin, usando o novo mecanismo por baixo | T017, T018 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`refactor`). Nenhuma Issue foi criada ainda para esta spec (`spec.md` ainda
  lista `Issue: —`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
