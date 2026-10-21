# Plan: Importação em lote das listas de domínios de e-mail temporário

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `email-service` com Spring Boot 4.1 (Spring Batch 6). Ainda sem Spring Batch, sem
  `@EnableScheduling` e sem rotas de operações: toda rota é de um cliente identificado pela
  `X-API-Key` (`ClientIdentityResolver`, nome do cliente da CLI do `api-key`).
- Schema `email_service`, migrations em `db/migration-email-service`, histórico em
  `email_service_schema_history` (05-032). `SchemaLayoutTest` confere que nada fica no `public`.
- A spec 05-047 cria o pacote `domain/` e `RecipientDomainVerifier`, chamado por
  `EmailSendService`. Esta spec depende dela.
- LocalStack do Compose já sobe com `s3` (`SERVICES: sqs,ses,s3,lambda`).
- Chave de teste do `jogo-acoes` em `docker/postgres-email-service/test-data/`, restaurada por
  `scripts/test-api-key.sh restore`, emitida pela CLI 1.0.1 (sem permissões).
- Pré-requisito: `api-key` 1.1.0, spec `013-key-permissions` (branch
  `claude/issue-14-cli-permissions-spec-v1lgmo` daquele repositório). Catálogo em
  `api-key.permissions` (`name`, `default`), lido pela CLI e pela biblioteca;
  `ApiKeyValidationResult.Valid` ganha `permissions()` e `hasPermission(String)`. Chave sem
  permissão gravada (anterior à 1.1.0) recebe as padrão.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Migrations | `V<n>__create_spring_batch_tables.sql`: cópia do `schema-postgresql.sql` do `spring-batch-core` na versão usada, sem mudar nomes (prefixo `BATCH_`, no `search_path` `email_service`). `spring.batch.jdbc.initialize-schema: never`. `V<n+1>__create_disposable_domain_tables.sql`: `disposable_domain_source (id BIGSERIAL PK, url VARCHAR(2048) NOT NULL UNIQUE, description VARCHAR(255) NOT NULL, last_downloaded_at TIMESTAMP NULL, last_updated_at TIMESTAMP NULL, content_sha256 CHAR(64) NULL, created_at TIMESTAMP NOT NULL)` e `disposable_domain (source_id BIGINT NOT NULL REFERENCES disposable_domain_source ON DELETE CASCADE, domain VARCHAR(253) NOT NULL, last_seen_execution_id BIGINT NOT NULL, PRIMARY KEY (source_id, domain))`, índice em `disposable_domain (domain)`. Repositório inicial inserido na mesma migration. | implementada | Tabelas do Spring Batch pelo Flyway, como o resto do schema (05-032). `content_sha256` diz se a lista mudou. `last_seen_execution_id` permite remover o que saiu sem apagar tudo antes. |
