# Iteração 5 — Planejamento técnico

Este documento registra as decisões técnicas da Iteração 5 (ver objetivo geral em
[`roadmap.md`](../roadmap.md)), no mesmo espírito de
[`iteracao-2.md`](iteracao-2.md)/[`iteracao-3.md`](iteracao-3.md)/[`iteracao-4.md`](iteracao-4.md):
um resumo pra servir de base caso a conversa precise mudar de contexto. **Estado: planejamento —
nada implementado ainda.**

## Por que esta iteração muda de rumo

O projeto começou antes da disciplina "Arquiteturas Avançadas de Software com Microsserviços e
Spring Framework", e seu desenvolvimento por iteração de capacidade de negócio não seguiu a
sequência de quatro Etapas do enunciado — ver
[`alinhamento-projeto-disciplina.md`](alinhamento-projeto-disciplina.md) para o mapeamento
completo do que já atende cada Etapa e o que falta. Esta iteração é o trabalho de adequação,
sujeito à conversa ainda pendente com o professor sobre se a abordagem é aceita.

A decisão de escopo tomada nesta sessão: em vez de extrair a checagem de MX/domínio descartável
(antiga Iteração 5 do roadmap) como um microsserviço isolado só para satisfazer a Etapa 2, ela
vira uma responsabilidade dentro de um **Serviço de E-mail** reutilizável — meu portfólio se
beneficia de ter algo que outras aplicações também podem consumir, não só o jogo-acoes. É a
mudança que aproveita mais do código já escrito (templates Thymeleaf, `EmailSender`/
`EmailContentRenderer` do `app/`, o próprio `email-lambda`) reorganizando em vez de descartando.

## Ordem de trabalho dentro da iteração

1. Adotar a convenção de arquivos do spec-kit (SDD) para o processo já seguido.
2. Tarefas pendentes, divididas por Etapa da disciplina (Etapa 1 primeiro — é a que não depende
   de nenhuma decisão de arquitetura nova).
3. Confecção final do PDF do projeto e do caderno de testes Swagger — por último, depois que o
   resto estiver estável.

## 1. Adoção do spec-kit (Spec-Driven Development)

