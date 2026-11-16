# Tasks: Camadas dentro dos módulos

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: —

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `app`: quatro regras de camada no `ArchitectureTest` e classes de exemplo que as violam, com um teste por regra confirmando que ela falha. Confirmar as regras verdes no código atual | — | [P] | — |
| ~~T002~~ | `email-service`: refatoração do `disposable` (`spec.md`, "Acesso a banco no `disposable`"): `DisposableDomainRepository`, `ImportExecutionRepository`, métodos novos no `DisposableDomainSourceRepository`, `content_sha256` mapeado, nenhum SQL nas seis classes. Confirmar a suíte do `email-service` verde sem mudar teste de comportamento | — | [P] | — |
| ~~T003~~ | `email-service`: as mesmas quatro regras e exemplos. Confirmar que falham antes da T002 (nas seis classes) e passam depois | T002 | | — |
| ~~T004~~ | Constitution, "Fronteira entre módulos e serviços": regra de camadas e "só repository acessa o banco", garantidas por teste de arquitetura | T001, T003 | | — |
| ~~T005~~ | Critério de aceite de exceções: nenhuma classe de exceção nova; registrar aqui | T002 | | — |
| T006 | Verificação manual, com comandos prontos: `ArchitectureTest` do `app` e do `email-service` verdes; plantar temporariamente um `UserRepository` num controller e ver a mensagem; importar uma lista no ambiente local antes e depois da T002 e comparar domínios adicionados, removidos e o estado da lista | T001–T003 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.

## O que foi verificado (2026-11-06)

Numa sessão de nuvem, com `./scripts/cloud-up.sh` (spec 05-052).

| Tarefa | Resultado |
|---|---|
| T001 | `LayerRulesTest` do `app`: cada uma das quatro regras falha na classe de exemplo que a viola e passa nas permitidas. `ArchitectureTest` do `app` verde com as quatro regras. Suíte do `app`: 297 testes, 0 falhas |
| T003 (antes da T002) | `onlyRepositoriesUseDatabaseApis` falhou no `email-service` exatamente nas seis classes: `DisposableDomainChecker`, `DisposableDomainImportService`, `DisposableDomainSourceService`, `DownloadListTasklet`, `ImportDisposableDomainsJobConfig` e `PruneDomainsTasklet` |
| T002, T003 | Depois da refatoração, as quatro regras verdes no `email-service`. Suíte do `email-service`: 169 testes, 0 falhas, 2 pulados (os mesmos cenários Cucumber de antes); nenhum teste de comportamento mudou |
| T002 no ambiente | Importação real de uma lista no S3 do LocalStack: 1ª com 3 domínios adicionados; 2ª (sai `tres.test`, entra `quatro.test`) com 1 adicionado e 1 removido; 3ª com a mesma lista `UNCHANGED`. A listagem do repositório mostrou 3 domínios, a última importação, as duas datas preenchidas e o hash com 64 caracteres. Suíte `blackbox-tests` verde |
| T005 | Nenhuma classe de exceção nova; o `IllegalStateException` novo do `DownloadListTasklet` é inalcançável e está justificado no `plan.md` |
| Quebra plantada | Um `@RestController` temporário em `competition` com um campo `CompetitionRepository`: `controllersDoNotUseRepositories` falhou com `Field <...PlantedLayerBreak.competitions> has type <...CompetitionRepository>`. Classe removida depois |

## Verificação manual (T006)

Na raiz do repositório, no WSL, no branch da implementação.

### 1. Testes de arquitetura (não precisam de container)

```bash
mvn -pl app -am test -Dtest='ArchitectureTest,LayerRulesTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
mvn -pl email-service -am test -Dtest='ArchitectureTest,LayerRulesTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```

Esperado nos dois: a última linha `Tests run:` com `Failures: 0, Errors: 0` e `BUILD SUCCESS`.

### 2. Plantar um controller que usa repository

```bash
cat > app/src/main/java/dev/leilaalgarve/jogoacoes/competition/PlantedLayerBreak.java <<'JAVA'
package dev.leilaalgarve.jogoacoes.competition;

@org.springframework.web.bind.annotation.RestController
class PlantedLayerBreak {
    CompetitionRepository competitions;
}
JAVA
mvn -pl app -am test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "PlantedLayerBreak" | sort -u
rm app/src/main/java/dev/leilaalgarve/jogoacoes/competition/PlantedLayerBreak.java
```

Esperado: uma linha com `Field <dev.leilaalgarve.jogoacoes.competition.PlantedLayerBreak.competitions> has type <dev.leilaalgarve.jogoacoes.competition.CompetitionRepository>`.

### 3. Importar uma lista (o `disposable` refatorado)

Começa de um banco vazio, sem repositórios (desde a spec 05-053, o do GitHub não é cadastrado
no `docker`), então o repositório criado aqui tem id `1`.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
docker compose up -d --wait --build db-email-service localstack dns config-server
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
OPS_API_KEY=dak_D9CebHAQ6cq2vAnYIomxJ1nX56qn_5dDRv2J-aHrQFc
docker compose exec -T localstack awslocal s3 mb s3://disposable-lists
printf "um.test\ndois.test\ntres.test\n" | docker compose exec -T localstack awslocal s3 cp - s3://disposable-lists/lista.conf
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8082/api/disposable-domain-sources \
  -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
  -d '{"url":"http://localstack:4566/disposable-lists/lista.conf","description":"Lista 05-063"}'
importa() {
  E=$(curl -s -X POST "http://localhost:8082/api/disposable-domain-imports?sourceId=1" -H "X-API-Key: $OPS_API_KEY" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["imports"][0]["executionId"])')
  timeout 60 bash -c "until curl -s http://localhost:8082/api/disposable-domain-imports/$E -H 'X-API-Key: $OPS_API_KEY' | grep -q '\"endTime\"'; do sleep 1; done"
  curl -s http://localhost:8082/api/disposable-domain-imports/$E -H "X-API-Key: $OPS_API_KEY"; echo
}
importa
```

Esperado: `make_bucket: disposable-lists`, `201` e a importação com `"status":"COMPLETED"`,
`"read":3`, `"added":3` e `"removed":0`.

```bash
printf "um.test\ndois.test\nquatro.test\n" | docker compose exec -T localstack awslocal s3 cp - s3://disposable-lists/lista.conf
importa
importa
```

Esperado: a primeira com `"added":1` e `"removed":1`; a segunda com `"exitMessage":"UNCHANGED"`,
`"read":0`, `"added":0` e `"removed":0`.

```bash
curl -s http://localhost:8082/api/disposable-domain-sources -H "X-API-Key: $OPS_API_KEY"; echo
docker compose exec -T db-email-service sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select domain from email_service.disposable_domain order by domain; select last_downloaded_at is not null, last_updated_at is not null, length(content_sha256) from email_service.disposable_domain_source"'
```

Esperado: o repositório com `domainCount` 3 e a última importação preenchida; no banco,
`dois.test`, `quatro.test`, `um.test` e a linha `t|t|64`.

### Derrubar no fim

```bash
docker compose down -v
```
