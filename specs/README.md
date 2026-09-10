# Specs

Cada feature não trivial ganha uma pasta `NN-NNN-nome-da-feature/` aqui, copiada a partir de
`templates/`:

- `NN` — número da iteração (2 dígitos, ex. `05`), mesmo número do label `iteration-N` da
  Issue (ver `memory/constitution.md`).
- `NNN` — número sequencial da funcionalidade **dentro dessa iteração** (3 dígitos, reinicia
  a cada iteração — a primeira feature da Iteração 5 é `001`, a segunda `002`, e a primeira
  feature de uma Iteração 8 volta a ser `001`).
- `nome-da-feature` — kebab-case.

```
specs/
  05-001-servico-email-templates/
    spec.md            # requisitos + critérios de aceite (o QUÊ e POR QUÊ)
    plan.md             # decisões técnicas (o COMO)
    data-model.md         # só se a feature tiver dados persistentes não cobertos pelo DER geral
    contracts/
      feign-client.md       # ou queue-message.md, etc. — só para interfaces não-REST
    tasks.md                # tarefas, espelhadas como Issues no GitHub
  05-002-servico-email-envio/
    spec.md
    plan.md
    tasks.md
```

Ver `memory/constitution.md` (seções "Adoção do spec-kit", "Documentação viva por feature" e
"Rastreamento de trabalho via Issues") para o fluxo completo.

**Granularidade — resolvida (sessão 2026-09-10, registrada em
`docs/context/iteracao-5.md`)**: por funcionalidade, agrupada por iteração — o `NN-` acima é
o que preserva o agrupamento sem exigir uma pasta por iteração inteira. Nenhuma pasta ainda
existe aqui; a primeira nasce com a primeira feature da Iteração 5 especificada nesse
formato (ver esse mesmo documento para o que está planejado).

**Isto não substitui `docs/context/iteracao-N.md`** — aqueles documentos continuam sendo o
diário de cada iteração (decisões de processo, continuidade entre sessões de chat); `specs/`
cobre só a especificação técnica fechada de uma feature específica. Ver a tabela comparativa
em `memory/constitution.md`.
