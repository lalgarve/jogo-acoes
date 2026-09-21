# Tasks: Client Hints (Sec-CH-UA) nos testes Python do ambiente blackbox

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Os dois perfis de
dispositivo (valores exatos, reaproveitados dos testes Java) e onde entram nas chamadas já
estão resolvidos em `plan.md` — esta lista só quebra a implementação em passos.

**Sem mudança no lado Java** — `DeviceLabelResolver`/`LoginLinkSessionService` (spec 05-009) já
existem; só passam a ser exercitados de verdade pelo lado Python (ver "Fora de escopo" em
`spec.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar `blackbox-tests/common/device_profiles.py`: `WINDOWS_DESKTOP` e `ANDROID_MOBILE` (dataclass ou `NamedTuple` com `sec_ch_ua`, `sec_ch_ua_platform`, `sec_ch_ua_platform_version`, `sec_ch_ua_mobile`), valores exatos da tabela de `plan.md` (iguais aos já usados em `DeviceIdentificationSteps.java`/`ManageActiveSessionsSteps.java`/`DeviceLabelResolverTest.java`) | — | | — |
| T002 | Atualizar `blackbox-tests/seed/flows.py`: `public_entry_new_player`, `complete_invited_registration` e `login_existing_player` ganham um parâmetro opcional de perfil de dispositivo (padrão `WINDOWS_DESKTOP`), repassado a `consume_login_link`/`complete_registration` — parâmetro com padrão, nenhum call site em `seed/__main__.py` precisa mudar | T001 | [P] | — |
| T003 | Atualizar `blackbox-tests/features/steps/public_competition_entry_steps.py`: os dois pontos que chamam `consume_login_link`/`complete_registration` passam a mandar `WINDOWS_DESKTOP` | T001 | [P] | — |
| T004 | Criar `blackbox-tests/features/manage_active_sessions.feature`: um `Scenario` — jogador registrado loga num dispositivo "Windows desktop", loga de novo num "Android mobile", lista sessões, vê dois rótulos distintos (texto exato em `plan.md`) | — | [P] | — |
| T005 | Criar `blackbox-tests/features/steps/manage_active_sessions_steps.py`: passos do cenário de T004 — login de jogador já registrado com um perfil de dispositivo (`request_login_link` + `consume_login_link`, mesma mecânica de `login_existing_player`, mas chamado direto aqui já que `seed/` não é importado pelos testes de `behave`), e checagem via `list_active_sessions` (`generated_client/.../api/sessions/`, spec 05-010) de que os dois `deviceLabel` retornados são diferentes | T001, T004 | | — |
| T006 | Criar `blackbox-tests/tests/test_device_profiles.py` (pytest, sem HTTP): os dois perfis têm `sec_ch_ua_platform`/`sec_ch_ua_platform_version` entre aspas e `sec_ch_ua_mobile` em `?0`/`?1`, formato que `DeviceLabelResolver` espera | T001 | [P] | — |
| T007 | Rodar `pytest` em `blackbox-tests/` — confirmar `test_device_profiles.py` verde junto do resto | T006 | | — |
| T008 | Verificação manual/behave fim a fim contra o ambiente `blackbox`: rodar `behave` com o cenário de T004 e confirmar os dois `deviceLabel` — registrar aqui qual caminho de verificação foi possível no ambiente de implementação (mesma ressalva de rede a Docker já registrada nas specs 05-014/05-015/05-016/05-018) | T002, T003, T005 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`test`). Coluna "Issue" preenchida quando a Issue-épico for aberta.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
