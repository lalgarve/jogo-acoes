# Spec: e-mail entregue duas vezes pela fila é mandado uma vez só

**Status:** rascunho
**Issue:** [#117](https://github.com/lalgarve/jogo-acoes/issues/117) (item 4)
**Iteração:** iteration-6

## Resumo

Quando a mesma mensagem chega duas vezes ao `email-lambda`, o destinatário recebe um e-mail só.
Uma nova tentativa que o `email-service` decidiu fazer depois de uma falha continua mandando o
e-mail normalmente.

## Motivação

A fila de envio é uma fila SQS padrão, não FIFO. Ela garante entrega pelo menos uma vez, e a
mesma mensagem pode chegar mais de uma vez. Hoje o `EmailSendHandler` chama o SES para cada
mensagem que recebe, sem conferir se já mandou aquela. Há três caminhos para a duplicata:

1. **O SQS entrega de novo** uma mensagem já processada (é permitido pelo serviço).
2. **A invocação falha depois do envio.** O handler percorre os registros do lote e, se um
   registro lança exceção (mensagem malformada, DLQ fora do ar, erro do SES não classificado), a
   invocação falha e o SQS reentrega o lote inteiro, inclusive os registros já mandados. No
   LocalStack o lote é de 1 (`docker/localstack/init/03-deploy-email-lambda.sh`), mas nada no
   código depende disso.
3. **O `email-service` publica duas vezes.** O `EmailQueuePublisher` publica dentro da
   transação que marca o envio como `QUEUED`; se a publicação dá certo e o commit falha, a mesma
   mensagem é publicada de novo na próxima rodada. O Javadoc da classe registra isso como
   aceito, "duplicates are another spec's".

A Decisão 8 da Iteração 4 (`docs/context/iteracao-4.md`) já decidiu que a checagem é do
consumidor e não pode ler o banco do `app` nem do `email-service`: uma tabela DynamoDB com chave
`correlationId` e escrita condicional antes de chamar o SES. Ela nunca foi implementada.

Essa decisão não considerava o que veio depois na spec 05-046: quando o SES está fora do ar, o
handler manda a mensagem para a DLQ com `SES_UNAVAILABLE` e o `email-service` publica de novo,
com o mesmo `correlationId` (o `id` do envio). Uma checagem só por `correlationId`, gravada
antes do SES, trataria essa nova tentativa como duplicata e o e-mail nunca sairia.

## Cenários (comportamento esperado)

Não há cenário de usuário. O comportamento é verificado por testes de integração do
`email-lambda` contra LocalStack (SQS, SES e DynamoDB reais do LocalStack, sem mock), e por um
teste de ponta a ponta na suíte `blackbox-tests/`:

- A mesma mensagem processada duas vezes gera um envio no SES.
- Um lote em que o segundo registro falha, reentregue, não manda de novo o primeiro.
- Uma mensagem que voltou para a DLQ com `SES_UNAVAILABLE` e foi publicada de novo pelo
  `email-service` é mandada quando o SES volta.

## Requisitos funcionais

- Cada envio (`correlationId`) gera no máximo uma chamada bem-sucedida ao SES
  (`SendTemplatedEmail`), qualquer que seja o número de vezes que a mensagem chegue.
- Uma tentativa que não chegou a ser aceita pelo SES (`TEMPLATE_MISSING`, `SES_REJECTED`,
  `SES_UNAVAILABLE`, exceção antes da chamada) não conta como envio: a próxima entrega da mesma
  mensagem tenta de novo.
- Uma mensagem reconhecida como duplicata é descartada com sucesso (não falha a invocação, não
  vai para a DLQ) e gera uma linha de log com o `correlationId`, sem o endereço de e-mail
  completo nem os dados do template.
- O registro de "já mandado" fica guardado pelo menos o tempo em que a mesma mensagem ainda pode
  chegar: retenção da fila mais as 24 horas de tentativas do `email-service` (spec 05-046).
- O `email-lambda` continua sem acesso ao banco do `app` e do `email-service` (Decisão 1 da
  Iteração 4; constitution, "Fronteira entre módulos e serviços").
- A falha de um registro do lote não faz reentregar os registros que já deram certo (ver
  "Decisões em aberto").

## Requisitos não-funcionais

- A tabela de controle e a permissão do Lambda para usá-la entram na mesma configuração que já
  cria a fila e o Lambda no LocalStack (`docker/localstack/init/`), e no Terraform quando houver
  conta AWS real.
- Se a tabela de controle estiver fora do ar, o handler não manda o e-mail sem checar: falha a
  invocação e deixa o SQS reentregar.

## Fora de escopo

- Chave de idempotência em `POST /emails` (spec 05-059).
- Bounce, complaint e eventos do SES (pipeline de eventos, a especificar).
- Garantia de "exatamente uma vez" contra uma falha entre o SES aceitar e o registro ser
  gravado: ver "Decisões em aberto".

## Decisões em aberto

- **Quando gravar o registro**: antes do SES (escrita condicional "em andamento", apagada se o SES
  recusar) ou depois do SES aceitar? Antes evita duas chamadas simultâneas ao SES, mas exige
  limpar o registro em cada falha; depois é mais simples, mas um Lambda que cai entre o SES
  aceitar e a gravação manda o e-mail de novo na reentrega. A Decisão 8 escolheu "antes"; a
  nova tentativa da 05-046 obriga a revisar.
- **Chave do registro**: só `correlationId` (com a regra acima de só gravar envio aceito) ou
  `correlationId` mais um número de tentativa vindo do `email-service`? A segunda opção acrescenta
  um campo à mensagem da fila (`EmailQueueMessage`). Pela pré-produção, a mensagem pode mudar sem
  manter o formato antigo funcionando (constitution, "Status do sistema: pré-produção"), então o
  custo dessa opção é só o código dos dois lados.
- **Falha parcial do lote**: usar a resposta de falha parcial do SQS (`ReportBatchItemFailures`)
  para reentregar só os registros que falharam, ou fixar o lote em 1 também na AWS? As duas
  reduzem o caminho 2 da "Motivação"; a checagem de duplicata continua necessária para os
  caminhos 1 e 3.
- **DynamoDB ou outro armazenamento**: a Decisão 8 escolheu DynamoDB com TTL. Confirmar, já que a
  conta AWS real ainda não existe e tudo roda no LocalStack.
