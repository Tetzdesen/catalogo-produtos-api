## 1. Limpeza e preparação

- [ ] 1.1 Criar um branch de trabalho a partir de `main` e confirmar com `git status` que a árvore está limpa antes de apagar qualquer coisa — é o ponto de retorno descrito no Migration Plan
- [ ] 1.2 Remover `src/main/java/br/com/catalogo/` e `src/test/java/` por inteiro; verificar com `find src -name '*.java'` que nenhum arquivo Java sobrou (a estrutura antiga não deve sobreviver em pedaços dentro da nova)
- [ ] 1.3 Remover `src/test/resources/application-test.yml`; verificar que `src/test/` ficou vazio ou inexistente
- [ ] 1.4 Limpar do `pom.xml` as dependências que existiam só para os testes (H2 e os starters `spring-boot-*-test`), mantendo `spring-boot-starter-test` apenas se algo ainda o exigir; verificar com `mvn -q dependency:resolve` que a resolução conclui sem erro

## 2. Banco de dados e configuração

- [ ] 2.1 Reescrever `src/main/resources/db/migration/V1__criar_tabelas.sql` criando apenas `categoria` e `produto`, sem `saldo_estoque` e sem `movimentacao_estoque`, mantendo `UNIQUE (nome)`, `UNIQUE (sku)`, a FK de produto para categoria, o `CHECK (preco > 0)` e os índices de `categoria_id` e `nome`; verificar lendo o arquivo que nenhuma referência a estoque permaneceu
- [ ] 2.2 Ajustar `application.yml`: manter `ddl-auto: validate`, `open-in-view: false`, `max-page-size: 100`, `include-stacktrace: never` e `api.base-path`, e remover o que era específico de estoque ou de testes; verificar que o arquivo não cita mais movimentação
- [ ] 2.3 Recriar a base de desenvolvimento com `docker compose down -v && docker compose up -d` e verificar com `docker compose ps` que o PostgreSQL subiu saudável

## 3. Camada de domínio

- [ ] 3.1 Criar `domain/shared/RecursoNaoEncontradoException.java` e `domain/shared/RegraDeNegocioException.java` como exceções não verificadas, com Javadoc explicando para qual status HTTP cada uma será traduzida e por quem; verificar que nenhuma das duas importa qualquer coisa de `org.springframework.web`
- [ ] 3.2 Criar os objetos de valor `domain/produto/Sku.java` e `domain/produto/Preco.java` como `record` `@Embeddable` autovalidantes (SKU não em branco e normalizado para maiúsculas; preço maior que zero e no máximo duas casas decimais), comentando por que a validação mora no construtor compacto; verificar que ambos rejeitam valor inválido lançando `RegraDeNegocioException`
- [ ] 3.3 Criar o agregado `domain/categoria/Categoria.java` com construtor de negócio validando o nome (2 a 60 caracteres, não em branco), construtor `protected` sem argumentos comentado para o Hibernate, método `alterarDados(...)` e nenhum setter; verificar por leitura que não existe método `setX` público na classe
- [ ] 3.4 Criar o agregado `domain/produto/Produto.java` com `Sku` e `Preco` embutidos, `Long categoriaId` como referência por identidade (sem `@ManyToOne`), situação ativa no cadastro, e os métodos de negócio `alterarDados(...)`, `ativar()`, `inativar()` e `moverPara(categoriaId)`; incluir no Javadoc de classe a nota da Decisão 2 sobre o domínio anotado com JPA e a alternativa descartada
- [ ] 3.5 Criar as interfaces `domain/produto/ProdutoRepository.java` e `domain/categoria/CategoriaRepository.java` com apenas os métodos que os casos de uso usam, comentando que é o domínio declarando o que precisa (Inversão de Dependência) e por que a lista é curta (Segregação de Interface); verificar que o único import de Spring permitido é `org.springframework.data.domain.{Page,Pageable}`, conforme a exceção da Decisão 3
- [ ] 3.6 Conferir com `grep -rn "org.springframework" src/main/java/br/com/catalogo/domain/` que o domínio não importa Spring além da exceção acima — nada de Spring Web, Spring Data repositories ou anotações de estereótipo

