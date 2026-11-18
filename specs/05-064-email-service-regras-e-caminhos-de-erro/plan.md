# Plan: email-service, regras de arquitetura e caminhos de erro que faltam

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Conferido em 2026-11-17 no código de `development`.

- **Testes de arquitetura:** o `ArchitectureTest` do `email-service` já usa ArchUnit, com as regras
  das specs 05-030, 05-034, 05-053, 05-061 e 05-063. O `LayerRules` (spec 05-063) define o
  predicado `REPOSITORIES`: interfaces do Spring Data **e** classes `@Repository`. O
  `email-service` tem os dois tipos: cinco interfaces `JpaRepository` e duas classes `@Repository`
  com `JdbcClient` (`DisposableDomainRepository` e `ImportExecutionRepository`, no pacote
  `disposable`). Todos os sete são `public`, então só uma regra impede o uso fora do pacote.
- **Regra equivalente no `app`:** `repositoriesAreOnlyAccessedFromTheirOwnModule` (spec 05-029). Ela
  olha só interfaces que estendem `Repository`, porque o `app` não tem classes `@Repository`.
- **Uso de AWS hoje:**
  - `SesTemplateClient` (`template`) usa o `SesClient` síncrono;
  - `EmailQueuePublisher` (`send`) usa o `SqsAsyncClient`;
  - `DeadLetterConsumer` (`send`) usa `@SqsListener` do Spring Cloud AWS.

  Os clientes vêm do autoconfigure do Spring Cloud AWS 4.1.0, que oferece `SesClientCustomizer`
  e `SqsAsyncClientCustomizer`. O mesmo `SqsAsyncClient` serve o publicador e o listener.
- **Tempo limite hoje:**
  - SES: nenhum. O `SesTemplateClient.call` já traduz qualquer `SdkClientException`, e por isso
    também um `ApiCallTimeoutException`, em `SesUnavailableException` (`503`).
  - SQS: o publicador espera cada `Future` por no máximo `SQS_TIMEOUT_SECONDS = 10`, e qualquer
    exceção vira `QUEUE_UNAVAILABLE`.
  - O `app` lê o `email-service` com `read-timeout: 5000` (Feign).
- **Testes HTTP:** os cenários Cucumber fazem as chamadas pelo
  `EmailServiceScenarioWorld.request()` (RestAssured). Testes de integração como o
  `SesUnavailableIntegrationTest` chamam `RestAssured.given()` direto.
- **Contrato:** `docs/openapi-email-service.yaml`, OpenAPI 3.0.3, `servers: /api`.
- **Simulação de serviço fora do ar:** o `TcpForwarder` (spec 05-044) recusa conexões até ser
  aberto. Não existe ainda um servidor que aceite a conexão e não responda.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Regra do requisito 1: o que é repository | O predicado `LayerRules.REPOSITORIES`, o mesmo da 05-063 | adotada | A regra do `app` (só interfaces) deixaria de fora as duas classes `@Repository` do `disposable`. |
