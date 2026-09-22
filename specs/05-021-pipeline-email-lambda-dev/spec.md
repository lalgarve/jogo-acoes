# Spec: Pipeline de e-mail ponta a ponta em desenvolvimento (`email-lambda` como consumidor vivo)

**Status:** implementado
**Issue:** [#78](https://github.com/lalgarve/jogo-acoes/issues/78)
**Iteração:** iteration-5

## Resumo

No `docker-compose` de desenvolvimento, `email-lambda` passa a consumir de verdade a fila que
`app` já publica — hoje ele existe, é testado, mas nunca roda como processo vivo fora dos
testes do próprio módulo. Uma mensagem publicada em `docker compose up` passa a chegar ao SES
simulado do LocalStack, fechando o pipeline `app → fila → email-lambda → SES simulado` numa
sessão normal de desenvolvimento — sem tocar AWS real.

## Motivação

O `docker-compose.yml` já sobe `app` publicando na fila `jogo-acoes-email-commands`
(`SqsEmailSender`) e um `localstack` só com SQS habilitado. `email-lambda` — o módulo que
consome essa fila e chama o SES — nunca aparece como serviço nesse arquivo; ele só é exercitado
pelo seu próprio teste (`EmailSendHandlerTest`, via Quarkus Dev Services, que sobe um LocalStack
descartável e isolado só para aquele teste). O contrato entre os dois lados (nome da fila,
formato de `EmailMessage`) já está alinhado desde a Iteração 4 (`docs/context/iteracao-4.md`,
decisão 1) — o que falta é só ligar o consumidor à fila viva.

Sem isso, qualquer mensagem publicada durante uma sessão normal de `docker compose up` fica
parada na fila para sempre — não há como observar, numa sessão manual de desenvolvimento, o
pipeline de e-mail funcionando de ponta a ponta. Isso é diferente da implantação real na AWS
(Lambda disparada pela fila via *event source mapping*), que continua bloqueada por não haver
conta AWS ainda (`docs/context/iteracao-4.md`, decisão 7) — essa spec não depende disso nem
tenta resolvê-lo; é puramente sobre o ambiente local.

## Cenários (comportamento esperado)

Não aplicável — infraestrutura/configuração de ambiente de desenvolvimento, sem `.feature`
novo nem mudança de comportamento de negócio (mesmo padrão das specs 05-006/05-007/05-011/
05-014).

## Requisitos funcionais

- `email-lambda` ganha um modo de execução "poller de desenvolvimento": ao subir como processo
  normal (não dentro do runtime da AWS Lambda), se explicitamente habilitado por configuração,
  consome mensagens da fila (`receiveMessage` com *long polling*) e chama o mesmo handler já
  usado pela Lambda real (`EmailSendHandler.handleRequest`) para cada uma — sem duplicar a
  lógica de envio.
- Esse modo é desabilitado por padrão — só fica ativo quando uma variável de ambiente dedicada
  é definida. O artefato de implantação real (imagem nativa GraalVM, quando algum dia houver
  conta AWS) nunca define essa variável, então o comportamento da Lambda de verdade não muda em
  nada por esta spec.
- Uma mensagem processada com sucesso é removida da fila; uma que falha (exceção) não é
  removida — mesmo raciocínio já decidido para a Lambda real (`docs/context/iteracao-4.md`:
  "deliberadamente sem lógica de retry", a própria fila SQS reentrega via seu *redrive
  policy*).
- `docker-compose` ganha uma sobreposição dedicada (mesmo padrão de
  `docker-compose.blackbox.yml`, spec 05-014) que adiciona o serviço `email-lambda` — sem
  alterar o comportamento de `docker-compose up` sem essa sobreposição.
- O LocalStack usado pelo `docker-compose.yml` passa a emular também SES (além de SQS, já
  emulado hoje), e o remetente configurado (`email.sender-address`,
  `no-reply@jogo-acoes.example`) é verificado automaticamente ao subir — LocalStack, como o SES
  real, rejeita envio de um remetente não verificado (achado já documentado em
  `docs/context/iteracao-4.md` a partir do teste do próprio módulo).
- `README.md` ganha uma seção curta explicando como subir esse pipeline completo
  (`docker compose -f docker-compose.yml -f docker-compose.email-lambda.yml up`) e como
  confirmar que uma mensagem foi de fato processada.

## Requisitos não-funcionais

- **Nunca ativo em `mvn test`/CI.** O modo poller de desenvolvimento é código adicional no
  módulo `email-lambda`, mas desligado por padrão — a suíte existente (`EmailSendHandlerTest`,
  via Dev Services) continua passando exatamente como hoje, sem depender dele.
- **Nunca ativo no artefato de implantação real** (build nativo `-Pnative`) — só a sobreposição
  `docker-compose.email-lambda.yml` define a variável de ambiente que liga o modo poller;
  nenhum outro caminho de build/execução a define.
- **Opt-in**, como o ambiente `blackbox` (spec 05-014): `docker compose up` sem a sobreposição
  continua se comportando exatamente como hoje — `email-lambda` não sobe, mensagens continuam
  só se acumulando na fila (comportamento inalterado para quem não pediu esse pipeline).

## Fora de escopo

- Qualquer infraestrutura como código (SAM/CDK/Terraform) para ligar a fila à Lambda real na
  AWS — continua bloqueado por não haver conta AWS (`docs/context/iteracao-4.md`, decisão 7),
  sem relação com este ambiente local.
- Deduplicação via DynamoDB — mencionada como intenção original do projeto, mas nunca
  implementada em `EmailSendHandler`; fora do escopo desta spec, que só conecta o que já existe.
- Qualquer forma de inspecionar visualmente a fila/DLQ (equivalente a um "Adminer para SQS") —
  fica para quando houver essa necessidade; hoje a confirmação de que uma mensagem foi
  processada é indireta (ver `plan.md`).
- Build/imagem nativa (GraalVM) para este novo modo de execução — roda em modo JVM, mesmo
  raciocínio já usado para CI (`docs/context/iteracao-4.md`: build nativo é manual, não entra em
  CI nem em ambientes que não precisam do cold-start baixo).
- Mudar o comportamento de `EmailSendHandler`/o contrato da fila — o poller de desenvolvimento é
  só um jeito novo de invocar o handler existente, não uma reimplementação dele.

## Decisões em aberto

Nenhuma — mecanismo do poller de desenvolvimento, propriedades de configuração do
`quarkus-amazon-sqs`/`quarkus-amazon-ses`, verificação do remetente no LocalStack e estrutura
da sobreposição do `docker-compose` resolvidos em `plan.md`.
