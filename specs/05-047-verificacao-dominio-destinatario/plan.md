# Plan: Verificação do domínio do destinatário por DNS (A e MX) com lista branca

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `send/EmailSendService.send` (05-031): confere remetente (`409`) e template
  (`TemplateService.reference`, `404`), grava `email_send` e publica na fila. A spec 05-046
  (rascunho) troca a publicação por um status `PENDING`; a verificação desta spec fica antes da
  gravação nos dois desenhos.
- `recipientEmail` já é validado como e-mail pelo DTO gerado do contrato (`400`).
- `ApiExceptionHandler` do `email-service` traduz as exceções do serviço para o corpo `Error`.
- Migrations em `db/migration-email-service`, a última é `V3__create_email_send_table.sql`
  (a 05-046 propõe a `V4`; a numeração final é a da ordem em que as duas forem implementadas).
- Configuração por ambiente em `config-repo/email-service-<ambiente>.yml`, entregue pelo
  `config-server` (05-042); a suíte de testes troca o `application.yml` e desliga o cliente do
  config-server.
- Domínios de destinatário usados hoje nos testes e no caderno de testes:
  `simulator.amazonses.com`, `example.com`, `exemplo.com`, `acme.com`, `client-a.example`,
  `jogo-acoes.example`. Os outros, fora o `simulator.amazonses.com` (na lista branca desde a
  migration), precisam existir na zona de teste com MX, senão as suítes do `app`, do
  `email-service` e o `blackbox-tests/` passam a receber `422`.
- `app`: `EmailServiceGateway.translate` transforma qualquer 4xx que não seja `401` em
  `EmailServiceRejectedException`. Envios em `LoginController`, `EntryRequestService`,
  `PlayerManagementService` e `CompetitionService` (este num laço, um por participante).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Biblioteca de DNS | `dnsjava` (`dnsjava:dnsjava`), `Lookup` com `SimpleResolver`/`ExtendedResolver`. | implementada | Distingue domínio inexistente (`HOST_NOT_FOUND`), domínio sem o registro (`TYPE_NOT_FOUND`) e falha (`TRY_AGAIN`/`UNRECOVERABLE`), aceita servidor e porta configuráveis e tempo máximo. O provedor JNDI do JDK também consulta, mas mistura esses casos em exceções genéricas. |
