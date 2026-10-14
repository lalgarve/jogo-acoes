# Tasks: Caderno de testes do Serviço de E-mail, versão 2

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

Issue-épico: a criar quando a spec for aprovada.

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Resolver as decisões em aberto de `spec.md` (token ausente, CI, versão da imagem) e registrar com commit `decision:` | — | | — |
| T002 | Leila: criar o segredo `LOCALSTACK_AUTH_TOKEN` no repositório do GitHub | T001 | [P] | — |
| T003 | `docker-compose.yml` (imagem `localstack-pro`, variável do token), `.gitignore` (`.env`), `ci.yml` (segredo). Confirmar a subida com token e a parada com a mensagem de licença sem token | T001 | | — |
| T004 | `README.md` e preparação dos dois cadernos: como fornecer o token | T003 | [P] | — |
| T005 | Executar os roteiros 1 e 2 (chaves e templates) e registrar resposta real, logs e `SELECT`s | T003 | | — |
| T006 | Executar o roteiro 3 (envio e domínio do destinatário) com a `email-lambda` entregando no SES: resposta, logs do `email-service` e da Lambda, `SELECT`s, e-mail em `/_aws/ses` | T005 | | — |
| T007 | Executar os roteiros 4 a 6 (listas de e-mail temporário) e registrar resposta, logs e `SELECT`s, incluindo as tabelas do Spring Batch | T005 | | — |
| T008 | Topo do caderno como "Versão 2" com data e commit da execução; seção "Achados" com as divergências encontradas; `docs/disciplina/CLAUDE.md` | T005–T007 | | — |
| T009 | Critério de aceite de exceções (`memory/constitution.md`): nenhuma classe de exceção nem branch de código novo; registrar aqui | T003 | | — |
| T010 | Verificação manual (Leila): rodar o caderno versão 2 do começo ao fim no WSL e registrar | T008 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação.

## Verificação manual (T010)

Na raiz do repositório, no WSL, com o branch da implementação. O token é o da conta da Leila no
LocalStack (`https://app.localstack.cloud`, "Auth Token"):

```bash
export LOCALSTACK_AUTH_TOKEN='<seu token>'
export COMPOSE_FILE=docker-compose.yml
docker compose down -v
docker compose up -d --wait --build db-email-service localstack dns
docker compose logs localstack | grep -i -E 'license|activated'
```

Esperado: a linha de licença ativada do LocalStack Pro, sem "License activation failed". Depois,
seguir a seção 1 do caderno do Serviço de E-mail a partir do primeiro `./scripts/test-api-key.sh
restore` e os roteiros 1 a 6, conferindo cada resposta, log e `SELECT` com o que está escrito.

Sem token, para conferir a mensagem:

```bash
docker compose down -v
LOCALSTACK_AUTH_TOKEN= docker compose up -d localstack
docker compose logs localstack | tail -20
```

Esperado: "License activation failed" e o container `localstack` parado (código 55).
