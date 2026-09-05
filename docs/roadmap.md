# Roadmap de Iterações — Jogo de Ações

Este documento organiza por iteração o que precisa ser feito, combinando o que já está
especificado no repositório (`app/src/test/resources/features`, `docs/diagrams/der.md`) com a
decisão de arquitetura de envio de e-mail discutida separadamente (VPS + AWS SQS/Lambda/SES).

> **Nota:** a Iteração 4 foi inferida a partir de um diagrama de arquitetura resumido, sem o
> restante da conversa que o originou. Os detalhes de contrato de mensagem, retries e
> observabilidade são um ponto de partida razoável, não uma decisão fechada — ajuste conforme
> o que foi de fato definido naquele chat. As Iterações 5 e 6 (a partir daqui) vêm de uma
> conversa registrada de ponta a ponta — ver `docs/context/iteracao-5.md`.

## Arquitetura de referência (envio assíncrono de e-mail)

```mermaid
flowchart TB
    subgraph VPS["VPS"]
        APP["Sistema Principal (Spring Boot)"]
        DB[("PostgreSQL (container)")]
        APP --> DB
    end

    subgraph AWS["AWS"]
        SQS["Amazon SQS"]
        LAMBDA["AWS Lambda (worker consumidor)"]
        SES["Amazon SES"]
        SQS -- dispara --> LAMBDA
        LAMBDA -- envia --> SES
    end

    APP -- publica mensagem --> SQS
```

A ideia é que o sistema principal nunca envie e-mail diretamente: ele publica uma mensagem
na fila (convite, link de login, confirmação de entrada, etc.) e um worker assíncrono
(Lambda) é quem efetivamente dispara o envio via SES. Isso desacopla o fluxo síncrono da
aplicação (criar competição, pedir entrada, gerar link de login) da latência/instabilidade
de terceiros no envio de e-mail. A partir da Iteração 5, esse "sistema principal" também
passa a incluir o Serviço de E-mail como peça própria, entre a aplicação e a fila — ver
`docs/context/iteracao-5.md`.

## Iteração 1 — Especificação de domínio (concluída)

- Arquivos `.feature` (Gherkin, em inglês) cobrindo:
  - `create_competition`: criação de competição pública/privada, validações, controle de acesso.
  - `login`: autenticação via link mágico enviado por e-mail.
  - `manage_competition_players`: gerência da lista de jogadores/convidados de uma competição.
  - `request_competition_entry`: pedido de entrada de jogador em competição pública.
- DER do domínio (`docs/diagrams/der.md`): `USER` + `ROLE`/`USER_ROLE`, `COMPETITION`,
  `PARTICIPATION`, `LOGIN_LINK`.

**Entregável:** especificação executável (viva) do domínio, pronta para servir de base ao
desenvolvimento.

## Iteração 2 — Esqueleto da aplicação e persistência

**Objetivo:** ter o projeto Spring Boot rodando com o modelo de dados do DER persistido em
PostgreSQL, sem ainda expor as regras de negócio.

- Setup do projeto Spring Boot (Web, Data JPA, Validation).
- Mapeamento JPA das entidades do DER (`User`, `Role`, `UserRole`, `Competition`,
  `Participation`, `LoginLink`) e dos enums (`CompetitionType`, `CompetitionStatus`,
  `ParticipationStatus`, `RequestType`).
- Migrations versionadas (Flyway ou Liquibase) refletindo o schema do DER.
- Docker Compose local (app + PostgreSQL) para desenvolvimento.
- Configuração do runner de Cucumber (glue code inicial) para os `.feature` existentes,
  mesmo que os *steps* ainda não tenham implementação real.

**Fora de escopo:** regras de negócio, envio de e-mail, autenticação.

## Iteração 3 — Fluxos principais (síncronos, e-mail via stub)

**Objetivo:** implementar as regras de negócio dos quatro `.feature` já especificados,
usando um envio de e-mail "fake" (log ou stub) para não depender ainda da infraestrutura
AWS.

- Criação de competição (pública/privada) com todas as validações já especificadas
  (nome obrigatório, data futura, duração > 0, taxas não-negativas, lista de e-mails
  válida) e controle de acesso por papel (`USER_ROLE`).
- Pedido de entrada em competição pública (jogador novo, registrado deslogado, registrado
  logado) e negação de pedido em competição privada.
- Geração e validação de `LOGIN_LINK` (token, expiração, uso único) e os fluxos de
  confirmação de entrada / redirecionamento descritos em `login.feature`.
- Gerência da lista de jogadores de uma competição: filtro por status, reenvio de e-mail,
  edição de e-mail (com validação de formato e duplicidade), remoção de jogador,
  cancelamento de convite pendente, convite de novos jogadores a uma competição privada
  já criada.
