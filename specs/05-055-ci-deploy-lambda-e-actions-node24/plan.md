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
| Onde conferir que o init do LocalStack terminou bem | No healthcheck do `localstack` no `docker-compose.yml`: lê `/_localstack/init/ready` e só sai com `0` quando `completed` é verdadeiro e todos os scripts estão `SUCCESSFUL` | adotada (Leila, 2026-10-24) | O `--wait` passa a cobrir o init em todo lugar que usa o Compose (CI duas vezes, `docker-up.sh`, roteiros manuais), sem um passo a mais em cada um. Alternativa descartada: só um passo no CI, que acusa no CI, mas deixa o ambiente local subindo "pronto" sem a Lambda. O passo do CI fica também, para o log (linha "Saída do init visível no CI"). O healthcheck roda dentro do container: usar o que a imagem já tem (`curl` e `python3`, a confirmar na T002; se faltar um, usar o outro sozinho) |
| Tempo limite do healthcheck | `start_period` que cubra o deploy da Lambda no runner (medir na T002 o tempo da subida no CI e no WSL e deixar folga), mantendo `interval: 5s` | proposta | Durante o `start_period`, falha do healthcheck não conta como tentativa: o deploy tem tempo de terminar sem dar `unhealthy` falso. Um script `ERROR` continua a dar `unhealthy` depois do `start_period` mais as `retries`, porque o estado não muda mais |
| `03-deploy-email-lambda.sh` quando a função não fica `Active` | Depois do laço, `exit 1` com mensagem (`EmailLambda did not become Active in 30s, last state: ...`) para o stderr. Depois do `create-event-source-mapping`, outro laço igual até o mapeamento ficar `Enabled` (`awslocal lambda list-event-source-mappings --function-name EmailLambda`), com o mesmo tratamento | proposta | Sem isso, o script pode terminar `SUCCESSFUL` com a função `Pending`/`Failed`, e o healthcheck não teria o que acusar. O mapeamento nasce `Creating` e passa a `Enabled`; conferir o estado é o que prova que ele existe e está ativo. Os 30 s atuais ficam, a não ser que a T002 mostre que não bastam no runner |
| Saída do init visível no CI | Script `scripts/check-localstack.sh`, chamado num passo do CI logo depois de cada subida do LocalStack: imprime o estado de cada script de init, o `Configuration.State` da `EmailLambda` e o `State` e o `EventSourceArn` do mapeamento, e sai com `1` se algum não estiver como esperado | proposta | Mesmo com o healthcheck falhando certo, o issue pede evidência no log de que o deploy aconteceu. Script e não comando solto no `ci.yml` porque roda duas vezes no CI e serve para a verificação manual (mesmo uso que `scripts/test-api-key.sh`). Usa `docker compose exec -T localstack awslocal ...`, como o roteiro do WSL |
| Log dos containers quando algo falha | Passo `if: failure()` antes de "Take down the containers of app's tests": `docker compose logs --no-color localstack email-lambda-builder`. E outro igual antes de "Stop Postgres, LocalStack, config-server and email-service", para a segunda subida | proposta | `docker compose down -v` apaga os containers e o log junto, então o passo tem que vir antes de cada derrubada. `failure()` cobre qualquer passo anterior que falhou, inclusive as suítes, onde o log do LocalStack também ajuda. O `email-lambda-builder` é one-shot e já saiu, mas o log dele continua até o `down` |
| `docker-up.sh` com `localstack` `unhealthy` | `compose_up` passa a mostrar o log do `localstack` também quando ele está `unhealthy` (`docker inspect -f '{{.State.Health.Status}}'`), não só `exited` | proposta | Com o healthcheck novo, um script de init quebrado deixa o container de pé e `unhealthy`; a causa está no log, como no caso do token |
| Versões das actions | Para cada uma das quatro, a T004 registra aqui: release mais recente, a versão escolhida, o runtime declarado no `action.yml` dela (`runs.using: node24`) e o que as notas de release mudaram desde a versão atual. `actions/setup-java` na `v5` (issue #182) | em aberto até a T004 | O issue pede para não chutar número de versão. A versão escolhida é a maior major que roda no Node.js 24 e não muda nada que o workflow usa; se a mais nova mudar algo que o workflow usa, ficar na major anterior que já roda no Node.js 24 e registrar o porquê |
| Forma de referenciar as actions | Tag de major (`@v5`), como hoje; `madrapps/jacoco-report` na tag que o projeto publica (hoje `@v1.7`) | adotada | Mantém o padrão atual do arquivo; fixar por SHA ficou fora de escopo |
| `madrapps/jacoco-report` sem versão no Node.js 24 | Fica na `v1.7`, com o aviso de Node.js 20 só para ela; a T004 registra aqui que não há versão no Node.js 24 | adotada (Leila, 2026-10-24) | É a única das quatro de terceiros. Manter evita trocar a action e o formato do comentário de cobertura; o risco aceito é ela parar de funcionar se o GitHub deixar de forçar o Node.js 24 |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

Nenhum código Java muda. Arquivos:

- `docker-compose.yml`: healthcheck do `localstack` e o comentário dele.
- `docker/localstack/init/03-deploy-email-lambda.sh`: falha explícita e espera do mapeamento.
- `scripts/check-localstack.sh` (novo).
- `scripts/docker-up.sh`: `compose_up` com `unhealthy`.
- `.github/workflows/ci.yml`: passos de conferência e de log, versões das actions.
- `README.md`: o `scripts/check-localstack.sh` na seção que explica como subir o ambiente.

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
