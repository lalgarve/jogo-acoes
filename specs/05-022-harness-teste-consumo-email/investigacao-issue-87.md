# Investigação — Issue #87: event source mapping nativo SQS→Lambda no LocalStack

**Issue:** [#87](https://github.com/lalgarve/jogo-acoes/issues/87)
**Branch:** `claude/jogo-acoes-iteracao-5-5hloak`
**Data:** 2026-09-30
**LocalStack:** `localstack/localstack:4` → versão real resolvida `4.14.0`, edição `community`
(confirmado via `GET /_localstack/health`)

## Pergunta a responder

A Issue #87 questionava uma premissa nunca verificada por trás do `EmailQueuePoller` (spec
05-021) e do harness rascunhado na spec 05-022: que, sem conta AWS real, não haveria como
rodar o mecanismo de disparo de produção de verdade (*event source mapping* SQS→Lambda) neste
ambiente, exigindo algo customizado no lugar dele.

**Resposta: a premissa era falsa.** O LocalStack (edição community, versão já fixada neste
projeto) suporta de verdade o *event source mapping* nativo SQS→Lambda. Verificado ponta a
ponta nesta sessão, com o artefato real do `email-lambda` — sem `EmailQueuePoller`, sem
`EmailSendHandler` reimplementado, sem `invoke` manual no caminho do teste final.

## Configuração necessária (não estava no `docker-compose.yml` antes desta investigação)

O `docker-compose.yml` atual habilita só `SERVICES: sqs,ses,s3`. Para o *event source mapping*
funcionar, faltam duas coisas:

```yaml
localstack:
  environment:
    SERVICES: sqs,ses,s3,lambda
  volumes:
    - ./docker/localstack/init:/etc/localstack/init/ready.d:ro
    - /var/run/docker.sock:/var/run/docker.sock   # LocalStack precisa criar um container
                                                    # sibling para rodar a função Lambda
                                                    # (executor "docker", o padrão da
                                                    # community edition)
```

Sem o `SERVICES` incluindo `lambda`: `CreateFunction` falha com `Service 'lambda' is not
enabled`. Sem o mount do socket do Docker: o LocalStack não consegue subir o container que
executa a função (não testado até a falha exata porque o mount já foi incluído de saída, mas é
um requisito documentado do executor "docker" do LocalStack).