| Dependência | Starter do Spring Batch com repositório JDBC (no Boot 4, `spring-boot-starter-batch-jdbc`; conferir o nome na implementação). `spring.batch.job.enabled: false` (nada roda ao subir). | implementada | Execução sob pedido ou pelo agendamento. |
| Job | `importDisposableDomainsJob`, uma execução por repositório, parâmetros `sourceId` e `requestedAt` (identificadores). Passos: `downloadStep` (`Tasklet`) → `JobExecutionDecider` (`UNCHANGED` termina `COMPLETED`) → `importStep` (*chunk* de 1000) → `pruneStep` (`Tasklet`). | implementada | Um repositório que falha não afeta os outros. `requestedAt` faz cada pedido ser uma instância nova; o trabalho é idempotente, então pedir de novo depois de uma queda termina o que faltou. |
| `downloadStep` | `java.net.http.HttpClient`, tempo máximo `email.disposable-domains.download-timeout` (30 s), tamanho máximo `email.disposable-domains.max-size` (10 MB). Grava num arquivo temporário e calcula o SHA-256. Status diferente de `200`, tamanho estourado ou arquivo sem nenhuma linha de domínio → `FAILED` com o motivo. Sucesso → `last_downloaded_at = agora`; se o SHA-256 é igual a `content_sha256`, saída `UNCHANGED`. | implementada | Requisitos "último download" e "lista igual não mexe na tabela". |
| `ItemReader` | `FlatFileItemReader<String>` sobre o arquivo temporário. | implementada | Requisito da disciplina; lê em blocos, sem carregar a lista inteira. |
| `ItemProcessor` | `DisposableDomainLineProcessor`: `trim`, ignora vazia e `#` (devolve `null`, contado como *filter*), minúsculas, `IDN.toASCII`, valida forma de domínio (rótulos de 1 a 63, total até 253, pelo menos um ponto). Inválida → `null` e `WARN` com a linha. | implementada | Linha ruim não derruba a lista inteira. |
| `ItemWriter` | `JdbcBatchItemWriter`: `INSERT INTO disposable_domain (source_id, domain, last_seen_execution_id) VALUES (...) ON CONFLICT (source_id, domain) DO UPDATE SET last_seen_execution_id = EXCLUDED.last_seen_execution_id`. Conta os incluídos (linhas novas) num `ExecutionContext`. | implementada | Idempotente: rodar de novo não duplica. |
| `pruneStep` | `DELETE FROM disposable_domain WHERE source_id = ? AND last_seen_execution_id <> ?` (conta os removidos); se incluiu ou removeu algo, `last_updated_at = agora`; grava `content_sha256`. | implementada | `content_sha256` só muda quando a importação termina; uma execução que falha no meio é refeita por inteiro na próxima. |
| Execução sob pedido | `JobOperator`/`JobLauncher` com `TaskExecutor` assíncrono. `POST /disposable-domain-imports` (corpo opcional `{ "sourceId": 1 }`) → para cada repositório, se não houver execução `STARTED`/`STARTING` com o mesmo `sourceId` (`JobExplorer.findRunningJobExecutions`), lança; senão `409`. Responde `202 { imports: [{ sourceId, executionId, status }] }`. `GET /disposable-domain-imports/{executionId}` lê os metadados do Spring Batch: `status`, `startTime`, `endTime`, `read` (linhas lidas), `skipped` (linhas ignoradas), `added`, `removed`, `exitMessage`. | implementada | Leila pediu cenários para "pedir a execução"; a consulta permite a cada cenário esperar o fim. **Nota (2026-10-21):** a spec 05-054 trocou o `TaskExecutor` por uma fila de uma thread (uma importação por vez) e passou a criar as execuções com `READ_COMMITTED`: em paralelo, o PostgreSQL cancelava às vezes uma das importações. |
| Repositórios pela API | `POST /disposable-domain-sources { url, description }` → `201` (`409` endereço repetido, `400` esquema diferente de `http`/`https` ou sem descrição). `GET /disposable-domain-sources` → lista com `id`, `url`, `description`, `lastDownloadedAt`, `lastUpdatedAt`, `domainCount`, `lastImport` (`executionId`, `status`, `endTime`, `exitMessage`). | implementada | Os quatro campos pedidos pela Leila, mais o resultado da última execução. |
| Permissões | `api-key` 1.1.0 (`scripts/install-api-key-lib.sh` baixa a release nova). Catálogo em `application.yml` (vale para todos os ambientes): `api-key.permissions: [{name: email:send, default: true}, {name: disposable-email:admin}]`. `ApiKeyAuthenticationFilter` guarda as permissões do `Valid` num atributo da requisição, ao lado do nome do cliente. Um `HandlerInterceptor` (`auth/PermissionInterceptor`) exige `email:send` em `/emails` e `/templates/**`, e `disposable-email:admin` em `/disposable-domain-*`; sem a permissão, `403` com `Missing permission: <nome>`. No `docker`: cliente `operations` com chave gerada por `generate --client operations --permissions disposable-email:admin` e salva no dump de `docker/postgres-email-service/test-data/` (o `test-api-key.sh` restaura as duas). A chave de teste do `jogo-acoes` continua a mesma (recebe `email:send` por ser anterior à 1.1.0). | implementada (decidida por Leila, 2026-10-14) | A biblioteca só informa as permissões; o serviço decide e responde `403` (FR12 da `013`). Mapa rota → permissão num lugar só. |
| Agendamento | `@Scheduled(cron = "${email.disposable-domains.import-cron:-}")` (`-` desliga). `config-repo/email-service-staging.yml`/`-production.yml`: `0 0 4 * * *`. | implementada (decidida por Leila, 2026-10-14) | Desligado no `docker` e nos testes. |
| Uso no envio | `domain/DisposableDomainChecker.isDisposable(domain)`: `SELECT 1 FROM disposable_domain WHERE domain IN (:domínio e cada domínio acima dele, até o penúltimo rótulo) LIMIT 1`. `RecipientDomainVerifier` (05-047) chama antes da lista branca; positivo → `RecipientDomainRejectedException` com motivo `DISPOSABLE` e a mensagem `Recipient domain is a disposable e-mail domain: <domínio>`. | implementada | Uma consulta com índice. Mesmo `422` da 05-047, mensagem diferente. |
| Contrato HTTP | `docs/openapi-email-service.yaml`: tag `disposable-domains`, as quatro rotas, esquemas `DisposableDomainSource`, `DisposableDomainSourceCreateRequest`, `DisposableDomainImport`, `DisposableDomainImportRequest`; `403` documentado em todas as rotas (inclusive `/emails`, `/templates`, `/sender`); `POST /emails`, descrição do `422` cita os dois motivos. | implementada | Contract-first. |
| Listas nos testes | Bucket `disposable-lists` no S3 do LocalStack; o passo do Gherkin grava o arquivo com o SDK e cadastra a URL `http://localhost:4566/disposable-lists/<arquivo>` (estilo *path*). No container, `http://localstack:4566/...`. "Repositório fora do ar": URL de um objeto que não existe (`404`) e uma porta sem nada escutando. | implementada | HTTP de verdade, conteúdo controlado por cenário, sem internet. Conferir na implementação que o LocalStack responde `GET` anônimo ao objeto. |

