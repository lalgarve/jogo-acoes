# Plan: Caderno de testes — Etapa 1 (Swagger)

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`docs/disciplina/` já tem convenção própria de arquivos e fluxo de edição (`CLAUDE.md` dessa
pasta) — inclusive a decisão de que esse trabalho roda numa **branch separada** da branch de
código da iteração, pra não misturar revisão de documento com revisão de código (Issue #43,
seção "Branch"). Esta spec só desenha o conteúdo; a execução (escrita de fato) segue essa
convenção de branch quando chegar a hora.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Nome/local do arquivo | `docs/disciplina/caderno-de-testes.md` — um arquivo só, seções por Etapa (`## Etapa 1`, `## Etapa 2`, ...), em vez de um arquivo por Etapa. | resolvida | Mais fácil de navegar como documento único conforme cresce; a Issue #43 pede "uma seção do PDF por Etapa", não necessariamente arquivos Markdown separados — a fonte pode ser um arquivo, a estrutura final em seções. |
| Formato de cada caso de teste | Sub-título por caso: **Objetivo** (qual critério da Etapa cobre) → **Passos no Swagger UI** → **Log esperado** (trecho literal ou com placeholder pros valores variáveis) → **Select de verificação**. | resolvida | Estrutura fixa e repetível, fácil de olhar e comparar execução real contra o esperado. |
| Branch de execução | Segue a convenção já estabelecida na Issue #43/`docs/disciplina/CLAUDE.md`: branch separada da branch de código (`docs/...`), não a `claude/jogo-acoes-iteracao-5-5hloak`. | resolvida | Já decidido antes desta spec, só reafirmado aqui pra não escrever o conteúdo na branch errada quando chegar a hora de implementar. |
| Dependência de outras specs | Esta spec só pode ser **escrita de conteúdo completo** depois que 05-009 (Swagger UI), 05-010 (sessões) e 05-011 (logs) estiverem implementadas — sem elas, não há o que descrever nos passos/logs/selects. `spec.md`/`plan.md` (este documento) podem existir antes, mas `tasks.md`/execução esperam as outras três. | resolvida | Ordem de dependência real, não arbitrária — o conteúdo depende literalmente do comportamento das outras specs. |

## Estrutura de módulos/pacotes

- `docs/disciplina/caderno-de-testes.md` (novo).

## Riscos e trade-offs

- **Conteúdo fica desatualizado se as specs 05-009/05-010/05-011 mudarem depois** (ex. formato
  de log ajustado) — mesma categoria de risco de qualquer documentação que descreve
  comportamento de código; sem mecanismo automático de sincronia (é um documento pra humano ler
  durante uma demonstração/avaliação, não um teste executável).
- **Cross-branch**: como a execução vai pra uma branch separada da branch de código, existe
  risco de essa branch ficar desatualizada em relação ao código (mesmo problema que já aconteceu
  com `docs/disciplina/` antes, registrado no próprio `CLAUDE.md` da pasta) — mitigação já
  documentada lá (fast-forward a partir de master antes de cada sessão nessa branch).
