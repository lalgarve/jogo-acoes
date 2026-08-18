# Como desenvolvemos software

Este documento descreve o **fluxo de trabalho e as convenções de nomenclatura** usados
neste projeto — não as decisões técnicas específicas dele (essas ficam em
[`roadmap.md`](roadmap.md) e nos documentos de cada iteração). A ideia é que este arquivo
seja **agnóstico de projeto**: pode ser copiado como ponto de partida para outro repositório
sem precisar reexplicar o processo do zero.

## Idioma

| O quê | Idioma |
|---|---|
| Código: identificadores, comentários, nomes de arquivo de código | Inglês |
| Mensagens de commit | Inglês |
| Especificações de comportamento (Gherkin, `.feature`) | Inglês |
| Documentação de projeto (`README.md`, `docs/*.md`) | Português |

Código em inglês porque é o padrão do ecossistema (bibliotecas, mensagens de erro,
convenções da linguagem). Documentação em português porque é o idioma da equipe — não faz
sentido traduzir decisões e raciocínio para um idioma que não é o nativo de quem escreve e
lê.

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
   decision in docs/iteracao-3.md").

Exemplo real deste projeto:

```
feat: add ALTCHA for captcha, resolving the stub decision (Iteração 3)

Self-hosted proof-of-work captcha instead of a third-party service or a fake
stub — since the challenge/solution round-trip is entirely local, the
captcha pass/fail scenarios can be tested for real: solve the challenge
correctly to pass, submit a tampered solution to fail.

AltchaSmokeTest proves the create/solve/verify round-trip actually works
against this project's dependency versions (2/2 passing).

Resolves the captcha-stub open decision in docs/iteracao-3.md.
```

Commits pequenos e focados em uma mudança revisável de cada vez — evitar juntar mudanças
sem relação numa mesma mensagem.

## Branches e Pull Requests

- Um branch por linha de trabalho revisável — nome descritivo do que está sendo feito, não
  um identificador genérico.
- Nunca commitar direto no branch principal (`main`/`master`); toda mudança entra por PR.
- Uma PR corresponde a um branch — não empilhar trabalhos sem relação na mesma PR só porque
  foram feitos na mesma sessão.
- Quando o trabalho de uma PR já mesclada precisa continuar, reaproveitar o mesmo branch
  (recriado a partir do estado atual da branch principal) em vez de acumular branches novos
  a cada retomada — mantém o histórico de PRs correspondendo 1:1 a unidades de trabalho
  reais, não a sessões de chat.

## Documentação viva por fase/iteração

- Antes de implementar uma fase de trabalho não trivial, registrar as decisões técnicas em
  aberto num documento de planejamento dessa fase (ex.: `docs/iteracao-N.md`). Funciona como
  uma ata que sobrevive a troca de contexto (nova sessão, outra pessoa assumindo o
  trabalho).
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

## Dados de teste: Object Mother + Test Data Builder

Fábricas de dados de teste ("Mother") retornam um objeto/builder já pré-preenchido com
dados **válidos** por padrão — o ponto de partida de qualquer cenário. Cenários que testam
uma variação **inválida** de um campo específico partem desse builder válido e sobrescrevem
só o campo sob teste, mantendo os demais válidos. Isso espelha a estrutura de uma tabela de
`Examples` do Gherkin, onde cada linha varia um campo por vez.

## CI e cobertura de testes

- **A suíte de testes roda em CI contra infraestrutura real** (perfil `docker` deste
  projeto — Postgres de verdade, não H2), não contra o perfil de sandbox usado no dia a dia
  — reduz a chance de "passou no CI, quebrou em produção" por uma diferença de banco.
- **Cobertura de linha tem um piso obrigatório** (JaCoCo, `mvn verify`) que quebra o build
  se ficar abaixo do limite — não é só um número informativo, é uma condição de build
  passar. Código gerado (ex.: interfaces/DTOs de um gerador de OpenAPI) fica de fora da
  contagem — não é código que a equipe escreve ou mantém, então não deveria puxar a média
  pra baixo nem pra cima.
- **O check de CI é obrigatório antes de mesclar** (branch protection do GitHub no branch
  principal, exigindo esse status check) — quebrar a suíte ou cair abaixo do piso de
  cobertura bloqueia o merge, não é um aviso.
- **A cobertura aparece como comentário na própria PR**, atualizado a cada push, mesmo
  quando o build falha por causa dela — assim dá pra ver o número exato sem precisar abrir
  os logs do CI.
