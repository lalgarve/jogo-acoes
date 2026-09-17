# Tasks: Consistência entre contrato OpenAPI e implementação

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (mecanismo de comparação de rotas, mecanismo de comparação de papéis, onde
vivem os testes) já estão resolvidas em `plan.md` — esta lista só quebra a implementação em
passos.

**Pré-requisito de ordem (não bloqueio técnico rígido)**: pressupõe a spec
[05-006](../05-006-securityconfig-modular/tasks.md) já aplicada — `SecurityConfigContributor`
por módulo é o que deixa a autorização real organizada e legível; o `WebInvocationPrivilegeEvaluator`
funcionaria igual contra o `SecurityConfig` monolítico de hoje, mas faz mais sentido sequenciar
depois pra não competir por revisão na mesma janela (ver `plan.md`).

**Ordem: testes primeiro, sem passo de contrato** — mesmo princípio das specs anteriores, mas
adaptado: esta spec não implementa comportamento nenhum, ela só verifica que o contrato já
existente e o código já existente batem entre si. Os dois testes (rotas, papéis) são o produto
final da spec, não um passo intermediário — cada um deve, idealmente, já nascer verde contra o
estado atual (é o que a spec afirma); se algum nascer vermelho, isso é um achado real (divergência
entre `docs/openapi.yaml` e o código), corrigido na T003.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Escrever `common/OpenApiRoutesConsistencyTest.java` — lê `docs/openapi.yaml` com SnakeYAML, extrai `(método, path)` de cada operação; compara contra `RequestMappingHandlerMapping` filtrado ao pacote-base do projeto; rodar e registrar o resultado (esperado: verde, nenhuma rota fora do contrato hoje) | — | [P] | #<n> |
| T002 | Escrever `common/OpenApiRolesConsistencyTest.java` — lê `x-roles` de cada operação; para cada uma, três chamadas a `WebInvocationPrivilegeEvaluator` (anônimo, `ROLE_PLAYER`, `ROLE_ADMINISTRATOR`) contra o path com placeholders substituídos por `999999`; compara resultado contra o esperado (`[]` → permite as três; `[ADMINISTRATOR]` → só `ROLE_ADMINISTRATOR`; `[PLAYER, ADMINISTRATOR]` → nega só anônimo); rodar e registrar o resultado (esperado: verde, ver levantamento em `plan.md`) | — | [P] | #<n> |
| T003 | Só se T001 e/ou T002 vierem vermelhos: investigar e corrigir o lado errado (`docs/openapi.yaml` ou o código — rota/regra de acesso), registrando o achado no `plan.md` desta spec. Se os dois vierem verdes na primeira execução, marcar esta tarefa como não aplicável ao concluir a spec, sem código nenhum pra escrever | T001, T002 | | #<n> |
| T004 | Rodar a suíte completa (`mvn test`) — confirmar que os dois testes novos entram na contagem e tudo continua verde, sem nenhuma mudança de comportamento além da eventual correção da T003 | T001, T002, T003 | | #<n> |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`test`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
