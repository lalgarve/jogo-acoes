# Plan: CI confere o deploy da Lambda e sai do Node.js 20

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `.github/workflows/ci.yml` (job `test`) sobe o LocalStack duas vezes:
  - "Start Postgres, LocalStack and the test DNS": `docker compose up -d --wait db
    db-email-service localstack dns`, antes da suíte do `app`;
  - depois de "Take down the containers of app's tests" (`docker compose down -v`, spec 05-046,
    T020), "Start PostgreSQL, LocalStack, the test DNS and config-server for email-service" sobe
    tudo de novo, do zero, para as suítes do `email-service` e da `email-lambda`.
- `docker-compose.yml`, serviço `localstack` (`localstack/localstack:4`): healthcheck
  `awslocal sqs list-queues`, `interval: 5s`, `retries: 10`, sem `start_period`. Depende de
  `email-lambda-builder` (`service_completed_successfully`), que gera
  `email-lambda/dist/function.zip`. Os scripts de `docker/localstack/init/` são montados em
  `/etc/localstack/init/ready.d` e rodam em ordem de nome:
  - `01-create-queue.sh`: fila `jogo-acoes-email-commands` e a DLQ;
  - `02-verify-ses-sender.sh`: remetente `no-reply@jogo-acoes.example`;
  - `03-deploy-email-lambda.sh`: `create-function` da `EmailLambda`, espera até 30 s pelo
    `Active` (sem falhar se não chegar) e `create-event-source-mapping` da fila para a função.
- `/_localstack/init/ready` devolve `completed` e, para cada script, `name` e `state`
  (`SUCCESSFUL`, `ERROR`, ...). `scripts/cloud-up.sh` já lê esse endpoint e trata `ERROR` como
  falha; é o formato conferido na prática neste projeto.
- `scripts/docker-up.sh`, `compose_up`: quando o `--wait` falha, mostra o log do `localstack`
  só se o container saiu (`exited`), caso do token inválido do Pro (spec 05-050).
- `docker-compose.localstack-pro.yml` só troca a imagem e acrescenta o token; herda o
  healthcheck e os scripts.
- Actions no `ci.yml`: `actions/checkout@v4`, `actions/setup-java@v4`,
  `actions/upload-artifact@v4`, `madrapps/jacoco-report@v1.7`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde conferir que o init do LocalStack terminou bem | No healthcheck do `localstack` no `docker-compose.yml`: lê `/_localstack/init/ready` e só sai com `0` quando `completed` é verdadeiro e todos os scripts estão `SUCCESSFUL` | adotada (Leila, 2026-10-24) | O `--wait` passa a cobrir o init em todo lugar que usa o Compose (CI duas vezes, `docker-up.sh`, roteiros manuais), sem um passo a mais em cada um. Alternativa descartada: só um passo no CI, que acusa no CI, mas deixa o ambiente local subindo "pronto" sem a Lambda. O passo do CI fica também, para o log (linha "Saída do init visível no CI"). O healthcheck roda dentro do container com `python3`, que as duas imagens têm (conferido na T002: `/usr/local/bin/python3` no Community e no Pro; o `curl` também existe, mas o JSON é lido e não filtrado com `grep`). Sem proxy no `urllib`, para um proxy da configuração do Docker não ficar entre o container e a própria porta |
