## Context

Ver `proposal.md` — Why. O que importa aqui é o estado de partida e as restrições que moldam o desenho.

**Estado atual:** 31 classes em `br.com.catalogo` organizadas por camada técnica (`controller`, `service`, `repository`, `domain`, `dto`, `exception`, `config`), Spring Boot 4.1.1 / Java 21, PostgreSQL 17 com Flyway e `ddl-auto: validate`, springdoc para OpenAPI, 18 arquivos de teste. A entidade `Produto` tem `@ManyToOne` para `Categoria`, campo `saldoEstoque` e um agregado inteiro de movimentações de estoque com bloqueio pessimista.

**Restrições que o desenho precisa respeitar:**

1. **O leitor-alvo é alguém aprendendo Spring.** Cada arquivo a mais é um arquivo que alguém precisa abrir para entender o fluxo. Quando DDD "por livro" e clareza para iniciante divergem, este desenho escolhe a clareza — e comenta a escolha no próprio código.
2. **Decisões já fechadas com o usuário:** DDD pragmático em quatro camadas (não hexagonal puro); escopo Produto + Categoria (estoque removido); PostgreSQL + Docker + Flyway mantidos; **sem suíte de testes**.
3. **Sem testes automatizados**, a verificação da reescrita passa a depender de `mvn compile`, do start da aplicação (com `ddl-auto: validate` conferindo o mapeamento contra o schema) e de exercício manual dos endpoints.

## Goals / Non-Goals

**Goals:**

- Uma estrutura de pastas em que a arquitetura seja **legível antes do código**: ao expandir `src/main/java`, o leitor vê `domain`, `application`, `infrastructure`, `interfaces` e entende a direção das dependências.
- **Regra de dependência** verificável por leitura de `import`: `domain` não importa Spring Web nem Spring Data; `application` depende só de `domain`; `infrastructure` e `interfaces` dependem das duas de dentro.
- Invariantes de negócio vivendo **dentro dos agregados**, não espalhadas nos serviços.
- Comentários que expliquem **decisão e efeito** (por que esta classe está nesta camada, o que esta anotação provoca em tempo de execução), nunca a sintaxe óbvia.
- Cada princípio SOLID com **pelo menos um ponto concreto no código** onde ele possa ser apontado com o dedo.

**Non-Goals:**

- Domínio livre de anotações JPA. Descartado deliberadamente — ver Decisão 2.
- Ports/adapters, casos de uso como interfaces de uma operação só, mapeadores entre entidade de domínio e entidade de persistência.
- Cobertura de testes (decisão explícita do usuário; registrada no README como evolução nº 1).
- Autenticação, observabilidade, cache, versionamento de API além do prefixo `/api/v1` já existente.

## Decisions

### 1. Quatro camadas, agrupadas por agregado dentro do domínio

```
src/main/java/br/com/catalogo/
├── CatalogoProdutosApiApplication.java
├── domain/                       ← o coração: regras que existiriam mesmo sem HTTP e sem banco
│   ├── produto/
│   │   ├── Produto.java          agregado
│   │   ├── Sku.java              objeto de valor
│   │   ├── Preco.java            objeto de valor
│   │   └── ProdutoRepository.java   INTERFACE (contrato declarado pelo domínio)
│   ├── categoria/
│   │   ├── Categoria.java        agregado
│   │   └── CategoriaRepository.java INTERFACE
│   └── shared/
│       ├── RecursoNaoEncontradoException.java
│       └── RegraDeNegocioException.java
├── application/                  ← casos de uso: orquestram agregados e a transação
│   ├── produto/
│   │   ├── ProdutoService.java
│   │   └── dto/ (CriarProdutoCommand, AtualizarProdutoCommand, ProdutoDTO, ProdutoFiltro)
│   └── categoria/
│       ├── CategoriaService.java
│       └── dto/ (CriarCategoriaCommand, AtualizarCategoriaCommand, CategoriaDTO)
├── infrastructure/               ← detalhes trocáveis: como se fala com o banco
│   └── persistence/
│       ├── ProdutoRepositoryJpa.java     (Spring Data, estende ProdutoRepository)
│       └── CategoriaRepositoryJpa.java
└── interfaces/                   ← a borda: como o mundo externo fala com a aplicação
    ├── rest/
    │   ├── ProdutoController.java
    │   ├── CategoriaController.java
    │   ├── payload/ (requests e responses HTTP, PaginaResponse)
    │   └── TratadorGlobalDeErros.java
    └── config/ (ApiConfig, OpenApiConfig)
```

**Por quê:** dentro do domínio, agrupar por agregado (`produto/`, `categoria/`) e não por estereótipo (`model/`, `repository/`) é o que DDD chama de módulo — e mantém junto o que muda junto. Fora do domínio, o agrupamento continua por camada porque é ali que a *direção da dependência* precisa ficar visível.

