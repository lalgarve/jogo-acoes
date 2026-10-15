# Apêndice A — Caderno de testes manuais: Jogo de Ações

Versão resumida. O caderno completo, com os trechos de log e as consultas ao banco que confirmam
cada passo, está em
https://github.com/lalgarve/jogo-acoes/blob/master/docs/disciplina/caderno-de-testes.md.

Os roteiros são organizados por funcionalidade: login, competições e listas de domínios de
e-mail temporário. Todos partem de uma massa de dados versionada no repositório. Os ids citados
valem para a massa recém-restaurada.

## Preparação

O caderno roda no ambiente `blackbox` (`docker-compose.blackbox.yml` sobre o
`docker-compose.yml`), o único que aceita o captcha vazio. A partir da raiz do repositório, no
WSL:

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose up -d --wait db db-email-service localstack
./scripts/test-api-key.sh restore
docker compose up -d --wait email-service
./scripts/test-api-key.sh restore
docs/disciplina/caderno-de-testes/scripts/restaurar-massa.sh
docker compose up -d app adminer ses-viewer log-viewer
```

O `restaurar-massa.sh` carrega o dump da massa e confere se ela tem o que os roteiros precisam.
A massa tem 11 competições (`[seed] C1` a `[seed] C11`) e 21 jogadores; os roteiros usam:

| Papel | Jogador | Competições |
|---|---|---|
| Jogador A | `success+seed-multi-m3@simulator.amazonses.com` | C9, C10 (públicas), C11 (privada) |
| Jogador B | `success+seed-c6-jogador-1@simulator.amazonses.com` | C6 (privada) |
| Jogador C | `success+seed-c2-jogador-1@simulator.amazonses.com` | C2 (pública), convite pendente em C8 |
| Jogador com histórico | `success+seed-multi-m4@simulator.amazonses.com` | C9, C10, C11 e convite pendente em C5 |
| Jogador já registrado | `success+seed-c3-jogador-1@simulator.amazonses.com` | C3 |

| Programa | Endereço |
|---|---|
| Swagger UI | `http://localhost:8080/api/swagger-ui/index.html` |
| Adminer (banco) | `http://localhost:8081` |
| Visualizador de e-mails | `http://localhost:3005` |
| Visualizador de logs | `http://localhost:8083` |

**Como "clicar no link do e-mail".** O link de login chega por e-mail e nunca na resposta da
API. No visualizador de e-mails, abrir a mensagem e clicar no link
(`http://localhost:8080/api/login-links/<token>`); a sessão fica no cookie do navegador, e as
chamadas seguintes no Swagger UI já saem autenticadas.

## Login

### Roteiro 1 — Logar como administrador

**Objetivo:** o administrador entra pelo link do e-mail, lista todos os jogadores e, depois do
logout, perde o acesso.

1. `POST /login-requests` com `{"email": "success+admin@simulator.amazonses.com"}`. Esperado:
   `202`, sem corpo (a resposta é a mesma se o e-mail não existir).
2. Clicar no link do e-mail (`GET /login-links/{token}`). Esperado: `200`,
   `{"redirectTo":"/admin"}`.
3. `POST /players/search` com `{"page": 0, "size": 100}`. Esperado: `200`, `total` 22 (o
   administrador e os 21 jogadores), cada jogador com as contagens de competições públicas,
   privadas e criadas.
4. Conferir na lista os jogadores A (2 públicas, 1 privada), B (0 e 1) e C (1 e 0).
5. `DELETE /sessions/current`. Esperado: `204`.
6. Repetir o passo 3. Esperado: `401`, `{"message":"Not logged in"}`.

### Roteiro 2 — Deslogar de outro dispositivo

**Objetivo:** o jogador encerra, de um navegador, a sessão aberta em outro.

**Pré-condições:** dois navegadores (ou uma janela anônima como segundo navegador).

1. Navegador 1: pedir o login do jogador A e clicar no link. Esperado: `200`,
   `{"redirectTo":"/competitions/mine"}`.
2. Navegador 1: `GET /competitions/mine`. Esperado: C9, C10 e C11 em `participating`; os demais
   grupos vazios.
3. Navegador 2: pedir o login do jogador A de novo e clicar no link.
4. Navegador 2: `GET /sessions`. Esperado: duas sessões, a do navegador 2 com
   `"current": true`.
