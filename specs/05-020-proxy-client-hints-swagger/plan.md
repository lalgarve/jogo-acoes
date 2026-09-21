# Plan: Proxy de Client Hints para testar rótulo de dispositivo pelo Swagger UI

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`Sec-CH-UA*` são proibidos pro Fetch/XHR de uma página (Fetch Standard, "forbidden request-
header name": qualquer nome começando com `Sec-` ou `Proxy-`) — verificado contra a spec, não só
lembrado. Confirmado também que `LoginController.consumeLoginLink`/`completeRegistration`
(`app/src/main/java/dev/leilaalgarve/jogoacoes/login/LoginController.java:48-64`) nem chegam a
ler os parâmetros `secCHUA*` que recebem — só existem na assinatura pra aparecer no contrato;
quem lê os headers de verdade é `LoginLinkSessionService`, direto do `HttpServletRequest` da
chamada. Ou seja, o que importa pro rótulo final é o header HTTP bruto que chega na requisição
de fato — daí o proxy precisar fazer uma chamada de saída de verdade com esses headers, não só
repassar um valor de parâmetro.

`spring-boot-starter-web` (Spring Boot 4.1.0) já traz `RestClient` (Spring Framework 6.1+) sem
dependência nova — usado para a chamada de saída.

`BlackboxController` (`app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/`) é o precedente
direto: `@RestController` + `@Profile("blackbox")`, rota fora de `docs/openapi.yaml`, permitAll
via `BlackboxSecurityConfigContributor` (também `@Profile("blackbox")`), testado por
`@SpringBootTest` (`BlackboxProfileIntegrationTest`), sem `.feature` Cucumber — mesmo molde
seguido aqui.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Uma operação genérica (path+método arbitrários) ou uma operação de domínio (token+ação)? | Domínio: `{token, action, name?, secChUa*}` — `action` só `"consume"`\|`"register"` | resolvida | "Uma só operação" pedida é mais simples e segura como algo específico pro problema (testar rótulo de dispositivo) do que um proxy HTTP genérico — reduz a superfície mesmo confinado ao perfil `blackbox`. |
| Cliente HTTP de saída | `RestClient` (Spring 6.1+, já disponível) | resolvida | Nenhuma dependência nova; API fluente, mais simples que `RestTemplate` pra montar headers dinamicamente. |
| Base URL da chamada de saída | Loopback pra própria aplicação: `http://localhost:${server.port}${server.servlet.context-path}` | resolvida | O proxy só precisa falar com a própria API rodando no mesmo processo/host — nunca um destino externo. |
| Onde aparece no Swagger UI | Segundo grupo do springdoc (`springdoc.swagger-ui.urls`), só configurado em `application-blackbox.yml` — um mini-contrato próprio (`docs/openapi-blackbox.yaml`, novo arquivo, não gerado/escaneado) com só esta operação | resolvida | `docs/openapi.yaml` continua sendo só o contrato de produto (mesma regra já seguida por `GET /blackbox/last-email`); springdoc já suporta múltiplos grupos como dropdown na mesma página `swagger-ui.html`, sem precisar de uma segunda instância/porta. |
| Cookie de entrada/saída | Repassa `Cookie` da requisição recebida (se houver) pra chamada de saída (`RestClient` não segue cookies sozinho); repassa `Set-Cookie` da resposta de volta na resposta do proxy | resolvida | Sem isso, o "login" feito através do proxy não deixaria a sessão realmente estabelecida no navegador de quem está testando pelo Swagger UI. |
| Superfície restrita | Só monta `GET /login-links/{token}` (consume) ou `POST /login-links/{token}/registration` (register) — nunca um caminho vindo do corpo da requisição | resolvida | Evita virar um relay HTTP genérico (SSRF-like) mesmo estando limitado ao perfil `blackbox` — mesmo cuidado já registrado no javadoc de `BlackboxController` sobre a superfície que abre. |

## Estrutura de módulos/pacotes

- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/DeviceHeaderProxyController.java`
  (novo) — `@RestController @Profile("blackbox")`, `POST /blackbox/device-header-proxy`,
  recebe `DeviceHeaderProxyRequest` (record: `token`, `action`, `name` opcional, `secChUa`,
  `secChUaPlatform`, `secChUaPlatformVersion`, `secChUaMobile`, todos `String` opcionais exceto
  `token`/`action`), monta a chamada de saída via `RestClient`, devolve `ResponseEntity<String>`
  com o corpo bruto da resposta real e `Set-Cookie` repassado.
- `app/src/main/java/dev/leilaalgarve/jogoacoes/blackbox/BlackboxSecurityConfigContributor.java`
  (modificado) — acrescenta `/blackbox/device-header-proxy` à lista de `permitAll()`.
- `docs/openapi-blackbox.yaml` (novo) — mini-contrato com só a operação
  `POST /blackbox/device-header-proxy`, schema do corpo acima; nunca lido pelo cliente Python
  gerado (spec 05-015 só gera a partir de `docs/openapi.yaml`) nem pelo build do `app/`
  (`app/pom.xml` só copia `docs/openapi.yaml` pra `static/`) — só serve pro springdoc mostrar via
  Swagger UI.
- `app/src/main/resources/application-blackbox.yml` (modificado) — acrescenta:
  ```yaml
  springdoc:
    swagger-ui:
      urls:
        - name: "Jogo de Ações API"
          url: ${server.servlet.context-path}/openapi.yaml
        - name: "Blackbox: proxy de Client Hints"
          url: ${server.servlet.context-path}/openapi-blackbox.yaml
  ```
  (`docs/openapi-blackbox.yaml` copiado pro classpath do mesmo jeito que `docs/openapi.yaml` já
  é, via `app/pom.xml` — mesmo plugin/execução, arquivo a mais.)
- `app/src/test/java/dev/leilaalgarve/jogoacoes/blackbox/DeviceHeaderProxyIntegrationTest.java`
  (novo) — `@SpringBootTest` com o perfil `blackbox` ativo (mesmo padrão de
  `BlackboxProfileIntegrationTest`): chama o proxy com `action: "consume"` e Client Hints de um
  perfil "Windows desktop" contra um link de login válido, confere que a sessão resultante
  (consultável via `GET /sessions`, spec 05-010) tem `deviceLabel` batendo com o que
  `DeviceLabelResolverTest` já calibrou pros mesmos valores — prova que o header realmente saiu
  no lugar certo, não só que o proxy devolveu 200.

## Riscos e trade-offs

- **Chamada de saída é HTTP de verdade, não um dispatch interno do Spring** (`RestClient` contra
  `localhost:<porta>`, não `MockMvc`/dispatcher direto): mais simples de implementar e mais
  fiel ao que um navegador faria, ao custo de uma volta de rede a mais (loopback, latência
  irrelevante) — aceitável por ser só ferramenta de teste manual, não caminho de produção.
- **Corpo da resposta repassado como `String` bruto, sem tipar** (`ResponseEntity<String>`, não
  `ResponseEntity<LoginResult>`): mais simples, e o Swagger UI mostra o JSON de qualquer jeito;
  perde validação de schema da resposta, aceitável porque o objetivo é ver o `deviceLabel`
  resultante em `GET /sessions` depois, não validar o formato de `LoginResult` em si (já coberto
  em outro lugar).
- **Segundo grupo do springdoc só em `blackbox`**: se algum dia outro perfil precisar de um
  Swagger UI com múltiplos grupos por outro motivo, essa config vira ponto de atenção pra não
  duplicar — não é um problema hoje, só registrado.
