# catalogo-produtos-api

API REST básica de catálogo de produtos em Spring Boot: um único recurso,
**Produto**, com cadastro, consulta, listagem paginada com busca por nome,
atualização e exclusão.

O código é pequeno de propósito — dá para ler de ponta a ponta em poucos
minutos — e cada arquivo é comentado explicando *por que* está ali.

---

## Como rodar

**Pré-requisitos:** Java 21 e Maven 3.8+. Docker apenas para o banco.

```bash
# 1. Sobe o PostgreSQL
docker compose up -d

# 2. Sobe a aplicação (o Flyway cria a tabela sozinho no start)
mvn spring-boot:run
```

- API: `http://localhost:8080/api/v1/produtos`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Spec OpenAPI: `http://localhost:8080/v3/api-docs`

> **Já tem um PostgreSQL instalado na máquina?** Ele provavelmente ocupa a porta
> 5432, e o contêiner vai falhar ao subir. Ou você para o serviço local, ou usa o
> banco que já tem: crie o papel e o banco (`CREATE USER catalogo WITH PASSWORD
> 'catalogo'` e `CREATE DATABASE catalogo OWNER catalogo`) e pule o
> `docker compose`. O usuário precisa ser **dono** do banco, porque a migração
> cria a extensão `unaccent`. A conexão também pode ser trocada pelas variáveis
> `DB_URL`, `DB_USER` e `DB_PASSWORD`.

> **Vindo de uma versão anterior deste projeto?** A migração `V1` foi reescrita, e
> o Flyway guarda um *checksum* dela. Em uma base já criada, o start vai falhar
> acusando a divergência. Recrie o banco do zero:
>
> ```bash
> docker compose down -v && docker compose up -d
> ```

---

## Estrutura

A API segue as camadas clássicas de uma aplicação Spring (MVC). Dentro delas,
cada classe tem um papel só, e os princípios SOLID aparecem em lugares que dá
para apontar.

```
src/main/java/br/com/catalogo/
├── CatalogoProdutosApiApplication.java   ponto de entrada
├── controller/     ProdutoController — só HTTP: rotas, status, cabeçalhos
├── service/        ProdutoService (interface) e ProdutoServiceImpl — orquestra e abre a transação
│   └── regra/      RegraDeProduto (interface) e SkuUnicoRegra — uma regra de negócio por classe
├── repository/     ProdutoRepository — gerado pelo Spring Data; também escapa o LIKE
├── model/          Produto — a entidade JPA, com o próprio comportamento
├── dto/            ProdutoRequest, ProdutoResponse, PaginaResponse — o contrato JSON
├── mapper/         ProdutoMapper — converte DTO ↔ entidade
├── exception/      exceções-base, exceções específicas e o TratadorGlobalDeErros
└── config/         ApiConfig (prefixo /api/v1), OpenApiConfig
src/main/resources/
├── application.yml
└── db/migration/V1__criar_tabelas.sql
src/test/java/br/com/catalogo/   testes unitários, sem Spring e sem banco
```

Uma requisição de cadastro percorre:

```
ProdutoController ──▶ ProdutoService (interface)
                          │  implementada por ProdutoServiceImpl
                          ├──▶ RegraDeProduto* (SkuUnicoRegra, ...)  ──▶ ProdutoRepository
                          ├──▶ ProdutoMapper  (request → Produto)
                          ├──▶ ProdutoRepository.save(...)
                          └──▶ ProdutoMapper  (Produto → response)
          erro em qualquer ponto ──▶ TratadorGlobalDeErros ──▶ application/problem+json
```

**Ordem de leitura sugerida:** `ProdutoController` → `ProdutoService` →
`ProdutoServiceImpl` → `RegraDeProduto` e `SkuUnicoRegra` → `ProdutoRepository`
→ `Produto` → `ProdutoMapper` → `TratadorGlobalDeErros`. Cada arquivo explica,
nos comentários, *por que* está ali.

### SOLID: onde cada princípio aparece

