# Spec: Publicação na fila de envio em segundo plano

**Status:** rascunho
**Issue:** [#185](https://github.com/lalgarve/jogo-acoes/issues/185) (relacionada à [#144](https://github.com/lalgarve/jogo-acoes/issues/144))
**Iteração:** iteration-5

## Resumo

`POST /emails` deixa de publicar na fila SQS dentro da chamada. O `email-service` faz as
verificações básicas, grava o envio como pendente e responde. Uma thread do próprio serviço
publica os envios pendentes na fila e muda o status depois de publicar. Quando a `email-lambda`
não consegue entregar, a mensagem vai para uma fila de erros (DLQ) com o tipo de erro; o
`email-service` lê essa fila, grava o tipo de erro e o número de tentativas no banco e decide se
tenta de novo ou desiste. Se o erro for template ausente no SES, ele recria o template antes de
tentar de novo.

## Motivação

Hoje (spec [05-031](../05-031-servico-email-envio/spec.md)) o `POST /emails` grava `email_send` e
publica na fila na mesma transação. Se a fila não responde, o pedido volta `503` e nada é
gravado. O `jogo-acoes` chama o `email-service` de forma síncrona e não repete o `POST /emails`
(spec 05-034, sem idempotência ainda), então uma fila fora do ar vira um e-mail de login ou de
convite perdido para o jogador.

Depois que a mensagem está na fila, uma falha na `email-lambda` (template ausente no SES, como na
Issue #144, ou SES fora do ar) hoje só gera novas entregas do SQS e não deixa registro no
`email-service`.

Decisões da Leila (2026-10-14), ao revisar a spec 05-044:
- o `email-service` sobe e aceita envios mesmo sem o SES, e falha de conexão não vira erro na
  chamada de envio;
- a lógica de tentar de novo e de desistir fica dentro do serviço;
- o normal é o template existir, então o envio não consulta o SES antes de publicar. O erro de
  template chega por uma DLQ e só então o template é recriado;
- o serviço guarda os templates recriados na última hora, para que uma rajada de erros do mesmo
  template (os convites de uma competição privada, por exemplo) não gere uma consulta ao SES por
  e-mail;
- o tipo de erro e o número de tentativas ficam gravados no banco.

## Cenários (comportamento esperado)

- Contrato HTTP: [`docs/openapi-email-service.yaml`](../../docs/openapi-email-service.yaml), rota
  `POST /emails`.
- Contrato da DLQ (mensagem da `email-lambda` para o `email-service`): `contracts/dlq-message.md`,
  escrito junto com a implementação (ver `tasks.md`).
- `.feature`: `email-service/src/test/resources/features/send_email.feature`. Muda antes do
  código (ver `tasks.md`): o envio aceito responde `PENDING` e chega a `QUEUED`; o cenário "The
  send queue is unavailable" deixa de esperar `503`; cenários novos para os erros que chegam pela
  DLQ e para a desistência.

## Requisitos funcionais

### Pedido

- **Verificações básicas na chamada**, como hoje: `X-API-Key` (`401`), pedido malformado (`400`),
  template inexistente no banco ou de outro cliente (`404`), remetente não configurado (`409`).
  Nenhuma delas consulta a fila nem o SES.
- **Pedido aceito:** o serviço grava o envio com status `PENDING` e tudo o que a publicação vai
  precisar (remetente, destinatário, nome do template no SES, `templateData`), e responde `202`
  com `{ id, status: "PENDING" }`. O `202` passa a significar "aceito e gravado".
- **A chamada não falha por causa da fila nem do SES.** O `503` de "fila indisponível" sai do
  contrato de `POST /emails`.
- O `jogo-acoes` continua chamando `POST /emails` de forma síncrona e não muda: ele lê só o `id`
  da resposta.

### Publicação

- Uma thread do `email-service` pega os envios `PENDING` cuja próxima tentativa já venceu,
  publica cada um na fila de envio com a mesma mensagem de hoje (`correlationId` = `id`) e muda o
  status para `QUEUED`, com a hora da publicação.
- A publicação não consulta o SES.
- Se a publicação falhar, o envio continua `PENDING`, com o tipo de erro `QUEUE_UNAVAILABLE`, uma
  tentativa a mais e uma próxima tentativa mais tarde (espera crescente).
- Duas instâncias do `email-service` não publicam o mesmo envio ao mesmo tempo.

### Erros na entrega (DLQ)

- A fila de envio ganha uma DLQ.
- Quando o SES recusa o envio, a `email-lambda` põe a mensagem na DLQ com o tipo de erro e a
  mensagem do SES, e não lança exceção (a mensagem sai da fila de envio). Tipos:
  - `TEMPLATE_MISSING`: o SES não tem o template;
  - `SES_REJECTED`: o SES recusou o e-mail (ex.: endereço recusado), sem chance de dar certo
    repetindo;
  - `SES_UNAVAILABLE`: SES fora do ar, limite de envio, erro 5xx.
- Uma mensagem malformada ou um erro que a Lambda não reconhece faz a Lambda lançar exceção, como
  hoje. Depois das novas entregas do SQS (`maxReceiveCount`), a mensagem cai na DLQ sem tipo, e o
  `email-service` grava `UNKNOWN`.
- O `email-service` lê a DLQ e, para cada mensagem, acha o envio pelo `correlationId`, grava o
  tipo de erro, a mensagem e uma tentativa a mais, e decide:
  - `TEMPLATE_MISSING`: se o template não foi recriado na última hora, recria no SES a partir do
    banco (mesma operação da 05-044, que não sobrescreve) e grava a hora da recriação. Se já foi,
    não chama o SES. Nos dois casos, o envio volta para `PENDING` e é publicado de novo logo.
  - `SES_UNAVAILABLE` e `UNKNOWN`: volta para `PENDING`, com espera crescente.
  - `SES_REJECTED`: `FAILED`, sem nova tentativa.
- Para voltar para `PENDING`, o serviço grava de novo o `templateData` que veio na mensagem da
  DLQ.

### Desistência

- Um envio que ainda não deu certo 24 horas depois do pedido passa para `FAILED` (ver "Decisões em
  aberto", item 1). O serviço registra um `ERROR` no log com o `id` e o tipo de erro.
- O `templateData` só fica gravado enquanto o envio está `PENDING`; é apagado quando ele vira
  `QUEUED` ou `FAILED` (pode ter dado pessoal; a 05-031 não gravava).

### O que fica gravado

- Em cada envio: status, número de tentativas, tipo do último erro, mensagem do último erro, hora
  da próxima tentativa, hora da publicação.
- Em cada template: hora da última recriação no SES.

### Pelo menos uma vez

- Se a publicação der certo e a mudança para `QUEUED` falhar, o envio pode ser publicado de novo.
  Aceito nesta spec; e-mails repetidos ficam para outra spec.

## Requisitos não-funcionais

- A resposta do `POST /emails` depende só do banco do `email-service`.
- Uma rajada de N erros `TEMPLATE_MISSING` do mesmo template na mesma hora gera uma chamada ao SES
  (duas instâncias tratando ao mesmo tempo podem fazer uma cada; a recriação não sobrescreve).
- A fila de trabalho é a própria tabela `email_send`, no PostgreSQL que o serviço já usa.
- Testes contra PostgreSQL real e LocalStack, sem mock (constitution, "Testes").

## Fora de escopo

- Identificar pedidos repetidos e não enviar duas vezes (outra spec, decisão da Leila
  2026-10-14). Inclui a entrega repetida pela fila, que já existia.
- Consulta do estado de um envio (`GET /emails/{id}`), painel e cancelamento de tentativas.
- Os eventos de entrega do SES (`Delivery`/`Bounce`/`Complaint`, Iteração 6). Um envio `QUEUED`
  que a Lambda entregou continua `QUEUED`.
- Reenviar um envio `FAILED` (manual ou por API).

## Decisões resolvidas

- **Spec própria** (Leila, 2026-10-14): separada da 05-044, que trata só dos templates perdidos
  no SES.
- **A publicação na fila sai da chamada** e fica numa thread do serviço, com status, novas
  tentativas e desistência dentro do serviço (Leila, 2026-10-14). Substitui o desenho da decisão
  4 de `docs/context/iteracao-4.md` (estado de retry no DynamoDB, mantido pela Lambda), que não
  foi implementado.
- **Template ausente chega pela DLQ**, sem consulta ao SES antes de publicar; templates recriados
  na última hora não são recriados de novo (Leila, 2026-10-14).
- **Tipo de erro e número de tentativas gravados no banco** (Leila, 2026-10-14).

## Decisões em aberto

Cada uma tem uma recomendação, já usada nos requisitos e no `plan.md`; falta a confirmação da
Leila.

1. **Quando desistir.** Recomendação: 24 horas depois do pedido, com espera de 1 s dobrando até
   5 min entre tentativas. Um e-mail de login ou convite que sai um dia depois já não serve para
   quem pediu. Valores configuráveis.
2. **Quem classifica o erro.** Recomendação: a `email-lambda`, que é quem recebe a resposta do
   SES, põe a mensagem na DLQ com o tipo. A redrive do SQS sozinha não diz o motivo da falha.
3. **Onde fica a "lista dos templates recriados na última hora".** Recomendação: uma coluna
   `ses_recreated_at` em `email_template`, e não uma lista em memória: vale para todas as
   instâncias e sobrevive a um reinício.
4. **Status na resposta.** Recomendação: `202` com `status: "PENDING"`, e o enum do contrato passa
   a ter `PENDING`, `QUEUED` e `FAILED` (os dois últimos só aparecem quando existir a consulta do
   envio).
