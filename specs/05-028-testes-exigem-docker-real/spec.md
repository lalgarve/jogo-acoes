# Spec: H2 sai do projeto — Postgres real sempre (sandbox, testes e Docker), sem Testcontainers

**Status:** implementada (T005, T006 e T011 substituídas pela spec 05-035; ver `tasks.md`)
**Issue:** #<a criar>
**Iteração:** iteration-5

## Resumo

H2 sai do projeto inteiro — tanto da suíte de testes automatizados (`mvn test`/`mvn verify`) de
`app` e `email-service` quanto do perfil `sandbox` (que roda a aplicação como processo vivo em
ambientes sem Docker, como o próprio sandbox da Claude). As duas coisas passam a usar PostgreSQL
real: a suíte de testes contra o que já está de pé via `docker-compose.yml` (e, quando a feature
precisar, LocalStack também do compose) — nunca subindo essa infraestrutura sozinha via
Testcontainers; o perfil sandbox contra o PostgreSQL já instalado nativamente nesse ambiente
(fora de Docker, ver "Contexto técnico" em `plan.md`). Nunca há substituto mais fraco para a
infraestrutura real.

**Revisão (2026-10-05):** o perfil `sandbox` descrito aqui foi removido depois pela spec
05-035, porque o ambiente da Claude passou a ter Docker. Com isso, o que acontecia com testes
que dependem de container num ambiente sem Docker deixou de existir: os testes sempre exigem a
infraestrutura de pé (`memory/constitution.md`, "Testes exigem a infraestrutura de pé"). Esta
spec fica com a troca de H2 e Testcontainers por infraestrutura real e com o step de CI do
`email-service`. As menções ao `sandbox` abaixo são histórico.

## Motivação

Dois problemas concretos, descobertos na sessão de 2026-10-02 trabalhando na spec 05-025:

1. **H2 estava vazando do perfil sandbox pra dentro da suíte de testes de verdade — e o motivo
   original pra H2 existir no sandbox não se sustenta mais.** O perfil `sandbox`
   (`application-sandbox.yml`, tanto de `app` quanto de `email-service`) usava H2 com a
   justificativa de que não havia Postgres disponível nesse ambiente (ver comentário hoje em
   `application-sandbox.yml`: "No Docker/Postgres available here"). Isso deixou de ser verdade:
   **o ambiente sandbox da Claude já tem PostgreSQL real instalado nativamente** (não via Docker
   — um script de setup sobe os clusters `app`/`email` do próprio `postgresql` do SO, nas portas
   5432/5433 (ver a nota sobre a porta 5433 em "Contexto técnico" de `plan.md`), com os mesmos papéis/bancos que `docker/postgres/init/01-roles.sql` e
   `docker/postgres-email-service/init/01-roles.sql` já criam para o perfil `docker`; ver
   "Contexto técnico" em `plan.md`). Sem essa limitação, não há mais razão pra manter H2 em lugar
   nenhum do projeto — nem no sandbox, nem (como já estava H2 por acidente) na suíte de testes.
   Além disso, `src/test/resources/application.yml` (o config que `mvn test` usa por padrão, tanto
   em `app` quanto em `email-service`) apontava pra H2 por padrão — só virava Postgres de verdade
   se alguém lembrasse de setar `SPRING_PROFILES_ACTIVE=docker` na hora de rodar. O CI já fazia
   isso certo pra `app` (`.github/workflows/ci.yml`, variável `SPRING_PROFILES_ACTIVE: docker`
   antes de `mvn ... verify`), mas qualquer execução local sem essa variável caía em H2
   silenciosamente — e `email-service` nem tem essa variável setada em CI, porque
   **`email-service` ainda não tem nenhum step de CI** (gap desta spec também).
2. **Testcontainers (usado só em `email-service`, pra subir o LocalStack da suíte Cucumber) não
   funciona neste ambiente de desenvolvimento Windows.** Investigado a fundo (ver
   `specs/05-025-servico-email-templates/tasks.md`, seção "Tentativa em 2026-10-02"): em 4
   transportes diferentes (named pipe do Windows, TCP exposto, socket Unix dentro do WSL, com e
   sem `DOCKER_HOST` explícito), o Docker Desktop responde normal pra CLI oficial e pro `curl`,
   mas devolve uma resposta vazia/placeholder pro cliente Java do Testcontainers — consistente
   com alguma política do Docker Desktop restringindo acesso à API só pro binário oficial, não
   com erro de configuração de qual socket apontar. Consertar isso tomaria mais tempo do que a
   pessoa que pediu esta spec tem disponível agora.

A solução adotada pras duas coisas é a mesma: **parar de pedir pro próprio processo de teste
gerenciar a infraestrutura** (seja via H2 como substituto, seja via Testcontainers subindo um
container sozinho) **e sempre depender do que já está de pé via `docker-compose.yml`**, do
mesmo jeito que o CI já faz pra `app`. Isso também é mais simples de explicar e documentar do
que depurar incompatibilidades específicas de Testcontainers em cada máquina de desenvolvimento.

