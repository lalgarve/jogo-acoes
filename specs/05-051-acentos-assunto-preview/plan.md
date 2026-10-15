# Plan: pré-visualização de template devolve o assunto com acentos corretos

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `TemplateService` chama `SesTemplateClient.testRenderTemplate`, que devolve o
  `renderedTemplate` do `TestRenderTemplate` do SES: uma mensagem MIME inteira em texto
  (cabeçalhos e partes), não o par assunto/corpo já separado.
- `MimeRenderedTemplateParser.parse` converte essa `String` em bytes UTF-8, monta um
  `MimeMessage` com `Session.getDefaultInstance(new Properties())` e lê `getSubject()` e o
  conteúdo das partes.
- Dependências: `jakarta.mail-api` 2.1.5 e `angus-mail` 2.0.5 (`email-service/pom.xml`).

## Diagnóstico

Feito em 2026-10-15 contra o LocalStack do Compose (`localstack/localstack:4`), antes de
qualquer mudança no código.

1. **O que o LocalStack devolve.** Template criado direto no SES do LocalStack (sem passar pelo
   email-service), com assunto `{{nome}}, sua conta está pronta`:
   ```bash
   docker compose exec localstack awslocal ses test-render-template \
     --template-name diag-accent-subject --template-data '{"nome":"Maria"}'
   ```
   Trecho do `RenderedTemplate`:
   ```
   Subject: Maria, sua conta está pronta
   Content-Type: multipart/alternative; boundary="===============6837394513318801677=="
   ...
   Content-Type: text/html; charset="utf-8"
   Content-Transfer-Encoding: base64

   PHA+T2zDoSBNYXJpYSwgdm9jw6ogasOhIHBvZGUgZW50cmFyLjwvcD4=
   ```
   O assunto vem em UTF-8 cru no cabeçalho, sem RFC 2047; o corpo vem em base64 com charset
   declarado. A saída do LocalStack já está certa em UTF-8, então o defeito não está nele.
2. **O que o Jakarta Mail faz com isso.** O mesmo `RenderedTemplate`, lido com
   `jakarta.mail-api` 2.1.5 + `angus-mail` 2.0.5 (as versões do `pom.xml`) num programa à parte:

   | Entrada | `mail.mime.allowutf8` | `getSubject()` |
   |---|---|---|
   | UTF-8 cru (LocalStack) | padrão (`false`) | `Maria, sua conta estÃ¡ pronta` |
   | UTF-8 cru (LocalStack) | `true` | `Maria, sua conta está pronta` |
   | RFC 2047 (`=?UTF-8?B?...?=`) | padrão (`false`) | `Maria, sua conta está pronta` |
   | RFC 2047 (`=?UTF-8?B?...?=`) | `true` | `Maria, sua conta está pronta` |

   O corpo HTML sai certo nos dois casos. Conclusão: o defeito está no parser, que lê bytes de 8
   bits em cabeçalho como ISO-8859-1 (o padrão do Jakarta Mail, fiel ao RFC 822). Com
   `mail.mime.allowutf8=true` (suporte ao RFC 6532) o cabeçalho é lido como UTF-8, e o RFC 2047
   continua funcionando.
3. **Teste automatizado.** O cenário novo (T001 de `tasks.md`) falha no `master` com
   `expected: "Ada, sua conta está pronta"` e `but was: "Ada, sua conta estÃ¡ pronta"`
   (bytes `c3 83 c2 a1` no lugar de `c3 a1`), contra o LocalStack de verdade.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde corrigir | No `MimeRenderedTemplateParser`. `SesTemplateClient` e `TemplateService` não mudam. | proposta | O defeito está na leitura do MIME (diagnóstico, item 2). O cliente do SES só repassa a `String`. |
| Como | A `Session` usada no parse ganha `mail.mime.allowutf8=true`. | proposta | Uma linha de configuração, suportada pelo Jakarta Mail desde a 1.6. Lê o UTF-8 cru do LocalStack e continua decodificando RFC 2047 (tabela do diagnóstico), então serve também se o SES de verdade codificar o assunto. |
| `getDefaultInstance` ou `getInstance` | Trocar por `Session.getInstance(props)`. | proposta | `getDefaultInstance` guarda a primeira `Session` criada na JVM e ignora as `Properties` das chamadas seguintes. Se qualquer outro código criar a sessão padrão antes, a nossa configuração some sem erro. `getInstance` cria uma sessão com as propriedades passadas; o custo é desprezível para uma chamada por pré-visualização. |
| Alternativas descartadas | (a) Recodificar o assunto de ISO-8859-1 para UTF-8 depois do `getSubject()`. (b) Separar o assunto do MIME na mão. (c) Pedir `mail.mime.allowutf8` como propriedade de sistema da JVM. | descartada | (a) corrompe um assunto que viesse de fato em ISO-8859-1 ou em RFC 2047 e esconde a causa. (b) é o parsing feito à mão que a decisão original (05-025) evitou de propósito. (c) muda o comportamento de todo uso do Jakarta Mail no processo, não só deste parser. |
| Teste | Cenário Cucumber em `register_templates.feature`, contra o LocalStack real, com assunto e corpo acentuados e comparação exata. Nenhum teste de unidade com MIME montado à mão. | proposta | Constitution, "Testes: preferir real a fake": o que importa é o formato que o SES (aqui, o LocalStack) devolve de verdade; um MIME escrito no teste poderia não bater com ele. O passo existente `the system returns the rendered subject and body` só confere `isNotBlank`, por isso não pegou o defeito. |
| SES de verdade | Não verificado. | aberta | Não se sabe se o `TestRenderTemplate` do SES de verdade devolve o assunto em UTF-8 cru ou em RFC 2047. A correção cobre os dois; a confirmação fica para quando houver acesso ao SES (Issue #104, cenários `@requires-real-ses`). |
| Exceções novas | Nenhuma. | resolvida | O `catch` existente do parser (`IllegalStateException`) não muda. |

## Estrutura de módulos/pacotes

- `email-service/src/main/java/.../template/MimeRenderedTemplateParser.java`: `Session` com
  `mail.mime.allowutf8=true`, via `Session.getInstance`; o Javadoc passa a citar o UTF-8 cru.
- `email-service/src/test/resources/features/register_templates.feature`: um cenário.
- `email-service/src/test/java/.../template/steps/RegisterTemplatesSteps.java`: passos
  `they already registered a template named {string} with subject {string} and body {string}`,
  `the rendered subject is {string}` e `the rendered body is {string}`.

## Verificações

1. Antes da correção: o cenário novo falha com o assunto `estÃ¡` (feito, ver diagnóstico).
2. Depois da correção: o cenário passa e o restante da suíte do email-service continua verde.
3. `mvn -pl app -am verify`: os testes do `app` que chamam a pré-visualização
   (`EmailServiceClientIntegrationTest`) continuam verdes. O template deles é em inglês, sem
   acento, então não mudam de resultado.
4. Verificação manual com o roteiro de `tasks.md`.

## Riscos e trade-offs

- **`allowutf8` também vale na escrita.** Com ela, o Jakarta Mail escreveria cabeçalhos em UTF-8
  cru em vez de RFC 2047. O parser só lê, e a `Session` é local a ele, então nada que envia
  e-mail é afetado.
