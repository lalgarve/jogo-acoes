# Spec: Publicação na fila de envio em segundo plano

**Status:** rascunho
**Issue:** a criar (relacionada à [#144](https://github.com/lalgarve/jogo-acoes/issues/144))
**Iteração:** iteration-5

## Resumo

`POST /emails` deixa de publicar na fila SQS dentro da chamada. O `email-service` faz as
verificações básicas, grava o envio como pendente e responde. Uma thread do próprio serviço
publica os envios pendentes na fila, muda o status depois de publicar, tenta de novo quando a
publicação falha e decide quando desistir. A entrega continua igual: a `email-lambda` consome a
fila e envia pelo SES.

## Motivação

Hoje (spec [05-031](../05-031-servico-email-envio/spec.md)) o `POST /emails` grava `email_send` e
publica na fila na mesma transação. Se a fila não responde, o pedido volta `503` e nada é
gravado. O `jogo-acoes` chama o `email-service` de forma síncrona e não repete o `POST /emails`
(spec 05-034, sem idempotência ainda), então uma fila fora do ar vira um e-mail de login ou de
convite perdido para o jogador.

Decisão da Leila (2026-10-14), ao revisar a spec 05-044: o `email-service` tem que subir e aceitar
envios mesmo sem o SES, e uma falha de conexão não pode virar erro na chamada de envio. A lógica de
tentar de novo e de desistir fica dentro do serviço.

## Cenários (comportamento esperado)

- Contrato: [`docs/openapi-email-service.yaml`](../../docs/openapi-email-service.yaml), rota
  `POST /emails`.
- `.feature`: `email-service/src/test/resources/features/send_email.feature`. Muda antes do
  código (ver `tasks.md`): o envio aceito responde `PENDING` e chega a `QUEUED` quando a fila
  recebe a mensagem; o cenário "The send queue is unavailable" deixa de esperar `503` e passa a
  esperar `PENDING`, e a mensagem na fila quando ela volta; cenário novo de desistência.

## Requisitos funcionais

- **Verificações básicas na chamada**, como hoje: `X-API-Key` (`401`), pedido malformado (`400`),
  template inexistente ou de outro cliente (`404`), remetente não configurado (`409`). Nenhuma
  delas consulta a fila nem o SES.
- **Pedido aceito:** o serviço grava o envio com status `PENDING` e tudo o que a publicação vai
  precisar (remetente, destinatário, nome do template no SES, `templateData`), e responde `202`
  com `{ id, status: "PENDING" }`. O `202` passa a significar "aceito e gravado".
- **A chamada não falha por causa da fila nem do SES.** O `503` de "fila indisponível" sai do
  contrato de `POST /emails`.
- **Publicação em segundo plano:** uma thread do `email-service` pega os envios `PENDING` cuja
  próxima tentativa já venceu, publica cada um na fila de envio com a mesma mensagem de hoje
  (`correlationId` = `id`) e muda o status para `QUEUED`, com a hora da publicação.
- **Falha na publicação:** o envio continua `PENDING`, conta mais uma tentativa, guarda o motivo e
  ganha uma próxima tentativa mais tarde (espera crescente).
- **Desistência:** depois do limite de tentativas (ver "Decisões em aberto", item 2), o envio
  passa para `FAILED`, com o último motivo, e não é mais tentado. O serviço registra um `ERROR`
  no log com o `id`.
- **`templateData` só fica gravado enquanto precisa:** é apagado quando o envio vira `QUEUED` ou
  `FAILED` (pode ter dado pessoal; a 05-031 não gravava).
- **Várias instâncias:** duas instâncias do `email-service` não publicam o mesmo envio ao mesmo
  tempo.
- **Pelo menos uma vez:** se a publicação der certo e a mudança para `QUEUED` falhar, o envio pode
  ser publicado de novo. Aceito nesta spec; e-mails repetidos ficam para outra spec.
- **Template ausente no SES:** ver "Decisões em aberto", item 1.
- O `jogo-acoes` continua chamando `POST /emails` de forma síncrona e não muda: ele lê só o `id`
  da resposta.

## Requisitos não-funcionais

- A resposta do `POST /emails` depende só do banco do `email-service`.
- Sem infraestrutura nova: a fila de trabalho é a própria tabela `email_send`, no PostgreSQL que o
  serviço já usa.
- Testes contra PostgreSQL real e LocalStack, sem mock (constitution, "Testes").

## Fora de escopo

- Identificar pedidos repetidos e não enviar duas vezes (outra spec, decisão da Leila
  2026-10-14). Inclui a entrega repetida pela fila, que já existia.
- Consulta do estado de um envio (`GET /emails/{id}`) e os eventos de entrega do SES (Iteração 6).
- Mudanças na `email-lambda` e na mensagem da fila.
- Reenviar um envio `FAILED` (manual ou por API).

## Decisões resolvidas

- **Spec própria** (Leila, 2026-10-14): separada da 05-044, que trata só dos templates perdidos
  no SES.
- **A publicação na fila sai da chamada** e fica numa thread do serviço, com status, novas
  tentativas e desistência dentro do serviço (Leila, 2026-10-14).

## Decisões em aberto

Cada uma tem uma recomendação, já usada nos requisitos e no `plan.md`; falta a confirmação da
Leila.

1. **A thread de publicação confere o template no SES antes de pôr na fila?** Recomendação: sim.
   Ela cria o template a partir do banco quando o SES não o tem (mesma operação da 05-044, que não
   sobrescreve) e, se o SES não responder, conta como falha de publicação e tenta de novo. Sem
   isso, um template perdido no SES faz a `email-lambda` falhar com o e-mail já na fila. Custo: uma
   chamada ao SES por e-mail, fora da resposta ao cliente.
2. **Quando desistir.** Recomendação: 24 horas depois do pedido, com espera de 1 s dobrando até
   5 min entre tentativas (cerca de 290 tentativas). Um e-mail de login ou convite que sai um dia
   depois já não serve para quem pediu. Valores configuráveis.
3. **Status na resposta.** Recomendação: `202` com `status: "PENDING"`, e o enum do contrato passa
   a ter `PENDING`, `QUEUED` e `FAILED` (os dois últimos só aparecem quando existir a consulta do
   envio).
