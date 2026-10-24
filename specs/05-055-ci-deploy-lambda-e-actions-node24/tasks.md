# Tasks: CI confere o deploy da Lambda e sai do Node.js 20

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issues: [#98](https://github.com/lalgarve/jogo-acoes/issues/98) (T001–T003, T006) e
[#182](https://github.com/lalgarve/jogo-acoes/issues/182) (T004, T005). As duas já existem e
fazem o papel de épico; não há Issue nova por tarefa.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `03-deploy-email-lambda.sh`: `exit 1` com mensagem quando a `EmailLambda` não chega a `Active` em 30 s; depois do `create-event-source-mapping`, laço igual (também `seq 1 30`) até o mapeamento ficar `Enabled`, com o mesmo tratamento. Comentário do script atualizado | — | [P] | #98 |
| ~~T002~~ | Healthcheck do `localstack` no `docker-compose.yml` lendo `/_localstack/init/ready` (completo e todos `SUCCESSFUL`), com `start_period` medido: registrar aqui quanto a subida leva no WSL e no CI. Confirmar antes o que a imagem tem (`curl`, `python3`). Comentário do serviço atualizado | T001 | | #98 |
| ~~T003~~ | `scripts/check-localstack.sh` (estado dos scripts de init, da função e do mapeamento; `exit 1` se algum não estiver certo). No `ci.yml`: passo que roda o script depois de cada uma das duas subidas do LocalStack, e passo `if: failure()` com `docker compose logs --no-color localstack email-lambda-builder` antes de cada `docker compose down -v`. `scripts/docker-up.sh`: log do `localstack` também quando `unhealthy`. README: o script na seção de subida do ambiente | T002 | | #98 |
| ~~T004~~ | Para `actions/checkout`, `actions/setup-java`, `actions/upload-artifact` e `madrapps/jacoco-report`: release mais recente, `runs.using` do `action.yml` da versão escolhida, e o que as notas de release mudaram desde a versão atual em relação ao que o workflow usa (`plan.md`). Registrar na tabela de decisões do `plan.md` (commit `decision:`). Se o `jacoco-report` não tiver versão no Node.js 24, ele fica na `v1.7` (decisão da Leila, 2026-10-24) | — | [P] | #182 |
| ~~T005~~ | `ci.yml`: as quatro actions nas versões da T004 (`actions/setup-java` na `v6`, ver `plan.md`), sem mudar `with:` além do que as notas de release exigirem | T004 | | #182 |
| ~~T006~~ | Critério de aceite de exceções (`memory/constitution.md`): nenhuma classe de exceção nem branch de exceção em código Java. Registrar aqui os caminhos de erro de shell (script de init com `ERROR`, função que não fica `Active`, mapeamento que não fica `Enabled`, `docker-up.sh` com `localstack` `unhealthy`) e onde a T008 exercita cada um | T001–T003 | | #98 |
| T007 | CI da PR: verde; o log mostra a saída do `check-localstack.sh` nas duas subidas; o artefato `jacoco-app` existe; o comentário de cobertura foi atualizado (não duplicado); nenhum aviso de Node.js 20 nem de `setup-java` v4 nas anotações do job (a T004 achou a `v1.8` do `jacoco-report`, em `node24`). Registrar aqui o link da execução | T003, T005 | | #98, #182 |
| ~~T008~~ | Verificação manual, com os comandos abaixo | T001–T003 | | #98 |
| T009 | Espera por tempo (decisão da Leila depois da T008): `wait_for_state` no `03-deploy-email-lambda.sh` (até 120 s pela função, 30 s pelo mapeamento, desiste com a função `Failed`), `start_period: 180s`, roteiro da T008 ajustado. Repetir no WSL os passos 1, 3 e 4 do roteiro abaixo | T008 | | #98 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo. As duas linhas de trabalho (T001–T003 e T004–T005) são independentes até a
  T007.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## O que foi verificado (2026-10-24)

Numa sessão de nuvem, onde o `docker compose up` não alcança a internet (`README.md`, "Numa sessão
do Claude Code na nuvem"): o LocalStack Community (`localstack/localstack:4`, versão 4.14.0) subiu
com `docker run` na rede do host, como o `cloud-up.sh` faz, com os scripts de
`docker/localstack/init/`, o `function.zip` compilado no host e o healthcheck exatamente como o
`docker compose config` o gera.

| Tarefa | Resultado |
|---|---|
| T001, função | Com o laço zerado (`seq 1 0`): `03-deploy-email-lambda.sh` em `ERROR` e, no log, `03-deploy-email-lambda: EmailLambda did not become Active in 30s, last state: none` |
| T001, mapeamento | Só o segundo laço zerado: `ERROR` e `03-deploy-email-lambda: the event source mapping from jogo-acoes-email-commands did not become Enabled in 30s, last state: none` |
| T002, imagem | `python3` (`/usr/local/bin/python3`, 3.13) e `curl` no `localstack/localstack:4` e no `localstack/localstack-pro:4` |
| T002, init completo | `healthy` em 21 s e 23 s (imagem do runtime da Lambda já baixada); `/_localstack/init/ready` com os três scripts `SUCCESSFUL`; `EmailLambda` `Active`, mapeamento `Enabled` |
| T002, init com erro | Runtime `java99`, laço da função zerado e laço do mapeamento zerado: os três `unhealthy` em 67–68 s (com `start_period: 20s` para o teste), com `completed: true` e o `03` em `ERROR` |
| T003, `check-localstack.sh` | Com `LOCALSTACK_CONTAINER`: `exit 0` e tudo certo no init completo; `exit 1` durante o init (`03` em `RUNNING`, `completed: false`), com o `03` em `ERROR` e com um container que não existe |
| T003, `docker-up.sh` | Só a sintaxe (`bash -n`) e o filtro do log: sem as linhas do healthcheck, as 15 últimas linhas do container quebrado trazem a mensagem do `03`; com elas, eram só `GET /_localstack/init/ready`. A rodada pelo Compose fica para a T008 |
| T004 | Tabela "Versões das actions" no `plan.md` |
| T006 | Nenhuma classe de exceção nem branch de exceção em código Java. Caminhos de erro de shell e onde são exercitados: script de init em `ERROR` (T002 acima; T008, passo 2), função que não fica `Active` (T001 acima; T008, passo 3), mapeamento que não fica `Enabled` (T001 acima; T008, passo 4), `docker-up.sh` com `localstack` `unhealthy` (T008, passo 4), `check-localstack.sh` sem conseguir ler o init (T003 acima) |

| T007, execução manual no branch | [Execução 40100070950](https://github.com/lalgarve/jogo-acoes/actions/runs/40100070950) (`workflow_dispatch`): verde. "Start Postgres, LocalStack and the test DNS" levou 73 s (com o pull das imagens e o deploy da Lambda), a segunda subida 17 s. Os dois "Check LocalStack's init" passaram; o do `email-service` mostra os três scripts `SUCCESSFUL`, `EmailLambda: Active` e o mapeamento `Enabled` (o do primeiro fica antes das 5.000 linhas finais que a API devolve). Artefato `jacoco-app` publicado. Nenhum aviso de Node.js 20 nem de `setup-java` no fim do job; os únicos `##[warning]` são do compilador Maven. Os passos de log com `failure()` ficaram `skipped`, como esperado. Falta, na PR: o comentário de cobertura (o passo só roda em `pull_request`) e as anotações na aba do job |

Não deu para rodar aqui: o Compose de verdade (`docker compose up --wait`, `docker compose exec`)
e o `docker-up.sh`, por isso a T008 continua aberta, para o WSL.

## Verificação manual no WSL (T008, 2026-10-24)

Os comandos de "Verificação manual (T008)" abaixo, exatamente como estão, no branch
`claude/project-thread-b3h617` (`0ef5480`), no WSL2 com Docker 29.8.0 e Compose v5.5.1, sem
`docker-up.conf` (o `docker-up.sh` usou o `docker-up.conf.example`: LocalStack Community, overlay
`blackbox`).

| Passo | Resultado |
|---|---|
| 1, primeira rodada | **Não saiu como o esperado**: `exit=1`, `unhealthy`; o `check-localstack.sh` mostrou o `03` em `ERROR`, `EmailLambda: Active` e o mapeamento `missing`, e `exit=1`. No log, `03-deploy-email-lambda: EmailLambda did not become Active in 30s, last state: Pending`: a função ficou `Pending` das 16:57:06 às 16:57:58. Cada `awslocal lambda get-function` levou ~1,5 s, então as 30 voltas do laço da T001 deram ~52 s, não 30 s. Pouco depois a função estava `Active`. A rodada também criou do zero o volume `maven_cache` do `email-lambda-builder` |
| 1, segunda rodada | Com a imagem `public.ecr.aws/lambda/java:21` já baixada: `exit=0` em 13 s, `healthy`, os três scripts `SUCCESSFUL`, `EmailLambda: Active`, `Event source mapping from jogo-acoes-email-commands: Enabled`, `check-localstack: LocalStack is ready`, `exit=0`. `CreateFunction` às 17:00:18 e `CreateEventSourceMapping` às 17:00:22: a função ficou `Active` em ~3 s |
| 1, com o cache frio | Depois de `docker rmi public.ecr.aws/lambda/java:21`, para repetir o pull do runtime: `exit=0` em 32 s e tudo certo, como na segunda rodada. Por isso a causa da primeira falha fica sem confirmar: com o pull, a função ainda ficou `Active` dentro das 30 voltas. A hipótese mais provável é um pull mais lento na primeira rodada |
| 2 | `exit=1`, `unhealthy`; o `check-localstack.sh` mostrou o `03` em `ERROR` (`EmailLambda: missing`) e `exit=1`. No log, `InvalidParameterValueException` do `CreateFunction` para `java99` |
| 3 | `exit=1` e `1` no `grep`; no log, `03-deploy-email-lambda: EmailLambda did not become Active in 30s, last state: none` |
| 4 | `1` no `grep`; `docker-up: localstack unhealthy; its last log lines:` e, nessas linhas, `03-deploy-email-lambda: the event source mapping from jogo-acoes-email-commands did not become Enabled in 30s, last state: none`, seguido de `docker-up: docker compose up --build db db-email-service localstack dns failed`; `exit=1` |
| Derrubar no fim | Nenhuma linha no `git status` |

Os caminhos de erro (passos 2 a 4) e o `docker-up.sh` saíram como o esperado. O passo 1 passou
nas duas rodadas seguintes, mas a primeira mostra que, no WSL, a espera da função pode passar
das 30 voltas. A falha foi a da T001 (o script falhou e o container ficou `unhealthy`, sem
esconder a função parada atrás de um `healthy`). Se as 30 voltas bastam fica em aberto, para
decidir.

## Espera por tempo (T009, 2026-10-24)

Na sessão de nuvem, como na seção "O que foi verificado" acima, com o script e o healthcheck novos
(`start_period` de 20 s nos casos de erro, para o teste andar mais rápido):

| Caso | Resultado |
|---|---|
| Init completo | `healthy` em 34 s e em 12 s; `check-localstack.sh` com tudo certo |
| `FUNCTION_TIMEOUT_SECONDS=0` | `unhealthy`; `EmailLambda did not become Active in 0s, last state: none` |
| `MAPPING_TIMEOUT_SECONDS=0` | `unhealthy`; `the event source mapping from jogo-acoes-email-commands did not become Enabled in 0s, last state: none` |
| `FUNCTION_TIMEOUT_SECONDS=1` | `unhealthy`; `EmailLambda did not become Active in 1s, last state: Pending`: o limite em segundos, com o estado lido |
| Imagem do runtime da Lambda indisponível | A sessão de nuvem não conseguiu baixar `public.ecr.aws/lambda/java:21` de novo (o proxy recusa o CDN do registro) e a função ficou `Failed`. Antes da mudança, o laço esperava os 120 s; com ela, `EmailLambda is Failed instead of Active` em ~2 s, seguido de `InternalError	Error while creating lambda: public.ecr.aws/lambda/java:21`, e `unhealthy` |

Este último caso não tem passo no roteiro do WSL: lá a imagem baixa normalmente.

## Verificação manual (T008)

Na raiz do repositório, no branch da implementação, no WSL. Só o LocalStack é usado, sem o
overlay `blackbox` nem os serviços: a verificação é do init dele. Os passos 2 a 4 quebram o
`03-deploy-email-lambda.sh` de propósito e o restauram no fim com `git checkout`. Cada um leva
uns 4 minutos: o `--wait` só desiste depois do `start_period` (180 s) e das `retries`. Os passos 3 e
4 zeram os limites de espera do script (`FUNCTION_TIMEOUT_SECONDS` e `MAPPING_TIMEOUT_SECONDS`), e
com zero o script nem consulta o estado.

### 1. Init completo

```bash
docker compose down -v
docker compose up -d --wait localstack; echo "exit=$?"
docker inspect -f '{{.State.Health.Status}}' jogo-acoes-localstack-1
./scripts/check-localstack.sh; echo "exit=$?"
```

Esperado: `exit=0`; `healthy`; o script lista `01-create-queue.sh`, `02-verify-ses-sender.sh` e
`03-deploy-email-lambda.sh` como `SUCCESSFUL`, a `EmailLambda` como `Active` e o mapeamento como
`Enabled` (`Event source mapping from jogo-acoes-email-commands: Enabled`), e termina com
`check-localstack: LocalStack is ready`; `exit=0`.

### 2. Script de init com erro

```bash
docker compose down -v
sed -i 's/--runtime java21/--runtime java99/' docker/localstack/init/03-deploy-email-lambda.sh
docker compose up -d --wait localstack; echo "exit=$?"
docker inspect -f '{{.State.Health.Status}}' jogo-acoes-localstack-1
./scripts/check-localstack.sh; echo "exit=$?"
git checkout -- docker/localstack/init/03-deploy-email-lambda.sh
```

Esperado: `exit=` diferente de `0`; `unhealthy`; o script mostra `03-deploy-email-lambda.sh`
como `ERROR`; `exit=1`.

### 3. Função que não fica `Active`

Com o limite da função zerado, o script não espera e cai direto na falha.

```bash
docker compose down -v
sed -i 's/^FUNCTION_TIMEOUT_SECONDS=120$/FUNCTION_TIMEOUT_SECONDS=0/' docker/localstack/init/03-deploy-email-lambda.sh
grep -c '^FUNCTION_TIMEOUT_SECONDS=0$' docker/localstack/init/03-deploy-email-lambda.sh
docker compose up -d --wait localstack; echo "exit=$?"
docker compose logs localstack | grep -c 'did not become Active'
git checkout -- docker/localstack/init/03-deploy-email-lambda.sh
```

Esperado: `1` no primeiro `grep`; `exit=` diferente de `0`; `1` no último `grep` (no log,
`03-deploy-email-lambda: EmailLambda did not become Active in 0s, last state: none`).

### 4. Mapeamento que não fica `Enabled`, pelo `docker-up.sh`

Só o limite do mapeamento zerado: a função fica `Active` e o mapeamento não é esperado. Roda pelo
`scripts/docker-up.sh`, para ver o log do `localstack` `unhealthy`.

```bash
docker compose down -v
sed -i 's/^MAPPING_TIMEOUT_SECONDS=30$/MAPPING_TIMEOUT_SECONDS=0/' docker/localstack/init/03-deploy-email-lambda.sh
grep -c '^MAPPING_TIMEOUT_SECONDS=0$' docker/localstack/init/03-deploy-email-lambda.sh
./scripts/docker-up.sh; echo "exit=$?"
git checkout -- docker/localstack/init/03-deploy-email-lambda.sh
```

Esperado: `1` no `grep`; o `docker-up.sh` para na primeira subida
com `docker-up: localstack unhealthy; its last log lines:` e, entre essas linhas,
`03-deploy-email-lambda: the event source mapping from jogo-acoes-email-commands did not become
Enabled in 0s, last state: none`, e `docker-up: docker compose up --build db db-email-service localstack dns failed` (sem `--build` se o `docker-up.conf` tiver `BUILD=false`);
`exit=1`.

### Derrubar no fim

```bash
docker compose down -v
git status --short docker/localstack/init/
```

Esperado: nenhuma linha no `git status` (o script de init restaurado).
