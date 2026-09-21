# Tasks: Gerador de dados de teste do ambiente blackbox

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (catálogo `C1`–`C11`, jogadores multi-competição `M1`–`M4`, relógio único
por execução, sem manifesto/idempotência, `common/blackbox_fixtures.py`) já estão resolvidas em
`plan.md` — esta lista só quebra a implementação em passos.

**Sem Cucumber/contrato OpenAPI novo** — ferramenta de geração de dados fora do `mvn test`,
mesmo padrão das specs 05-014/05-015/05-016 (ver "Cenários" em `spec.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar `blackbox-tests/common/__init__.py` e `blackbox-tests/common/blackbox_fixtures.py`: mover `NoEmailSentError`/`last_email_link` de `features/mailbox.py` para cá, mover `ADMIN_EMAIL` de `features/steps/public_competition_entry_steps.py` para cá, e acrescentar uma função que devolve também o `sentAt` do último e-mail (hoje só o `link` é exposto) — necessária para a checagem de relógio de T009. Apagar `features/mailbox.py` | — | | — |
| T002 | Atualizar `features/steps/public_competition_entry_steps.py` para importar `ADMIN_EMAIL` e a leitura de e-mail de `common.blackbox_fixtures` em vez do módulo/constante locais | T001 | [P] | — |
| T003 | Atualizar `tests/test_mailbox.py` para importar de `common.blackbox_fixtures` em vez de `mailbox` | T001 | [P] | — |
| T004 | Atualizar `blackbox-tests/pyproject.toml`: acrescentar `"."` (raiz de `blackbox-tests/`) a `tool.pytest.ini_options.pythonpath` para que `tests/` consiga `from common.blackbox_fixtures import ...` e `from seed... import ...` | T001 | [P] | — |
| T005 | Criar `blackbox-tests/seed/__init__.py` (pacote vazio) | — | [P] | — |
| T006 | Criar `blackbox-tests/seed/profiles.py`: dados do catálogo `C1`–`C11` (tipo, `later`/`now`, `durationDays`, contagem/fluxo de jogadores — ver tabela em `plan.md`), dos jogadores multi-competição `M1`–`M4` (competições e ordem — ver tabela em `plan.md`), e das composições dos três perfis (`minimal` = `C1`,`C2`,`C4`; `standard` = catálogo inteiro + `M1`–`M4`; `volume` = `standard` + uma pública com 200 jogadores) | — | [P] | — |
| T007 | Criar `blackbox-tests/seed/factories.py`: fábricas Object Mother (mesmo padrão de `CompetitionMother`/`UserMother`, `memory/constitution.md`) para os corpos de requisição usados pelo gerador — criar competição, `invite-emails`, `entry-requests`, `registration`, `login-requests` — cada uma com um valor padrão válido e aceitando sobrescrever um campo por vez | — | [P] | — |
| T008 | Criar `blackbox-tests/seed/flows.py`: as orquestrações de mais de uma chamada HTTP — login do administrador semeado (uma vez por execução), criação de competição a partir de uma entrada do catálogo, e os três fluxos de jogador (entrada pública, convite privado, jogador já registrado que confirma entrada) — cada fluxo usa `common.blackbox_fixtures` para ler o link após o envio do e-mail, como no diagrama de sequência de `plan.md` | T001, T007 | | — |
| T009 | Criar `blackbox-tests/seed/__main__.py` (CLI, `argparse`): opções `--profile` (`minimal`/`standard`/`volume`, padrão `standard`), `--clock-offset-days` (obrigatório, rejeita `0` e positivos), `--seed` (padrão `0`, só afeta o perfil `volume`), `--base-url` (padrão `API_BASE_URL`), `--dry-run`. Fluxo: valida o offset; loga como administrador, abortando com mensagem clara em falha de login ou `captchaToken` vazio rejeitado; cria a primeira competição do catálogo e confere, via `sentAt` do e-mail que ela dispara, que o relógio do app bate com `--clock-offset-days` (aborta se não bater); cria o resto do catálogo do perfil escolhido e os jogadores multi-competição; imprime a contagem final por tipo/estado | T006, T007, T008 | | — |
| T010 | Criar `blackbox-tests/tests/test_seed_factories.py` (pytest, sem HTTP): fábricas de `factories.py` produzem valor padrão válido e aceitam sobrescrever um campo por vez; composição de cada perfil em `profiles.py` bate com a tabela de `plan.md` (`minimal`/`standard`/`volume`, contagem de 200 no perfil `volume`); a validação de `--clock-offset-days` de `__main__.py` rejeita `0` e valores positivos | T006, T007, T009 | | — |
| T011 | Atualizar `blackbox-tests/README.md`: seção nova descrevendo o gerador — pré-requisito (`blackbox` no ar, relógio já deslocado via `../scripts/blackbox-clock-offset.sh`, spec 05-018) e o comando (`python -m seed --profile standard --clock-offset-days -15`) | T009 | [P] | — |
| T012 | Rodar `pytest` em `blackbox-tests/` — confirmar `test_seed_factories.py` e `test_mailbox.py` verdes | T003, T004, T010 | | — |
| T013 | Verificação manual fim a fim: subir o `blackbox` com `./scripts/blackbox-clock-offset.sh -15`, rodar `python -m seed --profile standard --clock-offset-days -15` contra ele, e conferir pelo Swagger UI (`GET /competitions/public`, e `GET /competitions/mine` se a spec 05-017 já estiver mesclada) que o catálogo e os jogadores multi-competição saíram como o `plan.md` descreve — registrar aqui qual caminho de verificação foi possível no ambiente de implementação (mesma ressalva de rede a Docker já registrada nas specs 05-014/05-015/05-016) | T012 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria quando
  grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de
  tipo (`test`). Coluna "Issue" preenchida quando a Issue-épico for aberta.
- T013 depende de um ambiente `blackbox` (spec 05-014) acessível no ambiente de implementação —
  se `docker compose up` completo não estiver disponível, validar contra o jar empacotado +
  Postgres real (mesmo caminho já usado para verificar as specs 05-014/05-015/05-016), e
  registrar explicitamente qual dos dois foi usado.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
