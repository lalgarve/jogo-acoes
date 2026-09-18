# Plan: Consistência entre contrato OpenAPI e implementação

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

`docs/openapi.yaml` é a fonte de todo o código gerado em
`app/target/generated-sources/openapi` (interfaces `*Api.java` em
`dev.leilaalgarve.jogoacoes.api`, via `openapi-generator-maven-plugin`, `inputSpec` =
`${project.basedir}/../docs/openapi.yaml`). Toda rota do projeto hoje vem de um controller
implementando uma dessas interfaces geradas — não existe `@RequestMapping` escrito à mão fora
desse mecanismo. `x-roles` é uma extensão vendor adicionada na spec 05-004 em toda operação do
arquivo — hoje é só documentação (Javadoc/`@Operation.description` no código gerado), sem
nenhuma checagem contra o `SecurityFilterChain`/`SecurityConfigContributor` (05-006) reais.
SnakeYAML já está no classpath (`org.yaml:snakeyaml:2.6`, compile, transitivo via Spring Boot),
confirmado via `mvn dependency:tree` nesta sessão — não precisa de dependência nova pra ler o
YAML.

Levantamento desta sessão: `docs/openapi.yaml` tem hoje 15 operações, usando só três valores de
`x-roles`: `[]` (5 operações), `[ADMINISTRATOR]` (8 operações), `[PLAYER, ADMINISTRATOR]` (2
operações) — nenhuma usa `[PLAYER]` sozinho. Esses três valores mapeiam 1:1 pras três únicas
formas de regra que o `SecurityConfig`/contributors da 05-006 conseguem expressar hoje:
`permitAll()`, `hasRole(ADMINISTRATOR)`, e "cai no catch-all `anyRequest().authenticated()`"
(usado pra "qualquer papel autenticado", já que não existe hoje um `hasRole(PLAYER)` explícito
em lugar nenhum).

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Como comparar rotas do contrato com rotas reais | Teste lê `docs/openapi.yaml` com SnakeYAML, extrai o conjunto `(método, path)` de cada operação em `paths`; compara contra o conjunto `(método, path)` de `RequestMappingHandlerMapping.getHandlerMethods()`, filtrado a `handlerMethod.getBeanType().getPackageName()` começando com `dev.leilaalgarve.jogoacoes` (mesmo filtro do `RouteOwnershipTest`, 05-006). Sintaxe de path variável é idêntica nos dois lados (`{competitionId}`) porque o gerador emite o path do OpenAPI direto pro `@RequestMapping` — comparação direta de string, sem tradução. | resolvida | Mais simples e direto que correlacionar por `operationId` (exigiria ler a anotação `@Operation` de cada `HandlerMethod` via merge de anotação de interface) — o par `(método, path)` já identifica a operação de forma única nos dois lados. |
| As duas direções da comparação importam igual? | Não — "rota implementada sem operação no contrato" é o caso real de risco (alguém adiciona `@GetMapping` direto no controller, fora do gerador, furando API-first). "Operação no contrato sem rota implementada" é estruturalmente quase impossível hoje (o código vem do gerador, que roda no build a partir do próprio arquivo) — mantida como asserção extra de qualquer forma (é barata), mas documentada como defesa em profundidade, não o risco principal. | resolvida | Transparência sobre qual metade do teste carrega o valor real, evitando a leitura de "as duas metades são igualmente prováveis de pegar alguma coisa". |
| Como comparar `x-roles` com a autorização real | `WebInvocationPrivilegeEvaluator` (interface pública do Spring Security — "essa autenticação teria acesso a essa URL?"), testado pra cada operação com três autenticações representativas: anônimo (sem `Authentication`), `ROLE_PLAYER`, `ROLE_ADMINISTRATOR`, contra o path com placeholders substituídos por um valor dummy improvável de colidir com literal real (`999999`). Resultado esperado por valor de `x-roles`: `[]` → permitido nas três; `[ADMINISTRATOR]` → permitido só em `ROLE_ADMINISTRATOR`; `[PLAYER, ADMINISTRATOR]` → permitido em `ROLE_PLAYER` e `ROLE_ADMINISTRATOR`, negado em anônimo. | resolvida | `WebInvocationPrivilegeEvaluator` é a API pública do Spring Security feita exatamente pra essa pergunta, sem precisar rodar a requisição de verdade através do `DispatcherServlet`/controller (que exigiria corpo/validação irrelevantes pra essa checagem) nem reimplementar a lógica de resolução de regra. |
| E se `x-roles` usar uma combinação que o `SecurityConfig` não consegue expressar hoje (ex. `[PLAYER]` sozinho)? | Sem tratamento especial — o teste falha normalmente, apontando a divergência. Não ocorre em nenhuma operação hoje (levantamento acima), mas se ocorrer no futuro é exatamente o tipo de coisa que esta spec existe pra pegar: `x-roles` prometeu uma regra que nenhum `SecurityConfigContributor` de módulo implementa. | resolvida | Coerente com o objetivo da spec — não faz sentido criar exceção pro próprio caso que o teste deveria detectar. |
| Onde vivem os testes | `common/OpenApiRoutesConsistencyTest.java` (rotas) e `common/OpenApiRolesConsistencyTest.java` (papéis) — dois testes separados, não um só, mesmo critério de separação de responsabilidade da 05-006 (`ArchitectureTest` vs `RouteOwnershipTest`). Ambos `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)`, mesmo padrão dos testes de arquitetura da 05-006. | resolvida | Duas perguntas diferentes ("a rota existe?" vs "o papel bate?") — separar deixa a falha mais legível (o nome do teste já diz qual das duas quebrou) e cada um pode evoluir independente. |
| Caminho do `docs/openapi.yaml` a partir do teste | `Paths.get("../docs/openapi.yaml")`, relativo ao working directory do Maven Surefire (`app/`) — mesmo caminho relativo já usado pelo plugin do gerador (`${project.basedir}/../docs/openapi.yaml`), não um caminho novo inventado. | resolvida | Consistência com a única outra referência a esse arquivo que já existe no build (`app/pom.xml`). |

