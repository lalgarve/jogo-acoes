# Spec: Identificação de dispositivo via User-Agent Client Hints + Swagger UI interativa

**Status:** rascunho
**Issue:** [#59](https://github.com/lalgarve/jogo-acoes/issues/59)
**Iteração:** iteration-5

## Resumo

Substitui o rótulo de dispositivo hoje gravado em `LOGIN_SESSION.device_id` (User-Agent cru)
por um rótulo legível montado a partir de User-Agent Client Hints (`Sec-CH-UA-*`), com fallback
pro User-Agent tradicional quando os hints não estão presentes. Também traz Swagger UI pra
rodar junto da aplicação, com os novos headers documentados como parâmetros — permitindo testar
manualmente sem precisar de um browser real.

## Motivação

Hoje `LoginLinkSessionService.deviceLabel()` grava o `User-Agent` cru como "nome do dispositivo
pro jogador" (`docs/diagrams/der.md`) — navegadores modernos baseados em Chromium já "congelam"
boa parte do User-Agent por privacidade (sempre reportam uma versão de SO genérica, por
exemplo), tornando esse rótulo cada vez menos informativo. Client Hints é o mecanismo que
substitui essa informação: o servidor pede explicitamente (header de resposta `Accept-CH`) os
dados que quer, e o browser passa a mandá-los como headers de requisição nas próximas
requisições àquela origem. Isso também prepara terreno pra spec seguinte (gestão de sessões
ativas), que precisa de um rótulo que o jogador reconheça pra decidir qual sessão revogar. Falta
ainda, sinalizado em `docs/disciplina/alinhamento-projeto-disciplina.md` (Etapa 1), uma UI
interativa do Swagger rodando junto da aplicação — hoje só existe o `docs/openapi.yaml`
estático.

## Cenários (comportamento esperado)

- `app/src/test/resources/features/device_identification.feature` (novo)

Cobre: link de login consumido com Client Hints presentes (rótulo usa marca/versão/plataforma
dos hints); consumido só com User-Agent (sem hints) usa o fallback; nenhum dos dois presentes
usa o rótulo genérico já existente (`"unknown-device"`).

## Requisitos funcionais

- Toda resposta HTTP inclui o header `Accept-CH` listando os Client Hints que o servidor quer
  (`Sec-CH-UA`, `Sec-CH-UA-Platform`, `Sec-CH-UA-Platform-Version`, `Sec-CH-UA-Mobile`), pra
  aumentar a chance de já estarem presentes numa requisição seguinte da mesma origem.
- Novo componente (função pura) resolve o rótulo de dispositivo a partir dos headers de uma
  requisição: usa os Client Hints quando `Sec-CH-UA-Platform` está presente (mínimo necessário
  pra montar algo melhor que o User-Agent puro); senão usa o `User-Agent` cru (comportamento de
  hoje); senão usa `"unknown-device"` (comportamento de hoje).
- `LoginLinkSessionService.establish` passa a gravar esse rótulo resolvido em vez do
  `User-Agent` cru direto.
- `consumeLoginLink` (`GET /login-links/{token}`) e `completeRegistration`
  (`POST /login-links/{token}/registration`) — as duas operações que hoje estabelecem sessão —
  ganham os Client Hints acima como parâmetros de header **opcionais** no contrato
  (`docs/openapi.yaml`), documentados o bastante pra aparecerem como campo preenchível no
  Swagger UI.
- Swagger UI passa a rodar junto da aplicação (nova dependência), servindo o `docs/openapi.yaml`
  estático existente (não escaneado por anotação) — endereço final considera o
  `context-path: /api` já configurado.

## Requisitos não-funcionais

- **Sem regressão de comportamento de segurança**: a checagem de "já autenticado no mesmo
  dispositivo" continua via `SecurityContext`/cookie de sessão (não muda nesta spec) — o rótulo
  de dispositivo continua sendo só um dado de exibição, nunca um sinal de identidade usado em
  decisão de acesso. Ver "Decisões em aberto".
- **Degradação graciosa**: navegadores sem suporte a Client Hints (Firefox, Safari) continuam
  funcionando exatamente como hoje (fallback pro User-Agent) — nenhuma regressão pra quem não
  manda os hints.

## Fora de escopo

- Qualquer coleta de Client Hints via API JavaScript (`navigator.userAgentData`) — não existe
  frontend neste projeto ainda; o mecanismo desta spec é 100% HTTP (headers de
  requisição/resposta), testável direto via Swagger UI/RestAssured sem precisar de browser real.
- Usar o rótulo de dispositivo como sinal de identidade/segurança (ex. bloquear login se o
  rótulo mudar) — continua sendo só um rótulo de exibição.
- Trocar `docs/openapi.yaml` de "escrito à mão" pra "gerado por anotação" — Swagger UI serve o
  arquivo estático existente, sem mudar como ele é mantido.

## Decisões em aberto

- **Correção de documentação, não requisito novo**: `docs/diagrams/der.md` (nota sobre
  `LOGIN_SESSION`) afirma hoje que `device_id` participa da checagem de "mesmo dispositivo" do
  link mágico — o código (`LoginLinkSessionService`, javadoc) diz o oposto (a checagem é via
  `SecurityContext`, `device_id` nunca é comparado). Corrigido nesta spec pra bater com o código
  real, já que a nota estava desatualizada — não é uma decisão de comportamento, é acerto de
  documentação.
- Nome exato do path do Swagger UI e se `docs/openapi.yaml` é servido como está ou copiado pro
  classpath — ver `plan.md`.
