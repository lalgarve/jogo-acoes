# Spec: Ambiente de testes blackbox (captcha sempre válido)

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Um novo perfil de aplicação (`blackbox`), empilhável sobre o perfil `docker`, em que o captcha
(ALTCHA) sempre é aceito como válido — necessário porque não existe (nem está no escopo atual)
um frontend que resolva o desafio de prova-de-trabalho de verdade, o que hoje torna
`captchaToken` impossível de preencher numa chamada blackbox real sem reimplementar o algoritmo
do ALTCHA no lado do teste. Junto com isso, documenta no `README.md` como rodar a suíte Java com
ou sem instrumentação do JaCoCo, e como gerar o relatório de cobertura depois.

## Motivação

A spec 05-015 (suíte de testes blackbox em Python) e qualquer sessão manual de testes via
Swagger UI ou Selenium precisam conseguir chamar `POST
/competitions/{competitionId}/entry-requests` (e qualquer outra rota que hoje ou no futuro
exija `captchaToken`) sem resolver o desafio ALTCHA de verdade — o objetivo desses testes é
validar o comportamento da API do ponto de vista de fora, não reimplementar prova-de-trabalho.
Um perfil dedicado, que nunca é ativado pela suíte de testes Java nem em produção, resolve isso
sem enfraquecer o captcha real usado em qualquer outro contexto.

## Cenários (comportamento esperado)

Não aplicável — infraestrutura/configuração de ambiente, sem `.feature` novo (mesmo padrão das
specs 05-006/05-007/05-011).

## Requisitos funcionais

- Um novo valor de configuração seleciona, em tempo de inicialização, qual implementação de
  verificação de captcha é usada — a real (ALTCHA, comportamento atual, permanece padrão em
  todo perfil existente) ou uma que sempre aceita qualquer token (incluindo vazio/nulo) como
  válido.
- A implementação "sempre válida" só fica ativa no perfil `blackbox`, empilhado sobre `docker`
  (`SPRING_PROFILES_ACTIVE=docker,blackbox`) — nenhum outro perfil (`sandbox`, `staging`,
  `production`) pode ativá-la, nem por engano.
- `docker-compose` ganha um arquivo de sobreposição dedicado que ativa esse perfil combinado
  para o serviço `app`, sem alterar o `docker-compose.yml` usado hoje por CI/desenvolvimento
  normal (`docker-compose -f docker-compose.yml -f docker-compose.blackbox.yml up`).
- A UI web do PostgreSQL (Adminer, spec 05-013) já sobe junto de qualquer `docker-compose up`
  que inclua o serviço `db` — inclusive neste ambiente, sem mudança adicional; citado aqui só
  como contexto (a única forma de inspecionar o banco visualmente durante um teste blackbox é
  esse ambiente Docker, não o perfil `sandbox`/H2).
- `README.md` ganha uma seção explicando: (1) como subir este ambiente; (2) o alcance do bypass
  de captcha (só aqui); (3) como rodar a suíte de testes Java com JaCoCo (padrão hoje,
  `mvn test`/`mvn verify`, relatório gerado em `target/site/jacoco/index.html`) e sem JaCoCo
  (mais rápido, quando cobertura não importa para o que está sendo depurado) via a própria
  flag que o `jacoco-maven-plugin` já reconhece.

## Requisitos não-funcionais

- **Nunca em produção/staging.** O bypass de captcha é uma escolha aceitável só porque o
  sistema está em pré-produção (`memory/constitution.md`) e este ambiente nunca é publicamente
  acessível — precisa ficar estruturalmente impossível de ativar em `staging`/`production` (não
  só "por convenção").
- **Nenhum teste automatizado Java (`mvn test`, suíte Cucumber/JUnit, CI) roda contra este
  ambiente** — ele existe só para consumo manual (Swagger UI, Selenium) e pela suíte de testes
  blackbox em Python (spec 05-015), nunca para os testes que já rodam em `sandbox`/`docker`
  hoje.

## Fora de escopo

- Qualquer frontend que resolva o desafio ALTCHA de verdade — fora do escopo desta iteração
  (roadmap futuro).
- Selenium em si (a menção a "depois com Selenium" é motivação/contexto de uso futuro deste
  ambiente, não uma entrega desta spec).
- Mudar o comportamento do captcha em qualquer perfil existente — `sandbox`/`docker` (sem o
  perfil `blackbox` empilhado)/`staging`/`production` continuam exigindo um captcha
  resolvido de verdade, comportamento idêntico ao de hoje.

## Decisões em aberto

Nenhuma — decisões técnicas (nome do perfil, mecanismo de seleção da implementação, arquivo de
sobreposição do `docker-compose`, flag do JaCoCo) resolvidas em conversa antes de escrever este
documento, ver `plan.md`.
