# Tasks: Os testes não sobem nada

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: [#193](https://github.com/lalgarve/jogo-acoes/issues/193).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Resolver as decisões em aberto de `spec.md` (2 a 6; a 1 foi resolvida em 2026-10-20) e registrar com commit `decision:` | — | | — |
| T002 | `memory/constitution.md`: a regra "os testes não sobem nada" em "Testes exigem a infraestrutura de pé"; nota na spec 05-028 | T001 | [P] | — |
| T003 | Levantamento, teste por teste, dos que precisam de outra configuração (`spec.md`, "Decisões resolvidas"): a lista fechada dos que vão para Python, registrada em `plan.md` | — | [P] | — |
| T003a | Suíte Python dos testes de configuração especial (onde: `spec.md`, decisão 6): apoio para levantar e derrubar os containers de um cenário; um teste por caso da T003, cada um levantando e derrubando o que usa | T003, T004 | | — |
| T003b | Repositório inicial das listas só em `staging`/`production` (`plan.md`, "Repositório inicial"): `INSERT` fora da V6, migration de dados em `db/seed-email-service/`, `spring.flyway.locations` no `config-repo` de `staging`/`production`; a suíte Cucumber deixa de apagar o repositório; nota no caderno de testes do Serviço de E-mail e na spec 05-048 | — | [P] | — |
| T004 | Ambiente de teste: overlay `blackbox` e `scripts/cloud-up.sh` com a configuração de teste e os agentes JaCoCo; `scripts/test-coverage.sh` | T001 | | — |
| T005 | `config-server`: testes contra o serviço rodando; regra de arquitetura do módulo | T004 | | — |
| T006 | `email-lambda`: teste pela fila do LocalStack, sem `@QuarkusTest` nem Dev Services; teste unitário do classificador de erros; regra de arquitetura | T004 | | — |
| T007 | `email-service`: `testsupport/` (HTTP, JDBC, AWS), suíte Cucumber com `cucumber-picocontainer`, testes JUnit convertidos ou tratados conforme T003, sem `application.yml` de teste; regra de arquitetura | T003, T004 | | — |
| T008 | `app`: o mesmo da T007 | T003, T004 | | — |
| T009 | CI: levantar tudo antes, rodar as suítes, cobertura com piso, derrubar | T004 | | — |
| T010 | README ("Como rodar os testes") e `scripts/cloud-blackbox-tests.sh` ou equivalente para as suítes Maven na sessão de nuvem | T005–T009 | | — |
| T011 | Critério de aceite de exceções (`memory/constitution.md`): para cada branch de exceção hoje coberto por um teste que sobe o serviço, apontar o teste que o cobre no formato novo ou a justificativa (verificação manual) | T005–T008 | | — |
| T012 | Verificação manual: com o ambiente fora do ar, as suítes falham com erro de conexão; com ele de pé, passam; uma classe de teste com `@SpringBootTest` faz a regra de arquitetura falhar | T005–T010 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- T005 a T008 saem em PRs separadas, uma por módulo (`plan.md`, "Ordem da migração").
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.
