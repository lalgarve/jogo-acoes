# Tasks: SecurityConfig modular por módulo

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões de
arquitetura referenciadas abaixo (assinatura de `SecurityConfigContributor`, onde vive, mecanismo
de agregação, desenho dos dois testes) já estão resolvidas em `plan.md` — esta lista só quebra a
implementação em passos.

**Ordem: testes primeiro, sem passo de contrato** — mesmo princípio de TDD já usado nas specs
05-004/05-005, mas esta spec não muda nenhuma rota nem `docs/openapi.yaml` (é refactor interno
puro), então não há passo de contrato OpenAPI entre os testes e a implementação. Os dois testes
estruturais (`ArchitectureTest`, `RouteOwnershipTest`) vêm antes da reorganização de código que
eles verificam — um dos dois nasce vermelho (nada implementa `SecurityConfigContributor` ainda),
o outro já nasce verde (nenhuma rota muda nesta spec, ele serve de trava contra regressão
futura, não de ciclo vermelho/verde).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Adicionar dependência `com.tngtech.archunit:archunit-junit5` (escopo `test`, versão mais recente estável) ao `app/pom.xml` | — | [P] | #<n> |
| T002 | Criar a interface `SecurityConfigContributor` em `login/` — só a interface (`contribute(registry)`), nenhuma implementação ainda; necessária pra T003 poder referenciar o tipo | — | [P] | #<n> |
| T003 | Escrever `common/ArchitectureTest.java` (regra ArchUnit: toda classe `@RestController` está num pacote-base que também contém uma classe implementando `SecurityConfigContributor`) e rodar — confirmar que falha (vermelho): `login`/`competition` têm `@RestController` mas nenhuma classe implementa `SecurityConfigContributor` ainda | T001, T002 | | #<n> |
| T004 | Escrever `common/RouteOwnershipTest.java` (via `RequestMappingHandlerMapping`: nenhuma rota mapeia `/`; cada primeiro segmento de path pertence a um único módulo) e rodar — já deve passar (verde) sem nenhuma mudança de rota | — | [P] | #<n> |
| T005 | Criar `login/LoginSecurityConfigContributor.java` (`@Component`), movendo para lá os matchers `permitAll` de `/login-requests`, `/login-links/**` que hoje estão em `SecurityConfig` | T002 | | #<n> |
| T006 | Criar `competition/CompetitionSecurityConfigContributor.java` (`@Component`), movendo para lá os matchers de `/competitions*` (públicos e administrativos) que hoje estão em `SecurityConfig` | T002 | | #<n> |
| T007 | Atualizar `SecurityConfig.securityFilterChain`: injetar `List<SecurityConfigContributor>`, iterar chamando `contribute(registry)` de cada um, remover os `requestMatchers` de módulo que ficaram inline, manter `.anyRequest().authenticated()` por último e toda a config transversal (csrf/formLogin/httpBasic/`SecurityContextRepository`/`exceptionHandling`) como está | T005, T006 | | #<n> |
| T008 | Rodar `ArchitectureTest` de novo — confirmar verde | T003, T007 | | #<n> |
| T009 | Rodar a suíte completa (`mvn test`) — confirmar que a contagem/resultado de comportamento não muda em relação ao estado antes desta spec (mesmos 119 cenários/testes existentes, mais os dois testes novos, nenhuma rota/regra de acesso diferente) | T004, T008 | | #<n> |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`refactor`/`test`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