| Princípio | Onde aparece no código |
|---|---|
| **S** — Responsabilidade única | O controller só traduz HTTP; o service só coordena; o mapper só converte; cada regra valida uma coisa; o repositório é o único que conhece SQL (inclusive o escape do `LIKE`); as exceções montam as próprias mensagens |
| **O** — Aberto/fechado | Regra nova = classe nova que implementa `RegraDeProduto`, sem editar o service. Exceção nova que estende `RecursoNaoEncontradoException` ou `RegraDeNegocioException` já sai como `404`/`409`, sem editar o tratador |
| **L** — Substituição de Liskov | Qualquer `RegraDeProduto` funciona na lista do service; qualquer subclasse das exceções-base funciona no tratador. Os testes trocam as implementações reais por *mocks* e o service nem percebe |
| **I** — Segregação de interfaces | `RegraDeProduto` tem um método só; o controller enxerga apenas os cinco métodos de `ProdutoService`, nada da implementação |
| **D** — Inversão de dependência | O controller depende da interface `ProdutoService`; o service depende de `List<RegraDeProduto>` e de `ProdutoRepository` (interfaces). Tudo chega pelo construtor, injetado pelo Spring, e por isso os testes montam o service com `new` |

### Padrões de projeto

**Usados:**

| Padrão | Onde |
|---|---|
| MVC e arquitetura em camadas | A organização dos pacotes |
| Front Controller | O `DispatcherServlet` do Spring, que recebe toda requisição e a despacha ao controller certo |
| Injeção de dependência | Construtores de controller, service, regra e mapper |
| Repository | `ProdutoRepository` (Spring Data) |
| DTO | `ProdutoRequest`, `ProdutoResponse`, `PaginaResponse` |
| Mapper | `ProdutoMapper` |
| Strategy | `RegraDeProduto` e suas implementações |
| Static Factory Method | `Produto.novo(...)`: o nome conta que o produto nasce ativo |
| Template Method | `TratadorGlobalDeErros` estende `ResponseEntityExceptionHandler` e sobrescreve só alguns passos |

**Deliberadamente não usados:**

| Padrão | Por quê |
|---|---|
| Builder | O produto tem quatro campos de entrada; um construtor/fábrica é suficiente |
| Observer / eventos | Nada no sistema precisa reagir a mudanças de produto |
| Facade | O service já é a fachada da camada de negócio |
| Hexagonal (Ports & Adapters) | Separaria domínio de entidade JPA e criaria portas; para um único recurso, a estrutura extra afasta quem está começando |

Duas escolhas são em parte didáticas, e vale dizer isso: a **interface
`ProdutoService`** tem uma única implementação, e o **Strategy** tem uma única
regra. Elas passam a se pagar de verdade quando aparece uma segunda
implementação (ou um decorador) e uma segunda regra.

---

## Testes

```bash
mvn test
```

Testes unitários com JUnit 5, Mockito e AssertJ, sem subir o Spring nem o banco:
`ProdutoTest`, `ProdutoMapperTest`, `SkuUnicoRegraTest` e
`ProdutoServiceImplTest`. O comportamento HTTP de ponta a ponta é conferido
subindo a aplicação e usando os exemplos de `curl` abaixo.

---

## Endpoints

Todos sob `/api/v1`.

| Método | Rota | Resposta de sucesso |
|---|---|---|
| `POST` | `/produtos` | `201` + cabeçalho `Location` |
| `GET` | `/produtos/{id}` | `200` |
| `GET` | `/produtos?nome=&page=&size=&sort=` | `200` com a página |
| `PUT` | `/produtos/{id}` | `200` |
| `DELETE` | `/produtos/{id}` | `204` |

**Regras:**
- `sku` (até 40 caracteres) é único no catálogo.
- `nome` é obrigatório (até 120); `descricao` é opcional (até 500).
- `preco` é maior que zero e tem no máximo duas casas decimais; `10.999` é
  recusado, e não arredondado.
- Todo produto nasce ativo. No `PUT`, todos os campos são enviados, inclusive
  `ativo`, que passa a ser obrigatório.
