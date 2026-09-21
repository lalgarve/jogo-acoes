# Spec: Proxy reverso de Client Hints para testar pelo Swagger UI

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Novo módulo Maven, `blackbox-proxy/` — uma aplicação Spring Boot separada, numa porta própria,
que funciona como proxy reverso transparente na frente do `app/`: encaminha qualquer requisição
para a API real, mas sempre com os headers `Sec-CH-UA*`/`User-Agent` que estiverem configurados
no momento (navegador não consegue mandar `Sec-*` de jeito nenhum, e alguns testes precisam
também de um `User-Agent` próprio — ver spec 05-009/motivação abaixo). Um endpoint próprio e
único, `POST /blackbox/proxy/headers`, só recebe os valores a usar dali em diante — configurar
isso uma vez, e o resto do Swagger UI (qualquer rota, não só login/registro) continua
funcionando exatamente como sempre, sem montar nada à mão a cada chamada. Um script novo
(`scripts/blackbox-proxy.sh`) deixa a porta do proxy e a URL/porta de destino sobrescrevíveis,
pra quem quiser rodar mais de uma instância (um dispositivo simulado por instância) em vez de
reconfigurar a mesma instância a cada troca de dispositivo.

## Motivação

Substitui a primeira tentativa desta spec (um endpoint único que recebia token+ação+headers
num corpo JSON): tecnicamente contornava a restrição do navegador, mas obrigava a montar esse
JSON toda vez que quisesse mudar o header, perdendo a vantagem de navegar pelo Swagger UI
normalmente — inadequado na prática (feedback do usuário).

A causa continua a mesma: o Fetch Standard proíbe `fetch`/`XMLHttpRequest` de uma página de
mandar qualquer header começando com `Sec-` — o campo aparece preenchível no "Try it out" do
Swagger UI (declarado em `docs/openapi.yaml`, spec 05-009), mas o valor digitado nunca sai do
navegador. Um proxy reverso de verdade resolve isso sem essa desvantagem: o navegador só fala
com o proxy (que não é uma página, é outra aplicação, sem a restrição do Fetch), e é o proxy —
não o navegador — quem efetivamente manda o header `Sec-CH-UA*` real pra API, em toda chamada,
transparentemente. Apontar o Swagger UI pro endereço do proxy em vez do endereço direto da API
já basta — nada muda na forma de usar o Swagger UI pra tudo o mais.

## Cenários (comportamento esperado)

Não aplicável — infraestrutura/ferramenta de teste, sem `.feature` novo (mesmo padrão do
restante do andaime `blackbox`, specs 05-014/05-018). Coberto por teste de integração (ver
`plan.md`).

## Requisitos funcionais

- Novo módulo `blackbox-proxy/` (Spring Boot, projeto Maven independente, mesmo padrão de
  `email-lambda/` — parent/BOM próprio, agregado pelo `pom.xml` da raiz), rodando numa porta
  própria (padrão `8090`, distinta da porta `8080` do `app/`).
- `POST /blackbox/proxy/headers` — recebe `secChUa`, `secChUaPlatform`,
  `secChUaPlatformVersion`, `secChUaMobile` e `userAgent` (todos opcionais; campo ausente/`null`
  limpa aquele header, deixando de sobrescrevê-lo nas próximas chamadas) e guarda como
  configuração corrente do processo (em memória — não persiste, não é multiusuário, é
  ferramenta de teste manual de uma pessoa por vez). `204` de resposta.
- Qualquer outra requisição (qualquer método, qualquer caminho — incluindo os arquivos estáticos
  do Swagger UI do próprio `app/`, já que o proxy encaminha tudo) é repassada pra API real
  (endereço configurável, padrão `http://localhost:8080`), preservando caminho, query string,
  método, corpo e todos os headers originais — exceto os cinco (`Sec-CH-UA*` e `User-Agent`),
  que são sobrescritos (ou adicionados) com o que estiver configurado no momento; os que não
  foram configurados não são tocados.
- Resposta da API real é repassada de volta sem alteração — status, corpo e headers (`Set-
  Cookie` incluso, essencial pra sessão de login continuar funcionando através do proxy).
- Resultado prático: apontar o navegador pro Swagger UI através do proxy
  (`http://localhost:8090/api/swagger-ui.html` em vez de `http://localhost:8080/api/
  swagger-ui.html`) faz toda chamada feita a partir dali (login, registro, qualquer outra rota)
  passar pelo proxy — `docs/openapi.yaml` já declara `servers: [{url: /api}]` (caminho relativo),
  então o Swagger UI carregado do proxy já chama de volta o próprio proxy sem nenhuma
  configuração extra.
- Novo script `scripts/blackbox-proxy.sh`, com três parâmetros sobrescrevíveis (todos com
  padrão, nenhum obrigatório): URL de destino (padrão `http://localhost`), porta de destino
  (padrão `8080`) e porta do próprio proxy (padrão `8090`) — rodar o script de novo com valores
  diferentes sobe outra instância independente, permitindo simular vários dispositivos ao mesmo
  tempo (um por instância/porta), cada um configurado uma vez via `POST /blackbox/proxy/
  headers` na sua própria porta.

## Requisitos não-funcionais

- **Nunca roda em `staging`/`production`**: não é um serviço do `docker-compose.yml`
  nem do `docker-compose.blackbox.yml` — só existe quando alguém decide rodar esse módulo à mão,
  localmente, pra testar manualmente (mesmo espírito de confinamento de
  `scripts/blackbox-clock-offset.sh`; ver "Decisões em aberto").
- **Estado em memória, de um processo só**: reinício do `blackbox-proxy` zera a configuração de
  headers — aceitável, é ferramenta de teste manual, não precisa sobreviver a reinício.
- **Sem autenticação própria**: o proxy não adiciona nenhuma camada de auth — quem quer que
  alcance a porta dele alcança a API real por trás (e pode reconfigurar os headers de todo
  mundo); aceitável pelo mesmo motivo de `BlackboxController` (spec 05-014): confinado a um
  ambiente de teste descartável, nunca exposto de verdade.

## Fora de escopo

- Qualquer mudança em `app/` — o proxy fica inteiramente fora dele, como aplicação separada;
  nenhuma mudança em `LoginController`/`SecurityConfig`/`docs/openapi.yaml`.
- Uma única instância nunca guarda mais de uma configuração de dispositivo ao mesmo tempo — cada
  `POST /blackbox/proxy/headers` sobrescreve a anterior naquela instância. Simular vários
  dispositivos em paralelo é resolvido rodando várias instâncias (`scripts/blackbox-proxy.sh`
  com portas diferentes), não guardando várias configurações numa instância só.
- Qualquer coisa além dos cinco headers (`Sec-CH-UA*`, `User-Agent`) — o proxy não modifica
  nenhum outro header/corpo da requisição.

## Decisões em aberto

Nenhuma — rodar via `scripts/blackbox-proxy.sh` (não entra em `docker-compose.yml`/
`docker-compose.blackbox.yml` nesta v1) decidido em conversa; detalhamento em `plan.md`.
