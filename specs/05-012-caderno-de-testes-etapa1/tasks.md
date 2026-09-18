# Tasks: Caderno de testes — Etapa 1 (Swagger)

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões (nome do
arquivo, formato de cada caso, branch de execução) já estão resolvidas em `plan.md` — esta lista
só quebra a escrita do documento em passos.

**Pré-requisito de conteúdo (não bloqueio de estrutura)**: as specs
[05-009](../05-009-identificacao-dispositivo-client-hints/tasks.md),
[05-010](../05-010-gestao-sessoes-ativas/tasks.md) e
[05-011](../05-011-logging-aspectos/tasks.md) precisam estar implementadas e mescladas antes dos
casos de teste (T004–T009) poderem ser escritos de verdade — sem elas rodando, não há Swagger
UI, endpoint de sessões nem log pra descrever. T002/T003 (branch e esqueleto) não dependem
disso.

**Um exemplo central, seis ângulos**: em vez de um endpoint diferente por critério da Etapa 1,
todos os casos giram em torno da mesma funcionalidade pequena (gestão de sessões ativas, spec
05-010) — cada caso olha pra ela por um ângulo diferente (fluxo, validação, exceção, consulta,
contrato, organização de pacotes), como o `spec.md` desta spec já definiu.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#62](https://github.com/lalgarve/jogo-acoes/issues/62) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Confirmar que as specs 05-009/05-010/05-011 estão implementadas e mescladas na branch de código | — | | #62 |
| T002 | Checkout/atualização da branch de documentação separada (`docs/...`, convenção da Issue #43) a partir do `master` atual — fast-forward simples, sem conflito esperado | — | [P] | #62 |
| T003 | Esqueleto de `docs/disciplina/caderno-de-testes.md`: seção `## Etapa 1` com o formato fixo por caso (Objetivo → Passos no Swagger UI → Log esperado → Select de verificação) | T002 | | #62 |
| T004 | Caso 1 — fluxo Controller→Service→Repository: `GET /sessions` no Swagger UI (caminho feliz), log esperado (spec 05-011) e `SELECT` em `login_session` confirmando o retorno | T001, T003 | [P] | #62 |
| T005 | Caso 2 — Bean Validation: `DELETE /sessions/{sessionId}` com um id não numérico no Swagger UI, resposta `400` esperada, log esperado | T001, T003 | [P] | #62 |
| T006 | Caso 3 — tratamento de exceção centralizado: `DELETE /sessions/{sessionId}` com um id que não existe (ou de outro usuário), `404` via `ApiExceptionHandler`, log esperado | T001, T003 | [P] | #62 |
| T007 | Caso 4 — consultas Spring Data além do CRUD básico: aponta as duas consultas dedicadas usadas por `GET`/`DELETE /sessions` (`findByUserIdAndEndedAtIsNull...`, `findByIdAndUserId`), com o `SELECT` gerado por cada uma | T001, T003 | [P] | #62 |
| T008 | Caso 5 — OpenAPI/Swagger UI: exercitar `consumeLoginLink` no Swagger UI preenchendo os headers de Client Hints (spec 05-009) manualmente, confirmando que aparecem como campo na UI | T001, T003 | [P] | #62 |
| T009 | Caso 6 — organização de pacotes por domínio: nota estrutural apontando `login/`/`link/` (sem chamada HTTP), referenciando as specs 05-001/05-002 | T003 | [P] | #62 |
| T010 | Executar cada caso manualmente contra a aplicação rodando (`docker-compose`) e confirmar que os logs/`SELECT`s descritos batem com a execução real — ajustar o texto onde divergir | T004, T005, T006, T007, T008, T009 | | #62 |
| T011 | Atualizar a linha "Documentação da API via OpenAPI/Swagger" (e qualquer outra que passe a se aplicar) em `docs/disciplina/alinhamento-projeto-disciplina.md`, já que deixa de faltar a UI interativa | T010 | | #62 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`docs`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
