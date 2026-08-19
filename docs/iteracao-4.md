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

1. **Contrato da mensagem da fila.** Formato (JSON?), campos exatos — a lista do roadmap
   ("tipo de e-mail, destinatário, dados de template, id de correlação") é um ponto de
   partida, não um schema fechado. Precisa decidir: tipos de dado, obrigatoriedade de cada
   campo, e se `EmailTemplate` (já existente) é reaproveitado como o "tipo de e-mail" da
   mensagem. Versionamento do contrato (o que acontece se o formato mudar com a fila já em
   produção) também é uma pergunta em aberto.
2. **Biblioteca/SDK para falar com SQS a partir do Spring Boot.** AWS SDK v2 puro
   (`software.amazon.awssdk:sqs`) vs. Spring Cloud AWS (`spring-cloud-aws-starter-sqs`,
   que dá `SqsTemplate`/listener declarativo). Nenhuma verificada ainda contra o Maven
   Central nesta sessão nem testada contra a stack atual (Spring Boot 4.1, Java 21).
3. **Como testar sem AWS real.** O sandbox deste agente não tem acesso à AWS nem, até agora,
   conseguiu rodar containers via `docker pull` (bloqueado, ver `iteracao-2.md`) — então
   LocalStack (se depender de imagem Docker) provavelmente tem a mesma limitação aqui, só
   validável numa sessão local (Docker Desktop), como já aconteceu com o Postgres real nas
   Iterações 2 e 3. Precisa decidir se `EmailSender` ganha uma terceira implementação (fila
   real) mantendo `StubEmailSender` ativo em `sandbox`, ou se o `sandbox` também passa a
   apontar para uma fila (LocalStack) — impacto direto em quais cenários dos `.feature` são
   testáveis onde.
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
    - Modelo de dados: uma tabela nova `EMAIL_EVENT` (insert-only, mesmo padrão de `LOG` e
      `SENT_EMAIL`) com `sent_email_id` (FK), `event_type`, `occurred_at` e um campo de
      detalhe (motivo do bounce, por exemplo) — em vez de mutar um campo de status em
      `sent_email`, já que um mesmo envio pode ter múltiplos eventos ao longo do tempo (envio
      aceito, depois bounce horas depois). Preserva a disciplina de tabelas imutáveis já
      usada no projeto.
    - Como associar o evento do SES de volta ao `sent_email`/`id de correlação` originais —
      o SES permite anexar *message tags* personalizadas ao `SendEmail`, então o id de
      correlação da decisão 1 provavelmente precisa ir como tag pra voltar no evento.
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
