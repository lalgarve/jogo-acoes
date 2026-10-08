# Spec: Cobertura de testes do `app` depois da PR #120

**Status:** implementada
**Issue:** [#132](https://github.com/lalgarve/jogo-acoes/issues/132)
**Iteração:** iteration-5

## Resumo

Recuperar a cobertura de testes do `app`, que caiu na PR #120 (spec 05-034). São três frentes:

1. tirar da contagem do JaCoCo o cliente Feign gerado do email-service, como a constitution
   já manda fazer com código gerado;
2. publicar o relatório HTML do JaCoCo como artifact do CI, para que dê para investigar a
   cobertura de uma PR sem rodar a suíte localmente;
3. novos testes JUnit para entradas que hoje nenhum teste exercita, no `EmailServiceGateway`,
   no `PlayerManagementService` e no `EntryRequestService`. Esses testes trazem uma única
   mudança de código: o gateway passa a recusar nome de template vazio ou nulo (D1).

## Motivação

A PR #120 baixou a cobertura do `app` para 76,83% de instruções. O relatório JaCoCo gerado
localmente em 2026-10-06 mostrou duas causas:

- **Código gerado contado na média, a causa principal.** O cliente Feign gerado a partir de
  `docs/openapi-email-service.yaml` fica em `dev.leilaalgarve.jogoacoes.email.client.api.**`
  (`app/pom.xml`, execução do openapi-generator do email-service). O `<excludes>` do JaCoCo só
  tira o pacote do outro gerador (`dev/leilaalgarve/jogoacoes/api/**` e `org/openapitools/**`),
  então o pacote novo entra na conta. São DTOs (`equals`/`hashCode`/`toString`/builders),
  `ClientConfiguration` e `ApiKeyRequestInterceptor`, com só 210 de 1381 instruções cobertas.
  Isso contraria a constitution ("CI e cobertura de testes": código gerado fica de fora da
  contagem). **Só tirando esse pacote, a cobertura vai de 76,83% para 93,89% de instruções e de
  80,35% para 94,42% de linhas.**
- **Caminhos de erro sem teste no código escrito à mão.** O `EmailServiceGateway` cobre 44 de 59
  linhas e 9 de 14 branches. O `EmailServiceRejectedException` está com 0%, porque nenhum teste
  recebe um 4xx do email-service além de 401/404. O caminho de criação do `upsertTemplate`
  (PUT → 404 → POST) também não roda: o único teste de upsert usa um template que já existe.

O CI não mostrava nada disso. Ele só manda o `jacoco.xml` para a action que comenta na PR, e o
HTML, que mostra linha a linha o que ficou sem cobertura, não fica disponível em lugar nenhum.

Outra coisa que confunde quem lê: o comentário da PR mostra cobertura de **instruções**
(76,83%, ❌), enquanto o `jacoco:check` mede **linhas** (80,35%, passa). Por isso o CI ficou
verde com o comentário vermelho.

## Comportamento atual do email-service (observado)

Em 2026-10-06, o email-service local (perfil `docker`, chave de teste do cliente `jogo-acoes`)
foi chamado direto, com o template `login-link-invite` (variáveis `name`, `competitionName` e
`link`). O resultado define o que cada cenário abaixo pode esperar:

| Chamada | Resposta |
|---|---|
| `POST /emails` com dados completos, `{}`, `null`, a mais ou faltando | **202** em todos os casos |
| `POST /templates/{name}/preview` com dados completos ou a mais | 200 |
| `POST /templates/{name}/preview` com `{}` ou faltando dados | 422 (`Attribute 'competitionName' is not present in the rendering data`) |
| `POST /templates/{name}/preview` com `variables: null` | 400 (`variables must not be null`) |
| `GET /templates/` (nome vazio) | 404 do Spring (rota inexistente), sem corpo `Error` |
| `GET /templates/%20` (nome em branco) | 404 (`No template named ' '`) |
| `PUT /templates/` (nome vazio) | 404 do Spring (rota inexistente) |
| `POST /templates` com `name: ""` | **201: cria um template de nome vazio** |
| `POST /templates` com `name: null` | 400 (`name must not be null`) |
| `PUT /templates/{name}` com `subject`/`body` vazios | 422 (SES: `The subject must be specified`) |
| `PUT /templates/{name}` com `subject`/`body` nulos | 400 (`body must not be null; subject must not be null`) |

Na prática:

- O `sendEmail` com dados faltando é aceito com 202. A falha só acontece depois, no
  `email-lambda`, quando o SES tenta renderizar, e o `app` nunca fica sabendo.
- O `upsertTemplate` com nome vazio cai em PUT 404 → POST 201 e **cria um template sem nome** no
  email-service. O gateway termina sem erro.

## Cenários (comportamento esperado)

Nenhum cenário tem `.feature` novo. A configuração não é comportamento de produto, e os testes
são de fronteira entre classes, não comportamento visível ao jogador.

### C1 — Código gerado fora da contagem

**Dado** o relatório JaCoCo do `app` (`mvn -pl app -am verify`)
**Quando** se procura qualquer classe de `dev.leilaalgarve.jogoacoes.email.client.api` ou dos
subpacotes dele (`model`, `configuration`)
**Então** nenhuma aparece no relatório (HTML, XML ou CSV), nem no comentário da PR
**E** as classes escritas à mão em `dev.leilaalgarve.jogoacoes.email.client`
(`EmailServiceGateway`, `EmailTemplateSynchronizer`, `EmailServiceClientConfiguration`,
`EmailServiceTemplate`, `TemplatePreview`) continuam no relatório.

### C2 — Relatório HTML disponível no CI

**Dado** um run do workflow `CI`, de PR, de push no `master` ou manual
**Quando** o run termina, mesmo que tenha falhado por teste ou pelo piso de cobertura
**Então** o run tem um artifact com o diretório `app/target/site/jacoco/` inteiro
**E** abrir `index.html` desse artifact mostra o mesmo relatório que `mvn verify` gera
localmente.

### C3 — Cobertura acima do piso nas duas métricas

**Dado** C1 aplicado e os testes novos abaixo escritos
**Quando** o CI roda na PR desta spec
**Então** a cobertura de linhas (o `jacoco:check`) e a de instruções (o comentário da PR) ficam
as duas em 80% ou mais.

### Testes novos

Os cenários a seguir são testes de método Java.

Em todos os cenários do gateway, "template válido" é um dos templates que o
`EmailTemplateSynchronizer` registra (ex.: `login-link-invite`). "Dados completos" são todas as
variáveis desse template (`name`, `competitionName`, `link`), e "destinatário válido" é um e-mail
único gerado por `TestEmails.unique(...)`.

### `EmailServiceGateway.sendEmail`

Base: template válido, destinatário válido.

| # | `templateData` | Então |
|---|---|---|
| S0 | completo | devolve o id do envio (não nulo) |
| S1 | vazio (`Map.of()`) | devolve o id do envio, sem exceção (D2) |
| S2 | `null` | devolve o id do envio, sem exceção (D2) |
| S3 | completo + uma chave a mais | devolve o id do envio; a chave a mais é ignorada pelo template |
| S4 | faltando `competitionName` | devolve o id do envio, sem exceção (D2) |

S1, S2 e S4 registram o comportamento atual, não o desejado. O e-mail é aceito, mas não chega a
ser entregue, porque a renderização falha depois, no `email-lambda`, e o `app` não fica sabendo.
O nome e o comentário de cada teste deixam isso explícito, para que alguém que mude esse
comportamento no email-service saiba que o teste precisa mudar junto.

### `EmailServiceGateway.findTemplate`

| # | Nome | Então |
|---|---|---|
| F1 | `""` | lança `IllegalArgumentException`, sem chamar o email-service (D1) |
| F2 | `null` | lança `IllegalArgumentException`, sem chamar o email-service (D1) |

### `EmailServiceGateway.upsertTemplate`

| # | Template | Então |
|---|---|---|
| U1 | template novo (nome único, ainda inexistente), chamado duas vezes | depois da 1ª chamada (caminho PUT 404 → POST), `findTemplate` devolve o template; depois da 2ª (caminho PUT), devolve o mesmo template, sem erro |
| U2 | nome `""` | lança `IllegalArgumentException`, sem chamar o email-service; nenhum template de nome vazio é criado (D1) |
| U3 | nome `null` | lança `IllegalArgumentException`, sem chamar o email-service (D1) |
| U4 | `subject` e `body` vazios | lança `EmailServiceRejectedException` (422); o template no email-service continua como estava |
| U5 | template `null` | lança `IllegalArgumentException`, sem chamar o email-service (D1) |

"Sem chamar o email-service": é o próprio tipo da exceção que prova isso. Com qualquer resposta
do email-service, o gateway devolveria `Optional.empty()`, terminaria sem erro ou lançaria uma
`EmailService*Exception`, nunca `IllegalArgumentException`.

### `EmailServiceGateway.preview`

Base: template válido.

| # | `variables` | Então |
|---|---|---|
| P0 | completo | devolve assunto e corpo renderizados, com os valores das variáveis |
| P1 | vazio (`Map.of()`) | lança `EmailServiceRejectedException` (422), sem nova tentativa |
| P2 | `null` | lança `jakarta.validation.ConstraintViolationException` antes de chamar o email-service: a bean validation do cliente gerado recusa (`variables` é obrigatório no contrato). Ver "Desvio encontrado na implementação" |
| P3 | completo + uma chave a mais | igual a P0 |
| P4 | faltando `competitionName` | lança `EmailServiceRejectedException` (422), sem nova tentativa |

"Sem nova tentativa": 4xx não entra no `retrying`. O teste confirma que a exceção chega em bem
menos tempo que `MAX_ATTEMPTS` × 200 ms de espera.

### `PlayerManagementService.removePlayer`

**Dado** que o jogador A participa da competição privada 1
**E** o jogador A não participa da competição privada 2
**Quando** `removePlayer` é chamado com o id da competição 2 e o id da participação de A na
competição 1
**Então** lança `PlayerNotFoundException`
**E** a participação de A na competição 1 continua existindo, com o mesmo status
**E** nenhum registro `PARTICIPATION_STATUS_CHANGED` é gravado no log de auditoria.

### `EntryRequestService.requestEntry`

Base: competição pública existente, captcha válido.

| # | E-mail | Então |
|---|---|---|
| E1 | `null` | lança `EntryRequestValidationException`; nenhuma participação é criada e nenhum e-mail é enviado |
| E2 | `"   "` (em branco) | igual a E1 |

## Requisitos funcionais

### Configuração

- `app/pom.xml`: acrescentar `<exclude>dev/leilaalgarve/jogoacoes/email/client/api/**</exclude>`
  ao `<excludes>` do `jacoco-maven-plugin` e atualizar o comentário ao lado, que hoje só cita os
  dois pacotes do gerador do `app`. O `<excludes>` fica na `<configuration>` do plugin, então já
  vale para o `report` e para o `check`.
- `.github/workflows/ci.yml`: um passo `actions/upload-artifact` com `if: always()` depois dos
  testes do `app`, publicando `app/target/site/jacoco/`.

### `EmailServiceGateway` (D1)

- Todo método público que recebe um nome de template (`sendEmail`, `findTemplate`,
  `upsertTemplate` e `preview`) lança `IllegalArgumentException` antes de qualquer chamada HTTP
  quando o nome é `null` ou em branco. O `upsertTemplate` faz o mesmo quando o template é `null`.
  Esses nomes vêm do próprio `app` (`EmailTemplateSynchronizer`, `EmailTemplate`), então um nome
  vazio é erro de programação e não deve chegar ao email-service.
- Fora isso, nenhuma mudança de comportamento no gateway.

### Testes

- Os testes chamam o método da classe direto (JUnit), sem passar pela API HTTP do `app`.
- Dependências reais, sem mock (constitution, "Testes: preferir real a fake sempre que der"):
  - gateway: o email-service real do Docker Compose, como em `EmailServiceClientIntegrationTest`;
  - `PlayerManagementService` e `EntryRequestService`: contexto Spring com Postgres real.
- Os dados de teste partem de um builder válido e mudam só o campo sob teste (constitution,
  "Dados de teste: Object Mother + Test Data Builder").
- Templates criados pelos testes (U1) usam nome único por execução: o email-service não tem
  `DELETE /templates/{name}`, então nada é apagado ao final.

## Requisitos não-funcionais

- Nenhum teste é pulado por falta de infraestrutura (constitution, "Testes exigem a
  infraestrutura de pé").
- O CI continua com os mesmos passos; os testes novos rodam no `mvn -pl app -am verify` que já
  existe.

## Fora de escopo

- Igualar a métrica do comentário da PR à do `jacoco:check` (linhas nos dois). C3 exige as duas
  acima do piso, então a diferença deixa de esconder um problema. Mudar o que o comentário mede
  é outra discussão.
- Corrigir o email-service para recusar `POST /templates` com nome vazio (400): Issue #133 (D1).
- Validar `templateData` contra as variáveis do template no `POST /emails`, seja no gateway ou
  no email-service: Issue #134 (D2).

## Decisões

Decididas em 2026-10-06, todas pela opção recomendada.

- **D1 — Nome de template vazio/nulo e template nulo (F1, F2, U2, U3, U5): o gateway recusa
  antes de chamar o email-service.** Sem validação, o resultado dependeria de como o Feign monta
  a URL: com nome vazio, o `findTemplate` devolve `Optional.empty()` só porque a rota não existe,
  e o `upsertTemplate` cria um template sem nome no email-service. Com template `null`, o
  `upsertTemplate` lança `NullPointerException`. Agora o gateway lança `IllegalArgumentException`
  (ver "Requisitos funcionais"). À parte, o email-service deve recusar nome vazio com 400
  (Issue #133). Opção descartada: só documentar o comportamento atual nos testes.
- **D2 — `sendEmail` com dados vazios, nulos ou faltando (S1, S2, S4): os testes registram o
  comportamento atual.** O email-service aceita com 202 e a falha só acontece no `email-lambda`.
  Os testes esperam o id do envio e dizem explicitamente que o `app` não fica sabendo da falha de
  renderização. Opções descartadas: o gateway validar os dados contra as variáveis do template; o
  email-service validar contra `variablesSchema` e responder 4xx (mudança de contrato do
  email-service, outra spec). Validar fica para a Issue #134.
- **D3 — Nível dos testes: método chamado direto, com dependências reais.** O pedido falava em
  "testes unitários". Os testes chamam o método da classe direto, mas contra o email-service e o
  Postgres reais, sem mock, como manda a constitution ("Testes: preferir real a fake sempre que
  der").

## Desvio encontrado na implementação

- **P2 (2026-10-06).** A spec esperava `EmailServiceRejectedException` (400 do email-service). Na
  prática, o cliente Feign gerado tem bean validation (`@NotNull` em `variables`, que é
  obrigatório em `docs/openapi-email-service.yaml`) e lança
  `jakarta.validation.ConstraintViolationException` antes de qualquer chamada HTTP. O mesmo vale
  para `sendEmail` com `templateName` nulo, que hoje a validação de D1 já intercepta antes. O teste
  registra o comportamento real. Isso quer dizer que uma exceção do cliente gerado chega ao resto
  do `app` sem tradução, o que contraria a ideia de que só o `EmailServiceGateway` conhece o
  cliente (spec 05-034). Decidir se o gateway deve recusar `variables` nulo com
  `IllegalArgumentException`, como em D1, fica em aberto.
