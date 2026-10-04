# Plan: Serviço de E-mail — validação real de API-KEY

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `email-service`: Spring Boot 4.1.0, Java 21, Flyway com migrations em `classpath:db/migration`
  (schema `public` do banco `email_service`, container `db-email-service`, porta 5433) — muda
  para um schema próprio nesta spec (ver "Um schema por serviço").
  Autenticação hoje: `auth/ApiKeyAuthenticationFilter` (`OncePerRequestFilter`, rejeita só
  header ausente/vazio) e `auth/ClientIdentityResolver` (`@RequestScope`, devolve o texto do
  header como id do cliente).
- `api-key` v1.0.1 (mesmo Spring Boot 4.1.0, Java 21): `api-key-validation` depende de
  `api-key-core` (entidade `ApiKey` mapeada em `@Table(name = "api_keys")`, `ApiKeyRepository`,
  `ApiKeyHasher` lendo `${API_KEY_HMAC_PEPPER:}`). O jar de `api-key-core` traz as migrations
  da tabela em `db/migration-api-key` (`V1__create_api_keys_table.sql`,
  `V2__add_revoked_at_to_api_keys.sql`) — fora do `db/migration` padrão desde a 1.0.1 — e a CLI
  guarda o histórico na tabela `api_key_schema_history`, não em `flyway_schema_history`. Quem
  executa as migrations é a CLI — a biblioteca não depende de Flyway
  ([plan 008](https://github.com/lalgarve/api-key/blob/main/specs/008-validate-api-key/plan.md),
  "Migrations no core, Flyway só na CLI"). Sem auto-configuração: o consumidor registra os
  pacotes (`scanBasePackages`, `@EntityScan`, `@EnableJpaRepositories` —
  [guia de integração HTTP](https://github.com/lalgarve/api-key/blob/main/specs/008-validate-api-key/http-integration.md)).
- A release v1.0.1 anexa três jars: o da CLI (`api-key-1.0.1.jar`) e os das duas bibliotecas
  (`api-key-core-1.0.1.jar`, `api-key-validation-1.0.1.jar`), cada um com o próprio `pom.xml`
  embutido em `META-INF/maven/`. As bibliotecas também estão no GitHub Packages, que exige token
  até para leitura.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Um schema por serviço, nunca o `public` | Cada dono de tabelas tem o próprio schema no banco: `email_service` para as tabelas do Serviço de E-mail e `api_key` para as da CLI. Nada fica no `public`. Cada Flyway migra só o próprio schema (`spring.flyway.schemas`), com histórico `<schema>_schema_history` (mesmo padrão do `api-key`: `api_key_schema_history` e `email_service_schema_history`) | resolvida (Leila, 2026-10-04) | O objetivo é poder colocar todos os serviços numa única instância do PostgreSQL, e até num único banco, quando isso for conveniente (no início, o custo de memória de várias instâncias pesa). Com schemas próprios, juntar ou separar bancos é só mudar a URL de conexão: nenhum nome colide, nenhum Flyway depende da ordem em que o outro roda e não precisa de `baseline-on-migrate`. Os containers separados de hoje (`db`, `db-email-service`) continuam funcionando, mas deixam de ser premissa. Regra geral registrada na [Issue #111](https://github.com/lalgarve/jogo-acoes/issues/111), que leva isso para `memory/constitution.md`. |
| Como o `email-service` aponta cada entidade para o seu schema | Conexão com `currentSchema=email_service` e `spring.jpa.properties.hibernate.default_schema: email_service` para as entidades do serviço; `META-INF/orm.xml` sobrescreve só a entidade `ApiKey` (`<table name="api_keys" schema="api_key"/>`) | resolvida (Leila, 2026-10-04) | Explícito e restrito a uma entidade, sem tocar no código da biblioteca. Alternativa: `currentSchema=email_service,api_key` (`search_path` com os dois) sem `orm.xml`, mais simples, mas faz toda consulta procurar nos dois schemas e depende de o `ddl-auto: validate` do Hibernate seguir o `search_path`. Escolhido por ser explícito e previsível; o T002 verifica que funciona. |
| Local das migrations e tabela de histórico do `email-service` | `classpath:db/migration-email-service`, `spring.flyway.schemas: email_service` e `spring.flyway.table: email_service_schema_history` | resolvida | Regra de nomes por serviço da [Issue #111](https://github.com/lalgarve/jogo-acoes/issues/111), no mesmo padrão do `api-key` (`db/migration-api-key`, `api_key_schema_history`). Com a 1.0.1 o jar de `api-key-core` já não colide com `db/migration` (na 1.0.0 trazia um `V1` lá), mas nome padrão em qualquer um dos lados volta a abrir a porta para o mesmo problema. Fica fora de `db/migration` porque a varredura do Flyway é recursiva. Pré-produção: mudar de schema e renomear não exige migração do histórico. |
| Como distribuir `api-key-validation` para o build | Baixar da release do GitHub uma única vez e instalar no repositório Maven local (`~/.m2`): `scripts/install-api-key-lib.sh` baixa `api-key-core-1.0.1.jar` e `api-key-validation-1.0.1.jar` da release e o POM pai (`pom.xml` da tag `v1.0.1`), instala os três com `mvn install:install-file` e não faz nada se a versão já estiver no `~/.m2`. Depois disso o Maven resolve a dependência como qualquer outra, sem GitHub. O `~/.m2` já é cacheado no CI (`actions/setup-java` com `cache: maven`); no `email-service/Dockerfile`, o mesmo script roda com cache de BuildKit (`RUN --mount=type=cache,target=/root/.m2`) | resolvida (Leila, 2026-10-04) | É o pedido de "cache do download do GitHub, como o Maven": o GitHub só é acessado na primeira vez em cada máquina, e não precisa de credencial (release pública). O POM pai é necessário porque os POMs embutidos nos jars herdam de `api-key-parent`: sem ele, o Maven não enxerga as dependências transitivas (`spring-boot-starter-data-jpa`). Testado nesta sessão: com os três instalados, `dependency:tree` de um consumidor resolve `api-key-validation → api-key-core → spring-boot-starter-data-jpa`. Alternativas: **GitHub Packages** (publicado desde a 1.0.1), que exige token com `read:packages` em todo lugar que builda e está bloqueado pela política de rede do sandbox da Claude (testado nesta sessão); **compilar a tag a partir do código-fonte**, que funciona mas recompila a cada máquina sem ganho. |
| Integração HTTP | Alternativa A do guia (`OncePerRequestFilter`): o `ApiKeyAuthenticationFilter` existente passa a chamar `ApiKeyValidator.validate` e guarda `clientName` num atributo da requisição; `ClientIdentityResolver` lê esse atributo | resolvida | O filtro já existe e já escreve o 401 sozinho; a troca não muda o contrato HTTP. Protege também rotas inexistentes (401 em vez de 404). |
| Política de resposta | `401` com o corpo atual (`Error.message = "Missing or invalid X-API-Key"`) para todos os motivos; motivo exato em log `INFO`, sem a chave | resolvida | Mesma sugestão do guia para API não pública a todos: evita enumeração de chaves. Para o chamador, nada muda em relação ao esqueleto. |
| Pepper do HMAC | `API_KEY_HMAC_PEPPER` por variável de ambiente; `application-docker.yml`/`application-sandbox.yml` usam o valor de teste como padrão (`${API_KEY_HMAC_PEPPER:jogo-acoes-test-pepper-not-a-secret}`); `application-production.yml` sem padrão | resolvida | Mesmo padrão já usado para as credenciais do banco por perfil. |
| Pepper ausente em `production` | Falhar na subida (verificação no início da aplicação), não na primeira requisição | resolvida | `ApiKeyHasher` só lança `MissingHmacPepperException` ao calcular um hash — sem verificação na subida, o serviço sobe "saudável" e responde 500 em toda chamada. |
| Permissão do papel `email_service_app` (`staging`/`production`) | `USAGE` e `SELECT`/`INSERT`/`UPDATE`/`DELETE` no schema `email_service`; só `USAGE` no schema `api_key` e `SELECT` em `api_key.api_keys`. Quem emite/revoga chave é a CLI, com o papel admin | resolvida | O serviço só lê as chaves. A equipe que cuida do banco nesses ambientes (`application-production.yml`) aplica os grants; em `docker`/`sandbox` o serviço já usa o papel admin. Os `ALTER DEFAULT PRIVILEGES` de `docker/postgres-email-service/init/01-roles.sql` passam do `public` para o schema `email_service`. |
| Chave de teste | Gerada pela CLI v1.0.0 para o cliente `jogo-acoes`, sem expiração, e atualizada para a 1.0.1 (tabela de histórico renomeada para `api_key_schema_history`, como o README do `api-key` manda para bancos criados pela 1.0.0); o schema `api_key` salvo em `docker/postgres-email-service/test-data/api-key-test-data.sql`, restaurado por `scripts/test-api-key.sh restore` | resolvida (entregue com esta spec) | A chave em texto puro não é recuperável do banco; o dump deixa reaproveitar a mesma chave em qualquer banco novo. Valores versionados de propósito, só `docker`/`sandbox`. |
| Chaves dos cenários Gherkin (`client-a`, `client-b`, expirada, revogada) | Criadas pelo próprio teste num hook `@Before`, gravando em `api_key.api_keys` com `ApiKeyHasher` (pepper de teste) e apagadas no `@After` (padrão de `TemplateCleanupHooks`) | resolvida | Cada cenário controla as próprias chaves e não depende da chave de teste fixa. O schema `api_key` precisa existir antes: no CI e localmente, `scripts/test-api-key.sh restore` roda antes do `mvn verify`. |

Todas as decisões estão resolvidas — cada uma virou commit `decision:` atualizando esta tabela.

## Estrutura de módulos/pacotes

Tudo dentro de `email-service`:

```
email-service/src/main/
  java/dev/leilaalgarve/jogoacoes/emailservice/
    EmailServiceApplication.java    # + scanBasePackages/@EntityScan/@EnableJpaRepositories
                                    #   com os pacotes do serviço e dev.leilaalgarve.apikey.*
    auth/
      ApiKeyAuthenticationFilter.java  # chama ApiKeyValidator; atributo apiKey.clientName
      ClientIdentityResolver.java      # lê o atributo, não mais o header
      HmacPepperStartupCheck.java      # falha a subida sem API_KEY_HMAC_PEPPER
  resources/
    META-INF/orm.xml                # schema api_key só para ApiKey
    application-*.yml               # currentSchema/flyway.schemas/default_schema = email_service
    db/migration-email-service/     # migrations movidas de db/migration (Issue #111)
```

`EmailServiceApplication` passa a listar explicitamente os pacotes do serviço em
`@EntityScan`/`@EnableJpaRepositories`, já que essas anotações substituem a varredura padrão
em vez de somar a ela.

## Riscos e trade-offs

- **Biblioteca vinda de download, não de repositório Maven**: uma
  máquina nova que não rode o script falha com "dependência não encontrada". Mitigado pelo script
  único e idempotente, chamado no CI e no Dockerfile. Revisar se aparecer um segundo consumidor
  da biblioteca.
- **Mesmo banco para dois donos de schema**: a CLI e o serviço rodam Flyway no mesmo banco. A
  separação por schema (cada um com sua tabela de histórico) é o que evita conflito; uma
  CLI rodada sem `currentSchema=api_key` criaria a tabela no `public`, que nenhum serviço usa.
  Documentado no README da chave de teste e em `scripts/test-api-key.sh`.
- **Valores de teste versionados** (chave e pepper): aceitos de propósito, só para
  `docker`/`sandbox`; `application-production.yml` não tem padrão para o pepper e a verificação
  na subida impede rodar sem ele.
- **Templates com o dono antigo**: registros cujo dono é o texto de uma chave de esqueleto
  ficam órfãos. Pré-produção — podem ser apagados.
