# Plan: Suíte de testes blackbox em Python (behave + pytest)

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

O reator Maven na raiz (`pom.xml`) já convive com dois módulos de stack diferente (`app/`
Spring Boot, `email-lambda/` Quarkus) sem um *parent* compartilhado — este projeto Python é um
terceiro tipo de módulo, fora do reator Maven por completo (Python não tem lugar num
`<modules>` do Maven). `docs/openapi.yaml` já é a fonte de verdade consumida pelo
`openapi-generator-maven-plugin` (gera o servidor Java, `interfaceOnly`); esta spec consome a
mesma fonte do lado cliente, em Python.

O ambiente contra o qual esta suíte roda é o `blackbox` da spec 05-014
(`docker-compose -f docker-compose.yml -f docker-compose.blackbox.yml up`) — captcha sempre
aceito, sem precisar resolver ALTCHA nos passos de teste; um administrador já semeado
(`admin@blackbox.local`, sem senha — login por link); e `GET /blackbox/last-email` pra ler o
link do e-mail mais recente enviado para um endereço, já que nenhuma resposta HTTP do sistema
devolve esse link diretamente.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Nome/local do diretório | `blackbox-tests/`, raiz do repositório, ao lado de `app/`/`email-lambda/` — projeto Python independente (`pyproject.toml` próprio), não um módulo Maven. | resolvida | Consistente com a organização por módulo já existente (cada um com seu próprio jeito de buildar/rodar), sem forçar Python dentro da árvore Maven. |
| Ferramenta de geração do cliente | `openapi-python-client` (pacote PyPI do mesmo nome), gerando a partir de `docs/openapi.yaml` — mesma fonte que o servidor Java usa, nenhuma edição manual do código gerado. | resolvida | Ferramenta dedicada e madura para clientes Python a partir de OpenAPI 3 (gera modelos tipados + cliente HTTP baseado em `httpx`); evita escrever/manter chamadas HTTP cruas à mão, mesmo raciocínio de "contrato é fonte única de verdade" já aplicado ao lado servidor. |
| Onde o cliente gerado vive | Gerado para dentro de `blackbox-tests/generated_client/` (nome do pacote definido pela própria ferramenta a partir do `title` do contrato), **não commitado** — regenerado a cada execução/setup (`.gitignore` cobre o diretório), mesmo espírito de `target/generated-sources` no lado Java (também não commitado). | resolvida | Evita o cliente gerado divergir silenciosamente do contrato commitado; regenerar é barato e determinístico. |
| `behave` vs. reaproveitar `.feature` Java | `.feature` novos, próprios de `blackbox-tests/features/`, mesmo quando cobrem a mesma regra de negócio de um `.feature` Java já existente — um cenário blackbox descreve requisição/resposta HTTP do ponto de vista de um cliente externo (ex. "envia `POST /competitions/{id}/entry-requests` com tal corpo, recebe `202`"), o `.feature` Java descreve o mesmo fluxo em termos de fixtures internas (ex. "o jogador já está registrado") — textos propositalmente diferentes, mesma regra coberta duas vezes por dois ângulos. | resolvida | Tentar compartilhar o arquivo `.feature` literal entre Cucumber (JVM) e `behave` (Python) acopla duas suítes com objetivos diferentes por um ganho pequeno (evitar duplicar texto Gherkin) — mais simples manter cada suíte falando a língua do seu próprio nível de teste. |
| Onde `pytest` entra | `blackbox-tests/tests/` (convenção padrão do `pytest`) — testes que não são cenário de negócio (ex.: um código de status específico, um shape de erro) ficam aqui em vez de virarem `Scenario` de `behave` só pra existir. | resolvida | `behave` fica reservado pra comportamento de negócio (o que a spec pede), `pytest` para verificações técnicas pontuais — mistura comum e já estabelecida na comunidade Python de teste de API. |
| Como apontar a suíte pro ambiente rodando | Variável de ambiente (`API_BASE_URL`, default `http://localhost:8080/api`) lida por uma fixture/hook compartilhada, usada para instanciar o cliente gerado — não hardcoded em cada passo/teste. | resolvida | Mesmo raciocínio de configuração via ambiente já usado no lado Java (`SPRING_DATASOURCE_URL` etc.) — a suíte não assume nada além de "a API está acessível nessa URL". |
| Como obter o link de um e-mail enviado durante um passo | Chamada direta via `httpx` (a mesma biblioteca do cliente gerado) a `GET /blackbox/last-email?email={endereço}` — **não** através do cliente gerado, porque essa rota não está em `docs/openapi.yaml` (é andaime de teste da spec 05-014, não contrato de produto, ver o `plan.md` dela). Um helper pequeno (`blackbox-tests/features/mailbox.py` ou nome equivalente) encapsula essa chamada, usado tanto por passos de `behave` quanto por testes `pytest`. | resolvida | O cliente gerado deve continuar refletindo só a API real do produto; misturar uma rota de teste nele confundiria o que é contrato de produto com o que é andaime — um helper à parte deixa essa distinção explícita no próprio código da suíte. |

## Estrutura de módulos/pacotes

- `blackbox-tests/pyproject.toml` (novo) — dependências (`behave`, `pytest`,
  `openapi-python-client`, `httpx` transitiva via o cliente gerado).
- `blackbox-tests/features/` (novo) — `.feature` + `steps/` do `behave`; inclui o helper de
  leitura de e-mail (`GET /blackbox/last-email`, spec 05-014) usado pelos passos que dependem
  de clicar num link.
- `blackbox-tests/tests/` (novo) — testes `pytest`.
- `blackbox-tests/generated_client/` (novo, gerado, não commitado).
- `blackbox-tests/README.md` (novo) — como instalar, gerar o cliente e rodar a suíte contra o
  ambiente `blackbox` (spec 05-014).
- `.gitignore` (modificado, raiz) — ignora `blackbox-tests/generated_client/` e o ambiente
  virtual Python local.

## Riscos e trade-offs

- **Cliente gerado não commitado** significa que a suíte não roda sem primeiro gerar o cliente
  (passo extra documentado no README) — aceito, evita divergência entre contrato e cliente.
- **Dois conjuntos de `.feature` cobrindo a mesma regra de negócio** (Java e Python) é
  duplicação deliberada de esforço de escrita, não de comportamento verificado — mitigado
  documentando claramente, no `README.md` de `blackbox-tests/`, que essa suíte existe para
  validar o contrato do ponto de vista externo, não para substituir a suíte Java existente.
- **Depende da spec 05-014 estar implementada** — não só o captcha sempre aceito: sem o
  administrador semeado, nenhum cenário que precise de uma ação de administrador (criar
  competição) é exercitável; sem `GET /blackbox/last-email`, nenhum cenário que dependa de
  clicar num link (a maioria) consegue avançar. As três coisas juntas são o que torna o
  ambiente utilizável de ponta a ponta por esta suíte.
