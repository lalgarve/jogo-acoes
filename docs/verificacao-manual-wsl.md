# Verificações manuais pendentes, no WSL

Reúne num roteiro só as verificações manuais que ainda faltam, para rodar no WSL. Cada passo
diz de qual spec e tarefa ele é; o resultado de cada um vai para o `tasks.md` da spec (riscar a
tarefa e registrar o que deu).

Montado em 2026-10-21, a partir de `development`. As expectativas foram atualizadas onde uma
spec posterior mudou o resultado (indicado em cada passo).

| Spec | Tarefa | Onde está aqui |
|---|---|---|
| [05-050](../specs/05-050-perfil-localstack-pro/tasks.md) LocalStack Pro opcional | T005 (roteiro A) | Bloco 1, passo 1 |
| [05-053](../specs/05-053-testes-nao-sobem-containers/tasks.md) Testes não sobem containers | T007, item 4 | Bloco 1, passo 2 |
| [05-047](../specs/05-047-verificacao-dominio-destinatario/tasks.md) Verificação do domínio | T011 | Bloco 1, passo 3 |
| [05-051](../specs/05-051-acentos-assunto-preview/tasks.md) Acentos no assunto | T006 | Bloco 1, passo 4 |
| [05-049](../specs/05-049-swagger-ui-email-service/tasks.md) Swagger UI do e-mail | T007 | Bloco 1, passo 5 |
| [05-048](../specs/05-048-importacao-listas-email-temporario/tasks.md) Listas de e-mail temporário | T015 | Bloco 1, passo 6 |
| [05-054](../specs/05-054-importacoes-uma-de-cada-vez/tasks.md) Importações uma de cada vez | T007 | Bloco 1, passo 7 |
| [05-046](../specs/05-046-publicacao-na-fila-em-segundo-plano/tasks.md) Publicação em segundo plano | T016 | Bloco 1, passo 8 |
| [05-044](../specs/05-044-recriar-templates-ausentes-no-ses/tasks.md) Recriar templates no SES | T013 | Bloco 1, passo 9 |
| [05-053](../specs/05-053-testes-nao-sobem-containers/tasks.md) Testes não sobem containers | T007, itens 1 a 3 | Bloco 2 |
| [05-050](../specs/05-050-perfil-localstack-pro/tasks.md) LocalStack Pro opcional | T006 (roteiro B) | Bloco 3 |

A ordem do Bloco 1 importa: a 05-047 conta os envios e os domínios aprovados desde o banco
vazio, então vem antes das outras que enviam e-mail; a 05-046 apaga a fila de envio e a 05-044
reinicia o LocalStack, então ficam por último.

Precisa de `docker`, `curl`, `jq` e, no Bloco 2, Maven e JDK 21.

## Bloco 1: ambiente completo

### 1. Subida sem `docker-up.conf` (05-050, T005, roteiro A)

```bash
mv docker-up.conf docker-up.conf.bak 2>/dev/null; true
./scripts/docker-up.sh --reset
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
API_KEY=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU
OPS_API_KEY=dak_D9CebHAQ6cq2vAnYIomxJ1nX56qn_5dDRv2J-aHrQFc
```

(`API_KEY` é a chave de teste do cliente `jogo-acoes`, só com `email:send`; `OPS_API_KEY`, a do
cliente `operations`, só com `disposable-email:admin`.)

Esperado na saída do `docker-up.sh`:

1. A primeira linha: `No docker-up.conf, using the template docker-up.conf.example (cp
   docker-up.conf.example docker-up.conf to change it)`, depois `Compose files:
   docker-compose.yml:docker-compose.blackbox.yml` e `LocalStack: Community`.
2. No fim: `Environment up.` e `export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml`.

Depois:

```bash
docker inspect jogo-acoes-localstack-1 -f '{{.Config.Image}}'
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/swagger-ui/index.html
```

Esperado: `localstack/localstack:4` e `200`.

### 2. Sem o repositório do GitHub no `docker` (05-053, T007, item 4)

```bash
docker compose exec -T db-email-service sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select count(*) from email_service.disposable_domain_source"'
```

Esperado: `0`.

### 3. Verificação do domínio do destinatário (05-047, T011)

