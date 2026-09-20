# catalogo-produtos-api

API REST para catálogo de produtos e controle de estoque, em Spring Boot.

Este README não documenta apenas **como** usar a API — ele explica **por que** cada
escolha técnica foi feita, e o que foi descartado no caminho. Se você veio pelo
código, a seção [Decisões](#decisões) é o mapa.

---

## O que a API faz

| Recurso | Operações |
|---|---|
| **Categorias** | CRUD completo; exclusão bloqueada quando há produtos vinculados |
| **Produtos** | CRUD, listagem paginada com filtros combináveis, ativação/inativação |
| **Estoque** | Entrada, saída e ajuste de inventário; histórico imutável por produto |

O saldo de estoque **nunca** é editado diretamente: ele só muda através de uma
movimentação registrada, e toda movimentação fica no histórico.

---

## Como rodar

**Pré-requisitos:** Java 21 e Maven 3.8+. Docker apenas para o banco.

```bash
# 1. Sobe o PostgreSQL
docker compose up -d

# 2. Sobe a aplicação (as migrações do Flyway rodam sozinhas no start)
mvn spring-boot:run
```

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Spec OpenAPI: `http://localhost:8080/v3/api-docs`

As credenciais do banco vêm do ambiente (veja `.env.example`); os valores padrão
já correspondem ao serviço do `docker-compose.yml`.

### Testes

```bash
mvn test     # testes unitários (rápidos)
mvn verify   # unitários + integração
```

**A suíte não precisa de Docker nem de banco externo** — roda inteira em H2 em
memória. Isso é uma decisão, não um acaso; veja a [decisão 9](#9-h2-nos-testes-postgresql-para-rodar).

---

## Endpoints

### Categorias

| Método | Caminho | Resposta |
|---|---|---|
| `POST` | `/api/v1/categorias` | `201` + `Location` |
| `GET` | `/api/v1/categorias?page=&size=` | `200` página |
| `GET` | `/api/v1/categorias/{id}` | `200` / `404` |
| `PUT` | `/api/v1/categorias/{id}` | `200` / `404` / `409` |
| `DELETE` | `/api/v1/categorias/{id}` | `204` / `409` se tiver produtos |

### Produtos

| Método | Caminho | Resposta |
|---|---|---|
| `POST` | `/api/v1/produtos` | `201` + `Location` |
| `GET` | `/api/v1/produtos` | `200` página |
| `GET` | `/api/v1/produtos/{id}` | `200` / `404` |
| `PUT` | `/api/v1/produtos/{id}` | `200` / `409` se tentar mudar o SKU |
| `PATCH` | `/api/v1/produtos/{id}/ativacao` | `200` |
| `PATCH` | `/api/v1/produtos/{id}/inativacao` | `200` |
| `DELETE` | `/api/v1/produtos/{id}` | `204` / `409` se tiver histórico |

Filtros da listagem (todos opcionais e combináveis):
`nome` (trecho, sem distinção de caixa), `categoriaId`, `precoMinimo`,
`precoMaximo`, `ativo`.

```
GET /api/v1/produtos?nome=martelo&categoriaId=1&precoMinimo=40&precoMaximo=90&ativo=true&page=0&size=20
```

### Movimentações de estoque

| Método | Caminho | Efeito |
|---|---|---|
| `POST` | `/api/v1/produtos/{id}/movimentacoes/entradas` | Soma ao saldo |
| `POST` | `/api/v1/produtos/{id}/movimentacoes/saidas` | Subtrai do saldo; `409` se insuficiente |
| `POST` | `/api/v1/produtos/{id}/movimentacoes/ajustes` | Define o saldo em valor absoluto |
| `GET` | `/api/v1/produtos/{id}/movimentacoes?tipo=` | Histórico, mais recente primeiro |

Não existe `PUT` nem `DELETE` de movimentação — e isso é intencional
([decisão 3](#3-estoque-como-histórico-de-movimentações-com-saldo-desnormalizado)).

---

## Exemplos

**Criar um produto**

```bash
curl -X POST http://localhost:8080/api/v1/produtos \
  -H 'Content-Type: application/json' \
  -d '{"sku":"MART-001","nome":"Martelo de unha","preco":49.90,"categoriaId":1}'
```

```json
{
  "id": 1,
  "sku": "MART-001",
  "nome": "Martelo de unha",
  "descricao": null,
  "preco": 49.90,
  "ativo": true,
  "saldoEstoque": 0,
  "categoria": { "id": 1, "nome": "Ferramentas", "descricao": "Itens de oficina" }
}
```

**Registrar uma entrada**

```bash
curl -X POST http://localhost:8080/api/v1/produtos/1/movimentacoes/entradas \
  -H 'Content-Type: application/json' \
  -d '{"quantidade":10,"motivo":"Compra do fornecedor XYZ"}'
```

```json
{
  "id": 1,
  "produtoId": 1,
  "tipo": "ENTRADA",
  "quantidade": 10,
  "diferenca": 10,
  "saldoResultante": 10,
  "motivo": "Compra do fornecedor XYZ",
  "dataHora": "2026-09-18T14:32:05.412Z"
}
```

**Listagem paginada** — o envelope é sempre o mesmo:

```json
{
  "conteudo": [ ... ],
  "pagina": 0,
  "tamanho": 20,
  "totalElementos": 137,
  "totalPaginas": 7
}
```

**Erro de validação** (RFC 9457, `application/problem+json`):

```json
{
  "type": "urn:catalogo:erro:validacao",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "A requisição contém campos inválidos.",
  "timestamp": "2026-09-18T14:33:10.882Z",
  "campos": [
    { "campo": "preco", "mensagem": "O preço deve ser maior que zero." },
    { "campo": "sku", "mensagem": "O SKU é obrigatório." }
  ]
}
```

**Erro de conflito** (saldo insuficiente):

```json
{
  "type": "urn:catalogo:erro:conflito",
  "title": "Conflito com o estado atual",
  "status": 409,
  "detail": "Saldo insuficiente para o produto 'MART-001': disponível 10, solicitado 12.",
  "timestamp": "2026-09-18T14:34:02.115Z"
}
```

### Códigos de status

| Código | Quando |
|---|---|
| `400` | O payload está errado — campo faltando, formato inválido, faixa de preço invertida |
| `404` | O recurso referenciado não existe |
| `409` | O payload está certo, mas o estado do servidor não permite — SKU duplicado, saldo insuficiente, categoria em uso |
| `500` | Erro inesperado; a stack trace vai para o log, nunca para a resposta |

A distinção entre `400` e `409` é deliberada: `400` diz "corrija o pedido",
`409` diz "o pedido está correto, mas não cabe no estado atual".

---

## Estrutura

```
src/main/java/br/com/catalogo/
├── config/       ApiConfig (prefixo /api/v1), OpenApiConfig, RelogioConfig
├── controller/   Borda HTTP: recebe, valida, delega, traduz
├── domain/       Entidades JPA com as invariantes do negócio
├── dto/          Records de entrada e saída, com Bean Validation
├── exception/    Exceções de domínio + tratador global (RFC 9457)
├── repository/   Spring Data + Specifications
└── service/      Regras de negócio e fronteira transacional
```

---

## Decisões

Cada seção abaixo responde três perguntas: **o que** foi feito, **por quê**, e
**o que foi descartado**.

### 1. Arquitetura em camadas, não hexagonal

Quatro responsabilidades separadas: `controller` → `service` → `repository` → `domain`.

**Por quê:** é o desenho que qualquer pessoa com Spring Boot no currículo lê sem
tradução. O volume de regras aqui — três agregados, poucas invariantes — não paga
o custo de inversão de dependência da arquitetura hexagonal.

**Descartado:** hexagonal/clean (cerimônia desproporcional ao tamanho do domínio);
organização por feature em vez de por camada (boa para times grandes, mas dilui o
valor didático da separação).

### 2. DTOs separados das entidades

Entidades JPA nunca cruzam a fronteira HTTP. Os DTOs são `record`s, e a conversão
é uma fábrica estática no próprio response (`ProdutoResponse.de(produto)`).

**Por quê:** expor a entidade amarra o contrato público ao schema do banco e abre
a porta para *mass assignment* — um cliente enviando `saldoEstoque` no POST de
produto. `record` dá imutabilidade e `equals` de graça.

**Descartado:** MapStruct (ótimo com dezenas de mapeamentos; aqui adicionaria
geração de código e uma camada a explicar, para três agregados); uma classe
`Mapper` separada por entidade (a fábrica estática mantém a conversão ao lado da
estrutura que ela produz, sem um arquivo extra que só repassa campos).

### 3. Estoque como histórico de movimentações, com saldo desnormalizado

`MovimentacaoEstoque` é *append-only* (tipo, quantidade, diferença, saldo
resultante, motivo, data). `Produto.saldoEstoque` guarda o saldo corrente. Os dois
são escritos na mesma transação.

**Por quê:** só o saldo responde "quantos tem", mas não responde "por quê" — e
essa é a pergunta que se faz quando o inventário não bate. Só o histórico responde
às duas, mas obriga a somar N linhas a cada leitura de produto. Manter o saldo
desnormalizado torna a leitura O(1), e o histórico continua sendo a fonte de
verdade para reconciliação.

Cada movimentação guarda `diferenca` (o que foi somado ao saldo) **e**
`saldoResultante` (quanto ficou). Isso permite auditar linha a linha sem
recalcular nada — e foi o que o teste `ConsistenciaEstoqueIT` verifica.

**Descartado:** *event sourcing* completo (complexidade de projeções e replay sem
demanda que a justifique); um campo de quantidade editável direto no produto
(perde a auditoria — quem mudou, quando e por quê deixam de existir).

**Consequência:** não existe rota para alterar ou excluir movimentação. Corrigir
um lançamento significa registrar um ajuste. A imutabilidade não é uma convenção
escrita na documentação: é a ausência da rota.

### 4. Concorrência: bloqueio pessimista na linha do produto

`ProdutoRepository.findByIdParaAtualizacao` usa `@Lock(PESSIMISTIC_WRITE)`, ou
seja, `SELECT ... FOR UPDATE`, dentro da transação que grava a movimentação.

**Por quê:** toda movimentação é um ler-decidir-gravar. Sem bloqueio, duas saídas
concorrentes leem o mesmo saldo 10, ambas aprovam uma baixa de 8, e o saldo fura.
O teste `saidasConcorrentes` dispara seis saídas simultâneas de 3 unidades sobre
um saldo de 10 e verifica que exatamente três passam.

**Por que pessimista e não otimista:** a transação é curtíssima (um SELECT, um
INSERT, um UPDATE). Com `@Version`, disputas legítimas devolveriam `409` ao
cliente — ruim para um endpoint de alta frequência como baixa de estoque.

**Descartado:** bloqueio otimista com `@Version` (mais erros visíveis ao cliente);
`UPDATE ... SET saldo = saldo - :qtd WHERE saldo >= :qtd` atômico (eficiente, mas
espalha a regra de negócio no SQL e dificulta uma mensagem de erro que informe o
saldo disponível).

**Trade-off aceito:** contenção sob carga alta concentrada no mesmo produto. A
transação é mantida mínima — nenhuma chamada externa dentro dela.

### 5. Erros no padrão RFC 9457

Um único `@RestControllerAdvice` converte exceções em `ProblemDetail`, servido
como `application/problem+json`.

**Por quê:** é o formato padronizado que o Spring já suporta nativamente, então o
cliente aprende uma única forma de ler erro. Centralizar no advice mantém os
controllers com um caminho só — o feliz. Estender `ResponseEntityExceptionHandler`
faz com que até os erros gerados pelo próprio Spring MVC (JSON malformado,
parâmetro ausente) saiam no mesmo formato.

**Descartado:** formato de erro próprio (reinventa um padrão existente);
`try/catch` por controller (duplicação e divergência inevitável).

### 6. Filtros via Specification

`ProdutoSpecifications` compõe predicados opcionais; o repositório estende
`JpaSpecificationExecutor`.

**Por quê:** cinco filtros opcionais e combináveis dão 32 combinações. Com
*derived queries* seria uma explosão de assinaturas; com JPQL, concatenação
condicional de string. Specification compõe só o que veio preenchido, com
tipagem e sem string.

Um detalhe que custou uma rodada de testes: desde o Spring Data JPA 4,
`Specification.allOf` **rejeita elementos nulos**. Filtro ausente devolve
`Specification.unrestricted()`, não `null`.

**Descartado:** Querydsl (mais ergonômico, mas exige geração de código para ganho
marginal neste tamanho); múltiplos *derived query methods* (não escala).

### 7. Paginação com envelope próprio

Controllers recebem `Pageable` e devolvem `PaginaResponse<T>`.

**Por quê:** o JSON do `Page` do Spring Data é um detalhe interno do framework,
muda entre versões, e o próprio Spring avisa contra expô-lo. Um record próprio
congela o contrato.

O teto de página (`spring.data.web.pageable.max-page-size: 100`) impede que um
cliente peça `?size=100000` e derrube a API — verificado por teste.

**Descartado:** serializar `Page` direto (contrato frágil); paginação por cursor
(melhor para listas muito grandes, desnecessária aqui).

### 8. Flyway com `ddl-auto: validate`

Migrações SQL versionadas em `db/migration`; o Hibernate **valida** o schema no
start, nunca o cria.

**Por quê:** `ddl-auto: update` é silenciosamente destrutivo e não versiona nada —
o schema de produção vira efeito colateral do código. Com Flyway, o schema é
revisável em PR e reproduzível. `validate` transforma divergência entre entidade e
tabela em falha no start, e não em erro em produção — foi o que fez o teste de
fumaça pegar problemas de mapeamento antes de qualquer outra coisa.

O SQL da migração é deliberadamente portável (nada exclusivo de um banco), porque
a mesma migração roda no H2 durante os testes.

As invariantes também estão no banco, como `CHECK`: preço positivo e saldo não
negativo. É a última linha de defesa — nem um `UPDATE` manual fura.

**Descartado:** `ddl-auto: update` (conveniência que se paga caro); Liquibase
(equivalente; Flyway escolhido por SQL puro, mais direto de ler).

### 9. H2 nos testes, PostgreSQL para rodar

O perfil `test` usa H2 em modo de compatibilidade PostgreSQL, rodando as mesmas
migrações Flyway.

**Por quê:** `mvn verify` precisa funcionar em CI e em máquina sem Docker. Como as
migrações reais rodam no H2, divergências grosseiras de DDL aparecem no teste.

**Descartado:** Testcontainers (mais fiel — pega dialeto, tipos e comportamento de
lock reais — mas exige Docker para rodar a suíte; é a evolução recomendada, veja
abaixo); H2 também em desenvolvimento (esconderia diferenças de dialeto até a
produção).

**Limitação conhecida:** a unicidade de nome de categoria sem distinção de caixa é
garantida pela aplicação (`existsByNomeIgnoreCase`) com um `UNIQUE (nome)` exato
no banco como apoio. O ideal em PostgreSQL seria um índice funcional sobre
`LOWER(nome)`, que o H2 não suporta — adotá-lo exigiria migrações específicas por
banco. Sob concorrência extrema resta uma janela estreita em que dois nomes
diferindo só na caixa poderiam passar.

### 10. Testes em dois níveis

Services testados isoladamente com Mockito; controllers testados com
`@SpringBootTest` + MockMvc contra H2. Surefire roda `*Test` na fase `test`,
Failsafe roda `*IT` na fase `verify`.

**Por quê:** regras de negócio (saldo negativo, SKU duplicado, categoria em uso)
são baratas de cobrir no nível de service e rodam em milissegundos. Já o formato
RFC 9457, os códigos HTTP e o envelope de paginação só se provam com o stack HTTP
de pé. A separação em duas fases dá retorno em segundos durante o desenvolvimento
e cobertura completa antes do merge.

**Descartado:** só testes de integração (lentos, ruins para casos de borda); só
unitários (não provam o contrato HTTP, que é justamente o que a especificação
descreve).

### 11. Exclusão restritiva, nunca cascata

Categoria com produtos → `409`. Produto com movimentações → `409`, orientando a
inativação.

**Por quê:** cascata apaga histórico operacional silenciosamente. Falhar
explicitamente e oferecer a inativação preserva a auditoria e dá ao cliente uma
mensagem acionável.

**Descartado:** `ON DELETE CASCADE` (perda de histórico); *soft delete* em tudo
(mais estado a gerenciar do que o necessário — a inativação já cobre o caso real).

### 12. OpenAPI gerado, não escrito

springdoc deriva a documentação dos controllers e DTOs.

**Por quê:** spec escrita à mão diverge do código na primeira semana. Gerada, ela
acompanha o código por construção. `DocumentacaoOpenApiIT` transforma uma spec
quebrada em falha de build, em vez de uma descoberta tardia ao abrir o Swagger.

**Descartado:** *contract-first* com arquivo OpenAPI versionado e geração de stubs
— superior quando há consumidores externos negociando o contrato antes da
implementação; excessivo aqui.

### 13. Prefixo `/api/v1` aplicado por configuração

`ApiConfig` aplica o prefixo a todos os controllers do pacote `controller`, via
propriedade `api.base-path`.

**Por quê:** a alternativa é repetir `/api/v1` em cada `@RequestMapping`, o que
convida ao erro de digitação e espalha a decisão de versionamento pelo código. O
Swagger, que está fora desse pacote, continua acessível na raiz.

### 14. Detalhes que valem o parágrafo

- **`BigDecimal` para preço, nunca `double`.** Ponto flutuante binário acumula
  erro; dinheiro não perdoa. A coluna é `NUMERIC(12,2)` e o DTO valida
  `@Digits(integer = 10, fraction = 2)` — preço com três casas é rejeitado na
  borda, não arredondado em silêncio pelo banco.
- **`Instant` para data/hora, não `LocalDateTime`.** O momento de uma movimentação
  é absoluto e não depende do fuso do servidor. A coluna é
  `TIMESTAMP WITH TIME ZONE`.
- **`Clock` injetado como bean.** `Instant.now()` espalhado pelo código torna o
  comportamento temporal impossível de testar sem truques; com o `Clock` injetado,
  o teste passa um relógio fixo.
- **`open-in-view: false`.** A sessão aberta na camada de view esconde N+1 e
  permite *lazy loading* acidental no controller. Desligada, quem precisa do dado
  carrega dentro da transação — por isso os métodos de leitura do
  `ProdutoController` são `@Transactional(readOnly = true)`.
- **`@Enumerated(STRING)`.** Ordinal quebraria o histórico inteiro, em silêncio,
  se alguém reordenasse as constantes do enum.
- **SKU imutável.** Tentar alterá-lo devolve `409` em vez de ser ignorado. É a
  identidade externa do produto; mudá-lo quebraria integrações e o histórico.
- **Injeção por construtor, sem `@Autowired` em campo.** Dependências explícitas,
  campos `final`, e a classe instanciável em um teste unitário sem subir contexto.

### 15. Sem autenticação — e isso é uma escolha declarada

A API é aberta. Não há Spring Security, usuários nem tokens.

**Por quê:** o escopo desta primeira versão é o domínio e o contrato HTTP.
Adicionar autenticação traria entidade de usuário, filtros, emissão de token e
testes de segurança — um segundo projeto dentro deste.

**Isto está documentado aqui justamente para que ninguém a exponha achando que
está protegida.** Antes de qualquer uso real, veja o primeiro item da próxima
seção.

---

## Evoluções naturais

1. **Autenticação e autorização.** Spring Security com JWT, protegendo ao menos as
   operações de escrita. É o pré-requisito para qualquer uso real.
2. **Testcontainers no lugar do H2.** Elimina a divergência de dialeto e permite
   testar o comportamento de lock do PostgreSQL de verdade. Custo: Docker passa a
   ser obrigatório para rodar a suíte.
3. **Índice funcional `LOWER(nome)` em PostgreSQL**, via migração específica de
   banco, fechando a janela de concorrência descrita na decisão 9.
4. **`UPDATE` condicional no lugar do bloqueio pessimista**, se a contenção no
   mesmo produto virar gargalo medido — e só então.
5. **Reserva de estoque** (separar "comprometido" de "disponível"), quando houver
   fluxo de pedido antes da baixa efetiva.
6. **Observabilidade**: Actuator, métricas de negócio (saídas recusadas por saldo
   insuficiente é um bom primeiro indicador) e tracing.

---

## Stack

| Componente | Versão | Papel |
|---|---|---|
| Java | 21 | LTS |
| Spring Boot | 4.1.1 | Framework base |
| Spring Data JPA / Hibernate | via Boot | Persistência |
| PostgreSQL | 17 | Banco de desenvolvimento e produção |
| Flyway | 12.x | Migrações versionadas |
| H2 | 2.x | Banco em memória dos testes |
| springdoc-openapi | 3.1.1 | Documentação OpenAPI |
| JUnit 5 + Mockito + AssertJ | via Boot | Testes |

**Nota sobre o Spring Boot 4:** as auto-configurações foram divididas em módulos
próprios. Declarar apenas `flyway-core` compila, mas as migrações nunca rodam — é
preciso `spring-boot-starter-flyway`. O mesmo vale para as fatias de teste:
`@DataJpaTest` e `@AutoConfigureMockMvc` agora vêm de `spring-boot-data-jpa-test`,
`spring-boot-webmvc-test` e `spring-boot-jdbc-test`, e não mais do
`spring-boot-starter-test` sozinho.
