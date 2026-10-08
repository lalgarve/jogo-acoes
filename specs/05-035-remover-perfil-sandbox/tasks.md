# Tasks: Remover o perfil `sandbox` — testes sempre contra a infraestrutura real

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Testes de verificação primeiro.** Quando esta spec foi escrita (2026-10-05), `app` e
`email-lambda` usavam `Assumptions` para decidir em tempo de execução. Conferido no `master` em
2026-10-08, esses usos já saíram por outras specs (ver T003 e T004), e as anotações para pular os
testes no `sandbox` pensadas na spec 05-028 nunca entraram. Por isso T001 nasce verde e é provada
com uma violação temporária, e T002 registra que os testes já falham sem a infraestrutura.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Regra ArchUnit "nenhuma classe de teste usa `org.junit.jupiter.api.Assumptions`" em `app` (`common/ArchitectureTest`) e `email-service` (`common/ArchitectureTest`); no `email-lambda`, adicionar `archunit-junit5` (escopo `test`) e um `ArchitectureTest` com a mesma regra. Nasce verde nos três módulos: provar acrescentando temporariamente um `Assumptions` numa classe de teste de cada módulo, ver o ArchUnit falhar apontando essa classe, e desfazer (sem commitar a violação). Registrar | — | [P] | #139 |
| T002 | Com o LocalStack parado e o perfil `docker`: rodar as suítes de `app` e `email-lambda` e registrar que os testes que dependem dele falham com erro de conexão e nenhum aparece como pulado — comportamento esperado já antes desta spec, porque nenhuma marcação de skip chegou a entrar | — | [P] | #139 |
| T003 | ~~`app`: remover o `@BeforeAll` com `assumeTrue(reachable(...))` de `SqsEmailSenderDockerIntegrationTest` e `QueueLoggingAspectIntegrationTest` e ajustar o Javadoc~~ — já resolvida: os dois testes foram apagados no commit `8818c94` (spec 05-034, T015), e nenhum teste do `app` usa `Assumptions` | — | | — |
| T004 | ~~`email-lambda`: em `EmailSendHandlerTest`, remover o `catch (SdkClientException)` + `assumeTrue(false, ...)` e ajustar o Javadoc~~ — já resolvida: removidos no commit `ffa11c6` (spec 05-031) | — | | — |
| T005 | Perfil padrão `docker`: `spring.profiles.default: docker` em `app/src/main/resources/application.yml` (hoje `sandbox`), `app/src/test/resources/application.yml` e `email-service/src/test/resources/application.yml`, com comentário explicando por que o de teste repete o valor | — | [P] | #139 |
| T006 | Apagar `app/src/main/resources/application-sandbox.yml` e `email-service/src/main/resources/application-sandbox.yml`; corrigir os comentários de `application.yml`, `application-staging.yml` e `application-production.yml` do `app` e os Javadocs de `StubEmailSender` (passa a citar as suítes Cucumber do `app`, spec 05-034) e `AdministratorBootstrap` | T005 | | #139 |
| T007 | Textos: `docker-compose.yml`, `scripts/test-api-key.sh`, `docker/postgres-email-service/test-data/README.md`, `docs/diagrams/modulos.md` e `docs/diagrams/classes.md` sem o perfil `sandbox` (as menções ao "modo sandbox" do SES ficam) | T006 | [P] | #139 |
| T008 | `README.md`: seção "Como rodar os testes" (subir `db`, `db-email-service` e `localstack` antes; `docker` é o padrão; o que acontece se a infraestrutura não estiver de pé) e tabela "Ambientes" sem o `sandbox` | T005, T006 | [P] | #139 |
| T009 | `docs/disciplina/*`: revisar as menções ao perfil `sandbox` seguindo `docs/disciplina/CLAUDE.md` | T006 | [P] | #139 |
| T010 | Rodar as três suítes sem `SPRING_PROFILES_ACTIVE`, com os containers de pé: tudo verde, nenhum teste pulado por falta de infraestrutura, T001 verde, log com `default profile: "docker"` (C1, C2, C5) | T001–T006 | | #139 |
| T011 | Rodar de novo com o LocalStack parado: os testes que dependem dele falham com erro de conexão, nenhum aparece como pulado (C3) | T010 | | #139 |
| T012 | Conferir que não sobra `application-sandbox.yml` nem menção ao perfil `sandbox` fora de specs implementadas, diários e "modo sandbox" do SES (C4) | T006–T009 | | #139 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`chore`/`test`, conforme o caso).
- T002, T010 e T011 dependem de Docker.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.