1. A zona de teste responde:
   ```bash
   docker run --rm --network host alpine:3.20 sh -c \
     'apk add -q bind-tools && for d in with-mx.test only-a.test null-mx.test missing.test; do echo "$d MX: $(dig +short -p 1053 @127.0.0.1 $d MX) A: $(dig +short -p 1053 @127.0.0.1 $d A)"; done'
   ```
   Esperado:
   ```
   with-mx.test MX: 10 mail.with-mx.test. A:
   only-a.test MX: A: 192.0.2.10
   null-mx.test MX: 0 . A: 192.0.2.20
   missing.test MX: A:
   ```
2. Um template para enviar:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"name":"verificacao-dominio","subject":"Teste","body":"Teste de dominio"}'
   ```
   Esperado: o JSON do template e ` [201]` no fim.
3. Domínio com MX:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"verificacao-dominio","recipientEmail":"jogador@with-mx.test"}'
   ```
   Esperado: `{"id":"<uuid>","status":"PENDING"} [202]` (`PENDING` desde a spec 05-046).
4. Domínio só com A:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"verificacao-dominio","recipientEmail":"jogador@only-a.test"}'
   ```
   Esperado: ` [202]` no fim.
5. Domínio inexistente:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"verificacao-dominio","recipientEmail":"jogador@missing.test"}'
   ```
   Esperado: `{"message":"Recipient domain does not accept e-mail: missing.test"} [422]`.
6. MX nulo:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"verificacao-dominio","recipientEmail":"jogador@null-mx.test"}'
   ```
   Esperado: `{"message":"Recipient domain does not accept e-mail: null-mx.test"} [422]`.
7. A lista branca:
   ```bash
   docker compose exec db-email-service psql -U email_service_admin -d email_service \
     -c 'SELECT domain, approved_by FROM email_service.approved_domain ORDER BY domain;'
   ```
   Esperado:
   ```
            domain          | approved_by
   -------------------------+-------------
    only-a.test             | A
    simulator.amazonses.com | SEED
    with-mx.test            | MX
   (3 rows)
   ```
8. Só os dois envios aceitos:
   ```bash
   docker compose exec db-email-service psql -U email_service_admin -d email_service \
     -c 'SELECT recipient_email FROM email_service.email_send ORDER BY created_at;'
   ```
   Esperado: `jogador@with-mx.test` e `jogador@only-a.test`, `(2 rows)`.

### 4. Acentos no assunto da pré-visualização (05-051, T006)

1. Template com acento no assunto e no corpo:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' --data-binary @- <<'JSON'
   {"name":"acentos-assunto","subject":"{{nome}}, sua conta está pronta","body":"<p>Olá, {{nome}}! Sua conta está pronta.</p>"}
   JSON
   ```
   Esperado: `201`, com `"subject":"{{nome}}, sua conta está pronta"` na resposta.
2. Pré-visualizar:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates/acentos-assunto/preview \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"variables":{"nome":"Maria"}}'
   ```
   Esperado:
   ```
   {"body":"<p>Olá, Maria! Sua conta está pronta.</p>","subject":"Maria, sua conta está pronta"} [200]
   ```
   Antes da correção, o assunto vinha `Maria, sua conta estÃ¡ pronta`.

### 5. Swagger UI do Serviço de E-mail (05-049, T007)

Gera as chaves do caderno de testes (`docs/disciplina/caderno-de-testes-email-service/chaves.env`,
fora do git). Precisa vir depois do último `test-api-key.sh restore`, que apaga essas chaves.

```bash
docs/disciplina/caderno-de-testes-email-service/scripts/gerar-chaves.sh
```

1. O contrato abre sem chave:
   ```bash
   curl -s -w '\n[%{http_code}]\n' http://localhost:8082/api/openapi.yaml | head -3
   ```
   Esperado: `openapi: 3.0.3`, `info:`, `  title: Serviço de E-mail` e, no fim, `[200]`.
2. A tela abre sem chave: no navegador, `http://localhost:8082/api/swagger-ui.html`. Esperado:
   o título "Serviço de E-mail", as tags `templates`, `emails` e `disposable-domains`, e o
   botão **Authorize**. Nenhuma mensagem "Failed to load remote configuration".
3. Sem chave, pela tela: `GET /templates` → **Try it out** → **Execute**. Esperado: `401`,
   `{"message":"Missing or invalid X-API-Key"}`.
4. Com a chave de envio: **Authorize**, colar o valor de `CHAVE_ENVIO` de `chaves.env`,
   **Authorize**, **Close**. `GET /templates` → `200`, `[]`. `GET /disposable-domain-sources` →
   `403`, `{"message":"Missing permission: disposable-email:admin"}`.
