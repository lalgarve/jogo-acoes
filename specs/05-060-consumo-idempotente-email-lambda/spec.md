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
- Num lote em que o segundo registro falha, só o segundo é reentregue, e o primeiro não é
  mandado de novo.
- Uma mensagem que voltou para a DLQ com `SES_UNAVAILABLE` e foi publicada de novo pelo
  `email-service` é mandada quando o SES volta.

## Requisitos funcionais

Decidido em 2026-10-25: o registro é gravado **antes** de chamar o SES, a chave inclui o **número
da tentativa**, e o lote usa **falha parcial**.

**Mensagem da fila**

- A mensagem (`EmailQueueMessage` no `email-service`, `EmailMessage` no `email-lambda`) ganha o
  campo obrigatório `attempt`, um inteiro a partir de 1. O `email-service` só muda esse número
  quando decide tentar o envio de novo (depois de uma falha, spec 05-046); publicar de novo a
  mesma tentativa, como no caminho 3 da "Motivação", repete o mesmo número.
- O contrato da mensagem muda sem manter o formato antigo (constitution, "Status do sistema:
  pré-produção"). O documento do contrato da 05-031/05-046 é atualizado.

**Checagem de duplicata**

- A chave do registro é (`correlationId`, `attempt`).
- Antes de chamar o SES, o handler grava o registro com uma escrita condicional ("só se não
  existir"). Se a chave já existe, a mensagem é duplicata: é descartada com sucesso (não falha o
  registro, não vai para a DLQ) e gera uma linha de log com `correlationId` e `attempt`, sem o
  endereço de e-mail completo nem os dados do template.
- Cada (`correlationId`, `attempt`) gera no máximo uma chamada ao SES, qualquer que seja o número
  de vezes que a mensagem chegue.
- Quando o SES recusa ou está fora do ar (`TEMPLATE_MISSING`, `SES_REJECTED`, `SES_UNAVAILABLE`),
  a mensagem vai para a DLQ com o tipo do erro, como hoje, e o registro fica. Se o
  `email-service` decidir tentar de novo, a nova mensagem tem outro `attempt` e passa pela
  checagem.
- Quando o handler falha antes de o SES aceitar a mensagem, por um erro que não é classificado
  (mensagem malformada, erro do SES não classificado, DLQ fora do ar), ele apaga o registro antes
  de devolver a falha, para que a reentrega do SQS tente de novo.
- Se a escrita do registro falha (armazenamento fora do ar), o handler não chama o SES: o
  registro falha e o SQS reentrega.
- O registro fica guardado pelo menos o tempo em que a mesma mensagem ainda pode chegar:
  retenção da fila mais as 24 horas de tentativas do `email-service` (spec 05-046).
- O `email-lambda` continua sem acesso ao banco do `app` e do `email-service` (Decisão 1 da
  Iteração 4; constitution, "Fronteira entre módulos e serviços").

**Lote**

- O handler devolve a resposta de falha parcial do SQS (`ReportBatchItemFailures`): um registro
  que falha é reentregue sozinho, e os que deram certo no mesmo lote não voltam.
- O mapeamento da fila para o Lambda passa a declarar esse tipo de resposta, no LocalStack
  (`docker/localstack/init/03-deploy-email-lambda.sh`) e no Terraform quando houver conta AWS
  real.

## Requisitos não-funcionais

- O armazenamento do registro é a tabela DynamoDB com TTL da Decisão 8 da Iteração 4, criada pela
  mesma configuração que já cria a fila e o Lambda no LocalStack (`docker/localstack/init/`), com
  a permissão do Lambda para usá-la.
- Os testes rodam contra SQS, SES e DynamoDB do LocalStack, sem mock (constitution, "Testes").

## Fora de escopo

- Chave de idempotência em `POST /emails` (spec 05-059).
- Bounce, complaint e eventos do SES (pipeline de eventos, a especificar).

## Decisões em aberto

- **Lambda interrompido entre gravar o registro e o SES responder** (tempo limite estourado,
  processo morto): a reentrega encontra o registro e trata a mensagem como duplicata. Se o SES não
  chegou a aceitar, esse e-mail não sai e ninguém fica sabendo. Aceitar esse risco (é raro), ou o
  registro guardar "em andamento" e, na reentrega, mandar a mensagem para a DLQ com um tipo de
  erro novo para o `email-service` decidir? A segunda opção troca o e-mail perdido por um
  possível e-mail duplicado.
