# Spec: Serviço de E-mail — cadastro de templates

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Novo módulo `email-service`, no mesmo reator Maven de `app`/`email-lambda`, expõe uma API REST
protegida por API-KEY para que clientes (a começar por
`jogo-acoes`) cadastrem os templates de e-mail que vão usar. Ao cadastrar/atualizar um
template, o serviço sincroniza com o Amazon SES (`CreateTemplate`/`UpdateTemplate`) — é essa
chamada que valida de verdade a sintaxe contra a AWS. O cliente também pode pedir uma
pré-visualização renderizada (`TestRenderTemplate`) sem enviar e-mail nenhum.

Esta é a primeira spec do Serviço de E-mail (brainstorm em `docs/context/iteracao-5.md`, seção
3) — cobre só cadastro de templates. Envio de e-mail propriamente dito fica para uma spec
seguinte.

## Motivação

`app/` hoje tem seu próprio `EmailTemplate`/`EmailContentRenderer`/templates Thymeleaf,
cadastrados em código, só para uso interno (ver `app/src/main/java/dev/leilaalgarve/jogoacoes/
email/`). O brainstorm da Iteração 5 já desenhou um Serviço de E-mail reutilizável por múltiplos
clientes, cuja primeira responsabilidade é ser a fonte da verdade do cadastro de templates — não
cada cliente hardcoding o próprio. Sem isso, não há como um cliente definir ou alterar o
conteúdo de um e-mail sem fazer deploy de código Java, nem como validar que um template é
sintaticamente aceito pelo SES antes do primeiro envio real.

## Cenários (comportamento esperado)

**Decidido:** Gherkin/Cucumber, mesmo padrão de `app/src/test/resources/features` — e API-first
via OpenAPI, contrato escrito antes de qualquer controller (mesma convenção já usada em
`docs/openapi.yaml` para `app/`, ver `memory/constitution.md`, "Documentação viva por feature").

- Contrato: [`docs/openapi-email-service.yaml`](../../docs/openapi-email-service.yaml) — rotas
  `GET/POST /templates`, `GET/PUT /templates/{name}`, `POST /templates/{name}/preview`.
- `.feature` Gherkin: ainda não escrito — próximo passo antes do código de implementação (fica
  em `email-service/src/test/resources/features/`, mesma convenção de `app/`).

## Requisitos funcionais

- Toda rota exige um header `X-API-Key`. **Nesta spec, a validação é um esqueleto**: qualquer
  valor não vazio é aceito como válido — a validação de verdade (formato, hash, expiração,
  revogação) fica para uma spec futura, quando existir uma biblioteca externa para isso (ver
  "Decisões em aberto").
- Mesmo no esqueleto, cada chamada precisa resolver "qual cliente está chamando" a partir da
  própria chave recebida — não de um campo separado na requisição. Nesta fase, o valor bruto da
  API-KEY enviada já serve como identificador do cliente (ex.: a própria string da chave vira o
  nome do cliente dono dos templates). Isso isola a resolução de identidade num componente
  único, para que a troca pela validação real, quando existir, não mude o contrato dos demais
  endpoints.
- Operação: cliente cadastra um template novo — nome (único por cliente), assunto, corpo
  (sintaxe Handlebars do SES) e, opcionalmente, um schema das variáveis esperadas.
- Operação: cliente atualiza um template já cadastrado (mesmo nome, mesmo cliente).
- Ao criar/atualizar, o serviço sincroniza o template com o SES real via `CreateTemplate`/
  `UpdateTemplate`. O nome do template no SES é namespaced por cliente (sugestão: `<cliente>__
  <nome>`) para nunca colidir entre clientes diferentes. Se o SES rejeitar (sintaxe inválida), o
  cadastro falha e o motivo retornado pelo SES é repassado ao cliente.
- Operação: cliente pede uma pré-visualização de um template já cadastrado, com dados de
  exemplo, via `TestRenderTemplate` do SES — devolve o assunto/corpo renderizados, sem enviar
  e-mail nenhum.
- Um cliente só enxerga e só altera os próprios templates — nunca os de outro cliente.

## Requisitos não-funcionais

- Roda como processo Spring Boot próprio (módulo novo, ao lado de `app`/`email-lambda` no mesmo
  reator Maven) — não é uma biblioteca embutida em `app/`.
- Persistência própria (banco/schema dedicado a este módulo) — mesmo princípio de isolamento por
  serviço já declarado no roadmap da Iteração 5.
- A resolução "chave → cliente" (hoje um esqueleto) precisa ser um componente isolado o
  suficiente para ser substituído por uma validação real sem alterar o contrato HTTP dos
  endpoints de template.

## Fora de escopo

- Validação de verdade da API-KEY (formato, hash, expiração, revogação) — depende de uma
  biblioteca externa que ainda não existe. O projeto de referência (`deployo-api-key`) só tem o
  lado de emissão (CLI `generate`/`revoke`/`list`), nenhum pacote de validação/leitura ainda, e
  seu próprio nome está para mudar (o domínio `deployo.io` não existe) — por isso esta spec não
  cria nenhuma dependência de código nele.
- Endpoint de envio de e-mail (`SendTemplatedEmail`) — fica para a spec seguinte do Serviço de
  E-mail (roadmap, seção 3: "renderiza → valida anti-bounce → enfileira").
- Qualquer UI de administração de templates.
- Migrar o `EmailTemplate`/`EmailContentRenderer`/templates Thymeleaf hoje em `app/` para este
  serviço — decisão em aberto separada (`docs/context/iteracao-5.md`, seção 3, "Relação com a
  infraestrutura da Iteração 4"), não resolvida por esta spec.
- Repositório próprio para este serviço — decidido nesta sessão que ele é um módulo no reator
  Maven atual de `jogo-acoes`, não um repositório novo (diferente do precedente do
  `deployo-api-key`).
- Validar `variables_schema` (JSON Schema) contra os dados enviados numa pré-visualização — fica
  para quando essa necessidade aparecer; nesta spec o schema é só armazenado, não verificado.

## Decisões em aberto

Resolvidas nesta sessão (registradas aqui, detalhamento técnico em `plan.md`):

- ~~Nome do módulo~~ — `email-service`, confirmado.
- ~~Esquema de namespacing do template no SES~~ — `<cliente>__<nome>`, confirmado.
- ~~Framework de cenário de aceite~~ — Gherkin/Cucumber + API-first via OpenAPI, confirmado (ver
  "Cenários" acima).

Ainda em aberto:

- Onde a validação real de API-KEY vai morar quando existir: biblioteca externa (projeto hoje
  chamado `deployo-api-key`, nome a trocar) consumida por este serviço, ou implementação própria
  aqui? Fica para a spec que substituir o esqueleto atual.
