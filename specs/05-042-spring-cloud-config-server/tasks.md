# Tasks: Spring Cloud Config Server

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). As decisões de `spec.md`/`plan.md`
foram resolvidas em 2026-10-13; esta lista só quebra a implementação em passos.

**Ordem:** verificações primeiro, falhando pelo motivo esperado; depois as correções.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `app` e `email-service`: teste que falha se `application-docker.yml`, `application-staging.yml` ou `application-production.yml` existir no classpath principal. Confirmar que falha hoje nos dois | — | [P] | #<n> |
| ~~T002~~ | `app` e `email-service`: `ArchitectureTest` proíbe classes de `..jogoacoes.configserver..`. Nasce verde (o pacote não existe); provar que pega algo com uma violação temporária, registrada abaixo | — | [P] | #<n> |
| ~~T003~~ | Módulo `config-server` no reator (`pom.xml` raiz; `config-server/pom.xml` com Boot 4.1.0, BOM `spring-cloud-dependencies` 2025.1.3, `spring-cloud-config-server`, `spring-boot-starter-security`, enforcer com `bannedDependencies` para `app`/`email-service`), só com `ConfigServerApplication` vazio e os testes: `ConfigServerTest` (com o `config-repo/` real: `GET /jogo-acoes/docker` e `/email-service/docker` com credenciais trazem propriedades conhecidas de cada um; sem credenciais → `401`; credenciais erradas → `401`; nos perfis `staging`/`production`, todo valor de chave com `password`, `api-key`, `secret` ou `pepper` no nome é placeholder `${...}`) e `ArchitectureTest` (nenhuma classe de `app` nem de `..emailservice..`). Confirmar que `ConfigServerTest` falha | — | [P] | #<n> |
| ~~T004~~ | `config-server`: `@EnableConfigServer`, `SecurityConfig` (HTTP Basic, tudo autenticado, usuário de `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD`, sem padrão fora do teste), `application.yml` (porta 8888, perfil `native`, `search-locations` de `CONFIG_REPO_LOCATION`). Confirmar na prática que o `${...}` volta bruto do servidor e é resolvido no cliente (registrar abaixo). | T003 | | #<n> |
| ~~T005~~ | Mover `app/src/main/resources/application-{docker,staging,production}.yml` para `config-repo/jogo-acoes-{docker,staging,production}.yml`, valores e comentários iguais | T001 | | #<n> |
| ~~T006~~ | `app`: `spring-cloud-starter-config` e `spring-retry`; `application.yml` com `spring.config.import: configserver:${CONFIG_SERVER_URL:http://localhost:8888}` (sem `optional:`), `spring.cloud.config.fail-fast: true`, `username`/`password` de `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD`; `src/test/resources/application.yml` com `spring.cloud.config.enabled: false` e sem import. Conferir que, sem perfil ativo, o cliente pede `/jogo-acoes/docker` (risco em `plan.md`). T001 do `app` fica verde | T005 | | #<n> |
| ~~T007~~ | `app`: `ConfigServerClientIntegrationTest` contra o `config-server` do Compose: o contexto sobe com o import ligado e uma propriedade que só existe em `jogo-acoes-docker.yml` (ex.: `links.base-url`) chega ao `Environment`; com `CONFIG_SERVER_URL` apontando para uma porta fechada, o contexto não sobe e a causa é a falha de buscar configuração | T006, T004 | | #<n> |
| ~~T008~~ | Mover `email-service/src/main/resources/application-{docker,production}.yml` para `config-repo/email-service-{docker,production}.yml` e criar `config-repo/email-service-staging.yml` no formato do production (datasource, senha e pepper de variável de ambiente sem padrão, Flyway desligado, comentário autodescritivo dizendo que o ambiente ainda não existe) | T001 | [P] | #<n> |
| ~~T009~~ | `email-service`: BOM `spring-cloud-dependencies` 2025.1.3, `spring-cloud-starter-config`, `spring-retry`, mesmo import/`fail-fast`/credenciais de T006 e mesmo desligamento no arquivo de teste. T001 do `email-service` fica verde | T008 | | #<n> |
| ~~T010~~ | `email-service`: `ConfigServerClientIntegrationTest`, mesmo desenho de T007 (propriedade de `email-service-docker.yml`, ex.: `spring.cloud.aws.ses.endpoint`) | T009, T004 | | #<n> |
| ~~T011~~ | `config-server/Dockerfile` (mesmo padrão do `email-service`); `Dockerfile` do `app` e `email-service/Dockerfile` copiam também `config-server/pom.xml`. `docker-compose.yml`: serviço `config-server` (porta 8888, `config-repo/` montado somente leitura, `CONFIG_SERVER_USERNAME`/`PASSWORD` de teste, `JAVA_TOOL_OPTIONS: -Xmx128m`, healthcheck por `/dev/tcp`); `app` e `email-service` com `depends_on` `service_healthy` e `CONFIG_SERVER_URL`/credenciais | T006, T009 | | #<n> |
| T012 | CI: subir `config-server` junto do resto do Compose antes dos `mvn verify`; passo `mvn -B -pl config-server -am verify`. Confirmar no CI que T003, T007 e T010 rodam (nenhum pulado) | T011 | | #<n> |
| ~~T013~~ | Documentação: `README.md` (módulo novo, como subir, que rodar o `app` fora do Compose exige o `config-server`); `memory/constitution.md`, "Nomenclatura de ambientes" (o arquivo autodescritivo de cada ambiente passa a morar em `config-repo/`); `docs/context/iteracao-5.md` (fechar a decisão em aberto "o `app/` também migra para o Config Server?" apontando para esta spec) | T011 | [P] | #<n> |
| ~~T014~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os caminhos de erro (Config Server inalcançável no cliente, `401` no servidor, credenciais ausentes no servidor fora do teste); para cada um, apontar o teste que o exercita. Registrar a lista abaixo | T007, T010, T011 | | #<n> |
| T015 | Rodar a suíte completa dos três módulos e a `blackbox-tests` contra o `docker compose up` — confirmar verde | T012, T013, T014 | | #<n> |
| T016 | Verificação manual (Leila): `docker compose up`; `GET http://localhost:8888/jogo-acoes/docker` com e sem credencial; pedir um login e ver o e-mail no `ses-viewer`; parar o `config-server` e confirmar que `app` não sobe | T015 | | #<n> |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature (label `iteration-5` e `feat`),
  ou uma Issue própria quando grande o suficiente para PR isolada.
