# Spec: Gerador de dados de teste do ambiente blackbox

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Um script Python (`blackbox-tests/seed/`) que popula o ambiente `blackbox` (spec 05-014) só
por HTTP — o administrador semeado cria competições e os jogadores passam a existir ao entrar
nelas — para servir de massa repetível ao teste manual, ao caderno de testes (spec 05-012) e
aos cenários de listagem/filtro da suíte Python (spec 05-015).

## Motivação

Hoje cada cenário da suíte blackbox cria um dado avulso (uma competição com UUID no nome, um
jogador novo) e o Swagger UI abre num banco vazio. Uma massa repetível, cobrindo os estados
alcançáveis do modelo (competição pública/privada em cada status, participação em cada
estágio), falta tanto para teste manual quanto para os cenários de listagem/filtro que a spec
05-015 ainda não tem.

O sistema não tem — e não é objetivo desta spec criar — nenhuma via de escrita direta no banco;
tudo passa pela API real, exatamente como um cliente de verdade veria. Isso significa aceitar
os limites do que a API hoje consegue produzir (ver "Fora de escopo") e usar o mecanismo de
relógio no passado já documentado no `README.md` (seção "Gerando dados de teste com uma data
no passado", `scripts/blackbox-clock-offset.sh`) para os estados que dependem de tempo
decorrido — subir o app com aquele script continua um passo manual, separado deste gerador (ver
"Requisitos funcionais").

## Cenários (comportamento esperado)

Não aplicável — ferramenta de geração de dados/infraestrutura de teste, sem `.feature` novo
nem mudança de comportamento de negócio (mesmo padrão das specs 05-014/05-015/05-016).

## Requisitos funcionais

- Script Python, dentro de `blackbox-tests/` (reaproveita o cliente gerado a partir de
  `docs/openapi.yaml`, spec 05-015), lendo `API_BASE_URL` (mesma variável já usada pela suíte).
- Fala com o sistema só por HTTP: o cliente gerado e o helper de `GET /blackbox/last-email`
  (spec 05-014), sem JDBC nem leitura de log.
- Faz login como o administrador semeado (`success+admin@simulator.amazonses.com`, spec
  05-014/05-016), uma vez por execução.
- Cria competições de um catálogo fixo (ver `plan.md`) com nome prefixado por `[seed]`, e
  jogadores pelos três fluxos possíveis (entrada pública, convite privado, jogador já
  registrado) com e-mails no formato da spec 05-016 (`success+seed-<qualificador>@
  simulator.amazonses.com`) — o qualificador identifica de qual competição/posição do catálogo
  veio cada jogador, para facilitar inspecionar os dados gerados (Swagger UI, `psql`).
- Roda uma vez, contra um ambiente `blackbox` recém-subido (banco vazio além do administrador
  semeado): não verifica se cada item do catálogo já existe antes de criar. Rodar de novo sobre
  dados já criados duplica — não é bug desta v1, é fora de escopo (ver "Fora de escopo");
  recomeçar é `docker compose down -v` + subir de novo.
- Recebe um parâmetro obrigatório de deslocamento de relógio (dias no passado) e roda o
  catálogo inteiro numa única execução, contra o relógio já deslocado por esse valor — o script
  não sobe nem derruba o app, só assume que já está rodando com aquele relógio (ver README).
  Nenhuma competição do catálogo nasce no relógio presente (offset 0): rodar a suíte de testes
  já produz dados no presente organicamente, então o único propósito deste gerador é preencher
  o passado — quem quiser dados "de hoje" já os tem de sobra sem precisar deste script.
- Dentro dessa única execução, cada competição do catálogo tem sua própria `durationDays`
  (curta ou longa) que, combinada com o deslocamento de relógio escolhido, decide se ela
  aparece "terminada por data" ou "em andamento" — não é preciso rodar o script mais de uma vez
  nem reiniciar o app com relógios diferentes para cobrir os dois estados.
- Catálogo inclui jogadores nomeados registrados em exatamente 1, 2, 3 e 4 competições
  diferentes cada — essencial para exercitar `GET /competitions/mine` com mais de um item por
  grupo (ver `plan.md` para a composição exata).
- Cada objeto nasce de uma fábrica com dados válidos por padrão, e uma variação sobrescreve um
  campo por vez — o padrão Object Mother já usado por `CompetitionMother`/`UserMother`
  (`memory/constitution.md`).
- Com a mesma semente (`--seed`), gera os mesmos nomes e a mesma distribuição de jogadores
  onde houver aleatoriedade (perfil `volume`); o catálogo fixo (perfis `minimal`/`standard`)
  não depende de sorteio.
- Aborta com mensagem clara se o login do administrador falhar, ou se um `captchaToken` vazio
  for rejeitado (sinal de que o perfil `blackbox` não está ativo).
- Ao final, imprime a contagem do que criou (ou já existia) por tipo e estado.

## Requisitos não-funcionais

- Nunca roda contra `staging`/`production` — depende do bypass de captcha e do
  `last-email`, que só existem no perfil `blackbox` (spec 05-014); `API_BASE_URL` padrão é
  `localhost`.
- Fora do `mvn test`/CI Java, como a suíte da spec 05-015 — comando próprio.
- Python 3.11+, mesmas dependências de `blackbox-tests/pyproject.toml`.
- Roda em sequência, não em paralelo — `GET /blackbox/last-email` guarda só o último e-mail
  por endereço, e cada login consome uma vaga de dispositivo (`login.max-devices-per-user: 3`).
- Testes `pytest` das fábricas e da lógica de seleção de perfil/catálogo, sem HTTP.

## Fora de escopo

- Estados que a API não tem como produzir hoje: status `CLOSED` de competição (nenhum código
  do `app/` grava esse valor — só é lido), participação `LINK_CLICKED` (o enum existe, nada
  grava) e um segundo administrador (não há via de API; `BlackboxDataSeeder` semeia só um).
  `LINK_CLICKED` nunca ser gravado é bug confirmado, não desta spec: a progressão pretendida é
  `EMAIL_NOT_SENT → EMAIL_SENT → LINK_CLICKED → IN_COMPETITION` (já documentada nas notas de
  iteração), mas `CompetitionLinkHandler.consume()` trata o clique como passagem direta para o
  formulário de inscrição, sem gravar o status — quando o jogador clica no link mas ainda não
  completou o cadastro, a participação continua `EMAIL_SENT`. Bug, não lacuna desta spec —
  correção em Issue própria: [#73](https://github.com/lalgarve/jogo-acoes/issues/73).
- Cotações de ações (`STOCK`, `PRICE_QUOTE`) — não existem no DER atual (roadmap, iterações
  8–10).
- Escrita direta no PostgreSQL — a v1 usa só HTTP, por isso não cobre os dois itens acima.
- Subir o app com o relógio deslocado — automatizado por `scripts/blackbox-clock-offset.sh`
  (`README.md`), mas continua um passo manual e separado, fora deste gerador (ver "Requisitos
  funcionais"): o gerador não sobe/derruba containers. Um `Clock` injetável (bean configurável
  por propriedade) foi considerado e descartado como último recurso — risco de algum call site
  de `now()` ficar de fora e misturar hora real com hora deslocada silenciosamente; ver
  `README.md` para o raciocínio completo.
- Endereços `bounce`/`complaint`/similares do simulador do SES (spec 05-016) — só casos de
  sucesso.
- Limpeza da massa gerada — não há API para apagar; `docker compose down -v` recria o banco do
  zero (o administrador é semeado de novo ao subir).
- Reexecução segura/idempotência — v1 é de uso único, contra ambiente recém-subido (ver
  "Requisitos funcionais"); rodar de novo duplica o catálogo, e não é objetivo desta spec evitar
  isso.

## Decisões em aberto

- Quantos jogadores no perfil `volume` — 200 é um palpite inicial; cada jogador novo custa três
  chamadas de API mais uma leitura de e-mail.
