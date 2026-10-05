# Plan: Testes automatizados exigem Docker real (Postgres + LocalStack)

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `app` e `email-service` já têm um perfil `docker` (`application-docker.yml`) com os valores
  reais de conexão (`jdbc:postgresql://localhost:5432/jogo_acoes` e
  `jdbc:postgresql://localhost:5433/email_service`, respectivamente) — essa spec não inventa
  valor novo, só muda onde/quando eles se aplicam.
- `docker-compose.yml` já define `db`, `db-email-service` e `localstack` com os init scripts
  necessários (roles/databases) — nenhum serviço novo precisa ser adicionado.
- `.github/workflows/ci.yml` já faz exatamente o padrão desejado pra `app`: `docker compose up -d
  --wait db localstack` antes de `mvn -B -pl app -am verify` com `SPRING_PROFILES_ACTIVE=docker`.
  Falta replicar isso pra `email-service` (que hoje não tem step de CI nenhum).
- `email-service/src/test/java/.../CucumberSpringConfiguration.java` hoje sobe um
  `LocalStackContainer` (Testcontainers) estaticamente; isso sai.
- O ambiente sandbox da Claude tem PostgreSQL real instalado nativamente (fora de Docker) via um
  script de setup próprio do ambiente (não versionado neste repositório): cluster `app` na porta
  5432 e cluster `email` na porta 5433 (`pg_ctlcluster`/`pg_createcluster`, Postgres 16), com os
  mesmos bancos/roles que `docker/postgres/init/01-roles.sql` e
  `docker/postgres-email-service/init/01-roles.sql` criam pro perfil `docker`
  (`jogo_acoes`/`jogo_acoes_admin`+`jogo_acoes_app` na 5432; `email_service`/
  `email_service_admin`+`email_service_app` na 5433). Ou seja: as credenciais/URLs que
  `application-sandbox.yml` passa a usar são **idênticas** às que `application-docker.yml` já usa
  — só o processo Postgres por trás é diferente (nativo vs. container).
- **Nota (2026-10-05), porta 5433 no sandbox:** a verificação da T011 da spec 05-032 registrou
  que não havia Postgres nativo na 5433 e o `email-service` subiu com a URL da 5432
  (`/email_service`). O `application-sandbox.yml` do `email-service` continua apontando para a
  5433. Falta confirmar no ambiente qual dos dois é o certo e alinhar.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como `src/test/resources/application.yml` aponta pra Postgres real sem depender de profile? | Hardcodar a URL/usuário/senha/driver reais diretamente nesse arquivo (mesmos valores de `application-docker.yml`), em vez de deixar H2 como default e esperar `SPRING_PROFILES_ACTIVE=docker` sobrescrever | resolvida | Elimina o fallback silencioso pra H2 — rodar `mvn test` sem nenhuma variável de ambiente passa a exigir Postgres de verdade, por padrão, sem exceção. |
