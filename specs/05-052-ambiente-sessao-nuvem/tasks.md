# Tasks: Ambiente completo numa sessão de nuvem da Claude

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: a criar.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Ter o `project_name_override` do cliente gerado neste branch: esperar o merge da PR #178 (spec 05-050) em `development` e fazer merge de `development` aqui. Decisão da Leila (2026-10-19): a correção fica só naquela PR, sem copiar o commit | — | [P] | — |
| ~~T002~~ | `scripts/cloud-down.sh`: encerrar os processos Java pelos PIDs gravados, remover o LocalStack e os containers do Lambda, `docker compose down -v`; idempotente | — | [P] | — |
| ~~T003~~ | `scripts/cloud-up.sh`: checagem da sessão de nuvem, `cloud-down.sh`, `dockerd`, build (`--no-build`), Compose, LocalStack com espera do init, `restore`/`email-service`/`restore`/`app` com espera e log em caso de falha | T002 | | — |
| ~~T004~~ | `scripts/cloud-blackbox-tests.sh`: venv e cliente gerado na pasta de estado, `DELETE /_aws/ses`, `behave` e `pytest` | T001 | [P] | — |
| ~~T005~~ | Comentário em `docker-compose.yml` e `docker-compose.blackbox.yml` apontando para `scripts/cloud-up.sh` | T003 | [P] | — |
| ~~T006~~ | `README.md` (seção sobre sessões de nuvem) e `CLAUDE.md` (aviso curto) | T003, T004 | | — |
| ~~T007~~ | Critério de aceite de exceções (`memory/constitution.md`): não se aplica, pois nenhuma classe Java muda e nenhuma exceção é criada. Registrar aqui que os caminhos de erro dos scripts são cobertos pelos cenários 5 a 8 da verificação manual. Nenhuma exceção criada | T003 | | — |
| ~~T008~~ | Verificação manual, roteiro abaixo, numa sessão de nuvem | T003, T004 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.

## O que foi verificado (2026-10-19)

Na sessão de nuvem da Claude, LocalStack Pro 4.14.0 com licença *student*, todos os passos do
roteiro abaixo:

| Passo | Resultado |
|---|---|
| 1. Subida do zero | `exit 0` em 1min34 (com build); `200`, `pro True`, `Active`, `5`. `/_localstack/init/ready` devolve `{"completed": true, "scripts": [{"stage": "READY", "name": "01-create-queue.sh", "state": "SUCCESSFUL"}, ...]}`, o formato que o script lê |
| 2. E-mail ponta a ponta | `202` e `1 jogo-acoes__login-link` |
| 2b. DNS de teste | `POST /api/emails` no `email-service` para `jogador@missing.test`: `422`, `Recipient domain does not accept e-mail: missing.test`. Confirma que `EMAIL_DOMAIN_CHECK_DNS_SERVERS=127.0.0.1:1053` vence o `dns:53` do `config-repo` (com o DNS inalcançável o envio seria aceito com um `WARN`, ver caderno de testes do Serviço de E-mail) |
| 3. Suíte de caixa-preta | `behave` 2/2 cenários (11 passos), `pytest` 29/29, `exit 0` |
| 4. Subida repetida | `--no-build`: `exit 0` em 56 s, sem `Building`, os três processos Java anteriores encerrados pelo PID, SES com `0` mensagens |
| 5. Derrubar | `exit 0` duas vezes, nenhum container do projeto, `0` processos `java -jar` |
| 6. Sem token | mensagem pedindo `LOCALSTACK_AUTH_TOKEN`, `exit 1`, nenhum container |
| 7. Licença inválida | log do LocalStack com `License activation failed!`, `exit 1`, o token real não aparece na saída |
| 8. Fora da nuvem | mensagem indicando o `scripts/docker-up.sh`, `exit 1`, nenhum container |
| 9. Falha de um serviço | parou no `email-service` com `Port 8082 was already in use` nas linhas do log, `exit 1` |

Ajuste feito depois do passo 7: quando a subida falha no meio, o script agora avisa que o que já
subiu continua no ar e que o `./scripts/cloud-down.sh` derruba.

