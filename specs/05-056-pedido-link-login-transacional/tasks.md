# Tasks: pedido de link de login não deixa rastro quando o e-mail não sai

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** os testes que reproduzem o defeito vêm primeiro, falhando pelo motivo esperado;
depois a correção. Nenhuma task começa sem pedido explícito (constitution, "Nunca começar a
implementar sem pedido explícito").

Todas as tarefas abaixo são acompanhadas como checklist na Issue
[#135](https://github.com/lalgarve/jogo-acoes/issues/135).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | `EmailSender` de teste ligável, só em `app/src/test/java/.../common/` (decisão resolvida 1 do `plan.md`): desligado, delega ao `StubEmailSender`; ligado, lança `EmailServiceUnavailableException`. Registrado como `@Primary` no contexto Cucumber (`CucumberSpringConfiguration`) e desligado num `@After` do Cucumber, para não vazar entre cenários. Rodar a suíte Cucumber do `app` e confirmar que nada muda com ele desligado | — | [P] | #135 |
| ~~T002~~ | `login.feature`: cenário `Requesting a login link while the e-mail service is unavailable changes nothing` (texto na `spec.md`), ao lado de "Requesting a new login link invalidates the previous one". Passos novos em `LoginSteps`: `the e-mail service is unavailable` (liga o T001), `the system reports that the e-mail could not be sent` (`503`, corpo `Error`), `no new login link was issued` (nenhum outro `link_record` do usuário e nenhum `LOGIN_LINK_ISSUED` dele) e `the previous login link can still be used to log in` (link prévio sem `invalidated_at` e aceito por `GET /login-links/{token}`), nesta ordem, porque consumir o link prévio muda o estado dele. Rodar e confirmar que falha hoje | T001 | | #135 |
| ~~T003~~ | `RecipientDomainRejectionIntegrationTest.aLoginLinkToADomainThatTakesNoEmailIsRejected`: antes do pedido, gravar um link de login ativo para o usuário de `missing.test`; depois do `422`, conferir que esse link continua sem `invalidated_at`, que não há outro `link_record` do usuário e que não há `LOGIN_LINK_ISSUED` dele. Rodar contra o Compose e confirmar que falha hoje pelos mesmos motivos | — | [P] | #135 |
| ~~T004~~ | `LoginLinkRequestService` novo em `loginsession`, com `@Transactional requestLoginLink(String email, String returnTo)`: busca o usuário (desconhecido → volta sem fazer nada), valida `returnTo`, invalida os links ativos, cria o link, grava `LOGIN_LINK_ISSUED` e envia. `LoginController.requestLoginLink` passa a só chamar o serviço e devolver `202`; saem do controller `UserService`, `EmailSender` e `AuditLogService`; Javadoc da classe atualizado. Confirmar T002 e T003 verdes, e verdes também "Requesting a new login link invalidates the previous one", o resto de `login.feature`, `redirect_after_login.feature` e `AuditLoggingIntegrationTest` | T002, T003 | | #135 |
| ~~T005~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os caminhos de erro alterados (`EmailServiceUnavailableException` e `RecipientDomainRejectedException` lançadas dentro da transação do pedido de link, desfazendo-a); apontar T002 e T003 como os testes que os exercitam. Registrar abaixo | T004 | | #135 |
| ~~T006~~ | Rodar `mvn -pl app -am verify` com a infraestrutura do Compose de pé e a chave de teste restaurada (`README.md`) e confirmar verde. Se o container da sessão não conseguir rodar algum teste por limitação do ambiente, registrar qual e fechar pelo CI da PR para `development` | T004 | | #135 |
| T007 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T007)" abaixo e conferir os resultados esperados | T006 | | #135 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- A Issue #135 leva os labels `iteration-5` e `fix`.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Verificação manual (T007)

Na raiz do repositório, no WSL, com o branch da correção. O `down -v` começa de um banco vazio,
para que as contagens abaixo saiam exatas; `--build` garante que a imagem do `app` tem a
correção. O overlay `blackbox` cria o administrador `success+admin@simulator.amazonses.com`, que
é o usuário usado no roteiro.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
docker compose up -d --wait --build db db-email-service localstack
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
docker compose up -d --wait --build app
```

O `--wait` do `app` volta antes de ele terminar de subir
([#207](https://github.com/lalgarve/jogo-acoes/issues/207)): o Spring ainda leva uns 20 s para
aceitar conexões e, depois, cadastra os cinco templates de e-mail do `app` no `email-service`. Se
o passo 2 parar o `email-service` antes disso, o `app` sai. Esperar os cinco templates no banco do
`email-service` (vazio por causa do `down -v`):

```bash
for i in $(seq 1 120); do
  n=$(docker compose exec -T db-email-service psql -U email_service_admin -d email_service -tAc \
    "SELECT count(*) FROM email_service.email_template")
  [ "$n" = 5 ] && break
  sleep 1
done
echo "templates=$n"
docker compose ps --format '{{.Service}} {{.State}}' app
```

Esperado: `templates=5` e `app running`.

Consultas usadas nos passos (copiar uma vez no mesmo terminal):

```bash
links() {
  docker compose exec -T db psql -U jogo_acoes_admin -d jogo_acoes -tAc \
    "SELECT count(*) FILTER (WHERE invalidated_at IS NULL AND used_at IS NULL) || ' ativo(s), ' || count(*) || ' no total' FROM jogo_acoes.link_record WHERE email = 'success+admin@simulator.amazonses.com'"
}
auditoria() {
  docker compose exec -T db psql -U jogo_acoes_admin -d jogo_acoes -tAc \
    "SELECT count(*) || ' LOGIN_LINK_ISSUED' FROM jogo_acoes.log WHERE log_type = 'LOGIN_LINK_ISSUED' AND message = 'Login link issued to success+admin@simulator.amazonses.com'"
}
pedir_link() {
  curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8080/api/login-requests \
    -H 'Content-Type: application/json' \
    -d '{"email":"success+admin@simulator.amazonses.com"}'
}
```

1. Primeiro pedido, com o `email-service` no ar:
   ```bash
   pedir_link; links; auditoria
   ```
   Esperado:
   ```
    [202]
   1 ativo(s), 1 no total
   1 LOGIN_LINK_ISSUED
   ```
2. Derrubar o `email-service` e pedir de novo:
   ```bash
   docker compose stop email-service
   pedir_link; links; auditoria
   ```
   Esperado (nada mudou desde o passo 1):
   ```
   {"message":"The e-mail could not be sent right now, try again"} [503]
   1 ativo(s), 1 no total
   1 LOGIN_LINK_ISSUED
   ```
   Antes da correção, o mesmo passo mostra `1 ativo(s), 2 no total` e `2 LOGIN_LINK_ISSUED`: o
   link do passo 1 foi invalidado e o link novo, que não saiu, ficou ativo.
3. Subir o `email-service` de novo e pedir mais uma vez:
   ```bash
   docker compose start email-service
   docker compose up -d --wait email-service
   pedir_link; links; auditoria
   ```
   Esperado (o fluxo normal continua igual: o link anterior é invalidado e o novo fica ativo):
   ```
    [202]
   1 ativo(s), 2 no total
   2 LOGIN_LINK_ISSUED
   ```

Para derrubar: `docker compose down -v`.

## Registro

Implementação de 2026-10-24, numa sessão de nuvem, com a infraestrutura de `./scripts/cloud-up.sh`
(spec 05-052).

- **T001**: `common/testsupport/SwitchableEmailSender` (`@TestComponent @Primary`), importado só
  por `CucumberSpringConfiguration`; o `@After` de `CommonSteps` liga o envio de novo depois de
  cada cenário. Com ele desligado, os 88 cenários Cucumber do `app` passaram como antes.
- **T002**: cenário e passos escritos. Antes da correção, o cenário falhou em
  `no new login link was issued`: `Expecting actual: [18L, 19L] to contain exactly: [18L]` (o
  link novo, que não saiu, ficou gravado).
- **T003**: `aLoginLinkToADomainThatTakesNoEmailIsRejected` virou
  `aLoginLinkToADomainThatTakesNoEmailIsRejectedAndChangesNothing`. Antes da correção, falhou
  com `expected: null but was: 2026-10-24T17:56:55.124346`: o link prévio foi invalidado por um
  pedido que respondeu `422`.
- **T004**: `LoginLinkRequestService` criado e `LoginController.requestLoginLink` reduzido a
  chamá-lo. T002 e T003 passaram, junto com os 88 cenários Cucumber, `AuditLoggingIntegrationTest`
  e `ArchitectureTest`.
- **T005**: nenhuma classe de exceção criada. Caminhos de erro alterados, ambos agora desfazendo
  a transação do pedido de link: `EmailServiceUnavailableException` (exercitado por T002, com o
  dublê) e `RecipientDomainRejectedException` (exercitado por T003, contra o `email-service`
  real). Os tratadores do `ApiExceptionHandler` não mudaram.
- **T006**: `mvn -B -pl app -am verify`: 286 testes, 0 falhas, 0 erros, 0 pulados
  (`BUILD SUCCESS`).
- **Roteiro da T007, adaptado à nuvem** (não substitui a T007): com o `app` recompilado pelo
  `cloud-up.sh`, um pedido com o `email-service` no ar deu `202`, `1 ativo(s), 1 no total` e
  `1 LOGIN_LINK_ISSUED`. Na nuvem o `email-service` roda com `java -jar`, então foi derrubado
  com `kill` no PID (em vez de `docker compose stop`). Um segundo pedido, enquanto ele ainda
  encerrava, saiu normalmente (`202`, `1 ativo(s), 2 no total`, `2 LOGIN_LINK_ISSUED`). Com ele já
  fora do ar, o pedido seguinte deu
  `{"message":"The e-mail could not be sent right now, try again"} [503]`, e as contagens ficaram
  iguais (`1 ativo(s), 2 no total`, `2 LOGIN_LINK_ISSUED`), com o link ativo de antes ainda
  ativo. No roteiro da T007, o `docker compose stop` só volta depois que o container parou, então
  essa corrida não acontece.

## Verificação manual no WSL (T007, 2026-10-24)

O roteiro "Verificação manual (T007)" acima, no branch `claude/project-thread-4wissn` (`8e9d408`),
no WSL2 com Docker 29.8.0 e Compose v5.5.1. **A T007 continua aberta:** na primeira rodada, o
roteiro como está escrito não deu o resultado esperado; a correção deu, numa segunda rodada com
uma espera pelo `app` que o roteiro não tem.

| Etapa | Resultado |
|---|---|
| Subida, primeira tentativa | `docker compose up -d --wait --build db db-email-service localstack` passou; o build do `email-service` falhou com `dev.leilaalgarve.apikey:api-key-validation:jar:1.1.0 was not found in https://repo.maven.apache.org/maven2`. Causa: defeito do `email-service/Dockerfile`, não da correção (o passo do `install-api-key-lib.sh` vem das camadas do build sem rodar, depois de o BuildKit apagar o cache mount `/root/.m2`); correção na spec 05-062. Contornado com `docker compose build --no-cache email-service`, e a subida repetida desde o `down -v`: todos os serviços de pé |
| 1, primeira rodada | **Não saiu como o esperado**: ` [000]` (conexão recusada), `0 ativo(s), 0 no total`, `0 LOGIN_LINK_ISSUED`. O `app` não tem healthcheck, então o `up -d --wait --build app` volta antes de o Spring aceitar conexões (`Started JogoAcoesApplication` só às 18:19:46) |
| 2, primeira rodada | `{"message":"The e-mail could not be sent right now, try again"} [503]`, `0 ativo(s), 0 no total`, `0 LOGIN_LINK_ISSUED`. O `docker compose stop email-service` caiu durante a sincronização de templates do `app` (`EmailTemplateSynchronizer`, no `ApplicationReadyEvent`): `PUT /templates/invite` falhou com `UnknownHostException: email-service`, `Application run failed`, e o `app` saiu com `Exited (1)` |
| 3, primeira rodada | ` [000]`: o `app` já tinha saído |
| Segunda rodada | `docker compose up -d --wait app`, espera até `/v3/api-docs` responder (13 s) e mais 15 s, com o banco ainda sem links do usuário (`0 ativo(s), 0 no total`, `0 LOGIN_LINK_ISSUED`) |
| 1, segunda rodada | ` [202]`, `1 ativo(s), 1 no total`, `1 LOGIN_LINK_ISSUED`, como o esperado |
| 2, segunda rodada | `{"message":"The e-mail could not be sent right now, try again"} [503]`, `1 ativo(s), 1 no total`, `1 LOGIN_LINK_ISSUED`: nada mudou, como o esperado |
| 3, segunda rodada | ` [202]`, `1 ativo(s), 2 no total`, `2 LOGIN_LINK_ISSUED`, como o esperado; o `app` continuou `running` |
| Derrubar | `docker compose down -v`; `git status` limpo |

Para fechar a T007: o roteiro precisa esperar o `app` ficar pronto e terminar a sincronização de
templates antes do passo 1, e ser rodado de novo no WSL.

## Repetição no WSL com a espera pelo `app` (T007, 2026-10-27)

O roteiro "Verificação manual (T007)" acima, já com a espera pelos cinco templates
([#207](https://github.com/lalgarve/jogo-acoes/issues/207)), no branch
`claude/project-thread-4wissn` (`e47ae4a` mais a espera), no WSL2 com Docker 29.8.0 e Compose
v5.5.1, com os comandos colados em sequência, sem pausa entre eles. **A T007 continua aberta:**
na primeira rodada, o passo 3 falhou sem causa encontrada; a segunda rodada deu o esperado em
todos os passos.

| Etapa | Rodada 1 | Rodada 2 |
|---|---|---|
| Subida | Os cinco comandos com `exit=0`; o build do `email-service` passou | Igual |
| Espera | `templates=5` em 4 s, `app running` | `templates=5` em 11 s, `app running` |
| 1 | ` [202]`, `1 ativo(s), 1 no total`, `1 LOGIN_LINK_ISSUED` | Igual |
| 2 | `{"message":"The e-mail could not be sent right now, try again"} [503]`, `1 ativo(s), 1 no total`, `1 LOGIN_LINK_ISSUED`; `app` de pé | Igual |
| 3 | **Não saiu como o esperado**: `{"message":"The e-mail could not be sent right now, try again"} [503]`, `1 ativo(s), 1 no total`, `1 LOGIN_LINK_ISSUED`, com o `email-service` `Healthy` | ` [202]`, `1 ativo(s), 2 no total`, `2 LOGIN_LINK_ISSUED`, como o esperado |
| Fim | `app running` | `app running`; `docker compose down -v`, `git status` limpo |

Rodada 1, passo 3, em detalhe:

- No log do `email-service`, `Tomcat started on port 8080` às 02:57:04.776 e `Started
  EmailServiceApplication` às 02:57:05.175. Nada às 02:57:10.
- No log do `app`, o pedido às 02:57:10.125 e a chamada ao `email-service` às 02:57:10.137, que
  falhou em 3 ms: `feign.RetryableException: Connection refused executing POST
  http://email-service:8080/api/emails`. No passo 2, a falha tinha sido outra: `Connect timed out`.
- Um pedido repetido à mão às 02:57:30 deu `202`.
- Hipótese descartada: IP novo do `email-service` depois do `stop`/`start`, com o `app` guardando o
  antigo (a JVM guarda o nome por 30 s). Num `stop`/`start` à parte, o IP ficou o mesmo
  (`172.18.0.9`), e o `getent hosts email-service` de dentro do `app` dava esse IP. O IP do passo 1
  da rodada 1 não foi anotado, então não dá para saber se mudou ali; na rodada 2, ficou
  `172.18.0.7` antes e depois.
- Duas repetições extras dos passos 2 e 3 (sem o `down -v`, pedindo o link logo depois do
  `Healthy`): `503` com o `email-service` parado e `202` no primeiro pedido depois do `Healthy`
  (às 02:58:37 e 02:58:54), nas duas.

A falha da rodada 1 não se repetiu em três tentativas. Ela não diz respeito à correção da spec (o
pedido com `503` não deixou rastro, como pede a spec), e sim a o `app` voltar a alcançar o
`email-service` logo depois de ele religar.
