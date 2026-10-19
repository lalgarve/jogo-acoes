# Plan: Ambiente completo numa sessão de nuvem da Claude

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

O que a sessão de nuvem oferece e impõe, conferido em 2026-10-17:

| Item | Situação |
|---|---|
| Docker | Instalado (29.x), mas o daemon não está rodando quando a sessão começa: precisa de `dockerd` |
| Saída para a internet | Só pelo proxy `HTTPS_PROXY=http://127.0.0.1:42333`, que reassina o TLS com a CA `/root/.ccr/ca-bundle.crt` |
| Java/Maven no host | Já configurados para o proxy (`JAVA_TOOL_OPTIONS` com proxy e truststore com a CA) |
| Imagens Docker | `docker pull` funciona (o `dockerd` herda o proxy do shell que o iniciou) |
| Containers na rede do Compose | Não alcançam o proxy em `127.0.0.1`: sem Maven nos `Dockerfile`s, sem licença do LocalStack Pro |
| Containers com `--network host` | Alcançam o proxy |
| Relay do proxy para os containers | Bloqueado pela segurança do ambiente; não é usado |
| `LOCALSTACK_AUTH_TOKEN` | Variável do ambiente de nuvem; ativa uma licença *student* |
| PyPI | Acessível (venv e `pip install` funcionaram) |
| Container da sessão | Reciclado quando fica parado: Docker, processos e volumes somem |

A receita que funcionou, passo a passo, é a base deste plano:

1. `scripts/install-api-key-lib.sh` e `mvn -B package -DskipTests` no host.
2. `docker compose up -d --wait db db-email-service dns`.
3. LocalStack Pro com rede `host`:
   ```bash
   docker run -d --name jogo-acoes-localstack --network host \
     --add-host localhost.localstack.cloud:127.0.0.1 \
     --add-host sqs.us-east-1.localhost.localstack.cloud:127.0.0.1 \
     -e LOCALSTACK_AUTH_TOKEN -e SERVICES=sqs,ses,s3,lambda \
     -e EMAIL_SENDER_ADDRESS=no-reply@jogo-acoes.example \
     -e LAMBDA_DOCKER_NETWORK=bridge \
     -e LAMBDA_DOCKER_FLAGS="--add-host localhost.localstack.cloud:host-gateway" \
     -e HTTPS_PROXY -e NO_PROXY=localhost,127.0.0.1,.localhost.localstack.cloud,localhost.localstack.cloud \
     -e REQUESTS_CA_BUNDLE=/etc/ccr-ca.crt -e SSL_CERT_FILE=/etc/ccr-ca.crt -e CURL_CA_BUNDLE=/etc/ccr-ca.crt \
     -v /root/.ccr/ca-bundle.crt:/etc/ccr-ca.crt:ro \
     -v /var/run/docker.sock:/var/run/docker.sock \
     -v "$PWD/docker/localstack/init:/etc/localstack/init/ready.d:ro" \
     -v "<pasta com o function.zip>:/tmp/email-lambda-target:ro" \
     localstack/localstack-pro:4
   ```
4. `test-api-key.sh restore`; `email-service` com `java -jar`; `restore` de novo; `app` com
   `java -jar`, perfis `docker,blackbox`.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde rodam `config-server`, `email-service` e `app` | No host, com `java -jar` sobre os JARs de `target/` | resolvida | As imagens não compilam na nuvem (Maven sem proxy no build). Montar imagens a partir dos JARs do host exigiria `Dockerfile`s só para a nuvem, e os serviços ainda precisariam falar com o LocalStack em rede `host`. No host, Java já confia no proxy e tudo fala por `localhost`. |
