# Tasks: Refatorar pacote base `io.deployo` → `dev.leilaalgarve`

Quebra `spec.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Esta spec não tem
`plan.md` — as decisões técnicas (`groupId` muda junto, `email-lambda` também é renomeado)
já foram resolvidas e registradas na seção "Decisões em aberto" do próprio `spec.md`, sem
justificar um `plan.md` separado para uma mudança puramente mecânica.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Confirmar o pacote Java atual do módulo `email-lambda` (grep `package`/imports) antes de aplicar a renomeação | — | [P] | — |
| T002 | Mover `app/src/main/java` e `app/src/test/java` de `io/deployo` para `dev/leilaalgarve`, ajustando a declaração `package` em cada arquivo | — | [P] | — |
| T003 | Atualizar todos os imports `io.deployo.*` → `dev.leilaalgarve.*` dentro de `app/` (main e test) | T002 | | — |
| T004 | Atualizar javadoc/comentários/strings de log que citem o pacote antigo por nome dentro de `app/` | T002 | | — |
| T005 | Atualizar `apiPackage`/`modelPackage` do `openapi-generator-maven-plugin` no `pom.xml` de `app/`, se configurado com `io.deployo` | — | [P] | — |
| T006 | Atualizar `groupId` do `pom.xml` de `app/` de `io.deployo` para `dev.leilaalgarve` | — | [P] | — |
| T007 | Conferir `mainClass` explícito do `spring-boot-maven-plugin` em `app/pom.xml` e qualquer classe referenciada por nome totalmente qualificado em `Dockerfile`/`docker-compose.yml`/scripts de `app/`; atualizar se houver | — | [P] | — |
| T008 | Mover o código-fonte de `email-lambda` do pacote confirmado em T001 para `dev.leilaalgarve`, ajustando a declaração `package` | T001 | | — |
| T009 | Atualizar imports/referências textuais ao pacote antigo dentro de `email-lambda/` | T008 | | — |
| T010 | Atualizar `groupId` do `pom.xml` de `email-lambda` de `io.deployo` para `dev.leilaalgarve` | T001 | [P] | — |
| T011 | Conferir e atualizar coordenadas cruzadas entre `app`/`email-lambda` no `pom.xml` pai do reator, se houver dependência declarada entre os módulos por `groupId`/`artifactId` | T006, T010 | | — |
| T012 | Grep final por `io.deployo` em todo o repositório (fora de diretórios de build gerados) para achar referências residuais não cobertas pelas tarefas acima, e corrigir | T003, T004, T005, T007, T009 | | — |
| T013 | Rodar a suíte completa (`.feature` + testes unitários/integração) de `app` e `email-lambda` e confirmar 100% verde, sem nenhuma asserção alterada — só pacote muda | T011, T012 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`refactor`). Nenhuma Issue foi criada ainda para esta spec (`spec.md` ainda
  lista `Issue: —`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
