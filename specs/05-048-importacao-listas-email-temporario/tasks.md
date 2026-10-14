# Tasks: Importação em lote das listas de domínios de e-mail temporário

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** testes do comportamento novo primeiro, falhando pelo motivo esperado; depois as
correções. Depende da spec 05-047 (zona de DNS de teste, `RecipientDomainVerifier`, `422` no
`POST /emails`).

Issue-épico: a criar quando a spec for aprovada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Cliente `operations` no `docker`: gerar a chave de teste com a CLI do `api-key`, salvar no dump de `docker/postgres-email-service/test-data/` (README com a chave em texto puro), `scripts/test-api-key.sh restore` restaura as duas. Trocar a linha `OPS_API_KEY=` da verificação manual abaixo pela chave gerada | — | [P] | — |
| T002 | `disposable.test` (com MX) na zona de DNS de teste da 05-047 | spec 05-047 T001 | [P] | — |
| T003 | `disposable_domains.feature` com os casos de `spec.md`, "Cenários", e os passos que gravam listas no S3 do LocalStack. Confirmar que falham (`404` nas rotas) | T001 | [P] | — |
| T004 | `send_email.feature`: destinatário em domínio temporário e em subdomínio dele → `422`. Confirmar que falham (`202`) | T002 | [P] | — |
| T005 | `DisposableDomainLineProcessorTest` e `ImportDisposableDomainsJobTest`. Registrar que não compilam antes do pacote `disposable/` | — | [P] | — |
| T006 | Contrato: tag `disposable-domains`, rotas, esquemas, `403`; `422` de `POST /emails` com os dois motivos | — | [P] | — |
| T007 | Migrations (tabelas do Spring Batch e das listas, repositório inicial), starter do Spring Batch, `spring.batch.*`. Confirmar `SchemaLayoutTest` verde | — | | — |
| T008 | Job (`downloadStep`, decisor, `importStep` com `ItemReader`/`ItemProcessor`/`ItemWriter`, `pruneStep`). Confirmar T005 verde | T005, T007 | | — |
| T009 | Rotas de repositórios e de execução, `OperationsAuthorizationFilter`, `403`/`409` no `ApiExceptionHandler`, `email.operations-clients` e `email.disposable-domains.*` em `config-repo/` e no `application.yml` de teste. Confirmar T003 verde | T001, T003, T006, T008 | | — |
| T010 | `DisposableDomainChecker` e a chamada em `RecipientDomainVerifier`. Confirmar T004 verde | T004, T007, spec 05-047 | | — |
| T011 | Agendamento (`email.disposable-domains.import-cron`), desligado no `docker` e ligado em `staging`/`production` | T008 | [P] | — |
| T012 | Documentação: `README.md` (cliente `operations`, como cadastrar repositório e pedir a execução); `docs/disciplina/caderno-de-testes.md`, seção do email-service, com os `curl` da verificação manual | T009 | [P] | — |
| T013 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: `DisposableDomainSourceAlreadyExistsException`, `DisposableDomainImportAlreadyRunningException`, `DisposableListDownloadException`) e os branches de exceção adicionados ou alterados (`400`/`403`/`409` das rotas; cada falha do `downloadStep`: status, tempo, tamanho, lista vazia; linha inválida no processador; execução parada e refeita; motivo `DISPOSABLE` no `422`); para cada um, apontar o teste que o exercita. O que não tiver teste é justificado em `plan.md` e avisado no chat ao abrir a PR. Registrar a lista aqui | T008–T010 | | — |
| T014 | Rodar a suíte completa do `email-service` e `mvn -pl app -am verify` — confirmar verde | T011–T013 | | — |
| T015 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T015)" abaixo e conferir os resultados esperados | T014 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico, ou uma Issue própria quando grande o
  suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de tipo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

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
OPS_API_KEY=
```

(A linha `OPS_API_KEY=` recebe a chave de teste do cliente `operations` na T001.)

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
   Esperado: o repositório com `"lastDownloadedAt":null,"lastUpdatedAt":null` e ` [201]` no fim;
   depois, o `id` (`2`, com o repositório inicial da migration como `1`).
3. O mesmo endereço de novo:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8082/api/disposable-domain-sources \
     -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
     -d '{"url":"http://localstack:4566/disposable-lists/lista.conf","description":"Lista de teste"}'
   ```
   Esperado: `409`.
4. O `jogo-acoes` não usa as rotas de operações:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/api/disposable-domain-sources \
     -H "X-API-Key: $API_KEY"
   ```
   Esperado: `403`.
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
