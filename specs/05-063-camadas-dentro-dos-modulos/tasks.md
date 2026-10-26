# Tasks: Camadas dentro dos módulos

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: —

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `app`: três regras de camada no `ArchitectureTest` e classes de exemplo que as violam, com um teste por regra confirmando que ela falha. Confirmar as regras verdes no código atual | — | [P] | — |
| T002 | `email-service`: as mesmas três regras e exemplos (`JdbcTemplate` permitido em service). Confirmar verdes | — | [P] | — |
| T003 | Constitution, "Fronteira entre módulos e serviços": regra de camadas garantida por teste de arquitetura | T001, T002 | | — |
| T004 | Critério de aceite de exceções: nenhuma classe de exceção nova; registrar aqui | T001, T002 | | — |
| T005 | Verificação manual, com comandos prontos: `ArchitectureTest` do `app` e do `email-service` verdes; plantar temporariamente um `UserRepository` num controller e ver a mensagem da regra | T001, T002 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.