## 4. Camada de aplicação

- [ ] 4.1 Criar os DTOs de aplicação de categoria (`CriarCategoriaCommand`, `AtualizarCategoriaCommand`, `CategoriaDTO`) como `record` em `application/categoria/dto`; verificar que nenhum deles carrega anotação de Bean Validation ou de springdoc — essas pertencem à camada `interfaces`
- [ ] 4.2 Implementar `application/categoria/CategoriaService.java` com os casos de uso de cadastro, consulta, listagem paginada, atualização e exclusão restritiva, aplicando a unicidade de nome sem distinção de caixa (desconsiderando a própria categoria na atualização) e bloqueando a exclusão quando houver produto vinculado; comentar `@Transactional` e `@Transactional(readOnly = true)` explicando o que cada um provoca
- [ ] 4.3 Criar os DTOs de aplicação de produto (`CriarProdutoCommand`, `AtualizarProdutoCommand`, `ProdutoDTO`, `ProdutoFiltro`) como `record` em `application/produto/dto`; verificar que `ProdutoDTO` carrega `categoriaId` e `categoriaNome` achatados, e não uma categoria aninhada
- [ ] 4.4 Implementar `application/produto/ProdutoService.java` com cadastro (SKU inédito, categoria existente, produto criado ativo), consulta, atualização, exclusão e a rejeição de alteração de SKU com `409`; comentar no ponto exato por que a categoria é buscada à parte (referência por identidade, Decisão 5)
- [ ] 4.5 Implementar no `ProdutoService` a listagem paginada com os filtros opcionais de trecho de nome e categoria, carregando os nomes das categorias da página em **uma única consulta** por ids; comentar ali o que seria o N+1 caso a consulta fosse feita por item — verificar o comportamento observando os logs SQL com `spring.jpa.show-sql` ligado temporariamente
- [ ] 4.6 Conferir com `grep -rn "jakarta.persistence\|org.springframework.web" src/main/java/br/com/catalogo/application/` que a aplicação não conhece nem JPA nem HTTP

## 5. Camada de infraestrutura

- [ ] 5.1 Criar `infrastructure/persistence/CategoriaRepositoryJpa.java` estendendo `CategoriaRepository` e `org.springframework.data.repository.Repository` (nunca `JpaRepository`), com `@Query` explícita em cada método cujo nome não segue a convenção de derived query; comentar por que `Repository` e não `JpaRepository` (Segregação de Interface)
- [ ] 5.2 Criar `infrastructure/persistence/ProdutoRepositoryJpa.java` nos mesmos moldes, incluindo a consulta de listagem com filtros opcionais de nome (sem distinção de caixa) e categoria, e a verificação de existência de produto em uma categoria
- [ ] 5.3 Subir a aplicação com `mvn spring-boot:run` e verificar que o start conclui sem erro — com `ddl-auto: validate`, isso prova que as entidades batem com a migração e que o Spring encontrou uma implementação para cada interface de repositório do domínio

## 6. Camada de interfaces (borda HTTP)

