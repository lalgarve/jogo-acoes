# Tasks: Importação em lote das listas de domínios de e-mail temporário

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** testes do comportamento novo primeiro, falhando pelo motivo esperado; depois as
correções. Depende da spec 05-047 (zona de DNS de teste, `RecipientDomainVerifier`, `422` no
`POST /emails`) e da spec `013-key-permissions` do `api-key` implementada e publicada (1.1.0),
que é pré-requisito.

Issue-épico: [#187](https://github.com/lalgarve/jogo-acoes/issues/187).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `api-key` 1.1.0 no `email-service` (`pom.xml`, `scripts/install-api-key-lib.sh`) e o catálogo `api-key.permissions` no `application.yml`. Cliente `operations` no `docker`: gerar a chave de teste com `generate --client operations --permissions disposable-email:admin`, salvar no dump de `docker/postgres-email-service/test-data/` (README com a chave em texto puro), `scripts/test-api-key.sh restore` restaura as duas. Trocar a linha `OPS_API_KEY=` da verificação manual abaixo pela chave gerada | `api-key` 013 publicada | [P] | — |
| ~~T002~~ | `disposable.test` (com MX) na zona de DNS de teste da 05-047 | spec 05-047 T001 | [P] | — |
| ~~T003~~ | `disposable_domains.feature` com os casos de `spec.md`, "Cenários", e os passos que gravam listas no S3 do LocalStack. Confirmar que falham (`404` nas rotas) | T001 | [P] | — |
| ~~T004~~ | `send_email.feature`: destinatário em domínio temporário e em subdomínio dele → `422`. Confirmar que falham (`202`) | T002 | [P] | — |
| ~~T005~~ | `DisposableDomainLineProcessorTest` e `ImportDisposableDomainsJobTest`. Registrar que não compilam antes do pacote `disposable/` | — | [P] | — |
| ~~T006~~ | Contrato: tag `disposable-domains`, rotas, esquemas, `403` em todas as rotas; `422` de `POST /emails` com os dois motivos | — | [P] | — |
| ~~T007~~ | Migrations (tabelas do Spring Batch e das listas, repositório inicial), starter do Spring Batch, `spring.batch.*`. Confirmar `SchemaLayoutTest` verde | — | | — |
| ~~T008~~ | Job (`downloadStep`, decisor, `importStep` com `ItemReader`/`ItemProcessor`/`ItemWriter`, `pruneStep`). Confirmar T005 verde | T005, T007 | | — |
| ~~T009~~ | Rotas de repositórios e de execução, `PermissionInterceptor` (permissões em todas as rotas), `403`/`409` no `ApiExceptionHandler`, `email.disposable-domains.*` em `config-repo/` e no `application.yml` de teste. Confirmar T003 verde | T001, T003, T006, T008 | | — |
| ~~T010~~ | `DisposableDomainChecker` e a chamada em `RecipientDomainVerifier`. Confirmar T004 verde | T004, T007, spec 05-047 | | — |
| ~~T011~~ | Agendamento (`email.disposable-domains.import-cron`), desligado no `docker` e ligado em `staging`/`production` | T008 | [P] | — |
| ~~T012~~ | Documentação: `README.md` (cliente `operations`, como cadastrar repositório e pedir a execução); `docs/disciplina/caderno-de-testes.md`, seção do email-service, com os `curl` da verificação manual | T009 | [P] | — |
| ~~T013~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: `DisposableDomainSourceAlreadyExistsException`, `DisposableDomainImportAlreadyRunningException`, `DisposableListDownloadException`) e os branches de exceção adicionados ou alterados (`400`/`403`/`409` das rotas; cada falha do `downloadStep`: status, tempo, tamanho, lista vazia; linha inválida no processador; execução parada e refeita; motivo `DISPOSABLE` no `422`); para cada um, apontar o teste que o exercita. O que não tiver teste é justificado em `plan.md` e avisado no chat ao abrir a PR. Registrar a lista aqui | T008–T010 | | — |
| ~~T014~~ | Rodar a suíte completa do `email-service` e `mvn -pl app -am verify` — confirmar verde | T011–T013 | | — |
| T015 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T015)" abaixo e conferir os resultados esperados | T014 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico, ou uma Issue própria quando grande o
  suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de tipo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Registro da implementação (2026-10-14)

Rodado num container da Claude, com o Compose (bancos, LocalStack, `dns`) de pé e o
`email-service` do branch rodando do jar, na rede do Compose. O roteiro da verificação manual
(T015) foi seguido do passo 1 ao 10 contra esse `email-service`, com os resultados esperados;
fica para a Leila rodar e registrar.

