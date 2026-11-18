# Plan: Dockerfiles que não quebram quando o cache do Maven some

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- Três Dockerfiles de build Maven, todos com o contexto na raiz do repositório
  (`docker-compose.yml`): `Dockerfile` (`app`), `email-service/Dockerfile` e
  `config-server/Dockerfile`. A `email-lambda` não tem Dockerfile: o `email-lambda-builder` do
  Compose compila com a imagem do Maven e um volume.
- `email-service` e `config-server` montam `--mount=type=cache,target=/root/.m2` sem `id`. O
  BuildKit usa o `target` como `id`, então os dois compartilham o mesmo cache mount
  (`docker buildx du --verbose`: um só `exec.cachemount`, `id "//root/.m2"`). O `app` não usa
  cache mount.
- Política de limpeza do BuildKit no Docker Desktop do WSL (`docker buildx inspect`): regra 0,
  `type==exec.cachemount` (entre outros), `Keep Duration: 48h`, `Max Used Space: 2.764GiB`; regra
  1, demais registros, `Keep Duration: 1440h`. É o padrão do Docker Desktop, não configuração
  deste projeto.
- A biblioteca é publicada no Maven Central a partir da 1.1.1 (spec 014 de `lalgarve/api-key`,
  workflow "Release"). A 1.1.0 não está lá: por isso a T001 reproduz o defeito antes da troca de
  versão.
- CI (`.github/workflows/ci.yml`): chama o script antes dos testes do `email-service`; o
  `docker compose up` constrói as imagens num runner novo, sem cache de build. Ele exercita o build
  dos Dockerfiles alterados, mas não o defeito, que depende de camadas antigas com o cache mount
  vazio.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como garantir a biblioteca quando o `~/.m2` some | Publicar no Maven Central e depender dela como de qualquer artefato | resolvida (Leila, 2026-11-09) | Substitui a decisão de 2026-10-26 de manter a distribuição pela release, e a decisão "Como distribuir `api-key-validation` para o build" da spec 05-030. Remove a causa em vez de contorná-la. Alternativa anterior: rodar o script no mesmo `RUN` do `mvn package`, que corrige o Dockerfile mas mantém o script no CI, no `cloud-up.sh` e no build local |
| Versão da biblioteca | 1.1.1 | resolvida (Leila, 2026-11-09) | É a primeira publicada no Central (spec 014 de `lalgarve/api-key`). Sem mudança de código em relação à 1.1.0 |
| `scripts/install-api-key-lib.sh` | Sai, com as três chamadas | resolvida | Sem o script, não há passo que o Docker possa pular. Mantê-lo seria ter dois caminhos para a mesma dependência |
| `dependency:go-offline` do `email-service` | Sai | resolvida | Com cache mount, o que ele baixa vai para o cache mount e não para a camada; o `package` baixa o que faltar do mesmo jeito |
| `config-server/Dockerfile` | Sai o `go-offline`, nada mais muda | resolvida (2026-11-09) | Mesmo caso do `email-service`, e os dois Dockerfiles com cache mount ficam com o mesmo padrão. Não lê `docs/` nem a `api-key-validation` |
| Arquivos de `docs/` no `email-service` | `COPY docs/openapi-email-service.yaml docs/openapi-email-service.yaml` | resolvida | Único arquivo de `docs/` que o `email-service/pom.xml` lê (gerador do `openapi-generator-maven-plugin` e cópia do `maven-antrun-plugin` para o Swagger UI, spec 05-049). O YAML não tem `$ref` para outro arquivo |
| Arquivos de `docs/` no `app` | `COPY docs/openapi.yaml docs/openapi.yaml` e `COPY docs/openapi-email-service.yaml docs/openapi-email-service.yaml` | resolvida | `app/pom.xml` lê `openapi.yaml` no gerador do servidor e no `maven-resources-plugin` (`<include>openapi.yaml</include>`), e `openapi-email-service.yaml` na execução `email-service-client` (cliente Feign, spec 05-034). Nenhum dos dois tem `$ref` externo |
| CLI da `api-key` e chave de teste | Ficam na 1.1.0 | resolvida | A 1.1.1 não muda o código; gerar a chave de novo não traria nada |
| Como provocar "cache mount apagado, camadas mantidas" na verificação | `docker buildx prune -f --filter type=exec.cachemount` | resolvida | Apaga só os cache mounts e deixa as camadas, que é o estado que a política de limpeza produz depois de 48 h. Hoje só existe um cache mount (o `/root/.m2` compartilhado), então o efeito colateral é o `config-server` baixar as dependências de novo no próximo build |
| Como forçar o `RUN` do `package` a rodar sem mudar código | Um arquivo temporário em `email-service/src/main/resources/`, apagado no fim | resolvida | O `COPY` do `src` compara o conteúdo, então um arquivo novo invalida a camada; não entra no Git e não muda o jar que fica no fim |

## Estrutura de módulos/pacotes

`Dockerfile`, `email-service/Dockerfile`, `config-server/Dockerfile`, `email-service/pom.xml`,
`.github/workflows/ci.yml`, `scripts/cloud-up.sh`, `README.md` e a remoção de
`scripts/install-api-key-lib.sh`. Nenhuma mudança em código Java nem no `docker-compose.yml`.

## Riscos e trade-offs

- **Depende da publicação.** Enquanto a 1.1.1 não estiver no Central, o build do `email-service`
  falha em todo lugar com a versão nova: a T002 só começa depois da publicação.
- **Um arquivo novo em `docs/` que a compilação passe a ler** (ex.: um `$ref` do OpenAPI para um
  arquivo separado) quebra o build da imagem até alguém acrescentar o `COPY`. O erro aparece no
  `generate-sources`, com o nome do arquivo, e os comentários dos dois Dockerfiles passam a dizer
  quais arquivos de `docs/` são copiados e por quê.
- **Sem `go-offline`, um `src` alterado com o cache mount vazio baixa as dependências no `RUN` do
  `package`.** É o mesmo download de hoje, só em outro passo.
- **Exceções:** nenhuma classe de exceção nem branch de exceção em código Java. O caminho de erro
  é de build: biblioteca ausente do `~/.m2` com as camadas antigas no cache, exercitado pela
  verificação manual (`tasks.md`).
