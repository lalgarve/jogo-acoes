# Apêndice B — Caderno de testes manuais: Serviço de E-mail

Versão resumida. O caderno completo, com os trechos de log e as consultas ao banco que confirmam
cada passo, está em
https://github.com/lalgarve/jogo-acoes/blob/master/docs/disciplina/caderno-de-testes-email-service.md.

Os roteiros cobrem as funcionalidades do serviço: chaves e permissões, templates, envio e listas
de e-mail temporário.

## Preparação

Nenhum roteiro usa a aplicação Jogo de Ações. Sobe só o Serviço de E-mail e o que ele precisa, a
partir de um banco vazio. A partir da raiz do repositório, no WSL:

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

O `gerar-chaves.sh` cria, com a CLI da biblioteca `api-key` 1.1.0, uma chave para cada
permissão do serviço:

| Chave | Cliente | Permissão | O que pode fazer |
|---|---|---|---|
| `envio` | `caderno-envio` | `email:send` | Cadastrar e mudar os próprios templates, enviar e-mail |
| `operacoes` | `caderno-operacoes` | `disposable-email:admin` | Cadastrar repositórios de listas de e-mail temporário e pedir a importação |

O serviço não tem Swagger UI: as chamadas são feitas pelo script
`$C/chamar.sh <chave> <MÉTODO> <caminho>`, com o corpo JSON na entrada padrão. Além das duas
chaves acima, ele aceita `jogo-acoes` (a chave de teste da aplicação, com a permissão padrão
`email:send`), `invalida` e `sem-chave`.

## Chaves e permissões

### Roteiro 1 — Cada chave só faz o que a permissão dela libera

1. `envio GET /templates`. Esperado: `200`, `[]`.
2. `operacoes GET /disposable-domain-sources`. Esperado: `200`, só o repositório inicial que a
   migration cadastra, ainda sem download.
3. `envio GET /disposable-domain-sources`. Esperado: `403`,
   `{"message":"Missing permission: disposable-email:admin"}`.
4. `operacoes POST /templates`. Esperado: `403`, `{"message":"Missing permission: email:send"}`.
5. `operacoes POST /emails`. Esperado: `403`, a mesma mensagem, mesmo com um template que não
   existe: a permissão é conferida antes.
6. `jogo-acoes GET /templates` e `jogo-acoes GET /disposable-domain-sources`. Esperado: `200` e
   `403`; a chave anterior às permissões vale com a padrão, `email:send`.
7. `invalida GET /templates` e `sem-chave GET /disposable-domain-sources`. Esperado: `401` nas
   duas, `{"message":"Missing or invalid X-API-Key"}`; o motivo fica só no log.

## Templates

### Roteiro 2 — Cadastrar, pré-visualizar e alterar um template

1. `envio POST /templates`:

   ```json
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
   ```

   Esperado: `201`. No SES, o template fica com o nome `caderno-envio__boas-vindas`.
2. Repetir o passo 1. Esperado: `409`,
   `{"message":"A template named 'boas-vindas' already exists for this client"}`.
3. `envio GET /templates/boas-vindas`. Esperado: `200`, o template do passo 1.
4. `envio POST /templates/boas-vindas/preview` com
   `{"variables": {"nome": "Maria", "link": "http://localhost:8080/api/login-links/exemplo"}}`.
   Esperado: `200`, assunto `Bem-vindo, Maria` e o corpo com as variáveis trocadas; nenhum e-mail
   enviado.
5. `envio PUT /templates/boas-vindas` com o assunto `{{nome}}, sua conta está pronta` e um corpo
   novo. Esperado: `200`, `updatedAt` mais recente que `createdAt`. Repetindo o passo 4, o
   assunto volta como `Maria, sua conta está pronta`, com o acento certo. A primeira execução do
   caderno achou aqui uma falha (o acento voltava trocado), corrigida pela spec 05-051.
6. `PUT`, `GET` e `POST .../preview` de `/templates/nao-existe`. Esperado: `404` nas três,
   `{"message":"No template named 'nao-existe' for this client"}`.
7. `jogo-acoes GET /templates/boas-vindas`. Esperado: `404`; os templates de um cliente não
   aparecem para outro.
8. `envio POST /templates` sem `subject`. Esperado: `400`,
   `{"message":"subject must not be null"}`.
9. `envio POST /templates/boas-vindas/preview` com `{"variables": {"nome": "Maria"}}`, sem o
   `link` que o corpo usa. Esperado: `422`, com a mensagem do SES
   `Attribute 'link' is not present in the rendering data.`

## Envio

### Roteiro 3 — Enviar e-mail e verificar o domínio do destinatário

**Pré-condições:** roteiro 2. A zona de DNS de teste (`docker/dns/test.zone`) tem um domínio
para cada caso.

1. `envio POST /emails`:

   ```json
   {
     "templateName": "boas-vindas",
     "recipientEmail": "success@simulator.amazonses.com",
     "templateData": {
       "nome": "Maria",
       "link": "http://localhost:8080/api/login-links/exemplo"
     }
   }
   ```

   Esperado: `202`, `{"id":"<uuid>","status":"QUEUED"}`. O e-mail aparece no visualizador de
   e-mails com o assunto `Maria, sua conta está pronta`. O domínio do simulador do SES já nasce
   na lista branca.
2. O mesmo pedido, trocando o destinatário:

   | `recipientEmail` | Esperado | Aprovado por |
   |---|---|---|
   | `jogador@with-mx.test` | `202` | registro MX |
   | `jogador@only-a.test` | `202` | registro A |
   | `jogador@only-aaaa.test` | `202` | registro AAAA |
   | `jogador@WITH-MX.test` | `202` | lista branca, sem consulta ao DNS |

   Os três primeiros domínios entram na lista branca.