**Descartado:** um pacote `shared`/`common` no topo (vira depósito); `interfaces` nomeado `presentation` ou `api` (`interfaces` é o termo da literatura de DDD em camadas e o nome ajuda na busca por referências).

### 2. Os agregados são as próprias entidades JPA

`Produto` e `Categoria` são classes de domínio anotadas com `@Entity`. Não haverá `ProdutoJpaEntity` + mapeador.

**Por quê:** a alternativa pura duplica cada campo em duas classes e acrescenta um mapeador que só copia valores. Para um projeto didático de dois agregados, isso triplica o número de arquivos a ler sem ensinar nada sobre Spring — ensina sobre boilerplate. A dependência do domínio passa a ser Jakarta Persistence (uma especificação, não um framework de aplicação), e não Spring.

**Custo assumido e comentado no código:** o domínio não é 100% puro; trocar JPA por outra tecnologia de persistência exigiria tocar nas classes de domínio. O comentário no topo de `Produto` diz exatamente isso e aponta a alternativa.

**Como o domínio continua protegido apesar do JPA:**
- Construtor sem argumentos `protected` (exigido pelo Hibernate) e comentado explicando por que é `protected` e não `public`.
- Construtor público único que recebe tudo que torna o produto válido e valida antes de atribuir.
- **Sem setters.** Mudanças acontecem por métodos de negócio: `alterarDados(...)`, `ativar()`, `inativar()`, `moverPara(categoriaId)`.
- Nenhum `@Data`/Lombok — os métodos são escritos à mão para ficarem visíveis.

### 3. Repositório: interface no domínio, Spring Data na infraestrutura

```java
// domain/produto/ProdutoRepository.java — sem nenhum import de Spring
public interface ProdutoRepository {
    Produto salvar(Produto produto);
    Optional<Produto> buscarPorId(Long id);
    boolean existeComSku(Sku sku);
    boolean existeNaCategoria(Long categoriaId);
    Page<Produto> buscar(String trechoNome, Long categoriaId, Pageable pagina);
    void remover(Produto produto);
}
```

```java
// infrastructure/persistence/ProdutoRepositoryJpa.java
public interface ProdutoRepositoryJpa extends ProdutoRepository, Repository<Produto, Long> { ... }
```

**Por quê:** é a **Inversão de Dependência** tornada visível — o domínio declara o que precisa, a infraestrutura serve. O `application` injeta `ProdutoRepository` (a interface do domínio) e nunca conhece o Spring Data.

**Detalhe técnico deliberado:** a interface da infraestrutura estende `org.springframework.data.repository.Repository` (o marcador vazio) e **não** `JpaRepository`. Estendendo `JpaRepository`, os serviços enxergariam dezenas de métodos genéricos (`findAll`, `flush`, `deleteAllInBatch`) que não fazem parte do contrato do domínio — exatamente a violação do **Princípio da Segregação de Interface** que este desenho quer evitar. Com `Repository`, os únicos métodos existentes são os que o domínio pediu.

Os nomes dos métodos do domínio são em português e não seguem a convenção de *derived query* do Spring Data, então cada um recebe `@Query` explícita na interface da infraestrutura. Isso é um ganho didático: o JPQL fica à vista em vez de mágico.

**Exceção deliberada:** `Page` e `Pageable` (de `org.springframework.data.domain`) aparecem na assinatura do domínio. Paginação é um conceito de leitura, não de negócio, e criar tipos próprios só para embrulhá-los custaria mais do que ensina. Registrado em Risks.

### 4. Objetos de valor: `Sku` e `Preco` como `@Embeddable`

```java
@Embeddable
public record Sku(String valor) {
    public Sku {
        if (valor == null || valor.isBlank()) throw new RegraDeNegocioException("...");
        valor = valor.trim().toUpperCase();
    }
}
```

**Por quê:** é o conceito de DDD mais fácil de demonstrar e o de retorno mais imediato. Um `Preco` que se recusa a existir negativo torna impossível um produto com preço inválido em qualquer ponto do código — a validação deixa de ser algo que alguém pode esquecer de chamar. `record` dá imutabilidade e `equals` por valor de graça (dois `Preco` de 49.90 são iguais; é essa a definição de objeto de valor).

**Escopo contido:** apenas dois objetos de valor, nos campos em que a invariante é real. `nome` e `descricao` continuam `String` — envolver toda `String` em um tipo é o caminho para um domínio ilegível.

**Descartado:** classes com construtor privado e fábrica estática `Preco.de(...)` (mais cerimônia, mesmo efeito em Java 21 com `record`).

### 5. `Produto` referencia a categoria por identidade, não por associação

