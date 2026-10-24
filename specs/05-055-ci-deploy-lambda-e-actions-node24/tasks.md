# Tasks: CI confere o deploy da Lambda e sai do Node.js 20

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issues: [#98](https://github.com/lalgarve/jogo-acoes/issues/98) (T001–T003, T006) e
[#182](https://github.com/lalgarve/jogo-acoes/issues/182) (T004, T005). As duas já existem e
fazem o papel de épico; não há Issue nova por tarefa.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | `03-deploy-email-lambda.sh`: `exit 1` com mensagem quando a `EmailLambda` não chega a `Active` em 30 s; depois do `create-event-source-mapping`, laço igual (também `seq 1 30`) até o mapeamento ficar `Enabled`, com o mesmo tratamento. Comentário do script atualizado | — | [P] | #98 |
| T002 | Healthcheck do `localstack` no `docker-compose.yml` lendo `/_localstack/init/ready` (completo e todos `SUCCESSFUL`), com `start_period` medido: registrar aqui quanto a subida leva no WSL e no CI. Confirmar antes o que a imagem tem (`curl`, `python3`). Comentário do serviço atualizado | T001 | | #98 |
| T003 | `scripts/check-localstack.sh` (estado dos scripts de init, da função e do mapeamento; `exit 1` se algum não estiver certo). No `ci.yml`: passo que roda o script depois de cada uma das duas subidas do LocalStack, e passo `if: failure()` com `docker compose logs --no-color localstack email-lambda-builder` antes de cada `docker compose down -v`. `scripts/docker-up.sh`: log do `localstack` também quando `unhealthy`. README: o script na seção de subida do ambiente | T002 | | #98 |
| T004 | Para `actions/checkout`, `actions/setup-java`, `actions/upload-artifact` e `madrapps/jacoco-report`: release mais recente, `runs.using` do `action.yml` da versão escolhida, e o que as notas de release mudaram desde a versão atual em relação ao que o workflow usa (`plan.md`). Registrar na tabela de decisões do `plan.md` (commit `decision:`). Se o `jacoco-report` não tiver versão no Node.js 24, levar a escolha para a Leila antes da T005 | — | [P] | #182 |
| T005 | `ci.yml`: as quatro actions nas versões da T004, `actions/setup-java` na `v5`, sem mudar `with:` além do que as notas de release exigirem | T004 | | #182 |
| T006 | Critério de aceite de exceções (`memory/constitution.md`): nenhuma classe de exceção nem branch de exceção em código Java. Registrar aqui os caminhos de erro de shell (script de init com `ERROR`, função que não fica `Active`, mapeamento que não fica `Enabled`, `docker-up.sh` com `localstack` `unhealthy`) e onde a T008 exercita cada um | T001–T003 | | #98 |
| T007 | CI da PR: verde; o log mostra a saída do `check-localstack.sh` nas duas subidas; o artefato `jacoco-app` existe; o comentário de cobertura foi atualizado (não duplicado); nenhum aviso de Node.js 20 nem de `setup-java` v4 nas anotações do job. Registrar aqui o link da execução | T003, T005 | | #98, #182 |
| T008 | Verificação manual, com os comandos abaixo | T001–T003 | | #98 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo. As duas linhas de trabalho (T001–T003 e T004–T005) são independentes até a
  T007.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Verificação manual (T008)

Na raiz do repositório, no branch da implementação, no WSL. Só o LocalStack é usado, sem o
overlay `blackbox` nem os serviços: a verificação é do init dele. Os passos 2 a 4 quebram o
`03-deploy-email-lambda.sh` de propósito e o restauram no fim com `git checkout`.

### 1. Init completo

```bash
docker compose down -v
docker compose up -d --wait localstack; echo "exit=$?"
docker inspect -f '{{.State.Health.Status}}' jogo-acoes-localstack-1
./scripts/check-localstack.sh; echo "exit=$?"
```

Esperado: `exit=0`; `healthy`; o script lista `01-create-queue.sh`, `02-verify-ses-sender.sh` e
`03-deploy-email-lambda.sh` como `SUCCESSFUL`, a `EmailLambda` como `Active` e o mapeamento como
`Enabled`, com `EventSourceArn` terminando em `:jogo-acoes-email-commands`; `exit=0`.

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

Com o laço zerado, o script não espera e cai direto na falha.

```bash
docker compose down -v
sed -i 's/seq 1 30/seq 1 0/' docker/localstack/init/03-deploy-email-lambda.sh
docker compose up -d --wait localstack; echo "exit=$?"
docker compose logs localstack | grep -c 'did not become Active'
git checkout -- docker/localstack/init/03-deploy-email-lambda.sh
```

Esperado: `exit=` diferente de `0` e `1` (a mensagem da T001 no log).

### 4. Mapeamento que não fica `Enabled`, pelo `docker-up.sh`

Só o segundo laço zerado: a função fica `Active` e o mapeamento não é esperado. Roda pelo
`scripts/docker-up.sh`, para ver o log do `localstack` `unhealthy`.

```bash
docker compose down -v
sed -i '0,/seq 1 30/! s/seq 1 30/seq 1 0/' docker/localstack/init/03-deploy-email-lambda.sh
grep -c 'seq 1 30' docker/localstack/init/03-deploy-email-lambda.sh
./scripts/docker-up.sh; echo "exit=$?"
git checkout -- docker/localstack/init/03-deploy-email-lambda.sh
```

Esperado: `1` no `grep` (só o primeiro laço intacto); o `docker-up.sh` para na primeira subida
com as últimas linhas do log do `localstack`, entre elas a mensagem do mapeamento que não ficou
`Enabled`, e `docker-up: docker compose up --build db db-email-service localstack dns failed` (sem `--build` se o `docker-up.conf` tiver `BUILD=false`);
`exit=1`.

### Derrubar no fim

```bash
docker compose down -v
git status --short docker/localstack/init/
```

Esperado: nenhuma linha no `git status` (o script de init restaurado).