| Servidor de DNS | `email.domain-check.dns-servers` (lista `host:porta`). Vazio = resolvedor do sistema (`/etc/resolv.conf`), usado em `staging`/`production`. No `docker`: `dns:53` em `config-repo/email-service-docker.yml`; na suíte de testes, `localhost:1053`. `email.domain-check.timeout` = `2s`. | implementada | A zona de teste só existe no Compose. |
| DNS de teste no Compose | Serviço `dns` novo (`coredns/coredns`, versão fixa), com `docker/dns/Corefile` e `docker/dns/test.zone`, porta `127.0.0.1:1053->53` em UDP e TCP. Zona `test.` com: `with-mx.test` (MX), `only-a.test` (só A), `only-aaaa.test` (só AAAA), `null-mx.test` (MX `0 .` e A), `no-mail.test` (só TXT). `missing.test` não existe (NXDOMAIN). Bloco à parte `unavailable.test` com o plugin `erratic` (`drop 1`), que nunca responde. Zonas dos domínios já usados nos testes (lista acima, sem o `simulator.amazonses.com`), com MX. Nenhum encaminhamento para a internet. | implementada | Respostas determinísticas, sem depender da rede no CI. `.test` é reservado para teste (RFC 2606). O `erratic` dá o caso "DNS sem resposta" sem parar o container. |
| Migration | `V<n>__create_approved_domain_table.sql`: `approved_domain (domain VARCHAR(253) PRIMARY KEY, approved_by VARCHAR(8) NOT NULL` (`MX`, `A`, `AAAA`, `SEED`)`, approved_at TIMESTAMP NOT NULL)`, e `INSERT` de `simulator.amazonses.com` com `approved_by = 'SEED'`. | implementada (o domínio do simulador: decidido, Leila, 2026-10-14) | 253 = maior nome de domínio. Sistema em pré-produção, sem dado a migrar. `SEED` separa o que a migration incluiu do que o DNS aprovou. |
| Onde mora | Pacote novo `domain/` no `email-service`: `RecipientDomainVerifier` (serviço), `DnsMailDomainLookup` (consulta), `ApprovedDomain` + `ApprovedDomainRepository`, `RecipientDomainRejectedException`. `EmailSendService` chama `RecipientDomainVerifier.verify(recipientEmail)` depois de remetente e template. | implementada | Regra de módulos (05-029): o pacote `send` usa o serviço de `domain`, nunca o repositório. |
| Ordem da consulta | MX; se `TYPE_NOT_FOUND`, A, depois AAAA. MX nulo (um único MX, prioridade 0, destino `.`) recusa. `HOST_NOT_FOUND` recusa sem consultar A. Resultado: `Approved(MX|A|AAAA)`, `Rejected(motivo)` ou `Unavailable`. | implementada | RFC 5321 (MX implícito) e RFC 7505 (MX nulo). AAAA decidido (Leila, 2026-10-14). |
| Gravação na lista branca | `INSERT ... ON CONFLICT (domain) DO NOTHING`, numa transação própria (`REQUIRES_NEW`), antes de gravar o envio. | implementada | Dois pedidos simultâneos não geram erro. A aprovação vale mesmo se o envio falhar depois: o DNS respondeu. |
| DNS sem resposta | `Unavailable` → aceita, não grava na lista branca, `WARN` com o domínio. | implementada | Mesma regra da 05-046. |
| Domínio internacionalizado | `java.net.IDN.toASCII(domain, IDN.ALLOW_UNASSIGNED)` e minúsculas, antes da consulta e da gravação. Domínio que o `IDN` rejeita → `422`. | implementada | DNS só conhece a forma ASCII. |
| Contrato HTTP | `POST /emails` ganha `422` ("The recipient's domain does not accept e-mail -- nothing was recorded or queued"), corpo `Error`. A descrição da rota cita a verificação. | implementada | Contract-first. |
| `ApiExceptionHandler` do `email-service` | `RecipientDomainRejectedException` → `422` com a mensagem `Recipient domain does not accept e-mail: <domínio>`. | implementada | O domínio vai na mensagem para quem chamou entender; o endereço completo não vai para o log. |
| `app` | `EmailServiceGateway.translate`: `422` → `RecipientDomainRejectedException` (pacote `email.exception` do `app`, não importada do `email-service`). `ApiExceptionHandler` do `app` → `422`. `docs/openapi.yaml`: `422` nas rotas que pedem e-mail (link de login, entrada em competição, convite). Envios em grupo capturam a exceção por participante e seguem: `CompetitionService.decideInviteEmailTiming` (o participante fica `EMAIL_NOT_SENT`) e, ampliado na implementação, `PlayerManagementService.invitePlayers` e `resendInviteEmails` (o jogador fica com o status que tinha). O reenvio individual (`resendInviteEmail`) deixa a exceção chegar a quem chamou: `422`. O `WARN` sai do `EmailServiceLoggingAspect` (`@AfterThrowing` no `sendEmail` do *gateway*), não de *logger* no serviço. | implementada | Exceção traduzida num ponto só, no *gateway*. Os dois laços do `PlayerManagementService` têm o mesmo problema do convite em grupo: um endereço recusado não pode parar os outros. |
| Testes de outros módulos | Nenhuma mudança no `app` com `email.sender=stub`. `EmailServiceClientIntegrationTest` (contra o `email-service` real) usa domínio com MX na zona de teste. `blackbox-tests/` e o caderno de testes continuam com os domínios da zona. | implementada | Conferir na implementação que nenhum teste usa um domínio fora da zona. |

## Estrutura de módulos/pacotes

- `docker-compose.yml`: serviço `dns`; `email-service` depende dele.
- `docker/dns/Corefile`, `docker/dns/test.zone` (e um arquivo por zona extra, se o CoreDNS
  pedir).