- Implementação dos *steps* de Cucumber para todos os cenários das quatro *features*.

**Entregável:** todos os cenários dos `.feature` passando, com envio de e-mail
representado por um stub (ex.: interface `EmailSender` com implementação de log).

## Iteração 4 — Infraestrutura assíncrona de e-mail (SQS + Lambda + SES)

**Objetivo:** substituir o stub de e-mail da Iteração 3 pela arquitetura real descrita no
diagrama acima.

- Definir o contrato da mensagem publicada na fila (tipo de e-mail — convite, link de
  login, confirmação — destinatário, dados de template, id de correlação).
- Sistema principal: implementação de `EmailSender` que publica na fila Amazon SQS em vez
  de enviar diretamente.
- AWS Lambda consumindo a fila e disparando o envio via Amazon SES.
- Fila de dead-letter (DLQ) para mensagens que falharem após as tentativas configuradas.
- Provisionamento da infraestrutura AWS (SQS, Lambda, SES) — de preferência como código
  (Terraform/CDK) para ser reproduzível.
- IAM com privilégio mínimo: o sistema principal só pode publicar na fila; a Lambda só
  pode consumir a fila e usar o SES.
- Verificação de domínio/remetente no SES e saída do modo sandbox (necessário para enviar
  a destinatários não verificados em produção).

**Estado:** núcleo (contrato da mensagem, produtor `SqsEmailSender`, consumidor Lambda,
templates Thymeleaf, testes contra LocalStack) implementado e mergeado — ver
`docs/context/iteracao-4.md`. O que ficou bloqueado por exigir a conta AWS real (policies
IAM de verdade, execução do Terraform, tabela DynamoDB de idempotência, infraestrutura de
eventos SES/SNS, verificação de domínio/saída do sandbox) passa a ser trabalho concreto da
Iteração 6.

**Fora de escopo:** métricas/alertas (ver Iteração 6).

## Iteração 5 — Adequação ao Projeto da Disciplina + Serviço de E-mail

**Objetivo:** alinhar o projeto aos critérios de avaliação da disciplina "Arquiteturas
Avançadas de Software com Microsserviços e Spring Framework" (ver
`docs/context/alinhamento-projeto-disciplina.md`), aproveitando a extração de uma
funcionalidade em serviço independente — exigida pela disciplina — para construir algo
reutilizável por outros projetos do portfólio, não só para cumprir o requisito. Substitui a
antiga Iteração 5 ("Redução de bounce no SES"): a checagem de MX/domínio descartável que
seria uma feature isolada dentro do `app/` passa a ser uma responsabilidade do novo Serviço
de E-mail. Decisões técnicas completas em `docs/context/iteracao-5.md`.

- Adotar a convenção de arquivos do spec-kit (Spec-Driven Development) no processo de
  trabalho já seguido (BDD-first, decisões registradas antes de implementar) — a prática já
  existe, muda a nomenclatura/estrutura de arquivo, não a essência do processo.
- Reorganizar os pacotes do `app/` por domínio/funcionalidade em vez de camada técnica.
- Completar o README com os módulos do sistema, um exemplo de dependência entre eles e a
  justificativa do candidato a serviço independente.
