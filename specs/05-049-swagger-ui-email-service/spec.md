# Spec: Swagger UI no Serviço de E-mail

**Status:** rascunho
**Issue:** [#188](https://github.com/lalgarve/jogo-acoes/issues/188)
**Iteração:** iteration-5

## Resumo

O `email-service` passa a servir um Swagger UI em `http://localhost:8082/api/swagger-ui.html`,
como o `app` já faz. A tela mostra o contrato `docs/openapi-email-service.yaml` e permite chamar
as rotas informando a chave de API no botão **Authorize**.

## Motivação

- O [caderno de testes do Serviço de E-mail](../../docs/disciplina/caderno-de-testes-email-service.md)
  chama a API por um script (`chamar.sh`), porque o serviço não tem Swagger UI. O caderno do
  `jogo-acoes` usa o Swagger UI, e os dois cadernos ficariam no mesmo formato: JSON num bloco
  ` ```json `, colado na tela.
- Testar as permissões das chaves da spec
  [05-048](../05-048-importacao-listas-email-temporario/spec.md) fica mais visível: trocar de
  chave no **Authorize** e ver a mesma rota responder `200` com uma e `403` com a outra.
- O contrato fica navegável para quem integra um cliente novo, sem ler o YAML.

## Cenários (comportamento esperado)

Não há `.feature` novo: o Swagger UI é uma ferramenta de quem testa e integra, não um
comportamento da API de e-mail (mesmo caso do Swagger UI do `app`, que também não tem
`.feature`). O comportamento é coberto por um teste de integração (ver `plan.md`,
"Verificações"):

- a página do Swagger UI e o contrato abrem sem chave de API;
- a tela mostra as rotas do contrato `docs/openapi-email-service.yaml`, e não um contrato gerado
  a partir do código;
- as rotas da API continuam exigindo a chave (`401` sem chave) e a permissão (`403`), inclusive
  quando chamadas pelo Swagger UI.

## Requisitos funcionais

- Endereços, todos sob o `context-path` `/api` do serviço:
  - `/api/swagger-ui.html` (e `/api/swagger-ui/index.html`): a tela;
  - `/api/openapi.yaml`: o contrato, cópia de `docs/openapi-email-service.yaml` feita no build.
- A tela e o contrato abrem sem `X-API-Key`. Nenhuma outra rota fica aberta.
- O botão **Authorize** pede a `X-API-Key` (esquema `ApiKeyAuth` que o contrato já declara). As
  chamadas feitas pela tela levam a chave informada.
- A chave informada continua na tela depois de recarregar a página, até o **Logout** do
  **Authorize** (troca de chave no caderno sem colar de novo a cada recarga).
- O contrato servido é o próprio arquivo de `docs/`, nunca um contrato montado pelas anotações do
  código (contract-first, como no `app`).
- O caderno de testes do Serviço de E-mail passa a usar o Swagger UI nos passos de chamada à API.
  Os scripts que não são chamada à API continuam: gerar as chaves, publicar as listas no S3 do
  LocalStack e, como atalho, esperar a importação.

## Requisitos não-funcionais

- Nenhuma mudança no contrato além do que ele já tem (`servers: /api`, `ApiKeyAuth`).
- O Swagger UI não muda o tempo de resposta das rotas da API: a página e o contrato são arquivos
  estáticos.
- Abrir a tela sem chave não expõe nada além do contrato, que já é público no repositório.

## Fora de escopo

- Gerar chave de API pela tela: as chaves continuam vindo da CLI do `api-key`.
- Swagger UI da `email-lambda` (não tem API HTTP).
- Mudar o Swagger UI do `app`.
- Interface web própria para operações (listas de e-mail temporário).

## Decisões em aberto

- **Em quais ambientes o Swagger UI fica ligado.** O do `app` fica ligado em todos, inclusive
  `staging` e `production`. Opções para o `email-service`:
  1. igual ao `app`, em todos os ambientes;
  2. só no `docker`, desligado em `staging` e `production` pelo `config-repo`.

  Nas duas, as chamadas pela tela exigem a chave; a diferença é só se a tela e o contrato ficam
  abertos fora do ambiente de teste.
