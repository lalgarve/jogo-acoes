# Tasks: Dockerfiles que não quebram quando o cache do Maven some

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** a reprodução do defeito vem primeiro, no Dockerfile atual; depois a publicação da
biblioteca (spec 014 de `lalgarve/api-key`) e a correção. Nenhuma task começa sem pedido explícito
(constitution, "Nunca começar a implementar sem pedido explícito").

Issue: #213 (épico com as tarefas abaixo como checklist, labels `iteration-5` e `fix`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Reproduzir o defeito no `email-service/Dockerfile` atual com o passo 1 da verificação manual abaixo e registrar aqui a saída: o build tem que falhar procurando `api-key-validation:jar:1.1.0` no Maven Central. Também o passo 2 no estado atual, para registrar que hoje uma mudança em `docs/context/` recompila as duas imagens | — | | — |
| T002 | `api-key-validation` 1.1.1 publicada no Maven Central (spec 014 de `lalgarve/api-key`) e confirmada em `https://repo.maven.apache.org/maven2/dev/leilaalgarve/apikey/api-key-validation/1.1.1/` | — | [P] | — |
| T003 | `email-service/pom.xml`: `api-key-validation` 1.1.1 e comentário corrigido. Remover `scripts/install-api-key-lib.sh` e as chamadas em `.github/workflows/ci.yml` (step e comentário) e `scripts/cloud-up.sh`; atualizar as duas menções do `README.md` e anotar na spec 05-030 que a decisão de distribuição foi substituída por esta spec | T001, T002 | | — |
| T004 | `email-service/Dockerfile`: saem o `COPY` e o `RUN` do script e o `RUN` do `dependency:go-offline`; `COPY docs docs` vira `COPY docs/openapi-email-service.yaml docs/openapi-email-service.yaml`. Comentários atualizados: de onde vem a biblioteca e qual arquivo de `docs/` é copiado | T003 | | — |
| T005 | `Dockerfile` (`app`): `COPY docs docs` vira `COPY docs/openapi.yaml docs/openapi.yaml` e `COPY docs/openapi-email-service.yaml docs/openapi-email-service.yaml`; comentário corrigido (hoje cita só o `openapi.yaml`) | T001 | [P] | — |
| T006 | `config-server/Dockerfile`: sai o `RUN` do `dependency:go-offline` | T001 | [P] | — |
| T007 | Critério de aceite de exceções (`memory/constitution.md`): nenhuma classe de exceção nem branch de exceção em código Java. Registrar aqui o caminho de erro de build (biblioteca ausente do `~/.m2` com camadas antigas no cache) e onde ele é exercitado (T001 antes da correção; passo 1 da verificação manual depois) | T003–T006 | | — |
| T008 | CI da PR verde: resolve a biblioteca do Central sem o passo de instalação, e o `docker compose up` constrói as imagens do `email-service` e do `config-server` com os Dockerfiles novos. Registrar aqui o link da execução | T003–T006 | | — |
| T009 | Verificação manual, com os comandos abaixo | T003–T006 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Verificação manual (T009)

Na raiz do repositório, no WSL, no branch da correção. Os passos 1 a 3 só constroem imagens, sem
subir containers. Os arquivos `t062` são temporários e cada passo os apaga no fim.

O passo 1 apaga o cache do Maven do BuildKit (o `/root/.m2` compartilhado pelo `email-service` e
pelo `config-server`): o build seguinte baixa as dependências do Maven Central de novo.

### 1. Cache do Maven apagado, camadas mantidas

```bash
docker compose build email-service
docker buildx prune -f --filter type=exec.cachemount
echo t062 > email-service/src/main/resources/t062.txt
docker compose build --progress=plain email-service 2>&1 | grep -E 'Downloaded from central: .*api-key-validation-1\.1\.1\.jar|api-key-validation:jar'; echo "exit=${PIPESTATUS[0]}"
rm email-service/src/main/resources/t062.txt
```

Esperado: uma linha com `Downloaded from central:` e `api-key-validation-1.1.1.jar`, nenhuma com
`api-key-validation:jar`, e `exit=0`.

No Dockerfile atual (T001): nenhuma linha `Downloaded from central`, linhas de erro com
`api-key-validation:jar:1.1.0` e `https://repo.maven.apache.org/maven2`, e `exit=1`.

### 2. Mudança só em `docs/`

```bash
docker compose build app email-service
echo t062 > docs/context/t062.md
docker compose build --progress=plain app 2>&1 | grep -A1 'package -DskipTests' | grep -c CACHED
docker compose build --progress=plain email-service 2>&1 | grep -A1 'package -DskipTests' | grep -c CACHED
rm docs/context/t062.md
```

Esperado: `1` e `1` (o passo de compilação das duas imagens vem do cache). No Dockerfile atual
(T001): `0` e `0`.

### 3. Mudança no contrato

```bash
echo '# t062' >> docs/openapi-email-service.yaml
docker compose build --progress=plain app 2>&1 | grep -A1 'package -DskipTests' | grep -c CACHED
docker compose build --progress=plain email-service 2>&1 | grep -A1 'package -DskipTests' | grep -c CACHED
git checkout -- docs/openapi-email-service.yaml
echo '# t062' >> docs/openapi.yaml
docker compose build --progress=plain app 2>&1 | grep -A1 'package -DskipTests' | grep -c CACHED
docker compose build --progress=plain email-service 2>&1 | grep -A1 'package -DskipTests' | grep -c CACHED
git checkout -- docs/openapi.yaml
```

Esperado: `0`, `0` (o `openapi-email-service.yaml` recompila as duas), depois `0`, `1` (o
`openapi.yaml` recompila só o `app`).

### 4. As imagens continuam funcionando

```bash
docker run --rm --entrypoint ls jogo-acoes-app /app
./scripts/docker-up.sh; echo "exit=$?"
docker compose down -v
git status --short
```

Esperado: `app.jar`, `jacocoagent.jar` e `jacococli.jar`; o `docker-up.sh` sobe tudo e termina
com `exit=0`; nenhuma linha no `git status` (os arquivos `t062` apagados e os YAML restaurados).
