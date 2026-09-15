# CLAUDE.md — docs/disciplina/

Este arquivo registra decisões sobre **esta pasta e suas subpastas** (`uml/`, `scripts/`,
`image/`) — o documento de entrega da disciplina e o material que o sustenta (rascunho,
diagramas, script de conversão). Não cobre decisões de código Java: essas continuam em
[`docs/context/iteracao-5.md`](../context/iteracao-5.md) e em `specs/`, seguindo a separação
já estabelecida em [`memory/constitution.md`](../../memory/constitution.md) ("Onde a
documentação mora").

**Por que um arquivo separado**: a edição deste documento roda em paralelo ao trabalho de
código da Iteração 5. Se as duas coisas fossem registradas no mesmo arquivo
(`iteracao-5.md`), sessões trabalhando em paralelo em cada frente disputariam as mesmas
linhas com frequência — só de decisões, não de conteúdo do documento em si (esse é o rascunho
`.md` desta pasta, comparado por diff normal do git). Manter os dois arquivos separados evita
esse conflito de merge.

## Convenção de arquivos desta pasta

| Arquivo/pasta | Papel |
|---|---|
| `alinhamento-projeto-disciplina.md` | Levantamento already-feito: mapeia o estado atual do projeto contra as quatro Etapas do enunciado. Histórico, não é o rascunho de entrega. |
| `leila-algarve_arquitetura-avancadas-de-software_pd.md` | Rascunho de trabalho do conteúdo que vai para o PDF final — ver "Fluxo de edição" abaixo. Nome segue o formato `nomedoaluno_nomedadisciplina_pd` exigido pelo enunciado (ver "Nome do arquivo" abaixo). |
| `leila-algarve.tex` | Fonte LaTeX (abnTeX2), gerada a partir do rascunho `.md` quando o conteúdo estabilizar. |
| `uml/` | Fontes PlantUML (`.puml`) dos diagramas deste documento. |
| `scripts/plantuml` | Launcher local (fixa JDK 11) para renderizar `uml/*.puml`. |
| `image/` | Imagens/diagramas já renderizados, incluídos no `.tex` via `\includegraphics`. |

## Fluxo de edição: Markdown primeiro, `.tex` só no final

**Decisão (sessão 2026-09-15):** o conteúdo de entrega é escrito e revisado em Markdown
(`leila-algarve_arquitetura-avancadas-de-software_pd.md`) — diffs de Markdown são muito mais
fáceis de revisar do que diffs de LaTeX. O `.tex` só é gerado a partir desse rascunho quando o
conteúdo estiver estável, não mantido em paralelo com ele.

**Por quê**: a primeira versão de `leila-algarve.tex` foi gerada diretamente (sessão
2026-09-13), sem um rascunho `.md` correspondente ainda escrito. Quando o rascunho `.md`
chegou, o `.tex` já não batia mais com o conteúdo — foi apagado pela autora nesta sessão. A
lição: gerar o `.tex` cedo demais custa retrabalho, porque as duas fontes divergem assim que
qualquer uma muda sozinha.

**Como aplicar**: enquanto o rascunho `.md` ainda está mudando, não existe (ou não é mantido)
um `.tex` correspondente. Regenerar o `.tex` a partir do `.md` é a última etapa antes de virar
PDF, não um artefato paralelo atualizado a cada sessão.

## Ponto de vista: primeira pessoa do plural

**Decisão (sessão 2026-09-15):** o texto do rascunho é escrito em primeira pessoa do plural
("decidimos", "optamos", "escolhemos"), não singular, mesmo sendo autoria individual —
convenção comum em textos técnico-acadêmicos formais em português. Revisar qualquer parágrafo
novo contra essa convenção antes de considerá-lo pronto.

## Diagramas: PlantUML aqui, Mermaid no resto do projeto

O restante do projeto (`docs/diagrams/`) usa Mermaid, renderizado nativamente pelo
GitHub/Docsy (ver `memory/constitution.md`). Este documento usa PlantUML em vez disso porque o
PDF/LaTeX não tem suporte nativo a Mermaid como o GitHub tem — PlantUML gera imagens estáticas
(PNG/SVG) que `\includegraphics` do LaTeX consome diretamente. Consequência: `scripts/plantuml`
e `uml/` existem só dentro de `docs/disciplina/`, não no resto do repositório.

**Nenhum diagrama específico para este documento foi feito ainda** (confirmado sessão
2026-09-13) — `uml/` está vazia (só `.gitkeep`).

## Ferramentas — ambiente dividido entre Windows e WSL

- `.tex` compila localmente via MiKTeX (`pdflatex`), direto no Windows — confirmado na sessão
  2026-09-13 (duas passadas, 23 páginas, sem erros, só avisos cosméticos de hbox).
- `scripts/plantuml` precisa de JDK 11 + o pacote `plantuml` (jar em
  `/usr/share/plantuml/plantuml.jar`) — só existe no WSL Ubuntu desta máquina, não no Windows.
  Rodar a partir do WSL (`wsl -d Ubuntu -- <caminho>/scripts/plantuml uml/*.puml`) até decidir
  se vale instalar `plantuml` nativamente no Windows.

## Pendência: exclusão do pipeline de import do deployo-website

`memory/constitution.md` (seção "Onde a documentação mora") registra que o site institucional
que importa este repositório pula, por convenção, qualquer diretório chamado `context`, `uml`
ou `backlog`. `docs/disciplina/uml/` já cai nessa regra (nome bate). **`docs/disciplina/` em
si não bate** — `alinhamento-projeto-disciplina.md` e o rascunho `.md` desta pasta ficariam
sujeitos a publicação no portfólio se o pipeline rodar contra este repositório, o que não
acontecia quando o primeiro vivia em `docs/context/` (protegido).

**Decisão pendente**: pedir ao `deployo-website` para também pular `disciplina`, ou aceitar
que esse conteúdo (incluindo as notas de alinhamento com a disciplina, que mencionam a
dificuldade da autora numa tentativa anterior) seja publicado como está. Não resolvida nesta
sessão — só registrada aqui para não se perder.

## Nome do arquivo: `nomedoaluno_nomedadisciplina_pd`

O enunciado da disciplina exige o formato `nomedoaluno_nomedadisciplina_pd.PDF` (ver
`docs/context/iteracao-5.md`, seção 6). **Decisão (sessão 2026-09-15):** o rascunho `.md` foi
renomeado de `arquitetura-avancadas-de-software-leila-algarve-pd.md` (ordem
disciplina→aluno, separador `-`) para `leila-algarve_arquitetura-avancadas-de-software_pd.md`
(ordem aluno→disciplina, separador `_` entre os três segmentos exigidos pelo enunciado,
hífen preservado dentro de cada segmento para legibilidade). O `.tex`/PDF final, quando
gerado, deve manter esse mesmo nome-base (`leila-algarve_arquitetura-avancadas-de-software_pd`).

## Log de decisões

### Sessão 2026-09-15

- POV do rascunho mudado de 1ª pessoa do singular para plural em
  `leila-algarve_arquitetura-avancadas-de-software_pd.md` (na época ainda
  `arquitetura-avancadas-de-software-leila-algarve-pd.md`, ver renomeação abaixo).
- Pequenas correções factuais/ortográficas no mesmo rascunho: "Função Lamba" → "Função
  Lambda"; bullet quebrado "usando o sistema de" / "DQL" unificado e corrigido para "sistema
  de DLQ (dead-letter queue)"; "Behavior Direct Development" → "Behavior Driven Development";
  "Testing Driven Development" → "Test Driven Development".
- Rascunho renomeado de `arquitetura-avancadas-de-software-leila-algarve-pd.md` para
  `leila-algarve_arquitetura-avancadas-de-software_pd.md`, para bater com o formato
  `nomedoaluno_nomedadisciplina_pd` exigido pelo enunciado (ver "Nome do arquivo" acima).
- `leila-algarve.tex` anterior (gerado sessão 2026-09-13) apagado pela autora por não
  corresponder mais ao conteúdo do rascunho `.md` — será regenerado a partir do `.md` quando o
  conteúdo estabilizar (ver "Fluxo de edição" acima).
- Este arquivo criado para registrar decisões só sobre `docs/disciplina/` e subpastas,
  separado de `docs/context/iteracao-5.md` e `specs/` (código Java) — objetivo: evitar
  conflito de merge, já que a edição deste documento roda em paralelo ao trabalho de código.
