# Plan: e-mail entregue duas vezes pela fila é mandado uma vez só

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

Esta spec e a 05-059 cobrem juntas a [Issue #205](https://github.com/lalgarve/jogo-acoes/issues/205).
A confirmação do envio pelos eventos do SES fica na [Issue #208](https://github.com/lalgarve/jogo-acoes/issues/208).

## Contexto técnico

- `email-lambda` (Quarkus, `quarkus-amazon-lambda`, `quarkus-amazon-ses`, `quarkus-amazon-sqs`):
  - `EmailSendHandler implements RequestHandler<SQSEvent, Void>` percorre os registros do lote.
    Erro classificado do SES vai para a DLQ com `errorType`/`errorMessage` e o registro conta como
    sucesso; mensagem malformada ou erro não classificado lança exceção e falha a invocação
    inteira, e o SQS reentrega o lote todo.
  - `EmailMessage` (seis campos) espelha o `EmailQueueMessage` do `email-service`, sem módulo
    compartilhado.
  - Testes: `EmailSendHandlerTest` (`@QuarkusTest`) contra o LocalStack de pé (porta 4566), com
    fila DLQ própria (`email-lambda-test-dlq`), e `SentRequestRecorder` para ler as tags.
- LocalStack (`docker-compose.yml`, `scripts/cloud-up.sh`): `SERVICES=sqs,ses,s3,lambda`, sem
  DynamoDB. `docker/localstack/init/03-deploy-email-lambda.sh` cria a função (`--timeout 15`) e o
  mapeamento da fila com `--batch-size 1`. A fila (`01-create-queue.sh`) tem `maxReceiveCount` 3 e
  o tempo de visibilidade padrão (30 s), maior que o tempo limite da função.
- `email-service`:
  - `EmailQueuePublisher.messageBody` monta a mensagem com `send.getId()` como `correlationId`.
  - `DeadLetterConsumer` acha o envio pelo `correlationId`, ignora o que não está `QUEUED` e chama
    `RetryPolicy.onFailure`, que incrementa `attempts` (`EmailSend.recordFailure`) e reagenda com
    espera crescente (1 s dobrando até 5 min) ou desiste depois de 24 h.
  - `DeliveryErrorType.fromAttribute` trata valor desconhecido como `UNKNOWN`.
  - `last_error_type` é `VARCHAR(32)`.
- Contrato da DLQ: `specs/05-046-publicacao-na-fila-em-segundo-plano/contracts/dlq-message.md`.
  O contrato da mensagem da fila está na tabela de decisões do `plan.md` da 05-031.
- Não há Terraform no repositório (conta AWS real ainda não existe).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Número da tentativa | `attempt` = `send.getAttempts() + 1` no momento de publicar (`EmailQueuePublisher.messageBody`). | proposta | `attempts` só aumenta quando uma tentativa falha (`recordFailure`), que é quando o `email-service` decide tentar de novo. Publicar de novo depois de um commit que falhou (caminho 3 da `spec.md`) não muda `attempts`, então repete o número. Uma falha de publicação (`QUEUE_UNAVAILABLE`) também aumenta o número, sem problema: só importa que mude a cada nova tentativa. |
| Mensagem da fila | Campo `attempt` (inteiro) em `EmailQueueMessage` e `EmailMessage`. `SCHEMA_VERSION` passa a `"2"`. A Lambda recusa como malformada uma mensagem sem `attempt`. | proposta | Pré-produção: sem formato antigo em paralelo. Mudar a versão não é para compatibilidade, é para a mensagem antiga, se aparecer numa fila de teste, falhar com uma mensagem clara. |
| Armazenamento do registro | Tabela DynamoDB `jogo-acoes-email-sends`, chave de partição `id` (texto `"<correlationId>#<attempt>"`), atributos `state` (`IN_PROGRESS`, `SENT`, `FAILED`) e `expiresAt` (TTL, epoch em segundos). Extensão `quarkus-amazon-dynamodb` no `email-lambda`; nome da tabela em `email.idempotency-table`. | proposta | Decisão 8 da Iteração 4, com a chave da `spec.md`. Uma chave texto só evita chave composta sem necessidade. |
| Retenção | `expiresAt` = agora + 5 dias. | proposta | Retenção padrão da fila (4 dias) mais as 24 h de tentativas do `email-service` (`spec.md`). |
| Antes do SES | `PutItem` com `attribute_not_exists(id)`, `state = IN_PROGRESS`. | resolvida | Decidido na `spec.md` (2026-10-25). |
| Chave já existe | `GetItem` com leitura consistente. `SENT` ou `FAILED`: duplicata, descartada com log (`correlationId`, `attempt`). `IN_PROGRESS`: DLQ com `errorType = SEND_IN_DOUBT`, sem chamar o SES. | proposta | `SEND_IN_DOUBT` decidido na `spec.md` (2026-10-29). `FAILED` (ver abaixo) separa "esta tentativa já foi para a DLQ" de "não se sabe o que houve". Com o tempo de visibilidade (30 s) maior que o tempo limite da função (15 s), um `IN_PROGRESS` encontrado numa reentrega é de uma invocação que já parou, não de uma ainda rodando. |
| SES aceitou | `UpdateItem` para `SENT`. Se essa escrita falhar, o registro do lote conta como sucesso e o erro vai para o log. | proposta | Reportar a falha faria o SQS reentregar, a reentrega acharia `IN_PROGRESS` e, 15 minutos depois, o e-mail sairia de novo. Deixar `IN_PROGRESS` só causa duplicata se a mesma mensagem chegar de novo pelo SQS, o que é bem mais raro. |
| SES recusou (erro classificado) | DLQ com o tipo, como hoje, e `UpdateItem` para `FAILED`. | proposta | A `spec.md` diz que o registro fica. Marcar `FAILED` faz uma cópia duplicada da mesma tentativa ser descartada, em vez de virar um `SEND_IN_DOUBT` falso aplicado à tentativa seguinte. |
| Erro não classificado antes do SES aceitar | `DeleteItem` do registro e o registro do lote é reportado como falha. | resolvida | `spec.md`: a reentrega tenta de novo. Se o `DeleteItem` também falhar, o registro fica `IN_PROGRESS` e a reentrega vira `SEND_IN_DOUBT`: o e-mail é decidido pelo `email-service`, não perdido. |
| DynamoDB fora do ar no `PutItem` | O registro do lote é reportado como falha, sem chamar o SES. | resolvida | `spec.md`. |
| Falha parcial do lote | `RequestHandler<SQSEvent, SQSBatchResponse>`: cada registro que falha entra em `batchItemFailures` com o `messageId`; nenhuma exceção sai do handler. Mapeamento com `--function-response-types ReportBatchItemFailures`. A mensagem malformada também passa a ser falha do registro, não da invocação. | resolvida | Decidido na `spec.md` (2026-10-25). Para a mensagem malformada o resultado final é o mesmo de hoje (DLQ sem tipo depois de 3 entregas), sem reentregar o resto do lote. |
| `email-service` e `SEND_IN_DOUBT` | Valor novo em `DeliveryErrorType`. `RetryPolicy.onFailure` usa, para ele, a espera `email.publisher.in-doubt-delay` (padrão `15m`) em vez da espera crescente. A regra de desistir depois de 24 h vale igual. | proposta | `spec.md`: 15 minutos antes da próxima tentativa. Configurável para os testes não esperarem 15 minutos. |
| DLQ de uma tentativa antiga | `DeadLetterConsumer` compara o `attempt` da mensagem com `send.getAttempts() + 1`. Diferente: descarta com log, como já faz com envio que não está `QUEUED`. | proposta | Um `SEND_IN_DOUBT` (ou outro erro) que chega atrasado, de uma tentativa já substituída, não pode contar como falha da tentativa atual. |
| Infraestrutura local | `dynamodb` em `SERVICES` (`docker-compose.yml` e `scripts/cloud-up.sh`, que repete a configuração do LocalStack); script novo `docker/localstack/init/04-create-email-sends-table.sh` (tabela e TTL); variáveis `QUARKUS_DYNAMODB_*` e `EMAIL_IDEMPOTENCY_TABLE` na função em `03-deploy-email-lambda.sh`, mais `--function-response-types ReportBatchItemFailures` no mapeamento. | proposta | A função só usa a tabela quando chega a primeira mensagem, depois que todos os scripts de `ready.d` rodaram, então a ordem dos scripts não importa. |
| Permissão na AWS real | Registrada para quando houver Terraform: `dynamodb:PutItem`, `GetItem`, `UpdateItem`, `DeleteItem` na tabela. | proposta | O LocalStack não confere IAM. Sem conta real, só fica anotado (Decisão 5 e 6 da Iteração 4). |
| Testes | `EmailSendHandlerTest`, contra o LocalStack: mesma mensagem duas vezes → um envio no SES; lote com um registro malformado no meio → só ele em `batchItemFailures` e os outros mandados uma vez; registro `IN_PROGRESS` gravado antes → DLQ com `SEND_IN_DOUBT` e nada no SES; `FAILED` e `SENT` → descartados; erro classificado → DLQ e `FAILED`; tabela inexistente → falha do registro e nada no SES. A suíte cria e apaga a própria tabela (`email-lambda-test-sends`), como já faz com a DLQ. No `email-service`, `DeadLetterConsumerIntegrationTest`: `SEND_IN_DOUBT` reagenda com a espera configurada; `attempt` antigo é descartado. | proposta | Infraestrutura real, sem mock (constitution, "Testes"). O teste de ponta a ponta da `spec.md` (`blackbox-tests/`) fica com o envio normal passando pela tabela; os casos de falha são difíceis de provocar de fora e já ficam cobertos aqui. |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

- `email-lambda/pom.xml`: `quarkus-amazon-dynamodb`.
- `email-lambda/.../EmailMessage.java`: campo `attempt`.
- `email-lambda/.../SendRecordStore.java` (novo): `PutItem`/`GetItem`/`UpdateItem`/`DeleteItem`
  da tabela, com os três estados. Classe à parte para o handler continuar lendo como a
  sequência da `spec.md`.
- `email-lambda/.../EmailSendHandler.java`: `SQSBatchResponse`, checagem de duplicata,
  `SEND_IN_DOUBT`.
- `email-lambda/src/main/resources/application.properties` e
  `src/test/resources/application.properties`: endpoint e credenciais do DynamoDB, nome da tabela.
- `email-service/.../send/EmailQueueMessage.java`, `EmailQueuePublisher.java`,
  `DeliveryErrorType.java`, `RetryPolicy.java`, `DeadLetterConsumer.java`.
- `docker-compose.yml`, `scripts/cloud-up.sh`, `docker/localstack/init/03-deploy-email-lambda.sh`,
  `docker/localstack/init/04-create-email-sends-table.sh` (novo).
- Contratos: `specs/05-046-publicacao-na-fila-em-segundo-plano/contracts/dlq-message.md`
  (`SEND_IN_DOUBT`); contrato da mensagem (`attempt`, `schemaVersion` 2) registrado num
  `contracts/queue-message.md` novo desta spec, que passa a ser o documento de referência da
  mensagem.

O `ArchitectureTest` do `email-lambda` continua valendo: nenhum acesso ao banco do `app` ou do
`email-service`.

## Riscos e trade-offs

- **Duplicata que sobra.** Se o SES aceitou e a invocação parou antes de gravar `SENT`, o
  `SEND_IN_DOUBT` faz o e-mail sair de novo depois de 15 minutos (risco aceito na `spec.md`, até
  a Issue #208).
- **LocalStack Community e `ReportBatchItemFailures`.** O plano supõe que o mapeamento do
  LocalStack 4 respeita a resposta de falha parcial. Conferir no primeiro teste (T004); se não
  respeitar, registrar e decidir antes de seguir.
- **Mais uma dependência na Lambda.** Cada mensagem passa a fazer duas escritas no DynamoDB além
  da chamada ao SES. Custo pequeno no volume do projeto.

## Decisões em aberto

Nenhuma.
