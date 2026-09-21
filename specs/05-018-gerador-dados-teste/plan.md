# Plan: Gerador de dados de teste do ambiente blackbox

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Verificado direto no código (`app/src/main/java/dev/leilaalgarve/jogoacoes/competition/`):
`CompetitionService.create` exige `startDate` estritamente depois de `LocalDate.now()`;
`CompetitionViewService.listPublicCompetitions` filtra só por `type`+`status`
(`findByTypeAndStatus`), sem olhar data — uma competição "terminada por data" mas ainda `OPEN`
continua aparecendo em `GET /competitions/public` (diferente do que
`browse_public_competitions.feature` testa para o fechamento por status); `POST
/competitions/{id}/invite-emails` aceita `timing: now|later`. `login.max-devices-per-user: 3`,
link válido por 7 dias, sessão HTTP por 30 dias (`application.yml`).

| Estado ou dado | Por HTTP? | Motivo |
|---|---|---|
| Competição PUBLIC em OPEN | Sim | `POST /competitions` cria a pública já aberta |
| Competição PRIVATE em AWAITING_INVITES | Sim | privada + `invite-emails` com `later` |
| Competição PRIVATE em OPEN | Sim | mesma chamada com `now` |
| Competição CLOSED | Não | nenhum código do `app/` grava `CLOSED`; só há leituras |
| Competição em andamento ou terminada por data | Sim, com o relógio no passado | `startDate` exige "depois de hoje", e "hoje" é o relógio do app |
| Participação EMAIL_NOT_SENT | Sim | privada criada com `later` |
| Participação EMAIL_SENT | Sim | convite com `now`, ou pedido de entrada pública aguardando o link |
| Participação LINK_CLICKED | Não | bug confirmado ([#73](https://github.com/lalgarve/jogo-acoes/issues/73)): `CompetitionLinkHandler.consume()` nunca grava esse status ao clicar no link, só ao completar o registro (ver `spec.md`) |
| Participação IN_COMPETITION | Sim | registro pelo link, ou confirmação de quem já tem sessão |
| Usuário com papel PLAYER | Sim | só nasce ao completar o registro de um link de competição |
| Administrador além do semeado | Não | não há API; `BlackboxDataSeeder` semeia só `success+admin@simulator.amazonses.com` |

Fluxo de criação de um jogador novo (entrada pública) — três chamadas de API mais uma leitura
de e-mail, a única fora do contrato `docs/openapi.yaml`:

```
Script -> API: POST entry-requests (e-mail)
API -->> Script: 202, sem link no corpo
API -> E-mail: envia e-mail com o link (sent_email)
Script -> API: GET /blackbox/last-email
API -->> Script: link do último e-mail
Script -> API: GET /login-links/{token}
API -->> Script: 202 (jogador novo)
Script -> API: POST registration (nome)
API -->> Script: 200 (IN_COMPETITION)
```

Em competição privada, o convite sai antes por `invite-emails` com `now`, e o link lido em
`last-email` é o do convite. Um jogador já registrado faz `POST /login-requests`, lê o link,
consome (`200`) e confirma a entrada com `POST entry-requests` sem corpo. `last-email` devolve
só o último e-mail de cada endereço — o script sempre lê o link logo após a ação que o gerou.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Catálogo de competições | 11 competições (`C1`–`C11`) cobrindo os estados alcançáveis, cada uma marcada com o deslocamento de relógio necessário; ver tabela abaixo | resolvida | Cobre a tabela de estados por HTTP sem repetir código de teste — mesma lista de qualquer forma precisaria existir num `.feature`/roteiro manual. |
| **Relógio presente (offset 0)** | Nunca usado — toda competição do catálogo nasce com o relógio do app em algum deslocamento no passado | resolvida | Dados do dia real já nascem organicamente toda vez que a própria suíte (`behave`/`pytest`) roda, contra o relógio verdadeiro — o gerador existe para cobrir o que a suíte sozinha não cobre: o passado. Repetir isso no presente seria massa duplicada e, pior, indistinguível da massa que os próprios testes geram. `--clock-offset-days` não tem valor padrão e rejeita `0`/positivos (ver "Estrutura de módulos/pacotes"). |
| **Um único deslocamento de relógio, não uma fase por estado** | Todo o catálogo (`C1`–`C11`, jogadores multi-competição) roda com o **mesmo** `--clock-offset-days` — o que diferencia "em andamento" de "terminada por data" é a `durationDays` de cada competição relativa a esse único deslocamento, não deslocamentos diferentes | resolvida | Com um só relógio, `startDate` = deslocamento + 1 dia é fixo para todas; uma `durationDays` curta (ex. 10) termina antes de hoje, uma longa (ex. 60) ainda não — dispensa subir o app mais de uma vez e dispensa qualquer raciocínio sobre ordem entre fases (a versão anterior deste plano tinha três deslocamentos — `−60`/`−30`/`hoje` — só por não ter pensado em variar a duração em vez do relógio). |
| Perfis | `minimal` (`C1`, `C2`, `C4`), `standard` (`C1`–`C11` + jogadores multi-competição), `volume` (`standard` + uma pública com N jogadores) — todos com o mesmo deslocamento de relógio numa única execução | resolvida | Cobre do teste rápido no Swagger UI até listas longas para filtro, sem forçar todo mundo a gerar a massa completa. |
| **Jogadores multi-competição** | Quatro jogadores nomeados (`M1`–`M4`), registrados em exatamente 1, 2, 3 e 4 competições cada, cruzando os grupos `participating`/`pendingConfirmation` de `GET /competitions/mine`; ver tabela abaixo | resolvida | Todo jogador do catálogo original é distinto por competição — nenhum cenário hoje exercita a listagem de um jogador com mais de um item. Essencial para os cenários de listagem/filtro da spec 05-015 (e para qualquer QA manual da tela "minhas competições"). |
| **Idempotência/reexecução** | Nenhuma — v1 roda uma vez, contra um `blackbox` recém-subido, e cria tudo direto, sem checar antes se já existe | resolvida | A ideia original (handout) tinha um manifesto local para permitir reexecução segura; descartada porque o uso real é rodar uma vez contra ambiente vazio, não uma pipeline que roda repetidamente. Sem essa exigência, some também a dependência da spec 05-017 (que só entraria para permitir checar "já criei essa competição?" antes de recriar) — 05-017 continua útil por si mesma (administrador listar o que criou), mas deixa de ser pré-requisito desta spec. |
| Nomes de jogador | Lista fixa de nomes (`Object Mother`), ciclada por posição — não sorteada, exceto no perfil `volume` | resolvida | Determinismo por posição é mais simples de depurar que aleatoriedade sem motivo; `volume` é o único caso com volume grande o bastante para gerar nomes repetitivos, por isso sorteia com `--seed`. |
| Como avançar o relógio | O script não sobe/derruba o app — só recebe `--clock-offset-days` e roda o catálogo inteiro do perfil escolhido contra aquele deslocamento; subir o app com o relógio certo é um passo manual e separado, hoje automatizado por `scripts/blackbox-clock-offset.sh` (`README.md`) | resolvida | Combinar os dois num só comando acoplaria este script Python ao ciclo de vida do Docker; mantê-los separados é mais simples — quem roda decide se quer o app já rodando ou vai subi-lo com o script de relógio primeiro. Fica fora de escopo (ver `spec.md`). |
| `"terminada por data"` com status `OPEN` | Aceito na v1, sem renomear | resolvida | `GET /competitions/public` realmente devolve isso hoje (confirmado no código) — não é um artifício do script, é o comportamento real da API; renomear fica para quando a Etapa de fechamento por data existir (roadmap, iterações 8–9). |
| Estrutura/local do código | Dentro de `blackbox-tests/` (`seed/`), reaproveitando o cliente gerado e o `pyproject.toml` já existentes | resolvida | Mesmo raciocínio da spec 05-015 — projeto Python independente, sem duplicar setup. `features/mailbox.py` e a constante do e-mail do administrador (`blackbox-tests/features/steps/public_competition_entry_steps.py`) movem para um módulo compartilhado que tanto `seed/` quanto `behave` importam (ver "Estrutura de módulos/pacotes"). |

Catálogo (perfil `standard`; `minimal` = só `C1`, `C2`, `C4`; `volume` = tudo isso mais uma
pública com N jogadores). Todas as linhas rodam com o **mesmo** `--clock-offset-days` — o
exemplo abaixo usa `−15` dias; `startDate` de toda competição é sempre "o dia seguinte ao
relógio deslocado" (aqui, dia `−14`). O que muda entre "em andamento" e "terminada por data" é
só a `durationDays`:

| Código | Tipo | Como é criada | Participações |
|---|---|---|---|
| C1 | PUBLIC, OPEN | `POST /competitions`, duração 30 dias | nenhuma |
| C2 | PUBLIC, OPEN | C1 + entrada de 5 jogadores novos | 5 IN_COMPETITION |
| C3 | PUBLIC recorrente, OPEN | `recurring: true` + 2 jogadores | 2 IN_COMPETITION |
| C4 | PRIVATE, AWAITING_INVITES | 3 e-mails + `later` | 3 EMAIL_NOT_SENT |
| C5 | PRIVATE, OPEN | 3 e-mails + `now` | 3 EMAIL_SENT |
| C6 | PRIVATE, OPEN | 3 e-mails + `now`; 2 convidados registram | 2 IN_COMPETITION, 1 EMAIL_SENT |
| C7 | PUBLIC de limites, OPEN | taxas 0, duração 1 dia | nenhuma |
| C8 | PRIVATE, OPEN | convida jogadores já registrados + `now` | 2 EMAIL_SENT com conta (pendingConfirmation) |
| C9 | PUBLIC **em andamento**, OPEN | duração 60 dias (termina no dia `+46` — ainda não chegou), 3 jogadores | 3 IN_COMPETITION |
| C10 | PUBLIC **terminada por data**, OPEN | duração 10 dias (termina no dia `−4` — já passou), 3 jogadores | 3 IN_COMPETITION |
| C11 | PRIVATE **em andamento**, OPEN | 3 e-mails + `now`, duração 60 dias; 2 convidados registram | 2 IN_COMPETITION, 1 EMAIL_SENT |

Uma única execução, um único relógio: `startDate` se calcula sobre o relógio do app no momento
da chamada (dia `−14`, com o exemplo de `−15`), e cada competição só varia a `durationDays`
para acabar antes (`C10`) ou depois (`C9`, `C11`) de hoje. O script confere, via `sentAt` do
`GET /blackbox/last-email` logo após o primeiro envio, que o relógio do app bate com o
`--clock-offset-days` recebido, antes de criar o resto do catálogo. Links (7 dias) e sessões
HTTP (30 dias) criados nesta execução já vencem no presente (com `−15` dias de deslocamento,
faltam só alguns dias — escolher um deslocamento com folga suficiente, ex. `−15` a `−20`, é
responsabilidade de quem roda o script, não uma checagem automática).

### Jogadores multi-competição

Quatro jogadores nomeados, cada um se registrando em mais competições que o anterior — a mesma
execução, ordem importa só para decidir se aquele jogador ainda é "novo" (fluxo de entrada) ou
já registrado (fluxo de login) na próxima competição:

| Jogador | Competições (nesta ordem) | Total | Grupos em `GET /competitions/mine` |
|---|---|---|---|
| M1 | `C9` | 1 | `participating`: 1 |
| M2 | `C9`, `C10` | 2 | `participating`: 2 |
| M3 | `C9`, `C10`, `C11` | 3 | `participating`: 3 |
| M4 | `C9`, `C10`, `C11`, convidado em `C5` (não confirma) | 4 | `participating`: 3, `pendingConfirmation`: 1 |

`pastParticipations` fica de fora de propósito — inalcançável hoje (`CLOSED` nunca é gravado,
ver "Fora de escopo" em `spec.md`), então mesmo `C10` ("terminada por data") aparece em
`participating`, não em `pastParticipations`. `M2`–`M4` entram em `C9`/`C10`/`C11` **além** dos
3 jogadores originais de cada uma (contagens da tabela acima não incluem `M1`–`M4`).

## Estrutura de módulos/pacotes

```
blackbox-tests/
  seed/
    __main__.py     # CLI
    profiles.py     # perfis como dados (C1 a C11 e contagens)
    factories.py    # Object Mother: pedidos válidos por padrão
    flows.py        # login do admin, entrada, convite, registro
  tests/
    test_seed_factories.py
```

`blackbox-tests/features/mailbox.py` e a constante do e-mail do administrador (hoje duplicada
em `features/steps/public_competition_entry_steps.py`) movem para um módulo compartilhado
(nome exato a definir no PR) que tanto `seed/` quanto os passos de `behave` importam — evita
duas fontes de verdade para o mesmo endereço.

Interface de linha de comando — uma execução só cobre o catálogo inteiro do perfil escolhido:

```
python -m seed --profile standard --clock-offset-days -15 --seed 42
python -m seed --profile minimal --clock-offset-days -15 --dry-run
```

| Opção | Padrão | Efeito |
|---|---|---|
| `--profile` | `standard` | escolhe `minimal`, `standard` ou `volume` |
| `--clock-offset-days` | *(obrigatório, sem padrão)* | deslocamento do relógio do app; rejeita `0` e valores positivos — deve bater com o relógio de verdade do app rodando (conferido via `sentAt` de `last-email`, ver acima) |
| `--seed` | `0` | fixa nomes/distribuição no perfil `volume` |
| `--base-url` | `API_BASE_URL` | sobrescreve o endereço da API |
| `--dry-run` | desligado | imprime o plano sem chamar a API |

## Riscos e trade-offs

- **v1 não é segura para reexecução**: roda uma vez contra um `blackbox` recém-subido, sem
  checar antes se cada item do catálogo já existe. Rodar de novo sobre o mesmo banco duplica
  tudo — recomeçar é `docker compose down -v` (ver "Fora de escopo" em `spec.md`), não uma
  limitação a resolver nesta v1.
- **`--clock-offset-days` não confere sozinho que o app está de fato com aquele relógio** — só
  compara contra o `sentAt` do último `last-email` depois de uma chamada; se o app subir com o
  relógio errado, o script só percebe depois de já ter tentado criar algo com uma `startDate`
  inconsistente. Aceitável para uma ferramenta de uso manual, documentado aqui em vez de
  resolvido com uma checagem própria.
- **Escolher um deslocamento sem folga suficiente confunde "terminada" com "em andamento"** —
  com `C10` em 10 dias e `C9`/`C11` em 60, qualquer `--clock-offset-days` entre aproximadamente
  `−11` e `−59` mantém a separação; fora dessa faixa (ex. `−5`) `C10` deixaria de estar
  "terminada", e um deslocamento muito profundo (ex. `−100`) faria até `C9`/`C11` acabarem.
  Não há checagem automática disso — documentado aqui, não validado pelo script nesta v1.
- **Perfil `volume` (N jogadores)**: cada jogador custa três chamadas de API mais uma leitura
  de e-mail — para N grande, a execução é sequencial (ver "Requisitos não-funcionais") e pode
  demorar; não paralelizado nesta v1.
