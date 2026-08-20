# Iteração 4 — Planejamento técnico

Este documento registra as decisões técnicas da Iteração 4 (ver objetivo geral e o diagrama
de arquitetura de referência em [`roadmap.md`](roadmap.md)) antes da implementação, no mesmo
espírito de [`iteracao-2.md`](iteracao-2.md)/[`iteracao-3.md`](iteracao-3.md): um resumo pra
servir de base caso a conversa precise mudar de contexto. **Nada aqui foi implementado
ainda — é o plano em discussão.**

**Aviso herdado do `roadmap.md`:** as iterações 4 e 5 foram inferidas a partir de um diagrama
de arquitetura resumido, sem o restante da conversa que o originou. Os detalhes de contrato
de mensagem, retries e observabilidade abaixo são um ponto de partida razoável, não uma
decisão fechada — cada um precisa ser confirmado (ou corrigido) conscientemente antes de
implementar, não só herdado do roadmap por omissão.

## Objetivo

Substituir o stub de e-mail da Iteração 3 (`StubEmailSender`, que só grava em `sent_email`)
pela arquitetura assíncrona real: o sistema principal publica uma mensagem numa fila Amazon
SQS em vez de enviar e-mail diretamente; uma AWS Lambda consome a fila e dispara o envio via
Amazon SES.

## O que já existe (herdado da Iteração 3)

- **`EmailSender`** (`io.deployo.jogoacoes.email`): interface única,
  `send(Long userId, String email, String link, EmailTemplate template)`, chamada pelos
  quatro serviços de negócio (`CompetitionService`, `EntryRequestService`, `LoginService`,
  `PlayerManagementService`). Trocar a implementação (de `StubEmailSender` para uma que
  publica na fila) não deveria exigir mudança nesses serviços.
- **`StubEmailSender`**: única implementação hoje — não envia nada de verdade, só grava uma
  linha em `sent_email` (entidade `SentEmail`) com e-mail, link e template.
- **`EmailTemplate`** (enum): `INVITE`, `REGISTRATION_LINK`, `LOGIN_LINK` — os três tipos de
  e-mail que as features de hoje disparam. Ainda é só um rótulo — a geração do texto de
  verdade (Thymeleaf) não existe, é mencionada no código como "iteração futura" mas não está
  no escopo do roadmap para a 4 nem a 5.
- **`sent_email`**: tabela imutável (`jogo_acoes_app` só tem `SELECT`/`INSERT`), mesma regra
  de `LOG`. Continua existindo mesmo depois desta iteração — é o registro de "o que foi
  enviado", independente de a entrega ser síncrona (stub) ou assíncrona (fila real).

## Escopo (do `roadmap.md`)

- Definir o contrato da mensagem publicada na fila (tipo de e-mail, destinatário, dados de
  template, id de correlação).
- Sistema principal: nova implementação de `EmailSender` que publica na fila Amazon SQS em
  vez de enviar diretamente (ou, aqui, em vez de só gravar em `sent_email`).
- AWS Lambda consumindo a fila e disparando o envio via Amazon SES.
- Fila de dead-letter (DLQ) para mensagens que falharem após as tentativas configuradas.
- **SES Event Publishing** (*Configuration Set* com destino SNS) para os eventos de
  `Bounce`/`Complaint`/`Delivery`, com uma fila SQS própria assinando esse tópico —
  resolvido nesta sessão que isso é uma peça distinta da fila de comando (ver decisão 10).
- Provisionamento da infraestrutura AWS (SQS, Lambda, SES) — de preferência como código
  (Terraform/CDK) para ser reproduzível.
- IAM com privilégio mínimo: o sistema principal só pode publicar na fila; a Lambda só pode
  consumir a fila e usar o SES.
- Verificação de domínio/remetente no SES e saída do modo sandbox (necessário para enviar a
  destinatários não verificados em produção).

**Fora de escopo (fica para a Iteração 5):** métricas/alertas, observabilidade do pipeline
(profundidade da fila, erros/retries da Lambda, *agregação*/alerta sobre taxa de
bounce/complaint do SES). A *captação* bruta dos eventos individuais de bounce/complaint via
SES Event Publishing é desta iteração — ver decisão 10; o que fica para a 5 é olhar pra esses
dados de forma agregada e alertar sobre eles.

