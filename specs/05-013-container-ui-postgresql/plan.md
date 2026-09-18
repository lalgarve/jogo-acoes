# Plan: Container de UI web para o PostgreSQL

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`docker-compose.yml` já roda `db` (Postgres 16) e `localstack`; nenhuma UI de banco existe
ainda.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| pgAdmin ou Adminer | **Adminer** (`adminer` na Docker Hub) — imagem única, sem estado próprio pra configurar (sem volume, sem variáveis de usuário/senha do próprio painel), login feito na tela inicial digitando host/usuário/senha do Postgres a cada acesso. | resolvida | Mais simples de subir e mais leve que pgAdmin (que exige `PGADMIN_DEFAULT_EMAIL`/`PASSWORD` e volume próprio pra persistir configuração) — objetivo aqui é só "rodar um `SELECT` visualmente", não um cliente de administração completo. |
| Porta | `8081:8080` (porta padrão do Adminer é 8080 dentro do container; `8080` do host já é `app`). | resolvida | Evita conflito com a porta já usada por `app`. |
| Credenciais pré-preenchidas | Não — Adminer não suporta pré-preencher via variável de ambiente de forma simples; usuário digita host `db`, usuário/senha `jogo_acoes_admin`, banco `jogo_acoes` (mesmos valores já visíveis em `docker-compose.yml`) na tela de login. | resolvida | Aceitável pra uso local — não vale complicar a imagem só pra evitar digitar 4 campos já documentados no próprio `docker-compose.yml`. |

## Estrutura de módulos/pacotes

- `docker-compose.yml` (modificado) — serviço `adminer` novo.

## Riscos e trade-offs

Nenhum relevante — mudança de infraestrutura de desenvolvimento local, sem tocar código da
aplicação.
