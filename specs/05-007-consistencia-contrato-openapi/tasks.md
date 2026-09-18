# Tasks: Consistência entre contrato OpenAPI e implementação

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (mecanismo de comparação de rotas, mecanismo de comparação de papéis, onde
vivem os testes) já estão resolvidas em `plan.md` — esta lista só quebra a implementação em
passos.

**Pré-requisito de ordem (não bloqueio técnico rígido)**: pressupõe a spec
[05-006](../05-006-securityconfig-modular/tasks.md) (Issue-épico
[#53](https://github.com/lalgarve/jogo-acoes/issues/53)) já aplicada —
`SecurityConfigContributor` por módulo é o que deixa a autorização real organizada e legível; o
`WebInvocationPrivilegeEvaluator` funcionaria igual contra o `SecurityConfig` monolítico de
hoje, mas faz mais sentido sequenciar depois pra não competir por revisão na mesma janela (ver
`plan.md`).

**Ordem: testes primeiro, sem passo de contrato** — mesmo princípio das specs anteriores, mas
adaptado: esta spec não implementa comportamento nenhum, ela só verifica que o contrato já
existente e o código já existente batem entre si. Os dois testes (rotas, papéis) são o produto
final da spec, não um passo intermediário — cada um deve, idealmente, já nascer verde contra o
estado atual (é o que a spec afirma); se algum nascer vermelho, isso é um achado real (divergência
entre `docs/openapi.yaml` e o código), corrigido na T003.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#54](https://github.com/lalgarve/jogo-acoes/issues/54) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Escrever `common/OpenApiRoutesConsistencyTest.java` — lê `docs/openapi.yaml` com SnakeYAML, extrai `(método, path)` de cada operação; compara contra `RequestMappingHandlerMapping` filtrado ao pacote-base do projeto; rodado — confirmado verde de primeira (2 testes: nenhuma rota implementada fora do contrato, nenhuma operação do contrato sem rota implementada) | — | [P] | #54 |
| ~~T002~~ | Escrever `common/OpenApiRolesConsistencyTest.java` — lê `x-roles` de cada operação; para cada uma, três chamadas a `WebInvocationPrivilegeEvaluator` (anônimo, `ROLE_PLAYER`, `ROLE_ADMINISTRATOR`) contra o path com placeholders substituídos por `999999`; compara resultado contra o esperado (`[]` → permite as três; `[ADMINISTRATOR]` → só `ROLE_ADMINISTRATOR`; `[PLAYER, ADMINISTRATOR]` → nega só anônimo); rodado — confirmado verde de primeira, exatamente como o levantamento do `plan.md` previu. Achado: `WebInvocationPrivilegeEvaluator` já vem auto-registrado por `@EnableWebSecurity` (bean `privilegeEvaluator()` em `WebSecurityConfiguration`) — nenhuma construção manual necessária, o "Plano B" do `plan.md` não foi preciso | — | [P] | #54 |
| ~~T003~~ | Não aplicável — T001 e T002 vieram verdes na primeira execução, nenhuma divergência entre `docs/openapi.yaml` e o código; nenhum código pra corrigir | T001, T002 | | #54 |
| ~~T004~~ | Rodar a suíte completa (`mvn test`) — confirmado: **125 testes, 0 falhas, 0 erros** (122 já existentes + os 3 testes novos), nenhuma mudança de comportamento | T001, T002, T003 | | #54 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`test`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
