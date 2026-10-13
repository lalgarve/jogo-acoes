# Caderno de testes manuais

Roteiros de teste manual organizados pelas funcionalidades do sistema: login e competições.
Cada roteiro parte da massa de dados versionada em `caderno-de-testes/dados/` e traz, em cada
passo, o corpo JSON pronto para copiar, a resposta esperada, o trecho de log que confirma que a
chamada passou pelo controller e pelos repositórios certos, e o `SELECT` que confirma o estado
gravado no banco (spec 05-040).

Os logs e os resultados de `SELECT` foram capturados executando o sistema de verdade sobre a
massa restaurada, em 2026-10-12. As linhas de log foram encurtadas com `…`; os ids e horários
vão mudar de uma execução para outra, salvo quando o texto diz que são fixos.

## Preparação

### 1. Gerar os dados de teste com Python

Só é preciso para recriar a massa (por exemplo, depois de uma migration nova). Quem só vai
executar o caderno pula para o item 2.

A massa é criada pelo gerador Python da spec 05-018 (`blackbox-tests/seed/`), que fala com o
sistema só pela API, como um cliente de verdade. As competições precisam parecer antigas (uma já
terminada, outra em andamento), então o `app` sobe com o relógio 30 dias no passado
(`scripts/blackbox-clock-offset.sh`) e o gerador cria tudo com esse relógio.

Pré-requisito: o ambiente Python do `blackbox-tests` pronto, como descrito em
`blackbox-tests/README.md` (cliente gerado a partir de `docs/openapi.yaml` e `pip install -e .`).

No WSL, a partir da raiz do repositório:

```bash
docs/disciplina/caderno-de-testes/scripts/gerar-massa.sh
```

O script apaga os volumes do Compose (pede confirmação), sobe a infraestrutura, sobe o `app`
com o relógio deslocado, roda `python -m seed --profile standard --clock-offset-days -30`, grava
o dump em `caderno-de-testes/dados/jogo-acoes-massa.sql` e o restaura. Cada passo aparece na
tela.

A massa tem 11 competições `[seed] C1` a `[seed] C11` criadas pelo administrador e 21 jogadores.
Os que os roteiros usam:

| Papel nos roteiros | Jogador | Públicas | Privadas | Observação |
|---|---|---|---|---|
| Jogador A | `success+seed-multi-m3@simulator.amazonses.com` | 2 | 1 | C9, C10, C11 |
| Jogador B | `success+seed-c6-jogador-1@simulator.amazonses.com` | 0 | 1 | C6 |
| Jogador C | `success+seed-c2-jogador-1@simulator.amazonses.com` | 1 | 0 | C2, convite pendente em C8 |
| Jogador com histórico | `success+seed-multi-m4@simulator.amazonses.com` | 2 | 1 | C9, C10, C11 e convite pendente em C5 |
| Jogador já registrado | `success+seed-c3-jogador-1@simulator.amazonses.com` | 1 | 0 | só C3 |

O arquivo `caderno-de-testes/dados/verificar-massa.sql` confere se a massa tem o que os roteiros
precisam; os scripts de geração e de restauração rodam essa verificação no fim.

### 2. Inicializar os dados de teste com o dump

Com o Postgres do Compose no ar, carregar o dump apaga o schema `jogo_acoes` e o recria com a
massa. No WSL:

```bash
docs/disciplina/caderno-de-testes/scripts/restaurar-massa.sh
```

No PowerShell:

```powershell
.\docs\disciplina\caderno-de-testes\scripts\restaurar-massa.ps1
```

O script para o `app`, confere se o dump foi gerado com a última migration do repositório
(recusa e manda regenerar se não foi), carrega o dump e mostra a verificação:

```
==> 1. Conferindo a versão do dump (…/caderno-de-testes/dados/jogo-acoes-massa.sql)
    dump gerado na migration V9 (2026-10-12); repositório na V9
==> 2. Parando o app
==> 3. Apagando o schema jogo_acoes e carregando o dump
    massa carregada
==> 4. Conferindo a massa (…/caderno-de-testes/dados/verificar-massa.sql)
                                  verificacao                                    | ok
---------------------------------------------------------------------------------+----
 Roteiro 1: ao menos 3 combinações diferentes de competições públicas/privadas   | t
 Roteiro 7: jogador em competição terminada por data, em andamento e com …       | t
 Roteiro 6: competição privada com convite pendente                              | t
 Roteiros 2 e 3: nenhuma sessão de login aberta (o dump não leva sessões)        | t
 Administrador semeado existe                                                    | t
```

O dump não leva as sessões de login: se levasse, elas ocupariam as vagas de dispositivo dos
jogadores e os roteiros 2 e 3 mostrariam contagens erradas. Restaurar o dump antes de cada
execução completa do caderno faz os ids citados nos roteiros 4 a 6 baterem.

### 3. Subir o sistema e acessar os programas auxiliares

O caderno roda no ambiente `blackbox` (`docker-compose.blackbox.yml` por cima do
`docker-compose.yml`), com o relógio real. É o único ambiente que aceita o captcha vazio,
necessário no roteiro 4. A partir da raiz do repositório, no WSL:

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose up -d --wait db db-email-service localstack
./scripts/test-api-key.sh restore
docker compose up -d --wait email-service
./scripts/test-api-key.sh restore
docs/disciplina/caderno-de-testes/scripts/restaurar-massa.sh
docker compose up -d app adminer ses-viewer
```

O `test-api-key.sh restore` roda duas vezes pelo mesmo motivo do `README.md` ("Como rodar os
testes"): a segunda vez define o remetente do cliente `jogo-acoes`, numa tabela que só existe
depois que o `email-service` sobe.

| Programa | Endereço | Para quê |
|---|---|---|
| Swagger UI | `http://localhost:8080/api/swagger-ui/index.html` | chamar a API |
| Adminer | `http://localhost:8081` (sistema PostgreSQL, servidor `db`, usuário e senha `jogo_acoes_admin`, base `jogo_acoes`) | rodar os `SELECT`s |
| E-mails enviados (JSON) | `http://localhost:4566/_aws/ses` | ver os e-mails no SES do LocalStack |
| Visualizador de e-mails | `http://localhost:3005` | mesma informação, em tela |
| Logs da aplicação | `docker compose logs -f app` | conferir os trechos de log |

