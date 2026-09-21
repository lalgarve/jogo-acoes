# Spec: Proxy de Client Hints para testar rótulo de dispositivo pelo Swagger UI

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Componente novo, ativo só no perfil `blackbox`, com uma única operação HTTP
(`POST /blackbox/device-header-proxy`) que recebe os valores de Client Hints desejados num
corpo JSON (não como headers de verdade — o navegador proíbe isso, ver "Motivação"), refaz a
chamada de login/registro correspondente contra a própria API com esses valores como headers
`Sec-CH-UA*` reais, e devolve a resposta (status, corpo, `Set-Cookie`) — permitindo, enfim,
testar pelo Swagger UI (rodando no próprio navegador do administrador) a montagem do rótulo de
dispositivo (spec 05-009) sem precisar de `curl`/script.

## Motivação

Todo `fetch`/`XMLHttpRequest` de uma página roda dentro de um navegador, e o navegador proíbe
JavaScript de mandar qualquer header começando com `Sec-` — é assim que ele impede uma página de
falsificar esses hints. Isso vale tanto pro Swagger UI que roda dentro do `app/` (spec 05-009)
quanto pra qualquer outro rodando num navegador: mesmo com o campo aparecendo preenchível no
"Try it out", o valor digitado nunca chega ao servidor — o navegador manda o valor real dele (ou
nada) por baixo, silenciosamente.

A spec 05-009 presumiu o contrário — seu próprio texto ("testável direto via Swagger UI...
sem precisar de browser real") e o comentário em `docs/openapi.yaml` sobre `SecChUa`
("Declared here so Swagger UI shows a fillable field for manual testing without a real
browser") descrevem um jeito de testar que não funciona na prática, só nunca foi percebido
porque a suíte automática (Java, RestAssured; ou a suíte Python, `httpx`) nunca passa por um
navegador de verdade — só um humano usando o Swagger UI embutido do `app/` esbarraria nisso, e é
exatamente o que aconteceu.

A saída: `Sec-*` só é proibido pra **scripts de página**, não pra um processo Java fazendo sua
própria chamada HTTP de servidor pra servidor. Um proxy que recebe os valores desejados por um
canal que o navegador permite (corpo JSON de uma chamada comum) e os aplica de verdade numa
segunda chamada, feita pelo próprio servidor, contorna a restrição sem violar o mecanismo de
proteção do navegador — a página em si nunca manda um header `Sec-*`, quem manda é o proxy.

## Cenários (comportamento esperado)

Não aplicável — mesmo padrão de `BlackboxController`/`BlackboxDataSeeder` (spec 05-014):
andaime de teste do ambiente `blackbox`, sem `.feature` novo. Coberto por teste de integração
(`@SpringBootTest`, ver `plan.md`), como `BlackboxProfileIntegrationTest` já faz para o resto do
andaime desse perfil.

## Requisitos funcionais

- Nova rota `POST /blackbox/device-header-proxy`, ativa só no perfil `blackbox` (mesmo padrão
  de `@Profile("blackbox")` de `BlackboxController`), fora de `docs/openapi.yaml` (é andaime de
  teste, não contrato de produto — mesma decisão já tomada para `GET /blackbox/last-email`).
- Corpo da requisição: `token` (o token do link), `action` (`"consume"` ou `"register"`, as duas
  operações que hoje estabelecem sessão), `name` (obrigatório só quando `action` é `"register"`)
  e os quatro valores de Client Hints (`secChUa`, `secChUaPlatform`, `secChUaPlatformVersion`,
  `secChUaMobile` — todos opcionais, mesma semântica de hoje).
- Internamente, refaz a chamada real (`GET /login-links/{token}` para `"consume"`,
  `POST /login-links/{token}/registration` para `"register"`) contra a própria aplicação, com
  os quatro headers acima setados de verdade na requisição de saída (só quando informados) —
  processo Java fazendo sua própria chamada HTTP não está sujeito à restrição de
  `Sec-*`/`Proxy-*` que só existe pra scripts de página.
- Repassa o `Cookie` da requisição recebida (se houver) pra chamada de saída, e repassa de volta
  o `Set-Cookie` da resposta da chamada de saída — sem isso, o "login" feito através do proxy não
  deixaria o navegador logado de verdade depois.
- Devolve status e corpo da chamada real, sem reformatar — o Swagger UI vê exatamente o que a
  rota real devolveria se o navegador conseguisse mandar os headers.
- Aparece como operação testável no Swagger UI do próprio `app/` (ver `plan.md` — grupo
  separado, sem entrar em `docs/openapi.yaml`).

## Requisitos não-funcionais

- **Superfície restrita**: só encaminha para `/login-links/{token}` e
  `/login-links/{token}/registration` — nunca um caminho arbitrário — mesmo estando confinado ao
  perfil `blackbox`, pra não virar um relay HTTP genérico à toa (mesmo cuidado de superfície já
  registrado no javadoc de `BlackboxController`).
- **Nunca ativo fora de `blackbox`**: mesma garantia estrutural que `BlackboxController`/
  `AlwaysPassCaptchaVerifier` já têm — `@Profile("blackbox")` em tudo.

## Fora de escopo

- Qualquer mudança em `LoginController`/`DeviceLabelResolver`/`LoginLinkSessionService` — o
  mecanismo de resolução de rótulo já funciona (spec 05-009); esta spec só destrava testá-lo
  manualmente pelo Swagger UI.
- Cobrir outras rotas além das duas de login/registro — nenhuma outra rota lê Client Hints hoje.
- Corrigir o texto desatualizado da spec 05-009/comentário em `docs/openapi.yaml` sobre "testável
  via Swagger UI sem browser real" — candidato a acerto de documentação à parte, não feito aqui.

## Decisões em aberto

Nenhuma — formato do corpo, superfície restrita a duas rotas, e forma de aparecer no Swagger UI
(grupo separado, ver `plan.md`) decididos em conversa antes de escrever este documento.