- [ ] 6.1 Criar `interfaces/rest/payload/PaginaResponse.java` como `record` genérico com `conteudo`, `pagina`, `tamanho`, `totalElementos` e `totalPaginas`, comentando por que o `Page` do Spring Data não é serializado direto
- [ ] 6.2 Criar os payloads HTTP de categoria (`CategoriaRequest` com Bean Validation, `CategoriaResponse` com fábrica estática `de(dto)`) em `interfaces/rest/payload`; comentar o que `@NotBlank` e `@Size` provocam e em que momento do fluxo eles rodam
- [ ] 6.3 Criar os payloads HTTP de produto (`ProdutoRequest` com `@NotBlank`, `@NotNull`, `@Positive` e `@Digits(integer = 10, fraction = 2)`, `ProdutoResponse`) e o objeto de filtros da listagem
- [ ] 6.4 Implementar `interfaces/rest/CategoriaController.java` com os cinco endpoints de `/categorias`, retornando `201` com `Location` no cadastro e `204` na exclusão; comentar `@Valid`, `@RequestBody` e `@PathVariable`
- [ ] 6.5 Implementar `interfaces/rest/ProdutoController.java` com os cinco endpoints de `/produtos`, incluindo a listagem paginada com os dois filtros opcionais e `Pageable`; verificar que não existe rota de estoque nem de ativação/inativação
- [ ] 6.6 Implementar `interfaces/rest/TratadorGlobalDeErros.java` como `@RestControllerAdvice` estendendo `ResponseEntityExceptionHandler`, mapeando `RecursoNaoEncontradoException` → `404`, `RegraDeNegocioException` → `409`, falhas de validação → `400` com a lista de campos, e o restante → `500` genérico, tudo em `application/problem+json`; comentar ali o ponto do princípio Aberto/Fechado (Decisão 7)
- [ ] 6.7 Mover `ApiConfig` e `OpenApiConfig` para `interfaces/config`, ajustando o predicado de pacote de `br.com.catalogo.controller` para `br.com.catalogo.interfaces.rest`; verificar que `GET /api/v1/categorias` responde e que `GET /categorias` (sem prefixo) devolve `404`
- [ ] 6.8 Confirmar que `RelogioConfig` não foi recriado (não há mais uso para o `Clock` injetado) com `find src -name 'RelogioConfig.java'` não retornando nada

## 7. Verificação manual do contrato

- [ ] 7.1 Rodar `mvn clean compile` e verificar que compila sem erro nem aviso relevante
- [ ] 7.2 Subir a aplicação e exercitar por `curl` os cenários de categoria: criação `201` com `Location`, nome duplicado `409`, nome em branco `400`, consulta `200`, consulta inexistente `404`, listagem `200` com os metadados de paginação, atualização mantendo o próprio nome `200`, exclusão com produto vinculado `409` e exclusão limpa `204`
- [ ] 7.3 Exercitar por `curl` os cenários de produto: criação `201`, SKU duplicado `409`, preço com três casas decimais `400`, categoria inexistente `404`, consulta `200` com `categoriaId` e `categoriaNome`, listagem filtrada por trecho de nome e por categoria, tentativa de alterar SKU `409`, atualização válida `200` e exclusão `204`
- [ ] 7.4 Verificar o contrato uniforme de erro: corpo JSON malformado devolvendo `400` em `application/problem+json`, identificador não numérico devolvendo `400`, e nenhuma resposta de erro contendo rastro de pilha
- [ ] 7.5 Verificar que `?size=100000` na listagem é atendido com o teto de 100 aplicado, sem erro, e que o metadado de tamanho reflete o valor efetivo
- [ ] 7.6 Verificar que `GET /v3/api-docs` responde `200` com documento válido e que o Swagger UI lista os dez endpoints com seus parâmetros e códigos de resposta

## 8. Revisão dos comentários e documentação

- [ ] 8.1 Revisar arquivo a arquivo se o padrão de comentário da Decisão 8 foi cumprido: Javadoc de classe dizendo em qual camada está e por quê, comentário nas anotações não óbvias, e comentário de decisão nos pontos indicados no design — removendo qualquer comentário que apenas repita o código
- [ ] 8.2 Verificar que cada um dos cinco princípios SOLID tem um comentário âncora no arquivo correspondente, conforme a Decisão 7
- [ ] 8.3 Reescrever o `README.md` como mapa de leitura das camadas: o que a API faz, como rodar (incluindo `docker compose down -v` para recriar a base), a lista de endpoints, a estrutura de pastas com o papel de cada camada e a direção das dependências, e as decisões didáticas centrais
- [ ] 8.4 Registrar no README, na seção de evoluções naturais, os itens conhecidos: suíte de testes (nº 1), índice funcional `LOWER(nome)` agora viável sem H2, e autenticação antes de qualquer uso real
- [ ] 8.5 Verificar que nada no README descreve capacidades que deixaram de existir (estoque, movimentações, filtros de preço e situação, ativação/inativação) com `grep -in "estoque\|movimenta\|saldo" README.md` retornando apenas menções históricas intencionais, se houver
