# Plan: Client Hints (Sec-CH-UA) nos testes Python do ambiente blackbox

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`DeviceLabelResolver.resolve(secChUa, secChUaPlatform, secChUaPlatformVersion, secChUaMobile,
userAgent)` (`app/src/main/java/dev/leilaalgarve/jogoacoes/login/DeviceLabelResolver.java`) só
usa Client Hints quando `Sec-CH-UA-Platform` está presente e não-branco — senão cai pro
`User-Agent` cru, senão `"unknown-device"`. `Sec-CH-UA` é uma lista Structured Field
(`"marca";v="versão", ...`), com uma entrada "greased" aleatória que o resolver ignora (contém
"not", case-insensitive) — reaproveitando os valores exatos já usados pelos testes Java
(`DeviceIdentificationSteps.java`, `ManageActiveSessionsSteps.java`,
`DeviceLabelResolverTest.java`) em vez de inventar novos, pra ficar consistente com o que já
está calibrado contra o parser real:

| Header | Perfil "Windows desktop" | Perfil "Android mobile" |
|---|---|---|
| `Sec-CH-UA` | `"Not_A Brand";v="24", "Chromium";v="131"` | `"Not_A Brand";v="24", "Chromium";v="131"` |
| `Sec-CH-UA-Platform` | `"Windows"` | `"Android"` |
| `Sec-CH-UA-Platform-Version` | `"15.0.0"` | `"14"` |
| `Sec-CH-UA-Mobile` | `?0` | `?1` |

`consume_login_link.sync`/`.sync_detailed` e `complete_registration.sync_detailed`
(`generated_client/jogo_acoes_client/api/login/`) já aceitam `sec_ch_ua`, `sec_ch_ua_platform`,
`sec_ch_ua_platform_version`, `sec_ch_ua_mobile` como parâmetros opcionais — confirmado no
código gerado, hoje só nunca chamados com eles. `list_active_sessions`/`revoke_session` já
existem em `generated_client/.../api/sessions/` (spec 05-010), prontos pra uso.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde ficam os perfis de dispositivo | `blackbox-tests/common/device_profiles.py` (mesmo pacote `common/` da spec 05-018) | resolvida | Mesma razão de `blackbox_fixtures.py` — evita duas fontes de verdade, usado tanto por `behave` quanto por `seed/`. |
| Quais/quantos perfis | Dois: "Windows desktop, Chromium" e "Android mobile, Chromium" (valores exatos acima, iguais aos já usados nos testes Java) | resolvida | Cobre o caso que o cenário novo precisa (dois rótulos distintos); reaproveitar os valores Java evita duas calibragens diferentes do mesmo parser. |
| Onde entram nas chamadas existentes | Toda chamada a `consume_login_link`/`complete_registration` em `blackbox-tests/` (passos do `behave` e `seed/flows.py`) passa um perfil — perfil "Windows desktop" como padrão de todo mundo, exceto o segundo dispositivo do cenário novo (`Android mobile`) | resolvida | Sem variar por padrão em todo o resto (seed, cenário público existente) — só onde o teste realmente precisa de dois rótulos diferentes, para não complicar sem necessidade. |
| Formato do cenário novo | `blackbox-tests/features/manage_active_sessions.feature`, só a Rule/Scenario de "dois dispositivos, dois rótulos" — não replica os outros cenários de `manage_active_sessions.feature` da suíte Java (revogar sessão, etc.), que já são cobertos lá | resolvida | Objetivo é só destravar a verificação de rótulo pela suíte Python; duplicar toda a feature Java não agrega, e não é o problema que motivou esta spec. |

## Estrutura de módulos/pacotes

- `blackbox-tests/common/device_profiles.py` (novo) — duas constantes/`dataclass`es
  (`WINDOWS_DESKTOP`, `ANDROID_MOBILE`), cada uma com os quatro valores de header da tabela
  acima.
- `blackbox-tests/seed/flows.py` (modificado, spec 05-018) — `public_entry_new_player`,
  `complete_invited_registration`, `login_existing_player` passam a repassar um perfil de
  dispositivo (`WINDOWS_DESKTOP` por padrão) para `consume_login_link`/`complete_registration`.
- `blackbox-tests/features/steps/public_competition_entry_steps.py` (modificado) — mesmo ajuste
  nos dois pontos que chamam `consume_login_link`/`complete_registration`.
- `blackbox-tests/features/manage_active_sessions.feature` (novo) — um `Scenario`:

  ```gherkin
  Feature: Manage active sessions

    Scenario: Player logs in on two devices and sees two distinct labels
      Given a registered player
      And they log in on a "Windows desktop" device
      And they also log in on an "Android mobile" device
      When they list their active sessions
      Then the system shows two sessions with distinct device labels
  ```

  Passos novos em `blackbox-tests/features/steps/manage_active_sessions_steps.py` (novo
  arquivo) — usa `request_login_link`/`consume_login_link` (fluxo de jogador já registrado,
  mesma mecânica de `login_existing_player` do `seed/flows.py`, mas chamado direto aqui já que
  `seed/` não é importado pelos testes de `behave`) com um perfil de dispositivo diferente por
  chamada, e `list_active_sessions` pra checar os dois rótulos.
- `blackbox-tests/tests/test_device_profiles.py` (novo, opcional) — confere que os dois perfis
  têm valores nos formatos que `DeviceLabelResolver` espera (aspas em `Platform`/
  `Platform-Version`, `?0`/`?1` em `Mobile`), sem HTTP.

## Riscos e trade-offs

- **Acoplamento aos valores exatos do parser Java**: se `DeviceLabelResolver` mudar o formato
  esperado no futuro, os dois perfis em `device_profiles.py` (e os testes Java que já usam os
  mesmos valores) precisam mudar juntos — risco aceito, não há como testar o rótulo sem
  reproduzir o formato real.
- **`Sec-CH-UA` "greased" fixo**: usar sempre a mesma entrada `"Not_A Brand";v="24"` (em vez de
  variar) é uma simplificação deliberada — o parser já ignora essa entrada por conter "not",
  então variá-la não mudaria nenhum resultado observável, só complicaria os perfis à toa.
