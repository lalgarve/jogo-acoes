# Tasks: Visualizador de logs no ambiente Docker Compose

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Escritas supondo o Dozzle,
a recomendação do `plan.md`; se a decisão for outra, estas tarefas são reescritas.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Verificação primeiro: rodar `docker compose up -d --wait log-viewer` e registrar que falha (serviço inexistente) | — | | — |
| T002 | Adicionar o serviço `log-viewer` ao `docker-compose.yml`: imagem `amir20/dozzle` com versão maior fixada, `/var/run/docker.sock:/var/run/docker.sock:ro`, porta `8083:8080`, healthcheck com o comando `healthcheck` do Dozzle e comentário no mesmo estilo dos de `adminer`/`ses-viewer` | T001 | | — |
| T003 | Repetir `docker compose up -d --wait log-viewer` e registrar que passa (healthcheck verde); conferir com `docker stats --no-stream` a memória em repouso (RNF1) | T002 | | — |
| T004 | Conferir que a CI não sobe o serviço novo (`.github/workflows/ci.yml` só nomeia `db`, `db-email-service`, `localstack`, `email-service`) | T002 | [P] | — |
| T005 | Atualizar `docs/disciplina/caderno-de-testes.md`, tabela "Ferramentas": "Logs da aplicação" passa a apontar `http://localhost:8083`, com `docker compose logs -f app` como alternativa | T002 | [P] | — |
| T006 | Verificação manual (Leila registra): com o ambiente de pé, executar o Roteiro 1 do caderno (login do administrador) e conferir no visualizador as linhas dos aspectos de `app` e `email-service` juntas, em ordem de tempo, e a busca pelo e-mail do administrador (RF2–RF4), no Windows | T003, T005 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Critério de aceite de exceções: não se aplica, nenhuma classe nem branch de exceção é
  criado (`plan.md`, "Riscos e trade-offs").
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").