## Estrutura de módulos/pacotes

- `email-service/pom.xml`: starter do Spring Batch.
- `specs/05-048-.../data-model.md`: DER das tabelas desta feature.
- `email-service/src/main/resources/db/migration-email-service/V<n>__create_spring_batch_tables.sql`,
  `V<n+1>__create_disposable_domain_tables.sql`.
- `email-service/src/main/java/.../disposable/` (novo): `DisposableDomainSource`,
  `DisposableDomainSourceRepository`, `DisposableDomainSourceController`,
  `DisposableDomainImportController`, `DisposableDomainImportService` (lança e consulta),
  `batch/ImportDisposableDomainsJobConfig`, `batch/DownloadListTasklet`,
  `batch/DisposableDomainLineProcessor`, `batch/PruneDomainsTasklet`,
  `DisposableDomainSourceAlreadyExistsException`, `DisposableDomainImportAlreadyRunningException`,
  `DisposableListDownloadException`.
- `email-service/src/main/java/.../domain/DisposableDomainChecker.java`; `RecipientDomainVerifier`
  chama.
- `email-service/src/main/java/.../auth/PermissionInterceptor.java`; `ApiKeyAuthenticationFilter` guarda as permissões.
- `email-service/pom.xml`: `api-key` 1.1.0; `scripts/install-api-key-lib.sh`.
- `common/ApiExceptionHandler.java`: `403`, `409`.
- `config-repo/email-service-*.yml`: `email.disposable-domains.*` (o catálogo fica no `application.yml`).
- `docker/postgres-email-service/test-data/`: chave de teste do cliente `operations`;
  `scripts/test-api-key.sh`.
- `docs/openapi-email-service.yaml`, `README.md`, `docs/disciplina/caderno-de-testes.md` (seção do
  email-service: cadastrar repositório e pedir a execução, com `curl`).

## Verificações (antes das correções, falhando)

1. `disposable_domains.feature` com os casos de `spec.md`, "Cenários". Hoje: `404` nas rotas.
2. `send_email.feature`: destinatário `x@disposable.test` (domínio com MX na zona de teste da
   05-047, mas na lista importada) → `422` com o motivo de domínio temporário; `x@sub.disposable.test`
   → `422`. Hoje: `202`.
3. `DisposableDomainLineProcessorTest`: vazia, comentário, maiúsculas, espaços, IDN, linha
   inválida. Hoje não compila; registrar.
4. `ImportDisposableDomainsJobTest` (contexto Spring com o job, PostgreSQL e LocalStack reais):
   lista de 100 mil linhas importada; execução parada no meio do `importStep` (`JobOperator.stop`)
   e pedida de novo → sem duplicatas, removidos corretos.
