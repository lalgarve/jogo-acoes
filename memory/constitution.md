# Como desenvolvemos software

Este documento descreve o **fluxo de trabalho e as convenções de nomenclatura** usados
neste projeto — não as decisões técnicas específicas dele (essas ficam em
[`docs/roadmap.md`](../docs/roadmap.md), nos documentos de cada iteração e, a partir da
Iteração 5, em `specs/NN-NNN-slug/plan.md`). A ideia é que este arquivo seja **agnóstico de
projeto**: pode ser copiado como ponto de partida para outro repositório sem precisar
reexplicar o processo do zero — foi, aliás, o que aconteceu no sentido inverso:
[`deployo-template-java`](https://github.com/lalgarve/deployo-template-java) consolidou a
versão anterior deste arquivo (antes em `docs/context/desenvolvimento.md`) com a de
`deployo-api-key` num template reutilizável, e esta versão migra `jogo-acoes` para a
localização e nomenclatura que esse template já usa — `memory/constitution.md`, vocabulário
do Spec-Driven Development (SDD): o conjunto de princípios que toda `spec.md`/`plan.md` deve
respeitar.

**Nota histórica:** até a Iteração 5, este arquivo vivia em `docs/context/desenvolvimento.md`.
O conteúdo não mudou por causa da mudança de local — só a partir daqui passa a incluir as
seções novas descritas abaixo. O arquivo antigo foi removido (não deixado como redirecionamento)
e os links que apontavam pra ele em documentos de iteração anteriores foram atualizados para
apontar pra cá; ver `docs/context/iteracao-5.md` para o registro dessa decisão.

## Idioma

| O quê | Idioma |
|---|---|
| Código: identificadores, comentários, nomes de arquivo de código | Inglês |
| Mensagens de commit | Inglês |
| Especificações de comportamento (Gherkin, `.feature`) | Inglês |
| Issues e Pull Requests (título e descrição) | Inglês |
| Documentação de projeto (`README.md`, `docs/*.md`, `specs/**/*.md`) | Português |

Código em inglês porque é o padrão do ecossistema (bibliotecas, mensagens de erro,
convenções da linguagem). Documentação em português porque é o idioma da equipe — não faz
sentido traduzir decisões e raciocínio para um idioma que não é o nativo de quem escreve e
lê.

## Status do sistema: pré-produção

O sistema ainda não tem usuários reais nem dado em produção — está em pré-produção durante toda
a Iteração 5, e continua assim até este status ser explicitamente revisado neste documento.
Isso tem consequências diretas em como `spec.md`/`plan.md` são escritas e implementadas:

- **Nenhuma migração de dado existente é necessária.** Uma coluna nova pode ser `NOT NULL`
  direto, sem `default`/sem virar `nullable` "para registros antigos" — não existe registro
  antigo real que dependa disso. Uma tabela pode ser recriada, uma coluna removida, um enum
  perder valores, sem plano de migração dos dados já lá.
- **Nenhuma preocupação de compatibilidade retroativa de contrato** (versionamento de API,
  período de depreciação, cliente antigo continuando a funcionar) — `docs/openapi.yaml` pode
  mudar de forma incompatível entre specs, sem manter a forma anterior funcionando em paralelo.
- **Nenhum plano de rollback que preserve dado real** — uma migration Flyway nova pode fazer
  `DROP COLUMN`/`DROP TABLE` sem se preocupar com "e se alguém já tiver algo lá".
- Isso não dispensa manter a suíte de testes verde nem seguir TDD/API-first — só remove a
  categoria de risco "o que fazer com dado/cliente já existente", que normalmente motivaria
  `nullable`s defensivos, migrações em duas fases, ou versionamento de contrato.

**Quando isso muda**: no primeiro deploy com usuário real em produção, esta seção precisa ser
atualizada (ou removida) — a partir daí, specs que tocam schema/contrato voltam a precisar
considerar migração de dado existente e compatibilidade retroativa.

## Adoção do spec-kit (Spec-Driven Development) — a partir da Iteração 5

O processo já seguido no projeto desde a Iteração 1 (specs em `.feature` antes do código,
decisões técnicas registradas em `docs/context/iteracao-N.md` antes de implementar, DER antes
das entidades) já era, na essência, SDD — só não usava a nomenclatura/estrutura de arquivo do
[spec-kit](https://github.com/github/spec-kit). A partir da Iteração 5, `specs/NN-NNN-slug/`
(`spec.md` + `plan.md` + `tasks.md`, com `templates/` fornecendo o ponto de partida de cada
arquivo — ver `templates/` e `specs/README.md`) passa a existir **ao lado** do mecanismo já
usado, não no lugar dele.

**Isso é diferente do `deployo-template-java`**: aquele repositório nasceu já usando só
`specs/`, sem um equivalente a `docs/context/iteracao-N.md`, porque nasceu depois da adoção do
spec-kit, sem iteração nenhuma de história anterior para herdar. `jogo-acoes` é o caso
inverso — tem quatro iterações inteiras (`iteracao-2.md` a `iteracao-4.md`) de história
anterior ao spec-kit — por isso os dois mecanismos coexistem aqui, o que não existe no
template:

| | `docs/context/iteracao-N.md` | `specs/NN-NNN-slug/` |
|---|---|---|
| Escopo | Uma iteração inteira (várias features/decisões de processo) | Uma feature/funcionalidade específica |
| Ciclo de vida | Diário: atualizado incrementalmente, sessão a sessão, enquanto a iteração está em andamento | Escrito antes da implementação da feature; "fechado" quando ela é entregue — não é diário |
| Papel principal | Continuidade de contexto entre sessões de chat, ou quando uma sessão trava no meio (ver `iteracao-5.md`, seção "Diário desta iteração") | Contrato de requisito (`spec.md`) e decisão técnica (`plan.md`) de uma funcionalidade específica |
| Convertido retroativamente para o formato novo? | Não — `iteracao-2.md` a `iteracao-4.md` ficam como histórico, não reescritos | Não aplicável — só existe a partir daqui |

**Granularidade — resolvida (sessão 2026-09-10)**: por funcionalidade, mas agrupada por
iteração. `NN` é o número da iteração (2 dígitos, ex. `05`, `08`, `17` — mesmo número do
label `iteration-N` da Issue, ver "Rastreamento de trabalho via Issues" abaixo); `NNN` é o
número sequencial da funcionalidade **dentro dessa iteração** (3 dígitos, reinicia a cada
iteração, ex. `001`, `002`). Uma feature da Iteração 5 sobre o Serviço de E-mail vira, por
exemplo, `specs/05-001-servico-email-templates/`. Ver `specs/README.md` para a estrutura
completa de cada pasta.

Os `.feature` Gherkin continuam vivendo em `app/src/test/resources/features` (contrato de
aceite, executável) — o spec-kit não substitui isso. O `spec.md` de uma feature referencia o(s)
arquivo(s) `.feature` correspondentes em vez de duplicar os cenários em prosa.

## Commits semânticos

Formato da primeira linha:

```
<tipo>: <resumo curto, no imperativo>
```

Tipos usados neste projeto (convenção Conventional Commits, mais um tipo próprio):

| Tipo | Quando usar |
|---|---|
| `feat` | Nova funcionalidade ou comportamento observável |
| `fix` | Correção de bug |
| `refactor` | Mudança estrutural que não altera comportamento (renomear, mover, reorganizar) |
| `test` | Adição/alteração de testes ou especificações (`.feature`) |
| `docs` | Mudança só de documentação |
| `chore` | Manutenção sem impacto em código de produção (dependências, config de build) |
| `decision` | Registra uma decisão de arquitetura/design tomada, antes ou junto da implementação que ela habilita |

`decision` é a extensão específica deste fluxo: quando uma pergunta de arquitetura em
aberto (documentada previamente como pendente) é resolvida, isso vira um commit próprio,
separado da implementação — mesmo que a decisão não mude nenhuma linha de código sozinha
(ex.: "decision: session/auth via Spring Security"). Isso deixa o histórico do git navegável
como uma trilha de decisões, não só de mudanças de código.

Esses mesmos tipos são usados como **labels de Issue** — ver "Rastreamento de trabalho via
Issues" abaixo — para que commit, Issue e PR falem o mesmo vocabulário.

### Corpo da mensagem

Uma mensagem de commit completa, para uma mudança não trivial, normalmente tem:

1. **Título**: `<tipo>: <resumo>`.
2. **Por quê** (parágrafo): o raciocínio/problema que motivou a mudança — não repetir o
   que o diff já mostra, explicar a razão por trás dele.
3. **O quê** (lista com marcadores, opcional): mudanças concretas relevantes, arquivo por
   arquivo ou tema por tema, quando o "por quê" sozinho não é suficiente para orientar quem
   revisa.
4. **Validação** (parágrafo, opcional): o que foi de fato testado/rodado nesta sessão de
   trabalho para confirmar que a mudança funciona (ex.: "Validado nesta sessão: migrations
   aplicadas de verdade contra H2, N/N testes passando").
5. **Referência cruzada** (linha final, opcional): se a mudança resolve uma decisão em
   aberto registrada em outro documento, apontar para ele (ex.: "Resolves the X open
   decision in docs/context/iteracao-3.md").

Exemplo real deste projeto:

```
feat: add ALTCHA for captcha, resolving the stub decision (Iteração 3)

Self-hosted proof-of-work captcha instead of a third-party service or a fake
stub — since the challenge/solution round-trip is entirely local, the
captcha pass/fail scenarios can be tested for real: solve the challenge
correctly to pass, submit a tampered solution to fail.

AltchaSmokeTest proves the create/solve/verify round-trip actually works
against this project's dependency versions (2/2 passing).

Resolves the captcha-stub open decision in docs/context/iteracao-3.md.
```

Commits pequenos e focados em uma mudança revisável de cada vez — evitar juntar mudanças
sem relação numa mesma mensagem.

### Enforcement

Um hook `commit-msg` versionado em `.githooks/commit-msg` pode validar o formato da
primeira linha automaticamente (o `<tipo>: ` do título — idioma não é validado por hook).
Ativar uma vez por clone/sessão:

```
git config core.hooksPath .githooks
```

**Exemplo usado nos repositórios que adotaram este padrão** (`deployo-website`,
`deployo-infra`): hook em `.githooks/commit-msg` que rejeita qualquer commit cuja primeira
linha não bata com `^(feat|fix|refactor|test|docs|chore|decision): .+`.

## Branches e Pull Requests

- Um branch por linha de trabalho revisável — nome descritivo do que está sendo feito, não
  um identificador genérico.
- Nunca commitar direto no branch principal (`main`/`master`); toda mudança entra por PR.
- Uma PR corresponde a um branch — não empilhar trabalhos sem relação na mesma PR só porque
  foram feitos na mesma sessão.
- Título e descrição de PR seguem a mesma convenção de idioma das mensagens de commit —
  inglês (ver tabela "Idioma" acima) — porque o GitHub usa o título da PR como corpo do
  merge commit em `main`/`master` quando a PR é mesclada; uma PR escrita em português vaza
  pro histórico de commits nesse ponto exatamente como um `git commit -m` em português
  vazaria. (Erro cometido nas primeiras PRs da Iteração 4/5 — título/descrição em
  português — corrigido a partir da PR #17.)
- Quando o trabalho de uma PR já mesclada precisa continuar, reaproveitar o mesmo branch
  (recriado a partir do estado atual da branch principal) em vez de acumular branches novos
  a cada retomada — mantém o histórico de PRs correspondendo 1:1 a unidades de trabalho
  reais, não a sessões de chat.
- Essa regra não tem exceção por tipo de mudança nem por onde a sessão está rodando: uma
  continuação pequena, só de documentação, ou uma correção de CI ainda pertence à mesma
  linha de trabalho — não é motivo pra abrir um branch novo. E o mesmo vale trocando de
  ferramenta (Claude Code no navegador numa sessão, Claude Code local na outra): a
  continuidade é da linha de trabalho, não da sessão nem da ferramenta que a executa.
  Antes de criar um branch, checar se já existe um branch/PR abordando a mesma iteração e
  reaproveitar esse em vez de abrir outro.
- Projetos envolvendo páginas estáticas e deploy devem ter um branch novo após cada deploy com sucesso em produção.

## Rastreamento de trabalho via Issues

O board de Issues do GitHub é o lugar para visualizar o andamento do projeto:

| Nível SDD | GitHub |
|---|---|
| Uma feature (`specs/NN-NNN-slug/`, quando existir) | 1 Issue "guarda-chuva" (épico), corpo linkando `spec.md`/`plan.md` |
| Cada tarefa de `tasks.md` | Item de checklist da Issue-épico, ou Issue própria quando grande o suficiente para PR isolada |
| Tipo do commit (`feat`/`fix`/`refactor`/...) | Label da Issue — mesma taxonomia da tabela de commits acima |
| Iteração (`docs/context/iteracao-N.md`) | Label `iteration-N` da Issue — mesmo `NN` do prefixo de `specs/NN-NNN-slug/` |

O label `iteration-N` (não o número no título — retirado de lá na Iteração 5, ver
`docs/context/iteracao-5.md`) é a fonte da verdade para popular o campo numérico "Iteration"
de um GitHub Project, via script, ao adicionar a issue ao Project — sincronização ainda não
automatizada (decisão em aberto registrada em `docs/context/iteracao-5.md`).

Commits e PRs fecham a Issue correspondente com `Closes #N` na mensagem — mesma convenção
usada para referenciar uma decisão resolvida em `iteracao-N.md`/`plan.md`, só que apontando
para a Issue.

## Documentação viva por fase/iteração - Projetos de Software

- Antes de implementar uma fase de trabalho não trivial, registrar as decisões técnicas em
  aberto num documento de planejamento dessa fase (ex.: `docs/context/iteracao-N.md` — ver
  "Onde a documentação mora" abaixo). Funciona como uma ata que sobrevive a troca de
  contexto (nova sessão, outra pessoa assumindo o trabalho).
- Decisões são marcadas como resolvidas no próprio texto conforme são tomadas, preservando
  o raciocínio e as alternativas consideradas — não só a conclusão final. Isso evita ter que
  re-explicar o "por quê" de uma escolha mais tarde.
- Specs de comportamento (Gherkin/BDD) são escritas **antes** do código de implementação —
  funcionam como contrato de aceite, não como documentação a posteriori do que já foi
  construído.
- Contratos de API (ex.: OpenAPI) são escritos **antes** de existir o controller —
  implementação segue o contrato, e o build quebra se implementação e contrato saírem de
  sincronia (geração de código a partir do contrato, quando possível).
- Modelo de dados (diagrama entidade-relacionamento) é desenhado antes das entidades de
  código.

## Documentação viva por feature (spec-kit) — a partir da Iteração 5

- Cada feature nasce a partir de `templates/` (`spec-template.md`, `plan-template.md`,
  `tasks-template.md`, `data-model-template.md`, `contracts-template.md`) — copiar o
  template para `specs/NN-NNN-slug/` (`NN` = iteração, `NNN` = sequência dentro dela, ver
  "Adoção do spec-kit" acima), não escrever do zero.
- `spec.md` (requisitos, critérios de aceite — o QUÊ e POR QUÊ) referencia os `.feature`
  Gherkin correspondentes em vez de duplicar cenários em prosa.
- `plan.md` traduz `spec.md` em decisões técnicas e é validado contra este arquivo
  (`memory/constitution.md`).
- Contratos REST deste projeto continuam contract-first via `docs/openapi.yaml` (já
  estabelecido desde a Iteração 1) — `contracts/` dentro de `specs/NN-NNN-slug/` é para
  interfaces não-REST (ex.: CLI, contrato de mensagem de fila) ou para documentar o consumo
  de um serviço externo via cliente Feign, não para reproduzir o que já está no OpenAPI.
- `tasks.md` quebra `plan.md` em tarefas pequenas, espelhadas como Issues (ver
  "Rastreamento de trabalho via Issues" acima).

## Onde a documentação mora

Documentação de projeto tem plateias diferentes, e cada uma mora num lugar diferente:

- **`memory/constitution.md`** (este arquivo) — princípios e convenções, agnósticos de
  feature específica.
- **`specs/NN-NNN-slug/`** — a especificação viva de cada feature, a partir da Iteração 5
  (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `tasks.md`). Serve quem está
  desenvolvendo essa feature. Ver `specs/README.md`.
- **Documentação de produto/arquitetura** (`docs/roadmap.md`, `docs/diagrams/`,
  `docs/openapi.yaml`) — destinada a quem avalia ou usa o projeto de fora. Pronta pra ser
  publicada como está.
- **`docs/context/`** — diário de desenvolvimento por iteração
  (`docs/context/iteracao-N.md`). Não é documentação de produto — não deve ser publicada.
  Até a Iteração 5, este próprio arquivo (`memory/constitution.md`) também vivia aqui, em
  `docs/context/desenvolvimento.md` — removido nessa migração, conteúdo preservado só no
  caminho novo (ver "Nota histórica" no topo deste arquivo).

A separação existe porque as três primeiras categorias têm ciclo de vida e tom diferentes de
`docs/context/`: documentação de produto e de feature são escritas pra durar e ser lidas por
terceiros (ou por quem revisa a feature); `docs/context/iteracao-N.md` é escrito rápido,
durante o próprio trabalho, pra sobreviver a uma troca de sessão — cheio de "ainda não
decidido"/"validado nesta sessão", raciocínio capturado no calor da decisão, não prosa
revisada. Publicar as duas juntas confundiria as audiências.

**Exemplo usado neste projeto**: o site institucional que importa este repositório como
projeto de portfólio (Hugo + Docsy) varre `docs/` e publica todo `.md` que encontra, mas
pula por convenção qualquer diretório chamado `uml`, `backlog` ou `context`. `memory/` e
`specs/` ficam fora de `docs/` inteiramente, então nem entram na varredura — não precisam de
regra de exclusão própria.

| Caminho | Categoria | Publicado? |
|---|---|---|
| `docs/roadmap.md`, `docs/diagrams/der.md`, `docs/openapi.yaml` | Produto | Sim |
| `specs/NN-NNN-slug/*.md` | Especificação de feature | Não |
| `memory/constitution.md` (este arquivo) | Convenção/processo | Não |
| `docs/context/iteracao-N.md` | Diário de sessão | Não |

Um novo documento de retomada de contexto (ex. a próxima iteração) já nasce em
`docs/context/` — não é escrito na raiz de `docs/` pra ser movido depois.

## Diagramas Mermaid: validar a renderização de verdade

Um diagrama Mermaid com sintaxe que "parece certa" pode ainda assim falhar ao renderizar —
a gramática tem armadilhas que só aparecem no parser de verdade (ex.: um `;` dentro do texto
de uma `Note` termina a instrução ali, mesmo no meio da frase, e quebra o resto da linha).
Escrever o `.md` e assumir que vai renderizar no GitHub/Docsy sem checar é o mesmo erro que
"passou no teste, quebrou em produção" (ver seção "Testes" abaixo) — só que pra documentação.
Antes de considerar um diagrama pronto, renderizar de verdade contra um motor Mermaid real,
não só validar visualmente/mentalmente a sintaxe.

**Exemplo usado neste projeto**: `npx @mermaid-js/mermaid-cli` (mesmo motor que o GitHub
embute pra renderizar blocos ```` ```mermaid ```` em Markdown) rodando localmente reproduziu,
com a mensagem de erro idêntica, um erro de renderização visto no GitHub — e foi bisseccionando
contra esse motor real, não adivinhando pela mensagem de erro, que a causa foi isolada (era o
`;` citado acima, não a seta `->` que parecia mais óbvia à primeira vista). Em ambiente
rodando como root, precisa do Puppeteer com sandbox desabilitado
(`-p puppeteer.json` com `{"args": ["--no-sandbox", "--disable-setuid-sandbox"]}`), senão o
Chromium headless não sobe. Depois de escrever ou alterar um diagrama, vale extrair e
renderizar os blocos novos ou modificados antes de considerar a mudança pronta — o teste é
barato e pega esse tipo de erro antes de virar surpresa só visível depois de publicado.

## Nomenclatura de ambientes

Nomear ambientes pela **característica real da infraestrutura**, não por um rótulo genérico
tipo "dev"/"qa"/"test" — esses termos são ambíguos e mudam de significado de projeto pra
projeto, obrigando a reexplicar o que cada um significa aqui. Preferir um nome que já
descreve a própria restrição ou característica do ambiente.

Exemplo usado neste projeto:

| Nome | O que descreve |
|---|---|
| `sandbox` | Sem infraestrutura externa disponível (ex.: rodando isolado, sem Docker) — usa um banco embarcado no lugar do banco real |
| `docker` | Infraestrutura real via containers, local (`docker-compose`) ou CI — descartável |
| `staging` | Pré-produção: infraestrutura e dados reais, mas isolados de produção |
| `production` | Produção |

Cada ambiente tem seu próprio arquivo de configuração autodescritivo — um comentário no
topo explicando por que aquele ambiente existe, quem o opera e quais restrições ele impõe
(ex.: "este ambiente não roda migração de schema sozinho, uma equipe separada faz isso à
mão").

## Testes: preferir real a fake sempre que der

Sempre que uma dependência externa tiver como rodar localmente/de verdade em teste
automatizado (ex.: um captcha open-source auto-hospedado, sessão persistida em banco real,
migrations de schema reais), preferir isso a um mock/stub. Um teste que passa contra uma
simulação que não bate com o comportamento real do sistema dá falsa confiança — "passou no
teste, quebrou em produção".

Usar stub/fake só quando a alternativa real não existe ou não é viável no ambiente de teste
(ex.: envio de e-mail de verdade). Mesmo nesses casos, o stub registra o que faria de
verdade (ex.: grava numa tabela o que seria enviado) para que o teste possa checar por
asserção, em vez de só confiar que o método foi chamado.

Não executar testes no merge que gerem cobrança por uso de API de inteligência artifical (Gemini).

## Dados de teste: Object Mother + Test Data Builder - Projetos de Software

Fábricas de dados de teste ("Mother") retornam um objeto/builder já pré-preenchido com
dados **válidos** por padrão — o ponto de partida de qualquer cenário. Cenários que testam
uma variação **inválida** de um campo específico partem desse builder válido e sobrescrevem
só o campo sob teste, mantendo os demais válidos. Isso espelha a estrutura de uma tabela de
`Examples` do Gherkin, onde cada linha varia um campo por vez.

## CI e cobertura de testes

- **A suíte de testes roda em CI contra infraestrutura o mais real possível** (a mesma
  lógica da seção "Testes: preferir real a fake" acima, aplicada ao pipeline), não contra
  atalhos usados só no dia a dia local — reduz a chance de "passou no CI, quebrou em
  produção" por uma diferença de ambiente.
- **Cobertura de linha tem um piso obrigatório**, quando a stack tiver ferramenta de
  cobertura — não é só um número informativo, é uma condição de build passar. Código
  gerado (ex.: interfaces/DTOs de um gerador de contrato) fica de fora da contagem — não é
  código que a equipe escreve ou mantém, então não deveria puxar a média pra baixo nem pra
  cima.
- **O check de CI é obrigatório antes de mesclar** (branch protection do GitHub no branch
  principal, exigindo esse status check) — quebrar a suíte ou cair abaixo do piso de
  cobertura bloqueia o merge, não é um aviso.
- **A cobertura aparece como comentário na própria PR**, quando a ferramenta suportar,
  atualizado a cada push, mesmo quando o build falha por causa dela — assim dá pra ver o
  número exato sem precisar abrir os logs do CI.
- Cada stack usada no projeto documenta sua própria subseção aqui, com as ferramentas
  concretas (executor de teste, cobertura, gate de merge) — ver exemplo abaixo.

**Exemplo usado neste projeto:**

### Java

- A suíte roda contra o perfil `docker` (Postgres de verdade, não H2), não contra o perfil
  de sandbox usado no dia a dia.
- Piso de cobertura via JaCoCo (`mvn verify`).
- Cobertura comentada na PR a cada push via `madrapps/jacoco-report`.

### Python

- Os testes do script Python devem rodar com sucesso antes do merge. Testes dependentes da
  API do Gemini não são executados para diminuir custos. Não há cobertura de testes
  definida.