| `SPRING_PROFILES_ACTIVE=docker` continua necessário em CI? | Sim, mas só pelos outros valores que esse profile ainda controla (endpoint do LocalStack pra SQS/SES, credenciais fake) — não mais pelo datasource, que passa a ser fixo no `application.yml` de teste | resolvida | Datasource e endpoint de infraestrutura externa são preocupações distintas; só o datasource tinha o problema do fallback pra H2. |
| Como a suíte de `email-service` aponta pro LocalStack sem Testcontainers? | `@DynamicPropertySource` sai da `CucumberSpringConfiguration`; o endpoint vira um valor fixo/configurável (`spring.cloud.aws.ses.endpoint=http://localhost:4566`, mesmo padrão de `application-docker.yml`), lido da config de teste normal | resolvida | Mesmo mecanismo que `application-docker.yml` já usa pra apontar pro LocalStack do compose — não precisa de nenhuma descoberta dinâmica de porta, porque o container não é mais efêmero/criado pelo próprio teste. |
| `email-service/pom.xml`: o que sai? | Dependências `org.testcontainers:testcontainers`, `org.testcontainers:junit-jupiter`, `org.testcontainers:localstack` saem do `pom.xml` | resolvida | Não são mais usadas por nenhum código depois da mudança acima. |
| CI de `email-service` | Novo job/step em `.github/workflows/ci.yml`, mesmo padrão do de `app`: `docker compose up -d --wait db-email-service localstack` → `SPRING_PROFILES_ACTIVE=docker mvn -B -pl email-service -am verify` → `docker compose down -v` | resolvida | Replica o padrão já validado, só trocando qual serviço de banco sobe. |
| Precisa de um mecanismo de skip pros testes que só usam Postgres (não LocalStack)? | **Não.** Postgres é considerado sempre disponível em qualquer ambiente (sandbox tem o nativo, aqui/CI tem o do `docker-compose.yml`) — as 13 classes `@SpringBootTest`/`@DataJpaTest` de `app` que só precisam de Postgres continuam sem nenhuma anotação/guarda nova; se Postgres não estiver lá, é erro de ambiente de verdade, deve falhar alto, não pular | resolvida (revista) | Decisão inicial desta spec (`@RequiresDocker` por `SPRING_PROFILES_ACTIVE`) foi descartada: além de não servir pra esse caso (Postgres não é opcional), tinha falso-skip (Postgres de pé, variável não setada) e falso-erro (variável setada, Postgres fora) — um sinal declarado, não uma checagem real. |
| E quando o teste depende de **LocalStack** especificamente (isso sim pode faltar)? | Dois mecanismos, cada um já adequado ao seu motor de execução — **não** um `@RequiresDocker` genérico: (1) `SqsEmailSenderDockerIntegrationTest` (`app`) já tinha, antes desta spec, um `@BeforeAll` que testa conectividade real (`reachable("localhost", 5432) && reachable("localhost", 4566)`) + `Assumptions.assumeTrue` — continua como está, não mexido; (2) a suíte Cucumber de `email-service` (só ela depende de LocalStack) usa a tag `@requires-docker` no `.feature` + `cucumber.filter.tags` (ver linha abaixo) | substituída (2026-10-05) | Substituída pela regra de `memory/constitution.md` ("Testes que dependem de infraestrutura em container"): marcação explícita, skip só pelo perfil `sandbox`, erro no perfil `docker`, nunca checagem em tempo de execução; implementação na spec 05-035. Raciocínio original: `@EnabledIfEnvironmentVariable` (motor Jupiter) comprovadamente **não tem efeito nenhum** sobre a suíte Cucumber (motor próprio, não Jupiter) — confirmado empiricamente: com a anotação aplicada e sem Postgres/LocalStack de pé, o teste ainda tentou conectar e falhou com erro de conexão, não SKIPPED. Já o `@BeforeAll`+checagem de socket funciona em qualquer classe Jupiter comum (roda antes do contexto Spring subir) — mas não existe equivalente dele pro Cucumber, daí o mecanismo de tag ser necessário só ali. |
| Mecanismo de tag do Cucumber, em detalhe | `@requires-docker` no topo de `register_templates.feature` (propaga pra todos os Scenarios). `email-service/pom.xml`: propriedade `cucumber.filter.tags` = `not @requires-docker` por padrão (exclui a feature inteira — nenhum Scenario selecionado, o contexto Spring nunca chega a ser criado, então nunca tenta conectar); um `<profile>` ativado por `env.SPRING_PROFILES_ACTIVE=docker` zera essa propriedade (sem filtro, roda tudo). Passado ao `cucumber.filter.tags` via `<systemPropertyVariables>` do `maven-surefire-plugin` | substituída (2026-10-05) | Substituída pela regra de `memory/constitution.md` ("Testes que dependem de infraestrutura em container"): marcação explícita, skip só pelo perfil `sandbox`, erro no perfil `docker`, nunca checagem em tempo de execução; implementação na spec 05-035. Raciocínio original: Confirmado empiricamente nos dois sentidos: sem Docker, `BUILD SUCCESS`/`Tests run: 14, Skipped: 14`; com `docker compose up -d db-email-service localstack` + `SPRING_PROFILES_ACTIVE=docker`, a suíte roda de verdade contra infraestrutura real. |
| `application-sandbox.yml` muda? | Sim — troca `datasource`/`flyway.locations` de H2 pros mesmos valores de `application-docker.yml` (Postgres real, `db/migration`), já que o sandbox agora tem Postgres nativo nas mesmas portas/roles | resolvida | O motivo original de H2 existir ali ("No Docker/Postgres available here") não é mais verdade — manter H2 só porque "sempre foi assim" contradiria a própria motivação desta spec. |
| O que acontece com `db/migration-h2`? | Removido, dos dois módulos (`app` e `email-service`) | resolvida | Depois que sandbox também migra pra Postgres real, nenhum perfil usa mais H2 em lugar nenhum do projeto — a árvore de migration H2 fica morta. |

