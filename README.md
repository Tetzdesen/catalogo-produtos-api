# catalogo-produtos-api

API REST básica de catálogo de produtos em Spring Boot: um único recurso,
**Produto**, com cadastro, consulta, listagem paginada com busca por nome,
atualização e exclusão.

O código é pequeno de propósito — dá para ler de ponta a ponta em poucos
minutos — e cada arquivo é comentado explicando *por que* está ali.

---

## Como rodar

**Pré-requisitos:** Java 21 e Maven 3.8+. Docker para o banco e o Keycloak.

```bash
# 1. Sobe o PostgreSQL e o Keycloak (o Keycloak leva cerca de um minuto na primeira vez)
docker compose up -d

# 2. Sobe a aplicação (o Flyway cria a tabela sozinho no start)
mvn spring-boot:run
```

- API: `http://localhost:8080/api/v1/produtos`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Spec OpenAPI: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`

O `mvn spring-boot:run` ativa o perfil `dev` (`application-dev.yml`), que traz a
conexão com o banco e com o Keycloak do `docker-compose.yml`. Rodando pela IDE,
ative o perfil com `SPRING_PROFILES_ACTIVE=dev`.

### Autenticação

Consultar (`GET`) é público. Cadastrar, atualizar e excluir exigem um JWT no
cabeçalho `Authorization: Bearer <token>`. A API não emite tokens: ela os valida
(assinatura, emissor, validade e audiência `catalogo-api`) contra um provedor
OAuth 2.0 / OpenID Connect.

Em desenvolvimento, o provedor é o Keycloak do `docker-compose.yml`, com o realm
`catalogo` importado de `keycloak/realm-catalogo.json`. Ele já traz o cliente
`catalogo-cli`, que obtém tokens por *client credentials*:

```bash
TOKEN=$(curl -s http://localhost:8180/realms/catalogo/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=catalogo-cli -d client_secret=catalogo-cli-segredo \
  | jq -r .access_token)
```

O token vale 5 minutos. No Swagger UI, cole-o no botão **Authorize**.

> O segredo `catalogo-cli-segredo` e o admin `admin/admin` do Keycloak existem só
> para desenvolvimento. O Keycloak em `start-dev` guarda tudo em memória e
> recria o realm a cada subida.

> **Já tem um PostgreSQL instalado na máquina?** Ele provavelmente ocupa a porta
> 5432, e o contêiner vai falhar ao subir. Ou você para o serviço local, ou usa o
> banco que já tem: crie o papel e o banco (`CREATE USER catalogo WITH PASSWORD
> 'catalogo'` e `CREATE DATABASE catalogo OWNER catalogo`) e pule o contêiner do
> PostgreSQL, subindo só o Keycloak com `docker compose up -d keycloak`. O
> usuário precisa ser **dono** do banco, porque a migração cria a extensão
> `unaccent`. A conexão também pode ser trocada pelas variáveis
> `DB_URL`, `DB_USER` e `DB_PASSWORD`.

> **Vindo de uma versão anterior deste projeto?** A migração `V1` foi reescrita, e
> o Flyway guarda um *checksum* dela. Em uma base já criada, o start vai falhar
> acusando a divergência. Recrie o banco do zero:
>
> ```bash
> docker compose down -v && docker compose up -d
> ```

---

## Como subir em produção

Fora do perfil `dev`, **nada tem valor padrão**. Estas variáveis são
obrigatórias, e sem elas a aplicação não sobe:

| Variável | O que é |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão com o PostgreSQL |
| `JWT_ISSUER_URI` | Emissor dos tokens, ex.: `https://auth.empresa.com/realms/catalogo`. A API busca nele as chaves públicas e recusa token de outro emissor |
| `JWT_AUDIENCE` | O `aud` que o token precisa ter (ex.: `catalogo-api`). Recusa tokens que o mesmo provedor emitiu para outras aplicações |

Faltando `JWT_*`, o erro é direto (`Could not resolve placeholder
'JWT_ISSUER_URI'`). Faltando `DB_*`, é menos claro (`'url' must start with
"jdbc"` ou falha de autenticação no banco). Em todos os casos é proposital:
a aplicação nunca sobe com a configuração de desenvolvimento.

```bash
docker build -t catalogo-produtos-api .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<host>:5432/catalogo \
  -e DB_USER=<usuario> -e DB_PASSWORD=<senha> \
  -e JWT_ISSUER_URI=https://<provedor>/realms/catalogo -e JWT_AUDIENCE=catalogo-api \
  catalogo-produtos-api
```

- O contêiner roda com um usuário sem privilégios.
- Para o orquestrador: `/actuator/health/liveness` e `/actuator/health/readiness`.
  Nenhum outro endpoint do actuator fica exposto.
- O usuário do banco precisa poder criar a extensão `unaccent` na primeira
  migração. Em bancos gerenciados (RDS, Cloud SQL etc.), confira as permissões.