| Onde roda o LocalStack | Container com `--network host` | resolvida | Único jeito de alcançar o proxy para ativar a licença sem relay. Os serviços no host o acham em `localhost:4566`, como no perfil `docker` padrão. |
| Rede dos containers do Lambda | `LAMBDA_DOCKER_NETWORK=bridge` e `localhost.localstack.cloud` apontando para `host-gateway` | resolvida | Com `LAMBDA_DOCKER_NETWORK=host` o LocalStack não descobre o IP do container do Lambda ("Unable to detect IP address ... in network host") e toda invocação falha. Em `bridge`, o Lambda chega ao LocalStack pelo gateway do host. O Lambda não precisa de internet. |
| Postgres e DNS de teste | Pelo `docker-compose.yml`, sem overlay | resolvida | Não precisam de internet e já publicam as portas no host (5432, 5433, 1053). Reaproveita a definição versionada. |
| Configuração dos serviços Java | Variáveis de ambiente iguais às do `docker-compose.yml` e do `docker-compose.blackbox.yml`, trocando nome de serviço por `localhost` (e o `dns:53` por `127.0.0.1:1053`) | resolvida | Mesmos valores de teste, já versionados. Ver risco de divergência abaixo. |
| Porta do `email-service` | `SERVER_PORT=8082` | resolvida | A mesma porta que o Compose publica; o `app` já usa `http://localhost:8082/api` fora do Compose. |
| Estado de uma subida para a outra | Sempre do zero: `cloud-up.sh` roda o `cloud-down.sh` antes | resolvida | O `email-service` só envia ao SES os templates que mudaram. Se o LocalStack é recriado com o banco do `email-service` ainda cheio, os templates somem do SES e todo e-mail falha com `TemplateDoesNotExistException` (aconteceu em 2026-10-17). Começar sempre do zero elimina essa armadilha. A subida custa poucos minutos. |
| Prontidão do LocalStack | Esperar `GET /_localstack/init/ready` dizer que os scripts terminaram, e conferir que nenhum falhou | resolvida | O *healthcheck* do LocalStack fica verde antes de o init criar a fila, verificar o remetente e implantar a `email-lambda`. Conferir o formato exato da resposta na implementação. |
| Prontidão dos serviços Java | Esperar a linha `Started ...Application` no log, ou `APPLICATION FAILED` / processo morto | resolvida | Mesmo critério usado à mão. Porta aberta também serviria, mas o log dá o motivo da falha de graça. Tempo limite de 120 s por serviço. |
| Onde ficam logs, PIDs, `function.zip` e venv | `${JOGO_ACOES_CLOUD_DIR:-/tmp/jogo-acoes-cloud}` | resolvida | Fora do repositório, então nada para o `.gitignore`. Some com o container, como o resto do ambiente. |
| Como derrubar os processos Java | Pelos PIDs gravados na subida | resolvida | `pkill -f` com o nome do JAR casou com o próprio shell que rodava o comando e o matou (aconteceu em 2026-10-17). PID gravado não tem esse problema e não mata processo de outra pessoa. |
| Como reconhecer a sessão de nuvem | `HTTPS_PROXY` definido e `/root/.ccr/ca-bundle.crt` existente | resolvida | São exatamente as duas coisas de que o script depende. Fora disso, mensagem indicando o `scripts/docker-up.sh`. |
| Token ausente | Parar com erro antes de fazer qualquer coisa, sem cair para o LocalStack Community | resolvida (Leila, 2026-10-19) | Um teste que passa no Community não prova nada sobre o Pro, e uma troca silenciosa (ou só avisada) de imagem deixaria dúvida sobre o que foi testado. Alternativa descartada: Community com aviso, que deixaria o script útil para quem não tem token; quem não tem token continua com o `scripts/docker-up.sh` no WSL. |
| Cliente Python gerado | Gerado na pasta de estado com `blackbox-tests/openapi-python-client-config.yaml` | resolvida | Depende do `project_name_override`, que chega pela PR #178 (spec 05-050) em `development`. Sem ele, o `pip` recusa o nome `jogo-de-açõ-es-api-client`. Ver T001. |
| SES antes da suíte | `DELETE /_aws/ses` antes do `behave` | resolvida | Um e-mail que sobrou no SES para o mesmo destinatário (o administrador) faz a suíte ler um link antigo, já invalidado, e falhar o login (aconteceu em 2026-10-17). |
| SessionStart hook | Nenhum | resolvida | Subir tudo a cada sessão custa minutos e nem toda sessão precisa da aplicação no ar. O próprio `cloud-up.sh` inicia o `dockerd`, então não sobra nada para um hook preparar. Pode ser revisto se o uso mostrar que vale. |
| Teste automatizado dos scripts | Nenhum; verificação manual (`tasks.md`) | resolvida | Mesmo critério da spec 05-050: sem infraestrutura de teste para shell, e o script é a sequência de comandos documentada aqui. Nenhuma classe de exceção nova. |

## Estrutura de módulos/pacotes

| Arquivo | Mudança |
|---|---|
| `scripts/cloud-up.sh` | Novo: sobe o ambiente |
| `scripts/cloud-down.sh` | Novo: derruba o ambiente |
| `scripts/cloud-blackbox-tests.sh` | Novo: roda a suíte de caixa-preta |
| `README.md` | Seção sobre sessões de nuvem, ao lado da do `docker-up.sh` |
| `CLAUDE.md` | Aviso curto: numa sessão de nuvem, subir a aplicação com `scripts/cloud-up.sh` |
| `docker-compose.yml`, `docker-compose.blackbox.yml` | Só comentário: ao mudar variáveis de `app`/`email-service`/`config-server`/`localstack`, atualizar também `scripts/cloud-up.sh` |

Código de ambiente fica em `scripts/`, fora dos módulos de produção (constitution, "Código de
teste/dev nunca dentro da aplicação").

## Armadilhas conhecidas

Todas vistas em 2026-10-17, e cada uma tem uma decisão acima que a evita:

| Sintoma | Causa |
|---|---|
| LocalStack Pro sai com 55, "Could not reach the LocalStack licensing server" | Container sem acesso ao proxy |
| Lambda falha com "Unable to detect IP address ... in network host" | `LAMBDA_DOCKER_NETWORK=host` |
| Lambda falha com `TemplateDoesNotExistException` | LocalStack recriado depois de o `email-service` já ter enviado os templates |
| Suíte falha com `LoginLinkInvalidException` | E-mail antigo no SES para o mesmo destinatário |
| O shell morre com código 144 | `pkill -f` casando com o próprio comando |
| `pip` recusa `jogo-de-açõ-es-api-client` | Cliente gerado sem `project_name_override` |

## Riscos e trade-offs

- **Divergência do `docker-compose.yml`:** as variáveis dos serviços Java e do LocalStack ficam
  copiadas no script. Uma variável nova no Compose que não for copiada só aparece como falha na
  nuvem. Mitigação: comentário nos arquivos do Compose apontando para o script, e a suíte de
  caixa-preta, que pega a maior parte das divergências. Gerar a configuração a partir de
  `docker compose config` foi considerado e deixado de fora: exigiria traduzir nomes de serviço
  e portas internas, mais complexo que a cópia.
- **Diferença em relação ao WSL:** na nuvem os serviços Java rodam fora de container. Um problema
  só da imagem Docker (ex.: um arquivo que o `Dockerfile` esquece de copiar) não aparece aqui. O
  build das imagens continua coberto pelo CI e pelo WSL.
- **Mudanças no ambiente de nuvem:** porta do proxy, caminho da CA ou comportamento do Docker
  podem mudar. O script lê `HTTPS_PROXY` e testa a CA em vez de fixar valores, e falha cedo com
  mensagem clara.
- **Licença *student* do LocalStack:** cada subida ativa a licença de novo. Se houver limite de
  ativações, aparece como falha de licença (cenário 5).