5. Recarregar a página (F5) e repetir `GET /templates`. Esperado: `200`, sem colar a chave de
   novo.
6. Trocar para a chave de operações: **Authorize** → **Logout** → colar `CHAVE_OPERACOES`.
   `GET /disposable-domain-sources` → `200`; `GET /templates` → `403`,
   `{"message":"Missing permission: email:send"}`.
7. Nada gerado pelas anotações:
   ```bash
   curl -s http://localhost:8082/api/v3/api-docs | jq '.paths | length'
   ```
   Esperado: `0`.

### 6. Importação das listas de e-mail temporário (05-048, T015)

1. Uma lista de teste servida pelo S3 do LocalStack:
   ```bash
   docker compose exec -T localstack awslocal s3 mb s3://disposable-lists
   printf '# lista de teste\ndisposable.test\n  OUTRO-TEMPORARIO.test \n\nlinha invalida\n' \
     | docker compose exec -T localstack awslocal s3 cp - s3://disposable-lists/lista.conf
   ```
   Esperado: `make_bucket: disposable-lists` e nenhum erro no `cp`.
2. Acrescentar o repositório e guardar o `id`:
   ```bash
   RESPONSE=$(curl -s -w ' [%{http_code}]' -X POST http://localhost:8082/api/disposable-domain-sources \
     -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
     -d '{"url":"http://localstack:4566/disposable-lists/lista.conf","description":"Lista de teste"}')
   echo "$RESPONSE"
   SOURCE_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
   echo "$SOURCE_ID"
   ```
   Esperado: o repositório sem `lastDownloadedAt` nem `lastUpdatedAt` (campos vazios não aparecem
   no JSON), `"domainCount":0` e ` [201]` no fim; depois, o `id` `1` (desde a spec 05-053 não há
   repositório inicial no `docker`; antes era `2`).
3. O mesmo endereço de novo:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8082/api/disposable-domain-sources \
     -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
     -d '{"url":"http://localstack:4566/disposable-lists/lista.conf","description":"Lista de teste"}'
   ```
   Esperado: `409`.
4. Cada chave só faz o que a permissão dela deixa:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/api/disposable-domain-sources \
     -H "X-API-Key: $API_KEY"
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"login-link","recipientEmail":"jogador@with-mx.test"}'
   ```
   Esperado: `403` e `{"message":"Missing permission: email:send"} [403]`.
5. Importar esse repositório:
   ```bash
   EXECUTION_ID=$(curl -s -X POST http://localhost:8082/api/disposable-domain-imports \
     -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
     -d "{\"sourceId\":$SOURCE_ID}" | grep -o '"executionId":[0-9]*' | grep -o '[0-9]*')
   sleep 3
   curl -s -H "X-API-Key: $OPS_API_KEY" http://localhost:8082/api/disposable-domain-imports/$EXECUTION_ID; echo
   ```
   Esperado: `"status":"COMPLETED"`, `"added":2`, `"removed":0` e uma linha ignorada (a
   `linha invalida`).
6. Os domínios no banco:
   ```bash
   docker compose exec db-email-service psql -U email_service_admin -d email_service \
     -c 'SELECT domain FROM email_service.disposable_domain ORDER BY domain;'
   ```
   Esperado: `disposable.test` e `outro-temporario.test`, `(2 rows)`.
7. As horas do repositório:
   ```bash
   curl -s -H "X-API-Key: $OPS_API_KEY" http://localhost:8082/api/disposable-domain-sources; echo
   ```
   Esperado: `lastDownloadedAt` e `lastUpdatedAt` preenchidos, com a hora do passo 5.
8. Rodar de novo com a mesma lista: repetir os comandos dos passos 5 e 7. Esperado: `"added":0`,
   `"removed":0`; `lastDownloadedAt` mudou, `lastUpdatedAt` continua a do passo 5.
9. O envio para um domínio temporário é recusado (`disposable.test` tem MX na zona de teste,
   então a recusa vem da lista):
   ```bash
   curl -s -o /dev/null -X POST http://localhost:8082/api/templates \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"name":"verificacao-temporario","subject":"Teste","body":"Teste"}'
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"verificacao-temporario","recipientEmail":"jogador@disposable.test"}'
   ```
   Esperado: `{"message":"Recipient domain is a disposable e-mail domain: disposable.test"} [422]`.
