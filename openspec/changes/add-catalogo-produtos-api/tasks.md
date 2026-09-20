## 1. Setup do projeto e infraestrutura

- [x] 1.1 Gerar o esqueleto Maven do projeto (Java 21, Spring Boot 4, grupo `br.com.catalogo`, artefato `catalogo-produtos-api`) com os starters `web`, `validation` e `data-jpa`; verificar que `mvn -q compile` conclui sem erro
- [x] 1.2 Confirmar a versão de `springdoc-openapi-starter-webmvc-ui` compatível com a versão do Spring Boot escolhida e adicioná-la; se não houver release compatível, aplicar o plano B do design (fixar Spring Boot 3.5.x) e registrar o motivo para o README. Verificar que a aplicação sobe e `/v3/api-docs` responde `200`
- [x] 1.3 Adicionar as demais dependências — `postgresql`, `flyway-core`, e em escopo de teste `spring-boot-starter-test` e `h2`; verificar com `mvn -q dependency:tree` que todas resolvem
- [ ] 1.4 Criar `docker-compose.yml` com PostgreSQL (porta, usuário, senha, volume nomeado) e verificar que `docker compose up -d` deixa o banco aceitando conexões
- [x] 1.5 Configurar `application.yml` (datasource, `jpa.hibernate.ddl-auto: validate`, Flyway habilitado, prefixo `/api/v1`, limite máximo de tamanho de página) e `application-test.yml` (H2 em memória em modo de compatibilidade PostgreSQL); verificar que a aplicação sobe com o perfil padrão e que o contexto de teste carrega
- [x] 1.6 Criar o pacote base e a classe `@SpringBootApplication`; verificar com um teste de smoke `contextLoads` passando

## 2. Modelo de dados e migrações

- [x] 2.1 Escrever a migração Flyway `V1__criar_tabelas.sql` com as tabelas `categoria` (nome único case-insensitive), `produto` (SKU único, FK para categoria, preço `NUMERIC(12,2)`, `saldo_estoque` não negativo, flag de situação) e `movimentacao_estoque` (FK para produto, tipo, quantidade, saldo resultante, motivo, data/hora), incluindo índices para os filtros de listagem; verificar que a migração aplica do zero em banco limpo
- [x] 2.2 Implementar as entidades JPA `Categoria`, `Produto` e `MovimentacaoEstoque` e o enum `TipoMovimentacao` (`ENTRADA`, `SAIDA`, `AJUSTE`); verificar com a aplicação subindo em `ddl-auto: validate` (divergência entre entidade e schema falha no start)
- [x] 2.3 Criar os repositórios Spring Data (`CategoriaRepository` com checagem de nome existente, `ProdutoRepository` estendendo `JpaSpecificationExecutor` e com busca por SKU, `MovimentacaoEstoqueRepository` com consulta paginada por produto e tipo); verificar com testes `@DataJpaTest` cobrindo cada método de consulta

## 3. Tratamento de erros, paginação e documentação

- [x] 3.1 Implementar as exceções de domínio `RecursoNaoEncontradoException` e `RegraDeNegocioException`; verificar que são lançadas e capturadas nos testes do handler
- [x] 3.2 Implementar o `@RestControllerAdvice` global produzindo `ProblemDetail` (RFC 9457) para erro de validação (`400` com lista de campos), não encontrado (`404`), regra de negócio (`409`) e erro interno (`500` sem stack trace); verificar com testes que cada caso retorna `application/problem+json` com o status e o corpo esperados
- [x] 3.3 Implementar `PaginaResponse<T>` e o utilitário de conversão a partir de `Page<T>`, com limite máximo de tamanho de página; verificar com teste que os metadados (`pagina`, `tamanho`, `totalElementos`, `totalPaginas`) são produzidos corretamente e que tamanho acima do limite é truncado
- [x] 3.4 Configurar o bean de `OpenAPI` (título, descrição, versão) do springdoc; verificar que `/swagger-ui.html` carrega e lista os endpoints já existentes

## 4. Capability `catalogo/categorias`

- [x] 4.1 Criar os DTOs `CategoriaRequest` (com Bean Validation para nome de 2 a 60 caracteres e descrição até 255) e `CategoriaResponse`, mais o mapper; verificar com teste unitário do mapeamento em ambos os sentidos
- [x] 4.2 Implementar `CategoriaService` com criação, busca por id, listagem paginada, atualização e exclusão, aplicando unicidade de nome case-insensitive e o bloqueio de exclusão quando há produtos vinculados; verificar com testes unitários JUnit 5 + Mockito cobrindo os cenários da spec `catalogo/categorias` (sucesso, nome duplicado na criação e na atualização, não encontrado, exclusão bloqueada)
- [x] 4.3 Implementar `CategoriaController` em `/api/v1/categorias` com os cinco endpoints, `201 Created` + `Location` na criação e `204 No Content` na exclusão; verificar com testes de integração MockMvc cobrindo cada cenário da spec, incluindo o corpo de erro RFC 9457 em `400`, `404` e `409`

