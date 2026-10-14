# Caderno de testes manuais — Serviço de E-mail

Roteiros de teste manual do `email-service`, organizados pelas funcionalidades do serviço:
chaves e permissões, templates, envio e listas de e-mail temporário. Segue o formato do
[caderno do `jogo-acoes`](caderno-de-testes.md) (spec 05-040): em cada passo, a operação, o
corpo JSON pronto para colar e a resposta esperada.

**Versão 1 — antes da implementação.** Os roteiros cobrem as specs 05-025 (templates), 05-031
(envio), 05-047 (domínio do destinatário) e 05-048 (listas de e-mail temporário e permissões por
chave). As respostas esperadas vêm do contrato (`docs/openapi-email-service.yaml`) e das specs.
Os trechos de log e os `SELECT`s de verificação entram na versão 2, capturados executando o
serviço de verdade depois da implementação da 05-048.

O `email-service` não tem Swagger UI: as chamadas são feitas pelo script `chamar.sh`, que
escolhe a chave, manda o JSON e mostra o status e a resposta.

## Preparação

### 1. Subir só o Serviço de E-mail

Nenhum roteiro usa o `app` nem o banco dele. Sobe só o `email-service` e o que ele precisa:
o banco próprio (`db-email-service`), o LocalStack (SES, SQS, S3 e a `email-lambda`), o DNS de
teste da spec 05-047 (`dns`) e o `config-server`, que o Compose sobe junto por ser dependência.
A partir da raiz do repositório, no WSL:

```bash
export COMPOSE_FILE=docker-compose.yml
docker compose down -v
docker compose up -d --wait --build db-email-service localstack dns
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
docs/disciplina/caderno-de-testes-email-service/scripts/gerar-chaves.sh
docker compose up -d ses-viewer log-viewer
docker compose up -d --no-deps adminer
C=docs/disciplina/caderno-de-testes-email-service/scripts
```

- O `down -v` começa de um banco vazio: lista branca só com `simulator.amazonses.com`, nenhum
  domínio temporário importado, nenhum template. Os resultados dos roteiros dependem disso.
- O `test-api-key.sh restore` roda duas vezes pelo mesmo motivo do caderno do `jogo-acoes`: a
  segunda define o remetente do cliente `jogo-acoes`, numa tabela que só existe depois que o
  `email-service` sobe.
- A última linha guarda a pasta dos scripts em `$C`, usada em todos os passos.

### 2. Criar as duas chaves de API