O processo já seguido no projeto (specs em `.feature` antes do código, decisões técnicas
registradas em `docs/context/iteracao-N.md` antes de implementar, DER antes das entidades —
ver `docs/context/desenvolvimento.md`) já é, na essência, SDD. O que muda é só nomenclatura e
estrutura de arquivo, para a convenção do [spec-kit](https://github.com/github/spec-kit)
(`specs/NNN-slug/spec.md` + `plan.md` + `tasks.md` por funcionalidade).

**Decisões a tomar antes de aplicar:**

- Migrar os documentos de iteração já existentes (`iteracao-2.md` a `iteracao-4.md`) para a
  estrutura nova, ou só passar a usá-la a partir daqui, deixando os anteriores como estão
  (histórico, não reescrito — mesmo princípio já usado para a convenção de commits em
  `desenvolvimento.md`, seção "Nota histórica")? **Provável: só a partir daqui** — reescrever
  documentos já fechados não agrega, e o padrão do projeto já é não retrabalhar histórico.
- Granularidade do slug `NNN-*`: uma pasta `specs/` por iteração (equivalente a
  `iteracao-N.md`), ou uma por funcionalidade dentro da iteração (mais granular, mais próximo
  do uso comum do spec-kit)? Afeta diretamente como esta própria iteração seria estruturada se
  já nascesse no formato novo.
- Os `.feature` Gherkin continuam vivendo em `app/src/test/resources/features` (contrato de
  aceite, executável) — o spec-kit não substitui isso, `spec.md`/`plan.md`/`tasks.md` registram
  decisão e planejamento, papel equivalente ao que `iteracao-N.md` já cumpre hoje.

## 2. Tarefas por Etapa da disciplina

Ver `alinhamento-projeto-disciplina.md` para o levantamento completo; aqui só o que muda de
"pendente" para "planejado nesta iteração".

### Etapa 1 — Organização Arquitetural

- Reorganizar os pacotes do `app/` por domínio/funcionalidade (`competition/`, `user/`,
  `login/` ou nomes equivalentes) em vez de camada técnica (`web/`, `service/`,
  `repository/`).
- Completar o README: módulos do sistema (agora incluindo Serviço de E-mail e Admin, não só
  `app`/`email-lambda`), um exemplo de dependência entre eles, e a justificativa do candidato
  a serviço independente (que deixa de ser hipotética — é o Serviço de E-mail sendo extraído
  nesta mesma iteração).
- Adicionar `springdoc-openapi-starter-webmvc-ui` ao `app/`, apontando pro `docs/openapi.yaml`
  já existente (mantém contract-first, só liga a UI interativa em cima do contrato).

### Etapa 2 — Separação e Comunicação Síncrona

Ver seção 3 (detalhamento do Serviço de E-mail) e seção 4 (Sistema de Admin) abaixo — é o
grosso desta iteração.

- `jogo-acoes` vira cliente do Serviço de E-mail via OpenFeign — comunicação síncrona real,
  fecha o requisito que a mensageria assíncrona (SQS/Lambda/SES, Iteração 4) não cobria.
- Swagger UI também no Serviço de E-mail, mesmo padrão da Etapa 1.

### Etapa 3 — Configuração e Execução

- Spring Cloud Config Server para os componentes novos (Serviço de E-mail, Admin) — decisão
  em aberto se `app/` também migra pra ele ou continua com profiles locais (`sandbox`/
  `docker`/`staging`/`production` já bem estabelecidos).
- `docker-compose.yml` cobrindo tudo: `app`, Serviço de E-mail, Admin, Config Server, e o(s)
  banco(s) — cada serviço com persistência própria (ver "Banco de dados" na seção 3).

### Etapa 4 — Processamento em Lote

- Job Spring Batch para a importação da lista de domínios descartáveis
  (`disposable-email-domains` no GitHub, mesma fonte já decidida na antiga Iteração 5) — agora
  dentro do Serviço de E-mail, já que é lá que a validação anti-bounce passa a viver. Estrutura
  già desenhada no roadmap antigo (baixa o arquivo, calcula diff contra a versão anterior,
  aplica só as entradas adicionadas/removidas) — o que muda é rodar como `Job`/`Step`/
  `ItemReader`/`ItemProcessor`/`ItemWriter` do Spring Batch em vez de um `@Scheduled` solto.

## 3. Serviço de E-mail — detalhamento

Resumo de um brainstorm feito em outra conversa (sem o contexto completo desta aplicação) —
registrado aqui como ponto de partida, não decisão fechada. Falta revisar contra o que já
existe em `app/`/`email-lambda` antes de implementar.

**Stack:** Spring Boot + Thymeleaf, com `StringTemplateResolver` (templates vêm do banco, não
de arquivo — diferença importante em relação ao `EmailContentRenderer` atual do `app/`, que usa
`SpringTemplateEngine` contra arquivos `.html` em `src/main/resources/templates/email/`).

**Endpoints para aplicações clientes:**
- Registrar templates.
- Enviar e-mail: recebe JSON → renderiza o template → valida anti-bounce (a checagem de MX/
  domínio descartável, movida da antiga Iteração 5) → enfileira.

**Endpoints admin** (`/admin/applications`, `/admin/applications/{id}/keys`):
- Registrar aplicação cliente, gerar/rotacionar API key.

**Autenticação:** só API Key (header `X-API-Key`). Hash SHA-256 armazenado; a chave bruta
(prefixo `emk_...`, gerada com `SecureRandom`) é exibida uma única vez, na criação — mesmo
princípio de segredo que não pode ser recuperado depois, só rotacionado.

**Dados que o serviço possui** (fonte da verdade sobre quem pode chamá-lo):
- `application` / `api_key` — quem pode chamar o serviço.
- `email_template` (`app_id`, `template_key`, `content`, `variables_schema`).
- Fila de envio (estrutura ainda não detalhada — ver pendências abaixo; pode reaproveitar o
  contrato de mensagem já validado na Iteração 4, `schemaVersion`/`correlationId`/
  `recipientEmail`/`subject`/`body`, em vez de desenhar um novo).
- Log de erros de envio (estrutura ainda não detalhada).

**Banco de dados:** próprio, separado do banco do `app/` — cada serviço com persistência
própria é requisito explícito da Etapa 3, e mantém o princípio já usado no projeto de nunca um
serviço acessar direto o schema de outro (`docs/context/alinhamento-projeto-disciplina.md`).

**Fluxo de autenticação:** o brainstorm original tinha um diagrama/trecho de código ilustrando
o fluxo que não veio junto neste resumo — falta desenhar antes de implementar (candidato a
diagrama de sequência, mesmo padrão de `docs/diagrams/sequencia.md`).

**Relação com a infraestrutura da Iteração 4** (decisão em aberto, não resolvida no
brainstorm): o `app/` hoje tem seu próprio `SqsEmailSender`/`EmailContentRenderer`/templates, e
o `email-lambda` consome a fila e fala com o SES. Com o Serviço de E-mail novo fazendo
"renderiza → valida → enfileira", faz sentido esse código migrar do `app/` para dentro do
Serviço de E-mail (que passaria a ser o único publicador na fila, e o `email-lambda` continuaria
como está, consumindo dela) — em vez de o `app/` continuar publicando direto e o Serviço de
E-mail virar só uma segunda validação em paralelo. Precisa ser decidido explicitamente antes de
implementar, não assumido por omissão.

**Onde este serviço mora** (decisão em aberto): repositório novo e próprio (mesmo padrão de
`deployo-infra`/`deployo-website`, já que o objetivo é ser reutilizável por *outras*
aplicações, não só um módulo interno do `jogo-acoes`), ou módulo dentro do reator Maven atual
(`app`/`email-lambda`)? Um repositório próprio parece mais alinhado ao objetivo de portfólio/
reuso declarado nesta conversa, mas afeta convenção de branch/PR e precisa de nome definido.

**Próximos passos técnicos ainda não detalhados** (do brainstorm original, registrados aqui
como pendências, não resolvidos):
- Endpoint completo de rotação de key — desativar a antiga sem quebrar chamadas em andamento
  (janela de sobreposição? invalidação imediata?).
- Validação de `variables_schema` (JSON Schema) contra o JSON recebido, antes de renderizar.
- Estrutura da fila de envio e do log de erros de envio.

## 4. Sistema de Admin — detalhamento

Interface fina, sem estado de negócio próprio:
- Login de admin por e-mail + link, sem senha — reaproveita o padrão de `LoginLink` já
  implementado no `jogo-acoes` (Iteração 3), não uma reimplementação.
- Só orquestra login e chama a API do Serviço de E-mail com sua própria API key — não duplica
  a tabela `application` nem nenhum outro estado que já mora no Serviço de E-mail.

**Onde este sistema mora:** mesma pendência do Serviço de E-mail acima (repositório próprio vs.
módulo) — provavelmente segue a mesma decisão.

## 5. `jogo-acoes` como primeiro cliente

- Recebe a API key manualmente do admin (variável de ambiente/secret manager) — não há
  auto-provisionamento do lado do cliente nesta fase.
- Chama o Serviço de E-mail com essa key via OpenFeign para enviar e-mail.
- Decisão em aberto (ligada à pendência da seção 3): se o `EmailSender`/`SqsEmailSender` atuais
  do `app/` são removidos e substituídos pelo cliente Feign, ou se convivem durante uma
  transição.

## 6. PDF final e caderno de testes Swagger

Últimos itens da iteração, depois que o resto estiver estável:
- PDF final sobre o projeto — para a entrega da disciplina (ver formato de nome de arquivo
  exigido no enunciado: `nomedoaluno_nomedadisciplina_pd.PDF`).
- Caderno de testes para o Swagger — a API não é trivial (múltiplos serviços, autenticação por
  API key, fluxos de e-mail assíncronos), então um roteiro de casos de teste via Swagger UI
  ajuda tanto a validação manual quanto a avaliação do professor.

## Decisões em aberto (resumo)

- Migrar `iteracao-N.md` antigos para o formato spec-kit, ou só usá-lo daqui pra frente?
- Granularidade das pastas `specs/NNN-*` — por iteração ou por funcionalidade?
- `app/` também migra para o Config Server, ou mantém profiles locais?
- O `SqsEmailSender`/templates do `app/` migram para dentro do Serviço de E-mail, ou
  convivem temporariamente com ele?
- Serviço de E-mail e Sistema de Admin: repositório próprio ou módulo no reator atual?
- Estrutura da fila de envio e do log de erros do Serviço de E-mail.
- Endpoint de rotação de API key — janela de sobreposição ou invalidação imediata.
- Validação de `variables_schema` via JSON Schema — biblioteca e ponto de validação exatos.
- Diagrama do fluxo de autenticação do Serviço de E-mail (perdido no resumo do brainstorm
  original).
