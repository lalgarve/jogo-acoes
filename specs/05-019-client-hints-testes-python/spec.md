# Spec: Client Hints (Sec-CH-UA) nos testes Python do ambiente blackbox

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Preenche `Sec-CH-UA`/`Sec-CH-UA-Platform`/`Sec-CH-UA-Platform-Version`/`Sec-CH-UA-Mobile` nas
chamadas de `blackbox-tests/` que já os aceitam como parâmetro no cliente gerado
(`consume_login_link`, `complete_registration`) — hoje ficam `UNSET` em toda parte (passos do
`behave` e gerador de dados, spec 05-018) — com dois ou três perfis fixos de dispositivo, e
acrescenta um cenário Gherkin que exercita `GET /sessions` com dois dispositivos simulados,
verificando rótulos distintos.

## Motivação

`DeviceLabelResolver` (spec 05-009) monta o rótulo de dispositivo mostrado em
`GET /sessions` (spec 05-010) a partir desses headers. Sem eles, toda sessão criada pela suíte
Python — inclusive as do gerador de dados (`seed/`, spec 05-018), que faz vários logins do
mesmo tipo de jogador em sequência — cai no mesmo fallback genérico (`User-Agent` cru do
`httpx`, ou `"unknown-device"`), tornando a listagem de dispositivos inútil pra verificação
manual ou automática: tudo parece a mesma coisa.

Diferente do Swagger UI rodando num navegador (ver spec 05-020) — onde o próprio navegador
proíbe scripts de mandar qualquer header começando com `Sec-` —, o `httpx` usado por
`blackbox-tests/` é um cliente HTTP nativo, sem essa restrição: já pode mandar esses headers
com qualquer valor, só que hoje ninguém preenche o parâmetro.

## Cenários (comportamento esperado)

- `blackbox-tests/features/manage_active_sessions.feature` (novo arquivo, espelhando
  `app/src/test/resources/features/manage_active_sessions.feature` da suíte Java) — cenário:
  jogador loga em dois "dispositivos" simulados (Client Hints diferentes), lista sessões
  (`GET /sessions`), vê dois rótulos de dispositivo distintos.

## Requisitos funcionais

- Módulo compartilhado (`blackbox-tests/common/`, mesmo diretório da spec 05-018) com 2-3
  perfis fixos de dispositivo (ex.: "Windows desktop, Chromium", "Android mobile, Chromium"),
  cada um com os quatro valores de header já no formato exigido por `DeviceLabelResolver`
  (Structured Fields: `Sec-CH-UA` como lista `"marca";v="versão"`, `Sec-CH-UA-Platform`/
  `-Platform-Version` entre aspas, `Sec-CH-UA-Mobile` como `?0`/`?1`) — reaproveitado tanto
  pelos passos do `behave` quanto pelo gerador `seed/`.
- Toda chamada em `blackbox-tests/` para `consume_login_link`/`complete_registration` passa a
  preencher esses quatro parâmetros com um dos perfis, em vez de deixar `UNSET`.
- Novo cenário Gherkin (`manage_active_sessions.feature`) cobrindo login em dois dispositivos
  simulados + `GET /sessions` mostrando dois rótulos distintos — usa `list_active_sessions` do
  cliente gerado (já existe em `generated_client/.../api/sessions/`, spec 05-010).

## Requisitos não-funcionais

- Nenhuma mudança no lado Java — `DeviceLabelResolver`/`LoginLinkSessionService` (spec 05-009)
  já fazem o trabalho certo; esta spec só passa a exercitá-los de verdade pelo lado Python.
- Perfis de dispositivo determinísticos (não sorteados) — mesmo raciocínio da spec 05-018 pra
  nomes de jogador: mais fácil de depurar, sem motivo pra variar.

## Fora de escopo

- Qualquer mudança no gerador de dados em si além de preencher os headers nas chamadas que já
  existem (spec 05-018 não muda de catálogo/perfis aqui).
- `revoke_session`/`DELETE /sessions/{sessionId}` — só a listagem (`GET /sessions`) é coberta
  pelo cenário novo; revogar sessão já está coberto do lado Java (`manage_active_sessions.feature`
  da suíte Cucumber) e não é o problema que motivou esta spec.
- Testar pelo Swagger UI com Client Hints reais — impossível pra qualquer Swagger UI rodando
  num navegador (ver spec 05-020, que ataca esse problema por um caminho diferente).

## Decisões em aberto

Nenhuma.
