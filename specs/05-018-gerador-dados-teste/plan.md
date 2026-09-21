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
| Participação LINK_CLICKED | Não | o enum existe, nada grava esse status |
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
| Perfis | `minimal` (`C1`, `C2`, `C4`, relógio "hoje"), `standard` (`C1`–`C11`, três fases de relógio), `volume` (`standard` + uma pública com N jogadores) | resolvida | Cobre do teste rápido no Swagger UI até listas longas para filtro, sem forçar todo mundo a gerar a massa completa. |
| **Idempotência de competições** | `GET /competitions/mine` (campo `created`, spec 05-017) filtrado pelo prefixo `[seed]` — não um manifesto local | resolvida (depende da 05-017) | A 05-017 existe justamente para isso: hoje o administrador não tem como listar as próprias competições, e é por isso que a ideia original (manifesto JSON local) existia — um manifesto perdido duplicaria as privadas, um problema que a API já resolve quando a 05-017 estiver implementada. Enquanto a 05-017 não é mesclada, esta spec não tem uma via de API para checar idempotência de competições privadas — ver "Decisões em aberto" em `spec.md`. |
| **Idempotência de jogadores** | E-mail determinístico por posição no catálogo (`success+seed-c<N>-jogador-<i>@simulator.amazonses.com`, nunca um contador solto) + `GET /blackbox/last-email` antes de criar (`404` = nunca criado, então cria; existe = pula) | resolvida | Sem manifesto: o endereço já é a chave de idempotência, e `last-email` já existe (spec 05-014) para outro propósito, reaproveitado aqui. O prefixo por competição (`c<N>`) evita que dois slots de catálogos diferentes colidam no mesmo endereço. |
| Manifesto local | Não existe mais como fonte de verdade — só um log opcional (`--manifest`, para inspeção humana, nunca lido de volta pelo script) | resolvida | Substitui a ideia original (manifesto como única fonte de verdade) pelas duas linhas acima — remove o risco "perder o manifesto duplica a massa" que a ideia original tinha. |
| Nomes de jogador | Lista fixa de nomes (`Object Mother`), ciclada por posição — não sorteada, exceto no perfil `volume` | resolvida | Determinismo por posição é mais simples de depurar que aleatoriedade sem motivo; `volume` é o único caso com volume grande o bastante para gerar nomes repetitivos, por isso sorteia com `--seed`. |
| Como avançar o relógio | O script não sobe/derruba o app — só recebe `--clock-offset-days` e roda a fatia do catálogo daquele deslocamento; subir o app com o relógio certo continua manual, seguindo o `README.md` | resolvida | Mesmo raciocínio já registrado no `README.md` — automatizar o Docker acopla o script a ele; fica fora de escopo (ver `spec.md`). |
| `"terminada por data"` com status `OPEN` | Aceito na v1, sem renomear | resolvida | `GET /competitions/public` realmente devolve isso hoje (confirmado no código) — não é um artifício do script, é o comportamento real da API; renomear fica para quando a Etapa de fechamento por data existir (roadmap, iterações 8–9). |
| Estrutura/local do código | Dentro de `blackbox-tests/` (`seed/`), reaproveitando o cliente gerado e o `pyproject.toml` já existentes | resolvida | Mesmo raciocínio da spec 05-015 — projeto Python independente, sem duplicar setup. `features/mailbox.py` e a constante do e-mail do administrador (`blackbox-tests/features/steps/public_competition_entry_steps.py`) movem para um módulo compartilhado que tanto `seed/` quanto `behave` importam (ver "Estrutura de módulos/pacotes"). |

Catálogo (perfil `standard`; `minimal` = `C1`, `C2`, `C4` só na fase "hoje"; `volume` = tudo
isso mais uma pública com N jogadores):

