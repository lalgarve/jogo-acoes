# Spec: Container de UI web para o PostgreSQL

**Status:** rascunho
**Issue:** #<número da Issue-épico, quando criada>
**Iteração:** iteration-5

## Resumo

Adiciona ao `docker-compose.yml` um serviço de UI web pra inspecionar o PostgreSQL do perfil
`docker` sem precisar de `psql`/cliente instalado — usado, entre outras coisas, pelos `SELECT`s
de verificação do caderno de testes (spec 05-012).

## Motivação

Hoje a única forma de olhar o banco do `docker-compose.yml` é `psql` via linha de comando (ou um
cliente externo configurado à mão) — uma UI web sobe junto do resto do ambiente, sem instalação
local, e é mais direta de usar durante uma demonstração/avaliação (ex. rodar o `SELECT` de um
caso do caderno de testes).

## Cenários (comportamento esperado)

Não aplicável — infraestrutura de desenvolvimento, sem comportamento de aplicação.

## Requisitos funcionais

- Novo serviço no `docker-compose.yml`, dependente de `db` (mesmo padrão de `app`), acessível
  numa porta própria do host.
- Credenciais/conexão pré-apontadas pro serviço `db` já existente (mesmo usuário/senha/banco que
  `app` usa hoje) — sem exigir configuração manual adicional além de subir o `docker-compose`.

## Requisitos não-funcionais

- Não roda em nenhum outro perfil (`staging`/`production`) — só existe no `docker-compose.yml`
  local, mesmo alcance de `localstack` hoje.

## Fora de escopo

- Qualquer autenticação/exposição além do ambiente local do `docker-compose` — não é pensado pra
  rodar em staging/produção.

## Decisões em aberto

Nenhuma — decisão (ferramenta escolhida) resolvida em conversa antes de escrever este documento,
ver `plan.md`.
