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
| ~~T001~~ | Regra ArchUnit "nenhuma classe de teste usa `org.junit.jupiter.api.Assumptions`" em `app` (`common/ArchitectureTest`) e `email-service` (`common/ArchitectureTest`); no `email-lambda`, adicionar `archunit-junit5` (escopo `test`) e um `ArchitectureTest` com a mesma regra. Nasce verde nos três módulos: provar acrescentando temporariamente um `Assumptions` numa classe de teste de cada módulo, ver o ArchUnit falhar apontando essa classe, e desfazer (sem commitar a violação). Registrar | — | [P] | #139 |
| ~~T002~~ | Com o LocalStack parado e o perfil `docker`: rodar as suítes de `app` e `email-lambda` e registrar que os testes que dependem dele falham com erro de conexão e nenhum aparece como pulado — comportamento esperado já antes desta spec, porque nenhuma marcação de skip chegou a entrar | — | [P] | #139 |
| T003 | ~~`app`: remover o `@BeforeAll` com `assumeTrue(reachable(...))` de `SqsEmailSenderDockerIntegrationTest` e `QueueLoggingAspectIntegrationTest` e ajustar o Javadoc~~ — já resolvida: os dois testes foram apagados no commit `8818c94` (spec 05-034, T015), e nenhum teste do `app` usa `Assumptions` | — | | — |
| T004 | ~~`email-lambda`: em `EmailSendHandlerTest`, remover o `catch (SdkClientException)` + `assumeTrue(false, ...)` e ajustar o Javadoc~~ — já resolvida: removidos no commit `ffa11c6` (spec 05-031) | — | | — |
| ~~T005~~ | Perfil padrão `docker`: `spring.profiles.default: docker` em `app/src/main/resources/application.yml` (hoje `sandbox`), `app/src/test/resources/application.yml` e `email-service/src/test/resources/application.yml`, com comentário explicando por que o de teste repete o valor | — | [P] | #139 |
| ~~T006~~ | Apagar `app/src/main/resources/application-sandbox.yml` e `email-service/src/main/resources/application-sandbox.yml`; corrigir os comentários de `application.yml`, `application-staging.yml` e `application-production.yml` do `app` e os Javadocs de `StubEmailSender` (passa a citar as suítes Cucumber do `app`, spec 05-034) e `AdministratorBootstrap` | T005 | | #139 |
| ~~T007~~ | Textos: `docker-compose.yml`, `scripts/test-api-key.sh`, `docker/postgres-email-service/test-data/README.md`, `docs/diagrams/modulos.md` e `docs/diagrams/classes.md` sem o perfil `sandbox` (as menções ao "modo sandbox" do SES ficam) | T006 | [P] | #139 |
| ~~T008~~ | `README.md`: seção "Como rodar os testes" (subir `db`, `db-email-service` e `localstack` antes; `docker` é o padrão; o que acontece se a infraestrutura não estiver de pé) e tabela "Ambientes" sem o `sandbox` | T005, T006 | [P] | #139 |
| ~~T009~~ | `docs/disciplina/*`: revisar as menções ao perfil `sandbox` seguindo `docs/disciplina/CLAUDE.md` | T006 | [P] | #139 |
| ~~T010~~ | Rodar as três suítes sem `SPRING_PROFILES_ACTIVE`, com os containers de pé: tudo verde, nenhum teste pulado por falta de infraestrutura, T001 verde, log com `default profile: "docker"` (C1, C2, C5) | T001–T006 | | #139 |
| ~~T011~~ | Rodar de novo com o LocalStack parado: os testes que dependem dele falham com erro de conexão, nenhum aparece como pulado (C3) | T010 | | #139 |
| ~~T012~~ | Conferir que não sobra `application-sandbox.yml` nem menção ao perfil `sandbox` fora de specs implementadas, diários e "modo sandbox" do SES (C4) | T006–T009 | | #139 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`chore`/`test`, conforme o caso).
- T002, T010 e T011 dependem de Docker.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Registro de verificação (2026-10-08)

- **T001**: regra `noTestSkipsItselfWithAssumptions` no `ArchitectureTest` de `app` e
  `email-service`, e num `ArchitectureTest` novo no `email-lambda` (com `archunit-junit5` 1.5.0 em
  escopo `test`). A regra nomeia `org.junit.jupiter.api.Assumptions` por string: um class literal
  faria o próprio teste depender da classe. Verde nos três módulos. Prova: uma classe
  temporária `TemporaryAssumptionsViolation` chamando `Assumptions.assumeTrue(true)` em cada
  módulo fez a regra falhar nos três, apontando a classe e a linha
  (`TemporaryAssumptionsViolation.java:6`), com as outras regras verdes. As classes e os
  `.class` compilados foram apagados; nada disso foi commitado.
