# Spec: Logging estruturado via aspectos (AOP), suprimido em produção

**Status:** rascunho
**Issue:** [#61](https://github.com/lalgarve/jogo-acoes/issues/61)
**Iteração:** iteration-5

## Resumo

Loga em nível DEBUG, via aspectos (Spring AOP), toda entrada/saída de controller,
leitura/escrita no banco (repositórios Spring Data) e mensagem enviada pra fila (SQS) — sem
espalhar chamada de log manual pelo código de negócio. Listas/mapas longos aparecem truncados no
log (só o primeiro item + quantidade restante). Em produção, esses logs não aparecem.

## Motivação

Hoje não existe nenhum log de execução sistemático — só o que cada classe decide logar
manualmente (pouco, e inconsistente). Pra depurar/demonstrar o sistema (inclusive pro "caderno
de testes" da disciplina, spec 05-012, que referencia o que aparece no log pra confirmar que uma
operação rodou), falta um rastro previsível de "o que entrou, o que saiu, o que foi lido/escrito
no banco, o que foi mandado pra fila". Fazer isso com aspectos, em vez de log manual espalhado
pelos controllers/services/repositories, mantém esse código de negócio livre de uma
responsabilidade transversal.

## Cenários (comportamento esperado)

Não aplicável — infraestrutura transversal, sem `.feature` novo (mesmo padrão das specs
05-006/05-007). Critério de aceite: testes dedicados que capturam a saída de log
(`OutputCaptureExtension`) e confirmam o formato esperado, incluindo truncamento.

## Requisitos funcionais

- Aspecto 1 — **controllers**: loga, em DEBUG, a entrada (nome do método + argumentos) e a saída
  (retorno, ou exceção) de todo método de todo `@RestController`.
- Aspecto 2 — **repositórios**: loga, em DEBUG, toda chamada a método de repositório Spring Data
  (`JpaRepository`), com argumentos e retorno — cobre leitura e escrita, já que ambas passam por
  métodos de repositório.
- Aspecto 3 — **filas**: loga, em DEBUG, toda mensagem enviada pela fila (SQS) — o envio feito
  por `SqsEmailSender`.
- **Truncamento de coleções**: quando um argumento ou retorno logado é uma `List`/`Set`/`Map`
  com mais de um elemento, o log mostra só o primeiro item, seguido de `+[N]` (N = quantidade de
  itens restantes) — ex. uma lista de 10 e-mails aparece como `joao@exemplo.com+[9]`. Coleções
  com 0 ou 1 item aparecem por completo, sem sufixo.
- **Configuração de nível por perfil**: o padrão (`root`) continua INFO (comportamento já
  existente do Spring Boot); os pacotes da aplicação (`dev.leilaalgarve.jogoacoes`) ganham
  DEBUG explícito nos perfis de desenvolvimento — é esse nível elevado que faz os três aspectos
  aparecerem no console. Nenhuma condicional de ambiente dentro do código do aspecto.
- **Suprimido em produção**: no perfil `production`, os pacotes da aplicação voltam pro `root`
  (INFO) — como os três aspectos logam em DEBUG, deixam de aparecer, sem precisar de um nível
  dedicado (`OFF`) nem de lógica no código.

## Requisitos não-funcionais

- Nenhuma mudança de comportamento funcional do sistema — só logging.
- Truncamento se aplica ao nível mais externo do argumento/retorno (uma lista/mapa direto) — não
  percorre recursivamente estruturas aninhadas dentro do primeiro item mantido.

## Fora de escopo

- Log estruturado em JSON / integração com ferramenta de agregação de log — só console, texto
  simples. A autora planeja VictoriaLogs na Etapa 3 da disciplina (ver
  `docs/context/iteracao-5.md`), na mesma lógica de centralização do Spring Cloud Config Server
  (configuração vs. log) — não é tratado aqui; quando chegar a hora, revisitar se o formato de
  log desta spec precisa mudar pra ser consumido por ele (ex. coletado a partir do stdout do
  container, sem mudança de formato, ou reformatado).
- Mascarar dados sensíveis (senha, token) nos argumentos logados — não existe hoje nenhum dado
  desse tipo trafegando em texto puro nos métodos alvo (a autenticação é por link mágico, não
  senha), então não é um requisito desta spec; revisar se isso mudar no futuro.
- Logar o corpo de mensagens SQS recebidas (consumidor, `email-lambda`) — só o lado de envio
  (`app`), que é o escopo original pedido.

## Decisões em aberto

Nenhuma — decisões técnicas (pointcuts exatos, onde vivem os aspectos, mecanismo de supressão)
resolvidas em conversa antes de escrever este documento, ver `plan.md`.
