# Tasks: Container de UI web para o PostgreSQL

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). A decisão (Adminer, porta,
sem credenciais pré-preenchidas) já está resolvida em `plan.md` — mudança pequena o bastante
para não precisar de mais que duas tarefas.

Ambas as tarefas são acompanhadas como checklist na Issue-épico
[#63](https://github.com/lalgarve/jogo-acoes/issues/63).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Adicionar serviço `adminer` ao `docker-compose.yml` (imagem `adminer`, `depends_on: db`, porta `8081:8080`) | — | | #63 |
| T002 | `docker-compose up`, acessar `http://localhost:8081`, logar manualmente com host `db`/usuário `jogo_acoes_admin`/senha `jogo_acoes_admin`/banco `jogo_acoes`, rodar um `SELECT` simples e confirmar que retorna dado — sem mudança de código da aplicação | T001 | | #63 |

- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`docs`, já que é só infraestrutura de desenvolvimento local).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