`Produto` guarda `Long categoriaId`, não `Categoria categoria`. Não há `@ManyToOne`.

**Por quê:** é a regra DDD de que um agregado referencia outro por identidade — e ela evita, de quebra, duas armadilhas concretas do JPA: o lazy loading acidental fora da transação (motivo de `open-in-view: false`) e a ilusão de que salvar um produto poderia alterar uma categoria.

**Consequência assumida:** o response de produto precisa do *nome* da categoria, que não está no agregado. O `ProdutoService` busca a categoria pelo id e monta o `ProdutoDTO` — uma consulta extra, explícita e comentada. Na listagem paginada, os nomes das categorias da página são carregados **em uma única consulta** por ids e usados como mapa, para não produzir N+1. Esse comentário é o lugar natural para explicar o que é N+1.

**Descartado:** manter `@ManyToOne` com `FETCH.LAZY` (mais conveniente, mas ensina a associação antes de ensinar a fronteira do agregado); criar uma projeção/view de leitura (sobrecarga de conceito para dois agregados).

### 6. Três formatos de dado, um por camada — e a justificativa para não reduzir

| Camada | Tipo | Papel |
|---|---|---|
| `interfaces/rest/payload` | `ProdutoRequest`, `ProdutoResponse` (`record`) | Contrato HTTP; carrega Bean Validation (`@NotBlank`, `@Positive`) e anotações do springdoc |
| `application/.../dto` | `CriarProdutoCommand`, `ProdutoDTO` (`record`) | Fronteira do caso de uso, em tipos de domínio |
| `domain` | `Produto`, `Sku`, `Preco` | Estado e regras |

**Por quê:** é a diferença entre "o que o cliente HTTP mandou" e "o que o caso de uso precisa". Faz com que trocar o formato JSON não alcance o serviço, e permite que o `ProdutoService` seja chamado de um job ou de um teste sem nada de HTTP.

**Custo assumido:** uma conversão a mais por requisição. A conversão fica em métodos estáticos no próprio `record` (`ProdutoRequest.paraComando()`, `ProdutoResponse.de(dto)`), sem MapStruct nem classe `Mapper` — o mapeamento fica ao lado do tipo que ele produz, e o leitor vê o código real em vez de código gerado.

**Descartado:** o controller falar direto com o domínio (mistura a validação HTTP com a de negócio e acopla o JSON ao schema); um único DTO servindo as três camadas (é o acoplamento que o desenho existe para evitar).

### 7. Onde cada princípio SOLID é apontável

Cada item abaixo vira um comentário no arquivo correspondente. Servem de âncora didática, não de justificativa retroativa.

- **S — Responsabilidade Única:** `ProdutoController` traduz HTTP; `ProdutoService` orquestra o caso de uso e delimita a transação; `Produto` guarda as invariantes; `ProdutoRepositoryJpa` fala com o banco. Nenhuma das quatro sabe fazer o trabalho das outras.
- **O — Aberto/Fechado:** `TratadorGlobalDeErros` mapeia exceções por tipo; uma nova exceção de domínio ganha tratamento adicionando um `@ExceptionHandler`, sem tocar nos existentes.
- **L — Substituição de Liskov:** `ProdutoRepositoryJpa` é usado em todo lugar apenas como `ProdutoRepository`; nenhum serviço faz *cast* para o tipo concreto nem depende de comportamento que só a implementação JPA tem.
- **I — Segregação de Interface:** `ProdutoRepository` expõe seis métodos — os que o domínio usa. É o motivo de estender `Repository` e não `JpaRepository` (Decisão 3).
- **D — Inversão de Dependência:** `application` depende da interface declarada em `domain`; a implementação em `infrastructure` é injetada pelo Spring em tempo de execução. A seta de compilação aponta para dentro, mesmo que a de execução aponte para fora.

### 8. Padrão de comentário

Três níveis, aplicados sem exceção:

1. **Javadoc de classe**: o que ela faz, **em qual camada está e por quê**, e o que ela deliberadamente não faz. É o comentário que responde "por que este arquivo existe".
2. **Comentário de anotação não óbvia**: `@Transactional`, `@Embedded`, `@Enumerated`, `@Valid`, `@ResponseStatus`, `@Query` — o que a anotação provoca em tempo de execução. `@Override` e `@Entity` não ganham comentário.
3. **Comentário de decisão**: no ponto exato em que alguém perguntaria "por que assim?" (a consulta extra da categoria, o construtor `protected`, a ausência de setter).

**O que não comentar:** getters, a sintaxe de um `record`, ou qualquer linha cujo comentário seja a tradução literal do código. Comentário que repete o código envelhece e mente.

**Idioma:** português, como o resto do projeto.

### 9. Banco: nova migração `V1`, base recriada

