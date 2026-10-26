# Tasks: Camadas dentro dos módulos

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: —

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `app`: quatro regras de camada no `ArchitectureTest` e classes de exemplo que as violam, com um teste por regra confirmando que ela falha. Confirmar as regras verdes no código atual | — | [P] | — |
| T002 | `email-service`: refatoração do `disposable` (`spec.md`, "Acesso a banco no `disposable`"): `DisposableDomainRepository`, `ImportExecutionRepository`, métodos novos no `DisposableDomainSourceRepository`, `content_sha256` mapeado, nenhum SQL nas seis classes. Confirmar a suíte do `email-service` verde sem mudar teste de comportamento | — | [P] | — |
| T003 | `email-service`: as mesmas quatro regras e exemplos. Confirmar que falham antes da T002 (nas seis classes) e passam depois | T002 | | — |
| T004 | Constitution, "Fronteira entre módulos e serviços": regra de camadas e "só repository acessa o banco", garantidas por teste de arquitetura | T001, T003 | | — |
| T005 | Critério de aceite de exceções: nenhuma classe de exceção nova; registrar aqui | T002 | | — |
| T006 | Verificação manual, com comandos prontos: `ArchitectureTest` do `app` e do `email-service` verdes; plantar temporariamente um `UserRepository` num controller e ver a mensagem; importar uma lista no ambiente local antes e depois da T002 e comparar domínios adicionados, removidos e o estado da lista | T001–T003 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.
