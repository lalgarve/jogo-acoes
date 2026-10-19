# Plan: Perfil LocalStack Pro e script de subida do ambiente

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `docker-compose.yml` sobe o LocalStack com `localstack/localstack:4` (SQS, SES, S3 e Lambda) e
  os scripts de `docker/localstack/init/` (fila, remetente verificado, `email-lambda` implantada
  com *event source mapping*).
- O overlay `docker-compose.blackbox.yml` (spec 05-014) já estabeleceu o padrão de arquivo
  empilhado com `-f`/`COMPOSE_FILE`, nunca usado sozinho.
- A sequência de subida que funciona está na seção 3 do caderno de testes e na constitution
  ("Verificação manual: comandos prontos para copiar"): bancos e LocalStack, `restore`,
  `email-service`, `restore`, `app`.
- Comportamento das imagens, conferido nesta sessão (Docker 29, imagens puxadas em 2026-10-17):

  | Imagem | Versão | Sem token | Token inválido |
  |---|---|---|---|
  | `localstack/localstack:4` | 4.14.0 | sobe (aviso de imagem unificada) | — |
  | `localstack/localstack-pro:4` | 4.14.0 | sai com 55, "No credentials were found" | sai com 55, "credentials ... are invalid" |

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como escolher o Pro | Overlay `docker-compose.localstack-pro.yml`, que troca só `image` e acrescenta `LOCALSTACK_AUTH_TOKEN` ao serviço `localstack` | resolvida | Mesmo padrão do overlay `blackbox`, que o projeto já usa. *Profiles* do Compose não servem: um serviço sem *profile* sobe sempre, então não dá para ter "Community, a não ser que o perfil Pro esteja ativo" sem duplicar o serviço inteiro (e os `depends_on` de `email-service` e `ses-viewer` apontam para um nome só). Uma variável `LOCALSTACK_IMAGE` funcionaria, mas o token continuaria precisando de tratamento à parte. |
| Imagem padrão | `docker-compose.yml` continua com o Community | resolvida | Requisito da Leila: quem não tem licença sobe a aplicação. O CI também não precisa de segredo. |
| Token ausente com o overlay | `${LOCALSTACK_AUTH_TOKEN:?mensagem}` | resolvida | Quem empilhou o overlay escolheu o Pro: parar no Compose, com a orientação, é melhor do que deixar o LocalStack subir e sair com 55. Não atrapalha mais ninguém, porque o arquivo base não referencia a variável. |
| Tag do Pro | `localstack/localstack-pro:4`, mesma tag do Community | resolvida | Mesma versão (4.14.0) dos dois lados: trocar de um para o outro não muda o comportamento dos serviços que o projeto usa. |
| Onde fica a configuração | `docker-up.conf` (local, no `.gitignore`) e `docker-up.conf.example` (versionado), na raiz, ao lado dos `docker-compose*.yml` | resolvida | Pedido da Leila: um modelo e um local não versionado. Sem o local, o script usa o modelo, que não precisa de token: quem acabou de clonar roda `./scripts/docker-up.sh` sem preparar nada. |
| Formato do arquivo | `CHAVE=valor`, lido linha a linha; chave desconhecida, chave ausente e booleano inválido param o script | resolvida | Igual a um `.env`, mas sem `source`: o arquivo é configuração, não código. Erro de digitação numa chave (ex.: `LOCALSTACK_PR0`) não pode virar "Community sem aviso". |
| Precedência do token | Do arquivo; vazio, o `LOCALSTACK_AUTH_TOKEN` exportado | resolvida | Quem já guarda o token no perfil do shell não precisa copiá-lo para o arquivo. |
| Opções do arquivo | `BLACKBOX`, `LOCALSTACK_PRO`, `LOCALSTACK_AUTH_TOKEN`, `BUILD`, `TOOLS` | resolvida | As escolhas que se repetem a cada subida. `--reset` (`down -v`) fica como argumento: é decisão de cada vez, não preferência. |
| Falha na subida | Se o container do LocalStack saiu, o script mostra as últimas 15 linhas do log dele | resolvida | O motivo da licença só aparece no log do LocalStack; o Compose só diz `exited (55)`. |
| Teste automatizado | Nenhum; verificação manual dos cenários (`tasks.md`) | resolvida | O projeto não tem infraestrutura de teste para scripts de shell, e o script não tem regra de negócio: é a sequência de comandos que a constitution já descreve. Nenhuma classe de exceção nova. |

## Estrutura de módulos/pacotes

| Arquivo | Mudança |
|---|---|
| `docker-compose.localstack-pro.yml` | Novo: overlay do Pro |
| `docker-up.conf.example` | Novo: modelo de configuração |
| `scripts/docker-up.sh` | Novo: subida do ambiente |
| `.gitignore` | `docker-up.conf` |
| `README.md` | Seção sobre o script e o LocalStack Pro |

Nada muda em `app/`, `email-service/`, `email-lambda/` nem no CI.

## Riscos e trade-offs

- **`docker compose` fora do script com outros arquivos:** um `docker compose up` sem o overlay
  depois de uma subida com Pro recria o LocalStack com o Community (o Compose vê a configuração
  mudada). Não quebra nada, mas perde o estado do LocalStack. O script mostra no fim o
  `export COMPOSE_FILE=...` para evitar isso.
- **Futuro da imagem Community:** o LocalStack avisa que vai unificar as imagens. Se a tag `:4`
  do Community um dia passar a exigir conta, o padrão quebra para quem não tem token; aí a saída
  é fixar a versão exata (`4.14.0`) no `docker-compose.yml`. Hoje não é necessário.
- **Token no disco:** `docker-up.conf` guarda o token em texto puro, como qualquer `.env`. Fica
  fora do Git pelo `.gitignore`; quem preferir exporta a variável e deixa o campo vazio.