5. `SchemaLayoutTest` continua verde com as tabelas do Spring Batch.
6. `PermissionInterceptor`, nos `.feature`: chave do `jogo-acoes` nas rotas de listas → `403`; chave de operações em `POST /emails` e `POST /templates` → `403`. Hoje: `404` nas rotas novas e `202`/`201` nas antigas.

## Desvios na implementação (2026-10-14)

O que a implementação fez diferente da tabela acima, e por quê:

- **Não existe rota `/sender`**: o remetente é definido por operações no banco
  (`scripts/set-email-sender.sh`, spec 05-031). `email:send` vale para `/emails` e `/templates`.
- **Linha que não é domínio**: o `DisposableDomainLineProcessor` lança
  `InvalidDisposableDomainLineException`, e o `importStep` é `faultTolerant()` com `skip` dessa
  exceção, sem limite. A linha entra no `processSkipCount` do Spring Batch, que é o `skipped` da
  execução. Linha em branco e comentário continuam `null` (filtro) e não contam como ignoradas. É
  o uso de *skip* que o Spring Batch oferece para isso, e a contagem sai dos metadados dele.
- **Decisor**: no lugar de uma classe `JobExecutionDecider`, as transições do próprio fluxo a
  partir do código de saída do `downloadStep` (`on("UNCHANGED").end()`, `on("FAILED").fail()`,
  `on("*").to(importStep)`). Mesmo efeito, uma classe a menos.
- **Contagem de incluídos**: o *writer* é o `JdbcBatchItemWriter` com o *upsert* da tabela; o
  `downloadStep` guarda quantos domínios o repositório tinha, e o `pruneStep` calcula os
  incluídos pela diferença antes de remover. Limite: numa execução que falhou depois de gravar
  domínios novos, a seguinte não os conta em `added`, e só muda `last_updated_at` se incluir ou
  remover mais algum (ver "Riscos e trade-offs").
- **Exceções a mais**: `DisposableDomainSourceNotFoundException` (`404` ao pedir a execução de um
  repositório que não existe) e `DisposableDomainImportNotFoundException` (`404` ao ler uma
  execução que não existe), além de `InvalidDisposableDomainLineException` (acima). O contrato
  ganhou esses `404`.
- **`DisposableDomainChecker` no pacote `disposable/`**, junto da tabela que ele consulta, e não em
  `domain/`. O `RecipientDomainVerifier` (`domain/`) chama o serviço; nenhum pacote usa
  repositório de outro.
- **Listas nos testes**: gravadas no S3 do LocalStack com `PUT` HTTP simples (o LocalStack aceita
  sem assinatura), sem o SDK do S3 como dependência de teste. Os casos que o S3 não faz (servidor
  que não responde, pedido enquanto outro roda) usam um servidor HTTP da própria suíte
  (`DisposableListFiles`), e "porta sem nada escutando" usa uma porta livre.
- **Última execução de cada repositório** (`lastImport`): uma consulta direta às tabelas do Spring
  Batch, porque a API dele carregaria todas as instâncias do *job*.
- **Cliente Feign do `app`**: o `app` gera o cliente a partir do mesmo
  `docs/openapi-email-service.yaml`; a tag nova virava um cliente sem configuração, e o contexto
  não subia. O `app/pom.xml` passou a gerar só `Templates` e `Emails` (`apisToGenerate`): as rotas
  das listas são de operações, e a chave do `app` não tem a permissão delas.
- **Campos nulos não aparecem no JSON** das respostas (`lastDownloadedAt`, `lastUpdatedAt`,
  `lastImport`, `exitMessage`), como nas outras respostas do serviço.

## Riscos e trade-offs

- **Lista truncada** (não vazia) remove domínios legítimos até a próxima execução.
- **Execução que falhou no meio do `importStep`**: a seguinte termina o trabalho, mas os domínios
  que a anterior já gravou não entram no `added` dela nem, sozinhos, mudam `last_updated_at`.
- **Fonte pública mudar de endereço**: o repositório passa a falhar; aparece em `lastImport`, não
  em alerta (Iteração 6).
- **Falso positivo**: um domínio legítimo numa lista pública bloqueia quem o usa. Sem lista de
  permissão nesta spec.
- **Chave de operações** é mais um segredo para operações guardar em `staging`/`production`.
- **Exceções sem teste possível**: nenhuma prevista. Queda do processo no meio da execução é
  simulada com `JobOperator.stop`, não matando o JVM.
