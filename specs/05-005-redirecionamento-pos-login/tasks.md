# Tasks: Redirecionamento pós-login para o destino original

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões de
arquitetura referenciadas abaixo (nome do campo, formato de `LoginResult.redirectTo`,
validação de segurança) já estão resolvidas em `plan.md` ("Decisões de arquitetura") — esta
lista só quebra a implementação em passos.

**Ordem: TDD + API-first**, mesmo princípio da spec 05-004 — os passos Cucumber vêm primeiro,
contra o comportamento esperado descrito em `plan.md` (JSON cru, sem depender de classes
geradas), rodam e falham de forma previsível antes do contrato OpenAPI existir; o contrato vem
em seguida, formalizando o que os passos já esperavam; a implementação por último.

**Pré-requisito de ordem**: pressupõe a spec [05-004](../05-004-visao-do-jogador-sobre-competicoes/tasks.md)
já aplicada — os caminhos `/competitions/mine`/`/competitions/{id}` precisam existir antes de
serem usados como destino padrão de redirecionamento.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#51](https://github.com/lalgarve/jogo-acoes/issues/51) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Passos Cucumber para `redirect_after_login.feature` — chamada direta a `POST /login-requests` com um campo `returnTo` cru no corpo JSON (`Map`, não a classe gerada `RequestLoginLinkRequest`, que ainda não tem esse campo), e verificação de `redirectTo` na resposta de `GET /login-links/{token}` como string simples; rodar e confirmar que falha agora — confirmado: os 3 cenários falham exatamente na asserção do `redirectTo` (`"competitions-list"` recebido, o padrão antigo por papel, em vez do caminho esperado — `returnTo` é aceito silenciosamente pelo Jackson mas ainda não tem efeito nenhum). Achado: reescrevi "the player is registered but not logged in" pra "... and not logged in" no `.feature`, pra reaproveitar o step já existente em `RequestCompetitionEntrySteps` em vez de duplicar | — | | #51 |
| ~~T002~~ | Contrato: em `docs/openapi.yaml`, adicionar `returnTo` (string, opcional) em `RequestLoginLinkRequest`; mudar `LoginResult.redirectTo` de `enum` pra `string`; remover `LoginResult.competitionId` — formaliza exatamente o que T001 já esperava. Achado (já sinalizado como risco no `plan.md`): a mudança quebra a compilação de `LoginController` (`RedirectToEnum`/`competitionId()` deixam de existir) — diferente da 05-004, aqui não dá pra isolar "só contrato" sem tocar código, porque `LoginController` referencia o tipo gerado pelo nome, não por interface. Corrigido junto (é mecanicamente o mesmo escopo da T008, adiantada aqui — ver nota lá). Reconfirmado T001 depois: mesmo estado vermelho de antes (`"competitions-list"` ainda recebido — o valor só muda na T006), agora compilando; suíte completa, 111 testes, exatamente as mesmas 3 falhas, nada mais quebrou | T001 | | #51 |
| T003 | Criar o validador de "só caminho relativo do próprio site" (começa com uma única `/`, sem `//` logo depois) — função pura, em `login/` | T002 | [P] | #51 |
| T004 | Teste dedicado do validador de T003: caminhos relativos válidos aceitos; URLs absolutas (`https://evil.com`) e protocol-relative (`//evil.com`) rejeitadas, sem lançar erro (retorna vazio/`Optional`) | T003 | [P] | #51 |
| T005 | Atualizar `LoginController.requestLoginLink` para ler `returnTo` do corpo, validar com T003, e gravar em `LinkPayload.extra["returnTo"]` ao chamar `linkService.create("login", ...)` | T003 | | #51 |
| T006 | Atualizar `LoginLinkHandler` para receber o `LinkPayload` completo em `redirectFor` (hoje só recebe o `userId`) e checar `payload.extra().get("returnTo")` antes de calcular o padrão por papel — usado tanto em `consume` quanto em `alreadyAuthenticated` | T005 | | #51 |
| T007 | Atualizar `CompetitionLinkHandler.redirectFor` para emitir o novo formato de caminho (`/competitions/{id}`) em vez do símbolo antigo `competition-page` + `competitionId` separado | T002 | [P] | #51 |
| ~~T008~~ | Simplificar `LoginController.toLoginResult`: repassar o caminho direto (sem mapear enum, sem montar `competitionId` à parte) — feita antecipadamente na T002, mecanicamente forçada pela quebra de compilação (ver nota lá); nada a fazer aqui além de registrar como concluída | T006, T007 | | #51 |
| T009 | Atualizar os testes existentes afetados pela mudança de contrato: `LoginLinkHandlerTest`, `CompetitionLinkHandlerTest`, `LoginSteps`/suporte Cucumber que hoje lê `redirectTo`/`competitionId` do resultado | T008 | | #51 |
| T010 | Rodar a suíte completa: confirmar que T001 passa agora (verde), incluindo o cenário de um link mais novo substituir o destino do anterior (reaproveita `invalidateActiveLinksFor` já existente, sem mudança nele), e que os cenários já existentes continuam passando com o novo formato de `redirectTo` | T009 | | #51 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