- **T001**: `api-key-validation` 1.1.0 no `pom.xml` e no `scripts/install-api-key-lib.sh`;
  catálogo `api-key.permissions` no `application.yml` (e no de teste, que o substitui). A tabela
  `api_key_permissions` entrou no `META-INF/orm.xml` (schema `api_key`). Chave de operações
  gerada com a CLI 1.1.0 (compilada da tag `v1.1.0`, que não anexa a CLI à release) e salva no
  dump; `ApiKeyIntegrationVerificationTest` confere as duas chaves e as permissões de cada uma
  (`jogo-acoes` → `email:send`, `operations` → `disposable-email:admin`).
- **T002**: `disposable.test` com MX já estava na zona de teste desde a 05-047.
- **T003/T004**: antes das tabelas, os 20 cenários novos de `disposable_domains.feature` e da
  `Rule` nova de `send_email.feature` falharam com `relation "email_service.disposable_domain_source"
  does not exist` (o passo que limpa ou grava a tabela vem antes das rotas); os cenários que já
  existiam passaram.
- **T005**: `DisposableDomainLineProcessorTest` e `ImportDisposableDomainsJobTest` não
  compilavam antes do pacote `disposable/` (`cannot find symbol`).
- **T006**: tag `disposable-domains`, quatro rotas, esquemas, respostas `403` (com a permissão que
  falta) em todas as rotas, `404` de repositório/execução inexistente, `422` de `POST /emails` com
  os dois motivos.
- **T007/T008**: `V5__create_spring_batch_tables.sql` (cópia do `schema-postgresql.sql` do
  `spring-batch-core` 6.0.4), `V6__create_disposable_domain_tables.sql` com o repositório
  inicial; `spring-boot-starter-batch-jdbc`; job em `disposable/batch/`. A lista de 100 mil
  linhas importa em poucos segundos (os dois testes do job juntos levaram 11,5 s), e a execução
  parada com `JobOperator.stop` no meio do `importStep` termina `STOPPED` sem remover nada; o
  pedido seguinte termina `COMPLETED` com 100 mil domínios e o domínio antigo removido.
- **T009/T010**: rotas, `PermissionInterceptor`, `ApiExceptionHandler`, `DisposableDomainChecker`
  chamado pelo `RecipientDomainVerifier` antes da lista branca. Desvios em `plan.md`, "Desvios na
  implementação".
- **T011**: `@Scheduled(cron = "${email.disposable-domains.import-cron:-}")`; `0 0 4 * * *` em
  `config-repo/email-service-staging.yml` e `-production.yml`.
- **T012**: `README.md`, seção "Listas de domínios de e-mail temporário"; caderno de testes,
  roteiro 8 (saídas capturadas desta execução); `docker/postgres-email-service/test-data/README.md`
  com a chave de operações.