10. Um domínio sai da lista:
    ```bash
    printf 'disposable.test\n' \
      | docker compose exec -T localstack awslocal s3 cp - s3://disposable-lists/lista.conf
    ```
    Repetir os comandos do passo 5. Esperado: `"added":0`, `"removed":1`. O `SELECT` do passo 6
    mostra só `disposable.test`.

### 7. Importações uma de cada vez (05-054, T007)

O bucket e a "Lista de teste" do passo 6 continuam lá, então "importar todos" importa quatro
repositórios: a "Lista de teste" e as três daqui.

1. Guardar a última execução que já existe e criar três listas e três repositórios:
   ```bash
   ANTES=$(docker compose exec -T db-email-service sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select coalesce(max(job_execution_id), 0) from email_service.batch_job_execution"')
   echo "$ANTES"
   for n in 1 2 3; do
     printf "lista$n.test\ncomum.test\n" | docker compose exec -T localstack awslocal s3 cp - s3://disposable-lists/lista$n.conf
     curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8082/api/disposable-domain-sources \
       -H "X-API-Key: $OPS_API_KEY" -H 'Content-Type: application/json' \
       -d "{\"url\":\"http://localstack:4566/disposable-lists/lista$n.conf\",\"description\":\"Lista $n\"}"
   done
   ```
   Esperado: um número (as execuções do passo 6) e `201` três vezes.
2. Importar todos os repositórios:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/disposable-domain-imports \
     -H "X-API-Key: $OPS_API_KEY"
   sleep 5
   ```
   Esperado: quatro execuções na resposta, com `executionId`, e ` [202]` no fim.
3. Conferir que terminaram e que uma só começou depois de a anterior terminar:
   ```bash
   docker compose exec -T db-email-service sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"SELECT job_execution_id, status, start_time, end_time, start_time >= lag(end_time) OVER (ORDER BY start_time) AS depois_da_anterior FROM email_service.batch_job_execution WHERE job_execution_id > $ANTES ORDER BY start_time\""
   ```
   Esperado: quatro linhas `COMPLETED`; na coluna `depois_da_anterior`, vazio na primeira e `t`
   nas outras três.

### 8. Publicação na fila em segundo plano (05-046, T016)

Um template para os envios deste passo:

```bash
curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d '{"name":"roteiro-t016","subject":"Roteiro T016","body":"<p>Oi {{name}}</p>"}'
```

Esperado: o JSON do template e ` [201]` no fim.

**(a) Fila de envio fora do ar**

1. Apagar a fila de envio e pedir um e-mail:
   ```bash
   docker compose exec localstack sh -c 'awslocal sqs delete-queue --queue-url $(awslocal sqs get-queue-url --queue-name jogo-acoes-email-commands --query QueueUrl --output text)'
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"roteiro-t016","recipientEmail":"success+t016a@simulator.amazonses.com","templateData":{"name":"Ada"}}'
   ```
   Esperado: `{"id":"...","status":"PENDING"} [202]`. Antes da spec, a resposta era `503`.
2. O envio está pendente, com o erro gravado:
   ```bash
   sleep 3
   docker compose exec db-email-service psql -U email_service_admin -d email_service -c "select status, attempts, last_error_type, template_data is not null as keeps_template_data from email_service.email_send where recipient_email = 'success+t016a@simulator.amazonses.com';"
   ```
   Esperado: `PENDING`, `attempts` maior que zero (cresce a cada vez que o comando é repetido),
   `QUEUE_UNAVAILABLE`, `t`.
3. Recriar a fila e conferir o envio:
   ```bash
   docker compose exec localstack sh /etc/localstack/init/ready.d/01-create-queue.sh
   sleep 10
   docker compose exec db-email-service psql -U email_service_admin -d email_service -c "select status, attempts, last_error_type, template_data is not null as keeps_template_data from email_service.email_send where recipient_email = 'success+t016a@simulator.amazonses.com';"
   ```
   Esperado: `QUEUED`, `QUEUE_UNAVAILABLE` (o último erro), `f` (os dados do template não ficam
   guardados depois de publicado).
4. O e-mail chegou ao SES:
   ```bash
   sleep 10
   curl -s 'http://localhost:4566/_aws/ses?email=no-reply@jogo-acoes.example' | grep -o 'success+t016a@simulator.amazonses.com' | head -1
   ```
   Esperado: `success+t016a@simulator.amazonses.com`; o e-mail também aparece no `ses-viewer`,
   em `http://localhost:3005`. Se não aparecer, conferir o gatilho da Lambda:
   ```bash
   docker compose exec localstack awslocal lambda list-event-source-mappings --query 'EventSourceMappings[].[EventSourceArn,State]' --output text
   ```
   Esperado: `arn:aws:sqs:us-east-1:000000000000:jogo-acoes-email-commands	Enabled`.