Visto no caminho, sem efeito no resultado: o proxy da sessão recusa conexões do LocalStack para
`analytics.localstack.cloud` e para um endereço do CloudFront. A licença ativa e o Lambda roda
mesmo assim.

## Verificação manual

Numa sessão do Claude Code na nuvem, com `LOCALSTACK_AUTH_TOKEN` configurado no ambiente, na
raiz do repositório e no branch da implementação. Quem roda é a própria Claude; o resultado vai
para este arquivo.

### 1. Subida do zero (cenário 1)

```bash
./scripts/cloud-up.sh
```

Esperado: termina com saída 0 e mostra a pasta de estado (`/tmp/jogo-acoes-cloud`). Depois:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/swagger-ui/index.html
curl -s http://localhost:4566/_localstack/info | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d["edition"], d["is_license_activated"])'
docker exec jogo-acoes-localstack awslocal lambda get-function --function-name EmailLambda --query Configuration.State --output text
docker exec jogo-acoes-localstack awslocal ses list-templates --query 'length(TemplatesMetadata)'
```

Esperado: `200`, `pro True`, `Active` e `5`.

### 2. E-mail ponta a ponta

```bash
curl -s -X DELETE http://localhost:4566/_aws/ses
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/login-requests \
  -H 'Content-Type: application/json' -d '{"email": "success+admin@simulator.amazonses.com"}'
sleep 15
curl -s http://localhost:4566/_aws/ses | python3 -c 'import json,sys;m=json.load(sys.stdin)["messages"];print(len(m), m[-1]["Template"] if m else "-")'
```

Esperado: `202` e `1 jogo-acoes__login-link`.

### 3. Suíte de caixa-preta (cenário 3)

```bash
./scripts/cloud-blackbox-tests.sh
echo "exit $?"
```

Esperado: `2 scenarios passed, 0 failed`, `29 passed` (ou o número atual de testes) e `exit 0`.

### 4. Subida repetida (cenário 2)

```bash
./scripts/cloud-up.sh --no-build
curl -s http://localhost:4566/_aws/ses | python3 -c 'import json,sys;print(len(json.load(sys.stdin)["messages"]))'
```

Esperado: saída 0 sem compilar e `0` mensagens no SES (LocalStack novo).

### 5. Derrubar (cenário 4)

```bash
./scripts/cloud-down.sh; echo "exit $?"
./scripts/cloud-down.sh; echo "exit $?"
docker ps -a --format '{{.Names}}'
ps -eo args | grep -c '^java -jar'
```

Esperado: `exit 0` duas vezes, nenhum container listado e `0`.

### 6. Sem token (cenário 8)

```bash
env -u LOCALSTACK_AUTH_TOKEN ./scripts/cloud-up.sh --no-build; echo "exit $?"
```

Esperado: mensagem pedindo `LOCALSTACK_AUTH_TOKEN` configurado no ambiente de nuvem, `exit 1`,
nenhum container novo e nenhum processo Java iniciado.

### 7. Licença inválida (cenário 5)

```bash
LOCALSTACK_AUTH_TOKEN=x ./scripts/cloud-up.sh --no-build 2>&1 | tail -15; echo "exit ${PIPESTATUS[0]}"
```

Esperado: linhas do log do LocalStack com `License activation failed!`, `exit 1` e o token não
aparece na saída.

### 8. Fora da sessão de nuvem (cenário 6)

```bash
env -u HTTPS_PROXY ./scripts/cloud-up.sh; echo "exit $?"
```

Esperado: mensagem indicando `scripts/docker-up.sh`, `exit 1`, nada iniciado.

### 9. Falha de um serviço (cenário 7)

Provocar com o banco do `email-service` fora do ar no meio da subida não é estável; em vez disso,
uma porta ocupada:

```bash
./scripts/cloud-down.sh
python3 -m http.server 8082 >/dev/null 2>&1 &
./scripts/cloud-up.sh --no-build; echo "exit $?"
kill %1
```

Esperado: o script para no `email-service`, mostra as últimas linhas do log dele (porta 8082 em
uso) e sai com `exit 1`.

### Derrubar no fim

```bash
./scripts/cloud-down.sh
```
