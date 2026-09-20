## Why

O repositório está vazio: existe apenas um README descrevendo a intenção de uma API de catálogo e estoque de produtos, sem nenhum código. Quem precisa cadastrar produtos, organizá-los por categoria e acompanhar a quantidade disponível hoje não tem ferramenta alguma, e não há uma base de projeto sobre a qual evoluir.

Esta mudança entrega a primeira versão funcional da API e, junto com ela, um README que explica a razão de cada decisão técnica — o projeto serve tanto como serviço utilizável quanto como referência didática de um Spring Boot bem estruturado.

## What Changes

- **Novo projeto Spring Boot** (Maven, Java 21, Spring Boot 4) com arquitetura em camadas: `controller` → `service` → `repository` → `entity`, com DTOs separados das entidades.
- **CRUD de Categorias**: criar, listar (paginado), buscar por id, atualizar e excluir. Exclusão bloqueada quando há produtos vinculados.
- **CRUD de Produtos**: criar, listar com paginação e filtros (nome, categoria, faixa de preço, status ativo), buscar por id, atualizar, ativar/inativar e excluir. SKU único por produto.
- **Controle de estoque por movimentações**: endpoints de entrada (`ENTRADA`), saída (`SAIDA`) e ajuste (`AJUSTE`). Cada movimentação é registrada com quantidade, motivo e data; o saldo do produto é recalculado na mesma transação. Saída maior que o saldo é rejeitada.
- **Histórico de movimentações** consultável por produto, paginado e ordenado por data decrescente.
- **Validação de entrada** com Bean Validation nos DTOs de request.
- **Tratamento de erros padronizado** em RFC 9457 (`application/problem+json`) via `@RestControllerAdvice`, cobrindo erro de validação, recurso não encontrado, conflito de regra de negócio e erro interno.
- **Documentação OpenAPI/Swagger** gerada por springdoc, servida em `/swagger-ui.html`.
- **Infraestrutura de execução**: `docker-compose.yml` com PostgreSQL, migrações de schema com Flyway, e perfil de teste usando H2 em memória.
- **Testes automatizados**: testes unitários de service com JUnit 5 + Mockito e testes de integração dos controllers com `@SpringBootTest` + MockMvc sobre H2.
- **README reescrito** documentando cada decisão (stack, arquitetura, modelagem, estoque por movimentação, RFC 9457, H2 nos testes, ausência de autenticação) com a justificativa e as alternativas descartadas.

**Não faz parte desta mudança** (registrado como evolução futura no README): autenticação/autorização, upload de imagens, multi-tenant, reserva de estoque e eventos assíncronos.

## Capabilities

### New Capabilities
- `catalogo/categorias`: cadastro e consulta de categorias usadas para organizar o catálogo, incluindo a regra que impede excluir categoria em uso.
- `catalogo/produtos`: cadastro, consulta paginada com filtros, atualização e desativação de produtos, com SKU único e vínculo obrigatório a uma categoria.
- `estoque/movimentacoes`: registro de entradas, saídas e ajustes de estoque, recálculo do saldo do produto e consulta do histórico de movimentações.

### Modified Capabilities
<!-- Nenhuma: o projeto não possui specs existentes. -->

## Impact

- **Código**: projeto criado do zero — `pom.xml`, `src/main/java/**`, `src/main/resources/**` (incluindo migrações Flyway), `src/test/java/**`.
- **APIs**: superfície HTTP nova sob `/api/v1` (`/categorias`, `/produtos`, `/produtos/{id}/movimentacoes`). Sem quebra de compatibilidade, pois nada existe hoje.
- **Dependências**: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `postgresql`, `flyway-core`, `springdoc-openapi-starter-webmvc-ui`, e em escopo de teste `spring-boot-starter-test` e `h2`.
- **Infraestrutura**: passa a exigir PostgreSQL em execução (via `docker-compose up -d`) para rodar a aplicação localmente; a suíte de testes não depende de serviços externos.
- **Documentação**: `README.md` na raiz é substituído pela versão comentada.
