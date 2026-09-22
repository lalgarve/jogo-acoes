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
| ~~T001~~ | Criar `blackbox-tests/common/__init__.py` e `blackbox-tests/common/blackbox_fixtures.py`: mover `NoEmailSentError`/`last_email_link` de `features/mailbox.py` para cá, mover `ADMIN_EMAIL` de `features/steps/public_competition_entry_steps.py` para cá, e acrescentar uma função que devolve também o `sentAt` do último e-mail (hoje só o `link` é exposto) — necessária para a checagem de relógio de T009. Apagar `features/mailbox.py` | — | | #74 |
| ~~T002~~ | Atualizar `features/steps/public_competition_entry_steps.py` para importar `ADMIN_EMAIL` e a leitura de e-mail de `common.blackbox_fixtures` em vez do módulo/constante locais | T001 | [P] | #74 |
| ~~T003~~ | Atualizar `tests/test_mailbox.py` para importar de `common.blackbox_fixtures` em vez de `mailbox` | T001 | [P] | #74 |
| ~~T004~~ | Atualizar `blackbox-tests/pyproject.toml`: acrescentar `"."` (raiz de `blackbox-tests/`) a `tool.pytest.ini_options.pythonpath` para que `tests/` consiga `from common.blackbox_fixtures import ...` e `from seed... import ...` | T001 | [P] | #74 |
| ~~T005~~ | Criar `blackbox-tests/seed/__init__.py` (pacote vazio) | — | [P] | #74 |
| ~~T006~~ | Criar `blackbox-tests/seed/profiles.py`: dados do catálogo `C1`–`C11` (tipo, `later`/`now`, `durationDays`, contagem/fluxo de jogadores — ver tabela em `plan.md`), dos jogadores multi-competição `M1`–`M4` (competições e ordem — ver tabela em `plan.md`), e das composições dos três perfis (`minimal` = `C1`,`C2`,`C4`; `standard` = catálogo inteiro + `M1`–`M4`; `volume` = `standard` + uma pública com 200 jogadores) | — | [P] | #74 |
| ~~T007~~ | Criar `blackbox-tests/seed/factories.py`: fábricas Object Mother (mesmo padrão de `CompetitionMother`/`UserMother`, `memory/constitution.md`) para os corpos de requisição usados pelo gerador — criar competição, `invite-emails`, `entry-requests`, `registration`, `login-requests` — cada uma com um valor padrão válido e aceitando sobrescrever um campo por vez | — | [P] | #74 |
| ~~T008~~ | Criar `blackbox-tests/seed/flows.py`: as orquestrações de mais de uma chamada HTTP — login do administrador semeado (uma vez por execução), criação de competição a partir de uma entrada do catálogo, e os três fluxos de jogador (entrada pública, convite privado, jogador já registrado que confirma entrada) — cada fluxo usa `common.blackbox_fixtures` para ler o link após o envio do e-mail, como no diagrama de sequência de `plan.md` | T001, T007 | | #74 |
| ~~T009~~ | Criar `blackbox-tests/seed/__main__.py` (CLI, `argparse`): opções `--profile` (`minimal`/`standard`/`volume`, padrão `standard`), `--clock-offset-days` (obrigatório, rejeita `0` e positivos), `--seed` (padrão `0`, só afeta o perfil `volume`), `--base-url` (padrão `API_BASE_URL`), `--dry-run`. Fluxo: valida o offset; loga como administrador, abortando com mensagem clara em falha de login ou `captchaToken` vazio rejeitado; cria a primeira competição do catálogo e confere, via `sentAt` do e-mail que ela dispara, que o relógio do app bate com `--clock-offset-days` (aborta se não bater); cria o resto do catálogo do perfil escolhido e os jogadores multi-competição; imprime a contagem final por tipo/estado | T006, T007, T008 | | #74 |
| ~~T010~~ | Criar `blackbox-tests/tests/test_seed_factories.py` (pytest, sem HTTP): fábricas de `factories.py` produzem valor padrão válido e aceitam sobrescrever um campo por vez; composição de cada perfil em `profiles.py` bate com a tabela de `plan.md` (`minimal`/`standard`/`volume`, contagem de 200 no perfil `volume`); a validação de `--clock-offset-days` de `__main__.py` rejeita `0` e valores positivos | T006, T007, T009 | | #74 |
| ~~T011~~ | Atualizar `blackbox-tests/README.md`: seção nova descrevendo o gerador — pré-requisito (`blackbox` no ar, relógio já deslocado via `../scripts/blackbox-clock-offset.sh`, spec 05-018) e o comando (`python -m seed --profile standard --clock-offset-days -15`) | T009 | [P] | #74 |
| ~~T012~~ | Rodar `pytest` em `blackbox-tests/` — confirmar `test_seed_factories.py` e `test_mailbox.py` verdes | T003, T004, T010 | | #74 |
| ~~T013~~ | Verificação manual fim a fim: subir o `blackbox` com `./scripts/blackbox-clock-offset.sh -15`, rodar `python -m seed --profile standard --clock-offset-days -15` contra ele, e conferir pelo Swagger UI (`GET /competitions/public`, e `GET /competitions/mine` se a spec 05-017 já estiver mesclada) que o catálogo e os jogadores multi-competição saíram como o `plan.md` descreve — registrar aqui qual caminho de verificação foi possível no ambiente de implementação (mesma ressalva de rede a Docker já registrada nas specs 05-014/05-015/05-016) | T012 | | #74 |

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

