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
3. PDF do projeto e caderno de testes Swagger — **revisado (ver seção 6 e Issue #43)**:
   escritos por Etapa, em paralelo ao código, num branch próprio, não mais deixados só pro
   final.

## 1. Adoção do spec-kit (Spec-Driven Development)

O processo já seguido no projeto (specs em `.feature` antes do código, decisões técnicas
registradas em `docs/context/iteracao-N.md` antes de implementar, DER antes das entidades —
ver `memory/constitution.md`) já é, na essência, SDD. O que muda é só nomenclatura e
estrutura de arquivo, para a convenção do [spec-kit](https://github.com/github/spec-kit)
(`specs/NN-NNN-slug/spec.md` + `plan.md` + `tasks.md` por funcionalidade).

**Executado (sessão 2026-09-10, ver seção 7):** a estrutura de arquivo em si já existe —
`memory/constitution.md` (movido de `docs/context/desenvolvimento.md`, expandido com a seção
comparando `docs/context/iteracao-N.md` e `specs/NN-NNN-slug/`), `templates/` (os 5
templates, adaptados dos usados em `deployo-template-java`) e `specs/README.md` (convenção
documentada, sem nenhuma pasta `NN-NNN-*` ainda). Com a granularidade resolvida logo abaixo,
não sobra mais nenhuma decisão bloqueando o uso de `specs/` — só falta a primeira feature ser
especificada nesse formato.

**Decisões a tomar antes de aplicar:**

- **Resolvido (sessão 2026-09-09/10, ver seção 7):** os documentos de iteração
  (`iteracao-N.md`, este incluído) continuam sendo escritos e **atualizados incrementalmente,
  sessão a sessão**, não só até a adoção do spec-kit nem só no início do planejamento de cada
  iteração. `specs/NN-NNN-*`, quando/se adotado, registra decisão técnica de escopo fechado de
  uma funcionalidade — não substitui nem interrompe este diário. Boa parte do que se discute
  numa sessão (organização de issues, convenção de label, a própria granularidade de
  `specs/NN-NNN-*` abaixo) não vira spec nenhuma, e só fica registrada aqui.
- **Resolvido (sessão 2026-09-10, ver seção 7): granularidade por funcionalidade, agrupada
  por iteração — `specs/NN-NNN-slug/`.** `NN` é o número da iteração (2 dígitos, mesmo número
  do label `iteration-N` da Issue); `NNN` é o número sequencial da funcionalidade **dentro
  dessa iteração** (3 dígitos, reinicia a cada iteração). Preserva o agrupamento por iteração
  que o projeto já tinha (`iteracao-N.md`) sem abrir mão da granularidade fina por
  funcionalidade, mais próxima do uso comum do spec-kit. Ver `specs/README.md` para a
  estrutura completa e um exemplo.
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

**Autenticação — atualizada, substitui o desenho original do brainstorm** (ver
"Emissão de API-KEY: `deployo-api-key`" abaixo): API Key só, via header `X-API-Key`. Não é
mais o Serviço de E-mail quem emite/gerencia as chaves através de um endpoint admin — a
emissão virou uma CLI própria, num repositório separado
([`lalgarve/deployo-api-key`](https://github.com/lalgarve/deployo-api-key)), pensada como
padrão reutilizável por qualquer API interna futura, não só o Serviço de E-mail. O Serviço de
E-mail só **valida** a chave recebida (leitura), não a gera.

**Dados que o serviço possui** (fonte da verdade só do que é dele — API keys não são mais
emitidas nem "donas" aqui, ver abaixo):
- `email_template` (`app_id`, `template_key`, `content`, `variables_schema`).
- Fila de envio — contrato ainda em aberto entre duas opções: o desenhado na Iteração 4
  (`schemaVersion`/`correlationId`/`recipientEmail`/`subject`/`body`, já renderizado no
  produtor) ou o revisado na seção 3.2 abaixo (`templateName`/`templateData`, renderizado
  pelo próprio SES) — ver 3.2 para o raciocínio e o que falta decidir.
- Log de erros de envio (estrutura ainda não detalhada).

**Banco de dados:** próprio, separado do banco do `app/`, para os dados que são realmente dele
(`email_template`, fila, log de erros) — cada serviço com persistência própria é requisito
explícito da Etapa 3. A tabela `api_keys` é uma exceção deliberada a essa regra — ver
"Emissão de API-KEY" abaixo, que registra a tensão com esse mesmo princípio.

### 3.1 Emissão de API-KEY: `deployo-api-key`

Substitui o desenho original do brainstorm (endpoints `/admin/applications`/
`/admin/applications/{id}/keys` dentro do próprio Serviço de E-mail). Em vez disso, a emissão
virou um projeto próprio — [`lalgarve/deployo-api-key`](https://github.com/lalgarve/deployo-api-key),
já com sua primeira feature especificada em SDD (`specs/001-generate-api-key/`) — pensado como
padrão de autenticação reutilizável por qualquer API interna futura, não amarrado ao Serviço de
E-mail. Vale a leitura de `specs/001-generate-api-key/spec.md`/`plan.md` lá para o detalhe
completo; resumo do que importa para esta iteração:

- **CLI, não endpoint HTTP**: comando `generate --service <nome> [--validity-days <dias>]`
  gera a chave, grava o hash e imprime a chave em texto puro uma única vez, no terminal — não
  existe interface de administração (decisão consciente registrada no README daquele
  repositório, dado que hoje só existe um consumidor).
- **Algoritmo**: HMAC-SHA256 com *pepper* (chave secreta fora do banco e do código-fonte, via
  variável de ambiente) — não SHA-256 simples como o brainstorm original tinha registrado. A
  chave já nasce com 256 bits de entropia aleatória, então não precisa de um hash
  memory-hard (Argon2/bcrypt) como senha de usuário precisaria.
- **Prefixo da chave**: `dak_` ("Deployo API Key") — não `emk_` como o brainstorm original
  tinha; nome genérico do projeto, não amarrado ao primeiro consumidor.
- **Validade opcional**: `--validity-days <N>` define expiração (`expires_at`); omitido, a
  chave não expira. Conceito novo, ausente do brainstorm original.
- **Rotação: fora de escopo por enquanto** — revogar ou rotacionar automaticamente uma chave
  não está implementado nem planejado na feature atual daquele repositório. Isso
  **substitui** o item "endpoint completo de rotação de key" que estava registrado como
  pendência abaixo — não é mais algo que o Serviço de E-mail (ou qualquer consumidor) expõe.
- **Modelo de dados**: uma tabela só, `api_keys` (`id`, `service_name`, `key_hash`,
  `created_at`, `expires_at`) — sem uma entidade `application` separada; `service_name` aceita
  qualquer string não vazia por enquanto (decisão em aberto naquele repositório se isso precisa
  validar contra uma lista fixa mais adiante).
- **Leitura (validação da chave) é uma biblioteca, no mesmo repositório `deployo-api-key`** —
  módulo/pacote Maven separado da emissão (mesmo repositório, duas frentes de código, ver
  `plan.md` daquele projeto), não um novo repositório à parte.

**Modelo de implantação — corrige o desenho anterior deste documento**: `deployo-api-key` não
é um serviço central compartilhado, chamado ou lido remotamente por múltiplos consumidores.
**Cada serviço que usa o gerador tem sua própria tabela `api_keys`, no seu próprio banco** — o
aplicativo `deployo-api-key` é instalado junto com o serviço consumidor no mesmo
`docker-compose` (container próprio, ao lado do container do serviço e do banco dele), gerando
chaves só para esse serviço específico. Isso **resolve** a tensão que este documento registrava
antes com o princípio da Etapa 3 (persistência própria por serviço, sem acesso direto ao banco
de outro serviço): não há banco compartilhado nem acesso cross-serviço — cada instância do
Serviço de E-mail (ou qualquer outro consumidor futuro) tem sua própria cópia da tabela e sua
própria instância do gerador.

**Por que a biblioteca é uma interface — corrige a leitura anterior deste documento**: o motivo
principal não é permitir trocar o algoritmo de geração da chave (esse deve continuar estável).
É abstrair **onde e como a chave fica armazenada** — a interface isola o consumidor (Serviço de
E-mail) de detalhes de local/mecanismo de persistência da tabela `api_keys`, que podem mudar
sem exigir mudança no código de quem só verifica uma chave recebida.

Se um dia a geração em si mudar (algoritmo de hash, formato da chave), a mitigação é simples e
não exige múltiplos verificadores na biblioteca: acrescentar uma coluna de versão na própria
tabela `api_keys` (ex. `key_version`), registrando com qual algoritmo cada chave foi gerada —
linhas antigas e novas convivem na mesma tabela, e a biblioteca lê essa coluna para saber como
verificar cada uma. Mesmo espírito do `schemaVersion` já usado no contrato de mensagem da fila
de e-mail (Iteração 4), só que como coluna em vez de campo de mensagem.

**Fluxo de autenticação:** ainda falta desenhar o diagrama de sequência completo (chamada do
cliente → Serviço de E-mail → validação via a biblioteca de leitura contra a tabela `api_keys`
própria daquele serviço) — o brainstorm original mencionava um trecho que não veio junto no
resumo, e o desenho mudou desde então com a extração do `deployo-api-key`. Candidato a
diagrama de sequência, mesmo padrão de `docs/diagrams/sequencia.md`.

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
reuso declarado nesta conversa, mas afeta convenção de branch/PR e precisa de nome definido. O
`deployo-api-key` (seção 3.1) já foi por esse caminho — repositório próprio, pequeno e
autocontido — o que é um precedente a favor da mesma escolha aqui, mas ainda não foi decidido
explicitamente para o Serviço de E-mail em si.

**Próximos passos técnicos ainda não detalhados** (do brainstorm original, revisados à luz do
`deployo-api-key`):
- ~~Endpoint completo de rotação de key~~ — não é mais um item deste serviço, ver seção 3.1
  ("Rotação: fora de escopo por enquanto").
- Validação de `variables_schema` (JSON Schema) contra o JSON recebido, antes de renderizar —
  continua pendente, não relacionado à mudança de API-KEY.
- Estrutura da fila de envio — ver seção 3.2 (alternativa via SES Templates, ainda não
  escolhida como definitiva) e o log de erros de envio, que continua pendente.

### 3.2 Renderização via SES Templates e correlação por message tags

Revisão levantada em sessão: a Amazon SES tem sistema de template próprio
(`CreateTemplate`/`SendTemplatedEmail` no SESv1, `CreateEmailTemplate` com `Content.Template`
no SESv2). Usá-lo permite reduzir bastante o tamanho da mensagem na fila SQS — ela deixa de
carregar `subject`/`body` já renderizados (potencialmente vários KB de HTML) e passa a
carregar só o nome do template e um JSON pequeno de variáveis.

**Decisão**: adotar `SendTemplatedEmail` (ou o equivalente SESv2) como a estratégia de envio
do Serviço de E-mail, mantendo a Lambda "burra" (Decisão 1 da Iteração 4) — ela repassa
`templateName`/`templateData` pro SES sem interpretar nada, exatamente como hoje repassa
`subject`/`body` sem interpretar. O Serviço de E-mail continua sendo a fonte da verdade do
cadastro de templates (`email_template`, com validação via `variables_schema`), mas ganha uma
responsabilidade nova: **sincronizar cada template criado/atualizado pro SES**
(`CreateTemplate`/`UpdateTemplate`), já que o SES precisa da própria cópia pra fazer a
substituição no momento do envio.

**Contrato de mensagem alternativo para este fluxo** (substitui, só quando o Serviço de
E-mail é quem publica, o contrato original da Iteração 4 — que continua valendo tal como está
para quem publica direto na fila sem passar por ele):

```json
{
  "schemaVersion": "2",
  "correlationId": "uuid",
  "recipientEmail": "...",
  "templateName": "...",
  "templateData": { "...": "..." }
}
```

**Correlação evento → `sent_email` sem tocar no conteúdo do e-mail**: a ideia de embutir um
código no HTML (levantada em sessão) foi descartada — o SES já resolve isso via *message tag*
(`Tags`/`EmailTags` no `SendTemplatedEmail`), mecanismo que já estava planejado desde a
Decisão 10 da Iteração 4 para o fluxo original, e vale igual aqui: o `correlationId` vai como
tag no envio, e com o *Configuration Set* de Event Publishing (Decisão 10) ligado, toda
`Send`/`Delivery`/`Bounce`/`Complaint` publicada no tópico SNS carrega essa mesma tag de volta
(`mail.tags.correlationId`). Nenhuma leitura de conteúdo do e-mail é necessária, e o mecanismo
funciona igual independente de o corpo ter sido renderizado no produtor ou pelo próprio SES.

**Em aberto**:
- SESv1 (`SendTemplatedEmail`) ou SESv2 (`SendEmail` com `Content.Template`)? A v2 é a API
  mais nova e recomendada atualmente pela AWS, mas o `email-lambda` (Iteração 4) já usa um
  `SesClient` — checar qual das duas esse client já expõe antes de decidir.
- **Tensão não resolvida com os 5 templates Thymeleaf já existentes**: o SES usa sintaxe
  Handlebars (`{{variavel}}`, suporte limitado a `{{#if}}`/`{{#each}}`), sem equivalente a
  `th:insert` — os fragmentos de header/footer reaproveitados entre os 5 arquivos
  (`docs/context/iteracao-4.md`, "Catálogo de templates de e-mail") precisariam ser
  duplicados manualmente em cada template do SES, ou o conteúdo final (header+corpo+footer já
  concatenado) pré-montado antes de cadastrar no SES. Não decidido se a redução de tamanho da
  fila compensa esse retrabalho.
- O contrato "renderiza no produtor" (Iteração 4 original) continua existindo em paralelo pra
  quem não usa o cadastro de templates do Serviço de E-mail — a Lambda provavelmente precisa
  distinguir os dois casos pelo `schemaVersion`.

## 4. Sistema de Admin — decisão em aberto (revisada)

O brainstorm original desenhava este sistema especificamente para orquestrar login e chamar os
endpoints `/admin/applications`/`.../keys` do Serviço de E-mail — gerar/rotacionar API keys por
uma UI fina. Com a emissão de API-KEY virando uma CLI operada manualmente
(`deployo-api-key generate`, sem interface HTTP nenhuma — ver seção 3.1), a justificativa
original deste sistema não se aplica mais como estava.

**Decisão em aberto**: o Sistema de Admin ainda faz sentido — para outra finalidade (ex.:
visualizar templates cadastrados, histórico de envio, erros da fila do Serviço de E-mail) — ou
deixa de existir como peça desta iteração, já que a única razão de ser dele no brainstorm
original (gerência de API keys) foi resolvida de outra forma? Se continuar a existir, o login
por e-mail + link (reaproveitando o padrão de `LoginLink` já implementado no `jogo-acoes`,
Iteração 3) continua válido como mecanismo de autenticação do próprio admin, independente da
resposta.

## 5. `jogo-acoes` como primeiro cliente

- Recebe a API key gerada manualmente por quem operar `deployo-api-key generate --service
  jogo-acoes` (variável de ambiente/secret manager) — não há auto-provisionamento do lado do
  cliente nesta fase, e não existe mais um "admin" que gera a chave por trás de uma API (ver
  seção 3.1).
- Chama o Serviço de E-mail com essa key via OpenFeign para enviar e-mail.
- Decisão em aberto (ligada à pendência da seção 3): se o `EmailSender`/`SqsEmailSender` atuais
  do `app/` são removidos e substituídos pelo cliente Feign, ou se convivem durante uma
  transição.

## 6. PDF final e caderno de testes Swagger — por Etapa, em branch próprio

**Revisado (sessão 2026-09-13, ver Issue [#43](https://github.com/lalgarve/jogo-acoes/issues/43)):**
o plano original ("últimos itens da iteração, depois que o resto estiver estável") foi
substituído. PDF e caderno de testes não são código — não precisam esperar a suíte/CI do
`app/` ficar verde pra existir — então passam a ser escritos **por Etapa, em paralelo ao
código**, em vez de acumulados pro final:

- Uma seção do PDF + os casos de teste Swagger correspondentes para cada uma das quatro
  Etapas da disciplina (`alinhamento-projeto-disciplina.md`, seção 4), à medida que cada
  Etapa fica pronta — não as quatro de uma vez no fim.
- **Branch próprio**, separado dos branches de código desta iteração (ex.
  `docs/iteracao-5-final-pdf-test-notebook`) — como não é código, revisão de documento não
  deveria se misturar com revisão de código na mesma PR.

O conteúdo em si:
- PDF final sobre o projeto — para a entrega da disciplina (ver formato de nome de arquivo
  exigido no enunciado: `nomedoaluno_nomedadisciplina_pd.PDF`).
- Caderno de testes para o Swagger — a API não é trivial (múltiplos serviços, autenticação por
  API key, fluxos de e-mail assíncronos), então um roteiro de casos de teste via Swagger UI
  ajuda tanto a validação manual quanto a avaliação do professor.

## 7. Diário desta iteração — continuidade entre sessões

Esta seção existe para o objetivo original de todo `docs/context/iteracao-N.md`, descrito em
`memory/constitution.md`: permitir retomar o trabalho depois de uma troca de chat, ou
depois de uma sessão que travou no meio (ficou repetindo pergunta/resposta sem sair) — não só
descrever o plano inicial da iteração uma única vez. Boa parte do que é decidido/discutido numa
sessão de trabalho não cabe em spec nenhuma (`specs/NN-NNN-*`, quando/se adotado, cobre só
decisão técnica de escopo fechado de uma funcionalidade específica) — é aqui que esse contexto
de processo fica registrado, sessão a sessão, incrementalmente, e não apagado/reescrito a cada
retomada.

### Sessão 2026-09-09/10

**Feito:**
- PR #40 aberta (`docs/alinhamento-projeto-disciplina` → `master`), adicionando
  `docs/context/alinhamento-projeto-disciplina.md` — ainda não mesclada.
- As 16 issues já existentes (#20–#35) foram traduzidas para inglês (título e corpo) e o
  número da iteração saiu do título, passando a viver só como label `iteration-N`. Motivação:
  um campo numérico "Iteration" foi criado no GitHub Project, e a ideia é que um script
  popule esse campo a partir do label ao adicionar a issue ao Project — não existe automação
  nativa do GitHub para isso (ver decisão em aberto abaixo).
- Renumeração aplicada nas issues, refletindo a renumeração já registrada no `roadmap.md`
  atual (que hoje pula de Iteração 6 para Iteração 8 — a antiga Iteração 7 em diante deslocou
  +1): #26→`iteration-8`, #27→`iteration-9`, #28→`iteration-10`, #29→`iteration-11`,
  #30→`iteration-12`, #31→`iteration-13`, #32→`iteration-14`, #33→`iteration-15`,
  #34→`iteration-16`, #35→`iteration-17`. #20–#23 (Iterações 1–4) mantiveram o número, só
  tradução. #24/#25 mantiveram `iteration-5`/`iteration-6` (o número em si não mudou nessas
  duas, só o escopo), com uma nota inserida no corpo de cada uma indicando que o escopo antigo
  ("Redução de bounce no SES", "Deploy, observabilidade e hardening") foi substituído/absorvido
  pelo escopo novo dessas iterações, descrito neste documento e em `docs/roadmap.md`.
- **Estrutura de arquivo do spec-kit criada** (ver seção 1): `memory/constitution.md` (movido
  de `docs/context/desenvolvimento.md`, arquivo antigo removido — conteúdo preservado só no
  caminho novo — e expandido com a seção "Adoção do spec-kit" e a tabela comparando
  `docs/context/iteracao-N.md` × `specs/NN-NNN-slug/`), `templates/` (os 5 templates do
  spec-kit, adaptados de `deployo-template-java` para as convenções já existentes aqui —
  `.feature` em vez de Gherkin inline, `docs/openapi.yaml`/`docs/diagrams/der.md` como
  contrato/DER já existentes em vez de um segundo lugar para redesenhar), `specs/README.md`
  (convenção documentada, nenhuma pasta `NN-NNN-*` criada ainda). Referências a
  `docs/context/desenvolvimento.md` corrigidas em `iteracao-3.md`, `iteracao-4.md`, no
  próprio `ci.yml` e em `CompetitionMother.java` (só correção mecânica de caminho, sem
  reescrever o conteúdo histórico desses documentos).
- **Granularidade de `specs/` resolvida**: por funcionalidade, agrupada por iteração —
  `specs/NN-NNN-slug/` (`NN` = iteração, 2 dígitos, mesmo número do label `iteration-N`;
  `NNN` = sequência da funcionalidade dentro dessa iteração, 3 dígitos, reinicia a cada
  iteração). `memory/constitution.md`, `specs/README.md` (com exemplo,
  `specs/05-001-servico-email-templates/`) e `templates/` atualizados para refletir a
  convenção. Não sobra mais nenhuma decisão bloqueando o primeiro uso de `specs/` — só falta
  a primeira feature ser especificada.

**Decisões em aberto levantadas nesta sessão** (novas, além das já listadas no resumo no fim
deste documento):
- Como popular o campo "Iteration" (numérico) do GitHub Project a partir do label da issue —
  decidido que a fonte da verdade é o label (`iteration-N`), sincronizado por script ao
  adicionar a issue ao Project; o script em si ainda não existe.

**Confirmado nesta sessão:** este arquivo (e os das iterações seguintes) são atualizados
incrementalmente a cada sessão relevante de trabalho — não são escritos uma vez no início do
planejamento e congelados dali em diante. É o mecanismo principal de continuidade entre trocas
de chat ou sessão travada, cobrindo inclusive decisão de processo (como esta) que não tem lugar
em spec nenhuma.

### Sessão 2026-09-13

**Feito:**
- PR #42 aberta (estrutura SDD + correção de `StubEmailSenderTest`/`AuditLogServiceTest`
  contra o vazamento de dados entre classes de teste no H2 compartilhado do perfil
  `sandbox`) — ainda não mesclada. Issue #41 aberta para o mesmo risco, não corrigido, em
  `LogRepositoryTest`.
- **Nova decisão registrada na seção 3.2**: usar `SendTemplatedEmail`/`SendEmail` com
  `Content.Template` do SES para renderizar do lado do SES em vez de no produtor — reduz bem
  o tamanho da mensagem na fila. Correlação evento→`sent_email` continua via *message tag*
  do SES (`correlationId`), não por nenhum código embutido no HTML (ideia cogitada e
  descartada em sessão) — mecanismo que já estava previsto desde a Decisão 10 da Iteração 4.
  Ficaram em aberto: SESv1 vs. SESv2, e como reconciliar os 5 templates Thymeleaf existentes
  (com fragmentos de header/footer) com a sintaxe Handlebars mais simples do SES.
- **Seção 6 revisada e Issue #43 aberta**: PDF final e caderno de testes Swagger deixam de
  ser "só no final" e passam a ser escritos por Etapa da disciplina, em paralelo ao código,
  num branch próprio (separado dos branches de código da iteração), já que não são código e
  não deveriam esperar a suíte ficar verde nem se misturar com revisão de código na mesma PR.

**Confirmado nesta sessão:** o diário continua sendo atualizado a cada sessão relevante,
inclusive para registrar uma decisão pontual de arquitetura (como esta), sem esperar o
fechamento de toda a Iteração 5.

### Sessão 2026-09-15

**Feito:**
- **Primeiras três specs de Iteração 5 escritas** (`specs/05-001-refactor-pacote-base`,
  `specs/05-002-modularizacao-inicial`, `specs/05-003-desacoplamento-login-link`) — tratadas
  como primeiro roadmap de implementação, sujeito a mudar. 05-001 renomeia o pacote base
  (`io.deployo` → `dev.leilaalgarve`, domínio `deployo.io` não é mais da autora); 05-002 é o
  primeiro passo de modularização por domínio (`link`/`competition`/`login`/`log`/`email`,
  depois `captcha`); 05-003 desacopla o módulo `link` dos seus consumidores via
  `LinkRouter`/`LinkHandler`/`LinkDto`.
- `plan.md` da 05-003 escrito depois de ler `LoginService`/`LoginController`/`LoginLink`/
  `EntryRequestService`/`PlayerManagementService`/`CompetitionService` (estado em `master`)
  linha a linha — três achados mudaram o desenho em relação ao que a spec ilustrava
  inicialmente: só existem **dois** `LinkHandler`s de verdade (não três, nem um por serviço
  chamador — três call sites diferentes criam o mesmo formato de link ligado a competição);
  `EntryRequestService.confirmEntry` não usa link/token nenhum (fora de escopo); o caso "link
  ligado a competição, sem conta ainda" é de **duas fases HTTP** sobre o mesmo token
  (`consume` retorna 202 pendente, `complete` fecha o cadastro depois) — a interface
  `LinkHandler` ganhou um segundo método (`complete`, com implementação padrão que recusa)
  por causa disso.
- **Nova decisão (05-003)**: colisão de chave entre dois `LinkHandler`s continua falhando no
  boot do Spring, e ganhou também um **teste dedicado** (`LinkRouterKeyUniquenessTest` ou
  similar) que constrói o `LinkRouter` com as implementações reais e verifica chave não-nula
  e não-duplicada sem precisar subir o contexto inteiro — registrado em `plan.md`.
- **Nova decisão (05-003)**: `LinkRecord` deixa de gravar o DTO inteiro serializado num único
  campo JSON — `userId` e `email` viram **colunas próprias** da entidade (reduzindo a perda de
  integridade referencial que um JSON opaco causaria), e só o campo `extra` do `LinkDto` (o
  que sobra de específico de cada implementação, ex. `participationId`) continua sendo
  serializado, num campo `extraJson` menor. Atualizado em `spec.md` (requisito funcional +
  diagrama "Depois") e `plan.md` (estrutura de pacotes + riscos) daquela spec.
- **Nova decisão (05-002)**: `CaptchaService`/integração ALTCHA ganha módulo próprio,
  `captcha/` — deixou de ser um item em aberto sobre "onde colocar classe que não pertence a
  nenhum módulo".
- **Convenção `client`/`dto`/`exception` confirmada e documentada em 05-002**, contra o
  repositório de referência da disciplina
  ([`elberthmoraes-prof/desenvolvimento-avancado-com-spring-e-microsservicos-26e3-26e3`](https://github.com/elberthmoraes-prof/desenvolvimento-avancado-com-spring-e-microsservicos-26e3-26e3),
  módulo `academico-service`, clonado localmente e lido arquivo a arquivo): `client/` (Feign +
  Gateway que traduz exceções + DTO de resposta remota, usado a partir da Etapa 2/OpenFeign);
  `dto/` (DTOs do próprio módulo, sem sufixo "Dto" no nome da classe); `exception/` por módulo
  só quando esse módulo tiver duas ou mais exceções próprias (uma exceção única fica na raiz
  do pacote), mais um `exception/` global na raiz de `{base}` com o `GlobalExceptionHandler`.
  Nomes de pacote/classe continuam em inglês — só a estrutura é adaptada do repositório de
  referência (em português), não o idioma. Observação registrada: o próprio repositório de
  referência não segue essa regra 100% consistentemente (um módulo mantém duas exceções soltas
  na raiz do pacote, sem subpacote `exception/`) — a spec adota a regra mesmo assim, por ser a
  mais clara de aplicar. Aplicação **incremental**, módulo a módulo, à medida que o trabalho
  avança — não é um requisito upfront da 05-002 em si.

**Confirmado nesta sessão:** o diário continua sendo atualizado a cada sessão relevante de
trabalho, mesmo quando o essencial da decisão técnica já está registrado dentro de uma spec
(`specs/05-002-.../spec.md`, `specs/05-003-.../plan.md`) — este arquivo guarda o resumo
narrativo e o porquê de cada mudança de rumo, papel que uma tabela de decisões dentro de uma
spec não cumpre sozinha.

### Sessão 2026-09-16

**Feito:**
- **Duas decisões em aberto da spec 05-001 resolvidas:** o `groupId` do `pom.xml` muda junto
  com o pacote Java (`io.deployo` → `dev.leilaalgarve`), mesmo não sendo publicado em nenhum
  repositório Maven — mantém coerência entre pacote e coordenada, e as coordenadas dos módulos
  do reator (`app`, `email-lambda`) são revisadas na mesma mudança; e `email-lambda` também
  tem seu pacote renomeado junto com `app/`, no mesmo escopo desta spec (presumido
  `io.deployo.*` como o resto do projeto — confirmar o pacote real ao implementar, mas o
  destino já está decidido). Atualizado em `specs/05-001-refactor-pacote-base/spec.md`.
- **Uma decisão em aberto da spec 05-002 resolvida:** `SecurityConfig` migra para `login/`
  (mesmo raciocínio que já aloca `LoginService`/`LoginController` ali); `ScenarioWorld`/
  fixtures de teste compartilhadas (`testsupport`) e classes de infraestrutura genérica sem
  domínio próprio ganham um módulo novo, `common/` — a modularização inicial passa de seis
  para sete módulos (`link`, `competition`, `login`, `log`, `email`, `captcha`, `common`).
  Atualizado em `specs/05-002-modularizacao-inicial/spec.md` (estrutura de pastas + decisões
  em aberto).
- **`tasks.md` escrito para as três specs**: `05-001` (13 tarefas T001–T013, mudança mecânica
  sem `plan.md` próprio), `05-002` (13 tarefas T001–T013, incluindo T001 como bloqueio
  explícito para confirmar o destino de `PlayerManagementService`/`EntryRequestService` antes
  de mover), `05-003` (19 tarefas T001–T019, quebrando `plan.md` em passos de implementação —
  tipos base, mecanismo genérico, os dois handlers concretos, os testes exigidos pela spec,
  migração das call sites antigas e remoção de código morto).
- **Três Issues-épico abertas**, uma por spec, cada uma com o checklist completo de `tasks.md`
  e labels `iteration-5` + `refactor` (label `refactor` criada nesta sessão, mesmo padrão de
  `docs`/`test` criadas em sessões anteriores): Issue
  [#45](https://github.com/lalgarve/jogo-acoes/issues/45) (05-001), Issue
  [#46](https://github.com/lalgarve/jogo-acoes/issues/46) (05-002), Issue
  [#47](https://github.com/lalgarve/jogo-acoes/issues/47) (05-003). `spec.md`/`tasks.md` de
  cada uma atualizados para apontar pra sua Issue.
- **Correção na 05-003: `LinkDto` renomeado para `LinkPayload`**, movido para o pacote
  `{base}.link.dto` — a spec tinha sido escrita antes de aplicar a convenção de sub-pacotes
  `client`/`dto`/`exception` (documentada em 05-002) ao próprio módulo `link`, e o nome
  original ainda carregava o sufixo "Dto" que essa convenção proíbe. Variáveis/parâmetros
  também renomeados de `dto` para `payload` em `spec.md`/`plan.md`/`tasks.md` para
  consistência. `LoginLinkHandler`/`CompetitionLinkHandler` (módulos `login`/`competition`)
  passam a importar `{base}.link.dto.LinkPayload` como o único tipo de `link` que cruza a
  fronteira do módulo.

**Confirmado nesta sessão:** decisões pontuais de fechamento de spec (como estas) também
entram no diário, não só decisões novas de arquitetura — o objetivo é que quem retomar o
trabalho depois de uma troca de sessão veja aqui, em ordem cronológica, quando e por que cada
"Decisões em aberto" de uma spec foi fechada, sem precisar reconstruir isso só pelo histórico
de commits.

### Sessão 2026-09-16 (continuação) — implementação das três specs

**Feito:** implementação de código das três specs planejadas nesta sessão, nesta ordem
(05-001 → 05-002 → 05-003), em clone local (`add_repo`/`register_repo_root`), com a suíte
completa rodada e verde após cada uma.

- **05-001 (rename de pacote)**: `io.deployo` → `dev.leilaalgarve` em `app/` e
  `email-lambda/` (85 arquivos), `groupId` dos três `pom.xml` (`app`, `email-lambda`, raiz)
  atualizado junto, conforme decidido. Commit
  `refactor: rename base Java package io.deployo to dev.leilaalgarve`. Issue
  [#45](https://github.com/lalgarve/jogo-acoes/issues/45) fechada, `tasks.md` com T001–T013
  riscados.
- **05-002 (modularização)**: ~46 classes principais e de teste redistribuídas nos sete
  módulos (`link`, `login`, `competition`, `log`, `email`, `captcha`, `common`). Descoberto na
  prática: mover classes para dentro de pacotes já existentes (`email/`, `captcha/`) também
  precisa do ajuste de `package`/imports, não só as pastas novas — passo que tinha ficado
  faltando no script inicial e foi corrigido manualmente em 4 arquivos. Imports que deixaram
  de ser "mesmo pacote" após a divisão foram resolvidos com um script Python auxiliar
  (mapa nome-de-classe → FQN), com remoção manual de alguns falsos positivos (nomes de classe
  citados em javadoc/string, não em código de fato). Commit
  `refactor: modularize app/ by domain (link, competition, login, log, email, captcha,
  common)`. Issue [#46](https://github.com/lalgarve/jogo-acoes/issues/46) fechada, `tasks.md`
  com T001–T013 riscados.
- **05-003 (desacoplar `link`)**: implementado `LinkPayload`/`LinkRecord`/`LinkOutcome`/
  `LinkHandler`/`LinkRouter`/`LinkService`/`LinkCreationResult`/`LinkSessionService` em
  `link/`, `LoginLinkHandler`/`LoginLinkSessionService` em `login/`,
  `CompetitionLinkHandler` em `competition/`; `LoginLink`/`LoginLinkRepository`/`LoginService`
  removidos (mortos); migração Flyway `V6__decouple_login_link_into_link_record.sql` (+
  espelho H2). **Três desvios em relação ao desenho original de `plan.md`**, todos detalhados
  em `plan.md` ("Achados feitos durante a implementação") e refletidos em `spec.md`/
  `tasks.md`:
  1. `LinkHandler` ganhou um terceiro método, `alreadyAuthenticated`, não previsto — necessário
     para o atalho "jogador já logado neste dispositivo" que `login.feature` exige (o desenho
     original de 2 métodos não cobria esse caminho sem pular lógica de sessão indevidamente).
  2. Não existe uma classe `LinkController` separada — `LoginController` (já existente, em
     `login/`) passou a delegar `consumeLoginLink`/`completeRegistration` para `LinkService`,
     mantendo `requestLoginLink` como lógica própria. `LinkService.create` passou a devolver
     `LinkCreationResult(id, token)` (não só o token), porque os três call sites de
     `competition` precisam do id numérico para auditoria.
  3. Escopo extra descoberto durante a implementação (fora da lista original de `tasks.md`):
     `LoginSession` (também em `link/` desde a 05-002) tinha uma FK Java direta pra
     `login.User`, violando o mesmo requisito de direção de dependência que motivou o
     redesenho de `LinkRecord` — corrigido trocando `User user` por `Long userId` (sem FK).
  - **Descoberta técnica**: este projeto usa Jackson 3 (`tools.jackson.*`, não
    `com.fasterxml.jackson.*`) — `LinkService` foi escrito inicialmente com os imports errados
    (Jackson 2) e corrigido; `tools.jackson.core.JacksonException` é unchecked (extends
    `RuntimeException`), então não precisa de try/catch ao redor das chamadas de
    serialização/desserialização de `extra`.
  - `docs/diagrams/der.md`/`classes.md` atualizados (`LOGIN_LINK` → `LINK_RECORD`, sem FK para
    `User`/`Participation`); `docs/diagrams/sequencia.md` **não** foi atualizado — sinalizado
    como pendência conhecida (4 diagramas de sequência a refazer), fora do escopo desta
    sessão.
  - Suíte completa (`mvn -pl app -am clean test`): **89 testes, 0 falhas, 0 erros** — todos os
    `.feature` de login/competição sem nenhuma alteração de texto Gherkin, mais os 4 testes
    dedicados novos (`LinkRouterKeyUniquenessTest`, `LoginLinkHandlerTest`,
    `CompetitionLinkHandlerTest`, `LinkServiceTest`); `email-lambda` inalterado (2 testes, 1
    skip, como antes). Commit
    `refactor: decouple the link module from login/competition (LinkRouter/LinkHandler)`.
    Issue [#47](https://github.com/lalgarve/jogo-acoes/issues/47) fechada, `tasks.md` com
    T001–T019 riscados (desvios documentados na própria tabela).

**Confirmado nesta sessão:** a "Decisão em aberto" sobre onde vive a lógica de estabelecer
sessão após consumir um link (`SecurityContext`/`LoginSession`, ver lista de fechamento
abaixo) foi resolvida pela implementação — vive em `LinkSessionService`
(`LoginLinkSessionService`, em `login/`), removida da lista de pendências.

### Sessão 2026-09-16 (continuação) — PR #48 mergeado; preparação da próxima spec (módulo `login`)

**Feito:**
- PR [#48](https://github.com/lalgarve/jogo-acoes/pull/48) (as três specs 05-001/05-002/05-003)
  aberto, CI verde, sem conflito, sem review pendente, e mergeado em `master`.
- A pedido, revisado o estado atual do módulo `login`/`link` como preparação para a próxima
  spec, começando pelos diagramas (antes de desenhar qualquer mudança nova):
  - `docs/diagrams/classes.md` conferido contra o código atual — já estava correto (atualizado
    durante a própria 05-003), nenhuma mudança necessária.
  - `docs/diagrams/sequencia.md` **reescrito** nas seções 1 (login — agora dividida em 1a
    pedido/1b consumo genérico via `LinkService`/`LinkRouter`/`LinkHandler`), 3 (criação de
    competição + convite), 4 (pedido de entrada — mais uma nova seção 4b detalhando o consumo
    em duas fases de `CompetitionLinkHandler`) e 5 (gerência de jogadores): todas ainda citavam
    `LoginService`/`LoginLink`/`LoginLinkRepository`, removidos na 05-003. Seções 2 e 6 não
    mudam (não afetadas pelo refactor). Os dois diagramas mais complexos (1b, 4b) validados via
    Mermaid antes de salvar.
- **Achado durante essa revisão, não relacionado à 05-003**: `EmailValidationService`/
  `MxRecordResolver`/`DisposableDomainRepository`/`DisposableDomain`/`EmailRejectedException`/
  `DisposableDomainRefreshJob` — documentados em `der.md`, `classes.md` ("Verificação de
  e-mail") e `sequencia.md` (seção 2) como se existissem — **não existem no código**, nem há
  tabela `disposable_domain` em nenhuma migração Flyway. Sinalizado com um aviso explícito na
  seção 2 de `sequencia.md` e nesta lista de decisões em aberto, sem alterar `der.md`/
  `classes.md` por ora — decisão de como tratar isso fica para depois, fora do escopo desta
  revisão.

## Decisões em aberto (resumo)

- `app/` também migra para o Config Server, ou mantém profiles locais?
- O `SqsEmailSender`/templates do `app/` migram para dentro do Serviço de E-mail, ou
  convivem temporariamente com ele?
- Serviço de E-mail: repositório próprio (precedente do `deployo-api-key`) ou módulo no reator
  atual?
- Estrutura definitiva da fila de envio — contrato original da Iteração 4 (`subject`/`body`
  já renderizados) ou o revisado na seção 3.2 (`templateName`/`templateData` via SES
  Templates, mensagem menor)? E o log de erros de envio, ainda sem estrutura desenhada.
- SESv1 (`SendTemplatedEmail`) ou SESv2 (`SendEmail` com `Content.Template`) para o fluxo da
  seção 3.2 — depende de checar o que o `SesClient` do `email-lambda` já expõe.
- Como reconciliar os 5 templates Thymeleaf existentes (fragmentos `th:insert` de
  header/footer) com a sintaxe Handlebars mais simples do SES, se a seção 3.2 for adotada —
  duplicar o header/footer em cada template do SES, ou pré-montar o HTML final antes de
  cadastrar.
- Validação de `variables_schema` via JSON Schema — biblioteca e ponto de validação exatos.
- Diagrama do fluxo de autenticação do Serviço de E-mail — precisa ser refeito considerando o
  `deployo-api-key` (não é mais o mesmo fluxo do brainstorm original).
- **Sistema de Admin ainda faz sentido** como peça desta iteração, dado que a emissão de API
  key deixou de precisar de uma UI/API admin (ver seção 4)?
- Desenho exato da interface de leitura do `deployo-api-key` (assinatura, o que ela abstrai de
  local/mecanismo de armazenamento) — o princípio (interface + coluna de versão se a geração
  mudar) já está definido, falta o desenho concreto (ver seção 3.1).
- Script de sincronização label → campo "Iteration" do GitHub Project (ver seção 7).
- Nome definitivo do branch de PDF/caderno de testes e conteúdo detalhado de cada seção por
  Etapa — rastreado na Issue #43, não neste documento.
- **`EmailValidationService`/`MxRecordResolver`/`DisposableDomainRepository` nunca foram
  implementados**, apesar de documentados como se existissem em `der.md` (`DISPOSABLE_DOMAIN`),
  `classes.md` ("Verificação de e-mail") e `sequencia.md` (seção 2) — confirmado por grep no
  código nesta sessão (nenhuma classe, nenhuma tabela `disposable_domain` em nenhuma migração
  Flyway). Sinalizado nos três lugares; decidir depois se vira spec própria ou se a
  documentação deve ser corrigida para remover a menção.