## Decisões a tomar antes de implementar

1. ~~Contrato da mensagem da fila.~~ — **resolvido.** O corpo/assunto do e-mail já vão
   *renderizados* na mensagem, não os ingredientes (`EmailTemplate` + `link`) para renderizar
   depois. Isso torna o contrato estável contra qualquer mudança futura no motor de
   templates (texto simples agora, Thymeleaf depois — ver nota abaixo) sem nunca precisar
   tocar na fila outra vez, e reduz a Lambda a um worker genérico ("manda este texto pra este
   endereço"), sem nenhum conhecimento do domínio da aplicação. Como `sent_email` já é
   gravada no sistema principal no momento da publicação (decisão 9 abaixo), `userId`
   também não precisa cruzar a fila — só serve pro FK local.

   ```json
   {
     "schemaVersion": "1",
     "correlationId": "uuid",
     "recipientEmail": "...",
     "subject": "...",
     "body": "..."
   }
   ```

   `schemaVersion` resolve o versionamento: a Lambda pode ramificar por versão em vez de
   exigir corte seco se o contrato mudar de novo. `correlationId` amarra a mensagem de volta
   ao `sent_email` e vira *message tag* do `SES.SendEmail` pra correlacionar os eventos da
   decisão 10.

   **Nota — geração do corpo/assunto:** como a geração de texto via Thymeleaf continua fora
   do escopo desta iteração (e da 5), quem produz `subject`/`body` antes de publicar é uma
   renderização simples e interna ao sistema principal (ex.: um `switch` por `EmailTemplate`
   devolvendo texto puro) — não adianta esse trabalho, só empurra o ponto onde
   `EmailTemplate`/`link` deixam de ser visíveis pra fora do processo: cruzavam a fila antes,
   agora só alimentam essa renderização interna. Trocar por um motor de template de verdade
   depois não muda o contrato da fila.
2. ~~Biblioteca/SDK para falar com SQS a partir do Spring Boot.~~ — **resolvido: Spring Cloud
   AWS** (`spring-cloud-aws-starter-sqs`), não AWS SDK v2 puro. Verificado nesta sessão: a
   versão 4.0.0 é compatível com Spring Boot 4.x (lançada em janeiro/2026) — o risco de
   incompatibilidade que motivava a dúvida não se aplica mais. Dá `SqsTemplate` pra publicar
   (bem mais simples que `SqsClient` cru) e deixa a porta aberta pra `@SqsListener` se o
   sistema principal também acabar consumindo a fila de eventos da decisão 10.
3. ~~Como testar sem AWS real.~~ — **resolvido, mesmo padrão do Postgres real (Iterações 2 e
   3).** LocalStack via `docker-compose` (serviço novo, ao lado do `db`), exercitado só no
   profile `docker`/CI — o `sandbox` deste agente continua sem depender de rede/Docker, então
   `StubEmailSender` permanece a implementação ativa lá. A implementação real (fila) só é
   validada de ponta a ponta em CI, onde o Docker existe — mesma lacuna já documentada e
   aceita nas iterações anteriores (o que roda aqui é só raciocínio + `mvn verify` local
   contra `sandbox`, a confirmação de verdade vem do primeiro workflow real no GitHub
   Actions).
4. **Retry e política da DLQ.** Quantas tentativas antes de cair na dead-letter queue,
   *backoff* entre elas — o roadmap menciona a DLQ mas não define esses números.
5. **Provisionamento de infraestrutura.** Terraform vs. AWS CDK vs. criação manual só
   documentada — o roadmap prefere "de preferência como código", sem fechar qual ferramenta.
   Também decide onde esse código de infra mora (este repositório, um repositório separado).
6. **IAM de privilégio mínimo.** Papéis/políticas exatos: o sistema principal só publica na
   fila (`sqs:SendMessage`), a Lambda só consome (`sqs:ReceiveMessage`/`DeleteMessage`) e usa
   o SES (`ses:SendEmail`) — desenhar as policies JSON de verdade, não só o princípio.

   **Credenciais AWS no CI/deploy (GitHub Actions):** o workflow de CI (`.github/workflows/
   ci.yml`, já existente desde a Iteração 3) e um futuro passo de deploy/provisionamento da
   Lambda vão precisar de credenciais AWS. Duas opções:
   - **GitHub Actions secrets** (`Settings → Secrets and variables → Actions`): *write-only*
     — depois de salvo, ninguém (nem admin do repositório) consegue ler o valor de novo pela
     UI/API, só sobrescrever ou apagar; mascarado automaticamente nos logs do workflow se
     aparecer na saída de um step (com ressalva: a mascara não é infalível pra segredo
     multi-linha ou reescrito em outro encoding). *Environment secrets* (em vez de
     repository-level) permitem regras de proteção adicionais — *required reviewers*,
     restrição por branch de deploy.
   - **OIDC (recomendado)**: em vez de guardar uma *access key* AWS de longo prazo como
     secret, o GitHub Actions assume uma IAM role via token de curta duração emitido pra cada
     execução do workflow — não existe credencial fixa armazenada em lugar nenhum pra vazar.
     Requer configurar um *identity provider* OIDC do GitHub na conta AWS e uma *trust
     policy* na role restringindo por repositório/branch. Mais seguro que secret nesse caso
     específico (CI/deploy), mas secrets do GitHub continuam necessários para o que não é
     credencial AWS assumível por role (ex. segredos de aplicação, se algum surgir).
7. **Verificação de domínio/remetente no SES e saída do sandbox mode.** Requer acesso real à
7. **Verificação de domínio/remetente no SES e saída do sandbox mode.** Requer acesso real à
   conta AWS do projeto — não é algo verificável dentro de uma sessão de agente; precisa de
   decisão/execução de quem tem essas credenciais.
8. **Idempotência do consumidor.** SQS entrega "at-least-once" — a mesma mensagem pode chegar
   duplicada na Lambda. Precisa decidir se isso importa aqui (reenviar o mesmo e-mail
   duas vezes é um problema real de produto?) e, se sim, como deduplicar (id de correlação
   como chave de idempotência, checagem contra `sent_email` antes de enviar, etc.).
9. ~~Onde a `sent_email` é gravada~~ — **resolvido, revisado nesta sessão.** A ideia inicial
   (uma segunda fila onde a própria Lambda reporta sucesso/falha logo após chamar o SES) foi
   descartada: a chamada `SES.SendEmail` retornar sucesso só significa que a AWS *aceitou*
   a mensagem para tentativa de entrega, não que ela foi entregue — bounce (hard/soft) e
   complaint acontecem depois, de forma assíncrona, do lado do servidor de e-mail do
   destinatário, e a Lambda não tem como saber disso no momento do envio. "Sucesso" reportado
   pela própria Lambda não diria nada de útil sobre entrega. A peça que resolve isso de
   verdade é outra — ver decisão 10. `sent_email` continua sendo gravada no sistema principal
   no momento de publicar (mesmo padrão atual, otimista) — o que muda é que ela deixa de ser
   a fonte da verdade sobre o resultado da entrega; isso passa a viver em `EMAIL_EVENT`
   (decisão 10).
10. **Como saber de bounce/complaint (novo, decorre da revisão da decisão 9).** Falha real de
    entrega não é reportada pela Lambda — é o próprio SES que a reporta, de forma assíncrona
    e desacoplada da chamada de envio original, via **SES Event Publishing**: um
    *Configuration Set* associado ao envio, configurado para publicar eventos (`Send`,
    `Delivery`, `Bounce`, `Complaint`, `Reject`, `DeliveryDelay`) num tópico SNS. Uma fila SQS
    própria (distinta da fila de comando) assina esse tópico, e algum consumidor (outra
    Lambda, ou um listener no próprio sistema principal) processa esses eventos. Falta
    decidir:
    - Quem consome essa fila de eventos — uma segunda Lambda simples que só grava no banco,
      ou o sistema principal direto (ex. `@SqsListener` via Spring Cloud AWS, mesma
      biblioteca da decisão 2).
    - ~~Modelo de dados~~ — **resolvido.** Tabela nova `EMAIL_EVENT` (insert-only, mesmo
      padrão de `LOG` e `SENT_EMAIL`, `jogo_acoes_app` só com `SELECT`/`INSERT`):
      `id` PK, `sent_email_id` (FK pra `SENT_EMAIL`), `event_type` (enum:
      `SEND`/`DELIVERY`/`BOUNCE`/`COMPLAINT`/`REJECT`/`DELIVERY_DELAY` — os mesmos tipos que o
      *Configuration Set* publica), `occurred_at`, `detail` (nullable — motivo do bounce,
      por exemplo). Uma tabela em vez de mutar um campo de status em `sent_email`, já que um
      mesmo envio pode ter múltiplos eventos ao longo do tempo (envio aceito, depois bounce
      horas depois) — preserva a disciplina de tabelas imutáveis já usada no projeto.
    - Como associar o evento do SES de volta ao `sent_email`/`id de correlação` originais —
      o SES permite anexar *message tags* personalizadas ao `SendEmail`, então o
      `correlationId` da decisão 1 vai como tag pra voltar no evento e resolver o
      `sent_email_id` de `EMAIL_EVENT`.
    - Se a agregação/alerta sobre esses eventos é desta iteração ou só a captação bruta —
      o roadmap já classifica "taxa de bounce/complaint" como observabilidade da Iteração 5;
      o que esta iteração precisa decidir é só se os eventos brutos já são capturados e
      persistidos aqui (dado que a infraestrutura SNS/SQS é criada agora), mesmo que a
      agregação/alerta fique para depois.

## Ordem sugerida de discussão

As decisões 1–2 (contrato da mensagem, SDK) são a base de tudo o resto e não dependem de
acesso à AWS real — dá para avançar nelas dentro de uma sessão de agente. O modelo de dados
da decisão 10 (`EMAIL_EVENT`) também não depende de AWS e pode ser desenhado junto. As
decisões 5–7 e a parte de infraestrutura da 10 (Configuration Set, tópico SNS, fila de
eventos) dependem de acesso à conta AWS do projeto, fora do alcance das ferramentas
disponíveis aqui — ver `docs/desenvolvimento.md` para o padrão já usado nas iterações
anteriores de documentar claramente o que foi validado de verdade vs. o que ficou por
raciocínio/pendente de uma sessão local ou de quem tem as credenciais.

## Catálogo de templates de e-mail (Thymeleaf)

O número de `EmailTemplate` existente (`INVITE`, `REGISTRATION_LINK`, `LOGIN_LINK`) foi
decidido na Iteração 3 só para viabilizar o stub — não veio de uma revisão dos quatro
`.feature` pra ver se o conjunto realmente cobre (e não duplica) os casos de negócio. Essa
revisão foi feita nesta sessão, ponto a ponto pelos quatro *call sites* de `emailSender.send`
e pelos *steps* de teste que os verificam:

| Template | Quando é usado | Sabe o nome? | Contexto de competição? |
|---|---|---|---|
| `INVITE` | Admin convida um e-mail sem `User` registrado pra uma competição privada | Não — `Participation` só guarda e-mail até o registro terminar | Sim — nome da competição |
| `REGISTRATION_LINK` | Pedido de entrada espontâneo numa competição pública, sem `User` registrado ainda | Não | Sim — nome da competição |
| `LOGIN_LINK` | Destinatário já tem `User` registrado | Sim | Depende do caso — ver abaixo |

**Achado real, confirmado no código**: o cenário "Registered but not logged in player
requests entry" (`request_competition_entry.feature`) diz "sends the link to **finish
registration**", mas o *step* que o verifica (`RequestCompetitionEntrySteps`) já afirma
`EmailTemplate.LOGIN_LINK`, não `REGISTRATION_LINK` — a frase do Gherkin é fraseado genérico
reaproveitado entre os três cenários de pedido de entrada, não uma descrição literal do
e-mail. O diferencial de negócio real é "**personalized with the name**", testado
explicitamente só nesse cenário.

**Decisão**: manter os 3 valores de `EmailTemplate` — cobrem os quatro `.feature` sem
precisar crescer — mas **5 arquivos físicos de template**, não 3. Revisado duas vezes depois
da primeira versão desta seção:

1. **Nenhum template pode ter lógica** (`th:if`/`th:unless`/ternário) — só substituição
   direta de variável. Cada texto precisa ser editável por qualquer pessoa sem entender
   ramificação nenhuma, e um único arquivo tem que dar conta de qualquer pedido de edição
   sobre aquele e-mail específico.
2. **A origem do e-mail (convite do admin vs. pedido espontâneo do jogador) é uma
   distinção de conteúdo que precisa sobreviver mesmo quando o destinatário já tem conta**
   — não é só "sabe o nome ou não". A primeira versão desta seção colapsava os dois casos
   num único `login-link-competition.html` genérico ("clique para entrar e acessar..."), o
   que apaga exatamente a mesma distinção que já existia entre `INVITE`/`REGISTRATION_LINK`
   pro caso sem conta. Corrigido: a matriz completa é origem × conhece-o-nome, não só
   conhece-o-nome:

   | Origem | Sem conta (`Participation` sem `User`) | Já tem conta |
   |---|---|---|
   | Admin convida | `invite.html` | `login-link-invite.html` |
   | Jogador pede entrada | `registration-link.html` | `login-link-request.html` |
   | Login avulso (sem competição, sempre tem conta) | — | `login-link.html` |

   `login-link-invite.html`/`login-link-request.html` têm a mesma abertura de
   `invite.html`/`registration-link.html` respectivamente ("Você foi convidado(a)..."/
   "Recebemos seu pedido..."), só trocando o pedido de cadastro por "como você já tem uma
   conta, é só entrar".

Qual dos 5 arquivos carregar continua sendo a mesma condição de negócio de sempre (origem +
existe `User` registrado pra esse e-mail) — a decisão de **qual arquivo carregar** fica em
código Java (fora do escopo desta sessão), não dentro de nenhum template.

Header e rodapé **são** reutilizados entre os 5 arquivos via fragmento do Thymeleaf
(`th:insert`) — isso é composição/inclusão, não ramificação condicional, então não conflita
com a regra 1. `email/fragments/header.html` (estático, sem variável) e
`email/fragments/footer.html` (parametrizado só por `link`, substituição direta).

**Nomes de jogadores registrados são usados** nos três templates de `LOGIN_LINK`
(`th:text="${name}"`) — texto mais amigável pra quem já tem conta. Não é possível nos outros
dois (`INVITE`/`REGISTRATION_LINK`): `Participation` sem `User` vinculado não guarda nome,
só e-mail.

**Todo o texto visível do e-mail está em português**, seguindo a mesma convenção do resto da
GUI (`docs/desenvolvimento.md`) — identificadores/comentários continuam em inglês, só o
conteúdo voltado ao usuário final muda de idioma.

**Pendência Java, não implementada nesta sessão (só os `.html`, sem código)**: a escolha
entre os 5 arquivos precisa de "existe um `User` registrado com esse e-mail?" — uma busca
fresca por e-mail, o que `EntryRequestService.requestEntry` já faz hoje
(`userRepository.findByEmail`). `CompetitionService.create` e
`PlayerManagementService.invitePlayers`/`templateFor` **não fazem essa busca**: sempre
tratam um convite/pedido recém-criado como "sem conta" (`participation.getUser() == null`),
mesmo que o e-mail já tenha um `User` registrado de outra competição — nesse caso o jogador
receberia um `INVITE`/`REGISTRATION_LINK` impessoal em vez do `login-link-invite.html`/
`login-link-request.html` personalizado. Essa lacuna já tem cenários de `.feature`
registrando o comportamento esperado (`manage_competition_players.feature`,
`create_competition.feature` — commits e167937/2467286).

**Arquivos**:
```
src/main/resources/templates/email/
  invite.html                 (admin convida, sem conta)
  registration-link.html      (pedido espontâneo, sem conta)
  login-link.html             (login avulso, sem competição, sempre com conta)
  login-link-invite.html      (admin convida, já tem conta)
  login-link-request.html     (pedido espontâneo, já tem conta)
  fragments/
    header.html
    footer.html
```
Convenção adotada: `<title>` carrega o assunto (também processado pelo Thymeleaf, permitindo
assunto dinâmico via substituição de variável — não ramificação), o `<body>` é o corpo do
e-mail — os dois passam pelo mesmo `TemplateEngine`. Estilo inline (não CSS externo), pela
compatibilidade de clientes de e-mail. Nenhuma dependência do Thymeleaf foi adicionada ao
`pom.xml` ainda, nem código Java de renderização escrito — fica para a implementação.
