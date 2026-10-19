# Tasks: Verificação do domínio do destinatário por DNS (A e MX) com lista branca

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** o DNS de teste primeiro (sem ele nenhum teste novo roda), depois os testes do
comportamento novo, falhando pelo motivo esperado, e só então as correções.

Issue-épico: a criar quando a spec for aprovada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Serviço `dns` (CoreDNS) no `docker-compose.yml`, `docker/dns/Corefile` e a zona de teste (`plan.md`, "DNS de teste no Compose"), incluindo os domínios já usados nos testes. Conferir com `dig` (comandos da verificação manual, passo 1) | — | | — |
| ~~T002~~ | `send_email.feature`: `Rule` "The recipient's domain must accept e-mail" com os casos de `plan.md`, "Verificações", item 1. Confirmar que falham (todos `202`, tabela inexistente) | T001 | [P] | — |
| ~~T003~~ | `DnsMailDomainLookupTest` e `RecipientDomainVerifierTest` (concorrência e `IDN`). Registrar que não compilam antes do pacote `domain/` | T001 | [P] | — |
| ~~T004~~ | `app`: testes do *gateway* (`422` → `RecipientDomainRejectedException`), do link de login (`422`) e do convite em grupo com um endereço recusado. Confirmar que falham | T001 | [P] | — |
| ~~T005~~ | Contratos: `422` em `POST /emails` (`docs/openapi-email-service.yaml`) e nas rotas do `app` que enviam e-mail (`docs/openapi.yaml`) | — | [P] | — |
| ~~T006~~ | Migration da lista branca, `dnsjava`, pacote `domain/`, chamada em `EmailSendService`, `422` no `ApiExceptionHandler`, `email.domain-check.*` em `config-repo/` e no `application.yml` de teste. Confirmar T002 e T003 verdes e `SchemaLayoutTest` verde | T002, T003, T005 | | — |
| ~~T007~~ | `app`: `RecipientDomainRejectedException`, tradução no `EmailServiceGateway`, `422` no `ApiExceptionHandler`, convite em grupo segue depois de um recusado. Confirmar T004 verde | T004, T005, T006 | | — |
| ~~T008~~ | `README.md`: o serviço `dns`, a zona de teste e como incluir um domínio novo nela | T001 | [P] | — |
| ~~T009~~ | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: `RecipientDomainRejectedException` no `email-service` e no `app`) e os branches de exceção adicionados ou alterados (cada resultado do DNS, inclusive sem resposta; `IDN` inválido; `ON CONFLICT`; tradução do `422` no *gateway*; o `catch` do convite em grupo); para cada um, apontar o teste que o exercita. O que não tiver teste é justificado em `plan.md` e avisado no chat ao abrir a PR. Registrar a lista aqui | T006, T007 | | — |
| ~~T010~~ | Rodar a suíte completa do `email-service`, `mvn -pl app -am verify` e o `blackbox-tests/` — confirmar verde (nenhum teste com domínio fora da zona) | T008, T009 | | — |
| T011 | Verificação manual (Leila): rodar o roteiro "Verificação manual (T011)" abaixo e conferir os resultados esperados | T010 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico, ou uma Issue própria quando grande o
  suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de tipo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.

## Registro da implementação (2026-10-14)

- **T001**: serviço `dns` (`coredns/coredns:1.11.3`) no `docker-compose.yml`, com
  `docker/dns/Corefile`, `docker/dns/test.zone` e `docker/dns/mail-domain.zone` (MX para
  `example.com`, `exemplo.com`, `acme.com`, `client-a.example` e `jogo-acoes.example`). Conferido
  com `dnspython` contra `127.0.0.1:1053`: cada domínio da zona responde como no `plan.md`,
  `missing.test` dá NXDOMAIN, `unavailable.test` não responde e um domínio fora das zonas
  (`simulator.amazonses.com`, `gmail.com`) dá `REFUSED`.
- **T002/T003**: antes do pacote `domain/`, 7 cenários novos de `send_email.feature` falharam
  pelo motivo esperado (todos `202`, `with-mx.test is not whitelisted` etc.) e os dois testes
  JUnit não compilavam. `RecipientDomainVerifierTest` reúne a concorrência (10 envios
  simultâneos → uma linha `MX`), o `IDN` (`jogador@ação.test` → `xn--ao-siap.test` na mensagem)
  e o rótulo de 64 caracteres (recusado).
- **T004**: `EmailServiceClientIntegrationTest.aRecipientDomainEmailServiceRejectsBecomesItsOwnException`
  e `email/RecipientDomainRejectionIntegrationTest` (5 testes), os dois contra o `email-service`
  real. O teste do link de login vai pelo HTTP (`POST /api/login-requests` → `422`), não com o
  `EmailSender` lançando a exceção como dizia o `plan.md`: o `email-service` real já recusa
  `missing.test`, sem *fake*.
- **T005**: `422` em `POST /emails` e nas rotas do `app` que enviam para um endereço só
  (`/login-requests`, `/competitions/{id}/entry-requests`,
  `/competitions/{id}/players/{participationId}/invite-emails`, resposta `RecipientDomainRejected`);
  as rotas em grupo dizem na descrição que o jogador recusado é pulado.
