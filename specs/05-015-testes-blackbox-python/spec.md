# Spec: Suíte de testes blackbox em Python (behave + pytest)

**Status:** rascunho
**Issue:** —
**Iteração:** iteration-5

## Resumo

Uma suíte de testes de caixa-preta, em Python, que exercita a API do sistema principal (`app/`)
só por fora — chamadas HTTP contra o contrato publicado (`docs/openapi.yaml`), sem nenhum
conhecimento do código Java por trás. Usa `behave` (BDD/Gherkin) para os cenários de negócio e
`pytest` para os testes mais técnicos/pontuais, com o cliente HTTP da API gerado a partir do
contrato em vez de escrito à mão.

## Motivação

Toda a suíte de testes existente hoje (Cucumber/JUnit, `app/src/test`) roda de dentro do
processo Java, com acesso direto a repositórios/beans Spring pra montar estado (`ScenarioWorld`,
`*Fixtures`, `*Mother`) — rápida e adequada para TDD, mas não prova, do ponto de vista de um
cliente externo real, que a API publicada funciona como o contrato descreve. Uma suíte blackbox
em Python, rodando contra a aplicação de verdade subida via `docker-compose` (spec 05-014, perfil
`blackbox` — sem precisar resolver captcha, com um administrador já semeado, e com um jeito de
ler o link de um e-mail enviado), fecha essa lacuna e serve de base pra testes de UI (Selenium)
mencionados na spec 05-014.

## Cenários (comportamento esperado)

- Cenários novos, em Gherkin, escritos para `behave` (não reaproveitam os `.feature` do Java —
  ver "Decisões em aberto" sobre reaproveitar texto vs. duplicar) — caminho feliz de pelo menos
  um fluxo completo (ex.: pedido de entrada pública → confirmação → listagem) como prova de
  conceito inicial; expandir cobertura é trabalho contínuo, não travado por esta spec.
- Testes `pytest` pontuais para verificações que não justificam um cenário Gherkin completo
  (ex.: um contrato de resposta específico, um código de erro).

## Requisitos funcionais

- Novo diretório na raiz do repositório (`blackbox-tests/`, nome definido em `plan.md`) — não é
  módulo Maven, projeto Python independente.
- Cliente HTTP da API gerado a partir de `docs/openapi.yaml` via `openapi-python-client` (não
  escrito/mantido à mão) — a mesma fonte de verdade que já gera o servidor Java
  (`openapi-generator-maven-plugin`), agora gerando também o consumidor Python.
- `behave` para os cenários de negócio (Gherkin) — passos Python chamando o cliente gerado.
- `pytest` para os testes técnicos pontuais que não são cenário de negócio.
- Um passo compartilhado para ler o link do e-mail mais recente enviado a um endereço (via o
  endpoint que a spec 05-014 expõe só no ambiente `blackbox`) — sem ele, nenhum cenário que
  dependa de clicar num link (login, registro, confirmação de convite) consegue avançar.
- Instruções no `README.md` (ou um `README.md` próprio de `blackbox-tests/`, ver `plan.md`) de
  como instalar dependências, gerar/regenerar o cliente e rodar a suíte contra o ambiente da
  spec 05-014.

## Requisitos não-funcionais

- Nenhuma dependência de estado interno da aplicação Java (sem JDBC direto, sem ler log pra
  decidir asserção) — só HTTP, como qualquer cliente real veria.
- Não faz parte do `mvn test`/CI Java existente — suíte independente, com seu próprio comando de
  execução (mesmo espírito não-automatizado-pela-suíte-Java da spec 05-014).

## Fora de escopo

- Selenium/testes de UI de verdade — mencionados como motivação futura, não entregues aqui.
- Rodar esta suíte automaticamente em CI — pode vir depois; esta spec só entrega a suíte
  executável manualmente.
- Cobertura completa de todos os fluxos do sistema — um exemplo de ponta a ponta primeiro,
  ampliar depois é trabalho contínuo (mesmo espírito incremental da Issue #43/spec 05-012).

## Decisões em aberto

Nenhuma — decisões técnicas (nome do diretório, ferramenta de geração do cliente, se os
`.feature` do `behave` reaproveitam texto dos `.feature` Java ou são escritos do zero) resolvidas
em conversa antes de escrever este documento, ver `plan.md`.
