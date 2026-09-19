# Spec: Ambiente de testes blackbox (captcha sempre válido + cobertura JaCoCo da aplicação)

**Status:** rascunho
**Issue:** [#68](https://github.com/lalgarve/jogo-acoes/issues/68)
**Iteração:** iteration-5

## Resumo

Um novo perfil de aplicação (`blackbox`), empilhável sobre o perfil `docker`, em que o captcha
(ALTCHA) sempre é aceito como válido — necessário porque não existe (nem está no escopo atual)
um frontend que resolva o desafio de prova-de-trabalho de verdade, o que hoje torna
`captchaToken` impossível de preencher numa chamada blackbox real sem reimplementar o algoritmo
do ALTCHA no lado do teste. O mesmo ambiente também ganha cobertura de código JaCoCo da
**aplicação rodando de verdade** — capturada enquanto ela é exercitada por fora (clique manual
no Swagger UI, ou a suíte de testes blackbox em Python da spec 05-015), não pela suíte
JUnit/Cucumber (`app/src/test`), que já tem seu próprio relatório JaCoCo e mecanismo próprio
(`jacoco-maven-plugin`, inalterado por esta spec). São dois relatórios de cobertura distintos,
gerados por dois mecanismos diferentes, medindo dois tipos de exercício diferentes do mesmo
código.

## Motivação

A spec 05-015 (suíte de testes blackbox em Python) e qualquer sessão manual de testes via
Swagger UI ou Selenium precisam conseguir chamar `POST
/competitions/{competitionId}/entry-requests` (e qualquer outra rota que hoje ou no futuro
exija `captchaToken`) sem resolver o desafio ALTCHA de verdade — o objetivo desses testes é
validar o comportamento da API do ponto de vista de fora, não reimplementar prova-de-trabalho.
Um perfil dedicado, que nunca é ativado pela suíte de testes Java nem em produção, resolve isso
sem enfraquecer o captcha real usado em qualquer outro contexto.

Além disso, sem cobertura de código medida enquanto a aplicação é exercitada por Swagger/pela
suíte Python, a única cobertura visível hoje é a da suíte JUnit/Cucumber — não mostra se um
teste blackbox de fato passou pelo código que se propõe a validar. `jacoco-maven-plugin`
(usado hoje) só instrumenta JVMs que o próprio Maven lança para rodar testes — não serve para
medir cobertura de um processo `java -jar app.jar` já em execução, exercitado de fora por HTTP;
o mecanismo pra isso é diferente (agente de execução do JaCoCo anexado ao próprio processo,
mais uma ferramenta de linha de comando pra extrair os dados coletados e gerar o relatório).

Dois furos adicionais, sem os quais o ambiente não é de fato utilizável de ponta a ponta por um
cliente externo (Swagger UI ou a suíte Python): **não existe nenhuma via de API para criar um
administrador** — só um administrador pode criar competições (`create_competition.feature`), e
sem uma linha em `app_user`/`user_role` já presente no banco, nenhum fluxo que dependa disso é
alcançável de fora; e **o link do e-mail (login mágico, confirmação de registro, convite) não
aparece em nenhuma resposta HTTP** — `POST /login-requests` sempre devolve `202` sem corpo
(deliberadamente, pra não revelar se o e-mail existe), e o link fica só em `sent_email`
(gravado por `StubEmailSender`/`SqsEmailSender`, spec 05-009 em diante), tabela que hoje só é
lida por dentro do processo Java (testes Cucumber), nunca por HTTP. Sem alguma forma de ler
esse link de fora, nenhum fluxo que dependa de clicar num link — a maioria dos fluxos do
sistema — é exercitável às cegas.

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
- A imagem Docker da aplicação (`Dockerfile`, usada tanto por `docker-compose.yml` quanto pela
  sobreposição `blackbox`) embute o agente de execução do JaCoCo e o `jacococli` — presentes em
  toda imagem, mas inertes: só o `docker-compose.blackbox.yml` de fato ativa o agente (via
  variável de ambiente no processo `java -jar app.jar`, modo `tcpserver`, sem parar/reiniciar
  a JVM para coletar dados), e só ele expõe a porta de *dump*. `docker-compose up` normal
  (sem a sobreposição) roda exatamente como hoje, sem agente anexado — "com ou sem JaCoCo" é
  literalmente subir com ou sem `docker-compose.blackbox.yml`.
- Um script dedicado (local, roda contra o container `app` já em execução) faz o *dump* dos
  dados de execução acumulados pelo agente e gera o relatório HTML de cobertura — comando único,
  documentado no `README.md`, gerando o relatório num diretório próprio (não sobrescreve nem se
  mistura com o relatório da suíte Java em `target/site/jacoco/`).
- `README.md` ganha uma seção explicando: (1) como subir o ambiente `blackbox` (com o agente
  JaCoCo ativo); (2) o alcance do bypass de captcha (só aqui); (3) como gerar o relatório de
  cobertura da aplicação exercitada externamente (Swagger UI manual e/ou suíte Python, spec
  05-015) via o script dedicado; (4) que a suíte JUnit/Cucumber continua com seu próprio
  relatório JaCoCo de sempre (`mvn test`/`mvn verify`), sem relação com este mecanismo novo;
  (5) o e-mail do administrador semeado (ver abaixo — não há senha, o login é só por link
  mágico) e como ler o link de um e-mail enviado durante um teste.
- No perfil `blackbox`, a aplicação garante — de forma idempotente, ao subir — que existe um
  administrador com e-mail/dados conhecidos, sem exigir nenhuma ação manual no banco antes de
  começar a testar.
- Um jeito de obter, via HTTP, o link do e-mail mais recente enviado para um endereço — só
  disponível no perfil `blackbox`, cobrindo o que hoje só é lido por dentro do processo Java
  (`sent_email`). Não é uma caixa postal completa (histórico, múltiplos e-mails, marcação de
  lido) — só o suficiente pra um teste blackbox conseguir avançar um fluxo que depende de
  clicar num link; uma experiência mais completa ("olhar na caixa postal") fica para quando a
  spec 05-015/o roadmap tiverem uma necessidade mais completa disso.

## Requisitos não-funcionais

- **Nunca em produção/staging.** O bypass de captcha é uma escolha aceitável só porque o
  sistema está em pré-produção (`memory/constitution.md`) e este ambiente nunca é publicamente
  acessível — precisa ficar estruturalmente impossível de ativar em `staging`/`production` (não
  só "por convenção").
- **Nenhum teste automatizado Java (`mvn test`, suíte Cucumber/JUnit, CI) roda contra este
  ambiente** — ele existe só para consumo manual (Swagger UI, Selenium) e pela suíte de testes
  blackbox em Python (spec 05-015), nunca para os testes que já rodam em `sandbox`/`docker`
  hoje.
- **Os dois relatórios de cobertura nunca se sobrescrevem nem se confundem** — o da suíte
  JUnit/Cucumber (`target/site/jacoco/`, gerado por `mvn test`/`mvn verify`, inalterado) e o da
  aplicação exercitada externamente (novo, script dedicado, diretório próprio) precisam
  conviver lado a lado, cada um respondendo por um tipo de exercício diferente do código.
- **O endpoint de leitura de e-mail é, por natureza, uma divulgação de informação que seria
  inaceitável em produção** (qualquer chamada, sem autenticação, lê o link mais recente enviado
  para qualquer endereço) — aceitável aqui exatamente pela mesma razão do bypass de captcha
  (sistema em pré-produção, spec confinada ao perfil `blackbox`, nunca exposta publicamente);
  precisa da mesma garantia estrutural de nunca existir fora desse perfil (não só documentada).

## Fora de escopo

- Qualquer frontend que resolva o desafio ALTCHA de verdade — fora do escopo desta iteração
  (roadmap futuro).
- Selenium em si (a menção a "depois com Selenium" é motivação/contexto de uso futuro deste
  ambiente, não uma entrega desta spec).
- Mudar o comportamento do captcha em qualquer perfil existente — `sandbox`/`docker` (sem o
  perfil `blackbox` empilhado)/`staging`/`production` continuam exigindo um captcha
  resolvido de verdade, comportamento idêntico ao de hoje.
- Uma experiência de "caixa postal" de verdade (múltiplos e-mails, histórico por endereço,
  marcação de lido, interceptação real de SMTP tipo MailHog/Mailpit) — fica para quando a
  implementação estiver mais completa; esta spec entrega só o mínimo (último link enviado por
  endereço) necessário pra destravar os fluxos de teste blackbox de hoje.
- Qualquer forma de criar administrador via API de verdade (endpoint de cadastro/promoção de
  papel) — o que esta spec resolve é só a semeadura de um administrador conhecido no ambiente
  `blackbox`, não uma funcionalidade de produto.

## Decisões em aberto

Nenhuma — decisões técnicas (nome do perfil, mecanismo de seleção da implementação de captcha,
arquivo de sobreposição do `docker-compose`, como o agente JaCoCo é embutido/ativado, como o
relatório de cobertura da aplicação exercitada externamente é gerado, como o administrador é
semeado, como o link de e-mail é lido de fora) resolvidas em conversa antes de escrever este
documento, ver `plan.md`.
