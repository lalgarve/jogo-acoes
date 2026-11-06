# Tasks: Nenhum ciclo de dependência entre módulos

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: —

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `ModuleDependencyGraph` no `app` (montagem do grafo + Tarjan + ciclo concreto) e teste unitário com grafos à mão: sem ciclo, ciclo de 2, ciclo de 3, dois ciclos, auto-referência | — | [P] | — |
| ~~T002~~ | Teste no `ArchitectureTest` do `app`. Confirmar que falha mostrando `competition → email → competition` e a referência `EmailRequest → RequestType` | T001 | | — |
| ~~T003~~ | `email`: interfaces `OutgoingEmail` e `EmailTemplateSource`; `EmailSender`, `EmailServiceEmailSender`, `StubEmailSender`, `SentEmailRecorder` e `EmailTemplateSynchronizer` usando as duas; ~~migration de `sent_email.template`~~ (não precisou, ver `plan.md`); saem `EmailRequest` e `EmailTemplate`. Teste do nome de template duplicado | T002 | | — |
| ~~T004~~ | `competition` e `loginsession`: e-mails próprios implementando `OutgoingEmail`, escolha do template (com `RequestType` só na `competition`) e `EmailTemplateSource` de cada um; templates movidos para `email-templates/<cliente>/`. Confirmar o teste da T002 verde, a suíte do `app` verde e a suíte de caixa-preta de e-mail verde | T003 | | — |
| ~~T005~~ | Mesmo teste no `email-service` (cópia de `ModuleDependencyGraph` e do teste unitário). Confirmar verde | T001 | [P] | — |
| ~~T006~~ | Constitution, "Fronteira entre módulos e serviços": sem ciclo entre módulos de um serviço, garantido por teste de arquitetura | T004, T005 | | — |
| ~~T007~~ | Critério de aceite de exceções: nome de template duplicado exercitado por teste; registrar aqui | T003 | | — |
| T008 | Verificação manual, com comandos prontos: suíte do `app` e do `email-service` verdes; plantar um `user → log` temporário e ver a mensagem do ciclo; os cinco e-mails chegam iguais aos de antes no ambiente com o overlay `blackbox` | T004, T005 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.

## O que foi verificado (2026-11-06)

Numa sessão de nuvem, com `./scripts/cloud-up.sh` (spec 05-052).

| Tarefa | Resultado |
|---|---|
| T001 | `ModuleDependencyGraphTest`: 5 testes verdes (sem ciclo, ciclo de 2, ciclo de 3, dois grupos, auto-referência) |
| T002 | Antes da T003, `modulesHaveNoDependencyCycles` falhou com um só ciclo, `competition -> email -> competition`. Exemplos: `EntryRequestService -> EmailTemplate` e `EmailServiceEmailSender -> RequestType` (o primeiro exemplo achado da aresta; `EmailRequest -> RequestType` era outro) |
| T003, T004 | `ArchitectureTest` do `app` verde. Suíte do `app` (`mvn -pl app -am verify`, com o `email-service` no ar e o `app` parado): 297 testes, 0 falhas, 0 pulados. Suíte `blackbox-tests` (`./scripts/cloud-blackbox-tests.sh`): 2 cenários `behave` e 29 testes `pytest` verdes. No banco do `app`, `sent_email.template` passou a ter os nomes dos templates (`login-link`, `registration-link`) |
| T005 | `ArchitectureTest` do `email-service` verde, sem ciclo; `ModuleDependencyGraphTest` (cópia) verde |
| T007 | Nenhuma classe de exceção nova. Caminho de erro novo: dois `EmailTemplateSource` com o mesmo nome de template lançam `IllegalStateException` na subida, exercitado por `EmailTemplateSynchronizerTest.twoSourcesDeclaringTheSameTemplateNameStopTheStartup` |
| Ciclo plantado | Uma classe temporária em `user` com um campo `AuditLogService`: o teste falhou com `log -> user -> log`, `log -> user: Log -> User` e `user -> log: PlantedCycle -> AuditLogService`. Classe removida depois |

## Verificação manual (T008)

Na raiz do repositório, no WSL, no branch da implementação.

### 1. Testes de arquitetura (não precisam de container)

```bash
mvn -pl app -am test -Dtest='ArchitectureTest,ModuleDependencyGraphTest,EmailTemplateSynchronizerTest,CompetitionEmailTest,LoginLinkEmailTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```

Esperado: a última linha `Tests run:` com `Failures: 0, Errors: 0` e `BUILD SUCCESS`.

### 2. Plantar um ciclo `user -> log`

```bash
cat > app/src/main/java/dev/leilaalgarve/jogoacoes/user/PlantedCycle.java <<'JAVA'
package dev.leilaalgarve.jogoacoes.user;

class PlantedCycle {
    dev.leilaalgarve.jogoacoes.log.AuditLogService auditLog;
}
JAVA
mvn -pl app -am test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "^log -> user -> log|    (log|user) -> " | sort -u
rm app/src/main/java/dev/leilaalgarve/jogoacoes/user/PlantedCycle.java
```

Esperado: três linhas, `log -> user -> log`, `    log -> user: dev.leilaalgarve.jogoacoes.log.Log -> dev.leilaalgarve.jogoacoes.user.User`
e `    user -> log: dev.leilaalgarve.jogoacoes.user.PlantedCycle -> dev.leilaalgarve.jogoacoes.log.AuditLogService`.

### 3. Os e-mails continuam chegando

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
docker compose up -d --wait --build db db-email-service localstack
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
docker compose up -d --wait --build app
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/login-requests \
  -H 'Content-Type: application/json' -d '{"email":"success+admin@simulator.amazonses.com","captchaToken":""}'
sleep 5
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select template from jogo_acoes.sent_email order by id desc limit 1"'
```

Esperado: `202` e `login-link`. No `ses-viewer` (`http://localhost:3005`), o e-mail de login para
`success+admin@simulator.amazonses.com`, com o mesmo texto de antes. Depois, a suíte
`blackbox-tests` (README da suíte) passa sem mudança: ela cobre convite, pedido de entrada e
login.

### Derrubar no fim

```bash
docker compose down -v
```