| Código | Tipo | Como é criada | Participações | Relógio |
|---|---|---|---|---|
| C1 | PUBLIC, OPEN | `POST /competitions` | nenhuma | hoje |
| C2 | PUBLIC, OPEN | C1 + entrada de 5 jogadores novos | 5 IN_COMPETITION | hoje |
| C3 | PUBLIC recorrente, OPEN | `recurring: true` + 2 jogadores | 2 IN_COMPETITION | hoje |
| C4 | PRIVATE, AWAITING_INVITES | 3 e-mails + `later` | 3 EMAIL_NOT_SENT | hoje |
| C5 | PRIVATE, OPEN | 3 e-mails + `now` | 3 EMAIL_SENT | hoje |
| C6 | PRIVATE, OPEN | 3 e-mails + `now`; 2 convidados registram | 2 IN_COMPETITION, 1 EMAIL_SENT | hoje |
| C7 | PUBLIC de limites, OPEN | taxas 0, duração 1 dia, início amanhã | nenhuma | hoje |
| C8 | PRIVATE, OPEN | convida jogadores já registrados + `now` | 2 EMAIL_SENT com conta (pendingConfirmation) | hoje |
| C9 | PUBLIC em andamento, OPEN | início no dia seguinte ao relógio, 60 dias, 3 jogadores | 3 IN_COMPETITION | −30 dias |
| C10 | PUBLIC terminada por data, OPEN | início no dia seguinte ao relógio, 10 dias, 3 jogadores | 3 IN_COMPETITION | −60 dias |
| C11 | PRIVATE em andamento, OPEN | 3 e-mails + `now`, 60 dias; 2 convidados registram | 2 IN_COMPETITION, 1 EMAIL_SENT | −30 dias |

Fases rodam da mais antiga para a mais nova (`−60`, `−30`, `hoje`) — `last-email` devolve o
e-mail de maior `sentAt` por endereço, e o administrador recebe um link em toda fase; rodar uma
fase antiga depois de uma nova faria o script ler o link errado. `startDate` se calcula sobre o
relógio do app no momento da chamada, não sobre o relógio do script — com o app em `−30` dias,
`startDate` precisa ser depois desse "hoje" deslocado; o `--clock-offset-days` do script serve
só para escolher a fatia do catálogo e checar (via `sentAt` de `last-email`) que o relógio do
app bate com o esperado. Links (7 dias) e sessões HTTP (30 dias) criados nas fases passadas já
vencem no presente — não atrapalha o script, mas quem testar à mão depois faz login de novo.

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

Interface de linha de comando:

```
python -m seed --profile standard --clock-offset-days -60 --seed 42
python -m seed --profile standard --clock-offset-days -30 --seed 42
python -m seed --profile standard --clock-offset-days 0 --seed 42
python -m seed --profile minimal --dry-run
```

| Opção | Padrão | Efeito |
|---|---|---|
| `--profile` | `standard` | escolhe `minimal`, `standard` ou `volume` |
| `--clock-offset-days` | `0` | deslocamento do relógio do app (deve bater com o real); roda só a fatia do catálogo dessa fase |
| `--seed` | `0` | fixa nomes/distribuição no perfil `volume` |
| `--base-url` | `API_BASE_URL` | sobrescreve o endereço da API |
| `--manifest` | `seed-manifest.json` | log opcional do que foi criado nesta execução, só para inspeção — nunca lido de volta |
| `--dry-run` | desligado | imprime o plano sem chamar a API |

## Riscos e trade-offs

- **Depende da spec 05-017 estar implementada** para o mecanismo de idempotência de
  competições funcionar como desenhado — ver "Decisões em aberto" em `spec.md`.
- **Sem manifesto como fonte de verdade**, a idempotência de jogadores depende inteiramente de
  `GET /blackbox/last-email` responder de forma consistente — já é o comportamento hoje
  (`sent_email` é insert-only, `findTopByEmailOrderBySentAtDesc`), mas qualquer mudança futura
  nesse endpoint (spec 05-014) precisa preservar essa garantia.
- **`--clock-offset-days` não confere sozinho que o app está de fato com aquele relógio** — só
  compara contra o `sentAt` do último `last-email` depois de uma chamada; se o app subir com o
  relógio errado, o script só percebe depois de já ter tentado criar algo com uma `startDate`
  inconsistente. Aceitável para uma ferramenta de uso manual, documentado aqui em vez de
  resolvido com uma checagem própria.
- **Perfil `volume` (N jogadores)**: cada jogador custa três chamadas de API mais uma leitura
  de e-mail — para N grande, a execução é sequencial (ver "Requisitos não-funcionais") e pode
  demorar; não paralelizado nesta v1.
