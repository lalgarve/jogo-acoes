# Tasks: Link absoluto nos e-mails de login e convite

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`). As decisões técnicas
(`links.base-url`/`LINKS_BASE_URL`, URL montada no módulo `link` via `LinkCreationResult.url`,
padrão só no perfil `docker`, validação na inicialização) já estão resolvidas em
`spec.md`/`plan.md`; esta lista só quebra a implementação em passos.

**Ordem:** verificações primeiro, falhando pelo motivo esperado; depois as correções.

Todas as tarefas abaixo são acompanhadas como checklist na Issue
[#92](https://github.com/lalgarve/jogo-acoes/issues/92).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Teste de integração (PostgreSQL real, `email.sender=stub`): para cada um dos quatro pontos de envio (pedido de login em `POST /login-requests`; abertura de competição privada com convites; pedido de entrada em competição pública; inclusão de jogador pelo administrador), o `sent_email.link` gravado é `http://localhost:8080/api/login-links/<token>`, com o token do `LinkRecord` criado. Rodar e confirmar que falha hoje porque o valor é relativo (`/login-links/<token>`) | — | [P] | #92 |
| T002 | Teste com `ApplicationContextRunner` para `LinksProperties`: base ausente, `localhost:8080/api` (sem esquema) e `/api` (relativa) impedem o contexto de subir; `http://localhost:8080/api/` (barra final) gera `http://localhost:8080/api/login-links/<token>`, sem `//`. Confirmar que falha (não compila enquanto `LinksProperties` não existe) | — | [P] | #92 |
| T003 | `blackbox-tests/tests/test_mailbox.py`: caso com `TemplateData` de link absoluto, e teste da extração do token a partir de `http://localhost:8080/api/login-links/<token>` e de `.../login-links/<token>/registration`. Confirmar que a extração atual (`link.strip("/").split("/")[1]`) falha | — | [P] | #92 |
| T004 | `link/LinksProperties` (`@ConfigurationProperties("links")`, `@Validated`, `baseUrl` com `@NotBlank` e `@Pattern(regexp = "^https?://\\S+$")`) registrado com `@EnableConfigurationProperties` numa configuração do módulo `link`. `application-docker.yml`: `links.base-url: ${LINKS_BASE_URL:http://localhost:8080/api}`; `application-staging.yml` e `application-production.yml`: `links.base-url: ${LINKS_BASE_URL}`, sem padrão, com comentário dizendo o valor esperado em produção (`https://jogoacoes.leilaalgarve.de/api`). Nada em `application.yml` | T002 | | #92 |
| T005 | `LinkCreationResult` ganha `url`; `LinkService.create` monta `<base sem barra final>/login-links/<token>`. Confirmar T002 verde | T004 | | #92 |
| T006 | `LoginController`, `CompetitionService`, `EntryRequestService` e `PlayerManagementService` passam `created.url()` no `EmailRequest` em vez de `"/login-links/" + created.token()`. Confirmar T001 verde | T001, T005 | | #92 |
| T007 | `blackbox-tests`: `_token_from_link` (`seed/flows.py` e `features/steps/public_competition_entry_steps.py`, usado também por `manage_active_sessions_steps.py`) passa a pegar o segmento depois de `/login-links/`, funcionando com link relativo e absoluto. Atualizar os comentários que dizem que o link é um caminho. Confirmar T003 verde e rodar a suíte `blackbox-tests` contra o `docker compose` | T003, T006 | | #92 |
| T008 | Documentação: em `docs/disciplina/caderno-de-testes.md`, trocar o texto "O link nos e-mails é relativo (Issue #92)" da seção "Como clicar no link do e-mail" (o link agora abre direto) e as saídas de exemplo que mostram `link /login-links/...` e o log do `SentEmailRepository.save`. Conferir o `README.md` pelo mesmo motivo. Os scripts `login-jogador.sh`/`.ps1` não mudam (já cortam pelo `/login-links/`) | T006 | [P] | #92 |
| T009 | Critério de aceite de exceções (`memory/constitution.md`, "Critério de aceite: exceções e caminhos de erro exercitados por teste"): listar as classes de exceção criadas (esperado: nenhuma) e os caminhos de erro adicionados (falha de validação/placeholder de `links.base-url` na inicialização); para cada um, apontar o teste de T002 que o exercita. Registrar a lista aqui | T006 | | #92 |
| T010 | Rodar a suíte completa (`mvn test`) e a `blackbox-tests` — confirmar verde | T007, T008, T009 | | #92 |
| T011 | Verificação manual (Leila): com `docker compose up`, pedir login, abrir o e-mail no `ses-viewer` e conferir que o link faz o login ao ser aberto | T010 | | #92 |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Cada linha vira um item de checklist na Issue #92, ou uma Issue própria quando grande o
  suficiente para PR isolada — a Issue leva o label `iteration-5`, além do label de tipo (`fix`).
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues") — não deixar a tabela dessincronizada do estado real.