| Regra do requisito 1: o que é "o próprio pacote" | O pacote de domínio de primeiro nível abaixo de `emailservice` (`auth`, `disposable`, `domain`, `send`, `template`), contando subpacotes. O `disposable.batch` usa os repositories de `disposable` | adotada | Mesma noção de módulo da 05-061 (`ModuleDependencyGraph`). |
| Regra do requisito 2: quem pode usar AWS | Lista fechada de classes no próprio teste: `SesTemplateClient`, `EmailQueuePublisher`, `DeadLetterConsumer`. A regra proíbe `software.amazon.awssdk..` e `io.awspring..` em qualquer outra classe de produção | adotada | A spec pede lista explícita, para que uma classe nova com AWS seja uma decisão visível. Uma regra por pacote (`send` e `template`) deixaria qualquer classe desses pacotes usar AWS. |
| Configuração dos clientes AWS (requisito 5) | Fica fora da lista do requisito 2. Uma classe `@Configuration` com um `SesClientCustomizer` usa o AWS SDK. Ela entra na lista, como `template.SesClientConfig` | adotada | A configuração é parte da integração com o SES. Fica no mesmo pacote do `SesTemplateClient`. |
| Tempo limite do SES | `apiCallTimeout` no cliente inteiro, por um `SesClientCustomizer`, lido de `email.ses.api-call-timeout` | adotada | O SES só é usado pelo `SesTemplateClient`, então um limite no cliente vale para todas as chamadas. O `apiCallTimeout` cobre as novas tentativas do SDK juntas, e é isso que o `app` espera. |
| Tempo limite do SQS | `apiCallTimeout` **por chamada**, com `overrideConfiguration` no `GetQueueUrlRequest` e no `SendMessageRequest` do publicador, lido de `email.publisher.sqs-timeout`. O `Future.get` passa a usar o mesmo valor, mais uma folga de 1 s, só como rede de segurança. `SQS_TIMEOUT_SECONDS` sai | adotada | Um limite no cliente inteiro (`SqsAsyncClientCustomizer`) também valeria para o *long polling* do `@SqsListener`, que espera mensagens de propósito, e quebraria a leitura da fila de devolução. Com o limite na chamada, o SDK encerra a chamada em vez de deixá-la rodando. |
| Valores dos tempos limite | Proposta: `email.ses.api-call-timeout: 3s` e `email.publisher.sqs-timeout: 10s` (o valor de hoje). A T001 mede uma chamada normal ao LocalStack, e a decisão final fica com a Leila | **em aberto** (decisão 2 da spec) | O SES fica abaixo dos 5 s de leitura do `app`, para o `app` receber `503` em vez de desistir antes. Para o SQS não há quem espere do outro lado, então o valor de hoje serve. |
| Como provocar "não responde" | Um `BlackHoleServer` novo no `testsupport`: um `ServerSocket` local que aceita a conexão e nunca responde. O endpoint do SES ou do SQS aponta para ele no teste | adotada | Um socket de verdade, sem mock (constitution, "Testes: preferir real a fake"). O `TcpForwarder` recusa a conexão, e isso já cai no "SES fora do ar" sem testar o tempo limite. |
| Teste do requisito 4, direção "o código devolve um status que o contrato não declara" | Um filtro global do RestAssured (`RestAssured.filters(...)`), registrado no `testsupport`, que valida cada resposta contra `docs/openapi-email-service.yaml` com o `swagger-request-validator-restassured` da Atlassian, dependência de teste. Só o erro de status desconhecido (`validation.response.status.unknown`) fica ligado; o resto da validação fica em `IGNORE`. A versão é conferida no Maven Central na T004 | adotada | Assim, todo teste HTTP que já existe confere o contrato sem mudar. O teste que receber um status não declarado falha com a operação e o status. Validar corpo, cabeçalho e caminho fora do contrato (Actuator, Swagger UI) é outra conversa e geraria barulho fora do escopo da spec. |
| Teste do requisito 4, direção "o contrato declara um status que nenhum teste produz" | Proposta: em vez de teste automático, fica no inventário da T005, numa tabela operação × status de erro × teste que o produz. Um status declarado sem teste ganha teste, ou sai do contrato, por decisão da Leila | **em aberto** | Um teste automático precisa juntar o que **todos** os outros testes viram, e só pode rodar depois deles. O Surefire não garante essa ordem, e uma falha num *listener* de fim de execução não derruba o build de forma confiável. Se a Leila aceitar, o requisito 4 da `spec.md` muda no mesmo commit `decision:`. |
| Divergência entre código e contrato | O contrato é a fonte da verdade. Cada divergência que o filtro achar é decidida pela Leila e registrada aqui, antes do código ou do contrato mudar | **em aberto** (decisão 1 da spec) | Contract-first. No caso já conhecido (`400` em `POST /templates` por nome em branco), a proposta é declarar o `400`. |
| Requisito 3: forma do inventário | Uma tabela no `tasks.md` (T005) com uma linha por classe de exceção e uma por ramo de erro, apontando o teste. O que faltar ganha teste numa task à parte (T006) | adotada | O inventário é uma conferência, não código. A tabela é o que quem revisa lê. |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

