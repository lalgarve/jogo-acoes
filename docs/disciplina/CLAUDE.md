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
| `leila-algarve_arquitetura-avancadas-de-software_pd.tex` | Fonte LaTeX (abnTeX2), **gerada** por `scripts/md2tex.py` a partir do rascunho `.md` e dos dois apêndices. Não editar à mão: mudar o `.md` (ou o script) e gerar de novo. |
| `referencias.bib` | Bibliografia em BibTeX (formato `abntex2cite`), consumida pelo `.tex`. Mantida desde já em paralelo à seção "Bibliografia" do rascunho `.md` (texto ABNT), porque as entradas são estáveis e não sofrem o problema de divergência do `.tex`. Livros lidos na O'Reilly entram como e-book, com `url` e `urlaccessdate`. |
| `uml/` | Fontes PlantUML (`.puml`) dos diagramas deste documento. |
| `scripts/plantuml` | Launcher local (fixa JDK 11) para renderizar `uml/*.puml`. |
| `scripts/md2tex.py` | Gera o `.tex` a partir dos `.md` (`python3 scripts/md2tex.py`, a partir desta pasta). Entende só o subconjunto de Markdown que os rascunhos usam. |
| `image/` | Imagens/diagramas já renderizados, incluídos no `.tex` via `\includegraphics`. |
| `caderno-de-testes.md` | Caderno de testes manuais, por funcionalidade do sistema (spec 05-040). |
| `caderno-de-testes/` | Apoio do caderno: dump da massa de dados (`dados/`), cabeçalhos de dispositivo (`dispositivos/`) e scripts bash/PowerShell (`scripts/`). |
| `apendice-a-caderno-de-testes.md`, `apendice-b-caderno-de-testes-email-service.md` | Versões resumidas dos dois cadernos (sem logs nem `SELECT`s), que viram os apêndices A e B do PDF. Quando um caderno mudar, atualizar o apêndice correspondente. |
| `caderno-de-testes-email-service.md` | Caderno de testes manuais do Serviço de E-mail (chaves e permissões, templates, envio, listas de e-mail temporário), sobe só o `email-service`. |
| `caderno-de-testes-email-service/` | Apoio desse caderno: listas de domínios de teste (`listas/`) e scripts bash (`scripts/`). As chaves geradas (`chaves.env`) e a CLI do `api-key` (`.cli/`) ficam fora do git. |

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

### Sessão 2026-10-15 (continuação) — tag `etapa-4` e `.tex`

