# Tasks: Importações das listas de e-mail temporário uma de cada vez

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: [#197](https://github.com/lalgarve/jogo-acoes/issues/197).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Passo de preparação "the import of every repository already ran" exige `COMPLETED` em cada execução | — | [P] | — |
| ~~T002~~ | Teste da fila (`plan.md`, "Teste da fila"); confirmar que falha antes da T003 (repetindo até aparecer uma execução que começa antes de a anterior terminar) | — | [P] | — |
| ~~T003~~ | Executor de uma thread com fila (`ImportDisposableDomainsJobConfig`) | T002 | | — |
| ~~T004~~ | `spring.batch.jdbc.isolation-level-for-create: read-committed` | T002 | [P] | — |
| ~~T005~~ | Suíte do `email-service` verde, rodada algumas vezes seguidas; nota na 05-048 (`plan.md`, "Execução sob pedido") | T001, T003, T004 | | — |
| ~~T006~~ | Critério de aceite de exceções (`memory/constitution.md`): nenhuma classe de exceção nova; registrar aqui | T003 | | — |
| ~~T007~~ | Verificação manual, com comandos prontos: três repositórios, "importar todos", conferir que as três execuções terminam `COMPLETED` e que os horários não se sobrepõem | T005 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.

## O que foi verificado (2026-10-21)

Numa sessão de nuvem, com a infraestrutura do `scripts/cloud-up.sh` e o `email-service` e o `app`
dele parados (a suíte do `email-service` roda sem o serviço no ar, T020 da 05-046):

| Tarefa | Resultado |
|---|---|
| T002, antes da T003 | `DisposableDomainImportQueueTest` falhou na primeira rodada: `round 0: an import started before the previous one ended` (as três execuções começaram com milissegundos de diferença) |
| T003 e T004 | O mesmo teste passou: cinco rodadas, três repositórios cada, todas `COMPLETED`, sem sobreposição, nenhum `could not serialize` no log. Com o log de transações em `DEBUG`, as criações do Spring Batch trocam o isolamento da conexão para `2` (`READ_COMMITTED`) e nenhuma para `8` (`SERIALIZABLE`) |
| T005 | Suíte do `email-service` três vezes seguidas: 154 testes, 0 falhas, 2 pulados (os mesmos de antes) em cada uma, nenhum `could not serialize` no log |
| T006 | Nenhuma classe de exceção nova e nenhum branch de exceção novo: a mudança é de configuração do executor e do isolamento |

Sem teste próprio para o `READ_COMMITTED`: o caso que ele fecha (criação de uma execução na thread
do pedido coincidindo com a criação do registro de um passo do job que está rodando) depende de
milissegundos e não dá para provocar de forma estável. O log de transações acima mostra que a
configuração vale.

## Verificação manual (T007)

Na raiz do repositório, no WSL, no branch da implementação. Começa de um banco vazio, sem
repositórios (desde a spec 05-053, o do GitHub não é cadastrado no `docker`).

```bash
docker compose down -v
docker compose up -d --wait --build db-email-service localstack dns config-server
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
OPS_API_KEY=dak_D9CebHAQ6cq2vAnYIomxJ1nX56qn_5dDRv2J-aHrQFc
```

(`OPS_API_KEY` é a chave de teste do cliente `operations`, com `disposable-email:admin`, a mesma da
verificação manual da 05-048.)

1. Três listas no S3 do LocalStack e três repositórios:
   ```bash
   docker compose exec -T localstack awslocal s3 mb s3://disposable-lists
   for n in 1 2 3; do
     printf "lista$n.test\ncomum.test\n" | docker compose exec -T localstack awslocal s3 cp - s3://disposable-lists/lista$n.conf
     curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8082/api/disposable-domain-sources \
       -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
       -d "{\"url\":\"http://localstack:4566/disposable-lists/lista$n.conf\",\"description\":\"Lista $n\"}"
   done
   ```
   Esperado: `make_bucket: disposable-lists` e `201` três vezes.
2. Importar todos os repositórios:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/disposable-domain-imports \
     -H "X-API-Key: $OPS_API_KEY"
   sleep 5
   ```
   Esperado: três execuções na resposta, com `executionId`, e ` [202]` no fim.
3. Conferir que terminaram e que uma só começou depois de a anterior terminar:
   ```bash
   docker compose exec -T db-email-service sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT job_execution_id, status, start_time, end_time, start_time >= lag(end_time) OVER (ORDER BY start_time) AS depois_da_anterior FROM email_service.batch_job_execution ORDER BY start_time"'
   ```
   Esperado: três linhas `COMPLETED`; na coluna `depois_da_anterior`, vazio na primeira e `t` nas
   outras duas.

### Derrubar no fim

```bash
docker compose down -v
```

## Verificação manual no WSL (2026-10-22)

Feita pela Leila com o roteiro único [`docs/verificacao-manual-wsl.md`](../../docs/verificacao-manual-wsl.md), Bloco 1, passo 7, com a "Lista de teste" da 05-048 já cadastrada (quatro repositórios).

| Item | Resultado |
|---|---|
| 1. Três listas e repositórios | `ANTES` = `3`; `201` três vezes |
| 2. Importar todos | quatro execuções `STARTING` na resposta, `[202]` |
| 3. Uma de cada vez | execuções 4 a 7 `COMPLETED`; `depois_da_anterior` vazio na primeira e `t` nas outras três |
