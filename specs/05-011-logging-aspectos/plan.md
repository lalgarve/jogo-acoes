# Plan: Logging estruturado via aspectos (AOP), suprimido em produção

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Nenhum log sistemático existe hoje. `spring-boot-starter-aop` (Spring AOP + AspectJ runtime,
versão gerenciada pelo `spring-boot-starter-parent`) ainda não é dependência do projeto. Perfis
existentes: `sandbox` (default), `docker`, `staging`, `production` — nenhum define
`logging.level` hoje (raiz fica no padrão do Spring Boot, INFO).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde vivem os aspectos | `common/logging/` (pacote novo dentro de `common/`) — três classes `@Aspect`: `ControllerLoggingAspect`, `RepositoryLoggingAspect`, `QueueLoggingAspect`. | resolvida | Infra transversal sem domínio próprio, mesmo critério que já levou `common/` a existir (`ArchitectureTest`/`RouteOwnershipTest`, specs 05-006/007) — aqui em código principal, não teste, mas o raciocínio de "sem dono de domínio" é o mesmo. |
| Pointcut de controllers | `@within(org.springframework.web.bind.annotation.RestController)` — cobre todo método público de toda classe anotada, sem listar pacote por pacote. | resolvida | Não precisa ser atualizado quando um módulo novo ganha um controller — a anotação já identifica o alvo. |
| Pointcut de repositórios | `execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))` combinado com um filtro de pacote (`within(dev.leilaalgarve.jogoacoes..*)`) pra não pegar infraestrutura do próprio Spring Data. | resolvida | Cobre todo repositório do projeto (leitura e escrita passam pelos mesmos métodos: `save`, `findBy*`, `delete*`, etc.) sem precisar nomear cada repositório. |
| Pointcut de fila | `execution(* dev.leilaalgarve.jogoacoes.email.SqsEmailSender.send(..))` — classe específica, não a interface `EmailSender` (que também tem `StubEmailSender`, que não é fila). | resolvida | O pedido é sobre "mensagens enviadas pelas filas" — `StubEmailSender` não manda pra fila nenhuma, só grava em `sent_email`; incluir ele mistura dois conceitos diferentes no mesmo aspecto. |
| Como truncar listas/mapas no log | Função utilitária `LogFormatter.summarize(Object value)`: se `value instanceof Collection` e `size() > 1`, retorna `first + "+[" + (size-1) + "]"`; se `value instanceof Map` e `size() > 1`, mesma lógica sobre o primeiro `entry` formatado como `chave=valor`; caso contrário (0/1 item, ou não é coleção/mapa), serializa o valor inteiro via Jackson (`ObjectMapper` já no classpath, `tools.jackson`). Aplicada a cada argumento/retorno individualmente, não recursivamente dentro deles. | resolvida | Bate exatamente com o exemplo dado (`joao@exemplo.com+[9]`); escopo raso evita ter que lidar com estruturas arbitrariamente aninhadas. |
| Nível dos três aspectos | **DEBUG**, não INFO (revisão desta decisão — a versão anterior deste `plan.md` usava INFO). `application.yml` (raiz, todos os perfis) ganha `logging.level.dev.leilaalgarve.jogoacoes: DEBUG` — não só o pacote dos aspectos, o pacote-base da aplicação inteira. O `root` (framework/bibliotecas) continua no padrão do Spring Boot, INFO, sem override. | resolvida | Decisão da autora: DEBUG é o nível certo pra log de execução detalhado (entrada/saída, leitura/escrita) — INFO fica reservado pra eventos de negócio relevantes por si só, se algum dia existirem. Aplicar no pacote-base (não só `common.logging`) estabelece a mesma convenção pra qualquer log futuro da aplicação, não só os três aspectos desta spec. |
| Como suprimir em produção | `application-production.yml` ganha `logging.level.dev.leilaalgarve.jogoacoes: INFO` — sobrescreve o `DEBUG` de `application.yml` de volta pro padrão do `root`. Como os três aspectos logam em DEBUG, deixam de aparecer (INFO > DEBUG na ordem de severidade) sem precisar de um nível dedicado (`OFF`) nem checagem de perfil dentro do código do aspecto. | resolvida | Usa o mecanismo de configuração que o Spring Boot já dá de graça por perfil (arquivo sobrepõe arquivo) — não inventa um `@ConditionalOnProperty`/checagem de `Environment` dentro de cada aspecto. |
| Guarda de performance | Cada aspecto checa `logger.isDebugEnabled()` antes de montar a mensagem (serializar argumentos é trabalho evitável quando o nível está desligado). | resolvida | Barato de fazer, evita gastar CPU serializando em produção só pra descartar a `String` em seguida. |

## Estrutura de módulos/pacotes

- `app/pom.xml` (modificado) — `spring-boot-starter-aop`.
- `common/logging/ControllerLoggingAspect.java` (novo).
- `common/logging/RepositoryLoggingAspect.java` (novo).
- `common/logging/QueueLoggingAspect.java` (novo).
- `common/logging/LogFormatter.java` (novo) — função utilitária de truncamento/serialização.
- `app/src/main/resources/application.yml` (modificado) — `logging.level.dev.leilaalgarve.jogoacoes: DEBUG`.
- `app/src/main/resources/application-production.yml` (modificado) — mesma chave de volta pra
  `INFO`.
- Testes novos em `common/logging/` (`LogFormatterTest`, e um teste de integração leve por
  aspecto usando `OutputCaptureExtension`).

## Riscos e trade-offs

- **Volume de log em ambientes não-produção** — todo request de controller e toda chamada de
  repositório gera uma linha (ou duas, entrada/saída); aceitável pro objetivo
  (depuração/demonstração), não pensado pra alto volume/produção (onde já fica suprimido).
- **Serialização de argumentos complexos pode falhar ou ficar grande** — objetos com referência
  cíclica (ex. entidades JPA com relação bidirecional) podem estourar o `ObjectMapper` padrão;
  mitigação: capturar exceção de serialização dentro de `LogFormatter` e cair num `toString()`
  simples nesse caso, em vez de quebrar a chamada real por causa do log.
- **Repositórios chamados de dentro de um método `@Transactional` self-invocado no mesmo bean**
  não seriam interceptados (limitação conhecida do Spring AOP baseado em proxy) — não é o padrão
  de uso hoje (repositórios são sempre injetados/chamados de fora), risco baixo.
- **VictoriaLogs (planejado pra Etapa 3, `docs/context/iteracao-5.md`) ainda não tem desenho
  nesta spec** — o log continua só console/texto simples aqui; quando a integração acontecer,
  revisitar se o formato precisa mudar (idealmente não: VictoriaLogs, como Loki, costuma coletar
  direto do stdout do container, sem exigir JSON estruturado).
