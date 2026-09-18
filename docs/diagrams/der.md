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
    LINK_RECORD {
        int id PK
        string token
        string service_key "identifies which LinkHandler module consumes this link"
        int user_id "no FK -- set by whichever module created the link, see note below"
        string email
        string extra_json "opaque per-service_key data, e.g. a participationId"
        datetime email_sent_at
        datetime expires_at
        datetime used_at
        datetime invalidated_at
    }
    LOGIN_SESSION {
        int id PK
        int user_id "no FK -- see LINK_RECORD's note"
        int link_record_id FK
        string device_id
        datetime created_at
        datetime ended_at
    }
    LOG {
        int id PK
        int related_object_id
        int user_id FK
        datetime created_at
        LogType log_type
        string message
    }
    SENT_EMAIL {
        int id PK
        int user_id FK
        string email
        string link
        EmailTemplate template
        datetime sent_at
    }
    DISPOSABLE_DOMAIN {
        int id PK
        string domain
        date added_at
    }

    USER          ||--o{ USER_ROLE      : has
    ROLE          ||--o{ USER_ROLE      : "assigned as"
    USER          ||--o{ COMPETITION    : creates
    COMPETITION   ||--o{ PARTICIPATION  : has
    USER          |o--o{ PARTICIPATION  : joins
    LINK_RECORD   ||--o| LOGIN_SESSION  : establishes
    USER          |o--o{ LOG            : performs
    USER          |o--o{ SENT_EMAIL     : receives
```

## Notas de modelagem

- **USER** unifica o que antes eram `ADMINISTRATOR` e `PLAYER` — as duas entidades tinham praticamente os mesmos atributos, e hoje o único papel que pode criar competições é o administrador, mas a regra de negócio já prevê evoluir para usuários pagantes e, futuramente, suporte. Modelar isso como atributo fixo do usuário exigiria migração de schema a cada novo papel; com **ROLE** + **USER_ROLE** (N:N), um usuário pode acumular papéis (ex.: jogador que também é criador pagante) sem alterar a estrutura.
- Quem pode acessar a tela de criação de competição (`create_competition.feature`, cenário "Non-administrator tries to access...") é uma regra de permissão avaliada sobre `USER_ROLE`, não uma restrição estrutural do DER — por isso `COMPETITION.creator_id` aponta para `USER`, não para um tipo específico.
- **PARTICIPATION** continua sendo a entidade associativa entre `USER` e `COMPETITION` — carrega o e-mail e o status do convite/pedido de entrada (`manage_competition_players.feature`, linhas 11–75). `user_id` é opcional porque um convidado/solicitante pode existir na lista antes de ter uma conta registrada.
- `request_type` distingue convite do administrador (competição privada) de pedido de entrada do próprio jogador (competição pública), conforme `request_competition_entry.feature` e `create_competition.feature` (envio de convites).
- **LINK_RECORD** modela o link mágico de `login.feature` — revisado em `specs/05-003-desacoplamento-login-link` para ser um mecanismo genérico (`LinkRouter`/`LinkHandler`) que o módulo `link` não amarra a nenhum consumidor específico. Por isso `user_id` **não é FK** (nem para `USER`, nem `participation_id` para `PARTICIPATION` — esse não existe mais como coluna própria): quem cria o link (`login` para login avulso, `competition` para convite/pedido de entrada) grava o `user_id` que já resolveu, e qualquer outro dado específico daquele consumidor (ex. um `participationId`) vai dentro de `extra_json`, opaco para `link`. `service_key` é o que permite ao `LinkRouter` despachar para a implementação correta ao consumir o link.
- `PARTICIPATION.first_email_sent_date` guarda só a data do **primeiro** e-mail (não é mais atualizada a reenvios) — cada reenvio agora gera um novo `LINK_RECORD`, e é o `email_sent_at` **desse** link que registra quando aquele envio específico aconteceu. `LINK_RECORD.invalidated_at` marca quando um link deixou de valer antes do vencimento natural (`expires_at`) — por ser substituído por um link mais novo, ou por ter sido usado no dispositivo errado (ver regras abaixo).
- **Um link só pode logar no dispositivo que o usou primeiro.** Usar o mesmo link novamente em outro dispositivo é rejeitado — a exceção é quando o jogador já está logado no dispositivo atual: nesse caso não há erro, já que não é um login de fato (só redireciona para onde ele já está). "Mesmo dispositivo" aqui é decidido pela sessão HTTP autenticada (cookie de sessão, checada via `SecurityContext`) já existir para aquele usuário no momento do clique — não por comparar `LOGIN_SESSION.device_id`, que é só um rótulo de exibição (User-Agent Client Hints quando disponíveis, spec 05-009) sem papel nessa decisão. Combinado com `LINK_RECORD.used_at`/`invalidated_at`, não uma restrição estrutural do DER.
- **Só um `LINK_RECORD` fica ativo por vez por usuário** (login avulso) — gerar um novo (ex.: reenvio) invalida qualquer link anterior ainda não usado/expirado desse usuário (`invalidated_at` preenchido no anterior). Links de competição (convite/pedido de entrada) não seguem essa regra — reenviar um convite não invalida o anterior, comportamento já existente antes da 05-003.
- **LOGIN_SESSION** é o log de sessões/dispositivos logados, criado para sustentar um limite de dispositivos simultâneos por usuário (`login.feature`). Cada uso bem-sucedido de um `LINK_RECORD` cria no máximo uma `LOGIN_SESSION` (por isso `LINK_RECORD ||--o| LOGIN_SESSION`). O **valor do limite** é configuração do sistema, não um dado modelado aqui; ao ser excedido, a sessão mais antiga é encerrada. `LOGIN_SESSION.user_id` segue a mesma decisão de `LINK_RECORD.user_id` (sem FK) pela mesma razão — `LOGIN_SESSION` também mora no módulo `link`.
- **LOG** é o registro de auditoria do sistema. `related_object_id` aponta pro registro que originou o evento (qual tabela é dada pelo `log_type`, não por uma FK — a referência é polimórfica, então não há restrição estrutural de integridade referencial nesse campo). `user_id` é opcional: nem todo evento de log é iniciado por um usuário (ex.: um job de sistema). O papel de banco da aplicação (`jogo_acoes_app`, ver `docker-compose.yml`) só tem `SELECT`/`INSERT` em `LOG` — nunca `UPDATE`/`DELETE`, pra manter o log imutável.
- **SENT_EMAIL** registra cada e-mail enviado. `user_id` é opcional pelo mesmo motivo de `PARTICIPATION`: o destinatário pode ainda não ter conta. `link` é o link enviado (convite, confirmação de registro ou login) e `template` (`EmailTemplate`) identifica qual modelo foi usado. Mesma regra de imutabilidade de `LOG`: `jogo_acoes_app` só tem `SELECT`/`INSERT`.
- Regras de validação (data no passado, taxa negativa, e-mail duplicado/inválido, captcha) são regras de negócio, não entidades, e por isso não aparecem no DER.
- Não modelado: unicidade de `(competition_id, email)` em `PARTICIPATION` e o relacionamento entre edições de uma competição recorrente.
- **DISPOSABLE_DOMAIN** (não relacionada a nenhuma outra entidade por FK — é uma lista de bloqueio consultada pelo domínio de um e-mail, não pelo e-mail em si) guarda os domínios de e-mail descartáveis/temporários usados na verificação de e-mail antes do cadastro (convite, pedido de entrada, pedido de login): `id`, `domain`, `added_at`. Atualizada uma vez por dia a partir de uma lista pública mantida em ordem alfabética.

## Enums

Campos String com conjunto fixo de valores viraram tipos `enum`, com constantes na convenção Java (`UPPER_SNAKE_CASE`):

| Enum | Constantes |
|---|---|
| `CompetitionType` | `PUBLIC`, `PRIVATE` |
| `CompetitionStatus` | `AWAITING_INVITES`, `OPEN`, `CLOSED` |
| `ParticipationStatus` | `EMAIL_NOT_SENT`, `EMAIL_SENT`, `LINK_CLICKED`, `IN_COMPETITION` |
| `RequestType` | `INVITE`, `REQUEST` |
| `LogType` | catálogo inicial/placeholder — ver nota abaixo |
| `EmailTemplate` | `INVITE`, `REGISTRATION_LINK`, `LOGIN_LINK` |

`ROLE` não é um enum fixo (é uma tabela) justamente para permitir adicionar novos papéis sem alterar código; hoje o catálogo tem apenas `ADMINISTRATOR` e `PLAYER`.

`LogType` é diferente dos outros enums: cada constante carrega, além do nome, uma descrição
e a tabela relacionada (`related_object_id` refere-se a ela). Catálogo por tabela sofreria o
mesmo problema — adicionar um tipo novo exige alterar código de qualquer forma, já que o
`log_type` decide como interpretar `related_object_id` — então um enum é mais simples que
uma tabela de catálogo aqui. O conjunto de valores em `LogType.java`: `COMPETITION_CREATED`,
`PARTICIPATION_STATUS_CHANGED` (inclui a remoção de um jogador — não existe um
`ParticipationStatus` de "removido", é uma exclusão física, não uma mudança de status, mas a
mensagem do log deixa isso explícito) e `LOGIN_LINK_ISSUED`.
