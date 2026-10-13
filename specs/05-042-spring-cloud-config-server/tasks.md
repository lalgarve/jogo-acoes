# Tasks: Spring Cloud Config Server

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). As decisões de `spec.md`/`plan.md`
foram resolvidas em 2026-10-13; esta lista só quebra a implementação em passos.

**Ordem:** verificações primeiro, falhando pelo motivo esperado; depois as correções.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `app` e `email-service`: teste que falha se `application-docker.yml`, `application-staging.yml` ou `application-production.yml` existir no classpath principal. Confirmar que falha hoje nos dois | — | [P] | #<n> |
| T002 | `app` e `email-service`: `ArchitectureTest` proíbe classes de `..jogoacoes.configserver..`. Nasce verde (o pacote não existe); provar que pega algo com uma violação temporária, registrada abaixo | — | [P] | #<n> |
| T003 | Módulo `config-server` no reator (`pom.xml` raiz; `config-server/pom.xml` com Boot 4.1.0, BOM `spring-cloud-dependencies` 2025.1.3, `spring-cloud-config-server`, `spring-boot-starter-security`, enforcer com `bannedDependencies` para `app`/`email-service`), só com `ConfigServerApplication` vazio e os testes: `ConfigServerTest` (com o `config-repo/` real: `GET /jogo-acoes/docker` e `/email-service/docker` com credenciais trazem propriedades conhecidas de cada um; sem credenciais → `401`; credenciais erradas → `401`; nos perfis `staging`/`production`, todo valor de chave com `password`, `api-key`, `secret` ou `pepper` no nome é placeholder `${...}`) e `ArchitectureTest` (nenhuma classe de `app` nem de `..emailservice..`). Confirmar que `ConfigServerTest` falha | — | [P] | #<n> |
| T004 | `config-server`: `@EnableConfigServer`, `SecurityConfig` (HTTP Basic, tudo autenticado, usuário de `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD`, sem padrão fora do teste), `application.yml` (porta 8888, perfil `native`, `search-locations` de `CONFIG_REPO_LOCATION`). Confirmar na prática que o `${...}` volta bruto do servidor e é resolvido no cliente (registrar abaixo). | T003 | | #<n> |
| T005 | Mover `app/src/main/resources/application-{docker,staging,production}.yml` para `config-repo/jogo-acoes-{docker,staging,production}.yml`, valores e comentários iguais | T001 | | #<n> |
| T006 | `app`: `spring-cloud-starter-config` e `spring-retry`; `application.yml` com `spring.config.import: configserver:${CONFIG_SERVER_URL:http://localhost:8888}` (sem `optional:`), `spring.cloud.config.fail-fast: true`, `username`/`password` de `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD`; `src/test/resources/application.yml` com `spring.cloud.config.enabled: false` e sem import. Conferir que, sem perfil ativo, o cliente pede `/jogo-acoes/docker` (risco em `plan.md`). T001 do `app` fica verde | T005 | | #<n> |
| T007 | `app`: `ConfigServerClientIntegrationTest` contra o `config-server` do Compose: o contexto sobe com o import ligado e uma propriedade que só existe em `jogo-acoes-docker.yml` (ex.: `links.base-url`) chega ao `Environment`; com `CONFIG_SERVER_URL` apontando para uma porta fechada, o contexto não sobe e a causa é a falha de buscar configuração | T006, T004 | | #<n> |
| T008 | Mover `email-service/src/main/resources/application-{docker,production}.yml` para `config-repo/email-service-{docker,production}.yml` e criar `config-repo/email-service-staging.yml` no formato do production (datasource, senha e pepper de variável de ambiente sem padrão, Flyway desligado, comentário autodescritivo dizendo que o ambiente ainda não existe) | T001 | [P] | #<n> |
| T009 | `email-service`: BOM `spring-cloud-dependencies` 2025.1.3, `spring-cloud-starter-config`, `spring-retry`, mesmo import/`fail-fast`/credenciais de T006 e mesmo desligamento no arquivo de teste. T001 do `email-service` fica verde | T008 | | #<n> |
| T010 | `email-service`: `ConfigServerClientIntegrationTest`, mesmo desenho de T007 (propriedade de `email-service-docker.yml`, ex.: `spring.cloud.aws.ses.endpoint`) | T009, T004 | | #<n> |
| T011 | `config-server/Dockerfile` (mesmo padrão do `email-service`); `Dockerfile` do `app` e `email-service/Dockerfile` copiam também `config-server/pom.xml`. `docker-compose.yml`: serviço `config-server` (porta 8888, `config-repo/` montado somente leitura, `CONFIG_SERVER_USERNAME`/`PASSWORD` de teste, `JAVA_TOOL_OPTIONS: -Xmx128m`, healthcheck por `/dev/tcp`); `app` e `email-service` com `depends_on` `service_healthy` e `CONFIG_SERVER_URL`/credenciais | T006, T009 | | #<n> |
| T012 | CI: subir `config-server` junto do resto do Compose antes dos `mvn verify`; passo `mvn -B -pl config-server -am verify`. Confirmar no CI que T003, T007 e T010 rodam (nenhum pulado) | T011 | | #<n> |
| T013 | Documentação: `README.md` (módulo novo, como subir, que rodar o `app` fora do Compose exige o `config-server`); `memory/constitution.md`, "Nomenclatura de ambientes" (o arquivo autodescritivo de cada ambiente passa a morar em `config-repo/`); `docs/context/iteracao-5.md` (fechar a decisão em aberto "o `app/` também migra para o Config Server?" apontando para esta spec) | T011 | [P] | #<n> |
| T014 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os caminhos de erro (Config Server inalcançável no cliente, `401` no servidor, credenciais ausentes no servidor fora do teste); para cada um, apontar o teste que o exercita. Registrar a lista abaixo | T007, T010, T011 | | #<n> |
| T015 | Rodar a suíte completa dos três módulos e a `blackbox-tests` contra o `docker compose up` — confirmar verde | T012, T013, T014 | | #<n> |
| T016 | Verificação manual (Leila): `docker compose up`; `GET http://localhost:8888/jogo-acoes/docker` com e sem credencial; pedir um login e ver o e-mail no `ses-viewer`; parar o `config-server` e confirmar que `app` não sobe | T015 | | #<n> |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature (label `iteration-5` e `feat`),
  ou uma Issue própria quando grande o suficiente para PR isolada.
- Marcar o ID como concluído (`~~T001~~`) na mesma PR que resolve a task, junto com o registro da
  verificação (`memory/constitution.md`, "Rastreamento de trabalho via Issues").

## Registro das verificações

(vazio — preenchido pela PR que implementar cada task)
