# Tasks: Nenhum ciclo de dependência entre módulos

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: —

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `ModuleDependencyGraph` no `app` (montagem do grafo + Tarjan + ciclo concreto) e teste unitário com grafos à mão: sem ciclo, ciclo de 2, ciclo de 3, dois ciclos, auto-referência | — | [P] | — |
| T002 | Teste no `ArchitectureTest` do `app`. Confirmar que falha mostrando `competition → email → competition` e a referência `EmailRequest → RequestType` | T001 | | — |
| T003 | Quebrar o ciclo `competition ↔ email` conforme a decisão em aberto da `spec.md`. Confirmar o teste da T002 verde e a suíte do `app` verde | T002 | | — |
| T004 | Mesmo teste no `email-service` (cópia de `ModuleDependencyGraph` e do teste unitário). Confirmar verde | T001 | [P] | — |
| T005 | Constitution, "Fronteira entre módulos e serviços": sem ciclo entre módulos de um serviço, garantido por teste de arquitetura | T003, T004 | | — |
| T006 | Critério de aceite de exceções: nenhuma classe de exceção nova; registrar aqui | T003 | | — |
| T007 | Verificação manual, com comandos prontos: suíte do `app` e do `email-service` verdes; plantar um `user → log` temporário e ver a mensagem do ciclo | T003, T004 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.