- Marcar o ID como concluído (`~~T001~~`) na mesma PR que resolve a task, junto com o registro da
  verificação (`memory/constitution.md`, "Rastreamento de trabalho via Issues").

## Registro das verificações

Implementação de 2026-10-13, branch `claude/project-thread-dmv9md`. Infraestrutura no container
da Claude: `db` e `db-email-service` do Compose; `localstack` com `--no-deps` (o
`email-lambda-builder` não baixa dependências do Maven Central de dentro do container, então a
Lambda não é implantada); `config-server` e `email-service` rodando com `java -jar` no host, pelos
mesmos jars que as imagens empacotam.

- **T001** — `EnvironmentConfigLocationTest` nos dois serviços. Antes da mudança: 3 falhas no
  `app` (os três arquivos) e 2 no `email-service` (`docker` e `production`; ele não tinha
  `staging`). Depois de T005/T008: verde.
- **T002** — `app`: regra nova `appDoesNotContainOrDependOnConfigServerClasses`. `email-service`:
  a regra existente `emailServiceDoesNotContainOrDependOnAppClasses` já proíbe qualquer classe da
  raiz compartilhada fora de `..emailservice..`, o que inclui `..configserver..`; não foi preciso
  regra nova. Prova nos dois: uma classe temporária `dev.leilaalgarve.jogoacoes.configserver.TemporaryViolation`
  em `src/main` fez cada regra falhar com 1 violação; a classe foi removida em seguida.
- **T003** — `ConfigServerTest` (8 testes) e `ConfigServerCredentialsTest` falharam antes de T004
  (`401` em tudo, porque não havia Config Server nem usuário configurado); `ArchitectureTest` do
  módulo, verde desde o início.
- **T004** — `mvn -pl config-server -am verify`: 11 testes verdes. O placeholder volta bruto do
  servidor (`links.base-url` = `${LINKS_BASE_URL:http://localhost:8080/api}`, conferido em
  `servesAppDockerConfigurationWithPlaceholdersLeftForTheClient`) e é resolvido no cliente
  (`ConfigServerClientIntegrationTest` do `app` lê `http://localhost:8080/api`). As credenciais
  usam o usuário do próprio Spring Boot (`spring.security.user.*` = `${CONFIG_SERVER_USERNAME}`/
  `${CONFIG_SERVER_PASSWORD}`, sem padrão), em vez de um `UserDetailsService` próprio.
- **T005/T008** — `git mv` dos cinco arquivos, valores iguais; cada um ganhou duas linhas de
  cabeçalho (quem o serve e para quem) e as referências a `app/src/main/resources/application-*.yml`
  nos comentários foram atualizadas. `email-service-staging.yml` novo, no formato do production.
