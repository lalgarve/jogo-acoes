# Contrato de interface: <nome da feature>

Escrito antes da implementação — a implementação segue o contrato.

**Endpoint REST?** Este projeto já é contract-first via
[`docs/openapi.yaml`](../../docs/openapi.yaml) (ver `memory/constitution.md`) — adicionar o
endpoint lá, não aqui. Este template é para interfaces que o OpenAPI não cobre: CLI, contrato
de mensagem de fila, ou o consumo de um serviço externo via cliente Feign (ex.: `jogo-acoes`
chamando o Serviço de E-mail, `docs/context/iteracao-5.md`). Adapte a estrutura (e o nome do
arquivo, ex. `endpoints.md`, `queue-message.md`, `feign-client.md`) ao tipo de interface que a
feature expõe ou consome.

## Comando: `<comando>`

**Uso:**
```
<nome-do-binario> <comando> [opções]
```

**Argumentos:**

| Argumento | Obrigatório | Descrição |
|---|---|---|
| `--<flag>` | sim/não | <descrição> |

**Saída (stdout), sucesso:**
```
<exemplo de saída>
```

**Exit codes:**

| Código | Significado |
|---|---|
| 0 | Sucesso |
| 1 | <erro esperado> |

**Erros (stderr):**

| Condição | Mensagem | Exit code |
|---|---|---|
| <condição> | <mensagem> | <código> |
