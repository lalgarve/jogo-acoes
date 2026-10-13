# Tasks: Caderno de testes por funcionalidade

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). As decisões técnicas
(ambiente `blackbox` com relógio real, dump sem sessões, scripts em
`docs/disciplina/caderno-de-testes/`, cookie em `cookie<número>`) estão em `plan.md`.

**Sem Cucumber/contrato OpenAPI novo** — documento de teste manual e scripts de apoio (ver
"Cenários" em `spec.md`). Nenhum código Java muda; o critério de aceite de exceções não se
aplica (nenhuma classe de exceção nem branch de erro novo).

Ordem: primeiro as verificações (o que a massa e os scripts precisam provar, rodadas antes de
existir o que elas verificam), depois o que as faz passar, e por fim o caderno, escrito a partir
de execuções reais.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Verificação da massa: escrever em `docs/disciplina/caderno-de-testes/dados/verificar-massa.sql` os `SELECT`s que provam que a massa atende aos roteiros — pelo menos três jogadores com combinações diferentes de `publicCount`/`privateCount` (roteiro 1), um jogador com participação em competição terminada, em andamento, convite pendente e nenhuma competição criada (roteiro 7), uma competição privada com convite pendente (roteiro 6). Rodar contra a massa gerada hoje (`python -m seed --profile standard --clock-offset-days -30` no ambiente `blackbox`) e registrar abaixo quais consultas falham | — | | |
| ~~T002~~ | Se T001 apontou faltas: acrescentar os casos ao catálogo do gerador (`blackbox-tests/seed/profiles.py`) e aos testes de `blackbox-tests/tests/test_seed_factories.py`; rodar `pytest` e repetir T001 até todas as consultas passarem. Se nada faltou, registrar "sem mudança no gerador" | T001 | | |
| ~~T003~~ | Verificação da restauração: descrever abaixo os três testes manuais que `restaurar-massa` tem que passar — restaurar num volume novo e subir o `app` sem erro do Flyway; restaurar sobre um banco já usado e conferir que os dados voltam ao estado do dump; simular uma migration mais nova que a do dump e conferir que o script recusa com mensagem para regenerar | — | [P] | |
| ~~T004~~ | Verificação dos scripts de login: descrever abaixo a saída esperada de `login-jogador` e `dispositivos-jogador` para a sequência do roteiro 3 (contagem de sessões 2, 3, 3 e a primeira encerrada), incluindo as mensagens de cada passo e o caso do e-mail que não chega (5 tentativas e erro claro) | — | [P] | |
| ~~T005~~ | Criar `docs/disciplina/caderno-de-testes/scripts/gerar-massa.sh`: sobe com `scripts/blackbox-clock-offset.sh -30`, roda o gerador, derruba e faz o `pg_dump` (`--schema=jogo_acoes`, sem dados de `spring_session`, `spring_session_attributes` e `login_session`) para `dados/jogo-acoes-massa.sql`, escrevendo na tela cada passo | T002 | | |
| ~~T006~~ | Rodar `gerar-massa.sh` e versionar `dados/jogo-acoes-massa.sql`; rodar `verificar-massa.sql` contra o banco restaurado e registrar a data da geração | T005 | | |
| ~~T007~~ | Criar `scripts/restaurar-massa.sh` e `scripts/restaurar-massa.ps1` (app parado, recria o schema, carrega o dump pelo container `db`, checa a versão da última migration), escrevendo na tela cada passo; passar os três testes de T003 nos dois | T003, T006 | | |
| ~~T008~~ | Criar `docs/disciplina/caderno-de-testes/dispositivos/1.json`, `2.json` e `3.json` com os quatro cabeçalhos `Sec-CH-UA*`, valores tirados de `blackbox-tests/common/device_profiles.py` e dos testes do `DeviceLabelResolver`, um rótulo diferente por dispositivo | — | [P] | |
| ~~T009~~ | Criar `scripts/login-jogador.sh` e `scripts/dispositivos-jogador.sh` (bash, `curl` + `jq`) conforme `plan.md`; passar a verificação de T004 | T004, T008 | | |
| ~~T010~~ | Criar `scripts/login-jogador.ps1` e `scripts/dispositivos-jogador.ps1` (PowerShell, sem dependência extra) com a mesma saída dos scripts bash; passar a verificação de T004 | T004, T008 | [P] | |
| ~~T011~~ | Reescrever `docs/disciplina/caderno-de-testes.md`: seções de preparação (gerar os dados com Python, restaurar o dump, subir o sistema com o overlay `blackbox` e acessar Swagger UI, Adminer, `ses-viewer` e logs) | T006, T007 | | |
| ~~T012~~ | Escrever no caderno os roteiros de login 1–3, executando cada um sobre a massa restaurada e capturando logs e resultados de `SELECT` reais; JSON de cada corpo em bloco próprio | T009, T010, T011 | | |
| ~~T013~~ | Escrever no caderno os roteiros de competições 4–7, do mesmo jeito | T011 | [P] | |
| ~~T014~~ | Atualizar a spec 05-012 para "substituída pela 05-040" e registrar a mudança no log de `docs/disciplina/CLAUDE.md` (inclui a pasta `caderno-de-testes/` na tabela de arquivos) | T011 | [P] | |
| T015 | Verificação final: executar o caderno inteiro do zero (volume novo, restaurar o dump, roteiros 1–7) no Windows, com os scripts no WSL e no PowerShell, e registrar abaixo o resultado de cada roteiro | T012, T013 | | |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature — a Issue leva o label
  `iteration-5`, além do label de tipo (`docs`). Coluna "Issue" preenchida quando a Issue-épico
  for aberta.
