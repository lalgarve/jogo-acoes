# Tasks: Perfil LocalStack Pro e script de subida do ambiente

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: a criar.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Overlay `docker-compose.localstack-pro.yml` | — | [P] | — |
| ~~T002~~ | `docker-up.conf.example`, `docker-up.conf` no `.gitignore` | — | [P] | — |
| ~~T003~~ | `scripts/docker-up.sh` | T001, T002 | | — |
| ~~T004~~ | `README.md`: subida com o script e LocalStack Pro | T003 | | — |
| T005 | Verificação manual sem token (Leila): roteiro A abaixo | T003 | [P] | — |
| T006 | Verificação manual com token (Leila): roteiro B abaixo | T003 | [P] | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.

## O que foi verificado nesta sessão

No ambiente de nuvem da Claude (Docker 29.8), em 2026-10-17:

- Erros de configuração, todos com saída 1 e sem chamar o Docker: `LOCALSTACK_PRO=true` sem
  token, `BLACKBOX=yes`, chave desconhecida (`FOO=1`), chave obrigatória ausente, `--config`
  apontando para arquivo inexistente, argumento desconhecido.
- `docker compose -f docker-compose.yml -f docker-compose.localstack-pro.yml config` sem token:
  recusado com a mensagem do overlay. Com token: imagem `localstack/localstack-pro:4` e a
  variável no serviço.
- `./scripts/docker-up.sh --reset` com o modelo (Community): `db`, `db-email-service`,
  `localstack` e `dns` de pé, *event source mapping* da `email-lambda` `Enabled`, remetente
  `no-reply@jogo-acoes.example` verificado no SES, primeiro `restore` aplicado.
- Pro com token inválido: o LocalStack sai com 55; o script mostra as linhas do log com
  `License activation failed!` e sai com erro. O token não aparece em nenhuma linha da saída.
- Voltar para o Community depois do Pro: o `localstack` é recriado com `localstack/localstack:4`.

**Não rodou aqui:** o build das imagens de `email-service`, `app` e `config-server` e do
`function.zip` dentro dos containers falha no proxy TLS deste ambiente (o Maven e o `curl`
dentro do container não confiam no certificado do proxy). Por isso as etapas a partir do
`email-service` e o Pro com token válido ficam para T005 e T006.

## Verificação manual

Na raiz do repositório, no WSL, com o branch da implementação.

### Roteiro A (T005): sem token, sem arquivo local

```bash
mv docker-up.conf docker-up.conf.bak 2>/dev/null; true
./scripts/docker-up.sh --reset
```

Esperado:

1. A primeira linha: `No docker-up.conf, using the template docker-up.conf.example (cp
   docker-up.conf.example docker-up.conf to change it)`, depois `Compose files:
   docker-compose.yml:docker-compose.blackbox.yml` e `LocalStack: Community`.
2. No fim: `Environment up.` e `export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml`.
3. A imagem do LocalStack:
   ```bash
   docker inspect jogo-acoes-localstack-1 -f '{{.Config.Image}}'
   ```
   Esperado: `localstack/localstack:4`.
4. O `app` de pé:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/swagger-ui/index.html
   ```
   Esperado: `200`.

### Roteiro B (T006): LocalStack Pro com token

```bash
[ -f docker-up.conf ] && cp docker-up.conf docker-up.conf.bak
cp docker-up.conf.example docker-up.conf
sed -i 's/^LOCALSTACK_PRO=false/LOCALSTACK_PRO=true/' docker-up.conf
```

Abrir `docker-up.conf` num editor e colar o token em `LOCALSTACK_AUTH_TOKEN=` (sem aspas). Depois:

```bash
git status --short docker-up.conf
./scripts/docker-up.sh --reset
```

Esperado:

1. `git status` não mostra nada (o arquivo está no `.gitignore`).
2. `Compose files: docker-compose.yml:docker-compose.blackbox.yml:docker-compose.localstack-pro.yml`
   e `LocalStack: Pro`; no fim, `Environment up.`.
3. A imagem e a licença:
   ```bash
   docker inspect jogo-acoes-localstack-1 -f '{{.Config.Image}}'
   docker compose logs localstack | grep -i -m1 'license'
   ```
   Esperado: `localstack/localstack-pro:4` e uma linha de licença ativada (sem
   `License activation failed`).
4. O ciclo de envio pela `email-lambda`: pedir um link de login como administrador do overlay
   `blackbox` e ler o e-mail no SES do LocalStack:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/login-requests \
     -H 'Content-Type: application/json' \
     -d '{"email": "success+admin@simulator.amazonses.com"}'
   sleep 10
   curl -s http://localhost:4566/_aws/ses | jq '.messages | length'
   ```
   Esperado: `202` e um número maior que `0`.
5. Token inválido: trocar o token do arquivo por `x` e rodar `./scripts/docker-up.sh`.
   Esperado: `docker-up: localstack exited; its last log lines:`, a mensagem
   `License activation failed!` e a última linha `docker-up: docker compose up ... failed`.
   Colocar o token certo de volta.

Para derrubar e voltar ao estado anterior:

```bash
docker compose down -v
mv docker-up.conf.bak docker-up.conf 2>/dev/null; true
```
