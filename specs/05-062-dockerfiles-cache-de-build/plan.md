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
- `scripts/install-api-key-lib.sh` (spec 05-030) sai com `exit 0` e
  `api-key 1.1.0 already in ... -- nothing to do` quando os três artefatos estão no `~/.m2`; só
  então acessa o GitHub. Continua como está: a distribuição pela release fica (Leila,
  2026-10-26).
- CI (`.github/workflows/ci.yml`): o `docker compose up` constrói as imagens do `email-service` e
  do `config-server` num runner novo, sem cache de build. Ele exercita o build dos Dockerfiles
  alterados, mas não o defeito, que depende de camadas antigas com o cache mount vazio.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como garantir que o script confere o `~/.m2` antes do Maven | O script roda no mesmo `RUN` do `mvn package`: `RUN --mount=type=cache,target=/root/.m2 ./scripts/install-api-key-lib.sh && mvn -B -pl email-service -am package -DskipTests` | resolvida | O `RUN` do `package` roda sempre que o `src`, o `docs/openapi-email-service.yaml` ou um `pom.xml` mudam, e o script vai junto, com o mesmo cache mount que o Maven vai ler. Com a biblioteca no cache, o custo é o teste de três arquivos. Alternativas: **invalidar a camada do script a cada build** (`ARG` com data), que roda o script mesmo quando nada mais roda, sem ganho; **`id` próprio no cache mount**, que não muda nada, porque o problema é a camada separada, não o compartilhamento |
| `dependency:go-offline` do `email-service` | Sai | resolvida | Com cache mount, o que ele baixa vai para o cache mount e não para a camada; o `package` baixa o que faltar do mesmo jeito. Mantê-lo exigiria o script no `RUN` dele também (seria mais um comando Maven lendo o `~/.m2`, `spec.md`), para nenhum ganho. Efeito: o `COPY` do script e o `RUN` passam para depois do `COPY` do `src` |
| Onde fica o `COPY` do script | Antes do `COPY` do `src`, junto com os outros `COPY` que não mudam com frequência | resolvida | A ordem dos `COPY` não muda o resultado, só o que é invalidado. O script quase nunca muda; o `src` muda sempre. |
| Arquivos de `docs/` no `email-service` | `COPY docs/openapi-email-service.yaml docs/openapi-email-service.yaml` | resolvida | Único arquivo de `docs/` que o `email-service/pom.xml` lê (gerador do `openapi-generator-maven-plugin` e cópia do `maven-antrun-plugin` para o Swagger UI, spec 05-049). O YAML não tem `$ref` para outro arquivo |
| Arquivos de `docs/` no `app` | `COPY docs/openapi.yaml docs/openapi.yaml` e `COPY docs/openapi-email-service.yaml docs/openapi-email-service.yaml` | resolvida | `app/pom.xml` lê `openapi.yaml` no gerador do servidor e no `maven-resources-plugin` (`<include>openapi.yaml</include>`), e `openapi-email-service.yaml` na execução `email-service-client` (cliente Feign, spec 05-034). Nenhum dos dois tem `$ref` externo |
| `config-server/Dockerfile` | — | em aberto (ver `spec.md`, "Decisões em aberto") | Tem o mesmo `go-offline` com cache mount, sem ganho, mas não quebra: as dependências dele estão todas no Maven Central. Se entrar, sai o `go-offline` e nada mais muda (não lê `docs/` nem a `api-key-validation`) |
| Como provocar "cache mount apagado, camadas mantidas" na verificação | `docker buildx prune -f --filter type=exec.cachemount` | resolvida | Apaga só os cache mounts e deixa as camadas, que é o estado que a política de limpeza produz depois de 48 h. Hoje só existe um cache mount (o `/root/.m2` compartilhado), então o efeito colateral é o `config-server` baixar as dependências de novo no próximo build |
| Como forçar o `RUN` do `package` a rodar sem mudar código | Um arquivo temporário em `email-service/src/main/resources/`, apagado no fim | resolvida | O `COPY` do `src` compara o conteúdo, então um arquivo novo invalida a camada; não entra no Git e não muda o jar que fica no fim |

## Estrutura de módulos/pacotes

Só `Dockerfile`, `email-service/Dockerfile` e, se a decisão em aberto incluir, o
`config-server/Dockerfile`. Nenhuma mudança em código Java, `pom.xml`, `docker-compose.yml` nem
nos scripts.

## Riscos e trade-offs

- **Um arquivo novo em `docs/` que a compilação passe a ler** (ex.: um `$ref` do OpenAPI para um
  arquivo separado) quebra o build da imagem até alguém acrescentar o `COPY`. O erro aparece no
  `generate-sources`, com o nome do arquivo, e os comentários dos dois Dockerfiles passam a dizer
  quais arquivos de `docs/` são copiados e por quê.
- **Sem `go-offline`, um `src` alterado com o cache mount vazio baixa as dependências no `RUN` do
  `package`.** É o mesmo download de hoje, só em outro passo.
- **Exceções:** nenhuma classe de exceção nem branch de exceção em código Java. O caminho de erro
  é de build: biblioteca ausente do `~/.m2` com as camadas antigas no cache, exercitado pela
  verificação manual (`tasks.md`).