## T013 — registro da verificação manual

Ambiente Docker completo indisponível neste ambiente de implementação (mesmo bloqueio de rede
ao registry já registrado nas specs 05-014/05-015/05-016) — validado, como nas specs anteriores,
contra o jar empacotado (`mvn -pl app -am package -DskipTests`) + PostgreSQL local real, com
`faketime "-15 days"` no lugar do `libfaketime` do `docker compose run` (mesmo mecanismo,
executado fora do Docker).

Achado durante a verificação, não previsto em `plan.md`: `docker` ativa `email.sender: sqs`
(publica na fila SQS via LocalStack, que só existe no `docker-compose.yml`); rodando o jar
sozinho, sem LocalStack, todo envio de e-mail falhava (`ConnectException`). Contornado com
`--email.sender=stub` (o `EmailSender` que só grava em `sent_email`, sem publicar em fila) — o
que a suíte Java já usa por padrão em `sandbox`/testes. Não é uma mudança ao script nem à spec:
é só uma peça a mais do "suba o `blackbox` primeiro" que já era responsabilidade de quem roda o
gerador — quem sobe via `docker compose` de verdade já tem o LocalStack junto e não precisa
disso.

Rodado `python -m seed --profile standard --clock-offset-days -15` contra esse ambiente:
`Criado: 11 competicoes, 21 jogadores` (sem erros, sem checagem de relógio falhando). Conferido
direto no Postgres:

- As 11 competições do catálogo, todas com `start_date = 2026-09-07` (dia seguinte ao relógio
  deslocado) e `status`/`type`/`duration_days` batendo com a tabela de `plan.md`.
- Comparando com a data real do dia (`2026-09-21`, não com o relógio ainda deslocado do app que
  ficou no ar): `C9`/`C11` (60 dias) terminam em `2026-11-06`, no futuro — "em andamento"; `C10`
  (10 dias) termina em `2026-09-17`, no passado — "terminada por data". Confirma o raciocínio de
  `plan.md`: o que importa é a data real no momento em que alguém olha os dados depois, não o
  relógio do app enquanto ele ainda está rodando deslocado.
- Contagem de participações por status, por competição, bate exatamente com a tabela de
  `plan.md` — incluindo `C5` com 4 `EMAIL_SENT` (3 do catálogo + o convite pendente de `M4`) e
  `C9`/`C10`/`C11` com os jogadores multi-competição somados aos 2-3 originais de cada uma.
- Login como `M4` (`GET /competitions/mine`) devolveu exatamente `participating: 3`,
  `pendingConfirmation: 1`, `pastParticipations: 0` — como `plan.md` descreve.