**(b) Template sumiu do SES**

5. Apagar o template do SES e pedir um e-mail:
   ```bash
   docker compose exec localstack awslocal ses delete-template --template-name jogo-acoes__roteiro-t016
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"roteiro-t016","recipientEmail":"success+t016b@simulator.amazonses.com","templateData":{"name":"Ada"}}'
   ```
   Esperado: `{"id":"...","status":"PENDING"} [202]`.
6. A Lambda não acha o template e põe a mensagem na DLQ com `TEMPLATE_MISSING`; o
   `email-service` recria o template e publica de novo:
   ```bash
   sleep 15
   docker compose exec db-email-service psql -U email_service_admin -d email_service -c "select status, attempts, last_error_type from email_service.email_send where recipient_email = 'success+t016b@simulator.amazonses.com';"
   docker compose exec db-email-service psql -U email_service_admin -d email_service -c "select name, ses_recreated_at is not null as recreated from email_service.email_template where name = 'roteiro-t016';"
   docker compose exec localstack awslocal ses get-template --template-name jogo-acoes__roteiro-t016 --query 'Template.SubjectPart' --output text
   docker compose logs email-service | grep -E "TEMPLATE_MISSING|Recreated template jogo-acoes__roteiro-t016"
   ```
   Esperado: `QUEUED`, `1`, `TEMPLATE_MISSING`; `roteiro-t016`, `t`; `Roteiro T016`; e as linhas
   `... TemplateService : Recreated template jogo-acoes__roteiro-t016 on SES after a
   TEMPLATE_MISSING delivery error` e `... DeadLetterConsumer : Send ... was not delivered
   (TEMPLATE_MISSING): Template (jogo-acoes__roteiro-t016) does not exist ...`.
7. O e-mail chegou ao SES:
   ```bash
   sleep 10
   curl -s 'http://localhost:4566/_aws/ses?email=no-reply@jogo-acoes.example' | grep -o 'success+t016b@simulator.amazonses.com' | head -1
   ```
   Esperado: `success+t016b@simulator.amazonses.com`, e o e-mail no `ses-viewer`.

### 9. Recriar no SES os templates que só o banco tem (05-044, T013)

1. O `app` subiu e cadastrou os templates dele no SES:
   ```bash
   docker compose logs app | grep -c "Started JogoAcoesApplication"
   docker compose exec localstack awslocal ses list-templates --query 'TemplatesMetadata[].Name' --output text
   ```
   Esperado: `1` e, na linha seguinte, os nomes `jogo-acoes__invite`, `jogo-acoes__login-link`,
   ... (com os templates criados nos passos anteriores) separados por tabulação.
2. Reiniciar só o LocalStack. O SES perde os templates; o banco do `email-service` continua com
   eles:
   ```bash
   docker compose restart localstack
   docker compose exec localstack awslocal ses list-templates --query 'TemplatesMetadata[].Name' --output text
   ```
   Esperado: uma linha vazia.
3. Reiniciar o `app`, que sincroniza os templates de novo:
   ```bash
   docker compose restart app
   sleep 30
   docker compose logs app --since 2m | grep -E "Started JogoAcoesApplication|answered 409"
   docker compose exec localstack awslocal ses list-templates --query 'TemplatesMetadata[].Name' --output text
   ```
   Esperado: uma linha com `Started JogoAcoesApplication` e nenhuma com `answered 409`; os
   templates `jogo-acoes__...` do `app` de volta no SES. Se ainda não aparecer o `Started`,
   esperar mais uns segundos e repetir o `logs`.
