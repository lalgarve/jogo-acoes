# Spec: Camadas dentro dos módulos

**Status:** rascunho (decisão em aberto abaixo)
**Issue:** —
**Iteração:** iteration-5

## Resumo

Um teste de arquitetura em cada serviço (`app` e `email-service`) garante que as classes seguem
três camadas, sem atalho: **controller → service → repository**. Controller recebe a requisição
e chama service; service tem a regra e chama repository; repository só acessa o banco. Nenhuma
camada de baixo depende de uma de cima.

## Motivação

- **Pedido da Leila (2026-10-26):** regra de dependência entre camadas, escolhida entre as
  sugestões da spec [05-061](../05-061-sem-ciclos-entre-modulos/spec.md).
- A 05-061 cuida das dependências **entre** módulos; esta cuida das dependências **dentro** de
  cada módulo. Hoje nada impede um controller de chamar um repository direto, nem um service de
  depender de um controller.
- **Situação atual** (conferida em 2026-10-26 pelo código-fonte de `origin/development`, sem
  rodar o teste): nenhum controller usa repository, nenhuma classe de produção usa controller e
  nenhum repository usa service. Cinco classes do `email-service` (`disposable`) usam
  `JdbcTemplate` direto, fora de repository (ver "Decisões em aberto").

## As camadas

Os módulos não têm subpacotes por camada (`competition` tem controller, service e repository no
mesmo pacote), então a camada vem do papel da classe, não do pacote:

| Camada | Classes |
|---|---|
| Controller | Anotadas com `@RestController` ou `@Controller` |
| Repository | Interfaces do Spring Data (que estendem `org.springframework.data.repository.Repository`) e classes anotadas com `@Repository` |
| Service | Todas as outras classes de produção: services, handlers, mappers, entidades, configuração, filtros |

## Cenários (comportamento esperado)

Não há `.feature` novo: a mudança é na forma de testar, não no comportamento dos serviços.
Verificação:

1. **Código atual:** o teste passa no `app` e no `email-service` (com a decisão em aberto
   resolvida).
2. **Controller usando repository:** um controller que receba um `UserRepository` no construtor
   faz o teste falhar, e a mensagem diz o controller e o repository.
3. **Controller usando API de banco:** um controller que use `JdbcTemplate` ou `EntityManager`
   também falha.
4. **Service usando controller:** uma classe que não é controller e referencia um controller
   (campo, parâmetro, chamada) faz o teste falhar.
5. **Repository usando service ou controller:** um repository que referencie um service ou um
   controller faz o teste falhar.
6. **Comentário não conta:** citar um controller em Javadoc (como `PlayerSummaryView` faz) não
   é dependência e não falha.

## Requisitos funcionais

- **Controller** não depende de repository nem de API de acesso a banco (`JdbcTemplate`,
  `NamedParameterJdbcTemplate`, `EntityManager`, `DataSource`).
- **Nenhuma classe** depende de controller. Quem chama controller é o Spring.
- **Repository** não depende de service nem de controller; pode depender de entidades e
  projeções (`PlayerSummaryView`), que ficam na camada de service pela tabela acima.
- A regra continua valendo junto da que já existe no `app`: repository só é acessado pelo
  próprio módulo (`repositoriesAreOnlyAccessedFromTheirOwnModule`).
- Classes geradas do OpenAPI (pacote `api`) ficam fora da regra: são interfaces e modelos que o
  controller implementa e usa.
- Só classes de produção (`src/main`) entram; testes ficam de fora.
- **Constitution:** a seção "Fronteira entre módulos e serviços" ganha a regra de camadas,
  garantida por teste de arquitetura.

## Requisitos não-funcionais

- O teste roda na suíte normal (`mvn test`), sem infraestrutura de pé.

## Fora de escopo

- Ciclos entre módulos: spec [05-061](../05-061-sem-ciclos-entre-modulos/spec.md).
- Reorganizar os módulos em subpacotes por camada.
- `email-lambda` e `config-server`: não têm controller nem repository.

## Decisões resolvidas

- **Camada pelo papel da classe, não pelo pacote**: os módulos são pacotes únicos; criar
  subpacotes por camada seria uma reorganização grande sem pedido.

## Decisões em aberto

1. **`JdbcTemplate` fora de repository.** No `email-service`, `DisposableDomainSourceService`,
   `DisposableDomainChecker`, `DisposableDomainImportService` e os tasklets `PruneDomainsTasklet`
   e `DownloadListTasklet` usam `JdbcTemplate` direto (importação em lote das listas de e-mail
   temporário, spec 05-048). Recomendação: a regra proíbe API de banco só em controller, e
   service pode usá-la. Mover esse SQL para repositories próprios deixaria a regra mais estrita
   ("só repository acessa o banco"), mas mexe na importação em lote, que acabou de ser
   estabilizada (spec 05-054), sem ganho de comportamento.