- No provedor, o cliente que chama a API precisa receber `catalogo-api` no
  `aud` do token. No Keycloak, isso é um *audience mapper*, como o de
  `keycloak/realm-catalogo.json`.
- Qualquer token válido do provedor com essa audiência pode escrever no
  catálogo. Não há distinção por papel ou *scope* (ver "Evoluções naturais").

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
└── config/         ApiConfig (prefixo /api/v1), SegurancaConfig (quem pode chamar o quê), OpenApiConfig
src/main/resources/
├── application.yml       configuração comum (sem credenciais)
├── application-dev.yml   conexão com o banco local, só no perfil dev
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
`ProdutoServiceImplTest`.

`SegurancaTest` sobe só a camada web (`@WebMvcTest`, sem banco e sem provedor) e
confere as regras de acesso: `GET` público, escrita com `401` sem token e
aceita com token.

O resto do comportamento HTTP de ponta a ponta é conferido subindo a aplicação
e usando os exemplos de `curl` abaixo.

---

## Endpoints

Todos sob `/api/v1`.

| Método | Rota | Token | Resposta de sucesso |
|---|---|---|---|
| `POST` | `/produtos` | sim | `201` + cabeçalho `Location` |
| `GET` | `/produtos/{id}` | não | `200` |
| `GET` | `/produtos?nome=&page=&size=&sort=` | não | `200` com a página |
| `PUT` | `/produtos/{id}` | sim | `200` |
| `DELETE` | `/produtos/{id}` | sim | `204` |

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
- `?sort=` aceita `id`, `sku`, `nome`, `descricao`, `preco` e `ativo`. Qualquer
  outro campo devolve `400`.

---

## Exemplos

```bash
# Cadastrar ($TOKEN obtido como em "Autenticação")
curl -i -X POST http://localhost:8080/api/v1/produtos \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"CAF-001","nome":"Café Torrado","descricao":"Pacote 500g","preco":29.90}'

# Consultar
curl http://localhost:8080/api/v1/produtos/1

# Listar, buscando por nome, 10 por página, ordenado por preço
curl 'http://localhost:8080/api/v1/produtos?nome=cafe&size=10&sort=preco,desc'

# Atualizar (substitui todos os campos)
curl -X PUT http://localhost:8080/api/v1/produtos/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"CAF-001","nome":"Café Torrado Especial","preco":34.90,"ativo":false}'

# Excluir
curl -i -X DELETE http://localhost:8080/api/v1/produtos/1 -H "Authorization: Bearer $TOKEN"
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
| `400` | O pedido está errado: campo inválido ou faltando, JSON malformado, id não numérico, ordenação por campo desconhecido |
| `401` | Escrita sem token, ou com token expirado, adulterado, de outro emissor ou de outra audiência. O cabeçalho `WWW-Authenticate` diz o motivo |
| `403` | Token válido, mas sem permissão para a operação (reservado para quando houver papéis ou *scopes*) |
| `404` | O produto não existe |
| `409` | O pedido está certo, mas conflita com o estado atual: SKU já usado por outro produto |
| `500` | Erro inesperado; o rastro de pilha vai para o log, nunca para a resposta |

---

## Evoluções naturais

1. **Testes de controller e de integração.** Fora o `SegurancaTest`, os testes
   são unitários. O próximo passo é cobrir o resto da camada HTTP com
   `@WebMvcTest` e usar `@SpringBootTest` com Testcontainers para rodar contra
   um PostgreSQL de verdade.
2. **Permissões por papel ou *scope*.** Hoje qualquer token válido escreve. Para
   separar, por exemplo, quem cadastra de quem só exclui, a `SegurancaConfig`
   passa a exigir `hasAuthority("SCOPE_catalogo:escrita")` nas rotas certas, e o
   `403` começa a ser usado.
3. **Controle de concorrência.** Duas atualizações simultâneas do mesmo produto
   se sobrescrevem (vence a última). Um campo `@Version` faria a segunda receber
   `409`.
4. **Índice para a busca por nome.** A busca lê a tabela inteira, o que é
   irrelevante para um catálogo pequeno. Com muitos produtos, o caminho é um
   índice GIN com a extensão `pg_trgm`.
5. **Categorias.** O recurso existia em versões anteriores do projeto e pode
   voltar como um segundo recurso, com vínculo obrigatório do produto (e com o
   código organizado por funcionalidade: `produto/`, `categoria/`).
6. **Arquitetura hexagonal.** Quando o domínio crescer a ponto de valer
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
| Spring Security (resource server) | via Boot | Validação dos JWT nas rotas de escrita |
| Keycloak | 26.4 | Provedor de identidade, só em desenvolvimento |
| springdoc-openapi | 3.1.1 | Documentação OpenAPI |
| JUnit 5 / Mockito / AssertJ | via Boot | Testes unitários |

**Nota sobre o Spring Boot 4:** as auto-configurações foram divididas em módulos
próprios. Declarar apenas `flyway-core` compila, mas as migrações nunca rodam — é
preciso `spring-boot-starter-flyway`.
