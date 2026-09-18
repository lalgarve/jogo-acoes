# Tasks: Logging estruturado via aspectos (AOP), suprimido em produção

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (pointcuts, truncamento, nível DEBUG, supressão em produção) já estão
resolvidas em `plan.md` — esta lista só quebra a implementação em passos.

**Sem Cucumber/contrato OpenAPI** — esta spec é infraestrutura transversal (log), sem `.feature`
nem rota nova (mesmo padrão das specs 05-006/05-007). O critério de aceite já definido em
`spec.md` são testes dedicados: `LogFormatterTest` (função pura) e um teste de integração por
aspecto usando `OutputCaptureExtension`, que sobem contexto real e confirmam o que aparece no
console.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#61](https://github.com/lalgarve/jogo-acoes/issues/61) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | ~~Adicionar dependência `spring-boot-starter-aop` ao `app/pom.xml`~~ — no-op: o artefato não existe mais gerenciado a partir do Spring Boot 4.x, e `aspectjweaver`/`AopAutoConfiguration` já chegam de graça via `spring-boot-starter-data-jpa` (ver `plan.md`) | — | [P] | #61 |
| T002 | Criar `common/logging/LogFormatter.java` — `summarize(Object value)`: `Collection`/`Map` com mais de um item vira `primeiro+[N]`; 0/1 item ou não-coleção serializa via Jackson (`ObjectMapper`); erro de serialização (ex. referência cíclica) cai num `toString()` simples | — | [P] | #61 |
| T003 | `LogFormatterTest` dedicado: lista de 10 e-mails vira `joao@exemplo.com+[9]`; mapa com mais de um item trunca igual; coleção com 0/1 item aparece completa; objeto comum serializado por inteiro; objeto com referência cíclica cai no fallback `toString()` sem lançar exceção | T002 | [P] | #61 |
| T004 | Criar `common/logging/ControllerLoggingAspect.java` — pointcut `@within(org.springframework.web.bind.annotation.RestController)`, loga em DEBUG (guardado por `logger.isDebugEnabled()`) entrada (método + argumentos via `LogFormatter`) e saída (retorno ou exceção) | T001, T002 | | #61 |
| T005 | Criar `common/logging/RepositoryLoggingAspect.java` — pointcut `execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))` combinado com `within(dev.leilaalgarve.jogoacoes..*)`, mesmo formato de log (entrada/saída, `LogFormatter`, guarda de nível) | T001, T002 | [P] | #61 |
| T006 | Criar `common/logging/QueueLoggingAspect.java` — pointcut `execution(* dev.leilaalgarve.jogoacoes.email.SqsEmailSender.send(..))`, loga a mensagem enviada (via `LogFormatter`) | T001, T002 | [P] | #61 |
| T007 | Teste de integração leve pro aspecto de controllers (`OutputCaptureExtension`): chama um endpoint real (ex. `GET /competitions/public`) e confirma que o log de saída contém entrada e saída esperadas, incluindo um caso com lista truncada | T004 | | #61 |
| T008 | Teste de integração leve pro aspecto de repositórios (`OutputCaptureExtension`): uma chamada de repositório real (ex. via um `@DataJpaTest` ou o contexto completo) aparece logada | T005 | [P] | #61 |
| T009 | Teste de integração leve pro aspecto de fila, seguindo o mesmo padrão condicional de `SqsEmailSenderDockerIntegrationTest` (só roda com LocalStack disponível, perfil `docker`) — confirma que o envio via `SqsEmailSender` aparece logado | T006 | [P] | #61 |
| T010 | Adicionar `logging.level.dev.leilaalgarve.jogoacoes: DEBUG` em `app/src/main/resources/application.yml` **e também em `app/src/test/resources/application.yml`** — é esse nível que faz os três aspectos aparecerem no console; sem ele, T007/T008/T009 continuam vermelhos mesmo com os aspectos implementados. Achado na implementação: `src/test/resources/application.yml` substitui (não empilha sobre) o `application.yml` principal durante os testes (mesmo padrão já documentado ali pra `spring.cloud.aws.region`/`email.queue-name`) — confirmado empiricamente rodando T007/T008 depois de mexer só no arquivo principal, que continuaram vermelhos até repetir a chave no arquivo de teste também | T007, T008, T009 | | #61 |
| T011 | Adicionar `logging.level.dev.leilaalgarve.jogoacoes: INFO` em `app/src/main/resources/application-production.yml`; teste dedicado com `@ActiveProfiles` incluindo `production` + `OutputCaptureExtension` confirmando que nenhum dos três aspectos aparece no console nesse perfil | T010 | | #61 |
| T012 | Rodar a suíte completa (`mvn test`) — confirmar verde, nenhum teste existente alterado além dos novos | T003, T011 | | #61 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