- A busca `?nome=` encontra o trecho em qualquer posição do nome e ignora
  maiúsculas, minúsculas e acentos: `cafe` encontra "Café Torrado" e
  "CAFETEIRA".
- Página padrão de 20 itens e teto de 100: `?size=100000` devolve 100.

---

## Exemplos

```bash
# Cadastrar
curl -i -X POST http://localhost:8080/api/v1/produtos \
  -H 'Content-Type: application/json' \
  -d '{"sku":"CAF-001","nome":"Café Torrado","descricao":"Pacote 500g","preco":29.90}'

# Consultar
curl http://localhost:8080/api/v1/produtos/1

# Listar, buscando por nome, 10 por página, ordenado por preço
curl 'http://localhost:8080/api/v1/produtos?nome=cafe&size=10&sort=preco,desc'

# Atualizar (substitui todos os campos)
curl -X PUT http://localhost:8080/api/v1/produtos/1 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"CAF-001","nome":"Café Torrado Especial","preco":34.90,"ativo":false}'

# Excluir
curl -i -X DELETE http://localhost:8080/api/v1/produtos/1
```

Resposta de um produto:

```json
{"id":1,"sku":"CAF-001","nome":"Café Torrado","descricao":"Pacote 500g","preco":29.90,"ativo":true}
```

Resposta de uma listagem:

```json
{"conteudo":[ ... ],"pagina":0,"tamanho":20,"totalElementos":1,"totalPaginas":1}
```

---

## Erros

Todo erro sai no formato da RFC 9457, com `Content-Type: application/problem+json`:

```json
{
  "type": "urn:catalogo:erro:validacao",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "A requisição contém campos inválidos.",
  "instance": "/api/v1/produtos",
  "timestamp": "2026-09-29T12:00:00Z",
  "campos": [
    {"campo": "preco", "mensagem": "deve ser maior que 0"}
  ]
}
```

| Código | Quando |
|---|---|
| `400` | O pedido está errado: campo inválido ou faltando, JSON malformado, id não numérico |
| `404` | O produto não existe |
| `409` | O pedido está certo, mas conflita com o estado atual: SKU já usado por outro produto |
| `500` | Erro inesperado; o rastro de pilha vai para o log, nunca para a resposta |

---

## Evoluções naturais

1. **Testes de controller e de integração.** Os testes atuais são unitários.
   O próximo passo é `@WebMvcTest` para a camada HTTP e `@SpringBootTest` com
   Testcontainers para rodar contra um PostgreSQL de verdade.
2. **Controle de concorrência.** Duas atualizações simultâneas do mesmo produto
   se sobrescrevem (vence a última). Um campo `@Version` faria a segunda receber
   `409`.
3. **Índice para a busca por nome.** A busca lê a tabela inteira, o que é
   irrelevante para um catálogo pequeno. Com muitos produtos, o caminho é um
   índice GIN com a extensão `pg_trgm`.
4. **Categorias.** O recurso existia em versões anteriores do projeto e pode
   voltar como um segundo recurso, com vínculo obrigatório do produto (e com o
   código organizado por funcionalidade: `produto/`, `categoria/`).
5. **Arquitetura hexagonal.** Quando o domínio crescer a ponto de valer
   isolá-lo do JPA e do Spring, o passo seguinte é separar a entidade de negócio
   da entidade de persistência e declarar o repositório como uma porta.

---

## Stack

| Componente | Versão | Papel |
|---|---|---|
| Java | 21 | LTS |
| Spring Boot | 4.1.1 | Framework base |
| Spring Data JPA / Hibernate | via Boot | Persistência |
| PostgreSQL | 17 | Banco (extensão `unaccent` para a busca) |
| Flyway | via Boot | Migrações versionadas |
| springdoc-openapi | 3.1.1 | Documentação OpenAPI |
| JUnit 5 / Mockito / AssertJ | via Boot | Testes unitários |

**Nota sobre o Spring Boot 4:** as auto-configurações foram divididas em módulos
próprios. Declarar apenas `flyway-core` compila, mas as migrações nunca rodam — é
preciso `spring-boot-starter-flyway`.
