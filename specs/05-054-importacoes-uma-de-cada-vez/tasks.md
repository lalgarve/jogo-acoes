# Tasks: Importações das listas de e-mail temporário uma de cada vez

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: a criar.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Passo de preparação "the import of every repository already ran" exige `COMPLETED` em cada execução | — | [P] | — |
| T002 | Teste da fila (`plan.md`, "Teste da fila"); confirmar que falha antes da T003 (repetindo até aparecer uma execução que começa antes de a anterior terminar) | — | [P] | — |
| T003 | Executor de uma thread com fila (`ImportDisposableDomainsJobConfig`) | T002 | | — |
| T004 | `spring.batch.jdbc.isolation-level-for-create: read-committed` (se a decisão for "A + `READ_COMMITTED`") | T002 | [P] | — |
| T005 | Suíte do `email-service` verde, rodada algumas vezes seguidas; nota na 05-048 (`plan.md`, "Execução sob pedido") | T001, T003, T004 | | — |
| T006 | Critério de aceite de exceções (`memory/constitution.md`): nenhuma classe de exceção nova; registrar aqui | T003 | | — |
| T007 | Verificação manual, com comandos prontos: três repositórios, "importar todos", conferir que as três execuções terminam `COMPLETED` e que os horários não se sobrepõem | T005 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.
