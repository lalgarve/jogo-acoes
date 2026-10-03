# Plan: Serviço de E-mail — validação real de API-KEY

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `email-service`: Spring Boot 4.1.0, Java 21, Flyway com migrations em `classpath:db/migration`
  (schema `public` do banco `email_service`, container `db-email-service`, porta 5433).
  Autenticação hoje: `auth/ApiKeyAuthenticationFilter` (`OncePerRequestFilter`, rejeita só
  header ausente/vazio) e `auth/ClientIdentityResolver` (`@RequestScope`, devolve o texto do
  header como id do cliente).
- `api-key` v1.0.0 (mesmo Spring Boot 4.1.0, Java 21): `api-key-validation` depende de
  `api-key-core` (entidade `ApiKey` mapeada em `@Table(name = "api_keys")`, `ApiKeyRepository`,
  `ApiKeyHasher` lendo `${API_KEY_HMAC_PEPPER:}`). O jar de `api-key-core` traz as migrations
  da tabela em `db/migration` (`V1__create_api_keys_table.sql`, `V2__add_revoked_at_to_api_keys.sql`),
  mas quem as executa é a CLI — a biblioteca não depende de Flyway
  ([plan 008](https://github.com/lalgarve/api-key/blob/main/specs/008-validate-api-key/plan.md),
  "Migrations no core, Flyway só na CLI"). Sem auto-configuração: o consumidor registra os
  pacotes (`scanBasePackages`, `@EntityScan`, `@EnableJpaRepositories` —
  [guia de integração HTTP](https://github.com/lalgarve/api-key/blob/main/specs/008-validate-api-key/http-integration.md)).
- A release v1.0.0 publica só o jar executável da CLI; `api-key-core`/`api-key-validation` não
  estão em nenhum repositório Maven.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde fica a tabela `api_keys` | Mesmo banco `email_service`, schema próprio `api_key`. A CLI cria e migra o schema (`currentSchema=api_key` na URL + `SPRING_FLYWAY_SCHEMAS=api_key`), com o próprio `flyway_schema_history` lá dentro | resolvida | O validador usa o `DataSource`/`EntityManager` do próprio serviço — um banco separado exigiria dois datasources no `email-service`. Mesmo banco, schema separado: cada Flyway (CLI e serviço) fica com seu histórico, sem disputar o `public`. Verificado nesta sessão: a CLI v1.0.0 gerou a chave de teste assim, contra o `db-email-service` real. |
| Como o `email-service` enxerga `api_key.api_keys` | `META-INF/orm.xml` do `email-service` sobrescreve só o schema da entidade `ApiKey` (`<table name="api_keys" schema="api_key"/>`) | proposta — confirmar no T002 | Explícito e restrito a uma entidade. Alternativa: `currentSchema=public,api_key` na URL do serviço — mais simples, mas depende de `search_path` também para o `ddl-auto: validate` do Hibernate e mistura os dois schemas para todas as consultas. O teste de verificação do T002 decide entre as duas. |
| Conflito de migrations | Mover as migrations do `email-service` para `classpath:db/email-service/migration` (`spring.flyway.locations`) | resolvida | Com o jar de `api-key-core` no classpath, `classpath:db/migration` passa a encontrar dois `V1` (o do serviço e o da `api_keys`) e o Flyway recusa subir. A varredura é recursiva, então um subdiretório de `db/migration` não resolve — o local precisa ficar fora dele. Pré-produção: renomear o caminho não exige migração do histórico. |
| Como distribuir `api-key-validation` para o build | Instalar a partir do código-fonte da tag: `git clone --depth 1 --branch v1.0.0` + `mvn -B install -DskipTests -pl api-key-validation -am`, num script versionado (`scripts/install-api-key-lib.sh`), chamado no CI, no `email-service/Dockerfile` (estágio de build) e uma vez em cada máquina/sandbox | proposta — confirmar | Não exige mudar o `api-key` nem credencial nenhuma (repositório público). Alternativas: **GitHub Packages** — exige token com `read:packages` em todo lugar que builda, inclusive no `docker compose build` e no sandbox, e mudar o workflow de release do `api-key`; **JitPack** — zero mudança no `api-key`, mas bloqueado pela política de rede do sandbox da Claude (testado nesta sessão) e dependente de um build de terceiro; **Maven Central** — verificação de namespace e assinatura, desproporcional para um consumidor. |
| Integração HTTP | Alternativa A do guia (`OncePerRequestFilter`): o `ApiKeyAuthenticationFilter` existente passa a chamar `ApiKeyValidator.validate` e guarda `clientName` num atributo da requisição; `ClientIdentityResolver` lê esse atributo | resolvida | O filtro já existe e já escreve o 401 sozinho; a troca não muda o contrato HTTP. Protege também rotas inexistentes (401 em vez de 404). |
| Política de resposta | `401` com o corpo atual (`Error.message = "Missing or invalid X-API-Key"`) para todos os motivos; motivo exato em log `INFO`, sem a chave | resolvida | Mesma sugestão do guia para API não pública a todos: evita enumeração de chaves. Para o chamador, nada muda em relação ao esqueleto. |
| Pepper do HMAC | `API_KEY_HMAC_PEPPER` por variável de ambiente; `application-docker.yml`/`application-sandbox.yml` usam o valor de teste como padrão (`${API_KEY_HMAC_PEPPER:jogo-acoes-test-pepper-not-a-secret}`); `application-production.yml` sem padrão | resolvida | Mesmo padrão já usado para as credenciais do banco por perfil. |
| Pepper ausente em `production` | Falhar na subida (verificação no início da aplicação), não na primeira requisição | resolvida | `ApiKeyHasher` só lança `MissingHmacPepperException` ao calcular um hash — sem verificação na subida, o serviço sobe "saudável" e responde 500 em toda chamada. |
| Permissão do papel `email_service_app` (`staging`/`production`) | `USAGE` no schema `api_key` e só `SELECT` em `api_key.api_keys`; quem emite/revoga chave é a CLI, com o papel admin | resolvida | O serviço só lê. A equipe que cuida do banco nesses ambientes (`application-production.yml`) aplica os grants; em `docker`/`sandbox` o serviço já usa o papel admin. |
| Chave de teste | Gerada pela CLI v1.0.0 para o cliente `jogo-acoes`, sem expiração; o schema `api_key` salvo em `docker/postgres-email-service/test-data/api-key-test-data.sql`, restaurado por `scripts/test-api-key.sh restore` | resolvida (entregue com esta spec) | A chave em texto puro não é recuperável do banco; o dump deixa reaproveitar a mesma chave em qualquer banco novo. Valores versionados de propósito, só `docker`/`sandbox`. |
| Chaves dos cenários Gherkin (`client-a`, `client-b`, expirada, revogada) | Criadas pelo próprio teste num hook `@Before`, gravando em `api_key.api_keys` com `ApiKeyHasher` (pepper de teste) e apagadas no `@After` (padrão de `TemplateCleanupHooks`) | resolvida | Cada cenário controla as próprias chaves e não depende da chave de teste fixa. O schema `api_key` precisa existir antes: no CI e localmente, `scripts/test-api-key.sh restore` roda antes do `mvn verify`. |

Decisões marcadas "proposta — confirmar" bloqueiam a implementação até serem confirmadas —
viram commit `decision:` quando resolvidas, atualizando esta tabela no mesmo commit.

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
    META-INF/orm.xml                # schema api_key só para ApiKey (se confirmado no T002)
    db/email-service/migration/     # migrations movidas de db/migration
```

`EmailServiceApplication` passa a listar explicitamente os pacotes do serviço em
`@EntityScan`/`@EnableJpaRepositories`, já que essas anotações substituem a varredura padrão
em vez de somar a ela.

## Riscos e trade-offs

- **Dependência compilada a partir do código-fonte** (se a proposta for confirmada): o build
  passa a depender de um passo extra e de acesso ao GitHub; uma máquina nova que esqueça o
  script falha com "dependência não encontrada". Mitigado pelo script único, chamado no CI e
  no Dockerfile. Revisar se aparecer um segundo consumidor da biblioteca.
- **Mesmo banco para dois donos de schema**: a CLI e o serviço rodam Flyway no mesmo banco. A
  separação por schema (cada um com seu `flyway_schema_history`) é o que evita conflito; uma
  CLI rodada sem `currentSchema=api_key` criaria a tabela no `public`, ao lado das do serviço.
  Documentado no README da chave de teste e em `scripts/test-api-key.sh`.
- **Valores de teste versionados** (chave e pepper): aceitos de propósito, só para
  `docker`/`sandbox`; `application-production.yml` não tem padrão para o pepper e a verificação
  na subida impede rodar sem ele.
- **Templates com o dono antigo**: registros cujo dono é o texto de uma chave de esqueleto
  ficam órfãos. Pré-produção — podem ser apagados.
