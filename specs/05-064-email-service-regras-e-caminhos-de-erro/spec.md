# Spec: email-service, regras de arquitetura e caminhos de erro que faltam

**Status:** rascunho
**Issue:** [#136](https://github.com/lalgarve/jogo-acoes/issues/136)
**Iteração:** iteration-5

## Resumo

Fecha o que ainda falta da [#136](https://github.com/lalgarve/jogo-acoes/issues/136) no
`email-service`:
- duas regras de arquitetura: repository só usado dentro do próprio pacote, e AWS só nas classes
  donas da integração;
- a conferência de que toda exceção e todo ramo de erro têm teste;
- um teste que compara os status de erro do código com o contrato OpenAPI;
- limite de tempo nas chamadas ao SES e ao SQS.

## Motivação

A #136 (aberta em 2026-10-07) pedia para o `email-service` as mesmas regras estruturais e a mesma
verificação de caminhos de erro que a spec
[05-034](../05-034-fronteira-sincrona-app-email-service/spec.md) deu ao `app`. A spec própria
nunca foi escrita, mas parte do escopo foi entregue por outras specs. Esta spec cobre só o resto.

**Situação atual** (conferida em 2026-11-16 no código de `origin/development`):

| Item da #136 | Situação |
|---|---|
| Nenhuma dependência das classes do `app` | Feito (05-034): regra no `ArchitectureTest` e `bannedDependencies` |
| Controller nunca usa repository; camadas | Feito (05-063): quatro regras de camada no `ArchitectureTest` |
| Sem ciclo entre os pacotes de domínio | Feito (05-061): `modulesHaveNoDependencyCycles` |
| SQS fora do ar em `POST /emails` | Mudou de forma com a [05-046](../05-046-publicacao-na-fila-em-segundo-plano/spec.md): a publicação saiu da requisição e roda em segundo plano, com nova tentativa (`QUEUE_UNAVAILABLE`, coberta em `send_email.feature`). O `POST /emails` não fala mais com o SQS |
| SES fora do ar em `POST`/`PUT /templates` e no preview | Feito: `SesUnavailableIntegrationTest` (`503`, nada gravado pela metade, conteúdo anterior mantido) |
| SES rejeitando template | Feito: cenários de `register_templates.feature` (`422` com o motivo do SES) |
| **Repository só usado dentro do próprio pacote** | **Falta.** Hoje não há violação (os sete repositories só são usados no próprio pacote), mas nenhuma regra impede |
| **AWS só nas classes donas da integração** | **Falta.** Hoje só `SesTemplateClient` (`template`), `EmailQueuePublisher` e `DeadLetterConsumer` (`send`) usam AWS, mas nenhuma regra impede |
| **Toda exceção e todo ramo de erro com teste** | **Falta conferir.** São 13 classes de exceção (a lista da #136 está desatualizada: a `EmailQueuePublishException` não existe mais, e entraram as de domínios descartáveis e de verificação de destinatário). Os testes HTTP cobrem `400`, `404`, `409`, `422` e `503`, mas ninguém conferiu classe por classe e ramo por ramo |
| **Status de erro do código iguais aos do contrato** | **Falta.** Não há teste. Exemplo de divergência já existente: `POST /templates` devolve `400` para nome em branco (cenário de `register_templates.feature`), e o contrato dessa operação não declara `400` |
| **Tempo limite dos clientes AWS** | **Falta, em parte.** O cliente do SES é o do autoconfigure do Spring Cloud AWS, sem tempo limite configurado: uma chamada presa segura a requisição. O publicador do SQS já desiste de esperar depois de 10 s (`SQS_TIMEOUT_SECONDS` no `EmailQueuePublisher`, com `Future.get`). Mas o valor é fixo no código, não está documentado no `application.yml`, e a chamada do SDK continua rodando depois que o publicador desiste |

## Cenários (comportamento esperado)

Esta spec não muda comportamento visto por quem usa a API. Por isso, não tem `.feature` novo. Os
testes são de arquitetura (ArchUnit), de contrato e de integração. Se a conferência das exceções
achar um ramo que só se vê pela API, o cenário dele vai para o `.feature` do pacote
(`register_templates.feature`, `send_email.feature` ou `disposable_domains.feature`).

## Requisitos funcionais

1. **Repository só dentro do próprio pacote.** Uma regra no `ArchitectureTest` do `email-service`
   impede que uma classe use um repository de outro pacote de domínio (`auth`, `disposable`,
   `domain`, `send`, `template`). A regra é a mesma da spec
   [05-029](../05-029-isolamento-repositorios-entre-modulos/spec.md) no `app`. Quem precisa de dado
   de outro pacote chama o service dele.
2. **AWS só nas classes donas da integração.** Uma regra no `ArchitectureTest` impede que outra
   classe use o AWS SDK (`software.amazon.awssdk..`) ou o Spring Cloud AWS (`io.awspring..`).
   As donas são, hoje, `SesTemplateClient`, `EmailQueuePublisher` e `DeadLetterConsumer`. Uma
   classe nova que precise de AWS entra na lista por decisão registrada no `plan.md`, não por
   ajuste silencioso da regra.
3. **Exceções e ramos de erro.** Um inventário no `tasks.md` aponta, para cada uma das 13
   classes de exceção e para cada ramo de erro, o teste que o exercita pelo caminho real do
   código. Os ramos de erro são:
   - cada `@ExceptionHandler` do `ApiExceptionHandler`;
   - os `catch` do `SesTemplateClient`, do `EmailQueuePublisher` e do `DeadLetterConsumer`;
   - os erros dos tasklets de `disposable`.

   Cada ramo que responde por HTTP é testado por HTTP, conferindo o status e o corpo `Error` do
   contrato. O que faltar ganha teste. O que for difícil demais de provocar é justificado no
   `plan.md` (constitution, "Critério de aceite: exceções e caminhos de erro exercitados por
   teste").
4. **Status de erro iguais aos do contrato.** Um teste automático compara, operação por operação,
   os status que o código pode devolver com os declarados em `docs/openapi-email-service.yaml`.
   Ele falha se um status devolvido não está no contrato, ou se um status declarado não é
   produzido por nenhum teste.
5. **Tempo limite nas chamadas ao SES e ao SQS.** Toda chamada ao SES e toda publicação no SQS
   têm um tempo limite configurado e documentado no `application.yml`, e a chamada é encerrada
   quando o limite estoura. A leitura da fila de devolução pelo `DeadLetterConsumer` fica de
   fora: ela espera mensagens de propósito (*long polling*). Um SES que
   não responde dentro do limite resulta no mesmo `503` de "SES fora do ar". Um SQS que não
   responde dentro do limite resulta na mesma nova tentativa de "fila fora do ar"
   (`QUEUE_UNAVAILABLE`).

## Requisitos não-funcionais

- **Prova de cada regra** (requisitos 1, 2 e 4), como pede a #136. Uma regra sem violação hoje
  nasce verde, e a prova é plantar uma violação temporária, ver o build falhar com a mensagem
  certa e reverter sem commitar. Uma regra com violação hoje nasce vermelha, listando as violações
  encontradas. O resultado vai para o `Registro` do `tasks.md`.
- **Fase vermelha** (constitution, "Fase vermelha: testes antes do código, com prova"). O teste do
  requisito 4 e os testes novos dos requisitos 3 e 5 são commitados antes de qualquer mudança de
  código ou de contrato, com a saída da falha registrada.
- **Infraestrutura real** (Postgres e LocalStack), como o resto da suíte do `email-service`.

## Fora de escopo

- O `app`: as regras equivalentes dele vieram das specs 05-029, 05-034, 05-061 e 05-063.
- O que já está feito e listado em "Situação atual".
- Idempotência do `POST /emails` (spec
  [05-059](../05-059-chave-idempotencia-envio-email/spec.md)) e consumo idempotente na
  `email-lambda` (spec [05-060](../05-060-consumo-idempotente-email-lambda/spec.md)).
- A `email-lambda`.
- Rejeição de sintaxe inválida pelo SES real ([#104](https://github.com/lalgarve/jogo-acoes/issues/104))
  e renderização contra o SES real ([#204](https://github.com/lalgarve/jogo-acoes/issues/204)).

## Decisões em aberto

1. **Quem cede quando o código e o contrato divergem** (requisito 4). Proposta: o contrato é a
   fonte da verdade (contract-first), então o código se ajusta, a não ser que o contrato esteja
   errado. Cada divergência encontrada é decidida pela Leila e registrada no `plan.md`. No exemplo
   já conhecido, o `400` de `POST /templates` por nome em branco, a proposta é declarar o `400` no
   contrato, porque é o mesmo erro de validação que `POST /emails` já declara.
2. **Valores dos tempos limite** (requisito 5). Proposta: decidir no `plan.md`, a partir do tempo
   medido de uma chamada normal ao LocalStack. A requisição ao SES fica abaixo do timeout de
   leitura do cliente Feign do `app` (hoje 5 s, no `application.yml` do `app`), para o `app` receber o `503` em vez de desistir antes.
