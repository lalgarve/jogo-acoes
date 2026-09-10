# Specs

Cada feature não trivial ganha uma pasta `NNN-nome-da-feature/` aqui (numeração sequencial
de 3 dígitos, nome em kebab-case), copiada a partir de `templates/`:

```
specs/
  001-nome-da-feature/
    spec.md            # requisitos + critérios de aceite (o QUÊ e POR QUÊ)
    plan.md             # decisões técnicas (o COMO)
    data-model.md         # só se a feature tiver dados persistentes não cobertos pelo DER geral
    contracts/
      feign-client.md       # ou queue-message.md, etc. — só para interfaces não-REST
    tasks.md                # tarefas, espelhadas como Issues no GitHub
```

Ver `memory/constitution.md` (seções "Adoção do spec-kit", "Documentação viva por feature" e
"Rastreamento de trabalho via Issues") para o fluxo completo.

**Ainda não há nenhuma pasta `NNN-*` aqui.** A granularidade — uma pasta por iteração inteira
(equivalente a `docs/context/iteracao-N.md`) ou uma por funcionalidade dentro dela — é uma
decisão em aberto, registrada em `docs/context/iteracao-5.md`. A primeira pasta nasce quando
essa decisão for tomada, para a primeira feature da Iteração 5 especificada dessa forma (ver
esse mesmo documento para o que está planejado).

**Isto não substitui `docs/context/iteracao-N.md`** — aqueles documentos continuam sendo o
diário de cada iteração (decisões de processo, continuidade entre sessões de chat); `specs/`
cobre só a especificação técnica fechada de uma feature específica. Ver a tabela comparativa
em `memory/constitution.md`.
