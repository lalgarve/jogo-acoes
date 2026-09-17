# Plan: SecurityConfig modular por módulo

Traduz `spec.md` em decisões técnicas. Valida contra `memory/constitution.md`.

## Contexto técnico

Spring Security 6 (`authorizeHttpRequests` DSL, sem `WebSecurityConfigurerAdapter`).
`SecurityConfig` hoje vive em `login/` (decisão da spec 05-002, ver
`docs/context/iteracao-5.md`) e concentra os matchers dos dois módulos que atualmente têm
endpoint HTTP: `login` (`LoginController` — `/login-requests`, `/login-links/**`) e
`competition` (`CompetitionsController`/`EntryRequestsController`/`PlayersController` — tudo sob
`/competitions*`). Precedente direto já existente no projeto: `LinkHandler` (interface em
`link/`, implementada por `login`/`competition` sem que `link` conheça seus consumidores) +
`LinkRouter` (agrega via `List<LinkHandler>` injetado pelo Spring, spec 05-003) — mesmo padrão de
inversão de dependência que esta spec aplica a regras de autorização em vez de consumo de link.

## Decisões de arquitetura

| Pergunta | Decisão | Status | Raciocínio |
|---|---|---|---|
| Onde vive a interface `SecurityConfigContributor`? | `login/` — mesmo módulo que já é dono de `SecurityConfig`/`SecurityFilterChain` (decisão da spec 05-002). | resolvida | Mesmo precedente de `LinkHandler` viver em `link/` (o módulo dono da infra), não em `common/` — `common/` é reservado a testsupport/infra sem domínio próprio, e segurança tem dono claro (`login`). |
| Assinatura do método da interface | `void contribute(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry)` — recebe o registry real do DSL do Spring Security, não uma abstração própria. | resolvida | Menor camada possível: o contributor usa a mesma API fluente (`.requestMatchers(...).permitAll()`/`.hasRole(...)`) que `SecurityConfig` já usa hoje, sem reinventar um modelo de regra próprio. |
| Como o `SecurityFilterChain` agrega os contributors | `SecurityConfig.securityFilterChain` passa a receber `List<SecurityConfigContributor> contributors` como parâmetro do bean; dentro do lambda de `authorizeHttpRequests`, itera a lista chamando `contribute(registry)` em cada um, e só depois de todos chama `.anyRequest().authenticated()`. | resolvida | Mesmo mecanismo de `LinkRouter(List<LinkHandler> handlers)` — Spring injeta automaticamente todos os beans do tipo; nenhum catálogo central de "quais módulos existem" para manter. |
| Ordem de aplicação dos contributors importa? | Não é garantida nem exigida — cada contributor só registra matchers dentro do prefixo de recurso do próprio módulo (`/login-*`, `/competitions*`), então não há sobreposição entre módulos diferentes. `anyRequest().authenticated()` continua sempre por último, fora de qualquer contributor. | resolvida | Evita mecanismo de `@Order`/prioridade que a spec não precisa agora; se dois módulos algum dia precisarem de matchers que se sobrepõem, isso é sinal de fronteira de módulo mal desenhada, não um caso para resolver com ordenação. |
| Contributors novos por módulo | `LoginSecurityConfigContributor` (`login/`) — move `/login-requests`, `/login-links/**` (`permitAll`). `CompetitionSecurityConfigContributor` (`competition/`) — move `/competitions/public`, `/competitions/*/entry-requests` (`permitAll`) e `POST /competitions`, `/competitions/*/invite-emails`, `/competitions/*/players`, `/competitions/*/players/**` (`hasRole(ADMINISTRATOR)`). Nenhuma rota muda de módulo dono — muda só onde o código que a registra mora. | resolvida | Mapeamento 1:1 com os controllers já existentes por módulo (`LoginController` em `login/`; os três controllers de competição em `competition/`). |
| Módulos sem endpoint HTTP hoje (`email`, `log`, `captcha`, `common`) | Não ganham contributor nesta spec. | resolvida | Nenhuma rota para registrar — um contributor vazio não teria propósito; ver "Fora de escopo" em `spec.md`. |
| O que fica central em `SecurityConfig`, fora dos contributors | `csrf().disable()`, `formLogin().disable()`, `httpBasic().disable()`, o bean `SecurityContextRepository`, `exceptionHandling` (401/403 em JSON), e o `.anyRequest().authenticated()` final. | resolvida | Nenhum desses é regra de rota de um módulo específico — é configuração transversal do mecanismo de autenticação/sessão do sistema inteiro. |
| ArchUnit para checar "todo módulo com `@RestController` tem contributor"? | Sim — nova dependência `com.tngtech.archunit:archunit-junit5` (escopo `test`, versão mais recente estável no Maven Central no momento da implementação). Um teste `ArchitectureTest` usa `@AnalyzeClasses(packages = "dev.leilaalgarve.jogoacoes")` com uma regra customizada: toda classe anotada `@RestController` deve estar num pacote-base que também contenha uma classe implementando `SecurityConfigContributor`. Roda como teste normal (`mvn test`), falha o build inteiro se violada — não é um teste isolado que alguém possa esquecer de olhar. | resolvida | Mitiga diretamente o risco "nenhuma checagem em tempo de compilação contra rota órfã" já sinalizado nesta spec. ArchUnit é o padrão de mercado pra regra estrutural desse tipo (reflection sobre bytecode/classes/pacotes) — não faz sentido inventar mecanismo próprio pra isso. |
| ArchUnit também detecta contributors com matchers sobrepostos entre módulos? | Não — fora do alcance da ferramenta: ArchUnit opera sobre estrutura de classes/pacotes/dependências, não sobre os valores de `String` passados em runtime pro DSL do Spring Security (`requestMatchers("/algum/caminho")`). **Revisão abaixo**: essa parte da análise era certa (ArchUnit não é a ferramenta), mas a conclusão de que não valia a pena checar automaticamente estava incompleta — existe uma ferramenta melhor pra esse caso específico. | resolvida | — |
| Como detectar sobreposição de rota entre módulos de forma automática, então? | Um teste dedicado usando `RequestMappingHandlerMapping` — o bean real do Spring MVC que resolve as rotas de verdade a partir das anotações dos controllers (inclusive herdadas das interfaces geradas pelo OpenAPI-generator, ex. `CompetitionsApi`). Agrupa os `RequestMappingInfo` por módulo (pacote) e por primeiro segmento de path, e falha se (a) algum padrão for exatamente `/`, ou (b) o mesmo primeiro segmento aparecer em módulos diferentes. | resolvida | Mais preciso que reconstruir/parsear os literais do `SecurityConfigContributor`: usa a metadata que o próprio Spring já computou, sem duplicar lógica de resolução de path. Também mais barato que subir a suíte Cucumber inteira só pra essa checagem estrutural — roda como teste unitário comum. |
| Onde vive o teste de `RequestMappingHandlerMapping` | `common/RouteOwnershipTest.java` — mesmo módulo do `ArchitectureTest`. `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)`, autowire de `RequestMappingHandlerMapping`, sem precisar subir servidor de verdade (porta aleatória). | resolvida | Mesmo critério de `common/` já usado pro `ArchitectureTest` — infra de verificação sem domínio próprio. Contexto completo (não um `@WebMvcTest` fatiado) porque os controllers dependem de serviços/repositórios reais pra instanciar, mesmo padrão que o resto da suíte já usa. |
| Como o teste define "módulo" e "primeiro segmento de rota" | Módulo = segmento de pacote imediatamente após o pacote-base `dev.leilaalgarve.jogoacoes` (`handlerMethod.getBeanType().getPackageName()`) — mesmo critério do `ArchitectureTest`; handler methods fora do pacote-base (ex. `BasicErrorController` do Spring Boot, mapeando `/error`) são ignorados. Primeiro segmento = trecho até a próxima `/` depois de remover a barra inicial (`/competitions/public` → `competitions`; `/login-requests` → `login-requests`; `/login-links/**` → `login-links`). | resolvida | Consistência entre os dois testes de arquitetura; `/error` não é rota de nenhum módulo de domínio, não faz sentido incluir na checagem. Regra de segmento simples o bastante pra cobrir as rotas reais de hoje sem generalizar além do necessário. |
| Onde vive o teste ArchUnit | `common/` (módulo já reservado a infra/testsupport sem domínio próprio — mesmo critério que já aloca `ScenarioWorld`/fixtures lá, spec 05-002), classe `ArchitectureTest`. Nome genérico, não `SecurityConfigContributorArchitectureTest`, para comportar outras regras estruturais do projeto no futuro sem precisar de uma classe por regra. | resolvida | Um teste de arquitetura é infra sem dono de domínio, mesmo raciocínio que já levou `common/` a existir. |