O `email-service` tem duas permissões (spec 05-048, "Permissões"), e o caderno cria uma chave
para cada caso de acesso, com a CLI do [`api-key`](https://github.com/lalgarve/api-key) 1.1.0:

| Chave (`chamar.sh`) | Cliente | Permissão | O que pode fazer |
|---|---|---|---|
| `envio` | `caderno-envio` | `email:send` | Cadastrar e mudar os próprios templates, enviar e-mail |
| `operacoes` | `caderno-operacoes` | `disposable-email:admin` | Cadastrar repositórios de listas de e-mail temporário e pedir a importação |

O `chamar.sh` também aceita `jogo-acoes` (a chave de teste do `jogo-acoes`, gerada antes da 1.1.0,
que vale com a permissão padrão `email:send`), `invalida` (uma chave que não existe) e
`sem-chave`.

O `gerar-chaves.sh` (já chamado no bloco da seção 1) baixa a CLI na primeira vez, roda o
`generate` das duas chaves num container Java na rede do Compose, define o remetente do
`caderno-envio` e grava as chaves em `caderno-de-testes-email-service/chaves.env`, fora do git.
Saída esperada (as chaves mudam a cada execução):

```
==> 1. Procurando o db-email-service
    container …, rede jogo-acoes_default
==> 2. CLI do api-key 1.1.0
    baixando https://github.com/lalgarve/api-key/releases/download/v1.1.0/api-key-1.1.0.jar
==> 3. Gerando a chave de caderno-envio (generate --client caderno-envio --permissions email:send)
    API key generated for client 'caderno-envio' (does not expire).
    This is the only time the plaintext key is shown — store it now:

    dak_5BX-…

    Permissions: email:send.
==> 4. Gerando a chave de caderno-operacoes (generate --client caderno-operacoes --permissions disposable-email:admin)
    API key generated for client 'caderno-operacoes' (does not expire).
    …
    Permissions: disposable-email:admin.
==> 5. Definindo o remetente de caderno-envio (no-reply@jogo-acoes.example)
    Sender address of client 'caderno-envio' set to no-reply@jogo-acoes.example
==> 6. Gravando as chaves em …/caderno-de-testes-email-service/chaves.env
    CHAVE_ENVIO=dak_5BX-…
    CHAVE_OPERACOES=dak_h8xI…
    chaves ativas (list):
    ID  CLIENT             CREATED_AT                   EXPIRES_AT  REVOKED_AT  STATUS  PERMISSIONS
    1   jogo-acoes         2026-10-03T19:49:29.138196Z  -           -           active  email:send
    2   caderno-envio      2026-10-14T17:37:02.161602Z  -           -           active  email:send
    3   caderno-operacoes  2026-10-14T17:37:08.326181Z  -           -           active  disposable-email:admin
```

O `restore` apaga o schema `api_key` inteiro, e com ele as chaves do caderno: depois de um
`restore`, rodar o `gerar-chaves.sh` de novo. Enquanto o dump de
`docker/postgres-email-service/test-data/` for o da CLI 1.0.1, o `restore` falha num banco em
que a CLI 1.1.0 já rodou (a tabela de permissões nova prende a chave primária que o dump tenta
apagar); por isso a seção 1 começa com `down -v`. A tarefa T001 da 05-048 regenera esse dump.

### 3. Programas auxiliares

| Programa | Endereço | Para quê |
|---|---|---|
| `email-service` | `http://localhost:8082/api` | a API, chamada pelo `chamar.sh` |
| E-mails enviados (JSON) | `http://localhost:4566/_aws/ses` | os e-mails que o SES do LocalStack recebeu |
| Visualizador de e-mails | `http://localhost:3005` | a mesma informação, em tela |
| Visualizador de logs | `http://localhost:8083` | logs do `email-service` e das `email-lambda`, com busca por texto; no terminal, `docker compose logs -f email-service` |
| Adminer | `http://localhost:8081` (sistema PostgreSQL, servidor `db-email-service`, usuário e senha `email_service_admin`, base `email_service`) | rodar os `SELECT`s |
| `psql` | `docker compose exec -e PGOPTIONS='-c search_path=email_service' db-email-service psql -U email_service_admin -d email_service` | o mesmo, no terminal |

**Como chamar a API.** `$C/chamar.sh <chave> <MÉTODO> <caminho>`, com o corpo JSON, quando há,
num bloco `<<'JSON' … JSON` logo abaixo. O script escreve a operação, a chave, o corpo e o
status, e no fim a resposta formatada:

```bash
$C/chamar.sh envio GET /templates
```

```
==> GET http://localhost:8082/api/templates
    chave: caderno-envio (email:send)
<== 200
[]
```

A resposta sai na saída padrão e o resto na saída de erro, então um campo da resposta pode ir
para uma variável com `jq` (os roteiros 4 a 6 fazem isso com os ids).

## Chaves e permissões

### Roteiro 1 — Cada chave só faz o que a permissão dela libera

**Objetivo:** mostrar que a chave de envio não administra as listas, que a de operações não
envia e-mail nem mexe em template, e que chave inválida ou ausente recebe `401`.

**Pré-condições:** seções 1 e 2 da preparação.

**1. Chave de envio lista os próprios templates.**

```bash
$C/chamar.sh envio GET /templates
```

Resposta: `200`, `[]` (o cliente `caderno-envio` ainda não tem template).

**2. Chave de operações lista os repositórios de listas.**

```bash
$C/chamar.sh operacoes GET /disposable-domain-sources
```

Resposta: `200`, só o repositório inicial que a migration cadastra, ainda sem download:

```json
[
  {
    "id": 1,
    "url": "https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/main/disposable_email_blocklist.conf",
    "description": "disposable-email-domains, lista principal",
    "lastDownloadedAt": null,
    "lastUpdatedAt": null,
    "domainCount": 0,
    "lastImport": null
  }
]
```

**3. Chave de envio tenta administrar as listas.**

```bash
$C/chamar.sh envio GET /disposable-domain-sources
```

Resposta: `403`, `{"message":"Missing permission: disposable-email:admin"}`.

**4. Chave de operações tenta cadastrar um template.**

```bash
$C/chamar.sh operacoes POST /templates <<'JSON'
{
  "name": "operacoes-nao-pode",
  "subject": "Teste",
  "body": "<p>Teste</p>"
}
JSON
```

Resposta: `403`, `{"message":"Missing permission: email:send"}`.

**5. Chave de operações tenta enviar um e-mail.**

```bash
$C/chamar.sh operacoes POST /emails <<'JSON'
{
  "templateName": "boas-vindas",
  "recipientEmail": "success@simulator.amazonses.com"
}
JSON
```

Resposta: `403`, `{"message":"Missing permission: email:send"}`. A permissão é conferida antes
do template: o `403` vem mesmo com um template que não existe.

**6. Chave do `jogo-acoes`, anterior às permissões.** Ela não tem permissão gravada e vale com a
padrão, `email:send`:

```bash
$C/chamar.sh jogo-acoes GET /templates
$C/chamar.sh jogo-acoes GET /disposable-domain-sources
```

Respostas: `200` com `[]` (o `app` não subiu, então não registrou os templates dele) e `403`,
`{"message":"Missing permission: disposable-email:admin"}`.

**7. Chave que não existe e chamada sem chave.**

```bash
$C/chamar.sh invalida GET /templates
$C/chamar.sh sem-chave GET /disposable-domain-sources
```

Respostas: `401` nas duas, `{"message":"Missing or invalid X-API-Key"}`. O `401` vem antes da
permissão e não diz o motivo.

## Templates

### Roteiro 2 — Cadastrar, pré-visualizar e alterar um template

**Objetivo:** o cliente `caderno-envio` cadastra um template, que vai para o SES, pré-visualiza
com dados de exemplo sem enviar nada e o altera; os templates de um cliente não aparecem para
outro.

**Pré-condições:** roteiro 1 não é necessário; só a preparação.

**1. Cadastrar o template `boas-vindas`.**

```bash
$C/chamar.sh envio POST /templates <<'JSON'
{
  "name": "boas-vindas",
  "subject": "Bem-vindo, {{nome}}",
  "body": "<p>Olá, {{nome}}!</p><p>Entre pelo link: <a href=\"{{link}}\">{{link}}</a></p>",
  "variablesSchema": {
    "type": "object",
    "required": ["nome", "link"],
    "properties": {
      "nome": { "type": "string" },
      "link": { "type": "string" }
    }
  }
}
JSON
```

Resposta: `201`, o template com `createdAt` e `updatedAt` iguais. No SES, o template fica com o
nome `caderno-envio__boas-vindas`:

```bash
docker compose exec localstack awslocal ses list-templates
```

**2. Cadastrar de novo com o mesmo nome.** Repetir o comando do passo 1. Resposta: `409`,
`{"message":"A template named 'boas-vindas' already exists for this client"}`.

**3. Consultar o template.**

```bash
$C/chamar.sh envio GET /templates/boas-vindas
```

Resposta: `200`, o mesmo JSON do passo 1.

**4. Pré-visualizar com dados de exemplo.**

```bash
$C/chamar.sh envio POST /templates/boas-vindas/preview <<'JSON'
{
  "variables": {
    "nome": "Maria",
    "link": "http://localhost:8080/api/login-links/exemplo"
  }
}
JSON
```

Resposta: `200`:

```json
{
  "subject": "Bem-vindo, Maria",
  "body": "<p>Olá, Maria!</p><p>Entre pelo link: <a href=\"http://localhost:8080/api/login-links/exemplo\">http://localhost:8080/api/login-links/exemplo</a></p>"
}
```

Nenhum e-mail novo em `http://localhost:3005`.

**5. Alterar o assunto e o corpo.**

```bash
$C/chamar.sh envio PUT /templates/boas-vindas <<'JSON'
{
  "subject": "{{nome}}, sua conta está pronta",
  "body": "<p>Olá, {{nome}}! Sua conta está pronta.</p><p><a href=\"{{link}}\">Entrar</a></p>"
}
JSON
```

Resposta: `200`, o template com o assunto novo, `variablesSchema` vazio (o `PUT` substitui o
template inteiro) e `updatedAt` mais recente que `createdAt`. Repetir o passo 4: o assunto
volta `"Maria, sua conta está pronta"`.

**6. Alterar, consultar e pré-visualizar um template que não existe.**

```bash
$C/chamar.sh envio PUT /templates/nao-existe <<'JSON'
{
  "subject": "Teste",
  "body": "<p>Teste</p>"
}
JSON
$C/chamar.sh envio GET /templates/nao-existe
$C/chamar.sh envio POST /templates/nao-existe/preview <<'JSON'
{
  "variables": {}
}
JSON
```

Respostas: `404` nas três, `{"message":"No template named 'nao-existe' for this client"}`.

**7. Outro cliente não vê o template.** A chave do `jogo-acoes`, que também tem `email:send`:

```bash
$C/chamar.sh jogo-acoes GET /templates/boas-vindas
```

Resposta: `404`, a mesma mensagem do passo 6.

**8. Corpo inválido.**

```bash
$C/chamar.sh envio POST /templates <<'JSON'
{
  "name": "sem-assunto",
  "body": "<p>Teste</p>"
}
JSON
```

Resposta: `400`, `{"message":"subject must not be null"}`.

O SES do LocalStack não valida a sintaxe Handlebars nem reclama de variável faltando na
pré-visualização: os `422` de template rejeitado pelo SES só aparecem com o SES de verdade (os
cenários marcados `@requires-real-ses` no `register_templates.feature`) e ficam fora deste
caderno.

## Envio

### Roteiro 3 — Enviar e-mail e verificar o domínio do destinatário

**Objetivo:** enviar e-mails com o template do roteiro 2 e ver a verificação de domínio da spec
05-047: lista branca, MX, A, AAAA, domínio inexistente, MX nulo e DNS sem resposta.

**Pré-condições:** roteiro 2 (template `boas-vindas` do cliente `caderno-envio`).

A zona de teste do DNS (`docker/dns/test.zone`) tem um nome para cada caso. Para conferir o que
ela responde:

```bash
docker run --rm --network host alpine:3.20 sh -c \
  'apk add -q bind-tools && for d in with-mx.test only-a.test only-aaaa.test null-mx.test no-mail.test missing.test; do echo "$d MX: $(dig +short -p 1053 @127.0.0.1 $d MX) A: $(dig +short -p 1053 @127.0.0.1 $d A) AAAA: $(dig +short -p 1053 @127.0.0.1 $d AAAA)"; done'
```

```
with-mx.test MX: 10 mail.with-mx.test. A: AAAA:
only-a.test MX: A: 192.0.2.10 AAAA:
only-aaaa.test MX: A: AAAA: 2001:db8::10
null-mx.test MX: 0 . A: 192.0.2.20 AAAA:
no-mail.test MX: A: AAAA:
missing.test MX: A: AAAA:
```

**1. Enviar para o simulador do SES.** `simulator.amazonses.com` já nasce na lista branca e não
é consultado no DNS.

```bash
$C/chamar.sh envio POST /emails <<'JSON'
{
  "templateName": "boas-vindas",
  "recipientEmail": "success@simulator.amazonses.com",
  "templateData": {
    "nome": "Maria",
    "link": "http://localhost:8080/api/login-links/exemplo"
  }
}
JSON
```

Resposta: `202`, `{"id":"<uuid>","status":"QUEUED"}` (`PENDING` se a spec 05-046 já estiver
implementada). Em alguns segundos o e-mail aparece em `http://localhost:3005`, de
`no-reply@jogo-acoes.example`, com o assunto `Maria, sua conta está pronta`.

**2. Domínios aprovados pelo DNS.** O mesmo pedido, trocando só `recipientEmail`, para cada um
destes destinatários:

```json
{
  "templateName": "boas-vindas",
  "recipientEmail": "jogador@with-mx.test",
  "templateData": { "nome": "Jogador", "link": "http://localhost:8080/api/login-links/exemplo" }
}
```

| `recipientEmail` | Resposta | Aprovado por |
|---|---|---|
| `jogador@with-mx.test` | `202` | `MX` |
| `jogador@only-a.test` | `202` | `A` (MX implícito, RFC 5321) |
| `jogador@only-aaaa.test` | `202` | `A` (o AAAA conta como A) |
| `jogador@WITH-MX.test` | `202` | já na lista branca, sem consulta ao DNS |

**3. Domínios recusados.** O mesmo pedido, para cada um destes:

| `recipientEmail` | Resposta |
|---|---|
| `jogador@missing.test` | `422`, `{"message":"Recipient domain does not accept e-mail: missing.test"}` |
| `jogador@null-mx.test` | `422`, `{"message":"Recipient domain does not accept e-mail: null-mx.test"}` (MX nulo, mesmo com A) |
| `jogador@no-mail.test` | `422`, `{"message":"Recipient domain does not accept e-mail: no-mail.test"}` (existe, sem MX nem endereço) |

Nada é gravado, nada vai para a fila, e nenhum desses domínios entra na lista branca.

**4. DNS sem resposta.** `unavailable.test` é servido por um bloco do DNS de teste que nunca
responde:

```bash
$C/chamar.sh envio POST /emails <<'JSON'
{
  "templateName": "boas-vindas",
  "recipientEmail": "jogador@unavailable.test",
  "templateData": { "nome": "Jogador", "link": "http://localhost:8080/api/login-links/exemplo" }
}
JSON
```

Resposta: `202`, depois do tempo máximo da consulta (2 s). O envio é aceito, o domínio não entra
na lista branca e o log tem um `WARN`. Repetir: demora de novo, porque o domínio não ficou
guardado.

**5. Pedidos recusados antes do DNS.**

```bash
$C/chamar.sh envio POST /emails <<'JSON'
{
  "templateName": "nao-existe",
  "recipientEmail": "jogador@missing.test"
}
JSON
$C/chamar.sh envio POST /emails <<'JSON'
{
  "templateName": "boas-vindas",
  "recipientEmail": "nao-e-um-email"
}
JSON
```

Respostas: `404`, `{"message":"No template named 'nao-existe' for this client"}` (o domínio
inexistente não chega a ser consultado) e `400`,
`{"message":"recipientEmail must be a well-formed email address"}`.

O `409` de remetente não configurado não aparece com as chaves do caderno: as duas com
`email:send` (`caderno-envio` e `jogo-acoes`) têm remetente.

## Listas de e-mail temporário

Os roteiros 4 a 6 usam listas de teste servidas pelo S3 do LocalStack, sem internet. Os
arquivos ficam em `caderno-de-testes-email-service/listas/`, e o `publicar-lista.sh` copia um
deles para o bucket `disposable-lists`, com o nome que o repositório usa:

| Arquivo | Conteúdo | Usado em |
|---|---|---|
| `lista-a-v1.conf` | comentário, `disposable.test`, `  OUTRO-TEMPORARIO.test ` (espaços e maiúsculas), linha em branco, `linha invalida`, `Temporário.test` (internacionalizado), `compartilhado.test` — 7 linhas | roteiro 4 |
| `lista-a-v2.conf` | `disposable.test` e `novo-temporario.test` | roteiro 5 |
| `lista-b.conf` | `compartilhado.test` | roteiro 5 |
| `lista-vazia.conf` | só comentário e linha em branco | roteiro 6 |

### Roteiro 4 — Cadastrar um repositório, importar a lista e bloquear o envio

**Objetivo:** operações cadastra um repositório, pede a importação, confere os domínios
importados e as horas do repositório; o envio para um desses domínios passa a ser recusado.

**Pré-condições:** preparação; roteiro 2 (template `boas-vindas`).

**1. Publicar a primeira versão da lista A.**

```bash
$C/publicar-lista.sh $C/../listas/lista-a-v1.conf lista-a.conf
```

```
==> 1. Bucket s3://disposable-lists
    make_bucket: disposable-lists
==> 2. Copiando …/listas/lista-a-v1.conf para s3://disposable-lists/lista-a.conf
==> 3. Conteúdo servido em http://localstack:4566/disposable-lists/lista-a.conf
    7 linhas
    | # Lista A, primeira versão (caderno de testes do email-service, roteiro 4)
    | disposable.test
    |   OUTRO-TEMPORARIO.test
    |
    | linha invalida
    | Temporário.test
    | compartilhado.test
```

**2. Cadastrar o repositório da lista A.**

```bash
SOURCE_A=$($C/chamar.sh operacoes POST /disposable-domain-sources <<'JSON' | tee /dev/stderr | jq -r .id
{
  "url": "http://localstack:4566/disposable-lists/lista-a.conf",
  "description": "Lista A do caderno de testes"
}
JSON
)
echo "SOURCE_A=$SOURCE_A"
```

Resposta: `201`, o repositório com `"lastDownloadedAt": null`, `"lastUpdatedAt": null` e
`"domainCount": 0`; `SOURCE_A=2` (o `1` é o repositório inicial da migration).

**3. Cadastrar repositórios inválidos.** O mesmo endereço de novo, um endereço que não é
`http`/`https` e um sem descrição:

```bash
$C/chamar.sh operacoes POST /disposable-domain-sources <<'JSON'
{
  "url": "http://localstack:4566/disposable-lists/lista-a.conf",
  "description": "Lista A repetida"
}
JSON
$C/chamar.sh operacoes POST /disposable-domain-sources <<'JSON'
{
  "url": "ftp://localstack/disposable-lists/lista-a.conf",
  "description": "Lista por FTP"
}
JSON
$C/chamar.sh operacoes POST /disposable-domain-sources <<'JSON'
{
  "url": "http://localstack:4566/disposable-lists/sem-descricao.conf"
}
JSON
```

Respostas: `409` (endereço já cadastrado), `400` e `400`.

**4. Pedir a importação só da lista A.**

```bash
EXECUCAO=$($C/chamar.sh operacoes POST /disposable-domain-imports <<JSON | tee /dev/stderr | jq -r '.imports[0].executionId'
{
  "sourceId": $SOURCE_A
}
JSON
)
$C/esperar-importacao.sh $EXECUCAO
```

O bloco `<<JSON` (sem aspas) deixa o shell trocar `$SOURCE_A` pelo id do passo 2. Resposta do
pedido: `202`, `{"imports":[{"sourceId":2,"executionId":<n>,"status":"STARTING"}]}` (ou
`STARTED`). O `esperar-importacao.sh` consulta a execução a cada 2 s até ela terminar:

```json
{
  "executionId": 1,
  "status": "COMPLETED",
  "startTime": "…",
  "endTime": "…",
  "read": 7,
  "added": 4,
  "removed": 0,
  "skipped": 3,
  "exitMessage": null
}
```

Lidas as 7 linhas; incluídos `disposable.test`, `outro-temporario.test` (sem espaços, em
minúsculas), `xn--temporrio-51a.test` (forma ASCII de `temporário.test`) e
`compartilhado.test`; ignorados o comentário, a linha em branco e a `linha invalida` (esta com um
`WARN` no log).

**5. As horas do repositório.**

```bash
$C/chamar.sh operacoes GET /disposable-domain-sources
```

Resposta: `200`; no repositório da lista A, `lastDownloadedAt` e `lastUpdatedAt` com a hora do
passo 4, `domainCount` 4 e `lastImport` com a execução do passo 4. O repositório inicial
continua sem download.

**6. Enviar para domínios temporários.** Com a chave de envio, o mesmo pedido do roteiro 3 para
cada destinatário:

```json
{
  "templateName": "boas-vindas",
  "recipientEmail": "jogador@disposable.test",
  "templateData": { "nome": "Jogador", "link": "http://localhost:8080/api/login-links/exemplo" }
}
```

| `recipientEmail` | Resposta |
|---|---|
| `jogador@disposable.test` | `422`, `{"message":"Recipient domain is a disposable e-mail domain: disposable.test"}` — tem MX na zona de teste, a recusa vem da lista |
| `jogador@sub.disposable.test` | `422`, a mesma recusa (subdomínio de um domínio da lista) |
| `jogador@outro-temporario.test` | `422`, `…disposable e-mail domain: outro-temporario.test` — não existe no DNS, mas a lista é consultada antes |
| `jogador@with-mx.test` | `202` — não está na lista |

### Roteiro 5 — Atualizar a lista e o mesmo domínio em dois repositórios

**Objetivo:** importar de novo a mesma lista não muda nada além da hora do download; a lista
mudada inclui e remove domínios só daquele repositório; um domínio que sai de um repositório
continua bloqueado se outro ainda o tem.

**Pré-condições:** roteiro 4, com `$SOURCE_A` definido no mesmo terminal (senão,
`SOURCE_A=2`).

**1. Importar de novo com a lista igual.** Repetir os comandos do passo 4 do roteiro 4.
Esperado: `"status":"COMPLETED"`, `"added":0`, `"removed":0`. Repetir o passo 5 do roteiro 4:
`lastDownloadedAt` mudou, `lastUpdatedAt` continua o do roteiro 4.

**2. Cadastrar e importar a lista B**, que também tem `compartilhado.test`.

```bash
$C/publicar-lista.sh $C/../listas/lista-b.conf
SOURCE_B=$($C/chamar.sh operacoes POST /disposable-domain-sources <<'JSON' | tee /dev/stderr | jq -r .id
{
  "url": "http://localstack:4566/disposable-lists/lista-b.conf",
  "description": "Lista B do caderno de testes"
}
JSON
)
EXECUCAO=$($C/chamar.sh operacoes POST /disposable-domain-imports <<JSON | jq -r '.imports[0].executionId'
{
  "sourceId": $SOURCE_B
}
JSON
)
$C/esperar-importacao.sh $EXECUCAO
```

Esperado: `201` no cadastro (`SOURCE_B=3`) e a execução `COMPLETED` com `"added":1`.

**3. Publicar a segunda versão da lista A e importar.** Saem `outro-temporario.test`,
`xn--temporrio-51a.test` e `compartilhado.test`; entra `novo-temporario.test`.

```bash
$C/publicar-lista.sh $C/../listas/lista-a-v2.conf lista-a.conf
EXECUCAO=$($C/chamar.sh operacoes POST /disposable-domain-imports <<JSON | jq -r '.imports[0].executionId'
{
  "sourceId": $SOURCE_A
}
JSON
)
$C/esperar-importacao.sh $EXECUCAO
```

Esperado: `"status":"COMPLETED"`, `"added":1`, `"removed":3`. No `GET
/disposable-domain-sources`, a lista A com `domainCount` 2 e `lastUpdatedAt` novo; a lista B
sem mudança.

**4. Conferir no envio.**

| `recipientEmail` | Resposta |
|---|---|
| `jogador@novo-temporario.test` | `422`, domínio temporário (entrou na lista A) |
| `jogador@compartilhado.test` | `422`, domínio temporário (saiu da lista A, continua na lista B) |
| `jogador@outro-temporario.test` | `422`, `Recipient domain does not accept e-mail: outro-temporario.test` — saiu da lista e agora a recusa vem do DNS |

### Roteiro 6 — Falhas na importação

**Objetivo:** um repositório fora do ar, com erro ou com lista vazia termina `FAILED` com o
motivo e não muda nada; os outros repositórios são importados; duas execuções do mesmo
repositório não rodam juntas.

**Pré-condições:** roteiros 4 e 5 (`$SOURCE_A` e `$SOURCE_B`).

**1. Repositório com erro e repositório fora do ar.** Um endereço de um objeto que não existe
(`404`) e uma porta sem nada escutando:

```bash
SOURCE_404=$($C/chamar.sh operacoes POST /disposable-domain-sources <<'JSON' | jq -r .id
{
  "url": "http://localstack:4566/disposable-lists/nao-existe.conf",
  "description": "Lista que não existe"
}
JSON
)
SOURCE_FORA=$($C/chamar.sh operacoes POST /disposable-domain-sources <<'JSON' | jq -r .id
{
  "url": "http://localstack:9999/lista.conf",
  "description": "Repositório fora do ar"
}
JSON
)
for S in $SOURCE_404 $SOURCE_FORA; do
  EXECUCAO=$($C/chamar.sh operacoes POST /disposable-domain-imports <<JSON | jq -r '.imports[0].executionId'
{
  "sourceId": $S
}
JSON
  )
  $C/esperar-importacao.sh $EXECUCAO
done
```

Esperado: as duas execuções `FAILED`, com o motivo em `exitMessage` (status `404` na primeira,
erro de conexão na segunda); no `GET /disposable-domain-sources`, os dois repositórios
continuam sem `lastDownloadedAt` nem `lastUpdatedAt`.

**2. Lista vazia.** A lista B passa a ter só comentários:

```bash
$C/publicar-lista.sh $C/../listas/lista-vazia.conf lista-b.conf
EXECUCAO=$($C/chamar.sh operacoes POST /disposable-domain-imports <<JSON | jq -r '.imports[0].executionId'
{
  "sourceId": $SOURCE_B
}
JSON
)
$C/esperar-importacao.sh $EXECUCAO
```

Esperado: `FAILED`, com o motivo de lista vazia. Nada removido: `jogador@compartilhado.test`
continua `422`, e a lista B continua com `domainCount` 1.

**3. Importar todos os repositórios.** Pedido sem corpo:

```bash
$C/chamar.sh operacoes POST /disposable-domain-imports < /dev/null
```

Resposta: `202`, uma execução por repositório em `imports`. Consultar cada uma com o
`esperar-importacao.sh`: a lista A termina `COMPLETED` (sem mudança), as que falharam nos
passos 1 e 2 falham de novo, e uma falha não impede as outras. O repositório inicial (`1`) baixa
a lista pública da internet: `COMPLETED` com dezenas de milhares de domínios se a máquina tem
acesso à internet, `FAILED` com erro de conexão se não tem.

**4. Duas execuções do mesmo repositório ao mesmo tempo.** Uma lista grande o bastante para a
importação não terminar antes do segundo pedido (100 mil domínios):

```bash
for i in $(seq 1 100000); do echo "temporario-$i.test"; done > /tmp/lista-grande.conf
$C/publicar-lista.sh /tmp/lista-grande.conf lista-grande.conf
SOURCE_GRANDE=$($C/chamar.sh operacoes POST /disposable-domain-sources <<'JSON' | jq -r .id
{
  "url": "http://localstack:4566/disposable-lists/lista-grande.conf",
  "description": "Lista com 100 mil domínios"
}
JSON
)
for vez in 1 2; do
  $C/chamar.sh operacoes POST /disposable-domain-imports <<JSON
{
  "sourceId": $SOURCE_GRANDE
}
JSON
done
```

Esperado: o primeiro pedido `202`, o segundo `409` (a execução do primeiro ainda está rodando).
Depois, o `esperar-importacao.sh` com o `executionId` do primeiro mostra `COMPLETED`,
`"added":100000`, em poucos segundos.

## Derrubar o ambiente

```bash
docker compose down -v
```
