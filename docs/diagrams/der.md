# DER — Jogo de Ações

Modelo de entidades e relacionamentos derivado dos arquivos `.feature` em `src/test/resources/features`:
`create_competition`, `login`, `manage_competition_players`, `request_competition_entry`.

```mermaid
erDiagram
    USER {
        int id PK
        string name
        string email
        boolean registered
    }
    ROLE {
        int id PK
        string name "ADMINISTRATOR, PLAYER (futuro: PAYING_CREATOR, SUPPORT, ...)"
    }
    USER_ROLE {
        int user_id FK
        int role_id FK
        datetime assigned_at
    }
    COMPETITION {
        int id PK
        string name
        CompetitionType type "PUBLIC, PRIVATE"
        date start_date
        int duration_days
        boolean recurring
        decimal buy_fee
        decimal sell_fee
        CompetitionStatus status "AWAITING_INVITES, OPEN, CLOSED"
        int creator_id FK
    }
    PARTICIPATION {
        int id PK
        int competition_id FK
        int user_id FK
        string email
        ParticipationStatus status "EMAIL_NOT_SENT, EMAIL_SENT, LINK_CLICKED, IN_COMPETITION"
        RequestType request_type "INVITE, REQUEST"
        date first_email_sent_date
        date joined_at
    }
    LOGIN_LINK {
        int id PK
        string token
        string email
        datetime email_sent_at
        datetime expires_at
        datetime used_at
        datetime invalidated_at
        int user_id FK
        int participation_id FK
    }
    LOGIN_SESSION {
        int id PK
        int user_id FK
        int login_link_id FK
        string device_id
        datetime created_at
        datetime ended_at
    }

    USER          ||--o{ USER_ROLE      : has
    ROLE          ||--o{ USER_ROLE      : "assigned as"
    USER          ||--o{ COMPETITION    : creates
    COMPETITION   ||--o{ PARTICIPATION  : has
    USER          |o--o{ PARTICIPATION  : joins
    USER          |o--o{ LOGIN_LINK     : receives
    PARTICIPATION |o--o{ LOGIN_LINK     : triggers
    USER          ||--o{ LOGIN_SESSION  : has
    LOGIN_LINK    ||--o| LOGIN_SESSION  : establishes
```

## Notas de modelagem

- **USER** unifica o que antes eram `ADMINISTRATOR` e `PLAYER` — as duas entidades tinham praticamente os mesmos atributos, e hoje o único papel que pode criar competições é o administrador, mas a regra de negócio já prevê evoluir para usuários pagantes e, futuramente, suporte. Modelar isso como atributo fixo do usuário exigiria migração de schema a cada novo papel; com **ROLE** + **USER_ROLE** (N:N), um usuário pode acumular papéis (ex.: jogador que também é criador pagante) sem alterar a estrutura.
- Quem pode acessar a tela de criação de competição (`create_competition.feature`, cenário "Non-administrator tries to access...") é uma regra de permissão avaliada sobre `USER_ROLE`, não uma restrição estrutural do DER — por isso `COMPETITION.creator_id` aponta para `USER`, não para um tipo específico.
- **PARTICIPATION** continua sendo a entidade associativa entre `USER` e `COMPETITION` — carrega o e-mail e o status do convite/pedido de entrada (`manage_competition_players.feature`, linhas 11–75). `user_id` é opcional porque um convidado/solicitante pode existir na lista antes de ter uma conta registrada.
- `request_type` distingue convite do administrador (competição privada) de pedido de entrada do próprio jogador (competição pública), conforme `request_competition_entry.feature` e `create_competition.feature` (envio de convites).
- **LOGIN_LINK** modela o link mágico de `login.feature`. Unificar `ADMINISTRATOR`/`PLAYER` em `USER` também simplificou esta entidade: antes tinha três FKs opcionais mutuamente exclusivas (`player_id`, `administrator_id`, `participation_id`); agora são só duas — `user_id` (pedido de login direto de um usuário já registrado, qualquer papel) ou `participation_id` (confirmação de entrada numa competição, inclusive para quem ainda não tem conta).
- `PARTICIPATION.first_email_sent_date` guarda só a data do **primeiro** e-mail (não é mais atualizada a reenvios) — cada reenvio agora gera um novo `LOGIN_LINK`, e é o `email_sent_at` **desse** link que registra quando aquele envio específico aconteceu. `LOGIN_LINK.invalidated_at` marca quando um link deixou de valer antes do vencimento natural (`expires_at`) — por ser substituído por um link mais novo, ou por ter sido usado no dispositivo errado (ver regras abaixo).
- **Um link só pode logar no dispositivo que o usou primeiro.** Usar o mesmo link novamente em outro dispositivo é rejeitado — a exceção é quando o jogador já está logado no dispositivo atual: nesse caso não há erro, já que não é um login de fato (só redireciona para onde ele já está). Isso é avaliado combinando `LOGIN_LINK.used_at`/`invalidated_at` com `LOGIN_SESSION.device_id`, não uma restrição estrutural do DER.
- **Só um `LOGIN_LINK` fica ativo por vez por jogador** — gerar um novo (ex.: reenvio) invalida qualquer link anterior ainda não usado/expirado desse jogador (`invalidated_at` preenchido no anterior).
- **LOGIN_SESSION** é o log de sessões/dispositivos logados, criado para sustentar um limite de dispositivos simultâneos por usuário (`login.feature`). Cada uso bem-sucedido de um `LOGIN_LINK` cria no máximo uma `LOGIN_SESSION` (por isso `LOGIN_LINK ||--o| LOGIN_SESSION`). O **valor do limite** é configuração do sistema, não um dado modelado aqui; o que acontece ao ser excedido (ex.: encerrar a sessão mais antiga) é regra de negócio a confirmar antes da Iteração 3 — ver `docs/iteracao-2.md`.
- Regras de validação (data no passado, taxa negativa, e-mail duplicado/inválido, captcha) são regras de negócio, não entidades, e por isso não aparecem no DER.
- Não modelado (fora do escopo atual): unicidade de `(competition_id, email)` em `PARTICIPATION` e o relacionamento entre edições de uma competição recorrente — ambos regras/decisões de implementação a definir antes da próxima iteração.

## Enums

Campos String com conjunto fixo de valores viraram tipos `enum`, com constantes na convenção Java (`UPPER_SNAKE_CASE`):

| Enum | Constantes |
|---|---|
| `CompetitionType` | `PUBLIC`, `PRIVATE` |
| `CompetitionStatus` | `AWAITING_INVITES`, `OPEN`, `CLOSED` |
| `ParticipationStatus` | `EMAIL_NOT_SENT`, `EMAIL_SENT`, `LINK_CLICKED`, `IN_COMPETITION` |
| `RequestType` | `INVITE`, `REQUEST` |

`ROLE` não é um enum fixo (é uma tabela) justamente para permitir adicionar novos papéis sem alterar código; hoje o catálogo tem apenas `ADMINISTRATOR` e `PLAYER`.