- **T006**: suíte do `email-service` verde: 68 testes, 2 pulados (os mesmos 2 cenários pulados
  no `master`). `SchemaLayoutTest` verde.
- **T007**: além do `CompetitionService.decideInviteEmailTiming`, os laços de
  `PlayerManagementService.invitePlayers` e `resendInviteEmails` também pulam o jogador recusado
  (ampliação registrada no `plan.md`). O `WARN` sai do `EmailServiceLoggingAspect`. T004 verde.
- **T008**: `README.md`, seção "Verificação do domínio do destinatário", o `dns` na subida dos
  testes e na tabela de endereços. A CI (`.github/workflows/ci.yml`) também sobe o `dns`.
- **T009**: classes de exceção criadas e o teste que as lança:
  - `email-service` `RecipientDomainRejectedException`: cenários de recusa de
    `send_email.feature` (`missing.test`, `no-mail.test`, `null-mx.test`) e
    `RecipientDomainVerifierTest` (rótulo inválido para o `IDN`);
  - `app` `RecipientDomainRejectedException`: `EmailServiceClientIntegrationTest` (tradução do
    `422`) e `RecipientDomainRejectionIntegrationTest.resendingToOneRejectedAddressReachesTheCaller`.

  Branches de exceção e o teste de cada um: resultado `MX`, `A`, `AAAA`, MX nulo, domínio
  inexistente, sem registro, sem resposta e nome inválido (`DnsMailDomainLookupTest`, 9 testes);
  `IDN` inválido (`RecipientDomainVerifierTest`); `ON CONFLICT` (concorrência em
  `RecipientDomainVerifierTest`); `422` → exceção no *gateway* (`EmailServiceClientIntegrationTest`);
  `422` do `ApiExceptionHandler` do `app` (`aLoginLinkToADomainThatTakesNoEmailIsRejected`); os
  três `catch` dos envios em grupo (`sendingInvitesNowSkipsOnlyTheRejectedAddress`,
  `invitingAGroupSkipsOnlyTheRejectedAddress`, `resendingToAGroupSkipsOnlyTheRejectedAddress`).
  Nada sem teste.
- **T010**: `mvn -pl app -am verify`: 284 testes, 279 verdes. Os 5 que falham são
  `EmailServiceClientIntegrationTest.sendingRecordsEmailServiceIdAndReachesSesWithTheTemplate`
  ("no e-mail ... reached SES on LocalStack"), o mesmo caso registrado na 05-041: neste container
  o LocalStack não cria o *runtime* da Lambda, então a mensagem fica na fila (o `email-service`
  aceitou o envio, `simulator.amazonses.com` é `SEED`). O CI sobe o Compose completo e roda esse
  teste. O `email-service` dos testes do `app` rodou do jar do branch, na rede do Compose, com o
  `dns:53`. `blackbox-tests/` não rodou aqui pelo mesmo motivo da Lambda; os domínios que ela
  usa (`simulator.amazonses.com`, `example.com`) são aprovados.

## Verificação manual (T011)

Na raiz do repositório, no WSL, com o branch da PR da implementação (ou o `master` depois do
merge). Só o `email-service` e o DNS de teste são necessários; `--build` garante a imagem com a
mudança. O `down -v` começa de um banco vazio, sem domínio na lista branca.

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.blackbox.yml
docker compose down -v
docker compose up -d --wait --build db-email-service localstack dns
./scripts/test-api-key.sh restore
docker compose up -d --wait --build email-service
./scripts/test-api-key.sh restore
API_KEY=dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU
```

1. A zona de teste responde:
   ```bash
   docker run --rm --network host alpine:3.20 sh -c \
     'apk add -q bind-tools && for d in with-mx.test only-a.test null-mx.test missing.test; do echo "$d MX: $(dig +short -p 1053 @127.0.0.1 $d MX) A: $(dig +short -p 1053 @127.0.0.1 $d A)"; done'
   ```
   Esperado (os endereços dependem da zona escrita na T001):
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
   Esperado: `{"id":"<uuid>","status":"QUEUED"}` e ` [202]` (`PENDING`, se a spec 05-046 já
   estiver implementada).
4. Domínio só com A: o mesmo `curl` com `"recipientEmail":"jogador@only-a.test"`.
   Esperado: ` [202]`.
5. Domínio inexistente:
   ```bash
   curl -s -w ' [%{http_code}]\n' -X POST http://localhost:8082/api/emails \
     -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"templateName":"verificacao-dominio","recipientEmail":"jogador@missing.test"}'
   ```
   Esperado:
   ```
   {"message":"Recipient domain does not accept e-mail: missing.test"} [422]
   ```
6. MX nulo: o mesmo `curl` com `"recipientEmail":"jogador@null-mx.test"`.
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
8. Só dois envios gravados (os de `with-mx.test` e `only-a.test`):
   ```bash
   docker compose exec db-email-service psql -U email_service_admin -d email_service \
     -c 'SELECT recipient_email FROM email_service.email_send ORDER BY created_at;'
   ```
   Esperado: `jogador@with-mx.test` e `jogador@only-a.test`, `(2 rows)`.

Para derrubar: `docker compose down -v`.
