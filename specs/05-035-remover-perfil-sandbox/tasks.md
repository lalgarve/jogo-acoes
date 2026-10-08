# Tasks: Remover o perfil `sandbox` — testes sempre contra a infraestrutura real

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Testes de verificação primeiro.** T001 nasce vermelho: hoje `app` e `email-lambda` usam
`Assumptions` para decidir em tempo de execução. T002 registra o comportamento atual antes da
mudança.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Regra ArchUnit "nenhuma classe de teste usa `org.junit.jupiter.api.Assumptions`" em `app` (`common/ArchitectureTest`) e `email-service` (`common/ArchitectureTest`); no `email-lambda`, adicionar `archunit-junit5` (escopo `test`) e um `ArchitectureTest` com a mesma regra. Rodar e registrar a falha em `app` e `email-lambda` | — | [P] | #139 |
| T002 | Com o LocalStack parado e o perfil `docker`: rodar as suítes de `app` e `email-lambda` e registrar que os testes com `assumeTrue` aparecem como pulados em vez de falhar | — | [P] | #139 |
| T003 | `app`: remover o `@BeforeAll` com `assumeTrue(reachable(...))` de `SqsEmailSenderDockerIntegrationTest` e `QueueLoggingAspectIntegrationTest` e ajustar o Javadoc (se a T015 da spec 05-034 já tiver apagado algum deles, só o que sobrar) | T001 | [P] | #139 |
| T004 | `email-lambda`: em `EmailSendHandlerTest`, remover o `catch (SdkClientException)` + `assumeTrue(false, ...)` e ajustar o Javadoc | T001 | [P] | #139 |
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
