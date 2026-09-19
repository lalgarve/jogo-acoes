# Tasks: Suíte de testes blackbox em Python (behave + pytest)

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). Todas as decisões técnicas
referenciadas abaixo (nome do diretório, `openapi-python-client`, `.feature` próprios em vez de
reaproveitar os do Java, onde `pytest` entra, `API_BASE_URL`, como o link de e-mail é lido via
`GET /blackbox/last-email` direto por `httpx`) já estão resolvidas em `plan.md` — esta lista só
quebra a implementação em passos.

**Pré-requisito de execução (não bloqueio de estrutura)**: T001–T005 e T009 (setup do projeto,
geração do cliente, `.gitignore`, o helper de leitura de e-mail, teste `pytest`) não dependem da
spec 05-014 estar implementada — são código que só chama uma URL configurável, sem precisar dela
no ar para ser escrito. T006–T008 (o cenário de negócio de ponta a ponta) precisam do ambiente
`blackbox` da spec 05-014 rodando de verdade para passar — sem o administrador semeado e sem
`GET /blackbox/last-email` respondendo de verdade, o passo que lê o link de login trava.

**Sem Cucumber/contrato OpenAPI Java novo** — este projeto é Python, fora do reator Maven; não
adiciona nem modifica nenhum `.feature` de `app/src/test/resources/features`.

Todas as tarefas abaixo são acompanhadas como checklist na Issue-épico
[#69](https://github.com/lalgarve/jogo-acoes/issues/69) — nenhuma virou Issue própria (todas
pequenas o bastante para não precisar de PR isolada).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Criar `blackbox-tests/pyproject.toml` (dependências: `behave`, `pytest`, `openapi-python-client`, `httpx`) | — | [P] | #69 |
| T002 | Gerar o cliente a partir de `docs/openapi.yaml` via `openapi-python-client generate` para `blackbox-tests/generated_client/`; confirmar que o pacote gerado importa sem erro (`python -c "import ..."`) | T001 | | #69 |
| T003 | Adicionar `blackbox-tests/generated_client/` e o ambiente virtual Python local ao `.gitignore` (raiz) | T002 | [P] | #69 |
| T004 | Criar o hook compartilhado de `behave` (`blackbox-tests/features/environment.py`) que lê `API_BASE_URL` (default `http://localhost:8080/api`) e instancia o cliente gerado, disponibilizando via `context` para os passos | T002 | | #69 |
| T005 | Criar o helper `blackbox-tests/features/mailbox.py` (nome definido em `plan.md`): uma função que chama `GET {API_BASE_URL}/blackbox/last-email?email={endereço}` via `httpx` direto (não pelo cliente gerado, rota fora de `docs/openapi.yaml`) e devolve o `link` do corpo, ou levanta um erro claro em `404` (nenhum e-mail enviado ainda); usável tanto por passos de `behave` quanto por testes `pytest` | T004 | [P] | #69 |
| T006 | Escrever o primeiro `.feature` novo (`blackbox-tests/features/`) — caminho feliz de ponta a ponta do ponto de vista de um cliente HTTP externo, incluindo pelo menos um passo que depende de clicar num link (ex.: login do administrador semeado via link mágico, para então criar uma competição), texto próprio (não copiado de nenhum `.feature` Java) | T004, T005 | | #69 |
| T007 | Implementar os passos Python do `.feature` de T006, chamando o cliente gerado para as chamadas de contrato e o helper de T005 para ler o link de e-mail quando o passo precisar dele (sem JDBC direto, sem ler log — só as respostas HTTP) | T006 | | #69 |
| T008 | Rodar o cenário de T006/T007 contra o ambiente `blackbox` (spec 05-014) — confirmar verde, incluindo o passo que envia `captchaToken` vazio (sem resolver o desafio ALTCHA de verdade) e os passos que leem o link de login/registro via `GET /blackbox/last-email`. Executado com sucesso: `behave` (1 cenário, 7 passos) e `pytest` (1 teste) verdes, rodando contra o jar da spec 05-014 com Postgres real (o `docker compose up` completo não pôde ser confirmado neste ambiente por bloqueio de rede ao registry de imagens — mesma limitação registrada em `specs/05-014-ambiente-testes-blackbox/tasks.md`) | T007 | | #69 |
| T009 | Escrever pelo menos um teste `pytest` técnico pontual (`blackbox-tests/tests/`) — uma verificação que não justifica um cenário Gherkin completo (ex.: o shape de um corpo de erro específico, ou o `404` do helper de T005 quando nenhum e-mail foi enviado para um endereço) | T002, T005 | [P] | #69 |
| T010 | Criar `blackbox-tests/README.md`: como instalar dependências, gerar/regenerar o cliente, rodar `behave`/`pytest`, e contra qual ambiente (link para a seção `blackbox` do `README.md` da raiz, spec 05-014, incluindo o e-mail do administrador semeado) | T008, T009 | | #69 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue-épico da feature, ou uma Issue própria
  quando grande o suficiente para PR isolada — a Issue leva o label `iteration-5`, além do
  label de tipo (`test`).
- T008, nesta implementação: o `docker compose up` completo (spec 05-014) ficou fora de alcance
  por bloqueio de rede ao registry de imagens no ambiente de implementação — a suíte rodou de
  ponta a ponta mesmo assim, contra o mesmo jar/Postgres real usado para verificar a spec 05-014
  diretamente (sem o container em si). `API_BASE_URL` aponta pra onde quer que a aplicação
  esteja — `docker compose up` ou não, o comportamento da suíte é o mesmo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) quando o commit que a resolve for
  mesclado — não deixar a tabela dessincronizada do estado real.