## Estrutura de módulos/pacotes

Nenhuma classe/pacote novo. Arquivos tocados:

```
app/
  src/main/resources/application-sandbox.yml  (datasource: H2 -> Postgres real, db/migration)
  src/main/resources/db/migration-h2/         (removido)
  src/test/resources/application.yml       (datasource: H2 -> Postgres real, fixo)
  (nenhuma classe de teste tocada -- as 13 @SpringBootTest/@DataJpaTest existentes continuam
   sem anotação de skip; SqsEmailSenderDockerIntegrationTest já tinha a sua própria,
   não mexida)
email-service/
  pom.xml                                   (remove dependências testcontainers; adiciona
                                              propriedade cucumber.filter.tags + profile
                                              docker-tests; configura maven-surefire-plugin -- substituído, ver spec 05-035)
  src/main/resources/application-sandbox.yml  (datasource: H2 -> Postgres real, db/migration)
  src/main/resources/db/migration-h2/         (removido)
  src/test/resources/application.yml        (datasource: H2 -> Postgres real, fixo;
                                              + endpoint SES fixo pro LocalStack do compose)
  src/test/resources/features/register_templates.feature
                                             (+ tag @requires-docker no topo da Feature -- substituído, ver spec 05-035)
  src/test/java/.../common/testsupport/TemplateCleanupHooks.java
                                             (novo hook @After -- ver T004a)
  src/test/java/.../CucumberSpringConfiguration.java
                                             (remove LocalStackContainer/@DynamicPropertySource)
.github/workflows/ci.yml                    (novo step pra email-service)
```

## Riscos e trade-offs

- **Testes locais passam a exigir `docker compose up` antes de `mvn test`**, sempre — perde a
  conveniência de rodar `mvn test` isoladamente sem nenhuma infraestrutura externa. Aceito
  conscientemente: é exatamente a troca que esta spec pede (infraestrutura real em vez de
  substituto rápido).
- **Nenhuma forma de rodar a suíte completa de `email-service` num ambiente sem Docker** (ex.: o
  perfil sandbox da própria Claude) — já era essencialmente verdade antes (Testcontainers também
  precisa de Docker), só deixa de existir a ilusão de que bastava consertar a conexão com o
  Docker local pra resolver; a limitação é estrutural, não de configuração.
- **O sandbox passa a depender de infraestrutura provisionada fora deste repositório** (o script
  de setup do PostgreSQL nativo) — se esse script mudar ou não rodar num sandbox novo, o perfil
  `sandbox` para de funcionar sem nenhum aviso no próprio código (o erro só aparece como falha de
  conexão ao subir a aplicação). Aceito porque é a mesma situação que `docker`/CI já têm hoje
  (dependem do `docker-compose.yml` já ter rodado) — só muda o mecanismo de provisionamento.