## Cenários (comportamento esperado)

Esta spec não adiciona nem muda comportamento de produto — é infraestrutura de teste. Não há
`.feature` novo. O "comportamento esperado" é sobre como a suíte de testes se comporta:

- `mvn test`/`mvn verify` (`app` e `email-service`), rodado sem nenhuma variável de ambiente
  especial, conecta em PostgreSQL real (`localhost:5432`/`5433`, mesmas credenciais já usadas em
  `application-docker.yml`) — nunca em H2.
- Se esse Postgres não estiver de pé (`docker compose up db`/`db-email-service` não executado
  antes), os testes falham de forma clara e imediata (erro de conexão), não com um fallback
  silencioso pra H2.
- A suíte de aceite de `email-service` (os 19 Scenarios de `register_templates.feature`) não
  sobe LocalStack sozinha — assume que `docker compose up localstack` já está rodando,
  igual ao que `email-lambda` já assume hoje em CI.
- Rodar a suíte num ambiente sem Docker (ex.: o perfil sandbox da própria Claude) não tenta
  nenhum truque de substituição. (Revisão: o perfil `sandbox` foi removido pela spec 05-035;
  ver o topo deste arquivo.)

## Requisitos funcionais

- Remover o uso de H2 de `app/src/test/resources/application.yml` e de
  `email-service/src/test/resources/application.yml` — passam a apontar direto pro Postgres real
  (mesmas credenciais/URLs já usadas em `application-docker.yml` de cada módulo), sem depender de
  nenhuma variável de ambiente/profile adicional pra isso acontecer.
- Remover a dependência de Testcontainers (`org.testcontainers:*`) de `email-service/pom.xml` e a
  lógica de `CucumberSpringConfiguration` que sobe o `LocalStackContainer` sozinha. A suíte passa
  a apontar pro endpoint do LocalStack já configurado (mesmo valor/variável que
  `application-docker.yml` usa, `http://localhost:4566` por padrão).
- `docker-compose.yml` continua sendo a única fonte da infraestrutura usada em teste — nenhuma
  infraestrutura adicional sobe por fora dele.
- Adicionar um step de CI pra `email-service` (hoje inexistente em `.github/workflows/ci.yml`),
  seguindo o mesmo padrão já usado pra `app` (sobe `db-email-service` + `localstack` via
  `docker compose`, roda `mvn -pl email-service -am verify`).
- A falta de Docker no ambiente corrente nunca é motivo pra reintroduzir H2/Testcontainers como
  substituto. Os testes sempre exigem a infraestrutura de pé (spec 05-035).
- `application-sandbox.yml` (de `app` e `email-service`) troca de H2 pra PostgreSQL real, apontando
  pro Postgres nativo já instalado no ambiente sandbox (mesmas portas/roles do perfil `docker`:
  `localhost:5432`/`jogo_acoes_admin` e `localhost:5433`/`email_service_admin`), usando
  `db/migration` (não mais `db/migration-h2`).
- `db/migration-h2` (as duas pastas, em `app` e `email-service`) são removidas — deixam de ter
  qualquer perfil que as use depois que o sandbox também migrar pra Postgres real.

## Requisitos não-funcionais

- Nenhuma mudança nesta spec deve exigir rodar `docker compose up` com mais serviços do que os
  que `email-lambda`/`app` já exigem hoje em CI — `db-email-service` e `localstack` já existem em
  `docker-compose.yml` (adicionados pela spec 05-025).
- O Postgres do ambiente sandbox é provisionado fora do código deste repositório (script de setup
  do próprio ambiente, não um arquivo versionado aqui) — esta spec só consome essa infraestrutura
  já configurada, não a provisiona.

## Fora de escopo

- `email-lambda` — já usa Testcontainers via Quarkus Dev Services em CI (não localmente, por
  isso não foi afetado pelo problema desta sessão) e isso já funciona lá hoje. Não mexer, a
  menos que surja o mesmo tipo de problema.
- Remover o perfil `sandbox` e os `assumeTrue` dos testes — spec 05-035.
- Resolver a causa raiz do Testcontainers não funcionar no Docker Desktop desta máquina Windows
  específica — foi investigado (ver `specs/05-025-servico-email-templates/tasks.md`), mas a
  decisão aqui é parar de depender disso, não consertar o Testcontainers em si.
- Provisionar o PostgreSQL do ambiente sandbox (script de setup, clusters, etc.) — isso já existe
  fora deste repositório; esta spec só aponta a configuração da aplicação pra ele.
- Qualquer mudança no comportamento de produção das features já implementadas (05-025, etc.) —
  esta spec só muda como os testes/sandbox delas se conectam à infraestrutura.

## Decisões em aberto

- Nenhuma identificada até agora — ver `plan.md` para as decisões técnicas já resolvidas.