Decisões marcadas "em aberto" viram commit `decision:` quando resolvidas (ver
`memory/constitution.md`), atualizando esta tabela no mesmo commit.

## Estrutura de módulos/pacotes

- `login/SecurityConfigContributor.java` (novo) — interface, um método `contribute(registry)`.
- `login/SecurityConfig.java` (modificado) — `securityFilterChain` passa a injetar
  `List<SecurityConfigContributor>` e iterar; perde os `requestMatchers` de módulo que hoje
  estão inline; mantém tudo o mais (cross-cutting) como está.
- `login/LoginSecurityConfigContributor.java` (novo) — `@Component`, registra
  `/login-requests`, `/login-links/**` como `permitAll`.
- `competition/CompetitionSecurityConfigContributor.java` (novo) — `@Component`, registra os
  matchers de `/competitions*` já existentes (públicos e administrativos), com o mesmo
  agrupamento de hoje.
- `common/ArchitectureTest.java` (novo, teste) — regra ArchUnit "todo `@RestController` tem
  `SecurityConfigContributor` no mesmo pacote-base".
- `common/RouteOwnershipTest.java` (novo, teste) — via `RequestMappingHandlerMapping`: nenhuma
  rota mapeia `/`; cada primeiro segmento de path pertence a um único módulo.
