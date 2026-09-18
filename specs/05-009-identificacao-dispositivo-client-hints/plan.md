# Plan: Identificação de dispositivo via User-Agent Client Hints + Swagger UI interativa

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Client Hints (spec WHATWG/W3C) tem dois grupos: "low entropy" (`Sec-CH-UA`,
`Sec-CH-UA-Mobile`, `Sec-CH-UA-Platform`), enviados por padrão em toda requisição de browsers
Chromium modernos, sem negociação prévia; e "high entropy" (`Sec-CH-UA-Platform-Version`,
`Sec-CH-UA-Model`, `Sec-CH-UA-Full-Version-List`, etc.), só enviados depois que o servidor pede
explicitamente via `Accept-CH` numa resposta anterior à mesma origem. Suporte por browser: bom
em Chrome/Edge/Opera e outros Chromium; Firefox e Safari não implementam Client Hints
(`navigator.userAgentData` também não existe neles) — nesses, só o `User-Agent` tradicional
chega. Limitação real pro fluxo de link mágico: um clique em link de e-mail costuma ser a
primeira requisição daquele browser a esta origem — não há "visita anterior" pra ter negociado
os high-entropy hints antes. Mandar `Accept-CH` em toda resposta (inclusive erros) aumenta a
chance de já estarem presentes numa eventual segunda requisição, mas não garante presença no
primeiro clique — aceito como limitação conhecida, coberta pelo fallback pro User-Agent.
`spring.session.store-type: jdbc` já existe (não usado por esta spec, relevante pra 05-010).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde vive o resolvedor de rótulo | `login/DeviceLabelResolver.java` (função pura/estática), mesmo módulo de `LoginLinkSessionService`, que passa a chamá-lo em vez de ler `User-Agent` direto. | resolvida | Mesmo critério de `ReturnToValidator`/`CompetitionAccessResolver` (specs 05-004/05-005) — lógica pura, testável sem contexto Spring, no módulo que a usa. |
| Quais Client Hints pedir/ler | `Sec-CH-UA` (marca+versão, formato `"Chromium";v="131", "Not_A Brand";v="24"` — ignorar entradas "greased"/fake brand que o próprio spec exige existirem, pra evitar fingerprinting por lista fixa de marcas), `Sec-CH-UA-Platform` (ex. `"Windows"`), `Sec-CH-UA-Platform-Version` (ex. `"15.0.0"`), `Sec-CH-UA-Mobile` (`?0`/`?1`). | resolvida | Conjunto mínimo pra montar um rótulo tipo "Windows 15 · Chromium 131 (mobile)" sem entrar em hints mais invasivos (`Sec-CH-UA-Model`, `Sec-CH-UA-Full-Version-List`) que não agregam pro caso de uso (rótulo pro jogador reconhecer o próprio dispositivo). |
| Formato do rótulo resolvido | `"{platform} {platformVersion} · {browserBrand} {browserVersion}"` (+ sufixo `" (mobile)"` se `Sec-CH-UA-Mobile: ?1`) quando `Sec-CH-UA-Platform` está presente; senão o `User-Agent` cru (comportamento de hoje); senão `"unknown-device"` (comportamento de hoje). | resolvida | Só monta o rótulo "melhor" quando há hint suficiente pra isso; nunca pior que o comportamento atual. |
| Quando mandar `Accept-CH` | Em toda resposta da aplicação (filtro `OncePerRequestFilter` dedicado, registrado no `SecurityFilterChain` ou fora dele) — não só nas rotas de login. | resolvida | Maximiza a chance de já estar negociado antes do clique no link mágico (ex. jogador visitou uma rota pública antes), mesmo sem garantir o caso de primeiro contato. |
| Onde declarar os headers no contrato | `parameters: - in: header, name: Sec-CH-UA-Platform, required: false` (e os demais) em `consumeLoginLink`/`completeRegistration`, do jeito que o gerador Spring já expõe `in: header` como `@RequestHeader(required = false)`. | resolvida | Mesmo mecanismo já usado hoje pra path params — sem novidade de geração de código. |
| Como servir Swagger UI a partir do `docs/openapi.yaml` estático | Nova dependência `springdoc-openapi-starter-webmvc-ui`, com `springdoc.swagger-ui.url` apontando pro arquivo estático (copiado pelo `maven-resources-plugin` de `docs/openapi.yaml` pra `static/openapi.yaml` em build time) e `springdoc.packages-to-scan` apontado pra um pacote inexistente (não escanear anotações pra gerar um segundo contrato). | resolvida (revisado) | O contrato já é a fonte de verdade (API-first, spec 05-007 valida isso continuamente) — gerar um segundo contrato via anotação criaria duas fontes conflitantes. Tentativa inicial usou `springdoc.api-docs.enabled: false`, mas isso desliga o bean `SpringDocConfiguration` inteiro, do qual o próprio controller da UI depende (`@ConditionalOnBean`) — confirmado subindo a aplicação, não só lendo a documentação; `packages-to-scan` vazio consegue o mesmo efeito (nenhuma operação escaneada) sem derrubar a UI. |

## Estrutura de módulos/pacotes

- `login/DeviceLabelResolver.java` (novo) — função pura.
- `login/LoginLinkSessionService.java` (modificado) — usa o resolver.
- `login/AcceptChFilter.java` (novo) — `OncePerRequestFilter` que adiciona `Accept-CH` em toda
  resposta.
- `docs/openapi.yaml` (modificado) — headers novos em `consumeLoginLink`/`completeRegistration`.
- `app/pom.xml` (modificado) — `springdoc-openapi-starter-webmvc-ui`.
- `app/src/main/resources/static/openapi.yaml` (novo, ou mecanismo equivalente) — o contrato
  servido pro Swagger UI.
- `docs/diagrams/der.md` (modificado) — corrige a nota sobre `LOGIN_SESSION.device_id`.

## Riscos e trade-offs

- **Cobertura parcial por browser** — só Chromium ganha rótulo rico; Firefox/Safari continuam
  no fallback. Aceito, sem alternativa viável sem frontend.
- **Primeiro clique de link mágico raramente tem os high-entropy hints** — limitação estrutural
  do mecanismo, não desta implementação; mitigado (não eliminado) por mandar `Accept-CH`
  cedo/sempre.
- **`Sec-CH-UA` tem formato de lista com aspas/vírgulas não trivial de parsear** — implementar
  com atenção a escaping, testar com valores de exemplo do spec (incluindo a entrada "greased"
  obrigatória).
