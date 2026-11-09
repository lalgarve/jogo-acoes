# Spec: Dockerfiles que não quebram quando o cache do Maven some

**Status:** rascunho
**Issue:** #213
**Iteração:** iteration-5
**Depende de:** `api-key-validation` 1.1.1 publicada no Maven Central (spec 014 do repositório
`lalgarve/api-key`)

## Resumo

Dois defeitos nos Dockerfiles da raiz (`app`) e do `email-service`:

1. **`email-service`: a biblioteca `api-key-validation` só é instalada quando o Docker resolve
   rodar o passo dela.** Quando o cache do Maven (`/root/.m2`) é apagado e as camadas do build
   não, o build falha procurando a biblioteca no Maven Central. A biblioteca passa a ser publicada
   no Maven Central e o `email-service` passa a depender dela como de qualquer outro artefato: o
   Maven a baixa quando faltar, e o script de instalação deixa de existir.
2. **`app` e `email-service` copiam a pasta `docs/` inteira** por causa de um ou dois arquivos
   OpenAPI. Qualquer mudança em `docs/` (um `.md` do diário, um diagrama) faz o Docker refazer a
   compilação. Passam a copiar só os arquivos que a compilação lê.

## Motivação

### A biblioteca some do cache

O `email-service` depende de `dev.leilaalgarve.apikey:api-key-validation:1.1.0`, que não está no
Maven Central. `scripts/install-api-key-lib.sh` a baixa da release do GitHub e a instala no
`~/.m2`, e não faz nada quando ela já está lá: o GitHub só é acessado na primeira vez (spec
05-030). No `email-service/Dockerfile`, o `~/.m2` é um cache mount do BuildKit
(`--mount=type=cache,target=/root/.m2`), para que a biblioteca e as dependências do Maven
sobrevivam entre builds.

O script roda num `RUN` próprio, separado do `RUN` que compila:

```dockerfile
RUN --mount=type=cache,target=/root/.m2 ./scripts/install-api-key-lib.sh
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl email-service -am dependency:go-offline
...
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl email-service -am package -DskipTests
```

O BuildKit guarda e apaga as camadas do build e o cache mount com regras diferentes. No Docker
Desktop do WSL (`docker buildx inspect`, "GC Policy"), um cache mount sem uso há 48 h, ou acima de
2,76 GiB, é apagado. Uma camada só é apagada depois de 60 dias ou quando falta espaço. Depois de
dois dias sem build do `email-service`:

1. o `src` mudou, então o `RUN` do `package` roda de novo;
2. os `RUN` do script e do `go-offline` vêm prontos das camadas, sem rodar: o script não chega a
   conferir o `~/.m2`;
3. o `~/.m2` já não tem a biblioteca, e o Maven a procura no Maven Central e falha.

Aconteceu em 2026-10-24, na verificação manual da spec 05-056 no WSL:

```
dev.leilaalgarve.apikey:api-key-validation:jar:1.1.0 was not found in https://repo.maven.apache.org/maven2 during a previous attempt.
```

`docker compose build --no-cache email-service` resolve na hora, porque refaz o passo do script,
mas o defeito volta no próximo intervalo de dois dias sem build.

Com a biblioteca no Maven Central, um cache vazio passa a ser só um download, como para qualquer
outra dependência. Isso também elimina o passo de instalação no CI, no `scripts/cloud-up.sh` e no
build local.

O `dependency:go-offline` em camada própria veio do `Dockerfile` do `app`, onde roda sem cache
mount: lá as dependências ficam gravadas na camada, e a camada evita baixá-las de novo enquanto os
`pom.xml` não mudam. No `email-service` e no `config-server`, com cache mount, o que ele baixa vai
para o cache mount, não para a camada, e o `package` lê do mesmo cache mount. A camada não poupa
nada.

### A pasta `docs/` inteira

| Dockerfile | Copia | A compilação lê |
|---|---|---|
| `Dockerfile` (`app`) | `COPY docs docs` | `docs/openapi.yaml` (gerador e Swagger UI) e `docs/openapi-email-service.yaml` (cliente Feign do `email-service`, spec 05-034) |
| `email-service/Dockerfile` | `COPY docs docs` | `docs/openapi-email-service.yaml` (gerador e Swagger UI, spec 05-049) |

`docs/` tem hoje 44 MB: diagramas, `disciplina/`, `context/`. O `COPY` invalida a camada quando
qualquer arquivo da pasta muda, e com ela todos os passos seguintes, inclusive a compilação. O
comentário do `Dockerfile` do `app` também está desatualizado: cita só o `openapi.yaml`.

## Cenários (comportamento esperado)

Não há `.feature` novo: a mudança é no build das imagens, não no comportamento dos serviços.
Verificação (comandos prontos em `tasks.md`):

1. **Cache do Maven apagado, camadas mantidas:** com as camadas do build do `email-service` no
   cache e o cache mount `/root/.m2` apagado, `docker compose build email-service` (depois de uma
   mudança no `src`) baixa a biblioteca do Maven Central e termina com sucesso.
2. **Mudança só em `docs/`:** mudar um arquivo de `docs/` que não seja OpenAPI (ex.: um `.md` de
   `docs/context/`) e refazer o build do `app` e do `email-service`: o passo de compilação vem do
   cache (`CACHED`).
3. **Mudança no contrato:** mudar `docs/openapi-email-service.yaml` refaz a compilação das duas
   imagens; mudar `docs/openapi.yaml` refaz só a do `app`.
4. **Build sem credencial:** o build das imagens, o CI e o build local resolvem a biblioteca sem
   `settings.xml`, sem token e sem passo de instalação.

## Requisitos funcionais

- `email-service/pom.xml` depende de `api-key-validation` 1.1.1, resolvida do Maven Central.
- `scripts/install-api-key-lib.sh` deixa de existir, e nada mais o chama (`email-service/Dockerfile`,
  `.github/workflows/ci.yml`, `scripts/cloud-up.sh`, `README.md`).
- `email-service/Dockerfile` e `config-server/Dockerfile` não têm mais o `RUN` do
  `dependency:go-offline`.
- `email-service/Dockerfile` copia de `docs/` só `docs/openapi-email-service.yaml`.
- `Dockerfile` (`app`) copia de `docs/` só `docs/openapi.yaml` e `docs/openapi-email-service.yaml`.
- Comentários dos Dockerfiles e do `email-service/pom.xml` atualizados: de onde vem a biblioteca e
  quais arquivos de `docs/` cada build lê.
- As imagens resultantes não mudam, além da versão da biblioteca: mesmos agentes do JaCoCo no
  `app`, mesma `ENTRYPOINT`.

## Requisitos não-funcionais

- Nenhuma credencial do Maven Central no build deste repositório: ela só é usada para publicar, no
  repositório `lalgarve/api-key`.
- O build de um `src` alterado não fica mais lento do que hoje com o cache cheio.

## Fora de escopo

- A publicação da biblioteca (spec 014 do repositório `lalgarve/api-key`).
- A CLI da `api-key` e a chave de teste gerada com ela (`docker/postgres-email-service/test-data/`,
  `scripts/test-api-key.sh`): continuam na 1.1.0, que grava o mesmo formato.
- Mudar a política de limpeza do BuildKit do Docker Desktop.
- Cache mount do `~/.m2` no `Dockerfile` do `app`, que hoje não usa: o `go-offline` dele, sem cache
  mount, funciona como deve.
- Healthcheck do `app` no Compose (o `up --wait app` volta antes de o Spring ficar pronto,
  visto na mesma verificação da spec 05-056): é outro assunto.

## Decisões em aberto

Nenhuma.