## 5. Capability `catalogo/produtos`

- [x] 5.1 Criar os DTOs `ProdutoRequest`, `ProdutoResponse` e `ProdutoFiltro` com Bean Validation (SKU e nome obrigatórios, preço positivo com duas casas, categoria obrigatória) e o mapper; verificar com teste unitário do mapeamento e das anotações de validação
- [x] 5.2 Implementar `ProdutoSpecifications` compondo os filtros opcionais de nome (contém, sem distinção de caixa), categoria, preço mínimo, preço máximo e situação; verificar com testes `@DataJpaTest` que cada filtro isolado e a combinação de todos retornam o conjunto esperado
- [x] 5.3 Implementar `ProdutoService` com criação (SKU único, categoria existente, situação ativa e saldo zero), busca por id, listagem filtrada e paginada, atualização (SKU imutável, saldo preservado), ativação/inativação e exclusão bloqueada quando há movimentações; verificar com testes unitários cobrindo todos os cenários da spec `catalogo/produtos`, incluindo faixa de preço inconsistente
- [x] 5.4 Implementar `ProdutoController` em `/api/v1/produtos` com os endpoints de CRUD, filtro por query params e as ações de ativar/inativar; verificar com testes de integração MockMvc cobrindo os cenários da spec e os respectivos códigos HTTP (`201`/`200`/`204`/`400`/`404`/`409`)

## 6. Capability `estoque/movimentacoes`

- [x] 6.1 Criar os DTOs `MovimentacaoRequest` (quantidade e motivo obrigatórios), `AjusteRequest` (quantidade zero ou maior e motivo obrigatório) e `MovimentacaoResponse` (tipo, quantidade, saldo resultante, motivo, data/hora) e o mapper; verificar com teste unitário de mapeamento e validação
- [x] 6.2 Adicionar ao `ProdutoRepository` a busca por id com `@Lock(PESSIMISTIC_WRITE)` usada pelo fluxo de movimentação; verificar com teste `@DataJpaTest` que a consulta retorna a entidade dentro de transação
- [x] 6.3 Implementar `MovimentacaoEstoqueService` com entrada, saída e ajuste, todos `@Transactional`, carregando o produto com bloqueio pessimista, gravando a movimentação com o saldo resultante e atualizando o saldo do produto atomicamente; verificar com testes unitários cobrindo os cenários da spec `estoque/movimentacoes` (entrada, saída, saída maior que o saldo, saída em produto inativo, ajuste para cima, ajuste para baixo, quantidade inválida, motivo ausente, produto inexistente)
- [x] 6.4 Implementar a consulta paginada do histórico por produto, ordenada por data decrescente e com filtro opcional por tipo; verificar com teste que a ordenação, o filtro e o caso de histórico vazio se comportam conforme a spec
- [x] 6.5 Implementar `MovimentacaoEstoqueController` em `/api/v1/produtos/{id}/movimentacoes` expondo entrada, saída, ajuste e histórico, sem qualquer operação de alteração ou exclusão de movimentação; verificar com testes de integração MockMvc que os quatro endpoints respondem conforme a spec e que não existe rota de PUT/DELETE de movimentação
- [x] 6.6 Escrever um teste de integração de consistência entre saldo e histórico — sequência de movimentações seguida da conferência de que o saldo do produto equivale ao acumulado — e um teste de falha no meio da transação provando que nada é persistido; verificar que ambos passam

## 7. Documentação e fechamento

- [x] 7.1 Reescrever o `README.md` da raiz com: visão geral da API, como rodar (Docker Compose, `mvn spring-boot:run`, Swagger), tabela de endpoints, exemplos de requisição/resposta (incluindo um corpo de erro RFC 9457) e a seção de decisões — uma por decisão do `design.md`, cada uma com o porquê e as alternativas descartadas — mais a seção de evoluções futuras (autenticação, Testcontainers, `UPDATE` condicional no lugar do lock pessimista). Verificar lendo o README de ponta a ponta: toda escolha visível no código tem um parágrafo que a justifica
- [x] 7.2 Adicionar `.gitignore` (target, arquivos de IDE, `.env`) e `.env.example` ou equivalente com as variáveis do datasource; verificar que `git status` fica limpo após um build completo
- [x] 7.3 Rodar `mvn clean verify` em máquina sem Docker e verificar que toda a suíte passa sem depender de serviços externos
- [x] 7.4 Subir a aplicação com o PostgreSQL do Docker Compose e percorrer manualmente o fluxo completo (criar categoria → criar produto → entrada → saída → ajuste → consultar histórico → tentar saída acima do saldo); verificar que cada resposta corresponde ao que as specs descrevem