- `config-repo/email-service-docker.yml`: `email.domain-check.dns-servers: dns:53`;
  `email-service-staging.yml`/`-production.yml`: vazio (resolvedor do sistema).
- `email-service/pom.xml`: `dnsjava`.
- `email-service/src/main/resources/db/migration-email-service/V<n>__create_approved_domain_table.sql`.
- `email-service/src/main/java/.../domain/` (novo): ver tabela.
- `send/EmailSendService.java`: chamada ao verificador.
- `common/ApiExceptionHandler.java`: `422`.
- `docs/openapi-email-service.yaml`, `docs/openapi.yaml`.
- `app/.../email/client/EmailServiceGateway.java`, `app/.../email/exception/RecipientDomainRejectedException.java`,
  `app/.../common/ApiExceptionHandler.java`, `app/.../common/logging/EmailServiceLoggingAspect.java`,
  `app/.../competition/CompetitionService.java`, `app/.../competition/PlayerManagementService.java`.
- `README.md`: o serviço `dns` na lista de containers e na subida.
- `.github/workflows/ci.yml`: o serviço `dns` sobe junto com os bancos e o LocalStack.

## Verificações (antes das correções, falhando)

1. `send_email.feature`, `Rule` "The recipient's domain must accept e-mail":
   - `with-mx.test` → `202`, mensagem na fila, `approved_domain` com `MX`;
   - `only-a.test` → `202`, `approved_domain` com `A`;
   - `only-aaaa.test` → `202`, `approved_domain` com `AAAA`;
   - domínio já na lista branca e ausente da zona (o passo grava `ghost.test` em
     `approved_domain` antes) → `202`. Prova que não houve consulta: pela zona, seria `422`;
   - `missing.test`, `no-mail.test`, `null-mx.test` → `422`, nada em `email_send`, nada na fila,
     nada em `approved_domain`;
   - `unavailable.test` → `202` e nada em `approved_domain` (DNS sem resposta);
   - `success@simulator.amazonses.com` → `202`; a linha dele em `approved_domain` é `SEED`, sem
     consulta (o domínio não está na zona de teste).
   Hoje: todos `202` e a tabela não existe.
2. Os cenários que já existem em `send_email.feature` continuam verdes com `example.com` na zona.
3. `DnsMailDomainLookupTest` (contra o CoreDNS do Compose): um teste por resultado (`MX`, `A`,
   `AAAA`, MX nulo, inexistente, sem registro, sem resposta dentro do tempo máximo).
4. `RecipientDomainVerifierTest`: dez chamadas simultâneas para `with-mx.test` → uma linha em
   `approved_domain`, nenhuma exceção; domínio internacionalizado gravado na forma ASCII; rótulo
   que o `IDN` rejeita → recusado.
5. `app`: teste do *gateway* contra o `email-service` real com destinatário `x@missing.test` →
   `RecipientDomainRejectedException`. `RecipientDomainRejectionIntegrationTest`, também contra
   o `email-service` real: `POST /api/login-requests` de um jogador cadastrado em `missing.test`
   → `422`; envio dos convites agora, convite em grupo e reenvio em grupo com um endereço
   recusado → só ele fica sem envio; reenvio individual → a exceção chega a quem chamou.
6. `SchemaLayoutTest` continua verde com a migration nova.

## Riscos e trade-offs

- **Aceitar com DNS fora do ar** deixa passar endereços ruins enquanto ele estiver fora.
- **Lista branca sem vencimento**: um domínio que deixa de receber e-mail continua aprovado.
- **Tempo do primeiro envio para um domínio novo**: até 3 consultas (MX, A, AAAA), cada uma com
  o tempo máximo, no pior caso.
- **Zona de teste a manter**: o CoreDNS recusa (`REFUSED`) qualquer domínio fora das zonas, o
  que conta como DNS sem resposta: o envio segue, sem entrar na lista branca. Um teste novo que
  precisa de um domínio aprovado ou recusado tem que incluí-lo na zona (`README.md`).
- **Exceções sem teste possível**: nenhuma prevista; o "DNS sem resposta" é exercitado pelo
  `erratic`.
