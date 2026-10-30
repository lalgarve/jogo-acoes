# Plan: pedido de link de login ou de entrada repetido

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- **Depende da spec 05-056** (Issue #135, branch `claude/project-thread-4wissn`, ainda não
  mesclada em `development`). Ela move o pedido de link de login de
  `LoginController.requestLoginLink` para `LoginLinkRequestService.requestLoginLink`, num
  `@Transactional` que desfaz tudo quando o envio falha. Este plano parte desse serviço.
- Módulo `link` (`LinkService`, `LinkRecord`, `LinkRecordRepository`): não conhece o que cada
  link significa. `LinkRecord` guarda `serviceKey` (`login` ou o `CompetitionLinkHandler.KEY`),
  `userId` (nulo para quem ainda não tem cadastro), `email`, `extraJson` (opaco: `returnTo` no
  login, `participationId` na entrada), `emailSentAt`, `expiresAt` (7 dias,
  `LINK_VALIDITY_DAYS`), `usedAt` e `invalidatedAt`. O token é um UUID guardado em claro, então
  reenviar o mesmo link só exige achar o registro.
- `LinkService.create` sempre cria um registro novo. `invalidateActiveLinksFor(userId)` invalida
  todos os links não usados do usuário, de qualquer `serviceKey`.
- Pedido de entrada (`EntryRequestService.requestEntry`) chama `linkService.create` a cada
  pedido, sem invalidar nada. Depois da spec 05-057, ele sempre reaproveita a mesma participação
  para o mesmo e-mail, então o `participationId` do `extra` identifica o link de entrada.
- `docs/openapi.yaml` diz, em `consumeLoginLink`, "a fresh link for the same player invalidates
  any previous unused link". Deixa de ser verdade quando o link é reenviado.
