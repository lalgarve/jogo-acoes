# Tasks: Pipeline de e-mail ponta a ponta em desenvolvimento (`email-lambda` como consumidor vivo)

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (mecanismo do poller, propriedades `quarkus-amazon-sqs`/`quarkus-amazon-ses`,
verificação do remetente, sobreposição do `docker-compose`) já estão resolvidas em `plan.md` —
esta lista só quebra a implementação em passos.

**Sem Cucumber/contrato OpenAPI novo** — infraestrutura de desenvolvimento, sem `.feature` novo
nem rota nova (mesmo padrão das specs 05-006/05-008/05-014).

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#78](https://github.com/lalgarve/jogo-acoes/issues/78) — nenhuma virou Issue própria
(todas pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Adicionar dependência `io.quarkiverse.amazonservices:quarkus-amazon-sqs` a `email-lambda/pom.xml` (sem versão explícita, gerenciada pelo BOM já importado) | — | [P] | #78 |
| ~~T002~~ | Adicionar `email.dev-poller.enabled=false` e `email.queue-name=${EMAIL_QUEUE_NAME:jogo-acoes-email-commands}` a `email-lambda/src/main/resources/application.properties` | — | [P] | #78 |
| ~~T003~~ | Criar `EmailQueuePoller.java` — bean CDI, observa `StartupEvent`, ativo só quando `email.dev-poller.enabled=true`; *thread* dedicada com `receiveMessage` (long polling, `waitTimeSeconds=20`) contra a fila resolvida de `email.queue-name`, monta `SQSEvent`/`SQSMessage` por mensagem e chama `EmailSendHandler.handleRequest`; deleta a mensagem só em caso de sucesso; observa interrupção da *thread* para sair do loop no `SIGTERM` | T001, T002 | | #78 |
| ~~T004~~ | Rodar a suíte de `email-lambda` (`mvn -pl email-lambda -am test`) — confirmar verde, incluindo o comportamento inalterado de `EmailSendHandlerTest` (a nova dependência `quarkus-amazon-sqs` não deve introduzir *side effect* observável, já que nenhum teste usa `SqsClient`) | T001, T002, T003 | | #78 |
| ~~T005~~ | Criar `email-lambda/Dockerfile` — multi-stage, builda só `-pl email-lambda -am package -DskipTests` (modo JVM), copia o jar empacotado para a imagem final | — | [P] | #78 |
| ~~T006~~ | Mudar `docker-compose.yml`: `localstack.environment.SERVICES` de `sqs` para `sqs,ses` | — | [P] | #78 |
| ~~T007~~ | Criar `docker/localstack/init/02-verify-ses-sender.sh` — `awslocal ses verify-email-identity --email-address no-reply@jogo-acoes.example`, mesmo valor de `email.sender-address` em `email-lambda/src/main/resources/application.properties` | T006 | | #78 |
| ~~T008~~ | Criar `docker-compose.email-lambda.yml` — serviço `email-lambda` (`build: email-lambda/Dockerfile`, contexto na raiz do repo), `depends_on: localstack` saudável, variáveis `QUARKUS_SQS_ENDPOINT_OVERRIDE`/`QUARKUS_SES_ENDPOINT_OVERRIDE=http://localstack:4566`, `QUARKUS_SQS_AWS_REGION`/`QUARKUS_SES_AWS_REGION=us-east-1`, `QUARKUS_SQS_AWS_CREDENTIALS_TYPE`/`QUARKUS_SES_AWS_CREDENTIALS_TYPE=static`, `QUARKUS_..._CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID`/`_SECRET_ACCESS_KEY=test`, `EMAIL_DEV_POLLER_ENABLED=true` | T003, T005 | | #78 |
| ~~T009~~ | Atualizar `README.md` — nova seção curta: como subir (`docker compose -f docker-compose.yml -f docker-compose.email-lambda.yml up`), como confirmar que uma mensagem foi processada (logs do serviço `email-lambda`, `awslocal sqs get-queue-attributes`), e que isso é só para desenvolvimento local | T008 | | #78 |
| ~~T010~~ | Verificação manual de ponta a ponta: `docker compose -f docker-compose.yml -f docker-compose.email-lambda.yml up`, disparar um fluxo que publique na fila (ex.: pedido de login avulso via Swagger UI/`app`), confirmar nos logs de `email-lambda` que a mensagem foi consumida e removida da fila, sem erro de remetente não verificado | T004, T006, T007, T008, T009 | | #78 |
| ~~T011~~ | Rodar a suíte completa do módulo `app` (`mvn -pl app -am test`) — confirmar verde, nenhum teste existente alterado; esta spec não toca `app/src` | — | [P] | #78 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`feat`).
- T010 depende de Docker disponível no ambiente de implementação (mesma limitação já registrada
  em `specs/05-014-ambiente-testes-blackbox/tasks.md` e `specs/05-016-.../tasks.md`) — se não
  estiver disponível, registrar explicitamente e validar o que der (T004/T011, que não
  precisam de `docker compose up` completo).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.

## T010 — registro da verificação

Ambiente Docker indisponível neste ambiente de implementação (mesmo bloqueio já registrado nas
specs 05-014/05-015/05-016/05-018/05-020) — `docker compose -f docker-compose.yml -f
docker-compose.email-lambda.yml up` não pôde ser executado de verdade. Validado o que dava para
validar sem Docker:

- `mvn -pl email-lambda -am test` verde (2/2, o teste que depende de LocalStack pula por falta
  de Docker, mesmo comportamento já documentado em `EmailSendHandlerTest`) — T004.
- `mvn -pl email-lambda -am package -DskipTests` roda de verdade e produz o artefato — usado
  para validar o `Dockerfile` (T005, achado abaixo) sem precisar do daemon Docker: copiado o
  jar + `lib/` resultantes para fora do `target/`, executado `java -jar app.jar` diretamente —
  sobe limpo (`Installed features: [amazon-lambda, amazon-sdk-ses, amazon-sdk-sqs, cdi]`,
  poller inativo por padrão) e encerra limpo ao receber `SIGTERM`.
- `docker compose -f docker-compose.yml -f docker-compose.email-lambda.yml config -q` — valida
  a sintaxe/resolução das duas sobreposições juntas sem precisar do daemon; retornou sem erro.
- `mvn -pl app -am test` verde (155/155) — T011, confirma que nada em `app/src` foi tocado.

Achado durante a implementação (T005): o módulo empacota como *legacy thin jar*
(`email-lambda-*-runner.jar` + `target/lib/*.jar`, `Class-Path` no manifest apontando pra
`lib/...` relativo), não como o layout *fast-jar* (`target/quarkus-app/`) que o `Dockerfile`
original do `app/` usa — o `plan.md` original assumia o segundo por engano. `Dockerfile` deste
módulo ajustado para copiar o jar + `lib/` para o mesmo diretório na imagem final, preservando o
`Class-Path` relativo.