- `app/pom.xml` (modificado) — dependência nova `com.tngtech.archunit:archunit-junit5`, escopo
  `test`.

## Riscos e trade-offs

- **Checagem de rota "órfã" mitigada, não eliminada** — o teste ArchUnit garante que todo módulo
  com `@RestController` tem *algum* `SecurityConfigContributor`, mas não que esse contributor
  cobre exatamente as rotas atuais do módulo (ex. o módulo `competition` ganha um endpoint novo e
  o contributor existente não é atualizado — o ArchUnit não detecta isso, só que a classe
  `CompetitionSecurityConfigContributor` continua existindo). Falha segura de qualquer forma: a
  rota não coberta cai no `anyRequest().authenticated()` central, nunca fica aberta por
  acidente.
- **Sobreposição de rota entre módulos, agora verificada automaticamente** — o teste
  `RouteOwnershipTest` (`RequestMappingHandlerMapping`) garante que dois módulos não mapeiam sob
  o mesmo primeiro segmento de path, e que nenhum mapeia o path raiz. O que continua sem
  checagem automática é mais fino: um `SecurityConfigContributor` cujo matcher (`String`
  literal) não corresponde exatamente às rotas reais do próprio módulo (ex. erro de digitação) —
  esse caso residual já é pego pela suíte Cucumber comportamental (a rota erraria o
  comportamento de acesso e algum cenário existente falharia), não por um teste de arquitetura
  dedicado.
- **Refactor puramente estrutural, sem `.feature` novo** — a suíte Cucumber existente é o único
  critério de aceite; risco baixo (mesma regra, só reorganizada), mas exige rodar a suíte
  completa antes/depois para confirmar bit-a-bit que nada mudou (mesma contagem, mesmos
  cenários, nenhuma alteração de texto Gherkin).
- **Não resolve a divergência `x-roles`/`SecurityConfig` sinalizada na spec 05-004** — continua
  em aberto; esta reorganização só deixa o terreno mais preparado para um checador futuro (um
  teste que percorra os contributors e compare com `x-roles` do `docs/openapi.yaml`), que não é
  implementado aqui.
