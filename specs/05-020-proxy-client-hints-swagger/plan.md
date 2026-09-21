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

`User-Agent` entra na mesma lista de headers sobrescritos pelo proxy (pedido do usuário — "alguns
testes precisam disso"): mesmo mecanismo dos `Sec-CH-UA*` resolve, sem precisar decidir se o
navegador bloquearia especificamente esse header via `fetch`/XHR — o proxy sobrescreve de
qualquer forma, então a pergunta fica irrelevante na prática.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Aplicação separada ou endpoint dentro do `app/`? | Aplicação Spring Boot separada (`blackbox-proxy/`), porta própria | resolvida | Pedido explícito do usuário — corrige a primeira tentativa desta spec (endpoint dentro do `app/`, JSON por chamada). |
| Como configurar/conferir os headers | Endpoint de controle próprio, `/blackbox/proxy/headers`, com dois métodos: `POST` escreve (substitui por inteiro), `GET` só lê a configuração corrente — guarda em memória, nada a ver com o corpo/headers das chamadas efetivamente proxiadas | resolvida | Desacopla "escolher o dispositivo" de "usar a API" — configura uma vez, usa o Swagger UI normalmente depois, que era exatamente o problema da primeira tentativa. Dois métodos (não só `POST`) porque leitura e escrita são operações com contratos diferentes (`GET` sem corpo/idempotente, `POST` com corpo/substitui estado) — pedido explícito do usuário. |
| Como o proxy decide o que encaminhar | Regra única: tudo que não é `/blackbox/proxy/headers` (nem `GET` nem `POST`) é encaminhado pra API real, sem lista de rotas permitidas | resolvida | Diferente da primeira tentativa (que restringia a duas rotas por cautela de superfície), aqui a transparência total é o requisito — o usuário quer navegar o Swagger inteiro através do proxy, não só duas operações. |
| Framework/mecanismo de proxy | `@RestController` com `@RequestMapping("/**")` capturando todo método, usando `RestClient` (Spring 6.1+, já disponível via `spring-boot-starter-web`, sem dependência nova) pra montar a chamada de saída | resolvida | Mais simples que adotar Spring Cloud Gateway (WebFlux/Netty, paradigma reativo diferente do resto do projeto) só pra um proxy de poucas linhas; Gateway fica registrado aqui como alternativa se o proxy algum dia precisar crescer (roteamento por regra, retries, etc.), não escolhida agora. |
| Headers hop-by-hop / `Content-Length` / `Host` | Nunca repassados como vieram — `RestClient` recalcula `Content-Length`, e `Connection`/`Transfer-Encoding`/`Keep-Alive`/`Host` são descartados da requisição de entrada antes de montar a de saída | resolvida | Erro clássico de proxy escrito à mão — copiar esses headers cegamente quebra a conexão (tamanho errado, `Host` da porta errada). Registrado aqui pra não esquecer na implementação. |
| Onde/como rodar | Script `scripts/blackbox-proxy.sh` chamando `mvn -pl blackbox-proxy -am spring-boot:run` — não entra em `docker-compose.yml`/`docker-compose.blackbox.yml` nesta v1 | resolvida (era decisão em aberto em `spec.md`) | Ferramenta de uso manual e ocasional (testar rótulo de dispositivo), não parte do pipeline automático — colocar em Docker Compose acrescentaria complexidade de rede (hostname de container vs. `localhost`) sem necessidade agora; documentado como possível próximo passo, não feito. |
| Como suportar vários dispositivos ao mesmo tempo | Não dentro de uma instância (uma configuração corrente só) — várias instâncias, cada uma sua porta, via `scripts/blackbox-proxy.sh` com `--proxy-port`/`--target-url`/`--target-port` sobrescritos | resolvida | Pedido explícito do usuário ("se o usuário quiser rodar 4 instâncias, tudo bem"); mais simples que dar à mesma instância um conceito de "sessão"/"aba" pra guardar mais de uma configuração — cada instância já é isolada (seu próprio `DeviceHeaderStore` em memória) de graça. |
| O que "não configurado" significa na chamada de saída | Header **removido**, nunca "deixa passar o que o navegador mandou" — e cada `POST /blackbox/proxy/headers` substitui a configuração inteira (sem merge com a chamada anterior) | resolvida | Correção de um desenho anterior desta spec, que dizia "os que não foram configurados não são tocados" — na prática isso deixaria vazar o `Sec-CH-UA` que o próprio navegador já tivesse negociado via `Accept-CH`, ou o `User-Agent` real, exatamente o oposto de "testar em branco" (feedback do usuário). Sem esse cuidado, não dá pra testar de propósito o fallback de `DeviceLabelResolver` pra `"unknown-device"`. |

## Estrutura de módulos/pacotes

```
blackbox-proxy/
  pom.xml                          # parent próprio (spring-boot-starter-parent), spring-boot-starter-web só
  src/main/java/.../blackboxproxy/
    BlackboxProxyApplication.java  # @SpringBootApplication
    DeviceHeaderStore.java         # estado em memória (AtomicReference), guarda os 5 valores atuais
    DeviceHeaderController.java    # GET/POST /blackbox/proxy/headers -> lê/escreve DeviceHeaderStore
    ReverseProxyController.java    # @RequestMapping("/**") todo método -> encaminha pra TARGET_BASE_URL
  src/main/resources/application.yml
    # server.port: 8090 (padrão)
    # blackbox-proxy.target-base-url: http://localhost:8080 (padrão, sobrescrevível)
  src/test/java/.../blackboxproxy/
    ReverseProxyIntegrationTest.java
scripts/
  blackbox-proxy.sh                # novo -- ver abaixo
```

- Raiz `pom.xml` — acrescenta `<module>blackbox-proxy</module>`.
- `DeviceHeaderStore`: cinco campos opcionais (`secChUa`, `secChUaPlatform`,
  `secChUaPlatformVersion`, `secChUaMobile`, `userAgent`), mesmo tratamento pros cinco — `null` =
  header removido da chamada de saída, nunca repassa o que o navegador mandou. `set(...)`
  substitui os cinco campos por inteiro a cada chamada (não faz merge com o estado anterior —
  reflete a decisão de `spec.md` de que cada `POST /blackbox/proxy/headers` descreve o
  dispositivo completo, do zero); `get()` devolve o estado corrente, sem efeito colateral, pro
  `GET`.
- `DeviceHeaderController`: mesmo record (`DeviceHeaders`, os cinco campos) como corpo do `POST`
  e corpo da resposta do `GET` — um schema só pros dois métodos, sem duplicar campo.
- `ReverseProxyController`: um único método (`@RequestMapping(value = "/**", method = {GET,
  POST, PUT, PATCH, DELETE})`) recebe `HttpServletRequest`, monta a chamada de saída (método +
  caminho + query string + corpo) via `RestClient`, copiando os headers de entrada **exceto** os
  cinco controlados pelo proxy e os hop-by-hop — nunca copia-e-depois-sobrescreve, pra não
  arriscar um valor do navegador vazar por um esquecimento de ordem; os cinco são adicionados
  separadamente, só os que `DeviceHeaderStore` tiver com valor não-`null`. Executa e devolve
  `ResponseEntity` com status/corpo/headers da resposta real (`Set-Cookie` incluso).
- `scripts/blackbox-proxy.sh` (novo, mesmo estilo de `scripts/blackbox-clock-offset.sh`) —
  três opções, todas com padrão, nenhuma obrigatória:
  ```
  ./scripts/blackbox-proxy.sh [--target-url http://localhost] [--target-port 8080] [--proxy-port 8090]
  ```
  Monta `TARGET_BASE_URL="${target-url}:${target-port}"` e roda
  `SERVER_PORT="$proxy_port" BLACKBOX_PROXY_TARGET_BASE_URL="$TARGET_BASE_URL" \
  mvn -pl blackbox-proxy -am spring-boot:run` (relaxed binding do Spring Boot já resolve as
  duas variáveis de ambiente pras propriedades `server.port`/`blackbox-proxy.target-base-url`,
  sem precisar de `-D`/`--spring-boot.run.arguments`). Rodar o script de novo, com portas
  diferentes, sobe outra instância independente — cada processo Maven é isolado por natureza,
  nenhuma mudança extra necessária no código pra suportar isso.
- `README.md` (raiz, modificado) — nova entrada na tabela de módulos, e um parágrafo em
  "Ambiente de testes blackbox" explicando o fluxo: subir `app/`, subir uma instância do proxy
  (`./scripts/blackbox-proxy.sh`), configurar o dispositivo uma vez
  (`POST http://localhost:8090/blackbox/proxy/headers`), abrir
  `http://localhost:8090/api/swagger-ui.html` em vez do endereço direto do `app/` — e, pra mais
  de um dispositivo ao mesmo tempo, rodar o script de novo com `--proxy-port` diferente.

## Riscos e trade-offs

- **Proxy sem restrição de rota é um relay HTTP completo pra API real** — diferente da primeira
  tentativa (restrita a duas rotas), aqui é intencional: é exatamente o que dá a transparência
  pedida. Contido pelo mesmo argumento de `BlackboxController`/`BlackboxDataSeeder`: nunca sobe
  fora de um ambiente de teste descartável, nunca em Docker Compose nesta v1, nunca com
  credenciais reais por trás.
- **Encaminhar corpo binário/grande sem streaming** (lê tudo em memória via `RestClient` antes
  de reenviar) — aceitável pro volume de teste manual; viraria problema real só num cenário de
  upload grande, que não existe hoje no contrato.
- **Nenhuma validação do formato dos valores recebidos em `POST /blackbox/proxy/headers`** — se
  vier um `Sec-CH-UA*`/`User-Agent` mal formado, o proxy manda do jeito que recebeu, e
  `DeviceLabelResolver` do lado do `app/` que decide o que fazer com isso (já tem fallback pra
  `"unknown-device"`); não duplicar essa validação aqui.
- **`scripts/blackbox-proxy.sh` não confere se a porta pedida já está em uso** — rodar duas
  instâncias com o mesmo `--proxy-port` por engano falha só quando o Spring Boot tentar subir
  (erro de bind de porta já claro o bastante); não vale a pena checar antes.
- **`User-Agent` em branco não sai literalmente ausente** (achado na implementação, confirmado
  por teste): diferente de `Sec-CH-UA*` (headers HTTP comuns, plenamente removíveis pelo
  `RestClient`/`java.net.http.HttpClient` de saída), o `User-Agent` é um dos poucos headers que
  o próprio `java.net.http.HttpClient` da JDK sempre preenche com um valor próprio
  (`"Java-http-client/<versão>"`) quando a aplicação não define um — não existe API pública pra
  suprimir isso por completo, mesma limitação prática que navegadores têm com esse header
  específico. A garantia que continua valendo (e é a que importa): o valor real de quem chamou o
  proxy nunca vaza — só nunca é *ausência total*, quando em branco vira o `User-Agent` do
  próprio processo Java, não `"unknown-device"` direto. `DeviceLabelResolverTest` já cobre esse
  caminho: `User-Agent` presente (mesmo que genérico) vira rótulo a partir dele, não o fallback
  final.
