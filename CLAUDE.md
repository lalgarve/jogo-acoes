# CLAUDE.md

Orientação rápida para trabalhar neste repositório. Antes de qualquer commit, Issue ou PR,
leia **[`memory/constitution.md`](memory/constitution.md)** — é a fonte da verdade para
processo e convenções deste projeto (fluxo SDD, formato de commit, branches/PRs, testes,
onde cada tipo de documentação mora). Este arquivo só resume o que já causou erro antes.

## Idioma — a regra mais violada até agora

Já aconteceu duas vezes (PRs da Iteração 4/5 antes da PR #17; de novo na Iteração 5, Issues
#74–#78 e PRs #77/#79/#80) então repetindo aqui, verbatim, de
`memory/constitution.md` → seção "Idioma":

| O quê | Idioma |
|---|---|
| Código: identificadores, comentários, nomes de arquivo de código | Inglês |
| Mensagens de commit | Inglês |
| Especificações de comportamento (Gherkin, `.feature`) | Inglês |
| Issues e Pull Requests (título e descrição) | Inglês |
| Documentação de projeto (`README.md`, `docs/*.md`, `specs/**/*.md`) | Português |

O motivo do PR/commit em inglês é estrutural, não estético: o GitHub usa o título da PR
como corpo do merge commit em `master` — uma PR em português vaza pro histórico de commits
exatamente como um `git commit -m` em português vazaria. Antes de chamar qualquer tool de
Issue/PR (criar ou editar) ou `git commit`, escrever o texto em inglês primeiro — não
escrever em português e "lembrar de traduzir depois".

## Nunca começar a implementar sem pedido explícito

Já aconteceu: uma sessão discutindo o desenho de uma correção, corrigida pelo usuário no meio
da conversa, assumiu que tinha entendido tudo certo, julgou a solução simples, e começou a
implementar sozinha — sem ninguém ter pedido isso. Discutir/decidir não é sinal verde pra
código. Só implementa quando pedirem explicitamente ("pode implementar", "começa a
implementação"), mesmo com desenho técnico claro na conversa, mesmo se a mudança parecer óbvia
— "parecer simples" é julgamento da sessão, não permissão de quem pediu. Detalhe completo:
`memory/constitution.md` → "Nunca começar a implementar sem pedido explícito".

## Baseline Java e upgrades de LTS

Java/JDK 21 é a baseline suportada atualmente por este projeto. Não sugerir nem iniciar uma
atualização para outro LTS durante uma tarefa não relacionada. Só discutir ou executar um
upgrade de Java/JDK quando houver um pedido explícito. Se uma dependência, framework,
ferramenta ou ambiente exigir outra versão, reportar a incompatibilidade concreta e aguardar
uma decisão explícita sobre a mudança da baseline — não transformar essa exigência em um
upgrade automático.

## O projeto

Simulação de investimentos em bolsa por competição: administradores criam competições,
jogadores entram por convite/link mágico e negociam ações fictícias. Back-end Java/Spring
Boot, multi-módulo Maven:

| Módulo | Papel |
|---|---|
| `app/` | API principal (Spring Boot) |
| `email-lambda/` | Consumidor da fila de e-mail (AWS Lambda / Quarkus) |
| `blackbox-proxy/` | Proxy reverso de teste — controla `Sec-CH-UA*`/`User-Agent` pro Swagger UI (spec 05-020), não roda em `staging`/`production` |
| `blackbox-tests/` | Suíte de testes de caixa-preta em Python (`behave` + `pytest`), fora do reator Maven |

## Onde procurar o quê

- **Processo/convenções** → `memory/constitution.md` (agnóstico de projeto; lido antes de
  qualquer coisa não trivial).
- **Requisito + decisão técnica de uma feature** (a partir da Iteração 5) →
  `specs/NN-NNN-slug/` (`spec.md`/`plan.md`/`tasks.md`).
- **Diário de sessão/iteração** (não é documentação de produto) → `docs/context/iteracao-N.md`.
- **Contrato de API** → `docs/openapi.yaml` (contract-first; controllers seguem o contrato).
- **`docs/disciplina/CLAUDE.md`** — decisões escopadas só a `docs/disciplina/` (documento de
  entrega da disciplina); não se aplica ao resto do repositório.

## Lembretes rápidos de convenção (detalhe completo em `memory/constitution.md`)

- Commit: `<tipo>: <resumo curto, no imperativo>` em inglês — tipos `feat|fix|refactor|test|
  docs|chore|decision`.
- Nunca commitar direto em `master`; um branch por linha de trabalho revisável, reaproveitado
  entre sessões/ferramentas em vez de criar um novo a cada retomada.
- Uma feature ganha uma Issue-épico linkando `spec.md`/`plan.md`; tarefas de `tasks.md` viram
  checklist da Issue ou Issues próprias.
- Testes: preferir dependência real a mock/fake sempre que der (ver seção "Testes" da
  constitution).
- Banco: cada serviço no próprio schema, nunca no `public`; migrations em
  `db/migration-<serviço>` e histórico do Flyway em `<schema>_schema_history` (seção "Banco de
  dados: um schema por serviço" da constitution, spec 05-032).
- Serviços (`app`, `email-service`) só se falam por contrato (OpenAPI/mensagem): nenhuma
  dependência Java nem persistência compartilhada entre eles, e retry automático só de operação
  idempotente (seção "Fronteira entre módulos e serviços" da constitution, spec 05-034).
- Código de teste/dev nunca dentro de um módulo de produção (`app/`, `email-lambda/`) — mesmo
  atrás de profile/flag, mesmo que funcione, mesmo que a alternativa exija mais código. Sempre
  um módulo/aplicação separada (padrão `blackbox-proxy/`). `email-lambda/` já corrigido
  (`EmailQueuePoller` removido — LocalStack dispara o Lambda nativamente, ver Issue #87); `app/`
  (pacote `blackbox/`) também corrigido ([Issue #84](https://github.com/lalgarve/jogo-acoes/issues/84),
  specs 05-023/05-026).