**Essa mudança foi revertida ao final desta sessão** — habilitar `lambda` e montar o socket do
Docker do host dentro do container do LocalStack é uma decisão de implementação (Issue #84),
não desta investigação. Fica registrada aqui como a receita pronta para quem implementar.

## Passos executados

### 1. Build do artefato real do `email-lambda`

```bash
mvn -B -pl email-lambda -am package -DskipTests
```

Gera `email-lambda/target/function.zip` (14.4 MB) — o artefato de deploy padrão que a extensão
`quarkus-amazon-lambda` já produz sozinha (não foi preciso nenhum passo extra de empacotamento).
Também gera `target/sam.jvm.yaml`, que revela o handler e runtime corretos:

- Handler: `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`
- Runtime: `java21`

### 2. Deploy da função no LocalStack

```bash
docker cp email-lambda/target/function.zip jogoacoes-localstack-1:/tmp/function.zip

docker exec jogoacoes-localstack-1 awslocal lambda create-function \
  --function-name EmailLambda \
  --zip-file fileb:///tmp/function.zip \
  --handler "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest" \
  --runtime java21 \
  --role arn:aws:iam::000000000000:role/lambda-role \
  --timeout 15 \
  --memory-size 256 \
  --environment 'Variables={
    QUARKUS_SES_ENDPOINT_OVERRIDE=http://localhost.localstack.cloud:4566,
    QUARKUS_SES_AWS_REGION=us-east-1,
    QUARKUS_SES_AWS_CREDENTIALS_TYPE=static,
    QUARKUS_SES_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID=test,
    QUARKUS_SES_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY=test
  }'
```

Achado incidental: de dentro do container que o LocalStack cria para rodar a função, o
hostname especial `localhost.localstack.cloud` resolve de volta para o próprio LocalStack —
é o que permite ao `SesClient` do handler (configurado via `QUARKUS_SES_ENDPOINT_OVERRIDE`)
alcançar o SES emulado sem precisar saber o IP/hostname real do container do LocalStack.

Função demorou ~25s para sair de `Pending` para `Active` (LocalStack builda a imagem do
container de execução na primeira vez).

### 3. Invoke direto (checkpoint intermediário, antes do event source mapping)

```bash
awslocal lambda invoke --function-name EmailLambda --payload fileb:///tmp/payload.json response.json
```

Confirma que o handler roda e manda e-mail de verdade via SES emulado — `GET /_aws/ses` mostra
a mensagem com `Source: no-reply@jogo-acoes.example`, destinatário e corpo corretos. Prova que
o artefato do `email-lambda` funciona dentro do LocalStack antes de complicar com o event
source mapping.

### 4. Event source mapping real SQS→Lambda

```bash
QUEUE_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/jogo-acoes-email-commands \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

awslocal lambda create-event-source-mapping \
  --function-name EmailLambda \
  --event-source-arn "$QUEUE_ARN" \
  --batch-size 1
```

Mapeamento saiu de `Creating` para `Enabled` em poucos segundos.

### 5. Teste decisivo: `SendMessage` puro na fila, sem tocar na Lambda

```bash
awslocal sqs send-message \
  --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/jogo-acoes-email-commands \
  --message-body '{"recipientEmail":"success+esm-real-test@simulator.amazonses.com","subject":"Issue 87 - via event source mapping real","body":"<p>Enviado pelo pipeline real SQS->Lambda, sem invoke manual e sem EmailQueuePoller</p>","correlationId":"corr-issue-87-esm-real"}'
```

Essa é exatamente a forma como `app/`'s `SqsEmailSender` publica uma mensagem em produção —
nenhum código de teste no caminho.

## Resultado

Alguns segundos depois do `send-message`:

- **Fila esvaziada de verdade**: `ApproximateNumberOfMessages` e
  `ApproximateNumberOfMessagesNotVisible` os dois em `0` — a mensagem foi recebida, processada
  e deletada pelo próprio LocalStack via o event source mapping, sem nenhum consumidor
  customizado rodando.
- **E-mail chegou ao SES emulado**: `GET /_aws/ses` mostra a mensagem nova, com o assunto,
  destinatário e corpo exatos publicados no passo 5 — prova que foi o `EmailSendHandler` real
  (compilado dentro de `function.zip`) quem processou, não uma simulação.

```json
{
  "Id": "dxzzjqgowidalyqu-...",
  "Region": "us-east-1",
  "Destination": {"ToAddresses": ["success+esm-real-test@simulator.amazonses.com"]},
  "Source": "no-reply@jogo-acoes.example",
  "Subject": "Issue 87 - via event source mapping real",
  "Body": {"text_part": null, "html_part": "<p>Enviado pelo pipeline real SQS->Lambda, sem invoke manual e sem EmailQueuePoller</p>"},
  "Timestamp": "2026-09-30T09:42:26"
}
```

## Implicações

Confirma o cenário "se funcionar" já antecipado no texto da própria Issue #87:

1. **Issue #84** muda de escopo: em vez de "mover `EmailQueuePoller` para seu próprio módulo",
   passa a ser "remover `EmailQueuePoller` inteiramente" — o mecanismo de disparo real já existe
   via LocalStack, nada precisa substituí-lo, nem em dev nem (presumivelmente, a confirmar
   contra AWS real) em produção.
2. **Spec 05-022** (harness Spring Boot para testar o pipeline) encolhe drasticamente: a
   decisão em aberto 1 ("como o harness invoca o `email-lambda`") está resolvida — não é
   nenhuma das duas opções listadas lá (`@SqsListener` próprio nem chamada direta à API
   Lambda), é configurar o event source mapping real, uma vez, como infraestrutura. O que
   sobra a fazer, se algo sobrar, é só ler o que já existe (`GET /_aws/ses`, filas) depois do
   pipeline rodar sozinho — não uma aplicação nova.
3. **`docker-compose.yml`** vai precisar do `SERVICES` com `lambda` e do mount do
   `docker.sock` do host quando a Issue #84 for implementada (receita documentada acima).

## Fora do escopo desta investigação (fica para a Issue #84)

- Remover `EmailQueuePoller` de `email-lambda/`.
- Tornar permanente a mudança no `docker-compose.yml` (`SERVICES` + `docker.sock`) — revertida
  ao final desta sessão.
- Automatizar o deploy da função / event source mapping (hoje foi feito à mão, via `awslocal`,
  só para esta verificação) — se isso virar um script de init (mesmo padrão de
  `docker/localstack/init/*.sh`) é decisão de implementação da Issue #84.
- Verificar o comportamento equivalente contra AWS real (bloqueado por acesso a conta AWS,
  mesma pendência já registrada em `docs/context/iteracao-4.md`).

## Limpeza

A função `EmailLambda` e o event source mapping criados ficam apenas no estado em memória do
container `jogoacoes-localstack-1` desta sessão — não sobrevivem a `docker compose down` nem
foram persistidos em nenhum arquivo do repositório. `docker-compose.yml` foi revertido para o
estado commitado (`SERVICES: sqs,ses,s3`, sem o mount do `docker.sock`) ao final desta sessão.
