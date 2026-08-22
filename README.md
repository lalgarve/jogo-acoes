# Jogo de Ações

Um jogo de simulação de investimentos em bolsa: administradores criam competições
(públicas ou privadas), jogadores entram via link de login enviado por e-mail e negociam
ações com dados de mercado reais, competindo pela melhor evolução de portfólio.

## O que o sistema faz

Um administrador cria uma competição — pública (qualquer jogador pode pedir entrada) ou
privada (só quem é convidado por e-mail). Em ambos os casos o jogador entra via um link de
login enviado por e-mail, sem senha: pede o link, recebe, clica, está dentro. Antes de aceitar
qualquer e-mail (convite, pedido de entrada ou pedido de login), o sistema verifica se o
domínio tem registro MX válido e não está numa lista de domínios descartáveis/temporários —
reduz o volume de e-mails que nunca chegariam a lugar nenhum. Administradores também têm
visão e controle sobre os jogadores de cada competição: reenviar um convite, remover um
jogador.

- **Sistema principal** (`app/`, Spring Boot): API REST (gerada a partir de
  [`docs/openapi.yaml`](docs/openapi.yaml)), persistência JPA/PostgreSQL, autenticação por
  link de login, ALTCHA como captcha (prova de trabalho auto-hospedada, sem serviço
  terceirizado), log de auditoria.
- **`email-lambda/`**: AWS Lambda (Quarkus, com suporte a imagem nativa GraalVM) que consome
  uma fila Amazon SQS e envia o e-mail via Amazon SES — desacoplada do sistema principal, que
  só publica na fila e nunca fala com o SES diretamente.
- **Especificação de domínio (BDD)**: cada fluxo (login, criação de competição, gerência de
  jogadores, pedido de entrada) tem cenários Gherkin cobrindo caminho feliz e casos de erro,
  executados a cada mudança.
- **Modelo de dados** em [`docs/diagrams/der.md`](docs/diagrams/der.md), com diagrama de
  classes e de sequência complementares em
  [`docs/diagrams/classes.md`](docs/diagrams/classes.md) e
  [`docs/diagrams/sequencia.md`](docs/diagrams/sequencia.md).
- **Integração contínua** (`.github/workflows/ci.yml`): suíte de testes com piso de cobertura
  de linha (JaCoCo, 80%) rodando a cada *pull request* contra infraestrutura real (Postgres,
  fila SQS) via Docker, não contra simulação em memória.

## Arquitetura planejada

- Negociação de ações com cotações de mercado reais via [Brapi](https://brapi.dev).
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
| `email-lambda/` | Quarkus | AWS Lambda que consome a fila de e-mail e envia via SES |

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
`GRANT`/`REVOKE` de papéis de banco que só existem no Postgres real.

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