A migração atual é **substituída** (não sucedida por um `V2`): `V1__criar_tabelas.sql` passa a criar apenas `categoria` e `produto`, sem `saldo_estoque` e sem `movimentacao_estoque`.

**Por quê:** o projeto não tem instalação em produção; um `V2__remover_estoque.sql` deixaria como primeira lição do Flyway uma migração de limpeza de algo que o leitor nunca viu existir. Reescrever o `V1` deixa a história do schema com um capítulo só, que é o que o leitor precisa ler.

**Consequência operacional:** o checksum do `V1` muda e o Flyway falha em qualquer base já migrada. Tratado no Migration Plan.

`ddl-auto: validate` é mantido: sem testes, ele passa a ser a principal rede de segurança automática do projeto — divergência entre entidade e tabela derruba a aplicação no start, em vez de virar erro em produção.

Os `CHECK` de invariante no banco são mantidos para preço (`preco > 0`) e o de saldo desaparece com a coluna. Índices reduzidos aos filtros que sobraram: `categoria_id` e `nome`.

### 10. Configuração e contrato HTTP preservados

`application.yml` segue praticamente igual: `ddl-auto: validate`, `open-in-view: false`, teto de página em 100, `include-stacktrace: never`, `api.base-path: /api/v1`. `ApiConfig` é mantido em `interfaces/config`, com o predicado de pacote ajustado de `br.com.catalogo.controller` para `br.com.catalogo.interfaces.rest`.

**Por quê:** são decisões já corretas e cada uma é uma boa lição. Trocá-las gratuitamente aumentaria o diff sem ganho.

`RelogioConfig` (bean de `Clock`) é **removido** — existia para testar as datas das movimentações, e nem as movimentações nem os testes sobrevivem.

## Risks / Trade-offs

- **Sem testes, uma regressão só aparece em execução** → `ddl-auto: validate` pega erro de mapeamento no start; a tarefa final do plano é um roteiro de verificação manual (`curl`) cobrindo o caminho feliz e os erros `400`/`404`/`409` de cada capacidade. Registrado no README como a evolução nº 1.
- **Domínio anotado com JPA não é domínio puro** (Decisão 2) → aceito conscientemente, comentado no código junto com a alternativa. É a troca explícita de pureza por legibilidade.
- **`Page`/`Pageable` do Spring Data vazam para a assinatura do domínio** (Decisão 3) → aceito; a alternativa (tipos próprios de paginação embrulhando os do Spring) custa mais atenção do leitor do que ensina.
- **A consulta extra do nome da categoria** (Decisão 5) → na leitura individual é uma consulta por id; na listagem, uma consulta por página, não por item. O comentário no código explica por que a solução ingênua seria N+1.
- **Unicidade de nome de categoria sem distinção de caixa** continua garantida pela aplicação, com `UNIQUE (nome)` exato no banco como apoio → sob concorrência extrema resta uma janela estreita. Como o H2 saiu junto com os testes, agora **é viável** o índice funcional `LOWER(nome)` exclusivo do PostgreSQL; fica registrado no README como melhoria, fora do escopo desta mudança.
- **Quatro camadas para dois agregados podem parecer excesso** → é o ponto: a estrutura existe para ser aprendida. O Javadoc de cada camada diz em que tamanho de projeto ela começa a se pagar, para o leitor não sair achando que todo CRUD precisa disto.
- **A reescrita perde capacidades reais** (estoque, filtros de preço/situação, ativação/inativação) → decisão explícita do usuário; a API atual permanece recuperável no histórico do Git.

## Migration Plan

1. **Ponto de retorno:** a API atual está commitada em `main` (`7577efc`) e o trabalho novo vai para um branch. Reverter é trocar de branch.
2. **Remover antes de escrever:** apagar `src/main/java/br/com/catalogo/**` e `src/test/java/**` inteiros, em vez de mover arquivo a arquivo. Um resto da estrutura antiga sobrevivendo dentro da nova é pior do que qualquer retrabalho.
3. **Base de desenvolvimento:** como o checksum do `V1` muda, a base existente precisa ser descartada — `docker compose down -v && docker compose up -d`. Instrução repetida no README, porque `flyway.repair` não é o caminho aqui.
4. **Verificação do schema:** o start com `ddl-auto: validate` é a primeira prova de que as entidades batem com a migração; ele deve rodar limpo antes de qualquer teste manual de endpoint.
5. **Verificação do contrato:** roteiro `curl` cobrindo os cenários das duas specs, incluindo os de erro. Em seguida, `/v3/api-docs` respondendo `200` e o Swagger UI listando os dez endpoints.
6. **README por último**, quando o código já é verdade — documentação escrita antes do código descreve a intenção, não o resultado.
