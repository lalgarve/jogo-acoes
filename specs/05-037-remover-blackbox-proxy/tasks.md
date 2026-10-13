# Tasks: Remover o módulo `blackbox-proxy/`

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Verificação primeiro.** Esta spec não cria comportamento testável por JUnit/ArchUnit: o que
prova a remoção é a busca por `blackbox-proxy` e o build. T001 roda essa busca antes de qualquer
mudança e registra as ocorrências (a verificação "falhando"); T010 roda a mesma busca no fim.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Rodar `grep -rn "blackbox-proxy" --exclude-dir=.git .` e registrar aqui as ocorrências fora dos registros históricos (lista esperada: tabela de "Contexto técnico" do `plan.md`). É a verificação de C1 e C3 antes da remoção | — | | #123 |
| ~~T002~~ | Apagar `blackbox-proxy/` inteiro e `scripts/blackbox-proxy.sh` | T001 | | #123 |
| ~~T003~~ | `pom.xml` raiz: tirar `<module>blackbox-proxy</module>` e reescrever o `<description>` para três módulos | T002 | | #123 |
| ~~T004~~ | `Dockerfile` e `email-service/Dockerfile`: tirar o `COPY blackbox-proxy/pom.xml` e o nome do módulo nos comentários sobre o reator | T003 | [P] | #123 |
| ~~T005~~ | `.gitattributes`: comentário dos scripts `*.sh` sem a menção ao proxy | T002 | [P] | #123 |
| ~~T006~~ | `README.md`: tirar a linha do proxy da tabela "Módulos", o `mvn -pl blackbox-proxy -am verify` e o bloco "Alternativa disponível, deixada de lado por enquanto" (com o script e o `curl` para `localhost:8090`); "builda os quatro" vira "os três"; manter o fluxo do `curl` do Swagger UI | T002 | [P] | #123 |
| ~~T007~~ | `CLAUDE.md` e `memory/constitution.md`: tirar o proxy da tabela de módulos do `CLAUDE.md` e da lista da baseline Java; na regra "Código de teste/dev nunca dentro da aplicação", usar `blackbox-tests/` como exemplo (constitution e `CLAUDE.md`) e tirar o parêntese "(como `blackbox-proxy/` faz)" | T002 | [P] | #123 |
| ~~T008~~ | Spec 05-020: status "removido — ver 'Por que este módulo foi removido' no final do arquivo" e a seção no fim do `spec.md`, com o motivo (decisão de usar o `curl`), o commit da implementação (`dbbd678`) e o link para esta spec. `plan.md` e `tasks.md` dela ficam como estão | — | [P] | #123 |
| ~~T009~~ | Spec 05-033: nota no `spec.md`, junto do requisito da baseline, dizendo que `blackbox-proxy` saiu do projeto com esta spec. `plan.md` e `tasks.md` dela ficam como estão | — | [P] | #123 |
| ~~T010~~ | Repetir a busca de T001: só sobram ocorrências nos registros históricos (`spec.md`, "Fora de escopo"), na spec 05-020 e nesta spec (C1, C3, C4, C5). Registrar | T002–T009 | | #123 |
| ~~T011~~ | Com a infraestrutura do Compose de pé, rodar `mvn -B verify` na raiz e confirmar que o reator builda só `app`, `email-lambda` e `email-service` (C1). Registrar | T003 | | #123 |
| ~~T012~~ | `docker compose build app email-service`: as duas imagens buildam (C2). Registrar | T004 | | #123 |
| T013 | ~~Critério de aceite de exceções~~ — não se aplica: esta spec só remove código e não cria nem altera classe ou branch de exceção | — | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Todas as tasks cabem numa PR só (`plan.md`, "Uma PR ou mais?"), que fecha a Issue #123. A
  Issue leva o label `iteration-5`, além do label de tipo (`chore`).
- T011 e T012 dependem de Docker.
## Registro da verificação (2026-10-08)

- **T001** (antes da remoção, no `master` `d8429d7`): fora dos registros históricos e desta
  spec, `blackbox-proxy` aparecia em `blackbox-proxy/` (o módulo), `scripts/blackbox-proxy.sh`,
  `pom.xml` (2), `Dockerfile` (2), `email-service/Dockerfile` (2), `.gitattributes` (1),
  `README.md` (4), `CLAUDE.md` (2), `memory/constitution.md` (3), spec 05-020 (status
  "implementado") e `specs/05-033-politica-baseline-java-lts/spec.md` (requisito da baseline).
  Bate com a tabela de "Contexto técnico" do `plan.md`.
- **T010**: `grep -rln "blackbox-proxy" --exclude-dir=.git .` só acha arquivos em `specs/`:
  05-020 (`spec.md`, `plan.md`, `tasks.md`), 05-022, 05-023, 05-024, 05-025 (`plan.md`,
  `tasks.md`), 05-027, 05-033 (`spec.md` com a nota, `plan.md`, `tasks.md`) e esta spec.
- **T011**: `SPRING_PROFILES_ACTIVE=docker mvn -B verify` na raiz listou no reator só
  `jogo-acoes` (`app`), `email-lambda`, `email-service` e o agregador. `email-lambda` e
  `email-service` passaram (`mvn -B verify -pl email-lambda,email-service`: 40 testes no
  `email-service`, 0 falhas, 2 cenários pulados pelo Cucumber). No `app`, 210 de 216
  passaram; as 6 falhas são todas do `EmailServiceClientIntegrationTest` e vêm do ambiente da
  sessão, não desta mudança, que não toca em `app/`: o LocalStack não conseguiu criar o
  executor do Lambda ("Failed to create the runtime executor for the function EmailLambda"),
  então nenhum e-mail chegou ao SES (5 casos), e a primeira chamada de preview levou 0,32 s,
  acima do limite do teste de "sem retry" (1 caso). O CI da PR roda a suíte do `app` num
  ambiente em que o Lambda sobe.
- **T012**: as imagens de `app` e `email-service` buildaram a partir dos `Dockerfile`s
  alterados. Nesta sessão, o build dentro do container só alcança o Maven Central e o GitHub
  pelo proxy de saída, então rodou com uma cópia de cada `Dockerfile` que acrescenta só o
  certificado e a configuração desse proxy no estágio de build. Fora isso, as instruções são as
  mesmas do repositório.

- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.
