# Tasks: Suíte de testes blackbox em Python (behave + pytest)

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (nome do diretório, `openapi-python-client`, `.feature` próprios em vez de
reaproveitar os do Java, onde `pytest` entra, `API_BASE_URL`) já estão resolvidas em `plan.md` —
esta lista só quebra a implementação em passos.

**Pré-requisito de execução (não bloqueio de estrutura)**: T001–T004 e T008 (setup do projeto,
geração do cliente, `.gitignore`, teste `pytest`) não dependem da spec 05-014 estar
implementada. T005–T007 (o cenário de negócio de ponta a ponta) precisam do ambiente `blackbox`
da spec 05-014 rodando de verdade para passar — sem ele, o passo que confirma entrada numa
competição privada/pública trava no captcha real.

**Sem Cucumber/contrato OpenAPI Java novo** — este projeto é Python, fora do reator Maven; não
adiciona nem modifica nenhum `.feature` de `app/src/test/resources/features`.

Issue-épico ainda não criada — coluna `Issue` fica `—` até existir; tasks.md é atualizado com o
número assim que a Issue for aberta (mesmo padrão já usado pelas specs 05-009 a 05-013).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar `blackbox-tests/pyproject.toml` (dependências: `behave`, `pytest`, `openapi-python-client`) | — | [P] | — |
| T002 | Gerar o cliente a partir de `docs/openapi.yaml` via `openapi-python-client generate` para `blackbox-tests/generated_client/`; confirmar que o pacote gerado importa sem erro (`python -c "import ..."`) | T001 | | — |
| T003 | Adicionar `blackbox-tests/generated_client/` e o ambiente virtual Python local ao `.gitignore` (raiz) | T002 | [P] | — |
| T004 | Criar o hook compartilhado de `behave` (`blackbox-tests/features/environment.py`) que lê `API_BASE_URL` (default `http://localhost:8080/api`) e instancia o cliente gerado, disponibilizando via `context` para os passos | T002 | | — |
| T005 | Escrever o primeiro `.feature` novo (`blackbox-tests/features/`) — caminho feliz de ponta a ponta do ponto de vista de um cliente HTTP externo (ex.: pedido de entrada pública → confirmação → listagem), texto próprio (não copiado de nenhum `.feature` Java) | T004 | | — |
| T006 | Implementar os passos Python do `.feature` de T005, chamando o cliente gerado (sem JDBC direto, sem ler log — só as respostas HTTP) | T005 | | — |
| T007 | Rodar o cenário de T005/T006 contra `docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up` (spec 05-014) — confirmar verde, incluindo o passo que envia `captchaToken` sem resolver o desafio ALTCHA de verdade | T006 | | — |
| T008 | Escrever pelo menos um teste `pytest` técnico pontual (`blackbox-tests/tests/`) — uma verificação que não justifica um cenário Gherkin completo (ex.: o shape de um corpo de erro específico) | T002 | [P] | — |
| T009 | Criar `blackbox-tests/README.md`: como instalar dependências, gerar/regenerar o cliente, rodar `behave`/`pytest`, e contra qual ambiente (link para a seção `blackbox` do `README.md` da raiz, spec 05-014) | T007, T008 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`feat`).
- T007 depende de um daemon Docker disponível no ambiente de implementação (para subir o
  ambiente `blackbox` da spec 05-014) — se não houver, validar T005/T006 pelo menos contra um
  `behave --dry-run`/revisão estática dos passos, e registrar explicitamente que a execução
  ponta a ponta fica pendente de um ambiente com Docker.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
