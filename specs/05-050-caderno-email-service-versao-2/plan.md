# Plan: Caderno de testes do Serviço de E-mail, versão 2

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `docker-compose.yml`, serviço `localstack`: imagem `localstack/localstack:4`,
  `SERVICES: sqs,ses,s3,lambda`, socket do Docker montado (o executor de Lambda cria um container
  irmão por função) e `docker/localstack/init/` montado em `ready.d` (fila, remetente verificado,
  implantação da `email-lambda` com o *event source mapping* na fila).
- O LocalStack Pro (`localstack/localstack-pro`) é a mesma base com licença: na subida ele ativa a
  licença em `https://api.localstack.cloud` com o token da variável `LOCALSTACK_AUTH_TOKEN`. Sem
  token ou sem acesso a esse endereço, sai com o código 55 e a mensagem "License activation
  failed".
- A Lambda usa a imagem `public.ecr.aws/lambda/java:21` (runtime `java21`), baixada pelo
  LocalStack na primeira invocação.
- CI (`.github/workflows/ci.yml`) sobe o mesmo serviço `localstack` do Compose.
- `.gitignore` não tem regra para `.env`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Imagem | `localstack/localstack-pro:4` | proposta | Decisão da Leila; tag igual à de hoje (spec, decisão em aberto 3). |
| Token | `LOCALSTACK_AUTH_TOKEN: ${LOCALSTACK_AUTH_TOKEN:-}` no `environment` do `localstack` | proposta | Lido do ambiente de quem roda o Compose; nunca versionado. `:-` pela decisão em aberto 1. |
| `.env` | `.env` no `.gitignore` | proposta | O Compose lê `.env` da raiz sozinho; quem preferir guardar o token ali não corre o risco de commitar. |
| CI | `env: LOCALSTACK_AUTH_TOKEN: ${{ secrets.LOCALSTACK_AUTH_TOKEN }}` no job que sobe o Compose | proposta | Sem isso o LocalStack não sobe no CI. O segredo é criado pela Leila. |
| Captura | Rodar cada roteiro na ordem, sobre o banco vazio da seção 1 (`down -v`), e colar no caderno a saída real encurtada com `…`, como no caderno do `jogo-acoes` | proposta | Mesmo formato da 05-040. A data da captura vai no topo do caderno. |
| Logs | Trechos do `email-service` (`docker compose logs email-service`) e da `email-lambda` (containers criados pelo LocalStack, vistos no Dozzle ou com `docker logs`) | proposta | O caderno já aponta o Dozzle na tabela de programas. |
| `SELECT`s | Um por passo que muda estado: `client_template`/templates, `disposable_domain_source`, `disposable_domain`, tabelas do Spring Batch (`batch_job_execution`, `batch_step_execution`), `client_sender`; nomes conferidos no schema na hora da captura | proposta | Mostram o estado gravado, como a 05-040. |
| Versão | O parágrafo "Versão 1 — antes da implementação" vira "Versão 2", com a data da execução e o commit | proposta | Rastreia de qual código os resultados vieram. |

## Estrutura de arquivos

- `docker-compose.yml`: imagem e variável do `localstack`.
- `.github/workflows/ci.yml`: segredo.
- `.gitignore`: `.env`.
- `README.md`: seção de como rodar com o token (exportar no WSL ou `.env`).
- `docs/disciplina/caderno-de-testes-email-service.md`: preparação com o token, roteiros com
  resposta real, log e `SELECT`, seção "Achados".
- `docs/disciplina/caderno-de-testes.md`: uma linha na preparação sobre o token.
- `docs/disciplina/CLAUDE.md`: registro da versão 2.

## Riscos

- **Execução no ambiente de nuvem da Claude:** os containers não alcançam a internet pelo proxy da
  sessão (o proxy só escuta em `127.0.0.1` do host). Sem isso a licença do LocalStack Pro não
  ativa e o build Maven dentro do Docker (`email-lambda-builder`, imagens do `email-service` e do
  `config-server`) não baixa dependências. A captura precisa ou desse acesso liberado no ambiente
  ou ser feita na máquina da Leila.
- **Respostas diferentes da versão 1:** esperado em alguns passos; tratado em "Achados" (spec,
  cenário 4), sem corrigir código nesta spec.