O `search_path` do banco já aponta para o schema `jogo_acoes`, então os `SELECT`s funcionam sem
prefixo.

**Como "clicar no link do e-mail".** O link nos e-mails é absoluto
(`http://localhost:8080/api/login-links/<token>`, spec 05-041) e abre direto: no `ses-viewer`
(`http://localhost:3005`), abrir o e-mail e clicar no link. O navegador chama
`GET /login-links/{token}`, a sessão fica no cookie desse navegador e as chamadas seguintes no
Swagger UI já saem autenticadas. O mesmo link aparece no campo `TemplateData` da mensagem em
`http://localhost:4566/_aws/ses` e na tabela de e-mails enviados:

```sql
SELECT link, template, sent_at
FROM sent_email
WHERE email = '<email do jogador>'
ORDER BY id DESC
LIMIT 1;
```

## Login

### Roteiro 1 — Logar como administrador

**Objetivo:** o administrador entra pelo link do e-mail, lista todos os jogadores e, depois do
logout, perde o acesso.

**Pré-condições:** massa restaurada; navegador sem sessão.

**1. Pedir o e-mail de login do administrador.** `POST /login-requests`:

```json
{
  "email": "success+admin@simulator.amazonses.com"
}
```

Resposta: `202`, sem corpo (a resposta é a mesma se o e-mail não existir).

```
DEBUG ... ControllerLoggingAspect : --> LoginController.requestLoginLink({"email":"success+admin@simulator.amazonses.com"})
DEBUG ... RepositoryLoggingAspect : --> UserRepository.findByEmail(success+admin@simulator.amazonses.com)
DEBUG ... RepositoryLoggingAspect : <-- UserRepository.findByEmail returned {"email":"success+admin@simulator.amazonses.com","id":1,"name":"Administrator","registered":true}
DEBUG ... RepositoryLoggingAspect : --> LinkRecordRepository.save({"email":"success+admin@simulator.amazonses.com", … "serviceKey":"login","token":"bc67c23f-…","usedAt":null,"userId":1})
DEBUG ... EmailServiceLoggingAspect : --> email-service send login-link to success+admin@simulator.amazonses.com
DEBUG ... EmailServiceLoggingAspect : <-- email-service accepted send bd88b744-…
DEBUG ... RepositoryLoggingAspect : --> SentEmailRepository.save({"email":"success+admin@simulator.amazonses.com", … "link":"http://localhost:8080/api/login-links/bc67c23f-…","template":"LOGIN_LINK", …})
DEBUG ... ControllerLoggingAspect : <-- LoginController.requestLoginLink returned null
```

```sql
SELECT id, email, token, email_sent_at, expires_at, used_at
FROM link_record
WHERE email = 'success+admin@simulator.amazonses.com'
ORDER BY id DESC
LIMIT 1;
```

Uma linha nova com `used_at` vazio e validade de 7 dias.

**2. Clicar no link do e-mail.** Pegar o token como em "Como clicar no link do e-mail" e chamar
`GET /login-links/{token}`. Resposta: `200`, `{"redirectTo":"/admin"}`.

```
DEBUG ... ControllerLoggingAspect : --> LoginController.consumeLoginLink(bc67c23f-…, …)
DEBUG ... RepositoryLoggingAspect : --> LinkRecordRepository.findByToken(bc67c23f-…)
DEBUG ... RepositoryLoggingAspect : --> LinkRecordRepository.save({… "usedAt":"2026-10-12T22:00:33.998344138","userId":1})
DEBUG ... RepositoryLoggingAspect : --> LoginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(1)
DEBUG ... RepositoryLoggingAspect : <-- LoginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc returned []
DEBUG ... RepositoryLoggingAspect : --> LoginSessionRepository.save({"createdAt":"2026-10-12T22:00:34.004725487","deviceId":"…","endedAt":null, …})
DEBUG ... ControllerLoggingAspect : <-- LoginController.consumeLoginLink returned {"redirectTo":"/admin"}
```

**3. Listar todos os jogadores.** `POST /players/search`, sem filtro, primeira página com o
tamanho máximo:

```json
{
  "page": 0,
  "size": 100
}
```

Resposta: `200`, `total` 22 (o administrador e os 21 jogadores da massa), cada item com as
contagens:

```json
{"email":"success+seed-multi-m3@simulator.amazonses.com","name":"Seed Multi M3","ownedCount":0,"privateCount":1,"publicCount":2,"userId":21}
```

```
DEBUG ... ControllerLoggingAspect : --> PlayerDirectoryController.searchPlayers({"page":0,"size":100})
DEBUG ... RepositoryLoggingAspect : --> ParticipationRepository.searchPlayerSummaries(null, null, null, {"offset":0,"pageNumber":0,"pageSize":100, …})
DEBUG ... RepositoryLoggingAspect : <-- ParticipationRepository.searchPlayerSummaries returned {"content":[{"userId":1,"name":"Administrator", …
DEBUG ... ControllerLoggingAspect : <-- PlayerDirectoryController.searchPlayers returned {"items":[ …
```

**4. Escolher três jogadores com números diferentes de competições públicas e privadas.** Os
roteiros seguintes usam os jogadores A (2 públicas, 1 privada), B (0 e 1) e C (1 e 0) da tabela
da preparação. O `SELECT` que dá as mesmas contagens:

