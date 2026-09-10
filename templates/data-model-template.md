# Data model: <nome da feature>

Só necessário quando a feature envolve dados persistentes ou estruturas de domínio não
triviais. Escrito antes das entidades de código.

Este projeto já mantém um DER único e cumulativo em
[`docs/diagrams/der.md`](../../docs/diagrams/der.md) (ver `memory/constitution.md`) — as
novas entidades desta feature entram lá, não como um diagrama paralelo aqui. Este arquivo
detalha o que o DER geral não cobre em nível de campo/validação.

## Entidades

### <NomeDaEntidade>

| Campo | Tipo | Obrigatório | Validação |
|---|---|---|---|
| <campo> | <tipo> | sim/não | <regra> |

## Relacionamentos

<só se não estiver claro já no DER geral — referenciar `docs/diagrams/der.md` em vez de
redesenhar>

## Invariantes

<regras que devem sempre valer, independente de como a entidade é criada/alterada>