5. Navegador 2: `DELETE /sessions/{sessionId}` com o `id` da sessão que tem
   `"current": false`. Esperado: `204`.
6. Navegador 1: `GET /sessions`. Esperado: `401`.
7. Navegador 2: `DELETE /sessions/current`. Esperado: `204`.

### Roteiro 3 — Logar em mais dispositivos que o máximo permitido

**Objetivo:** o jogador fica logado em até 3 dispositivos; o quarto login encerra a sessão mais
antiga.

O Swagger UI não consegue mandar cabeçalhos de dispositivo (`Sec-CH-UA*`) diferentes dos do
próprio navegador, então os logins dos dispositivos 1 a 3 são feitos pelos scripts
`login-jogador.sh <número> <email>` e `dispositivos-jogador.sh <número>`, em
`caderno-de-testes/scripts/` (há versões para PowerShell).

1. Swagger UI: pedir o login do jogador B, clicar no link e chamar `GET /sessions`. Esperado: uma
   sessão.
2. `./login-jogador.sh 1 <email B>` e `./dispositivos-jogador.sh 1`. Esperado: 2 dispositivos.
3. O mesmo com o dispositivo 2. Esperado: 3 dispositivos.
4. O mesmo com o dispositivo 3. Esperado: continuam 3 dispositivos, e a sessão do Swagger UI saiu
   da lista.
5. Swagger UI: `GET /sessions`. Esperado: `401`, `{"message":"Not logged in"}`.

**Resultado: falha no passo 5**, registrada em 2026-10-12 e confirmada numa nova execução. A resposta é `200`: o registro
da sessão mais antiga é encerrado, mas a sessão HTTP do navegador continua autenticada.

## Competições

### Roteiro 4 — Criar uma competição pública e entrar como jogador novo

**Objetivo:** o administrador cria uma competição pública, que aparece na lista sem login, e um
jogador que nunca usou o sistema entra nela pelo e-mail.

1. Logar como administrador (passos 1 e 2 do roteiro 1).
2. `POST /competitions`, com `startDate` igual a amanhã:

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

   Esperado: `201`, competição com `"id":12` e `"status":"OPEN"`.
3. Sem sessão, `GET /competitions/public`. Esperado: a competição 12 na lista.
4. `POST /competitions/12/entry-requests` com
   `{"email": "jogador.caderno@example.com", "captchaToken": ""}`. Esperado: `202`; o sistema
   envia o e-mail de cadastro.
5. Clicar no link do e-mail de `jogador.caderno@example.com`. Esperado: `202` (falta o nome).
   Completar com `POST /login-links/{token}/registration` e `{"name": "Jogador do Caderno"}`.
   Esperado: `200`, `{"redirectTo":"/competitions/12"}`, já com sessão.
6. `GET /competitions/mine`. Esperado: a competição 12 em `participating`.

### Roteiro 5 — Criar uma competição privada e convidar jogadores

**Objetivo:** o administrador cria uma competição privada, adia o envio dos convites e depois
manda todos de uma vez; um jogador já registrado e um novo entram, e quem não foi convidado não
enxerga a competição.

**Pré-condições:** roteiro 4 executado logo antes; administrador logado.

1. `POST /competitions`:

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

   Esperado: `201`, `"id":13`, `"status":"AWAITING_INVITES"`.
2. `POST /competitions/13/invite-emails` com `{"timing": "later"}`. Esperado: `204`, nenhum
   e-mail enviado.
3. `GET /competitions/13/players`. Esperado: participações 39, 40 e 41 em `EMAIL_NOT_SENT`; a do
   jogador já registrado com `userId`.
4. `POST /competitions/13/players/invite-emails` com `{"participationIds": [39, 40, 41]}`.
   Esperado: `202`; o jogador já registrado recebe um convite com link de login, os novos um
   convite de cadastro, e as três participações passam a `EMAIL_SENT`.
5. Sem sessão, `GET /competitions/public` não traz a 13, e
   `POST /competitions/13/entry-requests` com `{"email": "intruso.caderno@example.com",
   "captchaToken": ""}` responde `404`, como uma competição que não existe.
6. Jogador já registrado: clicar no link (`200`, `{"redirectTo":"/competitions/13"}`);
   `GET /competitions/13` mostra `"canConfirmEntry": true`; confirmar com
   `POST /competitions/13/entry-requests` sem corpo. Esperado: `200`,
   `"status":"IN_COMPETITION"`.