- Adicionar Swagger UI interativo (`springdoc-openapi-starter-webmvc-ui`) ao `app/`.
- Extrair um **Serviço de E-mail** novo (Spring Boot + Thymeleaf, templates vindos do
  banco): valida API keys (`X-API-Key`) emitidas por um projeto separado
  ([`deployo-api-key`](https://github.com/lalgarve/deployo-api-key), CLI, HMAC-SHA256 +
  pepper) — ver `docs/context/iteracao-5.md` para o detalhe —, registro/validação de
  templates, endpoint de envio (JSON → renderiza → valida MX/domínio descartável →
  enfileira). `jogo-acoes` vira o primeiro cliente, consumindo via OpenFeign — fecha o
  requisito de comunicação síncrona entre serviços da disciplina.
- **Sistema de Admin** — a definir se ainda cabe nesta iteração (ver
  `docs/context/iteracao-5.md`, seção 4): a emissão de API key deixou de precisar de uma
  UI/API admin, que era a justificativa original deste componente.
- Spring Cloud Config Server para os componentes novos, e inclusão de tudo (`app`, Serviço
  de E-mail, Admin, Config Server, bancos) no `docker-compose.yml`.
- Job Spring Batch para a importação da lista de domínios descartáveis (mesma fonte já
  decidida — `disposable-email-domains` no GitHub), agora rodando dentro do Serviço de
  E-mail em vez de dentro do `app/`.
- Confecção final de um PDF sobre o projeto e um caderno de testes para o Swagger (a API
  não é trivial).

**Fora de escopo:** provisionamento e execução da infraestrutura AWS real, e a rede
(VPC/Lightsail/VPN, conforme o que o `deployo-infra` decidir) necessária para rodar
`staging.deployo.io` — Iteração 6.

## Iteração 6 — Infraestrutura AWS real, rede e observabilidade

**Objetivo:** desbloquear e executar contra a conta AWS real tudo o que ficou pendente nas
Iterações 4 e 5, e colocar o sistema (app principal + Serviço de E-mail) em produção
acessível via `staging.deployo.io`. Substitui e absorve a antiga Iteração 6 ("Deploy,
observabilidade e hardening").

- Execução do Terraform (`deployo-infra`) contra a conta AWS/infra real: VPC, Lightsail
  e/ou VPN — o que for necessário para `staging.deployo.io` responder.
- Policies IAM de privilégio mínimo de verdade (sistema principal e Serviço de E-mail só
  publicam na fila; a Lambda só consome e usa o SES).
- Tabela DynamoDB de idempotência do consumidor (Decisão 8, `docs/context/iteracao-4.md`).
- *Configuration Set* do SES + tópico SNS + fila SQS de eventos (Decisão 10,
  `docs/context/iteracao-4.md`) — testável desde já contra o *mailbox simulator* do SES,
  mesmo antes de sair do sandbox.
- Verificação de domínio/remetente no SES e saída do modo sandbox.
- Deploy do sistema principal e do Serviço de E-mail na VPS via Docker Compose, com
  backups do(s) banco(s).
- Observabilidade do pipeline assíncrono: profundidade da fila SQS, erros/retries da
  Lambda, taxa de bounce/complaint do SES.
- Alertas para falhas persistentes (mensagens indo para a DLQ).
- Testes de integração de ponta a ponta contra a infraestrutura real: cenários de erro já
  especificados (link inválido/expirado, e-mail inválido, falha no captcha), agora também
  validados com a infraestrutura de e-mail real, não só LocalStack.
- Revisão de segurança do fluxo de login por link mágico (expiração curta, uso único,
  proteção contra reenvio abusivo).

## Iteração 8 — BDD da competição (negociação de ações) e novo DER

**Objetivo:** especificar em Gherkin os fluxos de negociação de ações dentro de uma
competição e desenhar o modelo de dados financeiro que vai sustentá-los.

- Cenários BDD cobrindo: compra e venda de ação, aplicação da corretagem (`buy_fee`/
  `sell_fee` já definidas em `create_competition.feature`), consulta de posição e saldo,
  extrato de lançamentos, fechamento da competição e apuração de resultado.
- Universo de ativos restrito aos 4 tickers disponíveis no plano gratuito da Brapi —
  usados como fixture/exemplo nos cenários.
- Novo DER com contabilidade em partida dobrada real, sem tabelas mutáveis:
  - `ACCOUNT` — uma conta caixa e uma conta de posição por ativo, por participação
    (`PARTICIPATION`).
  - `TRANSACTION` — cabeçalho insert-only (compra, venda, saldo inicial, corretagem).
  - `TRANSACTION_LINE` — as linhas de débito/crédito de cada `TRANSACTION` (valor em R$ e,
    quando aplicável, quantidade de ações), com a soma dos débitos sempre igual à soma dos
    créditos.
  - `STOCK` — cadastro dos ativos negociáveis.
  - `PRICE_QUOTE` — cotações coletadas, com origem (tempo real ou recuperação de lacuna).
- Sem Ibovespa — comparação de portfólio fica só entre jogadores (ver Iteração 13).

**Fora de escopo:** implementação (Iteração 9).

## Iteração 9 — Persistência e regras de negociação

**Objetivo:** implementar o DER da Iteração 8 e as regras de negócio por trás dos cenários
BDD.

- Migrations e mapeamento JPA das novas entidades.
- Implementação dos *steps* de Cucumber dos cenários de negociação.
- Serviço de compra/venda gerando `TRANSACTION` + `TRANSACTION_LINE` corretas (débito na
  conta do ativo/crédito no caixa na compra, e o inverso na venda), aplicando a corretagem
  como lançamento próprio.
- Cálculo de posição e saldo sempre por soma dos lançamentos (nunca por campo mutável).

## Iteração 10 — Coleta de cotações (Brapi)

**Objetivo:** job de coleta periódica de cotações, com tratamento de indisponibilidade.

Endpoints da Brapi confirmados manualmente (plano gratuito, `GET https://brapi.dev/api/v2/stocks/...`):

| Endpoint | Uso |
|---|---|
| `quote?symbols=X` | Cotação atual — `regularMarketPrice` e `regularMarketTime` (precisão de segundo). |
| `historical?symbols=X&range=1d&interval=15m` | Histórico intradiário a cada 15 min — **confirmado disponível no plano gratuito**, sem erro/bloqueio de plano. |
| `dividends?symbols=X` | Dividendos — fora do escopo atual. |

- Agendamento configurável de polling em `quote` para os 4 ativos disponíveis.
- Gravação em `PRICE_QUOTE` (`price = regularMarketPrice`, `collected_at = regularMarketTime`).
- Mecanismo de watermark (último instante coletado com sucesso) para detectar lacunas na
  volta do ar.
- **Recuperação de lacuna confirmada como viável**: ao detectar um intervalo sem cotações
  (ex.: sistema fora do ar), preencher os pontos faltantes chamando `historical` com
  `interval=15m` para o período da lacuna, marcando esses registros com origem
  `BACKFILL` em `PRICE_QUOTE` (em vez de `REALTIME`). Testar se intervalos menores que
  15 min (`5m`, `1m`) também estão disponíveis no plano gratuito antes de decidir a
  granularidade do polling em si — o teste feito cobriu apenas `15m`.

## Iteração 11 — Telas dos fluxos já especificados (login, competição, jogadores)

**Objetivo:** construir a interface dos fluxos das Iterações 1–3 (login por link mágico,
criação e gerência de competição, pedido de entrada), com acessibilidade validada desde
já — não deixada para o final.

- Telas de login/link mágico, criação de competição, gerência de lista de jogadores, pedido
  de entrada.
- Critério de aceite de cada tela: navegação completa testada com leitor de tela (NVDA e/ou
  VoiceOver), incluindo os estados de erro (link inválido/expirado, e-mail inválido,
  captcha).

## Iteração 12 — Telas de negociação e portfólio

**Objetivo:** construir a interface de compra/venda e acompanhamento de posição.

- Telas de compra/venda de ação, extrato de lançamentos (`TRANSACTION`/`TRANSACTION_LINE`),
  posição atual e saldo.
- Mesmo critério de aceite da Iteração 11: navegação testada com leitor de tela.

## Iteração 13 — Gráficos (preço, portfólio, comparação entre jogadores)

**Objetivo:** visualização gráfica dos dados coletados, sem acessibilidade ainda (tratada
nas Iterações 14–17).

- Gráfico de evolução de preço por ativo, a partir de `PRICE_QUOTE`.
- Snapshots periódicos do valor do portfólio (caixa + posições ao preço do momento), para
  permitir comparação de evolução entre dois jogadores sem recalcular tudo sob demanda.
- Comparação de portfólio entre jogadores (Ibovespa fora de escopo).

## Iteração 14 — Acessibilidade de gráficos: pesquisa e modelagem — descrição por pontos importantes

**Objetivo:** definir como detectar e descrever os pontos relevantes de uma série temporal
(picos, vales, mudanças de tendência) para gerar uma descrição textual do gráfico.

- Pesquisa de algoritmos de detecção de pontos relevantes.
- Modelagem de como compor a descrição textual a partir desses pontos (o que priorizar,
  nível de detalhe, idioma).

## Iteração 15 — Acessibilidade de gráficos: implementação — descrição por pontos importantes

**Objetivo:** implementar o modo de descrição textual definido na Iteração 14.

- Implementação do algoritmo de detecção de pontos (parte pesada em Rust/WebAssembly).
- Geração da descrição textual e integração com leitor de tela (texto alternativo/
  `aria-live`).
- Validação com leitor de tela real.

## Iteração 16 — Acessibilidade de gráficos: pesquisa e modelagem — sonorização

**Objetivo:** definir a experiência de sonorização do gráfico (som grave para valores
baixos, agudo para altos; posição estéreo da esquerda para a direita representando o
tempo).

- Pesquisa de mapeamento valor→frequência e tempo→posição estéreo, e das APIs de áudio
  disponíveis (Web Audio API) e sua integração com Rust/WebAssembly.
- Modelagem da experiência (faixa de frequências, duração/velocidade de reprodução,
  controles do usuário).

## Iteração 17 — Acessibilidade de gráficos: implementação — sonorização

**Objetivo:** implementar o modo de sonorização definido na Iteração 16.

- Motor de síntese/geração de sinal em Rust compilado para WebAssembly (parte pesada).
- Integração com Web Audio API no front (reprodução, controle estéreo).
- Validação com usuários/leitores de tela.
