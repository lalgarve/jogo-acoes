# Plan: Remover o módulo `blackbox-proxy/`

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Levantamento no `master` em 2026-10-08 (`d8429d7`), com
`grep -rn "blackbox-proxy" --exclude-dir=.git .`:

| Arquivo | O quê | Ação |
|---|---|---|
| `blackbox-proxy/` | módulo inteiro: `pom.xml`, `BlackboxProxyApplication`, `ReverseProxyController`, `DeviceHeaderStore`, `application.yml`, `ReverseProxyIntegrationTest` | apagar |
| `scripts/blackbox-proxy.sh` | sobe uma instância do proxy | apagar |
| `pom.xml` (raiz) | `<module>blackbox-proxy</module>`; `<description>` lista os quatro módulos | tirar o módulo; reescrever a descrição para três |
| `Dockerfile` | `COPY blackbox-proxy/pom.xml`; comentário "lists email-lambda, blackbox-proxy and email-service" | tirar a linha e o nome do comentário |
| `email-service/Dockerfile` | `COPY blackbox-proxy/pom.xml`; comentário "lists app, email-lambda and blackbox-proxy" | idem |
| `.gitattributes` | comentário "(LocalStack init, blackbox-proxy)" | deixar só "LocalStack init" |
| `README.md` | tabela "Módulos"; `mvn -pl blackbox-proxy -am verify`; bloco "Alternativa disponível…" com o script e o `curl` para `localhost:8090` | tirar; "builda os quatro" vira "os três"; "nenhum dos três" já estava desatualizado e continua certo com três módulos |
| `CLAUDE.md` | linha da tabela de módulos; "(padrão `blackbox-proxy/`)" | tirar a linha; trocar o exemplo (ver decisão abaixo) |
| `memory/constitution.md` | baseline Java (`app`, `email-service`, `email-lambda` e `blackbox-proxy`); regra de código de teste: "mesmo padrão já usado em `blackbox-proxy/`" e "expor uma API só pra receber configuração (como `blackbox-proxy/` faz)" | tirar da lista; trocar os dois exemplos |
| `specs/05-020-proxy-client-hints-swagger/spec.md` | status "implementado" | status "removido" + seção no fim |
| `specs/05-033-politica-baseline-java-lts/spec.md` | requisito lista `blackbox-proxy` na baseline | nota apontando para esta spec |

Fatos que pesam nas decisões:

- O CI não builda o módulo (`.github/workflows/ci.yml`: só `-pl app`, `-pl email-service` e
  `-pl email-lambda`). A remoção não muda nenhum job.
- Nenhum outro módulo depende do proxy: `app`, `email-service`, `email-lambda`,
  `docker-compose*.yml` e `blackbox-tests/` não citam `blackbox-proxy` nem a porta `8090`.
- O `.gitattributes` continua precisando da regra `*.sh text eol=lf`: os scripts de init do
  LocalStack e os de `scripts/` rodam em Linux/WSL.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Que exemplo a constitution e o `CLAUDE.md` passam a usar na regra "Código de teste/dev nunca dentro da aplicação"? | `blackbox-tests/` (suíte Python, spec 05-015), fora do reator Maven e de qualquer artefato de produção | proposta | É código de teste vivo, usado hoje, e fisicamente separado dos módulos de produção, que é o ponto da regra. Os outros candidatos não servem: o seeder do administrador virou funcionalidade de produto (05-026), e o `email-lambda` corrigido mostra o que foi removido, não onde o código de teste mora. |
| O que fazer com o trecho "expor uma API só pra receber configuração (como `blackbox-proxy/` faz)" | Manter a ideia e tirar o parêntese | proposta | O custo citado (duplicar código, expor API de configuração) continua sendo um exemplo válido do que a regra aceita pagar; só o caso concreto deixou de existir. |
| Spec 05-020: apagar ou manter? | Manter, com status "removido — ver 'Por que este módulo foi removido' no final do arquivo" | resolvida (pedido na Issue #123) | Mesmo padrão das specs 05-022 e 05-024: o raciocínio da época fica registrado. `plan.md` e `tasks.md` dela ficam sem mudança. |
| Specs 05-025 e 05-033 | 05-025 fica como está. 05-033 ganha uma nota no `spec.md`; `plan.md` e `tasks.md` ficam | proposta | A 05-025 só cita o proxy como fato verificado na época. A 05-033 define a baseline vigente, e o requisito dela lista os módulos atuais; uma nota evita que leiam o proxy como parte da baseline sem reescrever uma spec implementada. |
| Uma PR ou mais? | Uma PR só, com código e documentação juntos | proposta | A remoção do módulo e a das menções são a mesma mudança: separar deixaria o repositório num estado em que a documentação aponta para algo que não existe, ou o contrário. Esta PR de spec não mexe em código. |

## Estrutura de módulos/pacotes

Depois da remoção, o reator tem três módulos (`app`, `email-lambda`, `email-service`).
`blackbox-tests/` continua fora do reator. Nenhum pacote Java muda.

## Verificação

- `grep -rn "blackbox-proxy" --exclude-dir=.git .` só acha ocorrências nos registros
  históricos listados em "Fora de escopo" da `spec.md`, na spec 05-020 e nesta spec.
- `mvn -B verify` na raiz, com a infraestrutura do Compose de pé, builda os três módulos.
- `docker compose build app email-service` builda as duas imagens.
- O CI da PR fica verde sem mudança no `ci.yml`.

## Riscos e trade-offs

- **Perde-se a conveniência de clicar "Execute" no Swagger UI com headers de dispositivo.** Foi
  decidido que o `curl` basta; se a necessidade voltar, o código está no histórico do git
  (`dbbd678`) e a spec 05-020 continua descrevendo o desenho.
- **Algum checkout local com `mvn -pl blackbox-proxy`** em script próprio quebra. Não há uso
  conhecido no repositório nem no CI.