- Testes que tocam o fluxo: `login.feature` / `LoginSteps` (cenário "Requesting a new login link
  invalidates the previous one"), `request_competition_entry.feature` /
  `RequestCompetitionEntrySteps`, `LinkServiceTest`, `AuditLoggingIntegrationTest`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde mora a regra "reenviar o link ativo" | Método novo em `LinkService`: `findActive(String serviceKey, LinkPayload payload)` devolve o `LinkCreationResult` do link ativo com o mesmo `serviceKey`, o mesmo e-mail (sem diferenciar maiúsculas), o mesmo `userId` e o mesmo `extra`, se houver. Quem pede o link decide o que fazer com a resposta. | proposta | O módulo `link` continua sem saber o que é `returnTo` ou `participationId`: compara o `extra` inteiro, como mapa. A decisão de invalidar (login) ou não (entrada) continua com cada fluxo, como hoje. |
| Como comparar o `extra` | Ler os candidatos com `findByServiceKeyAndEmailIgnoreCaseAndUsedAtIsNullAndInvalidatedAtIsNullAndExpiresAtAfter` e comparar o `extra` desserializado (`Map.equals`) em Java; se houver mais de um, o mais recente (`id` maior). | proposta | O JSON gravado não tem ordem de chaves garantida, então comparar texto falharia. Os candidatos são poucos (um e-mail, links ativos). |
| Reenvio: o que muda no registro | Só `emailSentAt = agora`. Token, `expiresAt` e o resto ficam iguais. Método `LinkService.markResent(Long linkId)`. | proposta | `spec.md`: mesma validade, sem estender. `emailSentAt` passa a dizer quando saiu o último e-mail daquele link. |
| Login | Em `LoginLinkRequestService.requestLoginLink`: `findActive(LoginLinkHandler.KEY, payload)`. Achou: `markResent`, auditoria e envio do mesmo link. Não achou: o fluxo atual (invalidar os links ativos do usuário, criar, auditar, enviar). | proposta | Mesmo `returnTo` faz parte do `extra`, então um `returnTo` diferente não acha o link e cai no fluxo de link novo, como a `spec.md` pede. |
| Entrada | Em `requestEntry`: `findActive(CompetitionLinkHandler.KEY, payload)` com o `participationId` no `extra`. Achou: reenvia. Não achou: cria. Nunca invalida. | proposta | Vale para quem tem e quem não tem cadastro: a chave é e-mail + participação, não `userId`. Links de outras competições têm outro `participationId` e não são afetados. |
| Convites (`PlayerManagementService`) | Não mudam. | proposta | A `spec.md` só cobre login e entrada. O reenvio de convite pelo administrador já é explícito (`resendInviteEmail(s)`). |
| Auditoria | `LOGIN_LINK_ISSUED` com o id do link a cada e-mail; no reenvio, a mensagem termina em `(resent)`, por exemplo `Login link issued to x@y (resent)`. | proposta | `spec.md`: uma entrada por e-mail, com o mesmo id. O sufixo deixa a diferença visível sem tipo de log novo. |
| Falha no reenvio | Nada a fazer além da 05-056: o envio está dentro do `@Transactional` do serviço, e uma falha desfaz o `markResent` e a auditoria. O link continua ativo como estava. | resolvida | Já é o comportamento que a 05-056 cria para o login. `requestEntry` já envia dentro do próprio `@Transactional`. |
| Contrato | `docs/openapi.yaml`: descrições de `requestLoginLink` e `consumeLoginLink` dizem que um novo pedido reenvia o link ativo com o mesmo `returnTo` e que só um link novo invalida os anteriores; `requestOrConfirmEntry` diz que o pedido repetido reenvia o mesmo link. Sem mudança de esquema. | proposta | Contract-first. As respostas não mudam (`202`, `503`). |
| Exceções novas | Nenhuma. | resolvida | O link invalidado continua respondendo com `LoginLinkInvalidException`, já testado. |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

- `link/LinkRecordRepository.java`: consulta dos candidatos ativos.
- `link/LinkService.java`: `findActive` e `markResent`.
- `loginsession/LoginLinkRequestService.java` (criado pela 05-056): ramo do reenvio.
- `competition/EntryRequestService.java`: ramo do reenvio em `requestEntry`.
- `docs/openapi.yaml`: descrições.
- Testes: `LinkServiceTest` (casos de `findActive`: mesmo `extra`, `extra` diferente, link usado,
  invalidado, expirado, e-mail com outra grafia); `login.feature` e `LoginSteps` (os dois
  cenários da `spec.md` substituem "Requesting a new login link invalidates the previous one");
  cenário novo em `request_competition_entry.feature`; um cenário em `redirect_after_login.feature`
  ou em `LoginSteps` para o `returnTo` diferente (link novo, o anterior invalidado).

Nenhuma migration. O `ArchitectureTest` não é afetado: `loginsession` e `competition` já
dependem de `link`.

## Verificações (antes da correção, falhando)

1. Os dois cenários novos de login falham hoje: o segundo pedido manda um token diferente.
2. O cenário novo de entrada falha hoje no passo `And both e-mails contain the same link`
   (acrescentado à `spec.md` junto com este plano): hoje cada pedido gera um link diferente,
   mesmo que os dois funcionem.
3. Depois da implementação, verdes: `login.feature`, `redirect_after_login.feature`,
   `request_competition_entry.feature`, `AuditLoggingIntegrationTest` e a suíte
   `blackbox-tests/` (cenários que pedem link mais de uma vez).

## Riscos e trade-offs

- **Pedidos simultâneos.** Dois primeiros pedidos ao mesmo tempo não acham link ativo e criam
  dois. No login, o segundo invalida os links que enxerga, mas não o do primeiro, que ainda não
  foi gravado: os dois links ficam ativos e os dois funcionam. É o mesmo resultado que a
  `spec.md` quer (qualquer e-mail funciona), então não vale uma trava.
- **Link reenviado perto de expirar.** Um link pedido há quase 7 dias é reenviado com a validade
  que sobra. A `spec.md` decidiu não estender; quem receber um link expirado pede outro e, sem
  link ativo, ganha um novo.
- **Token em claro.** O reenvio depende de o token estar guardado em claro, como já está. Se um
  dia passar a ser guardado só como hash (Issue #117, item 6), o reenvio terá de mudar.

## Decisões em aberto

Nenhuma. A `spec.md` deixa em aberto juntar a implementação com a 05-056; isto é decisão de
planejamento, e o plano só exige que a 05-056 esteja mesclada antes.