- Tag anotada `etapa-4` ("Fim da Etapa 4") criada em `6552d1c`, merge da PR #176 no `master`, que
  já inclui o Spring Batch (PR #171) e a correção do acento no preview (PR #175). Mesmo padrão das
  tags anteriores. Tabela da Etapa 4 do rascunho atualizada (tag atendida).
- `.tex` gerado por `scripts/md2tex.py`. Compilado na sessão com `pdflatex` + `bibtex` no WSL,
  com o abnTeX2 e o `babel` em português baixados do CTAN só para o teste (o WSL não os tem):
  47 páginas, sem erro. A compilação de entrega continua no MiKTeX (`pdflatex`, `bibtex`,
  `pdflatex`, `pdflatex`).
- `\nocite{*}` não serve com o `abntex2cite`: ele também lê o `abntex2-options.bib` e lista as
  entradas de opção como referências. O script gera `\nocite{...}` com as chaves do
  `referencias.bib` e `\citeoption{abnt-full-initials=yes}` (nomes por extenso, como no `.md`).
- `referencias.bib`: o ano `[20--]` virou a macro `\decadaprovavel` (o colchete literal quebrava
  o rótulo do `\bibitem` e imprimia a chave); GitHub como autor `{{GITHUB}}` (o campo `org` não é
  usado pelo estilo); subtítulo do Smart entre chaves para manter as maiúsculas.
- Figuras com `[H]` (pacote `float`): o texto diz "abaixo"/"a seguir", então a figura fica onde
  aparece. Fonte das figuras: "elaborada pela autora".
- Autor na capa: `Leila Algarve` — conferir se a autora quer o nome completo.
- `.gitignore` desta pasta ignora os auxiliares do LaTeX gerados pelo MiKTeX (não o PDF).

### Sessão 2026-10-15

- Só pode ser entregue um PDF: os dois cadernos de testes (`caderno-de-testes.md` e
  `caderno-de-testes-email-service.md`) entram nele como **apêndices** (material da própria
  autora; "anexo", na ABNT, é material de terceiros). Convertidos por último, junto com o resto
  (ver "Fluxo de edição").
- Dados da folha de rosto: Instituto Infnet; MBA em Engenharia de Software com Java; disciplina
  Arquiteturas Avançadas de Software com Microserviços e Spring Framework; professor Elberth
  Moraes. Cidade e ano ainda não confirmados.
- Seção "Conclusão" acrescentada ao rascunho, antes da bibliografia, a partir de três pontos da
  autora (módulos revelando oportunidades de padrões, isolamento de repositórios, testes
  manuais achando problemas de ambiente e documentação). Acrescentados: verificação das regras
  por ArchUnit, API-First, SDD/BDD com IA e as pendências.
- Sem resumo e sem citações no texto: a bibliografia sai inteira com `\nocite{*}`.
- Seção "Repositório e versões entregues" no começo do rascunho; no `.tex`, logo depois do
  sumário. Traz o repositório, a *tag* de cada etapa (`etapa-4` ainda não existe), o
  repositório do `api-key` e os links dos cadernos completos.
- Cadernos de testes entram **resumidos** nos apêndices; cada apêndice começa com o link para o
  `.md` completo no GitHub.
- Ferramentas de IA (Claude/Claude Code, GitHub Copilot, Google no modo IA) listadas no fim de
  "Metodologia de desenvolvimento". O uso de cada uma foi escrito pela sessão a partir do
  histórico do repositório (branches `copilot/*`); o do Google ("pesquisa") é suposição, a
  confirmar com a autora.
- Folha de rosto: Rio de Janeiro, 2026.
- Apêndices escritos: `apendice-a-caderno-de-testes.md` e
  `apendice-b-caderno-de-testes-email-service.md`. Mantêm objetivo, pré-condições, passos com o
  corpo JSON e resultado esperado de cada roteiro; tiram os trechos de log, os `SELECT`s e as
  respostas longas (decisão da autora).

### Sessão 2026-10-14

- Caderno novo, `caderno-de-testes-email-service.md`, no formato do caderno do `jogo-acoes`, só
  para o Serviço de E-mail (specs 05-025, 05-031, 05-047 e 05-048). Separado do
  `caderno-de-testes.md` porque sobe só o `email-service` e chama a API com `curl` (o serviço não
  tem Swagger UI), com duas chaves criadas pelo próprio caderno, uma por permissão (`email:send`
  e `disposable-email:admin`).
- Versão 1 escrita antes da implementação da 05-048: passos com JSON e respostas esperadas do
  contrato e das specs. Logs e `SELECT`s entram na versão 2, capturados depois da implementação.
- Só bash (WSL): os scripts do caderno de e-mail não têm versão PowerShell por enquanto.
- Versão 2 do caderno de e-mail: roteiros executados com o serviço de verdade (LocalStack
  Community local, com a `email-lambda`), logs e `SELECT`s preenchidos. Respostas acertadas onde
  a versão 1 divergia do serviço: campos vazios não vêm no JSON, `skipped` conta só linhas
  inválidas (comentário e linha em branco são filtrados), reimportação sem mudança termina com
  `exitMessage` `UNCHANGED`. O aviso de que o `restore` falhava saiu: o dump já é o da CLI 1.1.0.
- Falha encontrada no roteiro 2: a pré-visualização devolvia o assunto com acento corrompido
  (`estÃ¡`). Corrigida pela spec 05-051 (PR #175); o roteiro 2 foi refeito em 2026-10-15 com o
  serviço corrigido, e o caderno traz a resposta certa com uma nota sobre a falha.
- Etapa 3 escrita no rascunho: tabela antes/depois (tags `etapa-2` → `etapa-3`) e principais
  tarefas (Config Server, ambiente de execução). Resolve a pendência da sessão 2026-10-08: a
  tabela não lista mais o perfil `sandbox`.
- Início da geração do PDF, pela bibliografia. As duas referências que estavam no rascunho só
  como link solto (template `tasks-template.md` do Spec Kit e o artigo do techknow sobre SDD)
  foram formatadas em ABNT na seção "Bibliografia" do `.md` e acrescentadas ao
  `referencias.bib` (`github-speckit-tasks`, `wildrik2026sdd`). Seção do `.md` reordenada em
  ordem alfabética.
- Data de acesso dessas duas: o rascunho dizia "16/10/2026", data futura nesta sessão. A
  autora confirmou que o mês estava errado: o certo é 16 set. 2026.
- Nenhuma referência é citada no corpo do texto ainda; no `.tex`, ou entram citações
  (`\cite`/`\citeonline`) ou a lista sai com `\nocite{*}`.
- abnTeX2 não está instalado no TeX do WSL (`kpsewhich abntex2.cls` vazio) — a compilação
  continua no MiKTeX do Windows.

### Sessão 2026-10-13

- Spec 05-043: `caderno-de-testes.md`, tabela de programas, troca `docker compose logs -f app`
  pelo visualizador de logs (Dozzle, `http://localhost:8083`) e sobe o `log-viewer` junto do
  Adminer e do `ses-viewer`. Os trechos de log já capturados nos roteiros não mudam.

### Sessão 2026-10-12

- Spec 05-040: `caderno-de-testes.md` reescrito por funcionalidade (login, competições), no lugar
  da versão por Etapa da spec 05-012. Os roteiros 1–3 de login são da autora; os 4–7 de
  competições foram acrescentados. Logs e `SELECT`s capturados sobre a massa restaurada do dump.
- Pasta nova `caderno-de-testes/` com o dump da massa, os cabeçalhos de dispositivo e os
  scripts. Os cookies que os scripts gravam ficam fora do git (`caderno-de-testes/.gitignore`).
- O roteiro 3 registra uma falha do sistema: a sessão encerrada pelo limite de dispositivos
  continua autenticada.

### Sessão 2026-10-08

- Spec 05-035 removeu o perfil `sandbox`. `caderno-de-testes.md` atualizado: a aplicação sobe
  com `docker compose up` (perfil `docker`) e o e-mail passa pelo SES do LocalStack. A nota
  sobre os `SELECT`s capturados contra H2 fica, como registro de como foram obtidos.
- `alinhamento-projeto-disciplina.md` não foi tocado: é histórico (ver tabela acima).
- **Pendente no rascunho** (`leila-algarve_arquitetura-avancadas-de-software_pd.md`, tabela da
  Etapa 3): "Profiles para ao menos dois ambientes" ainda lista
  `sandbox/docker/staging/production`; o certo agora é `docker/staging/production`, que continua
  atendendo o requisito. Não editado pela PR da spec 05-035 porque a PR #112 sincroniza o
  rascunho local da autora e mexe nas linhas vizinhas.

### Sessão 2026-09-16

- Primeiros diagramas desta pasta: 5 arquivos PlantUML em `uml/` (`login-arquitetura-classes`,
  `login-modelo-dados-classes`, `login-pedido-sequencia`, `login-consumo-sequencia`,
  `login-registro-duas-fases-sequencia`), cobrindo o mecanismo de link/login revisado na spec
  `05-003` — validados localmente (`plantuml -checkonly`, exit 0) e renderizados em PNG para
  `image/` (`plantuml -tpng`), a primeira vez que essa pasta deixa de estar vazia.
- Conteúdo correspondente adicionado ao rascunho `.md`, seção "Principais tarefas realizadas
  na Etapa 1", com as 5 imagens referenciadas via Markdown padrão (`![...](image/arquivo.png)`)
  — troca para `\includegraphics` só na geração do `.tex`, por enquanto continua Markdown puro
  (ver "Fluxo de edição" acima).
- Linha da tabela de status da Etapa 1 ("Organização de pacotes por domínio/funcionalidade")
  corrigida de "Não atendido" para "Atendido", refletindo as specs `05-001`/`05-002` já
  implementadas e mergeadas (PR #48) desde a última vez que este documento foi editado
  (sessão 2026-09-15, antes dessas specs existirem).
- Branch usado: `43-write-the-final-pdf-and-the-swagger-test-notebook-per-etapa-not-only-at-the-end-of-iteração-5`
  (o de nome mais longo do repositório, rastreado pela Issue #43) — estava parado desde a
  sessão 2026-09-13/15, então foi atualizado com um fast-forward simples a partir de `master`
  antes desta sessão (sem conflito — o branch é um ancestral direto de `master`).

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
