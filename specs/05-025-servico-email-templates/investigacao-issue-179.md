# Investigação — Issue #179: pré-visualização de templates com o SES do LocalStack

**Issue:** [#179](https://github.com/lalgarve/jogo-acoes/issues/179)
**Branch:** `claude/project-thread-6bql66` (mesmo commit do `master` `7ca4496` na hora do teste)
**Data:** 2026-10-24
**LocalStack:** `localstack/localstack:4` → versão `4.14.0`, edição `community`, sem licença
(confirmado via `GET /_localstack/info`)
**Continuação no SES real:** [#204](https://github.com/lalgarve/jogo-acoes/issues/204)
(Iteração 6), ao lado da [#104](https://github.com/lalgarve/jogo-acoes/issues/104)

## Resumo

As duas suspeitas da issue se confirmaram pela API do `email-service`: a pré-visualização sem
variável dá `422` com o LocalStack, e o `{{name` no assunto também dá `422`, só que com a mensagem
de um erro interno do LocalStack e depois de 4 tentativas do SDK.

| Pergunta da issue | Resposta |
| --- | --- |
| 1. Preview sem variável devolve `422`? | Sim. `422` com `Attribute 'nome' is not present in the rendering data.` O caderno dizia o contrário e foi corrigido. |
| 2. O que dá o preview com `{{name` no assunto? | `422` pelo caminho genérico de `SesException` → `TemplateRenderFailedException`, com `exception while calling ses.TestRenderTemplate: string index out of range` e `SDK Attempt Count: 4`. Não é "template rejeitado". |
| 3. Muda a cobertura de testes? | Sim. Um cenário de preview com variável faltando roda contra o LocalStack, fora de `@requires-real-ses`, e hoje nenhum teste lança a `TemplateRenderFailedException`. |

## Ambiente

- **LocalStack:** o serviço `localstack` do `docker-compose.yml`.
- **`email-service` e `config-server`:** jars do próprio código do branch, compilados fora do
  container e empacotados numa imagem `eclipse-temurin:21-jre` simples, porque o Maven dentro do
  `docker build` não confiava no proxy HTTPS da sessão na nuvem. O código que roda é o mesmo; só
  muda onde o build acontece.
- **Chaves:** `test-api-key.sh restore` duas vezes e `gerar-chaves.sh`, como na seção 1 do
  [caderno](../../docs/disciplina/caderno-de-testes-email-service.md). Todas as chamadas usaram a
  chave `envio` (`caderno-envio`, `email:send`) pelo `chamar.sh`, a partir de um banco vazio
  (`docker compose down -v`).

## Caso 1: preview sem variável

Com o LocalStack, faltar qualquer variável no `POST /templates/{name}/preview` dá `422` com a
mensagem `MissingRenderingAttribute` do SES. Template usado: o `boas-vindas` do roteiro 2 do
caderno (assunto `Bem-vindo, {{nome}}`, corpo com `{{nome}}` e `{{link}}`).

| Variáveis enviadas | Status | Resposta (`message`) |
| --- | --- | --- |
| `nome` e `link` | `200` | assunto `Bem-vindo, Maria` e corpo renderizado |
| `nome`, `link` e `extra` (sobrando) | `200` | igual à linha acima; variável a mais é ignorada |
| `{}` (nenhuma) | `422` | `Attribute 'nome' is not present in the rendering data. (Service: Ses, Status Code: 400, Request ID: …) (SDK Attempt Count: 1)` |
| só `nome` (falta `link`, só no corpo) | `422` | `Attribute 'link' is not present in the rendering data. …` |
| só `link` (falta `nome`) | `422` | `Attribute 'nome' is not present in the rendering data. …` |
| corpo `{}`, sem `variables` | `400` | `variables must not be null` (validação do próprio serviço, não chega ao SES) |

O SES aponta só a primeira variável que falta, não todas. Nenhum desses `422` escreve linha no
log do `email-service`.

## Caso 2: sintaxe inválida

O `email-service` aceita qualquer template inválido no cadastro (`201`/`200`), e o `{{name` no
assunto só aparece como erro no preview.

| Template | Cadastro | Preview | Resposta do preview |
| --- | --- | --- | --- |
| assunto `Hi {{name`, `name` informado | `201` | `422` | `exception while calling ses.TestRenderTemplate: string index out of range (Service: Ses, Status Code: 500, …) (SDK Attempt Count: 4)` |
| assunto `Hi {{name`, `variables: {}` | `201` | `422` | `Attribute 'name' is not present in the rendering data. …` (a falta da variável é checada antes do erro de sintaxe) |
| corpo `<p>Hello {{name</p>`, `name` informado | `201` | `422` | `Attribute 'name</p>' is not present in the rendering data. …` (o LocalStack lê `name</p>` como nome de variável) |
| corpo `<p>{{#if name}}x</p>` (bloco aberto) | `201` | `200` | corpo `<p>x</p>`, sem erro |
| `PUT` de `boas-vindas` para assunto `Oi {{nome` | `200` | `422` | o mesmo `string index out of range` da primeira linha |

**O caminho do `{{name`.** O SES devolve `InternalError` (HTTP 500). O
`SesTemplateClient.testRenderTemplate` cai no `catch (SesException e)` genérico e lança
`TemplateRenderFailedException`, que o `ApiExceptionHandler` mapeia para `422`. É o caminho
esperado pela issue: não há motivo de "template rejeitado" (`TemplateRejectedBySesException`),
que só existe no cadastro e na alteração.

**Decisão: só uma nota, sem tratamento.** O SES de verdade deve rejeitar esse template já no
`CreateTemplate`/`UpdateTemplate` (por isso os cenários `@requires-real-ses`), então em produção
um template assim não chegaria ao preview. O `InternalError` é um defeito do LocalStack, e o
`422` que sai dele já é razoável. A rejeição no SES real fica para a #104/#204.

## Variável faltando com e sem `if`

O sistema não tem biblioteca de Handlebars: nenhum `pom.xml` declara uma, e quem renderiza é o
SES (aqui, o LocalStack), no `TestRenderTemplate` do preview e no `SendTemplatedEmail` da
`email-lambda`. No preview, o LocalStack só reclama de variável que ele de fato precisa avaliar:
dentro de um `if` falso, a variável faltando passa.

Templates usados (assunto `Oi {{nome}}` nos dois):

- `sem-if`: corpo `<p>Olá {{nome}}, seu cupom é {{cupom}}</p>`
- `com-if`: corpo `<p>Olá {{nome}}</p>{{#if vip}}<p>Seu cupom: {{cupom}}</p>{{/if}}`

| Template | Variáveis enviadas | Preview | Resultado |
| --- | --- | --- | --- |
| `sem-if` | `nome` | `422` | `Attribute 'cupom' is not present in the rendering data.` |
| `com-if` | `nome`, `vip: false` | `200` | `<p>Olá Maria</p>` |
| `com-if` | só `nome` (nem `vip`) | `200` | `<p>Olá Maria</p>`: `vip` ausente conta como falso, sem erro |
| `com-if` | `nome`, `vip: true` | `422` | `Attribute 'cupom' is not present in the rendering data.` |
| `com-if` | `nome`, `vip: true`, `cupom` | `200` | `<p>Olá Maria</p><p>Seu cupom: X10</p>` |
| `com-if` | `nome`, `vip: false`, `cupom` | `200` | `<p>Olá Maria</p>` |

**No envio, o LocalStack não renderiza nada.** O `SendTemplatedEmail` aceitou os casos
`sem-if` sem `cupom`, `com-if` com `vip: false` e `com-if` com `vip: true` sem `cupom`, e devolveu
`MessageId` nos três; o `/_aws/ses` guarda só o nome do template e o `TemplateData`, com
`Subject` e `Body` nulos. O envio foi feito direto na API do SES (`awslocal ses
send-templated-email`), porque nesta sessão a `email-lambda` não resolvia
`localhost.localstack.cloud` (limitação de DNS do ambiente na nuvem, não do código).

**Não verificado:** se o SES de verdade também ignora a variável dentro do `if` falso, ou se
exige todas as variáveis do template; e o que acontece no envio com variável faltando. Fica para
a [#204](https://github.com/lalgarve/jogo-acoes/issues/204).

## Achados extras

1. **A mensagem do `422` vaza detalhes do SDK.** Todas as respostas trazem o sufixo
   `(Service: Ses, Status Code: 400, Request ID: …) (SDK Attempt Count: 1)`, porque o serviço
   repassa `e.getMessage()` da exceção do SDK. O `plan.md` diz "com a mensagem da AWS", então não
   é desvio da spec, mas o cliente vê o nome do serviço interno e o número de tentativas. Usar
   `e.awsErrorDetails().errorMessage()` deixaria só o texto do SES. Não alterado nesta issue.
2. **O erro 500 é tentado 4 vezes.** O SDK repete a chamada quando o SES devolve 5xx
   (`SDK Attempt Count: 4`): o preview com `{{name` levou 0,22 s contra 0,03 s do preview com
   variável faltando. O `TestRenderTemplate` não altera nada, então repetir é seguro e atende à
   regra de retry só de operação idempotente da constitution. Não pede ação.

## O que mudou no repositório

- [x] **Caderno** (`docs/disciplina/caderno-de-testes-email-service.md`, roteiro 2): passo novo
  de preview com variável faltando (`422`) e a nota sobre o LocalStack corrigida.
- [ ] **Feature** (`register_templates.feature`, Rule de preview): cenário novo, sem
  `@requires-real-ses`, "Client previews a template without a variable it uses" → `422` com o
  motivo do SES. Hoje nenhum teste lança a `TemplateRenderFailedException` nem passa pelo `catch`
  de `MissingRenderingAttributeException`, o que fere o critério de aceite de exceções da
  constitution. Não feito nesta issue.

## Como reproduzir

Com o ambiente da seção 1 do caderno no ar (chaves geradas e `C` definido), na raiz do
repositório:

```bash
$C/chamar.sh envio POST /templates <<'JSON'
{"name":"boas-vindas","subject":"Bem-vindo, {{nome}}","body":"<p>Olá, {{nome}}!</p><p>Entre pelo link: <a href=\"{{link}}\">{{link}}</a></p>"}
JSON
# 201

$C/chamar.sh envio POST /templates/boas-vindas/preview <<'JSON'
{"variables":{}}
JSON
# 422  Attribute 'nome' is not present in the rendering data. ...

$C/chamar.sh envio POST /templates <<'JSON'
{"name":"tag-aberta","subject":"Hi {{name","body":"<p>x</p>"}
JSON
# 201

$C/chamar.sh envio POST /templates/tag-aberta/preview <<'JSON'
{"variables":{"name":"A"}}
JSON
# 422  exception while calling ses.TestRenderTemplate: string index out of range ... (SDK Attempt Count: 4)

$C/chamar.sh envio POST /templates <<'JSON'
{"name":"com-if","subject":"Oi {{nome}}","body":"<p>Olá {{nome}}</p>{{#if vip}}<p>Seu cupom: {{cupom}}</p>{{/if}}"}
JSON
# 201

$C/chamar.sh envio POST /templates/com-if/preview <<'JSON'
{"variables":{"nome":"Maria","vip":false}}
JSON
# 200  {"subject":"Oi Maria","body":"<p>Olá Maria</p>"}
```
