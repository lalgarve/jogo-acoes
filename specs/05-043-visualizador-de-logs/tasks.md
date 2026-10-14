# Tasks: Visualizador de logs no ambiente Docker Compose

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Ferramenta: Dozzle,
decidida pela Leila em 2026-10-13 (`plan.md`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| ~~T001~~ | Verificação primeiro: rodar `docker compose up -d --wait log-viewer` e registrar que falha (serviço inexistente) | — | | #166 |
| ~~T002~~ | Adicionar o serviço `log-viewer` ao `docker-compose.yml`: imagem `amir20/dozzle` com versão maior fixada, `/var/run/docker.sock:/var/run/docker.sock:ro`, porta `8083:8080`, healthcheck com o comando `healthcheck` do Dozzle e comentário no mesmo estilo dos de `adminer`/`ses-viewer` | T001 | | #166 |
| ~~T003~~ | Repetir `docker compose up -d --wait log-viewer` e registrar que passa (healthcheck verde); conferir com `docker stats --no-stream` a memória em repouso (RNF1) | T002 | | #166 |
| ~~T004~~ | Conferir que a CI não sobe o serviço novo (`.github/workflows/ci.yml` só nomeia `db`, `db-email-service`, `localstack`, `email-service`) | T002 | [P] | #166 |
| ~~T005~~ | Atualizar `docs/disciplina/caderno-de-testes.md`, tabela "Ferramentas": "Logs da aplicação" passa a apontar `http://localhost:8083`, com `docker compose logs -f app` como alternativa | T002 | [P] | #166 |
| ~~T006~~ | Verificação manual (Leila registra): com o ambiente de pé, executar o Roteiro 1 do caderno (login do administrador) e conferir no visualizador as linhas dos aspectos de `app` e `email-service` juntas, em ordem de tempo, e a busca pelo e-mail do administrador (RF2–RF4), no Windows | T003, T005 | | #166 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Critério de aceite de exceções: não se aplica, nenhuma classe nem branch de exceção é
  criado (`plan.md`, "Riscos e trade-offs").
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Registro da verificação (2026-10-13, ambiente da Claude, Docker 29.8.2)

- **T001**: `docker compose up -d --wait log-viewer` → `no such service: log-viewer`, saída 1.
- **T002**: serviço `log-viewer` no `docker-compose.yml`, imagem `amir20/dozzle:v8` (v8.14.12 na
  data), `DOZZLE_NO_ANALYTICS=true` para o Dozzle não mandar estatísticas de uso para fora,
  healthcheck `/dozzle healthcheck`. `docker compose config -q` sem erros.
- **T003**: `docker compose up -d --wait log-viewer` → container `Healthy`, saída 0;
  `docker stats --no-stream` → 7,8 MiB em repouso; `GET http://localhost:8083/` → 200. Com um
  container de fora do Compose escrevendo uma linha por segundo, a API do Dozzle listou esse
  container e devolveu as linhas dele, o que vale também para os containers da Lambda que o
  LocalStack cria. `app` e `email-service` não subiram neste ambiente (o Maven dentro dos
  containers falha com PKIX aqui), então a conferência com eles fica para a T006.
- **T004**: `.github/workflows/ci.yml` só executa `docker compose up -d --wait` com `db`,
  `db-email-service`, `localstack` e `email-service`, e nenhum deles depende do `log-viewer`.
- **T005**: caderno de testes atualizado: `log-viewer` no `docker compose up` das ferramentas,
  linha "Visualizador de logs" na tabela (com `docker compose logs -f app email-service` como
  alternativa) e nota sobre o que o Dozzle não guarda (logs somem depois de
  `docker compose down`; containers da Lambda somem quando o LocalStack os recicla).

## Registro da verificação manual (T006)

- **T006**: feita pela Leila, que informou em 2026-10-14 ter concluído todos os testes da
  verificação manual. A implementação (T001–T005) foi mesclada na PR #163.
