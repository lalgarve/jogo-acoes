# Plan: email-service recusa template com nome vazio ou em branco

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- O email-service é contract-first: `openapi-generator-maven-plugin` (gerador `spring`,
  `interfaceOnly`) gera `TemplatesApi` e `EmailTemplateCreateRequest` a partir de
  `docs/openapi-email-service.yaml`. `TemplateController` implementa a interface gerada, que já
  traz `@Valid` no corpo da requisição.
- As restrições do schema viram anotações Bean Validation no DTO gerado: `required` → `@NotNull`
  (é o que dá o 400 de hoje para `name: null`), `minLength` → `@Size(min = …)`,
  `pattern` → `@Pattern(regexp = …)`.
- `ApiExceptionHandler.handleInvalidRequest` já converte `MethodArgumentNotValidException` em 400
  com `Error.message` no formato `<campo> <mensagem>`.
- `TemplateService.create` só é chamado se a validação passar, então nada chega ao banco nem ao
  SES (`SesTemplateClient.createTemplate`) quando o nome é recusado.
- O `app` gera o cliente Feign do mesmo arquivo; o DTO dele ganha as mesmas anotações, sem efeito
  no envio (o Feign não valida o corpo).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde validar | No contrato: `EmailTemplateCreateRequest.name` ganha `minLength: 1` e `pattern: '^[\s\S]*\S[\s\S]*$'`. Nenhuma linha de Java escrita à mão para a regra. | proposta | Contract-first: a regra fica documentada para qualquer cliente e o gerador produz a validação. Validar no `TemplateService` deixaria o contrato dizendo "qualquer string". |
| Forma do `pattern` | `^[\s\S]*\S[\s\S]*$` (ao menos um caractere que não seja espaço em branco, em qualquer posição). | proposta | No JSON Schema/OpenAPI o `pattern` é ECMA-262 e não ancorado; no Java o `@Pattern` usa `matches()`, que exige casar a string inteira. Com `^…$` e `[\s\S]` (em vez de `.`, que no Java não casa quebra de linha) as duas leituras dão o mesmo resultado. Um `\S` simples aceitaria `" a "` no schema e recusaria no Java. |
| `minLength: 1` além do `pattern` | Sim. | proposta | O `pattern` já recusa `""`, mas `minLength` deixa a restrição legível no contrato e na documentação gerada. Para `""` as duas anotações falham e a mensagem traz as duas (o handler junta com `; `), o que é aceitável. |
| Mensagem do 400 | A padrão do Bean Validation, montada pelo handler existente: `name must match "^[\s\S]*\S[\s\S]*$"` (e, para `""`, também `name size must be between 1 and …`). | proposta | Cumpre o critério (400, corpo `Error`, cita `name`) sem código novo. Uma mensagem mais amigável (`name must not be blank`) exigiria `x-field-extra-annotation` com `@NotBlank` ou validação manual; não vale o código para um erro de programação do cliente. |
| Cenário Cucumber para o nome vazio | Passo novo `When they register a template named "" …` reutiliza o passo existente com `{string}` (o Cucumber aceita `""`). Para o nome só com espaços, `" "`. Em vez de `Scenario Outline` com célula vazia (ambígua na tabela), dois `Scenario` explícitos. | proposta | Legibilidade: célula vazia ou com espaço numa tabela Gherkin é fácil de ler errado. |
| "Nada criado" | Passo novo `Then no template with that name is created`, que confere `GET /templates` sem o nome e o SES (LocalStack) sem `<cliente>__<nome>`, reaproveitando o que o passo `no template named {string} is created` já faz. | proposta | O passo existente já cobre banco e SES; generalizar evita duplicar. |
| Exceções novas | Nenhuma. | resolvida | O caminho de erro é o `MethodArgumentNotValidException` já tratado. |

## Estrutura de módulos/pacotes

- `docs/openapi-email-service.yaml`: `EmailTemplateCreateRequest.name`.
- `email-service/src/test/resources/features/register_templates.feature`: dois cenários.
- `email-service/src/test/java/.../template/steps/RegisterTemplatesSteps.java`: passo
  `Then the system rejects the registration because the name is blank` e, se preciso, o passo
  genérico de "nada criado".

Nenhuma mudança em `TemplateService`, `TemplateController` ou `ApiExceptionHandler`.

## Verificações (antes das correções, falhando)

1. Os dois cenários Cucumber (nome `""` e nome `" "`) falham no `master` com 201 em vez de 400.
2. Depois da mudança no contrato, passam, e o restante de `register_templates.feature` continua
   verde.
3. `mvn -pl app -am verify` continua verde (cliente Feign regenerado).

## Riscos e trade-offs

- **Mensagem de erro técnica.** O cliente recebe o regex na mensagem. Aceito: o `app` nunca manda
  nome em branco (05-036, D1) e outro cliente só vê isso por erro de programação.
- **Regra só sobre espaço em branco.** Nomes que o SES recusa por outros caracteres continuam
  chegando ao SES e voltando como 422 (decisão em aberto 1 da `spec.md`).