```sql
SELECT u.email,
       count(*) FILTER (WHERE c.type = 'PUBLIC')  AS publicas,
       count(*) FILTER (WHERE c.type = 'PRIVATE') AS privadas
FROM app_user u
JOIN participation p ON p.user_id = u.id AND p.status = 'IN_COMPETITION'
JOIN competition c ON c.id = p.competition_id
WHERE u.email IN ('success+seed-multi-m3@simulator.amazonses.com',
                  'success+seed-c6-jogador-1@simulator.amazonses.com',
                  'success+seed-c2-jogador-1@simulator.amazonses.com')
GROUP BY u.email;
```

**5. Fazer logout.** `DELETE /sessions/current`. Resposta: `204`.

```
DEBUG ... ControllerLoggingAspect : --> SessionsController.logoutCurrentSession()
DEBUG ... RepositoryLoggingAspect : --> LoginSessionRepository.findByHttpSessionIdAndEndedAtIsNull(e0ae2aa8-…)
DEBUG ... RepositoryLoggingAspect : --> LoginSessionRepository.save({… "endedAt":"2026-10-12T22:00:40.995806492", …})
DEBUG ... ControllerLoggingAspect : <-- SessionsController.logoutCurrentSession returned null
```

```sql
SELECT id, user_id, device_id, created_at, ended_at
FROM login_session
WHERE user_id = 1;
```

```
 id | user_id | device_id | created_at                 | ended_at
----+---------+-----------+----------------------------+----------------------------
 32 |       1 | …         | 2026-10-12 22:00:34.004725 | 2026-10-12 22:00:40.995806
```

**6. Listar todos os jogadores de novo.** Mesmo `POST /players/search` do passo 3. Resposta:
`401`, `{"message":"Not logged in"}`. Nenhuma linha de log: a requisição é barrada pela
segurança antes de chegar ao controller.

### Roteiro 2 — Deslogar de outro dispositivo

**Objetivo:** o jogador encerra, de um navegador, a sessão aberta em outro.

**Pré-condições:** dois navegadores diferentes (por exemplo, Chrome e Edge, ou uma janela anônima
como navegador 2), ambos com o Swagger UI aberto; jogador A sem sessão.

**1. Navegador 1: pedir o e-mail de login do jogador A.** `POST /login-requests`:

```json
{
  "email": "success+seed-multi-m3@simulator.amazonses.com"
}
```

**2. Navegador 1: clicar no link do e-mail.** `GET /login-links/{token}`. Resposta: `200`,
`{"redirectTo":"/competitions/mine"}`.

**3. Navegador 1: listar as competições do jogador A.** `GET /competitions/mine`. Resposta: `200`:

```json
{
  "participating": [
    {"id": 9,  "name": "[seed] C9 Copa publica em andamento",       "type": "PUBLIC",  "status": "OPEN"},
    {"id": 10, "name": "[seed] C10 Copa publica terminada por data", "type": "PUBLIC",  "status": "OPEN"},
    {"id": 11, "name": "[seed] C11 Copa privada em andamento",       "type": "PRIVATE", "status": "OPEN"}
  ],
  "pastParticipations": [],
  "pendingConfirmation": [],
  "created": []
}
```

```
DEBUG ... ControllerLoggingAspect : --> CompetitionsController.listMyCompetitions()
DEBUG ... RepositoryLoggingAspect : --> UserRepository.findByEmail(success+seed-multi-m3@simulator.amazonses.com)
DEBUG ... RepositoryLoggingAspect : --> ParticipationRepository.findByUser_Id(21)
DEBUG ... RepositoryLoggingAspect : --> CompetitionRepository.findByCreator_Id(21)
DEBUG ... RepositoryLoggingAspect : <-- CompetitionRepository.findByCreator_Id returned []
DEBUG ... ControllerLoggingAspect : <-- CompetitionsController.listMyCompetitions returned {"created":[],"participating":[ …
```

**4 a 6. Navegador 2: abrir o Swagger UI, pedir o e-mail de login do jogador A e clicar no
link.** Mesmo JSON do passo 1. O pedido de um link novo invalida o link anterior ainda não usado,
mas não mexe na sessão já aberta no navegador 1.

**7. Navegador 2: listar os dispositivos logados.** `GET /sessions`. Resposta: `200`, duas
sessões, a do navegador 2 com `"current": true`:

```json
[
  {"id": 33, "deviceLabel": "Windows 15.0.0 · Chromium 131", "createdAt": "2026-10-12T22:00:53.874755Z", "current": false},
  {"id": 34, "deviceLabel": "Windows 15.0.0 · Chromium 131", "createdAt": "2026-10-12T22:00:56.079027Z", "current": true}
]
```

O rótulo vem dos Client Hints que o navegador manda sozinho (spec 05-009), então varia com o
navegador usado.

**8. Navegador 2: encerrar a sessão do navegador 1.** `DELETE /sessions/{sessionId}` com o `id`
da sessão que tem `"current": false` (33 no exemplo). Resposta: `204`.

```
DEBUG ... ControllerLoggingAspect : --> SessionsController.revokeSession(33)
DEBUG ... RepositoryLoggingAspect : --> LoginSessionRepository.findByIdAndUserId(33, 21)
DEBUG ... RepositoryLoggingAspect : <-- LoginSessionRepository.findByIdAndUserId returned {"createdAt":"2026-10-12T22:00:53.874755", … "endedAt":null, …}
DEBUG ... RepositoryLoggingAspect : --> LoginSessionRepository.save({… "endedAt":"2026-10-12T22:00:56.161309534", …})
DEBUG ... ControllerLoggingAspect : <-- SessionsController.revokeSession returned null
```

**9. Navegador 1: listar os dispositivos logados.** `GET /sessions`. Resposta: `401`,
`{"message":"Not logged in"}`. A sessão HTTP do navegador 1 foi apagada junto com o registro.

**10. Navegador 2: fazer logout.** `DELETE /sessions/current`. Resposta: `204`.

