# Tasks: Testes que dependem de container — marcação explícita e perfil padrão `docker`

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Testes de verificação primeiro.** T001 e T002 nascem vermelhos: hoje os três módulos usam
`Assumptions` para decidir em tempo de execução, e nenhum teste está marcado. T003 resolve a
decisão em aberto do Cucumber com um experimento antes de qualquer mudança definitiva.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Regra ArchUnit "nenhuma classe de teste usa `org.junit.jupiter.api.Assumptions`" em `app` (`common/ArchitectureTest`) e `email-service` (`common/ArchitectureTest`); no `email-lambda`, adicionar `archunit-junit5` (escopo `test`) e um `ArchitectureTest` com a mesma regra. Rodar e registrar a falha nos três (`SqsEmailSenderDockerIntegrationTest`, `QueueLoggingAspectIntegrationTest`, `EmailSendHandlerTest`) | — | [P] | |
| T002 | Rodar as três suítes com `SPRING_PROFILES_ACTIVE=sandbox` e sem Docker; registrar o estado atual (o Cucumber do `email-service` quebra com erro de conexão; `app` e `email-lambda` pulam pelo `assumeTrue`). Rodar também no perfil `docker` com o LocalStack parado e registrar que `app` pula em vez de falhar | — | [P] | |
| T003 | Experimento para a decisão em aberto do Cucumber (`plan.md`): testar as candidatas (a) hook que aborta, (b) `@RequiresContainers` em `RunCucumberTest` e (c) `excludedGroups`, no `sandbox` e no `docker`. Registrar o resultado de cada uma e atualizar `plan.md` com a escolhida. Nada definitivo é mesclado nesta task | — | [P] | |
| T004 | `app`: `common/testsupport/RequiresContainers.java` (anotação composta do `plan.md`) | — | [P] | |
| T005 | `app`: trocar o `@BeforeAll` com `assumeTrue(reachable(...))` por `@RequiresContainers` em `SqsEmailSenderDockerIntegrationTest` e `QueueLoggingAspectIntegrationTest` | T001, T004 | | |
| T006 | `email-service`: `common/testsupport/RequiresContainers.java`, tag `@requires-containers` em `register_templates.feature` e o mecanismo escolhido na T003 (incluindo o `@After` de `TemplateCleanupHooks`, se a escolha for o hook) | T002, T003 | | |
| T007 | `email-lambda`: `RequiresContainers.java` e, em `EmailSendHandlerTest`, trocar o `catch (SdkClientException)` + `assumeTrue(false, ...)` por `@RequiresContainers` no método `sendsAWellFormedMessageWithoutError` | T001 | [P] | |
| T008 | Perfil padrão `docker`: `spring.profiles.default: docker` em `app/src/main/resources/application.yml` (hoje `sandbox`), `app/src/test/resources/application.yml` e `email-service/src/test/resources/application.yml`, com comentário explicando por que o de teste repete o valor | — | [P] | |
| T009 | Rodar as três suítes no perfil `docker` (sem `SPRING_PROFILES_ACTIVE`, containers de pé): tudo verde, nenhum teste pulado por falta de infraestrutura, T001 verde. Confirmar no log `default profile: "docker"` (C1, C2) | T005, T006, T007, T008 | | |
| T010 | Rodar no perfil `docker` com o LocalStack parado: os testes marcados falham com erro de conexão, não aparecem como pulados (C3) | T009 | | |
| T011 | Rodar com `SPRING_PROFILES_ACTIVE=sandbox` e sem Docker: os marcados aparecem como pulados com o motivo, os que só usam o banco rodam, build verde (C4) | T009 | | |
| T012 | Porta do Postgres do `email-service` no sandbox: confirmar no ambiente sandbox qual porta o script de setup usa e alinhar `email-service/src/main/resources/application-sandbox.yml`, o `plan.md` da 05-028 (nota em "Contexto técnico") e as notas da 05-030 | — | [P] | |
| T013 | `README.md`: seção "Como rodar os testes" (subir `db`, `db-email-service` e `localstack` antes; perfil padrão `docker`; `SPRING_PROFILES_ACTIVE=sandbox` sem Docker e o que é pulado ali) e a tabela "Ambientes" com o perfil padrão de cada serviço | T008 | [P] | |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`test`/`chore`, conforme o caso).
- T002, T003, T009 e T010 dependem de Docker. T011 e T012 dependem do ambiente sandbox (sem
  Docker, com o Postgres nativo).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
