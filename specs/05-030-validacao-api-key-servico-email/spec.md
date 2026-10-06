# Spec: Serviço de E-mail — validação real de API-KEY

**Status:** implementada
**Issue:** [#108](https://github.com/lalgarve/jogo-acoes/issues/108)
**Iteração:** iteration-5

## Resumo

O Serviço de E-mail (`email-service`, spec
[05-025](../05-025-servico-email-templates/spec.md)) deixa de aceitar qualquer valor não vazio
em `X-API-Key` e passa a validar a chave de verdade com a biblioteca `api-key-validation` do
projeto [`lalgarve/api-key`](https://github.com/lalgarve/api-key) (release
[v1.0.1](https://github.com/lalgarve/api-key/releases/tag/v1.0.1), spec
[008-validate-api-key](https://github.com/lalgarve/api-key/blob/main/specs/008-validate-api-key/spec.md),
Issue [lalgarve/api-key#23](https://github.com/lalgarve/api-key/issues/23)). O dono dos
templates passa a ser o cliente para o qual a chave foi emitida (`--client` da CLI), não mais
o texto bruto da chave.

## Motivação

A spec 05-025 deixou a autenticação como esqueleto de propósito: a biblioteca de validação
ainda não existia, e a decisão de onde ela ficaria foi adiada para "a spec que substituir o
esqueleto atual" (05-025, "Decisões resolvidas e adiadas"). Esta é essa spec — a biblioteca
agora tem release.

O esqueleto tem dois problemas que só a validação real resolve:

- Qualquer string passa: não há como restringir quem chama o serviço, nem revogar o acesso de
  um cliente.
- O texto da chave vira o identificador do cliente. Trocar a chave (rotação) faz o cliente
  "perder" os próprios templates, e a chave em texto puro acaba gravada no banco (coluna de
  dono do template) e no nome do template no SES (`<cliente>__<nome>`).

## Cenários (comportamento esperado)

- `email-service/src/test/resources/features/register_templates.feature` — a Rule "Every
  request requires an API key" ganha cenários para chave malformada, desconhecida, expirada e
  revogada (todas rejeitadas como não autorizadas); os passos `a client authenticated with the
  API key "client-a"` passam a usar uma chave real emitida para o cliente `client-a`. A ser
  escrito antes do código (ver `tasks.md`).

## Requisitos funcionais

- Toda rota continua exigindo o header `X-API-Key` (mesmo contrato HTTP de
  [`docs/openapi-email-service.yaml`](../../docs/openapi-email-service.yaml)).
- A chave recebida é validada por `ApiKeyValidator.validate`: formato, existência (hash
  HMAC-SHA256 com o pepper do ambiente), revogação e expiração — mesma ordem e mesmos motivos
  do contrato da biblioteca (`MISSING`, `MALFORMED`, `NOT_FOUND`, `REVOKED`, `EXPIRED`).
- Qualquer motivo de rejeição responde `401` com o mesmo corpo genérico já usado hoje
  (`Missing or invalid X-API-Key`) — o chamador não descobre se a chave existiu um dia. O motivo
  exato vai só para o log do serviço, nunca a chave em texto puro.
- Chave válida: o cliente dono dos templates é o `clientName` devolvido pela validação. Duas
  chaves ativas do mesmo cliente (ex.: durante uma rotação) enxergam os mesmos templates.
- O nome do template no SES continua `<cliente>__<nome>`, agora com o `clientName`.
- As chaves são emitidas, revogadas e listadas pela CLI do `api-key`, rodando contra o banco do
  Serviço de E-mail — este serviço só lê.
- Existe uma API-KEY de teste fixa (cliente `jogo-acoes`) para os ambientes `docker` e
  `sandbox`, restaurável num banco novo a partir de um dump versionado — ver
  [`docker/postgres-email-service/test-data/README.md`](../../docker/postgres-email-service/test-data/README.md).
  Já entregue junto com esta spec, antes da implementação, junto com a variável
  `EMAIL_SERVICE_API_KEY` do serviço `app` em `docker-compose.yml` (ainda não lida por
  ninguém).

## Requisitos não-funcionais

- A troca fica contida no pacote `auth` do `email-service` (05-025, "Requisitos
  não-funcionais": a resolução chave → cliente é o único ponto de troca). Nenhum outro pacote
  depende da biblioteca `api-key`.
- O pepper do HMAC (`API_KEY_HMAC_PEPPER`) é o mesmo usado pela CLI que emitiu as chaves. Em
  `docker`/`sandbox`, o valor de teste versionado; em `staging`/`production`, vem do gerenciador
  de segredos do ambiente, nunca do repositório.
- Os testes rodam contra PostgreSQL real (5433) e LocalStack, como hoje (spec 05-028) — sem
  mock do validador.

## Fora de escopo

- `app/` chamar o Serviço de E-mail (envio de e-mail, cadastro de templates a partir do
  `jogo-acoes`) — fica para a spec de envio. A variável `EMAIL_SERVICE_API_KEY` já existe no
  `docker-compose.yml`, mas nada a lê ainda.
- Autorização por cliente além do isolamento de templates que já existe (ex.: cliente só pode
  usar certas rotas).
- Limite de requisições por cliente.
- Migrar templates já cadastrados com o dono antigo (texto bruto da chave): o sistema está em
  pré-produção (`memory/constitution.md`, "Status do sistema"), esses registros podem ser
  descartados.
- Qualquer mudança no projeto `api-key` — a release v1.0.1 é usada como está.

## Decisões em aberto

- Nenhuma de requisito. A forma de distribuir a biblioteca para o build do `jogo-acoes` é
  decisão técnica, em `plan.md`.
