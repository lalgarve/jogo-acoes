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
- Provisionamento da infraestrutura AWS (SQS, Lambda, SES) — de preferência como código
  (Terraform/CDK) para ser reproduzível.
- IAM com privilégio mínimo: o sistema principal só pode publicar na fila; a Lambda só pode
  consumir a fila e usar o SES.
- Verificação de domínio/remetente no SES e saída do modo sandbox (necessário para enviar a
  destinatários não verificados em produção).

**Fora de escopo (fica para a Iteração 5):** métricas/alertas, observabilidade do pipeline
(profundidade da fila, erros/retries da Lambda, taxa de bounce/complaint do SES).

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
7. **Verificação de domínio/remetente no SES e saída do sandbox mode.** Requer acesso real à
   conta AWS do projeto — não é algo verificável dentro de uma sessão de agente; precisa de
   decisão/execução de quem tem essas credenciais.
8. **Idempotência do consumidor.** SQS entrega "at-least-once" — a mesma mensagem pode chegar
   duplicada na Lambda. Precisa decidir se isso importa aqui (reenviar o mesmo e-mail
   duas vezes é um problema real de produto?) e, se sim, como deduplicar (id de correlação
   como chave de idempotência, checagem contra `sent_email` antes de enviar, etc.).
9. **Onde a `sent_email` é gravada.** Hoje `StubEmailSender` grava e "envia" no mesmo método
   síncrono. Com fila real, isso se divide em duas pontas (sistema principal publica /
   Lambda consome e envia via SES) — decidir se `sent_email` é gravada no sistema principal
   no momento de publicar (antes de saber se o envio de fato aconteceu) ou se precisa de
   algum retorno da Lambda pro sistema principal marcar sucesso/falha, e o que isso implica
   pro modelo de dados atual (hoje `sent_at` é preenchido de forma síncrona e otimista).

## Ordem sugerida de discussão

As decisões 1–2 (contrato da mensagem, SDK) são a base de tudo o resto e não dependem de
acesso à AWS real — dá para avançar nelas dentro de uma sessão de agente. As decisões
5–7 (infraestrutura, IAM, SES) dependem de acesso à conta AWS do projeto, fora do alcance
das ferramentas disponíveis aqui — ver `docs/desenvolvimento.md` para o padrão já usado nas
iterações anteriores de documentar claramente o que foi validado de verdade vs. o que ficou
por raciocínio/pendente de uma sessão local ou de quem tem as credenciais.
