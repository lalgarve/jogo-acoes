# Spec: email-service recusa template com nome vazio ou em branco

**Status:** implementada
**Issue:** [#133](https://github.com/lalgarve/jogo-acoes/issues/133)
**Iteração:** iteration-5

## Resumo

`POST /templates` do email-service passa a recusar `name` vazio (`""`) ou só com espaços
(`" "`, `"\t"`), respondendo 400 com corpo `Error` e sem criar nada, nem no banco nem no SES. O
contrato `docs/openapi-email-service.yaml` passa a documentar essa restrição.

## Motivação

Hoje o email-service aceita o nome vazio e cria o template (observado em 2026-10-06 contra o
email-service local, perfil `docker`, chave de teste do cliente `jogo-acoes`, registrado na
spec 05-036):

```
POST /api/templates  {"name":"","subject":"s","body":"b"}
→ 201 {"name":"","subject":"s","body":"b",...}
```

`name: null` já é recusado com 400 (`name must not be null`), porque o campo é `required` no
contrato e o gerador põe `@NotNull` no DTO. Não há regra nenhuma para o conteúdo do nome.

Um template de nome vazio fica preso na lista do cliente: `GET /templates/` e `PUT /templates/`
não casam com nenhuma rota (404 do Spring) e não existe `DELETE`. O SES do LocalStack também
fica com um template `jogo-acoes__` que ninguém consegue alterar pela API.

Do lado do `app`, a spec 05-036 (D1) já faz o `EmailServiceGateway` recusar nome em branco antes
de chamar o email-service. Mas o email-service é um serviço com contrato próprio
(constitution, "Fronteira entre módulos e serviços"): a regra tem que estar nele, para qualquer
cliente.

## Requisitos funcionais

- `POST /templates` com `name` vazio ou só com caracteres de espaço em branco responde **400**
  com corpo `Error`, cuja mensagem cita o campo `name`.
- Nesse caso nada é criado: nenhuma linha em `email_service.email_template` e nenhuma chamada ao
  SES (`CreateTemplate`).
- Nomes com espaço no meio ou nas pontas, mas com algum caractere visível (ex.: `" welcome"`),
  continuam aceitos como hoje. Restringir os caracteres permitidos está fora de escopo (ver
  "Decisões em aberto").
- `name: null` continua 400, como hoje.
- `docs/openapi-email-service.yaml` documenta a restrição no `EmailTemplateCreateRequest.name`
  (`minLength: 1` e um `pattern` que exige ao menos um caractere que não seja espaço em branco).

## Critérios de aceite

Cenários novos na feature `register_templates.feature` do email-service (Gherkin em inglês,
constitution, "Idioma"), na regra "A client registers a new template":

```gherkin
Scenario: Client registers a template with an empty name
  When they register a template named "" with subject "Welcome!" and a valid body
  Then the system rejects the registration because the name is blank
  And no template with that name is created

Scenario: Client registers a template with a whitespace-only name
  When they register a template named " " with subject "Welcome!" and a valid body
  Then the system rejects the registration because the name is blank
  And no template with that name is created
```

- Os dois cenários falham no `master` atual (201, template criado) e passam depois da correção.
- "Rejects because the name is blank" = 400 com corpo `Error` cuja mensagem começa por `name`.
- "No template with that name is created" = `GET /templates` não lista o nome e o template
  namespaced não existe no SES.
- A suíte do email-service e a do `app` continuam verdes (o `app` gera o cliente Feign do mesmo
  contrato).

## Fora de escopo

- `PUT /templates/{name}` e `POST /templates/{name}/preview`: o nome vem do caminho, e o nome
  vazio nem chega ao controller (404 do Spring). Um nome só com espaços (`/templates/%20`) já
  responde 404 `No template named ' '`, porque não há como criar esse template depois desta
  correção.
- Remover templates de nome vazio que já existam em bancos locais: o sistema está em
  pré-produção; quem tiver um basta recriar o ambiente (`docker compose down -v`).
- `DELETE /templates/{name}`.
- Mudar o `EmailServiceGateway` do `app` (já coberto pela 05-036).

## Decisões em aberto

1. **Restringir os caracteres do nome.** O nome vira parte do nome do template no SES
   (`<cliente>__<nome>`), e o SES só aceita letras, números, `_` e `-`, até 64 caracteres. Um
   nome como `"boas vindas"` ou `"a/b"` passa por esta spec e é recusado pelo SES (422) ou fica
   inacessível pela rota com `{name}`. **Recomendação:** deixar fora desta spec, que corrige só o
   que a Issue #133 descreve, e abrir uma Issue própria se a Leila quiser a regra mais estrita.
