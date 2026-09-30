# Spec: Harness Spring Boot de testes para o pipeline de e-mail

**Status:** rascunho
**Issue:** ainda não criada
**Iteração:** iteration-5 (escrita agora; escopo de implementação adiado — ver "Decisões em
aberto")

## Resumo

Uma aplicação Spring Boot separada, só de teste — nunca embutida em `app/` ou
`email-lambda/` — que consome a fila de comando de e-mail e invoca o artefato real do
`email-lambda` (nunca reimplementa o envio), guardando o que observou pra verificação. Fecha,
ao mesmo tempo, duas lacunas já registradas: tira código de teste de dentro de um módulo de
produção (`EmailQueuePoller`, Issue #84) e dá uma forma repetível de verificar o pipeline de
e-mail de ponta a ponta, coisa que a spec 05-021 documentou como nunca verificado neste
ambiente de implementação por falta de Docker.

## Motivação

Duas lacunas do estado atual, que essa spec resolve junta:

1. **`EmailQueuePoller` mistura código de teste dentro do artefato de produção do
   `email-lambda/`** (spec 05-021) — só ativo atrás de `email.dev-poller.enabled`, mas o
   código ainda ship com o jar real. Isso já foi reconhecido como dívida e registrado na
   [Issue #84](https://github.com/lalgarve/jogo-acoes/issues/84) (nova regra em
   `memory/constitution.md`, "Código de teste/dev nunca dentro da aplicação") — correção
   adiada pra Etapa 2, mas essa spec já pode nascer com o desenho certo (harness separado),
   em vez de só mover o poller de lugar sem repensar o resto.
2. **Verificação manual de ponta a ponta do pipeline nunca foi possível** (spec 05-021,
   `tasks.md`, registro de T010) por falta de Docker no ambiente de implementação — e mesmo
   com Docker, o jeito documentado hoje (conferir que a fila esvaziou, ou o endpoint de debug
   do LocalStack pro SES, `GET /_aws/ses`) não confirma que o `EmailSendHandler` de verdade
   rodou sem erro, nem guarda um histórico consultável entre execuções de teste.

Um harness que consome a fila e invoca o artefato real do `email-lambda` (não uma
reimplementação do envio) resolve os dois: o consumo sai do `email-lambda/` para seu próprio
módulo, e o resultado de cada invocação fica registrado, verificável.

## Cenários (comportamento esperado)

Não aplicável — infraestrutura/ferramenta de teste, sem `.feature` novo (mesmo padrão de
specs 05-014/05-020/05-021).

## Requisitos funcionais

- Nova aplicação Spring Boot, módulo próprio (nunca dentro de `app/` nem `email-lambda/`) —
  mesmo padrão de `blackbox-proxy/` (spec 05-020): existe só para teste, nunca roda em
  `staging`/`production`.
- Consome a mesma fila de comando de e-mail que `app/` já publica (`SqsEmailSender`) e que
  `email-lambda` consumiria em produção via *event source mapping* real da AWS.
- Para cada mensagem consumida, invoca o artefato real do `email-lambda` — nunca reimplementa
  o envio (SES) nem duplica a lógica de `EmailSendHandler`. O mecanismo exato de invocação
  (chamada direta à API Lambda do LocalStack, ou deixar o próprio LocalStack fazer a ponte
  fila→Lambda) é uma decisão técnica de `plan.md`, não desta spec.
- Guarda um registro consultável do que observou por mensagem — ao menos: corpo recebido da
  fila, resultado da invocação (sucesso/erro), quando aconteceu. Formato de armazenamento
  (memória, tabela própria) é decisão de `plan.md`.
- Expõe uma forma de consultar esse registro (endpoint HTTP, mesmo espírito do
  `BlackboxController` atual, mas fora de `app/`).

## Requisitos não-funcionais

- **Nunca roda em `staging`/`production`** — não entra em `docker-compose.yml` base, só numa
  sobreposição opcional (mesmo espírito de `docker-compose.blackbox.yml`/
  `docker-compose.email-lambda.yml`).
- **Sem autenticação própria** — mesmo raciocínio já aceito para `BlackboxController`/
  `blackbox-proxy/`: confinado a ambiente de teste descartável, nunca exposto de verdade.
- **Estado efêmero aceitável** — não precisa sobreviver a reinício do processo; é ferramenta de
  verificação manual/exploratória, não um sistema de auditoria permanente.

## Fora de escopo

- Reimplementar a lógica de envio de e-mail (SES) — o harness sempre delega ao artefato real
  do `email-lambda`, nunca manda e-mail ele mesmo.
- O consumidor real de bounce/complaint no `app/` (decisão 10, `docs/context/iteracao-4.md`) —
  ainda não existe. Este harness pode, no futuro, publicar mensagens sintéticas na fila de
  eventos pra testar esse consumidor, mas isso só faz sentido depois que o consumidor existir —
  fica para uma spec própria, não esta.
- Remover o `EmailQueuePoller` do `email-lambda/` — rastreado na Issue #84 (Etapa 2); esta spec
  cria a alternativa, mas a remoção em si é acompanhada lá, podendo acontecer em separado.
- Qualquer invocação contra a AWS real — sempre contra o `email-lambda` rodando/deployado no
  LocalStack.

## Decisões em aberto

1. **Como o harness invoca o `email-lambda`**: chamando a API do serviço `lambda` do
   LocalStack diretamente (harness consome a fila via `@SqsListener` e faz um `Invoke` por
   mensagem), ou configurando um *event source mapping* real SQS→Lambda dentro do próprio
   LocalStack (o harness nem precisaria consumir a fila ele mesmo — só faria o "deploy" da
   função e leria o resultado depois)? Muda bastante o desenho; fica pra `plan.md`, com
   verificação de que o LocalStack (edição usada neste projeto) realmente suporta a opção
   escolhida antes de comprometer com ela.
2. **Onde/como fica o registro do que foi observado**: em memória (mais simples, mesmo
   espírito de `DeviceHeaderStore` em `blackbox-proxy/`) ou numa tabela própria (sobrevive a
   consultas mais elaboradas, mas exige um banco)?
3. **Nome do módulo/aplicação**.
4. Se este harness deve já nascer preparado para publicar mensagens sintéticas de bounce/
   complaint (mesmo sem consumidor do outro lado ainda) ou se isso fica inteiramente fora até
   uma spec futura — ver "Fora de escopo".