3. Domínios recusados, todos com `422`,
   `{"message":"Recipient domain does not accept e-mail: <domínio>"}`:
   `jogador@missing.test` (não existe), `jogador@null-mx.test` (MX nulo) e
   `jogador@no-mail.test` (sem MX nem endereço). Nada é gravado nem enviado.
4. `jogador@unavailable.test`, cujo DNS nunca responde. Esperado: `202` depois de cerca de 2 s;
   o envio é aceito, o domínio não entra na lista branca e o log registra um aviso.
5. Template que não existe: `404`. Destinatário `nao-e-um-email`: `400`,
   `{"message":"recipientEmail must be a well-formed email address"}`.
6. Os 7 envios aceitos estão no banco e chegam ao SES do LocalStack, pela fila e pela função
   Lambda.

## Listas de e-mail temporário

Os roteiros 4 a 6 usam listas de teste em `caderno-de-testes-email-service/listas/`, publicadas
no S3 do LocalStack pelo `publicar-lista.sh`. O `esperar-importacao.sh <executionId>` consulta a
execução do lote até ela terminar.

### Roteiro 4 — Cadastrar um repositório, importar a lista e bloquear o envio

1. Publicar a lista A (`lista-a-v1.conf`): um comentário, `disposable.test`,
   `  OUTRO-TEMPORARIO.test ` (com espaços e maiúsculas), uma linha em branco, `linha invalida`,
   `Temporário.test` (internacionalizado) e `compartilhado.test`.
2. `operacoes POST /disposable-domain-sources` com
   `{"url": "http://localstack:4566/disposable-lists/lista-a.conf", "description": "Lista A do caderno de testes"}`.
   Esperado: `201`, id 2, sem download.
3. Cadastros inválidos: a mesma URL (`409`), uma URL `ftp://` (`400`) e um repositório sem
   descrição (`400`).
4. `operacoes POST /disposable-domain-imports` com `{"sourceId": 2}`. Esperado: `202` na hora; a
   execução termina `COMPLETED` com 7 linhas lidas, 1 ignorada (`linha invalida`) e 4 domínios
   incluídos: `disposable.test`, `outro-temporario.test` (normalizado),
   `xn--temporrio-51a.test` (forma ASCII de `temporário.test`) e `compartilhado.test`. A
   execução passa pelos três *steps* do lote: `downloadStep`, `importStep` e `pruneStep`.
5. `operacoes GET /disposable-domain-sources`. Esperado: a lista A com `domainCount` 4 e as horas
   do download e da atualização preenchidas.
6. Envio com a chave `envio`:

   | `recipientEmail` | Esperado |
   |---|---|
   | `jogador@disposable.test` | `422`, domínio temporário (a recusa vem da lista, mesmo com MX) |
   | `jogador@sub.disposable.test` | `422`, subdomínio de um domínio da lista |
   | `jogador@outro-temporario.test` | `422`, a lista é consultada antes do DNS |
   | `jogador@with-mx.test` | `202` |

### Roteiro 5 — Atualizar a lista e o mesmo domínio em dois repositórios

1. Importar de novo a lista A sem mudança. Esperado: `COMPLETED` com
   `"exitMessage":"UNCHANGED"` e tudo zerado; só a hora do download muda.
2. Publicar, cadastrar e importar a lista B, que também tem `compartilhado.test`. Esperado:
   `201` (id 3) e `COMPLETED` com 1 domínio incluído.
3. Publicar a segunda versão da lista A (`disposable.test` e `novo-temporario.test`) e importar.
   Esperado: `COMPLETED`, 1 incluído e 3 removidos; a lista A fica com 2 domínios e a B não muda.
4. Envio:

   | `recipientEmail` | Esperado |
   |---|---|
   | `jogador@novo-temporario.test` | `422`, domínio temporário (entrou na lista A) |
   | `jogador@compartilhado.test` | `422`, domínio temporário (saiu da lista A, continua na B) |
   | `jogador@outro-temporario.test` | `422`, agora recusado pelo DNS (`does not accept e-mail`) |

### Roteiro 6 — Falhas na importação

1. Cadastrar e importar um repositório cujo arquivo não existe e outro numa porta sem nada
   escutando. Esperado: as duas execuções `FAILED`, com o motivo em `exitMessage`
   (`answered HTTP status 404` e `could not be downloaded … ConnectException`); nenhum download
   registrado.
2. Publicar uma lista vazia no lugar da lista B e importar. Esperado: `FAILED`,
   `The list is empty`; nada é removido, e `jogador@compartilhado.test` continua recusado.
3. `operacoes POST /disposable-domain-imports` sem corpo. Esperado: `202`, uma execução por
   repositório. A lista A termina `UNCHANGED`, as que falharam falham de novo pelo mesmo motivo, e
   uma falha não impede as outras. O repositório inicial baixa a lista pública da internet
   (cerca de 9.900 domínios).
4. Publicar uma lista com 100 mil domínios, cadastrá-la e pedir a importação duas vezes seguidas.
   Esperado: o primeiro pedido `202`, o segundo `409`,
   `An import of disposable domain repository <id> is already running`; a execução termina
   `COMPLETED` com 100 mil domínios incluídos em poucos segundos, e só ela aparece no banco.

## Derrubar o ambiente

```bash
docker compose down -v
```
