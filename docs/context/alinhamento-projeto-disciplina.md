# Jogo de Ações — Abordagem de Desenvolvimento e Alinhamento com o Projeto da Disciplina

*Arquiteturas Avançadas de Software com Microsserviços e Spring Framework — Projeto da
Disciplina.*

*Leila Algarve — setembro de 2026.*

{CLAUDE: colocar índice}

## 1. Introdução

O projeto **Jogo de Ações** foi iniciado antes do começo desta disciplina, como projeto para
portfólio pessoal e como preparação para cursar esta disciplina pela segunda vez. Na primeira vez, 
eu falhei na entrega do projeto final por ter tido dificuldades executivas - dificuldade em iniciar 
o projeto. Então comecei a desenvolver um projeto com o objetivo de estudar como usar IA de forma 
efetiva. Escolhi o projeto de um jogo de ações pois poderia ter uma implementação próxima a realidade
de empresas do mercado financeiro. Trabalhei por anos em um projeto onde fui responsável por todos os
cálculos financeiros.

 Por esse motivo, seu desenvolvimento não seguiu, desde o início, a
sequência de quatro Etapas descrita no enunciado do Projeto da Disciplina — a organização
adotada foi outra, descrita abaixo. Este documento existe para deixar essa diferença explícita
antes da entrega, e para servir de ponto de partida para uma conversa com o professor sobre
como (ou se) o trabalho já feito pode ser aproveitado, e o que precisaria ser ajustado para
atender aos critérios da disciplina.

## 2. Metodologia de desenvolvimento adotada

O projeto foi organizado por **iteração de capacidade de negócio**, não pelas quatro etapas
técnicas do enunciado (organização interna → separação de serviços → configuração e execução
→ mensageria/batch). O ponto de partida de cada iteração é sempre a especificação do
comportamento antes da implementação:

- **BDD primeiro:** cada funcionalidade tem cenários Gherkin (`.feature`, em inglês) escritos
  antes do código de implementação, cobrindo caminho feliz e casos de erro — funcionam como
  contrato de aceite, não como documentação escrita depois do fato.
- **Modelo de dados desenhado antes da entidade de código**: o DER
  (`docs/diagrams/der.md`) precede o mapeamento JPA de cada iteração.
- **Roadmap por iteração** (`docs/roadmap.md`): 16 iterações planejadas, cada uma entregando
  uma capacidade de negócio completa (ex.: login por link mágico, criação de competição,
  infraestrutura assíncrona de e-mail, negociação de ações, acessibilidade de gráficos) — não
  uma camada técnica isolada.
- **Documentação viva de contexto** (`docs/context/`): decisões de arquitetura registradas
  por iteração, incluindo alternativas consideradas, no momento em que são tomadas.
- **Preferência por infraestrutura real em teste**, em vez de mock/stub, sempre que viável
  (ex.: fila SQS real via LocalStack em vez de simulação em memória).

Essa abordagem responde bem à complexidade do domínio (um jogo de simulação de bolsa, com
regras de negócio não triviais), mas não foi desenhada em torno da sequência específica de
competências que a disciplina avalia — daí a necessidade de mapear uma coisa contra a outra
agora, na reta final.

## 3. Uso de ferramentas de IA

Conforme a política "Sinal Verde" da disciplina, o desenvolvimento contou com apoio de
ferramentas de IA (Claude Code, Anthropic) em diferentes momentos: revisão de código, análise
de aderência do projeto aos critérios de avaliação (incluindo o levantamento que originou a
seção 4 deste documento) e redação deste próprio documento a partir de instruções e revisão da
autora. Todo código gerado ou revisado com apoio de IA foi lido, entendido e validado antes de
ser incorporado ao repositório — commits e decisões de arquitetura permanecem de
responsabilidade da autora.

## 4. Mapeamento: estado atual do projeto × Etapas da disciplina

Levantamento feito diretamente no código do repositório (`lalgarve/jogo-acoes`, branch
principal), não uma estimativa.

### Etapa 1 — Organização Arquitetural

| Requisito | Situação |
|---|---|
| Fluxo Controller → Service → Repository → BD, sem acesso direto do controller ao repository | Atendido |
| Validação via Bean Validation | Atendido — via contrato OpenAPI (`docs/openapi.yaml`), que gera as anotações no DTO |
| Tratamento de exceções centralizado | Atendido — `ApiExceptionHandler` com `@ControllerAdvice` |
| Duas ou mais consultas Spring Data além do CRUD básico | Atendido — bem mais que duas, em vários repositórios |
| Documentação da API via OpenAPI/Swagger | Atendido como contrato estático (`docs/openapi.yaml`); falta uma UI interativa (Swagger UI) rodando junto da aplicação — ver seção 5 |
| Organização de pacotes por domínio/funcionalidade, não por camada técnica | **Não atendido** — pacotes hoje são `web/`, `service/`, `repository/`, `domain/`, `email/`, `captcha/` |
| README com módulos, dependência entre eles e candidato a serviço independente | Parcial — a informação existe implicitamente, mas não está escrita no README nesse formato |
| Tag `etapa-1` | **Não atendido** — no repositório existem apenas as tags referentes às iterações planejadas |

### Etapa 2 — Separação e Comunicação entre Serviços

