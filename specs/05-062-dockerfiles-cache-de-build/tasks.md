# Tasks: Dockerfiles que não quebram quando o cache do Maven some

Quebra `plan.md` em tarefas pequenas, ordenadas, prontas para virar Issues (ver
"Rastreamento de trabalho via Issues" em `memory/constitution.md`).

**Ordem:** a reprodução do defeito vem primeiro, no Dockerfile atual; depois a correção. Nenhuma
task começa sem pedido explícito (constitution, "Nunca começar a implementar sem pedido
explícito").

Issue: a criar (épico com as tarefas abaixo como checklist, labels `iteration-5` e `fix`).

| ID | Descrição | Depende de | Paralelizável | Issue |
|---|---|---|---|---|
| T001 | Reproduzir o defeito no `email-service/Dockerfile` atual com o passo 1 da verificação manual abaixo e registrar aqui a saída: o build tem que falhar procurando `api-key-validation:jar:1.1.0` no Maven Central. Também o passo 3 no estado atual, para registrar que hoje uma mudança em `docs/context/` recompila as duas imagens | — | | — |
| T002 | `email-service/Dockerfile`: sai o `RUN` do `dependency:go-offline`; o script roda no mesmo `RUN` do `mvn package` (`./scripts/install-api-key-lib.sh && mvn ...`); `COPY docs docs` vira `COPY docs/openapi-email-service.yaml docs/openapi-email-service.yaml`. Comentários atualizados: por que o script roda junto com o Maven (as camadas e o cache mount são apagados em momentos diferentes, `plan.md`) e qual arquivo de `docs/` é copiado | T001 | | — |
| T003 | `Dockerfile` (`app`): `COPY docs docs` vira `COPY docs/openapi.yaml docs/openapi.yaml` e `COPY docs/openapi-email-service.yaml docs/openapi-email-service.yaml`; comentário corrigido (hoje cita só o `openapi.yaml`) | T001 | [P] | — |
| T004 | `config-server/Dockerfile`: sai o `RUN` do `dependency:go-offline`, se a decisão em aberto do `spec.md` o incluir; se não, a task sai da tabela | decisão em aberto | [P] | — |
| T005 | Critério de aceite de exceções (`memory/constitution.md`): nenhuma classe de exceção nem branch de exceção em código Java. Registrar aqui o caminho de erro de build (biblioteca ausente do `~/.m2` com camadas antigas no cache) e onde ele é exercitado (T001 antes da correção; passo 1 da verificação manual depois) | T002–T004 | | — |
| T006 | CI da PR verde: o `docker compose up` do CI constrói as imagens do `email-service` e do `config-server` com os Dockerfiles novos. Registrar aqui o link da execução | T002–T004 | | — |
| T007 | Verificação manual, com os comandos abaixo | T002–T004 | | — |

- **[P]** marca tarefas que não dependem umas das outras e podem ser feitas em qualquer
  ordem/em paralelo.
- Marcar o ID como concluído (`~~T001~~` ou checkbox `[x]`) na mesma PR que resolve a task,
  junto com o registro da verificação (`memory/constitution.md`, "Rastreamento de trabalho via
  Issues").

## Verificação manual (T007)

Na raiz do repositório, no WSL, no branch da correção. Os passos 1 a 4 só constroem imagens, sem
subir containers. Os arquivos `t062` são temporários e cada passo os apaga no fim.

O passo 1 apaga o cache do Maven do BuildKit (o `/root/.m2` compartilhado pelo `email-service` e
pelo `config-server`): o build seguinte baixa a biblioteca do GitHub e as dependências do Maven
Central de novo.

### 1. Cache do Maven apagado, camadas mantidas

```bash
docker compose build email-service
docker buildx prune -f --filter type=exec.cachemount
echo t062 > email-service/src/main/resources/t062.txt
docker compose build --progress=plain email-service 2>&1 | grep -E 'Installed api-key|nothing to do|api-key-validation:jar:1.1.0'; echo "exit=${PIPESTATUS[0]}"
rm email-service/src/main/resources/t062.txt
```

Esperado: uma linha com `Installed api-key 1.1.0 into /root/.m2/repository`, nenhuma com
`api-key-validation:jar:1.1.0`, e `exit=0`.

No Dockerfile atual (T001): nenhuma linha `Installed api-key`, linhas de erro com
`api-key-validation:jar:1.1.0` e `https://repo.maven.apache.org/maven2`, e `exit=1`.

### 2. Biblioteca no cache

```bash
echo t062 > email-service/src/main/resources/t062.txt
docker compose build --progress=plain email-service 2>&1 | grep -E 'Installed api-key|nothing to do'; echo "exit=${PIPESTATUS[0]}"
rm email-service/src/main/resources/t062.txt
```

Esperado: uma linha com `api-key 1.1.0 already in /root/.m2/repository -- nothing to do` (o
GitHub não é acessado) e `exit=0`.

### 3. Mudança só em `docs/`

```bash
docker compose build app email-service
echo t062 > docs/context/t062.md
docker compose build --progress=plain app 2>&1 | grep -A1 'package -DskipTests' | grep -c CACHED
docker compose build --progress=plain email-service 2>&1 | grep -A1 'package -DskipTests' | grep -c CACHED
rm docs/context/t062.md
```

Esperado: `1` e `1` (o passo de compilação das duas imagens vem do cache). No Dockerfile atual
(T001): `0` e `0`.

### 4. Mudança no contrato

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

### 5. As imagens continuam funcionando

```bash
docker run --rm --entrypoint ls jogo-acoes-app /app
./scripts/docker-up.sh; echo "exit=$?"
docker compose down -v
git status --short
```

Esperado: `app.jar`, `jacocoagent.jar` e `jacococli.jar`; o `docker-up.sh` sobe tudo e termina
com `exit=0`; nenhuma linha no `git status` (os arquivos `t062` apagados e os YAML restaurados).
