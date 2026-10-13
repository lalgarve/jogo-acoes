# Spec: Visualizador de logs no ambiente Docker Compose

**Status:** aprovada
**Issue:** — (criar quando a Leila pedir)
**Iteração:** iteration-5

## Resumo

Adiciona ao `docker-compose.yml` uma interface web que mostra, no navegador, os logs dos
containers do ambiente (`app`, `email-service`, `localstack` e os demais), ao lado do Adminer
(banco) e do `ses-viewer` (e-mails) que já existem.

## Motivação

Nos testes manuais (caderno de testes, spec 05-040), cada roteiro confere um trecho de log que
mostra o que aconteceu por dentro: as linhas `DEBUG` dos aspectos de log da spec 05-011
(`ControllerLoggingAspect`, `RepositoryLoggingAspect`, `EmailServiceLoggingAspect`). Hoje a
única forma de ver esses logs é o terminal:

- `docker compose logs -f app` (tabela "Ferramentas" do caderno de testes);
- um terminal por serviço, ou a saída misturada de todos, quando o fluxo passa pelo `app` e
  pelo `email-service`;
- sem busca nem filtro além do `grep`, que não é igual no Windows (PowerShell) e no Linux.

No documento da disciplina, esse ponto aparece em "Informações sobre a execução" (Etapa 1): o
Adminer mostra o banco e os logs mostram a execução. O visualizador completa esse conjunto com
uma tela no navegador, como já temos para o banco e para os e-mails, e passa a valer para os
dois serviços da Etapa 2.

## Cenários (comportamento esperado)

Não aplicável — infraestrutura de desenvolvimento, sem comportamento de aplicação (mesmo caso
da spec 05-013, Adminer).

## Requisitos funcionais

- RF1. Um serviço novo no `docker-compose.yml` sobe junto do ambiente e fica acessível numa
  porta própria do host, sem instalação local além do Docker.
- RF2. A tela mostra os logs de cada container do Compose, no mínimo `app` e `email-service`,
  e acompanha as linhas novas em tempo real.
- RF3. É possível ver os logs de `app` e `email-service` juntos, em ordem de tempo, para seguir
  um pedido que passa pelos dois serviços (ex.: `POST /login-requests` → `POST /emails`).
- RF4. É possível buscar/filtrar por texto (ex.: o e-mail do jogador, o nome de um aspecto, o
  token de um link).
- RF5. O caderno de testes (`docs/disciplina/caderno-de-testes.md`, tabela "Ferramentas")
  passa a indicar o visualizador, mantendo o `docker compose logs` como alternativa.

## Requisitos não-funcionais

- RNF1. Leve: memória em repouso na ordem de dezenas de MB, sem banco nem volume próprio
  (memória no início é custo importante, mesma razão do schema único por serviço, spec 05-032).
- RNF2. Nenhuma mudança no código de `app`, `email-service` ou `email-lambda`: os serviços
  continuam escrevendo no stdout como hoje (constitution, "código de teste/dev nunca dentro de
  um módulo de produção").
- RNF3. Funciona no Docker Desktop do Windows e no Linux (a Leila executa o caderno de testes
  no Windows).
- RNF4. Não é necessário para `mvn test` nem para a CI: a CI sobe só os serviços que nomeia
  (`docker compose up -d --wait db db-email-service localstack ...`), então o serviço novo não
  entra lá.

## Fora de escopo

- Staging e produção: centralização, retenção e alertas de log em ambiente real são parte da
  observabilidade da Iteração 6 (`docs/roadmap.md`), não desta spec. Ver "Decisões em aberto".
- Mudar o formato dos logs (JSON estruturado, correlation id entre `app` e `email-service`).
- Métricas e traces (Actuator/Micrometer, OpenTelemetry).
- Autenticação no visualizador: só existe no Compose local, mesmo alcance do Adminer e do
  `ses-viewer`.

## Decisões em aberto

Nenhuma. A Leila aceitou as três recomendações em 2026-10-13 (detalhe em `plan.md`):

1. **Ferramenta:** Dozzle.
2. **Alcance:** só o Compose local.
3. **Logs da `email-lambda`:** aceitar que somem quando o LocalStack recicla o container, sem
   configuração extra; o caderno de testes registra essa limitação.