```sql
SELECT ls.id, ls.device_id, ls.created_at, ls.ended_at
FROM login_session ls
JOIN app_user u ON u.id = ls.user_id
WHERE u.email = 'success+seed-multi-m3@simulator.amazonses.com'
ORDER BY ls.id;
```

```
 id | device_id                     | created_at                 | ended_at
----+-------------------------------+----------------------------+---------------------------
 33 | Windows 15.0.0 · Chromium 131 | 2026-10-12 22:00:53.874755 | 2026-10-12 22:00:56.16131
 34 | Windows 15.0.0 · Chromium 131 | 2026-10-12 22:00:56.079027 | 2026-10-12 22:00:57.228645
```

### Roteiro 3 — Logar em mais dispositivos que o máximo permitido

**Objetivo:** o jogador pode ficar logado em até 3 dispositivos
(`login.max-devices-per-user: 3`); o quarto login encerra a sessão mais antiga.

**Introdução.** Como é preciso logar quatro vezes, cada vez com cabeçalhos de dispositivo
diferentes, fizemos scripts para o login. Há outras soluções possíveis, mas essa é a mais
simples: o Swagger UI não consegue mandar cabeçalhos `Sec-CH-UA*` diferentes dos do próprio
navegador. Os scripts ficam em `caderno-de-testes/scripts/`, para o WSL (precisa de `curl` e
`jq`) e para o PowerShell, com a mesma saída:

- `login-jogador <número> <email>` lê o arquivo `caderno-de-testes/dispositivos/<número>.json`
  para montar os cabeçalhos, pede o link de login, lê o e-mail pela API do LocalStack (se ainda
  não chegou, tenta de novo a cada 2 s, até 5 vezes), faz o login e grava o cookie de sessão em
  `caderno-de-testes/cookie<número>`.
- `dispositivos-jogador <número>` mostra os dispositivos em que o jogador está logado, usando o
  cookie do dispositivo `<número>`.

| Dispositivo | Rótulo mostrado pelo sistema |
|---|---|
| 1 | `Android 14 · Chromium 131 (mobile)` |
| 2 | `macOS 14.5.0 · Google Chrome 129` |
| 3 | `Linux 6.8.0 · Microsoft Edge 128` |

**Pré-condições:** jogador B sem sessão.

**1. Swagger UI: pedir o e-mail de login do jogador B, clicar no link e listar os
dispositivos.** `POST /login-requests`:

```json
{
  "email": "success+seed-c6-jogador-1@simulator.amazonses.com"
}
```

Depois `GET /login-links/{token}` e `GET /sessions`. Resposta: uma sessão, a do navegador
(`Windows · Google Chrome 141` no exemplo).

**2. Dispositivo 1.** No WSL, a partir de `docs/disciplina/caderno-de-testes/scripts/`:

```bash
./login-jogador.sh 1 success+seed-c6-jogador-1@simulator.amazonses.com
./dispositivos-jogador.sh 1
```

No PowerShell, `.\login-jogador.ps1 1 …` e `.\dispositivos-jogador.ps1 1`. Saída:

```
==> 1. Lendo os cabeçalhos do dispositivo 1 (…/caderno-de-testes/dispositivos/1.json)
    Sec-CH-UA: "Not_A Brand";v="24", "Chromium";v="131"
    Sec-CH-UA-Platform: "Android"
    Sec-CH-UA-Platform-Version: "14"
    Sec-CH-UA-Mobile: ?1
==> 2. Pedindo o link de login de success+seed-c6-jogador-1@simulator.amazonses.com (POST http://localhost:8080/api/login-requests)
    resposta: 202
==> 3. Lendo o e-mail no LocalStack (GET http://localhost:4566/_aws/ses)
    tentativa 1: e-mail encontrado, link http://localhost:8080/api/login-links/0a89d671-9ec5-48d8-b7c9-b2c58b21a168
==> 4. Fazendo o login como dispositivo 1 (GET http://localhost:8080/api/login-links/0a89d671-9ec5-48d8-b7c9-b2c58b21a168)
    resposta: 200 {"redirectTo":"/competitions/mine"}
    cookie de sessão gravado em …/caderno-de-testes/cookie1
==> Listando os dispositivos com o cookie do dispositivo 1 (GET http://localhost:8080/api/sessions)
    resposta: 200
    logado em 2 dispositivo(s):
    - sessão 28: Windows · Google Chrome 141, desde 2026-10-12T22:07:08.021042Z
    - sessão 29: Android 14 · Chromium 131 (mobile), desde 2026-10-12T22:07:08.243158Z (este)
```

O jogador está logado em 2 dispositivos.

**3. Dispositivo 2.** `login-jogador 2 <email B>` e `dispositivos-jogador 2`:

```
    logado em 3 dispositivo(s):
    - sessão 28: Windows · Google Chrome 141, desde 2026-10-12T22:07:08.021042Z
    - sessão 29: Android 14 · Chromium 131 (mobile), desde 2026-10-12T22:07:08.243158Z
    - sessão 30: macOS 14.5.0 · Google Chrome 129, desde 2026-10-12T22:07:08.445168Z (este)
```

O jogador está logado em 3 dispositivos.

**4. Dispositivo 3.** `login-jogador 3 <email B>` e `dispositivos-jogador 3`:

```
    logado em 3 dispositivo(s):
    - sessão 29: Android 14 · Chromium 131 (mobile), desde 2026-10-12T22:07:08.243158Z
    - sessão 30: macOS 14.5.0 · Google Chrome 129, desde 2026-10-12T22:07:08.445168Z
    - sessão 31: Linux 6.8.0 · Microsoft Edge 128, desde 2026-10-12T22:07:08.637369Z (este)
```

O jogador continua em 3 dispositivos, e o primeiro (o login pelo Swagger UI) saiu da lista.

```sql
SELECT ls.id, ls.device_id, ls.created_at, ls.ended_at
FROM login_session ls
JOIN app_user u ON u.id = ls.user_id
WHERE u.email = 'success+seed-c6-jogador-1@simulator.amazonses.com'
ORDER BY ls.id;
```

