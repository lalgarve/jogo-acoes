# Plan: Visualizador de logs no ambiente Docker Compose

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

- `docker-compose.yml` já tem duas UIs de apoio aos testes manuais: `adminer` (porta 8081,
  spec 05-013) e `ses-viewer` (porta 3005, Issue #88/#156). Portas ocupadas no host: 3005,
  4566, 5432, 5433, 8080, 8081, 8082 e, com a spec 05-042 (Config Server, ainda não mesclada),
  8888.
- `app` e `email-service` escrevem os logs no stdout (Logback padrão do Spring Boot); o Docker
  guarda esses logs com o driver padrão (`json-file`). Os aspectos de log da spec 05-011
  registram em `DEBUG` e ficam desligados no perfil `production`.
- O `localstack` já monta `/var/run/docker.sock` (executor de Lambda), então o ambiente já
  depende do socket do Docker no Windows e no Linux.
- A CI sobe só os serviços que nomeia (`.github/workflows/ci.yml`), então um serviço novo no
  Compose não entra na CI.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Qual ferramenta | **Dozzle** (`amir20/dozzle`) | resolvida (Leila, 2026-10-13) | Lê os logs direto do daemon Docker pelo socket, em tempo real, sem guardar nada: sem banco, sem volume, sem agente em cada serviço e sem mudar a configuração de log dos containers. Uma imagem pequena (binário Go) e memória em repouso na ordem de dezenas de MB. Mostra vários containers juntos em ordem de tempo (RF3), com busca e filtro por texto/regex (RF4). Alternativas: **Grafana + Loki + Promtail/Alloy**, que dá consulta histórica e serve para staging/produção, mas são três containers, volume próprio e algumas centenas de MB, um custo que só se justifica com a observabilidade da Iteração 6; **Seq**, que exige mandar os logs para ele (appender no Logback ou GELF), ou seja, mexer na configuração dos serviços (contra RNF2) e um container com armazenamento próprio, com licença gratuita só para uso individual. |
| Alcance | Só `docker-compose.yml` | resolvida (Leila, 2026-10-13) | Mesmo alcance do Adminer e do `ses-viewer`. Em staging/produção os aspectos de log estão desligados e o Dozzle sem autenticação exporia os logs; a decisão de log centralizado fica para a Iteração 6. |
| Nome e porta | Serviço `log-viewer`, porta `8083:8080` | resolvida | Nome pela função, como `ses-viewer`. 8083 é a próxima livre depois do `email-service` (8082) e não colide com o Config Server (8888). |
| Estatísticas de uso | `DOZZLE_NO_ANALYTICS=true` | resolvida | O Dozzle manda estatísticas anônimas de uso por padrão; num ambiente de teste não há motivo para sair tráfego daqui. |
| Acesso ao Docker | Montar `/var/run/docker.sock` só leitura (`:ro`) | resolvida | É como o Dozzle lê os logs; o `localstack` já monta o mesmo socket, então não há requisito novo no Windows/Linux. |
| Quais containers mostrar | Todos os do projeto Compose, sem filtro | resolvida | O valor está em ver `app`, `email-service` e `localstack` juntos. Os containers da Lambda que o LocalStack cria também aparecem enquanto existem; a Leila aceitou que somem quando o LocalStack os recicla (2026-10-13). |
| Versão da imagem | Fixar a versão maior: `amir20/dozzle:v8` (v8.14.12 em 2026-10-13) | resolvida | Evita que uma versão nova mude a interface no meio de uma rodada do caderno. |
| Verificação automática | Healthcheck do Compose com o comando `healthcheck` do próprio binário do Dozzle, e `docker compose up -d --wait log-viewer` como teste | resolvida | Dá um teste que falha antes (serviço inexistente) e passa depois, sem código em módulo de produção. A conferência na tela (RF2–RF4) continua manual, feita pela Leila. |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

- `docker-compose.yml` (modificado): serviço `log-viewer`.
- `docs/disciplina/caderno-de-testes.md` (modificado): linha "Logs da aplicação" da tabela
  "Ferramentas".
- Nenhum módulo Maven novo nem alterado.

## Riscos e trade-offs

- **Histórico limitado ao que o Docker guarda.** O Dozzle não armazena nada: `docker compose
  down` (ou recriar o container) apaga os logs daquele container. Aceitável para testes
  manuais; quem quiser guardar copia o trecho, como o caderno já faz.
- **Socket do Docker.** Quem lê o socket enxerga todos os containers da máquina. Ambiente
  local apenas, mesmo risco que o `localstack` já aceita.
- **Containers da Lambda efêmeros.** Os logs da `email-lambda` só aparecem enquanto o LocalStack
  mantém o container dela.
- **Sem exceções novas.** Nenhuma classe de exceção nem branch de exceção é criado; o critério
  de aceite de exceções da constitution não se aplica.
