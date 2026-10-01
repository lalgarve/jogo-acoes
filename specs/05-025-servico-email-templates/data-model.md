# Data model: Serviço de E-mail — cadastro de templates

**Desvio deliberado da convenção padrão** (`templates/data-model-template.md` manda usar o DER
único em `docs/diagrams/der.md`): aquele DER é escopado ao domínio do `app` ("DER — Jogo de
Ações", derivado dos `.feature` de `app/src/test/resources/features`). `email-service` é um
serviço à parte, com persistência própria (spec.md, "Requisitos não-funcionais") — sua(s)
entidade(s) não fazem parte do domínio de `jogo-acoes` e vivem num banco físico separado (ver
`plan.md`, "Banco de dados dedicado"). Por isso ganham este arquivo próprio em vez de entrar no
DER geral — mesmo espírito de isolamento que já levou `deployo-api-key` a ter sua própria tabela
`api_keys`, fora de qualquer DER do `jogo-acoes`.

## Entidades

### EmailTemplate

| Campo | Tipo | Obrigatório | Validação |
|---|---|---|---|
| `id` | `bigint` (identity) | sim | gerado pelo banco |
| `client_id` | `varchar(255)` | sim | esqueleto nesta spec: valor bruto do header `X-API-Key` recebido no cadastro (spec.md, "Decisões em aberto") |
| `name` | `varchar(255)` | sim | único junto com `client_id` (ver "Invariantes") |
| `subject` | `text` | sim | não vazio |
| `body` | `text` | sim | não vazio; sintaxe Handlebars do SES — validada de fato pela chamada `CreateTemplate`/`UpdateTemplate`, não por este serviço |
| `variables_schema` | `jsonb` | não | só armazenado nesta spec, nunca validado contra os dados de um preview (spec.md, "Fora de escopo") |
| `ses_template_name` | `varchar(510)` | sim | nome namespaced efetivamente registrado no SES (`<client_id>__<name>`) — guardado explicitamente em vez de recalculado, para nunca ficar órfão se o esquema de namespacing mudar depois |
| `created_at` | `timestamp` | sim | definido na criação |
| `updated_at` | `timestamp` | sim | atualizado a cada `UpdateTemplate` bem-sucedido |

## Relacionamentos

Nenhum — entidade única, sem FK para nada (nem para `app`'s `USER`/`COMPETITION`: este serviço
não conhece o domínio de `jogo-acoes`, só o `client_id` opaco de quem chama).

## Invariantes

- `(client_id, name)` é único — um cliente não pode ter dois templates com o mesmo nome; nomes
  iguais de clientes diferentes são templates distintos, sem nenhuma relação entre si.
- Uma linha só é gravada (`INSERT`) ou atualizada (`UPDATE`) **depois** da chamada
  `CreateTemplate`/`UpdateTemplate` ao SES ter retornado sucesso — nunca antes. Isso evita uma
  linha no banco apontar para um template que não existe de fato no SES (ver `plan.md`, "Ordem
  das operações").
- `ses_template_name` nunca muda depois de criado, mesmo que o esquema de namespacing evolua no
  futuro — uma migração de dados trataria isso explicitamente, não é um valor recalculado em
  tempo de leitura.