4. Atualizar e pré-visualizar um template que o SES perdeu:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"name":"roteiro-t013","subject":"Roteiro T013","body":"<p>Oi {{name}}</p>"}'
   docker compose exec localstack awslocal ses delete-template --template-name jogo-acoes__roteiro-t013
   curl -s -w ' [%{http_code}]\n' -X PUT http://localhost:8082/api/templates/roteiro-t013 \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"subject":"Roteiro T013 v2","body":"<p>Oi {{name}}</p>"}'
   docker compose exec localstack awslocal ses delete-template --template-name jogo-acoes__roteiro-t013
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/templates/roteiro-t013/preview \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"variables":{"name":"Ada"}}'
   ```
   Esperado (as datas mudam):
   ```
   {"body":"<p>Oi {{name}}</p>","createdAt":"...","name":"roteiro-t013","subject":"Roteiro T013","updatedAt":"..."} [201]
   {"body":"<p>Oi {{name}}</p>","createdAt":"...","name":"roteiro-t013","subject":"Roteiro T013 v2","updatedAt":"..."} [200]
   {"body":"<p>Oi Ada</p>","subject":"Roteiro T013 v2"} [200]
   ```
5. Recriação na subida do `email-service`:
   ```bash
   docker compose restart localstack
   docker compose restart email-service
   sleep 20
   docker compose logs email-service --since 1m | grep "Recreated template"
   docker compose exec localstack awslocal ses list-templates --query 'TemplatesMetadata[].Name' --output text
   ```
   Esperado: uma linha `... SesTemplateReconciler : Recreated template jogo-acoes__<nome> on SES
   from the database` para cada template do banco, e todos eles no `list-templates`. Se aparecer
   `SES template reconciliation failed, trying again in ...`, o LocalStack ainda estava subindo:
   esperar uns segundos e repetir os dois últimos comandos.

Derrubar no fim do bloco:

```bash
docker compose down -v
```

## Bloco 2: os testes não sobem containers (05-053, T007, itens 1 a 3)

1. Só a infraestrutura dos testes:
   ```bash
   export COMPOSE_FILE=docker-compose.yml
   docker compose up -d --wait db-email-service localstack dns config-server
   docker ps --format '{{.Names}}' | sort
   ```
   Esperado: `jogo-acoes-config-server-1`, `jogo-acoes-db-email-service-1`, `jogo-acoes-dns-1` e
   `jogo-acoes-localstack-1`.
2. Suíte da `email-lambda` com o LocalStack de pé:
   ```bash
   docker ps -aq | wc -l
   mvn -pl email-lambda -am test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD|testcontainers"
   docker ps -aq | wc -l
   ```
   Esperado: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`, nenhuma linha
   com `testcontainers`, e o mesmo número de containers antes e depois.
3. Suíte da `email-lambda` sem o LocalStack:
   ```bash
   docker compose stop localstack
   mvn -pl email-lambda -am test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD" | tail -2
   docker ps --format '{{.Names}}' | grep -c localstack
   ```
   Esperado: `Tests run: 12, Failures: 0, Errors: 5, Skipped: 0`, `BUILD FAILURE` e `0` (nenhum
   LocalStack subiu no lugar).

Derrubar no fim do bloco:

```bash
docker compose down -v
```

## Bloco 3: LocalStack Pro (05-050, T006, roteiro B)

```bash
cp docker-up.conf.example docker-up.conf
sed -i 's/^LOCALSTACK_PRO=false/LOCALSTACK_PRO=true/' docker-up.conf
```

Abrir `docker-up.conf` num editor e colar o token em `LOCALSTACK_AUTH_TOKEN=` (sem aspas). Depois:

```bash
git status --short docker-up.conf
./scripts/docker-up.sh --reset
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml:docker-compose.localstack-pro.yml
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
4. O ciclo de envio pela `email-lambda`:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/login-requests \
     -H 'Content-Type: application/json' \
     -d '{"email": "success+admin@simulator.amazonses.com"}'
   sleep 10
   curl -s http://localhost:4566/_aws/ses | jq '.messages | length'
   ```
   Esperado: `202` e um número maior que `0`.
5. Token inválido: trocar o token do arquivo por `x` e rodar `./scripts/docker-up.sh`. Esperado:
   `docker-up: localstack exited; its last log lines:`, a mensagem `License activation failed!`
   e a última linha `docker-up: docker compose up ... failed`. Colocar o token certo de volta.

Derrubar e voltar ao estado anterior:

```bash
docker compose down -v
rm docker-up.conf
mv docker-up.conf.bak docker-up.conf 2>/dev/null; true
```

## Fora deste roteiro

- **05-051, T004** (`mvn -pl app -am verify` verde): a suíte do `app` roda no CI de toda PR para
  `development`; ficou verde nas PRs #196 e #198 (com a correção de conexões da #196).
- **05-051, T005** (tirar a nota "Falha encontrada na execução de 2026-10-14" do caderno de
  testes do `email-service`): depende da correção estar no `master`, que está congelado até a
  avaliação da disciplina.
