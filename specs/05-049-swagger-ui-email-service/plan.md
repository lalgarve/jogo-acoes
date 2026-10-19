# Plan: Swagger UI no Serviço de E-mail

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- O `app` já serve o Swagger UI do contrato estático `docs/openapi.yaml`:
  - `springdoc-openapi-starter-webmvc-ui` 3.1.1 (`app/pom.xml`), compatível com o Spring Boot
    4.1 que o `email-service` também usa;
  - `maven-resources-plugin` (execução `copy-openapi-contract-for-swagger-ui`) copia o contrato
    para `target/classes/static/` no `generate-resources`;
  - `application.yml`: `springdoc.packages-to-scan` aponta para um pacote que não existe (sem
    contrato gerado pelas anotações, sem desligar o bean de que a tela depende) e
    `springdoc.swagger-ui.url: ${server.servlet.context-path}/openapi.yaml`. Os comentários de lá
    registram as duas armadilhas encontradas rodando: `api-docs.enabled: false` derruba a tela,
    e uma URL com `/` no começo ignora o `context-path`;
  - `SwaggerUiSecurityConfigContributor` libera `/openapi.yaml`, `/swagger-ui/**`,
    `/swagger-ui.html` e `/v3/api-docs/**` (a tela busca `/v3/api-docs/swagger-config` ao abrir).
- O `email-service` não usa Spring Security. A autenticação é o `ApiKeyAuthenticationFilter`
  (`OncePerRequestFilter`, spec 05-030), que responde `401` em **toda** requisição sem chave
  válida, e a autorização é o `PermissionInterceptor` (05-048), que só exige permissão das rotas
  mapeadas (`/emails`, `/templates/**`, `/disposable-domain-*`) e deixa passar o resto.
- O contrato `docs/openapi-email-service.yaml` já declara `servers: [{url: /api}]` e o esquema
  `ApiKeyAuth` (cabeçalho `X-API-Key`) como segurança global.
- `email-service/Dockerfile` já copia `docs/` para o build (o gerador de código lê o contrato),
  então a cópia do contrato também funciona na imagem.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Biblioteca | `springdoc-openapi-starter-webmvc-ui` 3.1.1, a mesma do `app` | proposta | Já validada no projeto com o Spring Boot 4.1. |
| Contrato servido | `maven-resources-plugin` copia `../docs/openapi-email-service.yaml` para `static/` com o nome `openapi.yaml` (`copy-resources` com renomeação pelo `maven-antrun-plugin` ou `copy-rename`; conferir na implementação o jeito mais simples) | proposta | Mesmo arquivo que o gerador de código lê: o contrato da tela nunca diverge do implementado. Nome `openapi.yaml` igual ao do `app`. |
| Configuração do springdoc | Bloco `springdoc` no `application.yml` do serviço, igual ao do `app`: `packages-to-scan: dev.leilaalgarve.jogoacoes.emailservice.__no_annotation_scanning__`, `swagger-ui.url: ${server.servlet.context-path}/openapi.yaml`, `swagger-ui.persist-authorization: true` | proposta | As duas primeiras pelos motivos registrados no `app`. `persist-authorization` guarda a chave no navegador entre recargas (requisito da chave na tela). |
| Rotas abertas | `ApiKeyAuthenticationFilter.shouldNotFilter`: `true` para o `servletPath` igual a `/openapi.yaml` ou `/swagger-ui.html`, ou começando com `/swagger-ui/` ou `/v3/api-docs` | proposta | O filtro é o único ponto que barra; o `PermissionInterceptor` já deixa passar rota sem permissão mapeada. Lista fechada de caminhos, em vez de "tudo que não é API", para nenhuma rota nova nascer aberta por engano. |
| Ambientes | Só `docker`: `springdoc.swagger-ui.enabled: false` e `springdoc.api-docs.enabled: false` em `config-repo/email-service-staging.yml` e `-production.yml`; o filtro continua liberando os caminhos (sem a tela e sem o contrato, eles respondem `404`) | resolvida (Leila, 2026-10-19) | Fora do ambiente de teste, nem a tela nem o contrato ficam abertos. |
| Teste | `SwaggerUiAccessTest` (JUnit, contexto Spring na porta aleatória, RestAssured, mesma infraestrutura dos testes do serviço): `GET /api/swagger-ui.html` e `GET /api/openapi.yaml` sem chave → `200` (o YAML com `title: Serviço de E-mail`); `GET /api/v3/api-docs/swagger-config` sem chave → `200`; `GET /api/templates` sem chave → `401`; `GET /api/v3/api-docs` sem chave → sem nenhuma rota do serviço (nada gerado pelas anotações) | proposta | Cobre os três cenários de `spec.md`. Constitution, "Testes: preferir real a fake": servidor de verdade, sem mock do filtro. |

## Estrutura de módulos/pacotes

- `email-service/pom.xml`: dependência do springdoc e a cópia do contrato.
- `email-service/src/main/resources/application.yml`: bloco `springdoc`.
- `email-service/src/main/java/.../auth/ApiKeyAuthenticationFilter.java`: `shouldNotFilter`.
- `email-service/src/test/java/.../common/SwaggerUiAccessTest.java`.
- `config-repo/email-service-staging.yml`, `-production.yml`: Swagger UI e contrato desligados.
- `README.md`: endereço do Swagger UI do `email-service` ao lado do do `app`.
- `docs/disciplina/caderno-de-testes-email-service.md`: os passos de chamada à API passam para o
  Swagger UI; `chamar.sh` sai do caderno (ou fica como alternativa, ver "Riscos").

## Verificações (antes das correções, falhando)

1. `SwaggerUiAccessTest`: hoje `401` na tela e no contrato (o filtro barra tudo) — registrar.
2. `ApiKeyIntegrationVerificationTest` e os `.feature` continuam verdes depois da mudança
   (nenhuma rota da API ficou aberta).
3. `ArchitectureTest` continua verde.

## Riscos e trade-offs

- **Rota aberta por engano.** Um caminho novo que comece com `/swagger-ui/` ou `/v3/api-docs`
  ficaria sem chave. Aceitável: são prefixos do springdoc, nenhuma rota do serviço usa.
- **Ids entre passos no caderno.** No Swagger UI, o id de um repositório ou de uma execução é
  copiado à mão da resposta para o passo seguinte, como o id de competição no caderno do
  `jogo-acoes`. O `esperar-importacao.sh` continua como atalho para a espera da importação.
- **Exceções sem teste possível:** nenhuma prevista; a mudança não cria classe de exceção.
