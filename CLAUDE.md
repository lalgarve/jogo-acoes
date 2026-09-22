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
