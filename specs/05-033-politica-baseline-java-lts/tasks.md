# Tasks: Política da baseline Java e dos upgrades de LTS

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue: [#115](https://github.com/lalgarve/jogo-acoes/issues/115) — cada linha abaixo é um
item de checklist nela.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `CLAUDE.md`: registrar Java/JDK 21 como baseline e dizer que nenhum upgrade de LTS deve ser sugerido ou iniciado em tarefa não relacionada; manter a exceção para pedido explícito e incompatibilidade concreta | — | [P] | #115 |
| ~~T002~~ | `memory/constitution.md`: registrar a política duradoura, a justificativa e o procedimento para incompatibilidades concretas; indicar que a mudança de baseline exige decisão explícita | — | [P] | #115 |
| ~~T003~~ | POMs: conferir `app`, `email-service`, `email-lambda` e `blackbox-proxy`; manter cada módulo em Java 21 e corrigir apenas divergências que contradigam a baseline, sem atualizar para outro LTS | — | [P] | #115 |
| ~~T004~~ | CI e Docker: conferir `.github/workflows/ci.yml`, `Dockerfile` e `email-service/Dockerfile`; manter Temurin/JRE 21 e documentar a intenção quando o comentário atual não for suficiente | — | [P] | #115 |
| ~~T005~~ | Verificação automatizada/documental: procurar referências de Java/JDK fora de `target/`, confirmar que não há configuração conflitante e revisar os cenários T001–T003 | T001, T002, T003, T004 | | #115 |
| ~~T006~~ | Rodar a validação mínima aplicável (checagem Maven/configuração e testes existentes necessários); registrar no resultado da spec que nenhuma migração de Java foi executada | T005 | | #115 |

- **[P]** marca tarefas que podem ser feitas em paralelo.
- Marcar o ID como concluído (`~~T001~~`) quando o commit que o resolve for mesclado; não
  deixar a tabela dessincronizada do estado real.
- Se uma divergência exigir uma decisão de upgrade, parar a implementação desta spec e
  registrar a incompatibilidade na Issue #115 ou em uma spec separada. Não escolher
  automaticamente um novo LTS.

## Resultado da verificação (2026-10-05)

- `CLAUDE.md` e `memory/constitution.md` registram Java/JDK 21 como baseline e exigem pedido
  explícito para qualquer upgrade de LTS.
- Os quatro POMs continuam declarando Java 21: `java.version` nos três módulos Spring Boot e
  `maven.compiler.release` no `email-lambda`.
- A CI continua usando Temurin 21; os dois Dockerfiles continuam usando Maven/Temurin 21 no
  build e Temurin 21 JRE no runtime.
- A busca de referências fora de `target/` não encontrou versão conflitante.
- `git diff --check` passou.
- Nenhuma migração de Java, atualização de dependência ou teste de integração foi executado:
  a implementação altera apenas política/documentação e comentários de configuração.