| Tempo limite do healthcheck | `start_period: 180s` (era 120 s; mudou com a espera por tempo, linha abaixo), mantendo `interval: 5s`, `timeout: 5s` e `retries: 10` | adotada | Durante o `start_period`, falha do healthcheck não conta como tentativa, e o container fica `healthy` assim que passa. Medido na T002: `healthy` em 21–23 s numa sessão de nuvem, com a imagem do runtime da Lambda já baixada; os 180 s cobrem a espera máxima do `03-deploy-email-lambda.sh` (120 s + 30 s) mais o resto do init. Um script `ERROR` dá `unhealthy` depois do `start_period` mais as `retries` (uns 68 s com `start_period: 20s` nos testes; com 180 s, perto de 230 s) |
| `03-deploy-email-lambda.sh` quando a função não fica `Active` | Função `wait_for_state` no script: roda o comando a cada segundo até o estado esperado e conta o tempo no relógio (`date +%s`), não em tentativas. Até `FUNCTION_TIMEOUT_SECONDS=120` pela função (`Active`) e `MAPPING_TIMEOUT_SECONDS=30` pelo mapeamento (`Enabled`, lido com `get-event-source-mapping --uuid` do mapeamento criado). Desiste na hora se a função ficar `Failed`, e então imprime o `StateReasonCode` e o `StateReason` dela. Mensagens para o stderr: `EmailLambda did not become Active in 120s, last state: ...`, `EmailLambda is Failed instead of Active` | adotada (espera por tempo: Leila, 2026-10-24, depois da T008) | Sem isso, o script pode terminar `SUCCESSFUL` com a função `Pending`/`Failed`, e o healthcheck não teria o que acusar. A primeira versão contava 30 tentativas de 1 s; no WSL cada `awslocal` leva ~1,5 s e a função ficou `Pending` por ~52 s na primeira rodada da T008, então o script falhou num ambiente certo. Os limites são só o máximo: a espera acaba assim que o estado chega. `Failed` não muda sozinho, então esperar mais não adianta |
| Saída do init visível no CI | Script `scripts/check-localstack.sh`, chamado num passo do CI logo depois de cada subida do LocalStack: imprime o estado de cada script de init, o `Configuration.State` da `EmailLambda` e o `State` do mapeamento que sai da fila `jogo-acoes-email-commands`, e sai com `1` se algum não estiver como esperado | adotada | Mesmo com o healthcheck falhando certo, o issue pede evidência no log de que o deploy aconteceu. Script e não comando solto no `ci.yml` porque roda duas vezes no CI e serve para a verificação manual (mesmo uso que `scripts/test-api-key.sh`). Roda tudo dentro do container (`docker compose exec -T localstack`, `python3` e `awslocal`), sem depender do que o host tem; `LOCALSTACK_CONTAINER` troca o serviço do Compose por um container com nome, como o do `cloud-up.sh` |
| Log dos containers quando algo falha | Passo `if: failure()` antes de "Take down the containers of app's tests": `docker compose logs --no-color localstack email-lambda-builder`. E outro igual antes de "Stop Postgres, LocalStack, config-server and email-service", para a segunda subida | adotada | `docker compose down -v` apaga os containers e o log junto, então o passo tem que vir antes de cada derrubada. `failure()` cobre qualquer passo anterior que falhou, inclusive as suítes, onde o log do LocalStack também ajuda. O `email-lambda-builder` é one-shot e já saiu, mas o log dele continua até o `down` |
| `docker-up.sh` com `localstack` `unhealthy` | `compose_up` passa a mostrar o log do `localstack` também quando ele está `unhealthy` (`docker inspect -f '{{.State.Health.Status}}'`), não só `exited` | adotada | Com o healthcheck novo, um script de init quebrado deixa o container de pé e `unhealthy`; a causa está no log, como no caso do token. As linhas das chamadas do healthcheck (`GET /_localstack/init/ready`, uma a cada 5 s) saem antes do `tail`: conferido na T002, sem isso as 15 últimas linhas eram só elas |
| Versões das actions | `actions/checkout@v7`, `actions/setup-java@v6`, `actions/upload-artifact@v7`, `madrapps/jacoco-report@v1.8` (tabela abaixo) | adotada (T004, 2026-10-24) | A maior major que roda no Node.js 24 e não muda nada que o workflow usa. `setup-java` vai para a `v6`, não a `v5` que o aviso e o issue citam: a `v6` já existe, roda no Node.js 24 e nada do que ela mudou toca o workflow; ficar na `v5` só adiaria a próxima migração. Fora do Node.js 24, as notas de release não mudam nada que o workflow usa |
| Forma de referenciar as actions | Tag de major (`@v7`), como hoje; `madrapps/jacoco-report` na tag de minor que o projeto publica (`@v1.8`, como era `@v1.7`) | adotada | Mantém o padrão atual do arquivo; fixar por SHA ficou fora de escopo |
| `madrapps/jacoco-report` sem versão no Node.js 24 | Se não houvesse, ficaria na `v1.7` com o aviso. A T004 achou a `v1.8.0`, em `node24`, então ela é atualizada como as outras | adotada (Leila, 2026-10-24); o caso não aconteceu | A `v1.8.0` só muda o runtime: o `action.yml` dela é igual ao da `v1.7.2` fora de `runs.using` |