| Requisito | Situação |
|---|---|
| Serviço independente com responsabilidade própria e justificada | Atendido — `email-lambda/` já existe como aplicação separada |
| Comunicação síncrona via REST + OpenFeign | **Não atendido** — a comunicação existente com `email-lambda` é assíncrona (fila SQS), não síncrona via Feign; nenhuma dependência Feign existe no projeto |
| DTOs de comunicação entre serviços | Atendido dentro do que já existe (mensagens da fila), mas não no formato REST/Feign pedido |
| Teste de disponibilidade/indisponibilidade do serviço | Não aplicável ainda, por depender da comunicação síncrona acima |
| Tag `etapa-2` | **Não atendido** |

*Observação: o que existe hoje (mensageria assíncrona) atende melhor ao espírito da Etapa 4
do que ao da Etapa 2, que pede explicitamente comunicação síncrona.*

### Etapa 3 — Configuração e Execução dos Serviços

| Requisito | Situação |
|---|---|
| Profiles para ao menos dois ambientes | Atendido, além do mínimo — `sandbox`/`docker`/`staging`/`production` |
| Configuração via variáveis de ambiente | Atendido (ex.: `SPRING_DATASOURCE_URL`, `SPRING_CLOUD_AWS_SQS_ENDPOINT` no `docker-compose.yml`) |
| Banco relacional real fora do ambiente local de desenvolvimento | Atendido — PostgreSQL nos perfis `docker`/`staging`/`production` |
| Cada serviço com persistência própria | Atendido para os serviços que existem hoje; a depender de como a Etapa 2 for resolvida, o novo serviço precisará da própria também |
| Spring Cloud Config Server | **Não atendido** — não existe em nenhum lugar do projeto |
| Containerização de cada aplicação | Parcial — há `Dockerfile` para a aplicação principal; falta para o(s) serviço(s) que ainda vão ser criados |
| Orquestração via Docker Compose de todos os componentes (app, serviço, bancos, Config Server) | Parcial — `docker-compose.yml` hoje sobe app + banco + fila simulada, mas não um Config Server nem um segundo serviço |
| Tag `etapa-3` | **Não atendido** |

### Etapa 4 — Comunicação Assíncrona e Processamento em Lote

| Requisito | Situação |
|---|---|
| Mensageria assíncrona real (produtor/fila/consumidor) | Atendido — `SqsEmailSender` publica em fila SQS real (LocalStack em dev/CI), consumida pela Lambda que dispara o SES |
| Tecnologia de mensageria = a definida pelo professor em aula | A confirmar — o enunciado cita RabbitMQ como exemplo; o projeto usa Amazon SQS. Precisa de confirmação se conta como "tecnologia equivalente" |
| Processamento em lote com Spring Batch (Job/Step/ItemReader/ItemProcessor/ItemWriter) | **Não atendido** — pode ser usado para a importação da lista de domínios temporários especificada na Iteração 5, hospedada no GitHub |
| Tag `etapa-4` | **Não atendido** |

## 5. O que faltaria para alinhamento completo

1. Reorganizar os pacotes da aplicação principal por domínio (ex.: `competition/`, `user/`,
   `login/`) em vez de por camada técnica.
2. Completar o README com a identificação explícita de módulos, um exemplo de dependência
   entre eles e a justificativa do candidato a serviço independente.
3. Adicionar Swagger UI interativo (`springdoc-openapi-starter-webmvc-ui`) além do contrato
   estático já existente.
4. Criar um novo microsserviço com comunicação **síncrona** via REST + OpenFeign — candidato
   natural: a checagem de MX/domínio descartável já planejada na Iteração 5 do roadmap do
   projeto, ainda não implementada.
5. Implementar um Spring Cloud Config Server e incluir todos os componentes (app, novo
   serviço, Config Server) no `docker-compose.yml`.
6. Implementar um job Spring Batch com a importação dos domínios de e-mails temporários.
7. Criar as tags `etapa-1` a `etapa-4` no repositório, conforme cada etapa for efetivamente
   fechada.

## 6. Metodologia de 

A metodologia de desenvolvimento usada foi baseada na experiência que tive durante minhas tentativas de desenvolvimento usando IA. Percebi que possuir uma documentação clara da estrutura do projeto antes do desenvolvimento do código permitia a IA entender melhor o projeto. Ou seja, o tempo usado para definição da arquitetura e confecção de diagramas ajudava a IA no entendimento do código que precisava ser gerado. Para os diagramas eu uso Code As Diagram. Em outros projetos usei o PlantUML mas ao descobrir o suporte nativo do Github ao Mermaid.js decidi usá-lo no projeto do Jogo de Ações.

Nesta semana descobri o SDD - Specification Driven Design. Meu método de desenvolvimento estava no caminho certo: primeiro específica, depois implementa. No entanto, como a estrutura e organização do SSD são superiores, gostaria passar a usá-la no projeto do Jogo de Ações.

Repositório do Jogo Ações: https://github.com/lalgarve/jogo-acoes
Repositório usando SSD: https://github.com/lalgarve/deployo-api-key


## 7. Projeto Jogo de Ações

{CLAUDE: se basear na documentação do projeto, incluindo os diagramas gerados}


