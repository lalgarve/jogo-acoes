# Plan: Proxy reverso de Client Hints para testar pelo Swagger UI

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`Sec-CH-UA*` são "forbidden request-header names" pro Fetch Standard (qualquer nome começando
com `Sec-`/`Proxy-`) — nenhum script de página, de nenhum Swagger UI, em nenhum navegador,
consegue mandá-los. Confirmado também que `LoginController` (spec 05-009) nem lê os parâmetros
que recebe — quem lê o header de verdade é `LoginLinkSessionService`, direto do
`HttpServletRequest` — então o que importa é o header HTTP bruto que chega na chamada de saída
do proxy, não um valor de parâmetro/corpo.

`docs/openapi.yaml` declara `servers: [{url: /api}]` (`docs/openapi.yaml:10-11`) — caminho
relativo, não um host fixo. Isso é o que permite o truque de "só aponte o Swagger UI pro proxy":
o Swagger UI, carregado a partir do endereço do proxy, já chama de volta o próprio proxy (mesma
origem de onde foi carregado) sem precisar reconfigurar nada no contrato.

`app/` já é um módulo Spring Boot completo, `email-lambda/` é o segundo módulo (Quarkus) — o
`pom.xml` da raiz só agrega, cada módulo mantém seu próprio parent/BOM (`README.md`, seção
"Módulos"). `blackbox-proxy/` seria o terceiro, mesmo padrão de agregação.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Aplicação separada ou endpoint dentro do `app/`? | Aplicação Spring Boot separada (`blackbox-proxy/`), porta própria | resolvida | Pedido explícito do usuário — corrige a primeira tentativa desta spec (endpoint dentro do `app/`, JSON por chamada). |
| Como configurar os headers | Endpoint de controle próprio, `POST /blackbox/proxy/headers`, guarda em memória (não persiste) — nada a ver com o corpo/headers das chamadas efetivamente proxiadas | resolvida | Desacopla "escolher o dispositivo" de "usar a API" — configura uma vez, usa o Swagger UI normalmente depois, que era exatamente o problema da primeira tentativa. |
| Como o proxy decide o que encaminhar | Regra única: tudo que não é `POST /blackbox/proxy/headers` é encaminhado pra API real, sem lista de rotas permitidas | resolvida | Diferente da primeira tentativa (que restringia a duas rotas por cautela de superfície), aqui a transparência total é o requisito — o usuário quer navegar o Swagger inteiro através do proxy, não só duas operações. |
| Framework/mecanismo de proxy | `@RestController` com `@RequestMapping("/**")` capturando todo método, usando `RestClient` (Spring 6.1+, já disponível via `spring-boot-starter-web`, sem dependência nova) pra montar a chamada de saída | resolvida | Mais simples que adotar Spring Cloud Gateway (WebFlux/Netty, paradigma reativo diferente do resto do projeto) só pra um proxy de poucas linhas; Gateway fica registrado aqui como alternativa se o proxy algum dia precisar crescer (roteamento por regra, retries, etc.), não escolhida agora. |
| Headers hop-by-hop / `Content-Length` / `Host` | Nunca repassados como vieram — `RestClient` recalcula `Content-Length`, e `Connection`/`Transfer-Encoding`/`Keep-Alive`/`Host` são descartados da requisição de entrada antes de montar a de saída | resolvida | Erro clássico de proxy escrito à mão — copiar esses headers cegamente quebra a conexão (tamanho errado, `Host` da porta errada). Registrado aqui pra não esquecer na implementação. |
| Onde/como rodar | `mvn -pl blackbox-proxy -am spring-boot:run`, documentado no README — não entra em `docker-compose.yml`/`docker-compose.blackbox.yml` nesta v1 | resolvida (era decisão em aberto em `spec.md`) | Ferramenta de uso manual e ocasional (testar rótulo de dispositivo), não parte do pipeline automático — colocar em Docker Compose acrescentaria complexidade de rede (hostname de container vs. `localhost`) sem necessidade agora; documentado como possível próximo passo, não feito. |

## Estrutura de módulos/pacotes

```
blackbox-proxy/
  pom.xml                          # parent próprio (spring-boot-starter-parent), spring-boot-starter-web só
  src/main/java/.../blackboxproxy/
    BlackboxProxyApplication.java  # @SpringBootApplication
    DeviceHeaderStore.java         # estado em memória (AtomicReference), guarda os 4 valores atuais
    DeviceHeaderController.java    # POST /blackbox/proxy/headers -> DeviceHeaderStore
    ReverseProxyController.java    # @RequestMapping("/**") todo método -> encaminha pra TARGET_BASE_URL
  src/main/resources/application.yml
    # server.port: 8090 (padrão)
    # blackbox-proxy.target-base-url: http://localhost:8080 (padrão, sobrescrevível)
  src/test/java/.../blackboxproxy/
    ReverseProxyIntegrationTest.java
```

- Raiz `pom.xml` — acrescenta `<module>blackbox-proxy</module>`.
- `ReverseProxyController`: um único método (`@RequestMapping(value = "/**", method = {GET,
  POST, PUT, PATCH, DELETE})`) recebe `HttpServletRequest`, monta a chamada de saída (método +
  caminho + query string + corpo + headers de entrada menos os hop-by-hop) via `RestClient`,
  sobrescreve os quatro `Sec-CH-UA*` com o que `DeviceHeaderStore` tiver configurado (pulando os
  que estiverem `null`), executa, e devolve `ResponseEntity` com status/corpo/headers da
  resposta real (`Set-Cookie` incluso).
- `README.md` (raiz, modificado) — nova entrada na tabela de módulos, e um parágrafo em
  "Ambiente de testes blackbox" explicando o fluxo: subir `app/`, subir `blackbox-proxy/`
  (`mvn -pl blackbox-proxy -am spring-boot:run`), configurar o dispositivo uma vez
  (`POST http://localhost:8090/blackbox/proxy/headers`), abrir
  `http://localhost:8090/api/swagger-ui.html` em vez do endereço direto do `app/`.

## Riscos e trade-offs

- **Proxy sem restrição de rota é um relay HTTP completo pra API real** — diferente da primeira
  tentativa (restrita a duas rotas), aqui é intencional: é exatamente o que dá a transparência
  pedida. Contido pelo mesmo argumento de `BlackboxController`/`BlackboxDataSeeder`: nunca sobe
  fora de um ambiente de teste descartável, nunca em Docker Compose nesta v1, nunca com
  credenciais reais por trás.
- **Encaminhar corpo binário/grande sem streaming** (lê tudo em memória via `RestClient` antes
  de reenviar) — aceitável pro volume de teste manual; viraria problema real só num cenário de
  upload grande, que não existe hoje no contrato.
- **Nenhuma validação do formato dos valores de `Sec-CH-UA*` recebidos em
  `POST /blackbox/proxy/headers`** — se vier um valor mal formado, o proxy manda do jeito que
  recebeu, e `DeviceLabelResolver` do lado do `app/` que decide o que fazer com isso (já tem
  fallback pra `"unknown-device"`); não duplicar essa validação aqui.