## Estrutura de módulos/pacotes

- `common/OpenApiRoutesConsistencyTest.java` (novo) — lê `docs/openapi.yaml`, compara
  `(método, path)` contra `RequestMappingHandlerMapping`.
- `common/OpenApiRolesConsistencyTest.java` (novo) — lê `docs/openapi.yaml`, compara `x-roles`
  de cada operação contra `WebInvocationPrivilegeEvaluator` para três autenticações.
- Nenhum arquivo de produção muda — só os dois testes novos, ambos em `common/` (mesmo critério
  de "infra de verificação sem domínio próprio" já usado pros dois testes da 05-006).

## Riscos e trade-offs

- **Faz mais sentido depois da spec 05-006, sem ser bloqueio técnico rígido** —
  `SecurityConfigContributor` (autorização por módulo) é o que torna a autorização real
  legível/organizada de forma centralizada; tecnicamente o teste de papéis funcionaria contra o
  `SecurityConfig` monolítico de hoje também (`WebInvocationPrivilegeEvaluator` não sabe nem se
  importa como a regra foi organizada em código), mas faz mais sentido sequenciar depois da
  05-006 pra não competir por revisão na mesma janela.
- ~~**`WebInvocationPrivilegeEvaluator` pode não estar auto-configurado como bean**~~ — não se
  confirmou: `WebSecurityConfiguration` (importada por `@EnableWebSecurity`, já presente em
  `SecurityConfig`) expõe um bean `privilegeEvaluator()` automaticamente. `@Autowired
  WebInvocationPrivilegeEvaluator` funcionou direto, sem nenhuma construção manual. (Achado
  extra: a implementação real em Spring Security 7.x é
  `AuthorizationManagerWebInvocationPrivilegeEvaluator`, não `DefaultWebInvocationPrivilegeEvaluator`
  como o "Plano B" original desta linha supunha — API baseada em `AuthorizationManager`, não na
  antiga `FilterSecurityInterceptor`; irrelevante na prática já que o bean vem pronto.)
- **Falso positivo se um path tiver múltiplos segmentos-variável em posições que colidem com
  literais de outra rota** (ex. testar `/competitions/{competitionId}` com um valor que por
  acaso bate num path literal de outra rota) — mitigação: usar um valor dummy improvável
  (`999999`), e revisar manualmente a lista de paths ao implementar, já que são só 15 operações.
- **Teste de rotas não pega drift de schema de request/response**, só path+método — fora de
  escopo desta spec (ver `spec.md`), risco aceito.
