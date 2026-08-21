# Jogo de Ações

Um jogo de simulação de investimentos em bolsa: administradores criam competições
(públicas ou privadas), jogadores entram via link de login enviado por e-mail e negociam
ações com dados de mercado reais, competindo pela melhor evolução de portfólio.

## Estado atual do projeto

Iterações 1–4 do [roadmap](docs/roadmap.md) implementadas: especificação de domínio,
esqueleto da aplicação, os quatro fluxos principais (login, criação de competição, convite/
entrada de jogadores) rodando de ponta a ponta, e a infraestrutura assíncrona de e-mail
(SQS → Lambda → SES, com LocalStack substituindo a AWS real em CI). O que existe hoje:

- **Sistema principal** (`app/`, Spring Boot): API REST (gerada a partir de
  [`docs/openapi.yaml`](docs/openapi.yaml)), persistência JPA/PostgreSQL, autenticação por
  link de login, ALTCHA como captcha, log de auditoria.
- **`email-lambda/`** (Quarkus): AWS Lambda que consome a fila de e-mail e envia via SES —
  ver [`docs/context/iteracao-4.md`](docs/context/iteracao-4.md) pra decisões e pendências (a
  maioria bloqueada por acesso a uma conta AWS real, não por código faltando).
- **Especificação de domínio (BDD)** em `app/src/test/resources/features`: cenários Gherkin
  de login, criação de competição, gerência de jogadores e pedido de entrada em competição —
  todos implementados e passando.
- **Modelo de dados** em [`docs/diagrams/der.md`](docs/diagrams/der.md): diagrama
  entidade-relacionamento do domínio. [`docs/diagrams/classes.md`](docs/diagrams/classes.md)
  e [`docs/diagrams/sequencia.md`](docs/diagrams/sequencia.md) complementam com diagrama de
  classes e de sequência dos fluxos implementados (e, marcado à parte, do que já foi decidido
  mas ainda não tem código).
- **CI** (`.github/workflows/ci.yml`): testes com cobertura (JaCoCo, piso de 80%) contra
  Postgres/LocalStack reais a cada PR.
- **Plano de iterações** em [`docs/roadmap.md`](docs/roadmap.md): o que falta, da negociação
  de ações em diante até gráficos e acessibilidade.

## Arquitetura planejada

- Backend em Spring Boot, persistência em PostgreSQL.
- Cotações de ações via [Brapi](https://brapi.dev).
- Envio assíncrono de e-mail: VPS (Spring Boot) publica mensagens numa fila Amazon SQS,
  consumida por uma AWS Lambda que envia via Amazon SES.
- Contabilidade da competição modelada como partida dobrada (lançamentos insert-only,
  débito/crédito em contas separadas), no estilo de sistemas financeiros reais.
- Acessibilidade de gráficos com dois modos: descrição textual por pontos relevantes da
  série e sonorização (grave/agudo por valor, estéreo por tempo), com a parte mais pesada
  implementada em Rust compilado para WebAssembly.

Detalhes de cada etapa estão em [`docs/roadmap.md`](docs/roadmap.md).

## Módulos

Reator Maven multi-módulo (`pom.xml` na raiz é só um agregador, não é *parent* de nenhum
dos dois — cada módulo mantém seu próprio *parent*/BOM):

| Módulo | Framework | O quê |
|---|---|---|
| `app/` | Spring Boot | O sistema principal (API, persistência, regras de negócio) |
| `email-lambda/` | Quarkus | AWS Lambda que consome a fila de e-mail e envia via SES (ver [`docs/context/iteracao-4.md`](docs/context/iteracao-4.md)) |

`mvn verify` na raiz builda os dois. Pra rodar só um: `mvn -pl app -am verify` ou
`mvn -pl email-lambda -am verify`.

## Ambientes

O perfil ativo do Spring é escolhido por `SPRING_PROFILES_ACTIVE` (ou `sandbox`, se
nenhum for definido). Cada um tem seu arquivo `application-<nome>.yml` em
`app/src/main/resources`:

| Perfil | Banco | Quando usar |
|---|---|---|
| `sandbox` (padrão) | H2 embarcado, migrations em `db/migration-h2` | Rodar/testar sem precisar de Docker nem Postgres instalado |
| `docker` | PostgreSQL real em containers | Localmente via `docker-compose up`, ou CI |
| `staging` | PostgreSQL real, gerido por outra equipe | Pré-produção |
| `production` | PostgreSQL real, gerido por outra equipe | Produção |

`sandbox` e `docker` compartilham o mesmo modelo de dados, mas em pastas de migration
separadas (`db/migration-h2` e `db/migration`) — a versão para H2 não tem os comandos
`GRANT`/`REVOKE` de papéis de banco que só existem no Postgres real. Mais detalhes em
[`docs/context/desenvolvimento.md`](docs/context/desenvolvimento.md#nomenclatura-de-ambientes).

Para rodar localmente com Postgres real:

```
docker-compose up
```

## Licença

Este projeto está licenciado sob a GNU General Public License v3.0 (ou, a seu critério,
qualquer versão posterior) — veja o arquivo [LICENSE](LICENSE) para o texto completo.

```
Jogo de Ações
Copyright (C) 2026 Leila Algarve

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