```
 id | device_id                          | created_at                 | ended_at
----+------------------------------------+----------------------------+----------------------------
 28 | Windows · Google Chrome 141        | 2026-10-12 22:07:08.021042 | 2026-10-12 22:07:08.633388
 29 | Android 14 · Chromium 131 (mobile) | 2026-10-12 22:07:08.243158 |
 30 | macOS 14.5.0 · Google Chrome 129   | 2026-10-12 22:07:08.445168 |
 31 | Linux 6.8.0 · Microsoft Edge 128   | 2026-10-12 22:07:08.637369 |
```

**5. Swagger UI: listar os dispositivos e verificar o erro.** `GET /sessions`.

Resultado esperado: `401`, `{"message":"Not logged in"}`.

**Resultado em 2026-10-12: falha.** A resposta é `200` com as três sessões dos scripts. O
registro da sessão 28 é encerrado (`ended_at` preenchido), mas a sessão HTTP do navegador
continua autenticada: `LoginLinkSessionService.enforceDeviceLimit` só grava o `ended_at` e não
apaga a sessão HTTP, ao contrário do `DELETE /sessions/{sessionId}` do roteiro 2, que apaga.

```
DEBUG ... ControllerLoggingAspect : --> SessionsController.listActiveSessions()
DEBUG ... RepositoryLoggingAspect : --> UserRepository.findByEmail(success+seed-c6-jogador-1@simulator.amazonses.com)
DEBUG ... RepositoryLoggingAspect : --> LoginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(9)
DEBUG ... ControllerLoggingAspect : <-- SessionsController.listActiveSessions returned {"createdAt":"2026-10-12T22:07:08.243158Z","current":false, …
```

## Competições

### Roteiro 4 — Criar uma competição pública e entrar como jogador novo

**Objetivo:** o administrador cria uma competição pública, que aparece na lista sem login, e um
jogador que nunca usou o sistema entra nela pelo e-mail.

**Pré-condições:** massa recém-restaurada (a competição criada recebe o id 12, o jogador novo o
id 23).

**1. Logar como administrador.** Passos 1 e 2 do roteiro 1.

**2. Criar a competição pública.** `POST /competitions`, com `startDate` igual a amanhã:

```json
{
  "name": "Copa do caderno de testes",
  "type": "PUBLIC",
  "startDate": "2026-10-13",
  "durationDays": 30,
  "recurring": false,
  "buyFee": 0.5,
  "sellFee": 0.5
}
```

Resposta: `201`:

```json
{"buyFee":0.5,"creatorId":1,"durationDays":30,"id":12,"name":"Copa do caderno de testes","recurring":false,"sellFee":0.5,"startDate":"2026-10-13","status":"OPEN","type":"PUBLIC"}
```

```
DEBUG ... ControllerLoggingAspect : --> CompetitionsController.createCompetition({"name":"Copa do caderno de testes","type":"PUBLIC","startDate":"2026-10-13", …})
DEBUG ... RepositoryLoggingAspect : --> UserRepository.findByEmail(success+admin@simulator.amazonses.com)
DEBUG ... RepositoryLoggingAspect : --> CompetitionRepository.save({"buyFee":0.5, … "name":"Copa do caderno de testes", …})
DEBUG ... RepositoryLoggingAspect : <-- CompetitionRepository.save returned {… "id":12,"name":"Copa do caderno de testes", …}
DEBUG ... RepositoryLoggingAspect : --> LogRepository.save({… "logType":"COMPETITION_CREATED","message":"Competition \"Copa do caderno de testes\" created","relatedObjectId":12, …})
DEBUG ... ControllerLoggingAspect : <-- CompetitionsController.createCompetition returned {… "id":12, … "status":"OPEN","type":"PUBLIC"}
```

**3. Sem sessão, listar as competições públicas.** Fazer logout (`DELETE /sessions/current`) ou
usar outro navegador, e chamar `GET /competitions/public`. A nova aparece na lista:

```json
{"durationDays":30,"id":12,"name":"Copa do caderno de testes","startDate":"2026-10-13","status":"OPEN","type":"PUBLIC"}
```

**4. Pedir entrada com um e-mail novo.** `POST /competitions/12/entry-requests`. No ambiente
`blackbox` o captcha vazio é aceito:

```json
{
  "email": "jogador.caderno@example.com",
  "captchaToken": ""
}
```

Resposta: `202`, sem corpo. O sistema cria a participação, manda o e-mail de cadastro e marca a
participação como `EMAIL_SENT`.

```
DEBUG ... ControllerLoggingAspect : --> EntryRequestsController.requestOrConfirmEntry(12, {"captchaToken":"","email":"jogador.caderno@example.com"})
DEBUG ... RepositoryLoggingAspect : --> CompetitionRepository.findById(12)
DEBUG ... RepositoryLoggingAspect : --> UserRepository.findByEmail(jogador.caderno@example.com)
DEBUG ... RepositoryLoggingAspect : <-- UserRepository.findByEmail returned null
DEBUG ... RepositoryLoggingAspect : --> ParticipationRepository.save({…})
DEBUG ... RepositoryLoggingAspect : --> LinkRecordRepository.save({"email":"jogador.caderno@example.com", …})
DEBUG ... EmailServiceLoggingAspect : --> email-service send registration-link to jogador.caderno@example.com
DEBUG ... RepositoryLoggingAspect : --> LogRepository.save({… "logType":"PARTICIPATION_STATUS_CHANGED","message":"Participation status changed to EM…
DEBUG ... ControllerLoggingAspect : <-- EntryRequestsController.requestOrConfirmEntry returned null
```

