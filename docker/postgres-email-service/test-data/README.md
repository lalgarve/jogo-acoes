# API-KEY de teste do Serviço de E-mail

Chave usada só no ambiente `docker` (ver `memory/constitution.md`, "Nomenclatura
de ambientes") para testar o Serviço de E-mail com a validação real de API-KEY da spec
[05-030](../../../specs/05-030-validacao-api-key-servico-email/spec.md). **Nunca** usar estes
valores em `staging`/`production`: estão versionados de propósito, não são segredo.

| O quê | Valor |
|---|---|
| Cliente (`--client`) | `jogo-acoes` |
| API-KEY (texto puro) | `dak_IpfF8aaAizW6r1rSC59yi6BwMs4ox3GQDPiAWamRucU` |
| Pepper do HMAC (`API_KEY_HMAC_PEPPER`) | `jogo-acoes-test-pepper-not-a-secret` |
| Validade | sem expiração, não revogada |
| Gerada com | CLI [`lalgarve/api-key` v1.0.0](https://github.com/lalgarve/api-key/releases/tag/v1.0.0) em 2026-10-03; schema atualizado para a [v1.0.1](https://github.com/lalgarve/api-key/releases/tag/v1.0.1) em 2026-10-04 (tabela de histórico renomeada, mesma chave) |

A chave em texto puro só aparece uma vez, na saída do `generate`, e não dá para recuperá-la a
partir do banco (só o hash HMAC-SHA256 fica gravado). Por isso o resultado da geração foi
salvo em [`api-key-test-data.sql`](api-key-test-data.sql): restaurar esse dump num banco novo
(volume do Docker recriado, por exemplo) torna a mesma chave válida de novo, sem gerar
outra e sem atualizar todos os lugares que a usam. O hash só bate com o pepper acima — com
outro pepper, a chave volta `NOT_FOUND`.

## Onde os dados ficam

Schema `api_key` do banco `email_service` (container `db-email-service`, porta 5433), separado
do schema das tabelas do próprio Serviço de E-mail — ver `plan.md` da spec 05-030, "Um schema
por serviço, nunca o `public`". O dump contém o schema inteiro: a tabela `api_keys`
(uma linha) e o histórico do Flyway da CLI (`api_key_schema_history`, nome usado desde a 1.0.1), para que a CLI reconheça o schema como já
migrado ao rodar de novo contra ele.

## Restaurar

Com o `db-email-service` de pé (`docker compose up -d --wait db-email-service`):

```
./scripts/test-api-key.sh restore
```

Apaga e recria só o schema `api_key` — as tabelas do Serviço de E-mail (templates) não são tocadas. Pode rodar
quantas vezes quiser. Depois, define o remetente do cliente `jogo-acoes` como
`no-reply@jogo-acoes.example` (spec [05-031](../../../specs/05-031-servico-email-envio/spec.md),
`scripts/set-email-sender.sh`), o endereço que o LocalStack verifica na subida. Isso só acontece
se o `email-service` já subiu alguma vez contra o banco (é o Flyway dele que cria a tabela
`client_sender`); senão o script avisa e basta rodar de novo depois. Sem o container rodando, usa o `psql` local contra `localhost:5433`
(um Postgres fora do Compose); `--host`/`--port` mudam o destino.

## Gerar uma chave nova (só se for de propósito)

```
export API_KEY_HMAC_PEPPER=jogo-acoes-test-pepper-not-a-secret
export SPRING_PROFILES_ACTIVE=docker
export SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5433/email_service?currentSchema=api_key'
export SPRING_DATASOURCE_USERNAME=email_service_admin
export SPRING_DATASOURCE_PASSWORD=email_service_admin
export SPRING_FLYWAY_SCHEMAS=api_key
java -jar api-key-1.0.1.jar generate --client jogo-acoes
./scripts/test-api-key.sh dump
```

`currentSchema`/`SPRING_FLYWAY_SCHEMAS` fazem a CLI criar e migrar o schema `api_key` em vez do
`public`, que nenhum serviço usa. Depois do `dump`, atualizar a tabela acima e a variável `EMAIL_SERVICE_API_KEY` do
serviço `app` em `docker-compose.yml`.