- **T013**: classes de exceção criadas e o teste que as lança:
  - `DisposableDomainSourceAlreadyExistsException` → "Operations adds a repository whose address is already registered" (`409`);
  - `DisposableDomainSourceNotFoundException` → "Importing a repository that does not exist" (`404`);
  - `DisposableDomainImportNotFoundException` → "Reading an import that does not exist" (`404`);
  - `DisposableDomainImportAlreadyRunningException` → "Importing a repository that is already being imported" (`409`);
  - `DisposableListDownloadException` → os quatro exemplos de "A repository that fails changes nothing…" (status `404`, porta sem nada escutando, sem resposta no tempo máximo, maior que o limite) e "An empty list is refused and nothing is removed";
  - `InvalidDisposableDomainLineException` → `DisposableDomainLineProcessorTest` (dez linhas inválidas e o domínio com mais de 253 caracteres) e o cenário de normalização (uma linha ignorada).

  Outros branches: `400` (endereço sem `http`/`https`, sem endereço, sem descrição) no
  *outline* de repositório inválido; `403` nos dois sentidos ("A client that only sends e-mail
  cannot manage the lists", "Operations cannot send e-mail nor change templates"); `401` sem
  chave; execução parada e refeita (`ImportDisposableDomainsJobTest`); motivo de domínio
  temporário no `422`, inclusive subdomínio e domínio já na lista branca (`send_email.feature`);
  `UNCHANGED` ("Importing again a list that did not change"). O `catch` do agendamento
  (execução em andamento: loga e espera a próxima) → `DisposableDomainImportSchedulerTest`, que
  chama o método direto, porque o cron fica desligado na suíte. Nada sem teste.
- **T014**: suíte do `email-service`: 119 testes, 2 pulados (os mesmos do `master`), todos os
  outros verdes. `mvn -pl app -am verify`: 284 testes, 279 verdes; os 5 que falham são os de
  `EmailServiceClientIntegrationTest.sendingRecordsEmailServiceIdAndReachesSesWithTheTemplate`, o
  mesmo caso da 05-041 e da 05-047 (o LocalStack deste container não cria o *runtime* da Lambda;
  o CI roda com o Compose completo). A primeira rodada do `app` mostrou que ele gerava um cliente
  Feign para a tag nova e não subia; corrigido no `app/pom.xml` (`plan.md`, "Desvios"). A suíte
  do `config-server` (que lê os arquivos reais de `config-repo/`) passou: 11 testes.

## Verificação manual (T015)

Na raiz do repositório, no WSL, com o branch da PR da implementação (ou o `master` depois do
merge). Usa o `email-service`, o DNS de teste (05-047) e o S3 do LocalStack para servir a lista.
O `down -v` começa de um banco vazio.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
docker compose up -d --wait --build db-email-service localstack dns
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
API_KEY=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU
OPS_API_KEY=dak_D9CebHAQ6cq2vAnYIomxJ1nX56qn_5dDRv2J-aHrQFc
```

(`OPS_API_KEY` é a chave de teste do cliente `operations`, só com `disposable-email:admin`,
gerada na T001.)

1. Uma lista de teste servida pelo S3 do LocalStack:
   ```bash
   docker compose exec -T localstack awslocal s3 mb s3://disposable-lists
   printf '# lista de teste\ndisposable.test\n  OUTRO-TEMPORARIO.test \n\nlinha invalida\n' \
     | docker compose exec -T localstack awslocal s3 cp - s3://disposable-lists/lista.conf
   ```
   Esperado: `make_bucket: disposable-lists` e nenhum erro no `cp`.
2. Acrescentar o repositório e guardar o `id`:
   ```bash
   RESPONSE=$(curl -s -w ' [%{http_code}]' -X POST http://localhost:8082/api/disposable-domain-sources \
     -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
     -d '{"url":"http://localstack:4566/disposable-lists/lista.conf","description":"Lista de teste"}')
   echo "$RESPONSE"
   SOURCE_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
   echo "$SOURCE_ID"
   ```
   Esperado: o repositório sem `lastDownloadedAt` nem `lastUpdatedAt` (campos vazios não aparecem
   no JSON), `"domainCount":0` e ` [201]` no fim; depois, o `id` (`2`, com o repositório inicial
   da migration como `1`).
3. O mesmo endereço de novo:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8082/api/disposable-domain-sources \
     -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
     -d '{"url":"http://localstack:4566/disposable-lists/lista.conf","description":"Lista de teste"}'
   ```
   Esperado: `409`.
4. O `jogo-acoes` (só `email:send`) não usa as rotas de operações:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/api/disposable-domain-sources \
     -H "X-API-Key: $API_KEY"
   ```
   Esperado: `403`.
   E operações (só `disposable-email:admin`) não envia e-mail:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"login-link","recipientEmail":"jogador@with-mx.test"}'
   ```
   Esperado:
   ```
   {"message":"Missing permission: email:send"} [403]
   ```
5. Pedir a execução do lote para esse repositório:
   ```bash
   EXECUTION_ID=$(curl -s -X POST http://localhost:8082/api/disposable-domain-imports \
     -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
     -d "{\"sourceId\":$SOURCE_ID}" | grep -o '"executionId":[0-9]*' | grep -o '[0-9]*')
   sleep 3
   curl -s -H "X-API-Key: $OPS_API_KEY" http://localhost:8082/api/disposable-domain-imports/$EXECUTION_ID; echo
   ```
   Esperado: `"status":"COMPLETED"`, `"added":2`, `"removed":0` e uma linha ignorada (a
   `linha invalida`).
6. Os domínios no banco:
   ```bash
   docker compose exec db-email-service psql -U email_service_admin -d email_service \
     -c 'SELECT domain FROM email_service.disposable_domain ORDER BY domain;'
   ```
   Esperado:
   ```
           domain
   -----------------------
    disposable.test
    outro-temporario.test
   (2 rows)
   ```
7. As horas do repositório:
   ```bash
   curl -s -H "X-API-Key: $OPS_API_KEY" http://localhost:8082/api/disposable-domain-sources; echo
   ```
   Esperado: no repositório da lista de teste, `lastDownloadedAt` e `lastUpdatedAt` preenchidos,
   com a hora do passo 5.
8. Rodar de novo com a mesma lista: repetir os comandos dos passos 5 e 7. Esperado: `"added":0`,
   `"removed":0`; `lastDownloadedAt` mudou, `lastUpdatedAt` continua a do passo 5.
9. O envio para um domínio temporário é recusado (`disposable.test` tem MX na zona de teste,
   então a recusa vem da lista):
   ```bash
   curl -s -o /dev/null -X POST http://localhost:8082/api/templates \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"name":"verificacao-temporario","subject":"Teste","body":"Teste"}'
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"verificacao-temporario","recipientEmail":"jogador@disposable.test"}'
   ```
   Esperado:
   ```
   {"message":"Recipient domain is a disposable e-mail domain: disposable.test"} [422]
   ```
10. Um domínio sai da lista:
    ```bash
    printf 'disposable.test\n' \
      | docker compose exec -T localstack awslocal s3 cp - s3://disposable-lists/lista.conf
    ```
    Repetir os comandos do passo 5. Esperado: `"added":0`, `"removed":1`. O `SELECT` do passo 6
    mostra só `disposable.test`.

Para derrubar: `docker compose down -v`.