7. Outro navegador: clicar no link de `convidado1.caderno@example.com` (`202`) e completar com
   `POST /login-links/{token}/registration` e `{"name": "Convidado Um"}`.
8. Como administrador, `GET /competitions/13/players?status=IN_COMPETITION`. Esperado: as
   participações 39 e 40. Com `status=EMAIL_SENT`, só a de `convidado2.caderno@example.com`.

### Roteiro 6 — Gerenciar os jogadores de uma competição privada

**Objetivo:** o administrador corrige o e-mail de um convite pendente, reenvia o convite, vê a
validação recusar um e-mail inválido e cancela o convite.

**Pré-condições:** roteiro 5 executado (convite pendente 41 na competição 13); administrador
logado.

1. `PATCH /competitions/13/players/41` com `{"email": "convidado2.corrigido@example.com"}`.
   Esperado: `200`, o e-mail novo e o mesmo status `EMAIL_SENT`.
2. `POST /competitions/13/players/41/invite-emails`, sem corpo. Esperado: `202`.
3. `PATCH /competitions/13/players/41` com `{"email": "nao-e-um-email"}`. Esperado: `400`,
   `{"message":"Invalid e-mail"}`; nada muda no banco.
4. `DELETE /competitions/13/players/41`. Esperado: `204`; a competição fica só com as
   participações 39 e 40.

### Roteiro 7 — Ver as competições de um jogador com histórico

**Objetivo:** um jogador que participa de várias competições vê cada uma no grupo certo e
confirma um convite pendente.

1. Logar como o jogador com histórico.
2. `GET /competitions/mine`. Esperado: C9, C10 e C11 em `participating` e C5 em
   `pendingConfirmation`. A C10 já terminou por data, mas continua em `participating`, porque
   nenhuma operação grava hoje o status `CLOSED`.
3. `GET /competitions/5`. Esperado: `200`, `"canConfirmEntry": true`.
4. `POST /competitions/5/entry-requests` sem corpo. Esperado: `200`,
   `"status":"IN_COMPETITION"`.
5. `GET /competitions/mine`. Esperado: C5 em `participating` e `pendingConfirmation` vazio.
6. `GET /competitions/4` (competição privada sem convite para ele). Esperado: `404`.

## Serviço de E-mail

### Roteiro 8 — Importar uma lista de domínios de e-mail temporário

**Objetivo:** operações cadastra um repositório de lista, pede a execução do lote (Spring Batch)
e o envio para um domínio da lista passa a ser recusado.

O Serviço de E-mail não tem Swagger UI: os passos usam `curl` contra `http://localhost:8082/api`,
com as chaves de teste do `jogo-acoes` (`$API_KEY`, só envia e-mail) e de operações
(`$OPS_API_KEY`, só administra as listas). A lista é servida pelo S3 do LocalStack.

1. Publicar uma lista de teste no S3 com `disposable.test`, `OUTRO-TEMPORARIO.test` (com espaços),
   um comentário, uma linha em branco e uma `linha invalida`.
2. `POST /disposable-domain-sources` com a chave de operações e
   `{"url": "http://localstack:4566/disposable-lists/lista.conf", "description": "Lista de teste"}`.
   Esperado: `201`, sem horário de download.
3. Com a chave do `jogo-acoes`, `GET /disposable-domain-sources`; com a de operações,
   `POST /emails`. Esperado: `403` nas duas (`Missing permission: disposable-email:admin` e
   `Missing permission: email:send`).
4. `POST /disposable-domain-imports` com o `sourceId` do passo 2. Esperado: `202` na hora; a
   consulta `GET /disposable-domain-imports/{executionId}` mostra `COMPLETED`, 5 linhas lidas, 1
   ignorada (`linha invalida`) e 2 domínios incluídos.
5. Repetir o passo 4 com a lista igual. Esperado: `COMPLETED` com `"exitMessage":"UNCHANGED"` e
   nada lido; só a hora do download muda.
6. Com a chave do `jogo-acoes`, `POST /emails` para `jogador@disposable.test`. Esperado: `422`,
   `{"message":"Recipient domain is a disposable e-mail domain: disposable.test"}`.
7. Publicar a lista só com `disposable.test` e repetir o passo 4. Esperado: 1 linha lida e 1
   domínio removido.