### Versões das actions (T004, 2026-10-24)

Lidas nas páginas de releases de cada action e no `action.yml` de cada versão, e as tags
conferidas com `git ls-remote`.

| Action | Antes | Release mais recente | Escolhida | `runs.using` | O que mudou desde a versão anterior e toca o workflow |
|---|---|---|---|---|---|
| `actions/checkout` | `v4` | `v7.0.1` | `v7` | `node24` | Nada. `v5`: Node.js 24. `v6`: credenciais em arquivo separado. `v7`: ESM e bloqueio de checkout de PR de fork em `pull_request_target`/`workflow_run`, gatilhos que o CI não usa |
| `actions/setup-java` | `v4` | `v6.1.0` | `v6` | `node24` | Nada. `v6`: tira as distribuições Adopt (o CI usa `temurin`), renomeia `jdkFile` (não usado), rejeita booleano inválido (nenhum), muda a chave do cache Maven (só invalida o cache uma vez) |
| `actions/upload-artifact` | `v4` | `v7.0.2` | `v7` | `node24` | Nada. `v6`: Node.js 24. `v7`: upload direto com `archive: false`, em que `name` é ignorado; o CI não usa `archive` |
| `madrapps/jacoco-report` | `v1.7` | `v1.8.0` | `v1.8` | `node24` | Nada: o `action.yml` da `v1.8.0` só difere do da `v1.7.2` em `runs.using` (`node20` → `node24`) |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

Nenhum código Java muda. Arquivos:

- `docker-compose.yml`: healthcheck do `localstack` e o comentário dele.
- `docker/localstack/init/03-deploy-email-lambda.sh`: falha explícita e espera do mapeamento.
- `scripts/check-localstack.sh` (novo).
- `scripts/docker-up.sh`: `compose_up` com `unhealthy`.
- `.github/workflows/ci.yml`: passos de conferência e de log, versões das actions.
- `README.md`: o healthcheck e o `scripts/check-localstack.sh` em "Pipeline de e-mail ponta a ponta em
  desenvolvimento".

## Riscos e trade-offs

- **Subida local mais lenta:** o `--wait` espera o deploy da Lambda (dezenas de segundos). Quem
  só quer o banco continua subindo só `db`; quem pede o `localstack` passa a esperar ele pronto
  de verdade.
- **Ambiente local que antes subia "meio pronto" passa a falhar:** com um script de init
  quebrado, o `--wait` falha em vez de entregar um LocalStack sem a Lambda. É o objetivo, mas
  muda o comportamento para quem usava o ambiente sem precisar da Lambda.
- **Re-rodar um script de init à mão** (ex.: `docs/verificacao-manual-wsl.md` roda
  `01-create-queue.sh` de novo pelo `docker compose exec`) não muda o estado de
  `/_localstack/init/ready`; o healthcheck não é afetado.
- **Formato do `/_localstack/init/ready`** pode mudar numa versão futura do LocalStack (a imagem
  é `:4`, não uma versão fixa). O `scripts/cloud-up.sh` já depende do mesmo formato; os dois
  quebrariam juntos e de forma visível (container `unhealthy`), não silenciosa.
- **Major nova de action com mudança de comportamento** (ex.: `upload-artifact` mudando o que
  sobe ou o nome do artefato, `jacoco-report` mudando o comentário): coberto pela leitura das
  notas de release na T004 e pela execução do CI da PR, que tem que mostrar o artefato
  `jacoco-app` e o comentário de cobertura atualizado.
- **Critério de aceite de exceções:** nenhuma classe de exceção nem branch de exceção em código
  Java. Os caminhos de erro novos são de shell (script de init que falha, função que não fica
  `Active`, mapeamento que não fica `Enabled`, `unhealthy` no `docker-up.sh`) e são exercitados
  na verificação manual (T008), não por teste automatizado: provocar a falha exige quebrar o
  init do LocalStack, o que não cabe numa suíte que usa o LocalStack compartilhado.
