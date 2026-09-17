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

## Riscos e trade-offs

- **Nenhuma checagem em tempo de compilação contra rota "órfã"** — se um módulo adicionar um
  endpoint novo e esquecer de estender seu contributor (ou criar um contributor para um módulo
  novo), a rota cai no `anyRequest().authenticated()` central por padrão (exige sessão, nunca
  fica aberta por acidente) — falha segura, mas silenciosa; não há teste dedicado nesta spec que
  force "todo `@RestController` tem contributor correspondente".
- **Contributors com matchers sobrepostos entre módulos diferentes não são detectados** — a
  invariante ("cada módulo só mexe no próprio prefixo de rota") é uma convenção, não uma
  checagem automática; violação só apareceria em produção como uma regra silenciosamente
  ignorada pelo "primeiro match vence" do Spring Security. Aceitável pelo tamanho atual do
  projeto (2 módulos com HTTP hoje); revisar se crescer para módulos com namespaces de rota
  menos óbvios.
- **Refactor puramente estrutural, sem `.feature` novo** — a suíte Cucumber existente é o único
  critério de aceite; risco baixo (mesma regra, só reorganizada), mas exige rodar a suíte
  completa antes/depois para confirmar bit-a-bit que nada mudou (mesma contagem, mesmos
  cenários, nenhuma alteração de texto Gherkin).
- **Não resolve a divergência `x-roles`/`SecurityConfig` sinalizada na spec 05-004** — continua
  em aberto; esta reorganização só deixa o terreno mais preparado para um checador futuro (um
  teste que percorra os contributors e compare com `x-roles` do `docs/openapi.yaml`), que não é
  implementado aqui.
