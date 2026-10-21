# Tasks: Os testes não sobem containers

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: [#193](https://github.com/lalgarve/jogo-acoes/issues/193).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Regra contra Testcontainers (ArchUnit) nos quatro módulos e conferência de `quarkus.devservices.enabled=false` na `email-lambda`. Confirmar que a conferência da `email-lambda` falha antes da T002 | — | [P] | — |
| ~~T002~~ | `email-lambda`: Dev Services desligado e SES (e SQS, com a 05-046) apontando para `http://localhost:4566` nas propriedades de teste; ajustes no `EmailSendHandlerTest` (remetente, comentários) e no comentário do `pom.xml`. Confirmar a suíte verde com o LocalStack do Compose e vermelha, com erro de conexão, sem ele | T001 | | — |
| ~~T003~~ | CI: "Build and test email-lambda" antes de derrubar os containers | T002 | | — |
| ~~T004~~ | Repositório inicial das listas só em `staging`/`production` (`plan.md`): `INSERT` fora da V6, migration de dados em `db/seed-email-service/` (sem duplicar), `spring.flyway.locations` no `config-repo`; `DisposableDomainCleanupHooks` sem o caso do GitHub. Confirmar a suíte do `email-service` verde | — | [P] | — |
| ~~T005~~ | Documentação: constitution ("Testes exigem a infraestrutura de pé"), notas nas specs 05-028 e 05-048, nota no caderno de testes do Serviço de E-mail, README ("Como rodar os testes": a `email-lambda` também precisa do LocalStack de pé) | T002–T004 | | — |
| ~~T006~~ | Critério de aceite de exceções (`memory/constitution.md`): nenhuma classe de exceção nova; registrar aqui | T002, T004 | | — |
| T007 | Verificação manual, com comandos prontos: suíte da `email-lambda` com e sem LocalStack; `docker ps` sem container novo durante o `mvn test`; `GET /disposable-domain-sources` vazio num banco novo do `docker` | T001–T005 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.

## O que foi verificado (2026-10-20)

Numa sessão de nuvem, depois de `./scripts/cloud-up.sh` (que compila tudo do zero):

| Tarefa | Resultado |
|---|---|
| T001 | Antes da T002, `ArchitectureTest.devServicesIsSwitchedOffInTests` da `email-lambda` falhou (`expected: "false"`). Depois, a regra contra Testcontainers ficou verde nos quatro módulos: `app` 11/11, `config-server` 3/3, `email-lambda` 3/3, `email-service` dentro da suíte abaixo |
| T002, sem LocalStack | Docker de pé, nenhum container: `mvn -pl email-lambda -am test` com `exit 1`, 5 erros `Connection refused` no `EmailSendHandlerTest`, nenhum teste pulado, nenhum container criado durante a execução (`docker ps -a` a cada segundo) |
| T002, com LocalStack | 12 testes, 0 falhas, `exit 0`; nenhuma linha `testcontainers` no log; nenhum container novo durante a execução |
| T003 | Só no CI da PR: o passo da `email-lambda` roda antes de derrubar os containers |
| T004, `docker` | Banco novo: `select count(*) from email_service.disposable_domain_source` devolve `0`; histórico do Flyway de 1 a 7, sem `6.1` |
| T004, `staging`/`production` | Banco novo, `email-service` com as duas pastas em `SPRING_FLYWAY_LOCATIONS`: o Flyway aplica 1, 2, 3, 4, 5, 6, **6.1** e 7, e o repositório do GitHub fica cadastrado. Aplicar o arquivo de novo: `INSERT 0 0`, continua 1 repositório |
| T004, suíte | `email-service` com o `email-service` e o `app` do ambiente parados (T020 da 05-046): 154 testes, 0 falhas, 2 pulados (os mesmos cenários de antes) |
| T006 | Nenhuma classe de exceção nova e nenhum branch de exceção novo: a mudança é de configuração de teste, migration, CI e documentação |

## Verificação manual (T007)

Na raiz do repositório, no branch da implementação, no WSL. Leva uns 5 minutos.

### 1. Subir só a infraestrutura dos testes

```bash
docker compose down -v
docker compose up -d --wait db-email-service localstack dns config-server
docker ps --format '{{.Names}}' | sort
```

Esperado: quatro containers, `jogo-acoes-config-server-1`, `jogo-acoes-db-email-service-1`,
`jogo-acoes-dns-1` e `jogo-acoes-localstack-1`.

### 2. Suíte da `email-lambda` com o LocalStack de pé

```bash
docker ps -aq | wc -l
mvn -pl email-lambda -am test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD|testcontainers"
docker ps -aq | wc -l
```

Esperado: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, nenhuma linha com
`testcontainers`, e o mesmo número de containers antes e depois.

### 3. Suíte da `email-lambda` sem o LocalStack

```bash
docker compose stop localstack
mvn -pl email-lambda -am test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD" | tail -2
docker ps --format '{{.Names}}' | grep -c localstack
docker compose start localstack
```

Esperado: `Tests run: 12, Failures: 0, Errors: 5, Skipped: 0`, `BUILD FAILURE` e `0` (nenhum
LocalStack subiu no lugar).

### 4. Repositório do GitHub fora do `docker`

```bash
./scripts/test-api-key.sh restore
docker compose up -d --wait email-service
docker compose exec db-email-service sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select count(*) from email_service.disposable_domain_source"'
```

Esperado: `0`.

### Derrubar no fim

```bash
docker compose down -v
```
