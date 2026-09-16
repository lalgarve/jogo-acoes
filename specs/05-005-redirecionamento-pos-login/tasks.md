# Tasks: Redirecionamento pós-login para o destino original

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões de
arquitetura referenciadas abaixo (nome do campo, formato de `LoginResult.redirectTo`,
validação de segurança) já estão resolvidas em `plan.md` ("Decisões de arquitetura") — esta
lista só quebra a implementação em passos.

**Pré-requisito de ordem**: pressupõe a spec [05-004](../05-004-visao-do-jogador-sobre-competicoes/tasks.md)
já aplicada — os caminhos `/competitions/mine`/`/competitions/{id}` precisam existir antes de
serem usados como destino padrão de redirecionamento.

Ainda sem Issue-épico aberta — a coluna Issue é preenchida depois que ela existir, mesmo
padrão já usado nas specs 05-001/05-002/05-003.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Contrato primeiro: em `docs/openapi.yaml`, adicionar `returnTo` (string, opcional) em `RequestLoginLinkRequest`; mudar `LoginResult.redirectTo` de `enum` pra `string`; remover `LoginResult.competitionId` | — | | — |
| T002 | Criar o validador de "só caminho relativo do próprio site" (começa com uma única `/`, sem `//` logo depois) — função pura, em `login/` | — | [P] | — |
| T003 | Teste dedicado do validador de T002: caminhos relativos válidos aceitos; URLs absolutas (`https://evil.com`) e protocol-relative (`//evil.com`) rejeitadas, sem lançar erro (retorna vazio/`Optional`) | T002 | [P] | — |
| T004 | Atualizar `LoginController.requestLoginLink` para ler `returnTo` do corpo (T001), validar com T002, e gravar em `LinkPayload.extra["returnTo"]` ao chamar `linkService.create("login", ...)` | T001, T002 | | — |
| T005 | Atualizar `LoginLinkHandler` para receber o `LinkPayload` completo em `redirectFor` (hoje só recebe o `userId`) e checar `payload.extra().get("returnTo")` antes de calcular o padrão por papel — usado tanto em `consume` quanto em `alreadyAuthenticated` | T004 | | — |
| T006 | Atualizar `CompetitionLinkHandler.redirectFor` para emitir o novo formato de caminho (`/competitions/{id}`) em vez do símbolo antigo `competition-page` + `competitionId` separado | T001 | [P] | — |
| T007 | Simplificar `LoginController.toLoginResult`: repassar o caminho direto (sem mapear enum, sem montar `competitionId` à parte) | T001, T006 | | — |
| T008 | Atualizar os testes existentes afetados pela mudança de contrato: `LoginLinkHandlerTest`, `CompetitionLinkHandlerTest`, `LoginSteps`/suporte Cucumber que hoje lê `redirectTo`/`competitionId` do resultado | T005, T006, T007 | | — |
| T009 | Passos Cucumber para `redirect_after_login.feature`, incluindo o cenário de um link mais novo substituir o destino do anterior (reaproveita `invalidateActiveLinksFor` já existente, sem mudança nele) | T005, T008 | | — |
| T010 | Rodar a suíte completa e confirmar que os cenários já existentes (login, convite, pedido de entrada) continuam passando com o novo formato de `redirectTo`, sem alteração de texto Gherkin | T009 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
