# Spec: Remover o módulo `blackbox-proxy/`

**Status:** rascunho
**Issue:** [#123](https://github.com/lalgarve/jogo-acoes/issues/123)
**Iteração:** iteration-5

## Resumo

Remove do repositório o módulo `blackbox-proxy/` (spec 05-020) e o script
`scripts/blackbox-proxy.sh`, e tira da documentação ativa toda menção que trata o proxy como
parte viva do projeto. Os headers de dispositivo (`Sec-CH-UA*`/`User-Agent`) continuam sendo
testados pelo `curl` que o Swagger UI já gera, rodado num terminal.

## Motivação

O proxy foi implementado em `dbbd678` (2026-09-21) para que o Swagger UI conseguisse mandar os
headers de Client Hints, que o navegador proíbe scripts de página de enviar. Depois o projeto
decidiu não usá-lo: o `curl` copiado do Swagger UI resolve o mesmo problema sem manter mais um
processo no ar. O commit `cd7cc6b` (2026-09-30) só atualizou o README, descrevendo o proxy como
"alternativa disponível, deixada de lado por enquanto", e o módulo ficou no repositório.

Hoje ele é código morto que ainda cobra manutenção:

- **Ainda é buildado.** Está no `<modules>` do `pom.xml` raiz, então `mvn verify` na raiz
  compila e testa o módulo (`ReverseProxyIntegrationTest`). O CI não o builda (roda só
  `-pl app`, `-pl email-service` e `-pl email-lambda`), então uma quebra nele só aparece numa
  execução local.
- **As imagens Docker dependem dele.** O `Dockerfile` do `app` e o do `email-service` copiam
  `blackbox-proxy/pom.xml`, porque o reator exige o `pom.xml` de todo módulo listado.
- **Ainda aparece como módulo ativo** no `README.md`, no `CLAUDE.md` e no comentário do
  `pom.xml` raiz.
- **A constitution o usa como exemplo.** A baseline Java lista `blackbox-proxy` entre os
  módulos, e a regra "Código de teste/dev nunca dentro da aplicação" o cita como o padrão a
  seguir. Um exemplo que não é usado ensina o padrão pelo caso errado.

## Cenários (comportamento esperado)

Esta spec é limpeza de repositório, não comportamento de produto. Não há `.feature` novo, e
nenhum `.feature` existente muda: nenhum cenário Gherkin passa pelo proxy.

### C1 — O módulo não existe mais

**Dado** o repositório depois desta spec
**Quando** alguém lista os módulos do reator (`pom.xml` raiz) ou procura `blackbox-proxy/` e
`scripts/blackbox-proxy.sh`
**Então** nenhum dos dois existe
**E** `mvn verify` na raiz builda só `app`, `email-lambda` e `email-service`.

### C2 — As imagens continuam buildando

**Dado** que o módulo foi removido
**Quando** `docker compose build` roda para `app` e `email-service`
**Então** as duas imagens buildam sem copiar nenhum arquivo de `blackbox-proxy/`.

### C3 — A documentação ativa não trata o proxy como vivo

**Dado** o repositório depois desta spec
**Quando** alguém procura `blackbox-proxy` fora dos registros históricos (ver "Fora de escopo")
**Então** não há ocorrência em `README.md`, `CLAUDE.md`, `memory/constitution.md`, `pom.xml`,
`Dockerfile`, `email-service/Dockerfile` nem `.gitattributes`
**E** o README continua descrevendo o teste de headers de dispositivo pelo `curl` do Swagger UI.

### C4 — A regra de código de teste continua com um exemplo real

**Dado** a seção "Código de teste/dev nunca dentro da aplicação" da constitution
**Quando** ela dá um exemplo de código de teste fora dos módulos de produção
**Então** o exemplo é algo que existe e é usado hoje.

### C5 — A spec 05-020 registra a remoção

**Dado** `specs/05-020-proxy-client-hints-swagger/spec.md`
**Quando** alguém a abre
**Então** o status diz que o módulo foi removido, com o motivo e o link para esta spec
**E** o conteúdo original fica como registro histórico (mesmo padrão das specs 05-022 e 05-024).

## Requisitos funcionais

- Apagar `blackbox-proxy/` inteiro e `scripts/blackbox-proxy.sh`.
- Tirar `blackbox-proxy` do `<modules>` e do `<description>` do `pom.xml` raiz.
- Tirar o `COPY blackbox-proxy/pom.xml` e a menção ao módulo nos comentários do `Dockerfile` e
  do `email-service/Dockerfile`.
- Tirar a menção ao proxy do comentário do `.gitattributes`.
- `README.md`: tirar a linha do proxy da tabela "Módulos", o `mvn -pl blackbox-proxy -am verify`
  e o bloco "Alternativa disponível, deixada de lado por enquanto"; manter o fluxo do `curl`.
  Corrigir a contagem de módulos do mesmo trecho ("builda os quatro", "nenhum dos três").
- `CLAUDE.md`: tirar a linha do proxy da tabela de módulos e trocar o exemplo "padrão
  `blackbox-proxy/`".
- `memory/constitution.md`: tirar `blackbox-proxy` da lista de módulos da baseline Java e trocar
  os dois usos dele como exemplo na regra de código de teste.
- Spec 05-020: status "removido", com seção "Por que este módulo foi removido" no fim do
  `spec.md`.
- Spec 05-033: nota no `spec.md` dizendo que o módulo saiu da baseline com esta spec.

## Requisitos não-funcionais

- Nenhuma mudança em `app/`, `email-service/`, `email-lambda/` nem `blackbox-tests/`: nenhum
  deles depende do proxy (conferido no `master` em 2026-10-08).

## Fora de escopo

- **Registros históricos**: as specs 05-022, 05-023, 05-024, 05-025 e 05-027 e os `plan.md`/
  `tasks.md` da 05-033 citam o proxy como fato da época. Ficam como estão (mesma decisão da
  spec 05-035 para o `sandbox`). O mesmo vale para os diários em `docs/context/`.
- **Documento da disciplina** (`docs/disciplina/`): já descreve o proxy só como uma opção
  considerada (PR #112).
- O perfil e o ambiente `blackbox` (spec 05-014), o `docker-compose.blackbox.yml` e a suíte
  `blackbox-tests/`: são outra coisa, apesar do nome, e continuam.

## Decisões em aberto

Nenhuma de requisito. A escolha do exemplo novo para a constitution está no `plan.md`.