- **T002** (antes de T005/T006, com `SPRING_PROFILES_ACTIVE=docker` e o LocalStack do Compose
  parado):
  - `app`: 217 testes, 1 falha, 37 erros, 0 pulados. Os 36 do
    `EmailServiceClientIntegrationTest` e o `ProductionProfileSuppressesLoggingAspectsTest` não
    sobem o contexto porque o email-service responde 500 ao registrar os templates (o SES dele
    está fora). A falha do `RepositoryLoggingAspectIntegrationTest` é cascata: o contexto
    `production` que não subiu desmonta o sistema de log, e a saída capturada fica vazia.
    Sozinho, com o LocalStack ainda parado, ele passa.
  - `email-lambda`: 7 testes verdes, porque ele não usa o LocalStack do Compose: o Quarkus sobe
    o próprio pelo Dev Services. A tentativa de simular "sem Docker" com `DOCKER_HOST` inválido
    não funcionou (o Testcontainers caiu no npipe local). Sem `Assumptions`, garantido pela T001,
    ele não tem como se pular.
- **T005**: `spring.profiles.default: docker` no `application.yml` principal do `app` e nos de
  teste de `app` e `email-service`, com comentário.
- **T006**: os dois `application-sandbox.yml` apagados. Os comentários de `application.yml`
  (`altcha.secret`) e o Javadoc do `AdministratorBootstrap` corrigidos. Os de `staging`/`production`
  e do `StubEmailSender` já não citavam o `sandbox` (a spec 05-034 tinha resolvido).
- **T007**: `docker-compose.yml`, `scripts/test-api-key.sh` e
  `docker/postgres-email-service/test-data/README.md`. Os diagramas já não citavam o perfil. Dois
  comentários do `email-lambda/pom.xml` que o plano não listava ("not this agent's sandbox",
  "blocked from this sandbox") também foram reescritos.
- **T008**: `README.md` ganhou a seção "Como rodar os testes" (ordem de subida da CI, as três
  suítes sem variável de ambiente, o que acontece sem infraestrutura, incluindo a cascata vista
  na T002), e a tabela "Ambientes" ficou sem o `sandbox`.
- **T009**: `caderno-de-testes.md` atualizado. `alinhamento-projeto-disciplina.md` não foi tocado
  (é histórico). **Pendente no rascunho de entrega**: a linha "Profiles para ao menos dois
  ambientes" da Etapa 3 ainda lista o `sandbox`. Ficou para a autora, porque a PR #112
  sincroniza o rascunho local dela e mexe nas linhas vizinhas. Registrado em
  `docs/disciplina/CLAUDE.md`.
- **T010** (sem `SPRING_PROFILES_ACTIVE`, ambiente recriado com `docker compose down -v` na ordem
  da CI): `app` 217 testes, 0 falhas, 0 pulados, `jacoco:check` verde, Cucumber 74/74,
  `ArchitectureTest` 9/9; `email-service` 41 testes, 0 falhas, 2 pulados (os
  `@requires-real-ses`, excluídos pelo filtro de tags, Issue #104), `ArchitectureTest` 4/4;
  `email-lambda` 7 testes, 0 falhas, `ArchitectureTest` 1/1. Todo contexto Spring de `app` e
  `email-service` sem perfil explícito logou `default profile: "docker"` (C1, C2, C5).
- **T011** (sem `SPRING_PROFILES_ACTIVE`, LocalStack do Compose parado): `email-service` 27 erros,
  todos `SdkClientException: Unable to execute HTTP request: Connection refused` em
  `localhost:4566`, 0 pulados além dos 2 do filtro de tags. `app` o mesmo quadro da T002 (1
  falha, 37 erros, 0 pulados). Nenhum teste aparece pulado por falta de infraestrutura (C3).
- **T012**: nenhum `application-sandbox.yml`. As menções que sobram fora de specs e diários são o
  registro desta própria mudança em `docs/disciplina/CLAUDE.md`, a nota histórica dos `SELECT`s
  no caderno, o levantamento histórico `alinhamento-projeto-disciplina.md`, a linha pendente do
  rascunho (T009), a narrativa histórica da constitution ("Nomenclatura de ambientes"), o
  sandbox do Puppeteer e o "modo sandbox" do SES (C4).
- **Achado à parte**: depois de reiniciar só o LocalStack (para a T002), o `app` não subia mais.
  O SES do LocalStack perde os templates ao reiniciar, mas o banco do email-service continua com
  eles: o email-service responde 404 ao PUT e 409 ao POST, e o `upsertTemplate` do `app` desiste
  (`Template invite answered 409 on create and 404 on update`). Só `docker compose down -v`
  resolveu. O email-service não se recupera sozinho quando o banco e o SES divergem.
