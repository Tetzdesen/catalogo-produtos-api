## Why

A API atual funciona, mas foi escrita para demonstrar decisões de engenharia — lock pessimista, Specifications, histórico imutável de estoque, suíte de testes em dois níveis. Para quem está **aprendendo Spring**, esse volume esconde o que importa: como uma requisição HTTP vira uma regra de negócio e uma linha no banco.

Esta mudança reescreve a API do zero com um objetivo diferente e explícito: **ser lida e entendida**. Código menor, cada arquivo comentado explicando o *porquê* de cada anotação e de cada camada, organizado segundo DDD pragmático e os princípios SOLID — para que a estrutura ensine a arquitetura, não só a implemente.

## What Changes

- **BREAKING**: o pacote `br.com.catalogo` é reorganizado das camadas técnicas atuais (`controller`, `service`, `repository`, `domain`, `dto`, `exception`) para as quatro camadas de DDD: `domain`, `application`, `infrastructure`, `interfaces`. Nenhuma classe atual sobrevive no mesmo lugar.
- **BREAKING**: o agregado **Estoque** é removido por inteiro — entidade `MovimentacaoEstoque`, o campo `Produto.saldoEstoque`, os endpoints `/produtos/{id}/movimentacoes/**` e o bloqueio pessimista. Controle de estoque é o assunto mais complexo da API atual e não é necessário para ensinar Spring.
- **BREAKING**: os endpoints de ativação/inativação de produto (`PATCH /produtos/{id}/ativacao` e `/inativacao`) são removidos; a situação passa a ser um campo comum, editado pelo `PUT`.
- **BREAKING**: a listagem de produtos deixa de usar `Specification` com cinco filtros combináveis e passa a oferecer dois filtros opcionais (`nome` e `categoriaId`) resolvidos por *derived queries* do Spring Data — o mecanismo que um iniciante encontra primeiro.
- O domínio ganha vocabulário DDD explícito: `Produto` e `Categoria` como **agregados** que protegem as próprias invariantes (construtor e métodos de negócio, sem *setters* públicos), `Preco` e `Sku` como **objetos de valor** imutáveis e autovalidantes, e `ProdutoRepository`/`CategoriaRepository` como **interfaces declaradas no domínio** e implementadas na infraestrutura — inversão de dependência (o "D" de SOLID) visível na estrutura de pastas.
- `Produto` referencia a categoria por **identidade** (`categoriaId`), não por associação JPA `@ManyToOne` — a regra DDD de que um agregado não navega para dentro de outro.
- Todo o código-fonte passa a ser **comentado com propósito didático**: o que a classe faz, por que ela está naquela camada, e o que cada anotação Spring/JPA relevante provoca. Comentários explicam decisão, não repetem a sintaxe.
- **BREAKING**: a suíte de testes atual (15 arquivos) é **removida sem substituição**, por decisão explícita do usuário. Fica registrado como a evolução natural nº 1 no README.
- Infraestrutura mantida como está: PostgreSQL 17 via `docker-compose`, migrações Flyway versionadas e `ddl-auto: validate`. A migração é reescrita para o novo schema (sem tabela de movimentações, sem coluna de saldo).
- Mantidos: prefixo `/api/v1`, erros no padrão RFC 9457 via `@RestControllerAdvice`, envelope de paginação próprio, DTOs como `record` com Bean Validation, `BigDecimal` para preço, injeção por construtor e OpenAPI gerado pelo springdoc.
- O README é reescrito para acompanhar a nova API: menos catálogo de decisões de engenharia, mais mapa de leitura das camadas para quem está estudando.

## Capabilities

### New Capabilities
- `catalogo/produtos`: cadastro, consulta individual, listagem paginada com filtros simples, atualização e exclusão de produtos, com SKU único e imutável, preço positivo e vínculo obrigatório a uma categoria existente.
- `catalogo/categorias`: cadastro, consulta individual, listagem paginada, atualização e exclusão de categorias, com nome único e exclusão bloqueada enquanto houver produtos vinculados.

### Modified Capabilities
<!-- Nenhuma. O diretório openspec/specs/ está vazio: a mudança anterior
     (add-catalogo-produtos-api) nunca foi arquivada nem sincronizada, então não
     existe spec principal a modificar. Estas duas capacidades entram como novas. -->

## Impact

- **Código removido por completo**: todo o conteúdo atual de `src/main/java/br/com/catalogo/` (31 arquivos) e `src/test/java/` (18 arquivos).
- **Código novo**: cerca de 25 arquivos sob `domain/`, `application/`, `infrastructure/` e `interfaces/`, todos comentados.
- **Banco de dados**: nova migração Flyway `V1__criar_tabelas.sql` substituindo a atual — tabelas `categoria` e `produto` apenas; sem `movimentacao_estoque` e sem `produto.saldo_estoque`. Como o schema muda de forma incompatível, bases de desenvolvimento existentes precisam ser recriadas (`docker compose down -v`).
- **Contrato HTTP**: consumidores dos endpoints de estoque e de ativação/inativação perdem essas rotas; a listagem de produtos perde os filtros `precoMinimo`, `precoMaximo` e `ativo`. O campo `saldoEstoque` some da resposta de produto e `categoria` deixa de vir aninhada, passando a `categoriaId` + `categoriaNome`.
- **Dependências (`pom.xml`)**: sem alterações de infraestrutura; possível remoção das dependências usadas apenas pelos testes (H2, starters de teste), já que a suíte deixa de existir.
- **Documentação**: README reescrito; Swagger UI e `/v3/api-docs` seguem disponíveis e refletem automaticamente o novo contrato.