- `email-service/src/test/java/.../emailservice/common/`:
  - `ArchitectureTest`: duas regras novas, `repositoriesAreOnlyUsedInsideTheirOwnModule` e
    `onlyTheAwsIntegrationClassesUseAws`;
  - `testsupport/BlackHoleServer`, novo;
  - `testsupport/ContractStatusFilter`, novo: o filtro do RestAssured. Ele é registrado uma vez
    por execução, antes de qualquer teste, por um `LauncherSessionListener` do JUnit Platform
    declarado em `src/test/resources/META-INF/services/`. Assim vale tanto para os cenários
    Cucumber quanto para os testes de integração, que rodam na mesma JVM.
- `email-service/src/test/java/.../template/SesTimeoutIntegrationTest`, novo.
- `email-service/src/test/java/.../send/SqsTimeoutIntegrationTest`, novo.
- `email-service/src/main/java/.../template/SesClientConfig`, novo: `SesClientCustomizer`.
- `email-service/src/main/java/.../send/EmailQueuePublisher`: tempo limite configurável, por
  chamada.
- `email-service/src/main/resources/application.yml`: `email.ses.api-call-timeout` e
  `email.publisher.sqs-timeout`, com comentário.
- `email-service/pom.xml`: `swagger-request-validator-restassured` com escopo `test`.
- `docs/openapi-email-service.yaml`: o que as divergências decidirem.

## Riscos e trade-offs

- **O filtro global vale para todos os testes HTTP do módulo.** Um teste que chama de propósito
  uma rota fora do contrato (Swagger UI, Actuator) não pode falhar por isso. Mitigação: o filtro
  só valida respostas de operações que o contrato tem; caminho desconhecido fica em `IGNORE`. A
  T004 confere a suíte inteira com o filtro ligado.
- **Divergências podem ser mais do que o `400` já conhecido.** O filtro roda contra a suíte toda,
  então pode achar outras. Cada uma para na decisão da Leila, sem mudar código nem contrato por
  conta própria.
- **Jackson duplicado no classpath de teste.** O validador da Atlassian usa Jackson 2
  (`com.fasterxml`), e o projeto usa Jackson 3 (`tools.jackson`). Os pacotes são diferentes e
  convivem. A T004 confirma.
- **Os testes de tempo limite medem tempo.** Para não ficarem instáveis, cada teste usa um limite
  curto (1 s), definido no próprio teste, e só confere que a resposta chega **antes** de um teto
  folgado (5 s). Ele não confere o tempo exato. Hoje a resposta só chegaria depois de dezenas de
  segundos, então a fase vermelha falha com folga.
- **A regra do requisito 2 por lista de classes** precisa ser atualizada quando uma classe nova de
  integração entrar. É de propósito (ver decisões).

## Critério de aceite: exceções

- **Classes de exceção novas:** nenhuma prevista.
- **Ramos de erro novos ou alterados:**
  - o tempo limite do SES vira `ApiCallTimeoutException`, que cai no `catch (SdkClientException)`
    do `SesTemplateClient.call`, já existente. A T002 o exercita pelo `POST /templates`;
  - o tempo limite do SQS vira exceção na chamada do publicador, que cai nos `catch (Exception)`
    do `EmailQueuePublisher`, já existentes. O publicador pede a URL da fila a cada lote, então,
    com o SQS sem responder, a T003 exercita o `catch` do `getQueueUrl`. O `catch` do
    `sendMessage` só é alcançado se a URL vier e o envio travar. A T005 confere se algum teste já
    o percorre; se não, a T006 decide como provocá-lo, ou a justificativa vem para cá.
- **Inventário das 13 classes e dos ramos que já existem:** T005.
