# Tasks: Gestão de sessões ativas (listar e revogar)

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (coluna `http_session_id`, mecanismo de revogação via Spring Session JDBC,
nome dos endpoints) já estão resolvidas em `plan.md` — esta lista só quebra a implementação em
passos.

**Pré-requisito de ordem (não bloqueio técnico rígido)**: faz mais sentido depois da spec
[05-009](../05-009-identificacao-dispositivo-client-hints/tasks.md) — sem ela, a listagem
funciona igual, só com rótulo de dispositivo menos útil (`User-Agent` cru em vez do rótulo
formatado). O cenário de T001 que usa dois rótulos distintos pra validar a 05-009 depende dela
de fato, os demais não.

**Ordem: TDD + API-first.** Os passos Cucumber vêm primeiro, contra o comportamento esperado
descrito em `plan.md` — corpo/resposta em JSON cru (RestAssured, sem depender de classes
geradas, que ainda não existem) — e devem rodar e falhar de forma previsível (404 — os
caminhos ainda não existem) antes de qualquer outra tarefa. O contrato OpenAPI vem em seguida,
formalizando exatamente o que os passos já esperavam, e só depois disso a implementação.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#60](https://github.com/lalgarve/jogo-acoes/issues/60) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Passos Cucumber para `manage_active_sessions.feature` — cenários: (a) listar sessões ativas mostra id/rótulo/data/flag "é a atual"; (b) logar simulando dois dispositivos com Client Hints/User-Agent diferentes e confirmar que a listagem mostra dois rótulos distintos e reconhecíveis (valida a spec 05-009 de ponta a ponta); (c) revogar uma sessão de outro dispositivo simulado e confirmar que uma chamada autenticada seguinte com aquele cookie/sessão devolve 401; (d) revogar a própria sessão atual também desloga; (e) revogar sessão inexistente ou de outro usuário devolve 404. Chamada direta a `GET /sessions`/`DELETE /sessions/{id}` em JSON cru; rodar e confirmar que falha agora (caminhos ainda não existem) | — | [P] | #60 |
| T002 | Contrato: em `docs/openapi.yaml`, adicionar `GET /sessions` (retorna array de `Session`) e `DELETE /sessions/{sessionId}` (`204`/`404`); schema novo `Session` (`id`, `deviceLabel`, `createdAt`, `current`); `x-roles: [PLAYER, ADMINISTRATOR]` nas duas (qualquer jogador autenticado, mesma convenção da spec 05-004) | T001 | | #60 |
| T003 | Migration Flyway nova: coluna `http_session_id` (`NOT NULL`) em `login_session`; atualizar `docs/diagrams/der.md` com a coluna nova e a nota de que ela liga o registro à sessão HTTP real (Spring Session JDBC) | T002 | | #60 |
| T004 | `link/LoginSession.java` ganha o campo `httpSessionId`; `LoginLinkSessionService.establish()` passa a gravá-lo a partir de `request.getSession().getId()`, chamado depois de `securityContextRepository.saveContext(...)` | T003 | | #60 |
| T005 | `link/LoginSessionRepository.java` ganha `findByIdAndUserId(Long id, Long userId)` — usado pela revogação para já filtrar por dono numa query só | T002 | [P] | #60 |
| T006 | Criar `login/SessionsController.java` (implementa a interface `SessionsApi` gerada) + lógica de listagem: mapeia `LoginSession` ativa do usuário autenticado para `Session`, calculando `current` pela comparação `httpSessionId` == `request.getSession().getId()` | T004, T005 | | #60 |
| T007 | Lógica de revogação no mesmo controller (ou serviço dedicado, se ficar grande): `findByIdAndUserId` (404 se ausente), injeta `SessionRepository<? extends Session>` (Spring Session JDBC) e chama `.deleteById(httpSessionId)`, marca `endedAt` no próprio registro | T006 | | #60 |
| T008 | Rodar `manage_active_sessions.feature` de novo — confirmar verde, incluindo o cenário que valida a spec 05-009 | T001, T007 | | #60 |
| T009 | Rodar a suíte completa (`mvn test`) — confirmar verde, nenhum cenário/teste existente alterado além dos novos | T008 | | #60 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
