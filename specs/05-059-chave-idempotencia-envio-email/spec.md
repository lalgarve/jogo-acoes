# Spec: chave de idempotência no pedido de envio de e-mail

**Status:** rascunho
**Issue:** [#117](https://github.com/lalgarve/jogo-acoes/issues/117) (item 4)
**Iteração:** iteration-6

## Resumo

`POST /emails` do `email-service` passa a aceitar o header `Idempotency-Key`. Repetir o mesmo
pedido com a mesma chave devolve o mesmo envio e não cria outro. Com isso o `app` pode repetir
o pedido automaticamente quando o `email-service` não responde, como já faz com as outras
operações.

## Motivação

`POST /emails` é a única operação do contrato entre `app` e `email-service` que não é
idempotente (`specs/05-034-fronteira-sincrona-app-email-service/contracts/feign-client.md`,
"Operações usadas"). Cada chamada grava uma linha nova em `email_send`
(`EmailSendService.send`) e, depois, um e-mail.

Por isso o `EmailServiceGateway.sendEmail` nunca repete o pedido: se a resposta `202` se perde
(tempo limite de leitura de 5 s, conexão cortada), repetir poderia mandar o e-mail duas vezes.
A consequência é que uma falha passageira de rede vira `503` para quem pediu o convite ou o
link, mesmo quando o `email-service` já aceitou o envio. A constitution ("Fronteira entre
módulos e serviços", "Repetição de chamadas síncronas") pede justamente uma chave de
idempotência para que uma operação assim possa ser repetida.

A 05-034 deixou isso para a Etapa 4 da disciplina, que já terminou sem incluir este item.

## Cenários (comportamento esperado)

Não há cenário de usuário novo: quem usa o sistema só percebe menos `503`. O comportamento é
verificado por testes de integração dos dois lados, contra o `email-service` real
(Testcontainers), como os testes do cliente da 05-034:

- Lado `email-service`: dois `POST /emails` com a mesma chave e o mesmo corpo criam uma linha em
  `email_send` e respondem o mesmo `id`; a mesma chave com outro corpo é recusada.
- Lado `app`: quando a primeira resposta se perde, o `EmailServiceGateway` repete com a mesma
  chave e o destinatário recebe um e-mail só.

## Requisitos funcionais

- `POST /emails` exige o header `Idempotency-Key` (texto até 255 caracteres). O contrato em
  `docs/openapi-email-service.yaml` muda de forma incompatível, sem manter a forma antiga: o
  sistema está em pré-produção (constitution, "Status do sistema: pré-produção").
- A chave vale por cliente (`X-API-Key`): dois clientes podem usar a mesma chave sem conflito.
- Mesmo cliente, mesma chave e mesmo corpo: responde `202` com o `id` do envio já gravado, sem
  gravar outro e sem publicar outra mensagem na fila. Vale também se o primeiro pedido ainda
  estiver sendo processado: o segundo espera o primeiro terminar e responde com o mesmo `id` (ou
  com o erro do primeiro, se ele falhar).
- Mesmo cliente, mesma chave e corpo diferente: responde `422` com corpo `Error`, sem gravar
  nada.
- Um pedido recusado (`400`, `422` de domínio, `404` de template) não guarda a chave: repetir
  com a mesma chave depois de corrigir o problema é um pedido novo.
- Sem o header, `POST /emails` responde `400` com corpo `Error`, sem gravar nada.
- O `app` gera uma chave por e-mail que quer mandar, manda em todas as tentativas desse e-mail e
  passa a repetir `POST /emails` com a mesma política das outras operações (até 3 tentativas,
  200 ms, só quando o `email-service` não foi alcançado ou respondeu `5xx`).
- A tabela "Operações usadas" de `contracts/feign-client.md` (05-034) e o Javadoc do
  `EmailServiceGateway` passam a dizer que `POST /emails` é idempotente com a chave.

## Requisitos não-funcionais

- A chave fica guardada pelo menos 24 horas, o mesmo prazo em que o `email-service` tenta
  publicar um envio (spec 05-046).
- A unicidade de (cliente, chave) é garantida no banco do `email-service`, no schema dele
  (spec 05-032), para que dois pedidos simultâneos com a mesma chave não gravem dois envios.

## Fora de escopo

- Mensagem publicada duas vezes na fila e entrega duplicada pelo SQS (spec 05-060).
- Repetição pelo próprio usuário (clicar de novo em "convidar" ou "pedir link"): cada clique é
  um pedido novo no `app` e ganha chave nova. Isso é tratado nas specs 05-057 e 05-058.
- Reenvio manual de um envio `FAILED`.

## Decisões em aberto

Nenhuma de requisito. Onde a chave fica guardada (coluna nova em `email_send` ou tabela própria)
e por quanto tempo, além das 24 horas mínimas, são decisões técnicas do `plan.md`. Pela
pré-produção, a coluna ou tabela nova pode nascer `NOT NULL`, sem tratar envios já gravados.
