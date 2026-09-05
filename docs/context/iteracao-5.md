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
- Fila de envio (estrutura ainda não detalhada — ver pendências abaixo; pode reaproveitar o
  contrato de mensagem já validado na Iteração 4, `schemaVersion`/`correlationId`/
  `recipientEmail`/`subject`/`body`, em vez de desenhar um novo).
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
- Estrutura da fila de envio e do log de erros de envio — continua pendente.

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
- Serviço de E-mail: repositório próprio (precedente do `deployo-api-key`) ou módulo no reator
  atual?
- Estrutura da fila de envio e do log de erros do Serviço de E-mail.
- Validação de `variables_schema` via JSON Schema — biblioteca e ponto de validação exatos.
- Diagrama do fluxo de autenticação do Serviço de E-mail — precisa ser refeito considerando o
  `deployo-api-key` (não é mais o mesmo fluxo do brainstorm original).
- **Sistema de Admin ainda faz sentido** como peça desta iteração, dado que a emissão de API
  key deixou de precisar de uma UI/API admin (ver seção 4)?
- Desenho exato da interface de leitura do `deployo-api-key` (assinatura, o que ela abstrai de
  local/mecanismo de armazenamento) — o princípio (interface + coluna de versão se a geração
  mudar) já está definido, falta o desenho concreto (ver seção 3.1).