- **T006/T009** — Achado: a premissa de `plan.md` ("os testes já não leem `application-docker.yml`")
  estava errada. O `application.yml` de teste substitui o principal, mas os arquivos de perfil do
  jar continuavam sendo lidos, porque `docker` é o perfil padrão. A suíte do `app` dependia de dois
  valores de lá: `ADMIN_EMAIL` e `links.base-url`. Os dois foram para o `application.yml` de teste,
  com os mesmos valores. O `email-service` não dependia de nenhum, porque o arquivo de teste dele já
  repetia tudo. `ProductionProfileSuppressesLoggingAspectsTest` dependia de
  `application-production.yml` e passou a ler o perfil `production` do `config-server`. Agora ele
  parte do `application.yml` real (`target/classes`), como `ConfigServerClientIntegrationTest`.
  `BlackboxProfileIntegrationTest` deixou de sobrescrever `links.base-url`.
  Perfil sem `SPRING_PROFILES_ACTIVE`: o cliente pede `/<aplicação>/default` e depois
  `/<aplicação>/docker` (log do `email-service` com `java -jar`: `Located environment:
  name=email-service, profiles=[docker]`), então não foi preciso fixar o perfil no Compose.
  `spring-retry` 2.0.13 (a versão com que `spring-cloud-config-client` 5.0.5 é compilado, onde
  ele é opcional).
- **T007/T010** — `ConfigServerClientIntegrationTest` nos dois serviços, verde contra o
  `config-server` real: o contexto inteiro sobe a partir do `application.yml` empacotado, e a
  propriedade lida vem de uma fonte `configserver:…/<aplicação>-docker.yml`. Com
  `CONFIG_SERVER_URL=http://localhost:1` e uma tentativa, a inicialização falha com
  `ConfigClientFailFastException` ("Could not locate PropertySource").
- **T011** — `docker compose config -q` sem erro. A imagem do `config-server` não foi construída
  aqui: o `mvn` dentro do build falha com PKIX, pelo mesmo motivo do `email-lambda-builder`. A parte
  de execução foi conferida com o jar do host num container `eclipse-temurin:21-jre`, com o
  `config-repo/` montado e as variáveis do Compose. O healthcheck `bash -c '</dev/tcp/localhost/8888'`
  passou, `GET /email-service/staging` respondeu, e o container ocupou 210 MiB com `-Xmx128m`.
  Na CI, `docker compose up --wait config-server` constrói a imagem de verdade.
- **T013** — `README.md` (módulo novo, comando de teste, variáveis do cliente, `config-server`
  necessário fora do Compose), `memory/constitution.md` ("Nomenclatura de ambientes", exemplo do
  projeto) e `docs/context/iteracao-5.md` (decisão em aberto fechada).
- **T014** — Nenhuma classe de exceção criada. Caminhos de erro:
  - Config Server inalcançável no cliente (`fail-fast`, depois das tentativas):
    `ConfigServerClientIntegrationTest.appDoesNotStartWhenConfigServerIsUnreachable` /
    `serviceDoesNotStartWhenConfigServerIsUnreachable`.
  - `401` sem credencial e com credencial errada: `ConfigServerTest.rejectsRequestsWithoutCredentials`
    e `rejectsWrongCredentials`.
  - Credenciais ausentes no servidor: `ConfigServerCredentialsTest.doesNotStartWithoutCredentials`.
  - Sem teste: `CONFIG_SERVER_USERNAME`/`PASSWORD` ausentes no **cliente**. É a mesma falha de
    placeholder sem padrão que já vale para `LINKS_BASE_URL` e `SPRING_DATASOURCE_URL` em
    staging/production, sem código próprio desta spec.
  - Sem teste: as tentativas do cliente (`spring.cloud.config.retry`, padrão de 6). O teste de falha
    usa 1 tentativa para não esperar; o comportamento é da biblioteca, só ligado pela presença do
    `spring-retry`.
- **T015 (parcial)** — Suítes completas no container da Claude: `config-server` 11/11 e
  `email-service` 46 testes, verde (2 pulados no Cucumber, que já eram pulados antes). No `app`,
  278 testes: só falharam os 5 casos de `EmailServiceClientIntegrationTest.sendingRecordsEmailServiceIdAndReachesSesWithTheTemplate`
  ("no e-mail … reached SES on LocalStack"), porque a Lambda não está implantada aqui. Isso não
  tem relação com esta spec. Falta rodar na CI (T012) e a `blackbox-tests`.
