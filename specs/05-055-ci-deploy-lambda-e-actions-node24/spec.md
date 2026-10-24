# Spec: CI confere o deploy da Lambda e sai do Node.js 20

**Status:** rascunho
**Issue:** [#98](https://github.com/lalgarve/jogo-acoes/issues/98) e
[#182](https://github.com/lalgarve/jogo-acoes/issues/182)
**Iteração:** iteration-5

## Resumo

Duas mudanças no CI (`.github/workflows/ci.yml`), juntas numa spec só porque mexem no mesmo
arquivo e se verificam pela mesma execução do CI:

1. **Deploy da `email-lambda` no LocalStack conferido de verdade (#98).** Hoje o passo que sobe o
   LocalStack passa mesmo que os scripts de init (`docker/localstack/init/`) falhem, e a saída
   desses scripts não aparece no log. Passa a falhar quando a fila, o remetente, a função
   `EmailLambda` ou o mapeamento de evento SQS não ficam prontos, e o estado da função e do
   mapeamento passa a aparecer no log do CI.
2. **Actions fora do Node.js 20 (#182).** `actions/checkout`, `actions/setup-java`,
   `actions/upload-artifact` e `madrapps/jacoco-report` passam para versões que rodam no
   Node.js 24, `actions/setup-java` para a `v5`, sem mudar o que o workflow faz.

## Motivação

- **#98, falha silenciosa:** a execução manual
  [36906561775](https://github.com/lalgarve/jogo-acoes/actions/runs/36906561775) passou e o log
  inteiro não tem nenhuma linha de `03-deploy-email-lambda`, `create-function` nem
  `create-event-source-mapping`. Causa:
  - `docker compose up -d` é destacado: o log do CI mostra a saída do `docker compose`, não a de
    dentro dos containers. A saída dos scripts `ready.d` do LocalStack e o build Maven do
    `email-lambda-builder` ficam invisíveis.
  - o `--wait` só espera o healthcheck do `localstack` (`awslocal sqs list-queues`), que prova
    que o SQS está de pé, não que os scripts de init terminaram. O comentário do
    `scripts/cloud-up.sh` confirma: "The container is healthy before the init scripts have
    created the queue, verified the sender and deployed email-lambda".
  - `03-deploy-email-lambda.sh` espera até 30 s a função ficar `Active` e, se não ficar, segue
    em frente sem falhar.

  Um `03-deploy-email-lambda.sh` quebrado não derruba o CI e nem aparece no log. O CI é o único
  lugar que roda o ambiente `docker` do zero a cada mudança; se ele não acusa, só a próxima
  verificação manual acusaria.
- **#182, avisos de deprecação:** o CI da PR #181 (2026-10-19) mostra dois avisos no job `test`,
  sem nenhum passo falhando:
  - "Node.js 20 is deprecated. The following actions target Node.js 20 but are being forced to
    run on Node.js 24: `actions/checkout@v4`, `actions/setup-java@v4`,
    `actions/upload-artifact@v4`, `madrapps/jacoco-report@v1.7`"
    ([changelog do GitHub](https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/));
  - "setup-java v4 is deprecated and will no longer receive updates. Please migrate to
    actions/setup-java@v5."

  Hoje o GitHub força essas actions a rodar no Node.js 24 e elas funcionam, mas isso pode parar.

## Cenários (comportamento esperado)

Não há `.feature` novo: a mudança é no ambiente e no CI, não no comportamento dos serviços.
Verificação (comandos prontos em `tasks.md`):

1. **Init do LocalStack completo:** `docker compose up -d --wait localstack` só termina com
   sucesso depois de os três scripts de init terminarem sem erro, com a função `EmailLambda` em
   `Active` e o mapeamento de evento da fila `jogo-acoes-email-commands` em `Enabled`.
2. **Init do LocalStack quebrado:** com um script de init que falha (ex.: runtime inválido no
   `create-function`), `docker compose up -d --wait localstack` termina com erro e o container
   fica `unhealthy`.
3. **Função que não fica `Active`:** `03-deploy-email-lambda.sh` termina com erro, em vez de
   seguir em frente, quando a função não chega a `Active` no tempo de espera; idem para o
   mapeamento que não chega a `Enabled`.
4. **Log do CI:** cada vez que o CI sobe o LocalStack (antes da suíte do `app` e de novo antes
   da suíte do `email-service`), o log mostra o estado dos scripts de init, o estado da função e
   o do mapeamento. Quando algum passo do job falha, o log mostra também a saída do `localstack`
   e do `email-lambda-builder`.
5. **Actions:** a execução do CI da PR desta spec fica verde e não mostra nenhum aviso de
   Node.js 20 nem de `setup-java` v4, a não ser o do `madrapps/jacoco-report`, se ele ficar na
   `v1.7` (decisões resolvidas).

## Requisitos funcionais

### Deploy da Lambda (#98)

- **Healthcheck do `localstack`** (`docker-compose.yml`): o container só fica `healthy` quando
  `/_localstack/init/ready` diz que o init terminou e todos os scripts estão `SUCCESSFUL`.
  Qualquer script `ERROR` deixa o container `unhealthy` e faz o `--wait` falhar. Vale para todo
  lugar que sobe o LocalStack pelo Compose: CI, `scripts/docker-up.sh`, verificações manuais, e
  também o overlay `docker-compose.localstack-pro.yml`, que herda o healthcheck.
- **`03-deploy-email-lambda.sh`** termina com erro quando a função não chega a `Active` ou o
  mapeamento não chega a `Enabled` no tempo de espera, com uma mensagem que diz o que faltou e o
  último estado lido.
- **CI**, depois de cada subida do LocalStack: um passo que imprime o estado dos scripts de init,
  o estado da função e o do mapeamento, e falha se algum não estiver como esperado. Um passo no
  fim do job, só quando algo falhou e antes de os containers serem derrubados, imprime
  `docker compose logs` do `localstack` e do `email-lambda-builder`.
- **`scripts/docker-up.sh`:** quando o `--wait` falha com o `localstack` `unhealthy` (não só
  quando ele saiu), mostra as últimas linhas do log dele, como já faz quando ele sai.

### Actions (#182)

- `actions/checkout`, `actions/setup-java`, `actions/upload-artifact` e `madrapps/jacoco-report`
  em versões que rodam no Node.js 24, escolhidas pela release mais recente de cada uma e pelas
  notas de release, nunca por palpite; `actions/setup-java` na `v5`.
- O que o workflow usa continua igual:
  - `setup-java`: `distribution: temurin`, `java-version: '21'`, `cache: maven`;
  - `upload-artifact`: `name: jacoco-app`, `path: app/target/site/jacoco/`, e o upload com
    `if: always()`;
  - `jacoco-report`: `paths`, `token`, `min-coverage-overall: 80`,
    `min-coverage-changed-files: 0`, `title`, `update-comment: true`, e o comentário na PR
    continua sendo atualizado, não duplicado.

## Requisitos não-funcionais

- A subida do LocalStack fica mais demorada, porque o `--wait` passa a esperar o deploy da
  Lambda; o limite do healthcheck tem que cobrir o tempo de deploy no runner do GitHub sem dar
  falso `unhealthy`.
- Nenhum segredo novo no CI: continua o LocalStack Community, sem token (spec 05-050).

## Fora de escopo

- Baseline Java: continua Java 21 (constitution, "Baseline Java e upgrades de LTS"). Só mudam as
  versões das actions.
- `scripts/cloud-up.sh`: sobe o LocalStack com `docker run`, fora do Compose, e já espera
  `/_localstack/init/ready` e falha com script `ERROR`. Ganha só a mudança do
  `03-deploy-email-lambda.sh`, que ele monta.
- Testar o consumo da fila pela Lambda no CI (publicar uma mensagem e conferir o e-mail no SES
  do LocalStack): esta spec confere que a função e o mapeamento existem e estão prontos, não o
  envio ponta a ponta. A suíte da `email-lambda` já testa o handler dentro da JVM (spec 05-053).
- Dependabot ou outra atualização automática das actions.
- Fixar as actions por SHA de commit em vez de tag de versão.

## Decisões resolvidas

- **Uma spec só para as duas Issues** (Leila, 2026-10-24): as duas mudam o CI.
- **Init conferido no healthcheck do Compose e também no CI** (Leila, 2026-10-24). O healthcheck
  do `localstack` faz o `--wait` falhar em todo lugar que usa o Compose, e o passo do CI
  (`scripts/check-localstack.sh`) deixa o estado da função e do mapeamento no log. Efeito local
  aceito: `docker compose up --wait localstack` demora mais (espera o deploy da Lambda) e, com um
  script de init quebrado, falha, quando antes subia um ambiente sem a Lambda.
- **`madrapps/jacoco-report` sem versão no Node.js 24** (Leila, 2026-10-24): se a T004 não
  encontrar release dessa action que rode no Node.js 24, ela fica na `v1.7`, com o aviso de
  Node.js 20 só para ela, e o motivo é registrado no `plan.md`. Se encontrar, é atualizada como as
  outras.

## Decisões em aberto

Nenhuma.