**5. Completar o cadastro.** Pegar o link de `jogador.caderno@example.com` (ver "Como clicar no
link do e-mail"). `GET /login-links/{token}` responde `202`: o link é válido, mas falta o nome.
Então `POST /login-links/{token}/registration`:

```json
{
  "name": "Jogador do Caderno"
}
```

Resposta: `200`, `{"redirectTo":"/competitions/12"}`. O cadastro já abre a sessão.

```
DEBUG ... ControllerLoggingAspect : --> LoginController.completeRegistration(ebfe1a66-…, {"name":"Jogador do Caderno"}, …)
DEBUG ... RepositoryLoggingAspect : --> LinkRecordRepository.findByToken(ebfe1a66-…)
DEBUG ... RepositoryLoggingAspect : --> UserRepository.save({"email":"jogador.caderno@example.com","id":null,"name":"Jogador do Caderno","registered":true})
DEBUG ... RepositoryLoggingAspect : <-- UserRepository.save returned {"email":"jogador.caderno@example.com","id":23,"name":"Jogador do Caderno","registered":true}
DEBUG ... RepositoryLoggingAspect : --> RoleRepository.findByName(PLAYER)
DEBUG ... RepositoryLoggingAspect : --> UserRoleRepository.save({… "userId":23,"roleId":2 …})
DEBUG ... RepositoryLoggingAspect : --> LogRepository.save({… "message":"Participation status changed to IN…
DEBUG ... RepositoryLoggingAspect : --> LoginSessionRepository.save({… "endedAt":null, …})
DEBUG ... ControllerLoggingAspect : <-- LoginController.completeRegistration returned {"redirectTo":"/competitions/12"}
```

**6. Listar "minhas competições".** `GET /competitions/mine`. A nova aparece em `participating`:

```json
{"created":[],"participating":[{"durationDays":30,"id":12,"name":"Copa do caderno de testes","startDate":"2026-10-13","status":"OPEN","type":"PUBLIC"}],"pastParticipations":[],"pendingConfirmation":[]}
```

**7. Conferir no banco.**

```sql
SELECT id, name, type, status, start_date FROM competition WHERE id = 12;
SELECT id, name, email, registered FROM app_user WHERE email = 'jogador.caderno@example.com';
SELECT id, competition_id, email, status, request_type, first_email_sent_date, joined_at
FROM participation
WHERE email = 'jogador.caderno@example.com';
```

```
 id | name                      | type   | status | start_date
----+---------------------------+--------+--------+------------
 12 | Copa do caderno de testes | PUBLIC | OPEN   | 2026-10-13

 id | name               | email                       | registered
----+--------------------+-----------------------------+------------
 23 | Jogador do Caderno | jogador.caderno@example.com | t

 id | competition_id | email                       | status         | request_type | first_email_sent_date | joined_at
----+----------------+-----------------------------+----------------+--------------+-----------------------+------------
 38 |             12 | jogador.caderno@example.com | IN_COMPETITION | REQUEST      | 2026-10-12            | 2026-10-12
```

### Roteiro 5 — Criar uma competição privada e convidar jogadores

**Objetivo:** o administrador cria uma competição privada, adia o envio dos convites e depois
manda todos de uma vez; um jogador já registrado e um novo entram, e quem não foi convidado não
enxerga a competição.

**Pré-condições:** roteiro 4 executado logo antes, sobre a massa recém-restaurada (a competição
criada recebe o id 13); administrador logado.

**1. Criar a competição privada** com um jogador já registrado da massa e dois e-mails novos.
`POST /competitions`:

```json
{
  "name": "Copa privada do caderno de testes",
  "type": "PRIVATE",
  "startDate": "2026-10-13",
  "durationDays": 30,
  "recurring": false,
  "buyFee": 0.5,
  "sellFee": 0.5,
  "emails": [
    "success+seed-c3-jogador-1@simulator.amazonses.com",
    "convidado1.caderno@example.com",
    "convidado2.caderno@example.com"
  ]
}
```

Resposta: `201`, com `"id":13` e `"status":"AWAITING_INVITES"`.

**2. Adiar o envio dos convites.** `POST /competitions/13/invite-emails`:

```json
{
  "timing": "later"
}
```

Resposta: `204`. Nenhum e-mail sai:

```
DEBUG ... ControllerLoggingAspect : --> CompetitionsController.decideInviteEmailTiming(13, {"timing":"later"})
DEBUG ... RepositoryLoggingAspect : --> CompetitionRepository.findById(13)
DEBUG ... ControllerLoggingAspect : <-- CompetitionsController.decideInviteEmailTiming returned null
```

**3. Listar os jogadores.** `GET /competitions/13/players`. Os três estão em `EMAIL_NOT_SENT`;
o jogador já registrado vem com `userId`:

```json
{"competitionId":13,"email":"success+seed-c3-jogador-1@simulator.amazonses.com","id":39,"requestType":"INVITE","status":"EMAIL_NOT_SENT","userId":7}
{"competitionId":13,"email":"convidado1.caderno@example.com","id":40,"requestType":"INVITE","status":"EMAIL_NOT_SENT"}
{"competitionId":13,"email":"convidado2.caderno@example.com","id":41,"requestType":"INVITE","status":"EMAIL_NOT_SENT"}
```

**4. Mandar os convites para o grupo.** `POST /competitions/13/players/invite-emails` com os
ids do passo 3:

```json
{
  "participationIds": [39, 40, 41]
}
```

Resposta: `202`. O jogador já registrado recebe um convite com link de login; os novos, um
convite de cadastro:

```
DEBUG ... ControllerLoggingAspect : --> PlayersController.resendPlayerInviteEmails(13, {"participationIds":[39,40,41]})
DEBUG ... EmailServiceLoggingAspect : --> email-service send login-link-invite to success+seed-c3-jogador-1@simulator.amazonses.com
DEBUG ... EmailServiceLoggingAspect : --> email-service send invite to convidado1.caderno@example.com
DEBUG ... EmailServiceLoggingAspect : --> email-service send invite to convidado2.caderno@example.com
DEBUG ... ControllerLoggingAspect : <-- PlayersController.resendPlayerInviteEmails returned null
```

```sql
SELECT p.id, p.email, p.status, p.request_type, p.first_email_sent_date
FROM participation p
WHERE p.competition_id = 13
ORDER BY p.id;
```

```
 id | email                                             | status     | request_type | first_email_sent_date
----+---------------------------------------------------+------------+--------------+-----------------------
 39 | success+seed-c3-jogador-1@simulator.amazonses.com | EMAIL_SENT | INVITE       | 2026-10-12
 40 | convidado1.caderno@example.com                    | EMAIL_SENT | INVITE       | 2026-10-12
 41 | convidado2.caderno@example.com                    | EMAIL_SENT | INVITE       | 2026-10-12
```

A competição continua `AWAITING_INVITES` depois desse envio; só a decisão `"timing": "now"` a
passa para `OPEN`. Os jogadores conseguem entrar mesmo assim (passo 6).

**5. Quem não foi convidado não enxerga a competição.** Sem sessão, `GET /competitions/public`
não traz o id 13. E um pedido de entrada, `POST /competitions/13/entry-requests`:

```json
{
  "email": "intruso.caderno@example.com",
  "captchaToken": ""
}
```

Resposta: `404`, a mesma de uma competição que não existe.

**6. O jogador já registrado aceita e um convidado novo se cadastra.**

- Navegador do jogador já registrado: pegar o link de
  `success+seed-c3-jogador-1@simulator.amazonses.com` e chamar `GET /login-links/{token}`
  (`200`, `{"redirectTo":"/competitions/13"}`). `GET /competitions/13` mostra
  `"canConfirmEntry": true`. Confirmar com `POST /competitions/13/entry-requests` **sem corpo**
  (com sessão, o corpo não é necessário). Resposta: `200`, a participação com
  `"status":"IN_COMPETITION"`.

```
DEBUG ... ControllerLoggingAspect : --> EntryRequestsController.requestOrConfirmEntry(13, null)
DEBUG ... RepositoryLoggingAspect : --> ParticipationRepository.findByCompetition_IdAndUser_Id(13, 7)
DEBUG ... RepositoryLoggingAspect : --> ParticipationRepository.save({…})
DEBUG ... RepositoryLoggingAspect : --> LogRepository.save({… "logType":"PARTICIPATION_STATUS_CHANGED","message":"Participation status changed to IN…
DEBUG ... ControllerLoggingAspect : <-- EntryRequestsController.requestOrConfirmEntry returned {"competitionId":13, … "status":"IN_COMPETITION","userId":7}
```

- Outro navegador: pegar o link de `convidado1.caderno@example.com`; `GET /login-links/{token}`
  responde `202`; completar com `POST /login-links/{token}/registration`:

```json
{
  "name": "Convidado Um"
}
```

**7. Listar os jogadores por status.** Como administrador,
`GET /competitions/13/players?status=IN_COMPETITION`:

```json
{"competitionId":13,"email":"success+seed-c3-jogador-1@simulator.amazonses.com","firstEmailSentDate":"2026-10-12","id":39,"joinedAt":"2026-10-12","requestType":"INVITE","status":"IN_COMPETITION","userId":7}
{"competitionId":13,"email":"convidado1.caderno@example.com","firstEmailSentDate":"2026-10-12","id":40,"joinedAt":"2026-10-12","requestType":"INVITE","status":"IN_COMPETITION","userId":24}
```

E `GET /competitions/13/players?status=EMAIL_SENT` traz só o `convidado2.caderno@example.com`.

```sql
SELECT p.id, p.email, p.status, p.user_id, p.joined_at
FROM participation p
WHERE p.competition_id = 13
ORDER BY p.id;
```

```
 id | email                                             | status         | user_id | joined_at
----+---------------------------------------------------+----------------+---------+------------
 39 | success+seed-c3-jogador-1@simulator.amazonses.com | IN_COMPETITION |       7 | 2026-10-12
 40 | convidado1.caderno@example.com                    | IN_COMPETITION |      24 | 2026-10-12
 41 | convidado2.caderno@example.com                    | EMAIL_SENT     |         |
```

### Roteiro 6 — Gerenciar os jogadores de uma competição privada

**Objetivo:** o administrador corrige o e-mail de um convite pendente, reenvia o convite, vê a
validação recusar um e-mail inválido e cancela o convite.

**Pré-condições:** roteiro 5 executado (convite pendente 41 na competição 13); administrador
logado. Sem o roteiro 5, qualquer convite pendente da massa serve, por exemplo os da `[seed] C4`.

**1. Corrigir o e-mail do convite pendente.** `PATCH /competitions/13/players/41`:

```json
{
  "email": "convidado2.corrigido@example.com"
}
```

Resposta: `200`, a participação com o e-mail novo e o mesmo status `EMAIL_SENT`.

```
DEBUG ... ControllerLoggingAspect : --> PlayersController.updatePlayerEmail(13, 41, {"email":"convidado2.corrigido@example.com"})
DEBUG ... ControllerLoggingAspect : <-- PlayersController.updatePlayerEmail returned {"competitionId":13,"email":"convidado2.corrigido@example.com", …
```

```sql
SELECT id, email, status FROM participation WHERE id = 41;
```

```
 id | email                            | status
----+----------------------------------+------------
 41 | convidado2.corrigido@example.com | EMAIL_SENT
```

**2. Reenviar o convite só para ele.** `POST /competitions/13/players/41/invite-emails`, sem
corpo. Resposta: `202`.

```
DEBUG ... ControllerLoggingAspect : --> PlayersController.resendPlayerInviteEmail(13, 41)
DEBUG ... EmailServiceLoggingAspect : --> email-service send invite to convidado2.corrigido@example.com
DEBUG ... ControllerLoggingAspect : <-- PlayersController.resendPlayerInviteEmail returned null
```

**3. Tentar um e-mail inválido.** `PATCH /competitions/13/players/41`:

```json
{
  "email": "nao-e-um-email"
}
```

Resposta: `400`, `{"message":"Invalid e-mail"}`, no formato `Error` do contrato. Nenhuma linha
de log: a validação (`format: email` do contrato, `@Email` no código gerado) recusa antes de o
controller rodar, e o `ApiExceptionHandler` monta a resposta. O banco não muda (o `SELECT` do
passo 1 continua igual).

**4. Cancelar o convite pendente.** `DELETE /competitions/13/players/41`. Resposta: `204`.

```
DEBUG ... ControllerLoggingAspect : --> PlayersController.removePlayer(13, 41)
DEBUG ... RepositoryLoggingAspect : --> ParticipationRepository.delete({…})
DEBUG ... ControllerLoggingAspect : <-- PlayersController.removePlayer returned null
```

```sql
SELECT id, email, status FROM participation WHERE competition_id = 13 ORDER BY id;
```

```
 id | email                                             | status
----+---------------------------------------------------+----------------
 39 | success+seed-c3-jogador-1@simulator.amazonses.com | IN_COMPETITION
 40 | convidado1.caderno@example.com                    | IN_COMPETITION
```

### Roteiro 7 — Ver as competições de um jogador com histórico

**Objetivo:** um jogador que participa de várias competições vê cada uma no grupo certo e
confirma um convite pendente.

**Pré-condições:** massa restaurada; o jogador com histórico
(`success+seed-multi-m4@simulator.amazonses.com`) sem sessão.

**1. Logar como o jogador com histórico.** `POST /login-requests`:

```json
{
  "email": "success+seed-multi-m4@simulator.amazonses.com"
}
```

e `GET /login-links/{token}`.

**2. Listar "minhas competições".** `GET /competitions/mine`:

```json
{
  "participating": [
    {"id": 9,  "name": "[seed] C9 Copa publica em andamento"},
    {"id": 10, "name": "[seed] C10 Copa publica terminada por data"},
    {"id": 11, "name": "[seed] C11 Copa privada em andamento"}
  ],
  "pastParticipations": [],
  "pendingConfirmation": [
    {"id": 5, "name": "[seed] C5 Copa privada com convites enviados"}
  ],
  "created": []
}
```

A `[seed] C10` terminou por data (começou em 2026-09-13 e durou 10 dias), mas continua em
`participating`: o sistema decide o grupo pelo status da competição, e nenhuma operação grava o
status `CLOSED` hoje (já registrado na spec 05-018, "Fora de escopo"). Por isso
`pastParticipations` fica vazio.

**3. Abrir o detalhe da competição pendente.** `GET /competitions/5`:

```json
{"accessLevel":"READ","buyFee":0.5,"canConfirmEntry":true,"durationDays":30,"id":5,"name":"[seed] C5 Copa privada com convites enviados","recurring":false,"sellFee":0.5,"startDate":"2026-09-13","status":"OPEN","type":"PRIVATE"}
```

**4. Confirmar a entrada.** `POST /competitions/5/entry-requests` sem corpo. Resposta: `200`:

```json
{"competitionId":5,"email":"success+seed-multi-m4@simulator.amazonses.com","firstEmailSentDate":"2026-09-12","id":37,"joinedAt":"2026-10-12","requestType":"INVITE","status":"IN_COMPETITION","userId":22}
```

O convite foi mandado em 2026-09-12, quando a massa foi gerada com o relógio no passado; a
entrada é de hoje.

**5. Listar de novo.** `GET /competitions/mine`: a `[seed] C5` passou para `participating` e
`pendingConfirmation` ficou vazio.

**6. Abrir o detalhe de uma competição sem relação com o jogador.** `GET /competitions/4`
(`[seed] C4`, privada, sem convite para ele). Resposta: `404`, a mesma de uma competição que não
existe.

```
DEBUG ... ControllerLoggingAspect : --> CompetitionsController.getCompetitionDetail(4)
DEBUG ... RepositoryLoggingAspect : --> CompetitionRepository.findById(4)
DEBUG ... RepositoryLoggingAspect : --> ParticipationRepository.findByCompetition_IdAndUser_Id(4, 22)
DEBUG ... RepositoryLoggingAspect : <-- ParticipationRepository.findByCompetition_IdAndUser_Id returned null
DEBUG ... ControllerLoggingAspect : <-- CompetitionsController.getCompetitionDetail returned null
```

```sql
SELECT c.id, c.name, p.status, p.request_type, p.joined_at
FROM participation p
JOIN competition c ON c.id = p.competition_id
JOIN app_user u ON u.id = p.user_id
WHERE u.email = 'success+seed-multi-m4@simulator.amazonses.com'
ORDER BY c.id;
```

```
 id | name                                         | status         | request_type | joined_at
----+----------------------------------------------+----------------+--------------+------------
  5 | [seed] C5 Copa privada com convites enviados | IN_COMPETITION | INVITE       | 2026-10-12
  9 | [seed] C9 Copa publica em andamento          | IN_COMPETITION | REQUEST      | 2026-09-12
 10 | [seed] C10 Copa publica terminada por data   | IN_COMPETITION | REQUEST      | 2026-09-12
 11 | [seed] C11 Copa privada em andamento         | IN_COMPETITION | INVITE       | 2026-09-12
```

## Referências

- Spec [05-040](../../specs/05-040-caderno-de-testes-por-funcionalidade/spec.md): este caderno.
- Spec [05-018](../../specs/05-018-gerador-dados-teste/spec.md): gerador da massa de dados.
- Spec [05-009](../../specs/05-009-identificacao-dispositivo-client-hints/spec.md): rótulo do
  dispositivo a partir dos Client Hints.
- Spec [05-011](../../specs/05-011-logging-aspectos/spec.md): os aspectos de log citados nos
  trechos de log.
- Spec [05-039](../../specs/05-039-busca-de-jogadores/spec.md): busca de jogadores do roteiro 1.