- T015 precisa da máquina da Leila (Windows com WSL e PowerShell); as outras rodam em qualquer
  ambiente com Docker, e os scripts `.ps1` podem ser testados com `pwsh` no Linux antes.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Registro das verificações

Executado em 2026-10-12 num container Linux da sessão da Claude. O Docker funcionou para
Postgres e LocalStack, mas o Docker Hub recusou baixar a imagem base do `app` e do
`email-service` (`429 Too Many Requests`) e o registro da AWS que o LocalStack usa para a imagem
da Lambda Java estava bloqueado. Por isso:

- `app` e `email-service` rodaram como `java -jar` no host, com os mesmos perfis e variáveis do
  `docker-compose.yml` (`docker,blackbox`, `EMAIL_SENDER=email-service`), e o relógio deslocado
  com `faketime "-30 days"` no lugar do `scripts/blackbox-clock-offset.sh`;
- a Lambda de e-mail foi substituída por um repasse descartável fora do repositório (lê a fila
  SQS e chama `SendTemplatedEmail` no SES do LocalStack, como o `EmailSendHandler`).

**T001.** Primeiro achado: o gerador não rodava no `master`. Desde a spec 05-031 o e-mail sai
por `SendTemplatedEmail`, que o LocalStack guarda com `Template` + `TemplateData` e sem `Body`;
`last_email()` só lia o `Body` e nunca achava o link. A checagem de relógio também comparava com
o horário do LocalStack, que não é deslocado. Corrigido em `blackbox-tests/common/blackbox_fixtures.py`
(link lido de `TemplateData`) e `seed/__main__.py` (checa o `joinedAt` gravado pelo app), com dois
testes novos em `tests/test_mailbox.py`; `pytest` (25 testes) e `behave` (2 cenários) verdes.
Depois disso, `python -m seed --profile standard --clock-offset-days -30` criou 11 competições e
21 jogadores, e `verificar-massa.sql` passou nas quatro verificações de massa (a de sessões só
passa depois do dump, como esperado).

**T002.** Sem mudança no catálogo do gerador: a massa já cobre os roteiros. Os jogadores
escolhidos estão na tabela da preparação do caderno.

**T003 e T007.** `restaurar-massa.sh` e `restaurar-massa.ps1` (PowerShell 7.4 no Linux):

- volume novo do `db` (`docker volume rm`), restaurar e subir o `app`: Flyway "Successfully
  validated 9 migrations", "Schema jogo_acoes is up to date", app no ar;
- banco já usado, com um usuário extra inserido: depois de restaurar o usuário extra sumiu e a
  verificação passou;
- migration falsa `V10__teste.sql` (via `MIGRATIONS_DIR`): os dois scripts recusam com "o dump é
  da migration V9, mas o repositório já está na V10" e código de saída 1;
- dump com fim de linha CRLF (como o git entrega no Windows): carrega igual.

**T004, T009 e T010.** Roteiro 3 com os scripts bash e com os PowerShell: contagens 2, 3 e 3,
com a sessão mais antiga fora da lista no quarto login; o cookie gravado por um é lido pelo
outro. Com um e-mail sem cadastro, os dois fazem 5 tentativas, uma a cada 2 s, e terminam com
erro. Achado: o passo 5 do roteiro 3 falha no sistema (a sessão encerrada pelo limite de
dispositivos continua autenticada, `GET /sessions` responde `200`), registrado no caderno.

**T005 e T006.** `gerar-massa.sh` não rodou inteiro aqui porque sobe o `app` pelo Compose; os
passos dele foram executados um a um (gerador, `pg_dump` com o cabeçalho de versão, restauração).
Fica para a T015.

**T011 a T013.** Roteiros 1 a 7 executados sobre a massa restaurada; os logs e `SELECT`s do
caderno vêm dessas execuções. Diferenças em relação à spec, registradas no caderno:

- roteiro 7: `pastParticipations` fica vazio, porque o grupo depende do status `CLOSED`, que
  nenhuma operação grava (spec 05-018, "Fora de escopo"); a competição terminada por data aparece
  em `participating`;
- roteiro 5: depois de mandar os convites pelo grupo, a competição continua `AWAITING_INVITES`;
  os convidados entram mesmo assim.

**T015.** Pendente: executar o caderno do zero na máquina da Leila (Windows, WSL e PowerShell),
incluindo `gerar-massa.sh` pelo Compose.
