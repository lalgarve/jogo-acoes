# Testes blackbox (behave + pytest)

Suíte de testes de caixa-preta em Python (spec 05-015): exercita a API do sistema principal
(`app/`) só por fora, via HTTP, contra o contrato publicado
([`docs/openapi.yaml`](../docs/openapi.yaml)) — sem nenhum conhecimento do código Java por
trás. Projeto Python independente, fora do reator Maven da raiz.

- `behave` (BDD/Gherkin) para os cenários de negócio (`features/`).
- `pytest` para verificações técnicas pontuais que não justificam um cenário Gherkin completo
  (`tests/`).
- Cliente HTTP gerado a partir do contrato via
  [`openapi-python-client`](https://github.com/openapi-generators/openapi-python-client) — não
  escrito/mantido à mão, mesma fonte de verdade que já gera o servidor Java
  (`openapi-generator-maven-plugin`).

## Pré-requisito: o ambiente blackbox (spec 05-014)

Esta suíte roda contra a aplicação de verdade, subida com o perfil `blackbox` — ver a seção
["Ambiente de testes blackbox"](../README.md#ambiente-de-testes-blackbox) do README da raiz.
Sem ele:

- Todo passo que dependa de um administrador (ex.: criar uma competição) trava — não existe via
  de API para criar um administrador, só a semeadura automática desse perfil.
- Todo passo que dependa de clicar num link (login, registro, confirmação de convite — a
  maioria dos fluxos do sistema) trava — sem `GET /blackbox/last-email`, não há como ler o link
  enviado por e-mail de fora do processo Java.
- Os passos que testam o caminho "sem resolver o desafio ALTCHA de verdade" falham — em
  qualquer outro perfil, um `captchaToken` vazio/inválido é rejeitado de propósito.

```
docker compose -f docker-compose.yml -f docker-compose.blackbox.yml up
```

## Instalar

```
cd blackbox-tests
python3 -m venv .venv
. .venv/bin/activate
pip install openapi-python-client
```

## Gerar (ou regenerar) o cliente

O cliente gerado (`generated_client/`) não é commitado — regenere sempre que
`docs/openapi.yaml` mudar, ou na primeira vez que for rodar a suíte:

```
openapi-python-client generate --path ../docs/openapi.yaml --output-path generated_client \
  --overwrite --meta setup --config openapi-python-client-config.yaml
pip install -e generated_client/
```

`openapi-python-client-config.yaml` só fixa o nome do pacote gerado (`jogo_acoes_client`) —
sem ele, a ferramenta deriva um nome estranho a partir do `title` do contrato (que tem acento).

## Instalar as dependências da própria suíte

```
pip install -e .
```

## Rodar

Com o ambiente `blackbox` no ar (`http://localhost:8080/api` por padrão — sobrescrevível via
`API_BASE_URL`):

```
behave
pytest
```

## O que NÃO está no cliente gerado

`GET /blackbox/last-email` (spec 05-014) não está em `docs/openapi.yaml` de propósito — é
andaime de teste do ambiente `blackbox`, não contrato de produto. `features/mailbox.py`
encapsula essa chamada via `httpx` direto, usada tanto pelos passos de `behave` quanto pelos
testes `pytest` — o cliente gerado continua refletindo só a API real do sistema.
