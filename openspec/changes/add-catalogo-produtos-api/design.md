## Context

Repositório vazio, sem código nem convenções estabelecidas — toda decisão aqui é de partida, não de migração. O README existente já fixa a direção da stack (Spring Boot 4, Spring Data JPA, PostgreSQL, Bean Validation, RFC 9457, paginação, JUnit 5 + Mockito, OpenAPI) e esta mudança a materializa. Motivação em `proposal.md — Why`; comportamento exigido em `specs/`.

Três restrições moldam o desenho:

1. **O projeto é também material de referência.** O usuário pediu explicitamente um README explicando cada decisão. Isso favorece soluções idiomáticas e legíveis sobre soluções espertas — cada escolha precisa ser defensável em um parágrafo.
2. **A suíte de testes não pode depender de serviços externos.** Decisão do usuário: PostgreSQL via Docker Compose para rodar a aplicação, H2 em memória nos testes.
3. **Sem autenticação nesta fase.** Decisão do usuário; a API é aberta e a segurança fica documentada como evolução futura.

## Goals / Non-Goals

**Goals:**
- Estrutura em camadas explícita o bastante para servir de modelo, sem abstrações que só se pagam em projetos grandes.
- Saldo de estoque sempre derivável do histórico, com atomicidade garantida no banco.
- Contrato HTTP estável e autodocumentado: erros uniformes, paginação uniforme, OpenAPI gerado do próprio código.
- Suíte de testes que roda com `mvn test` em máquina limpa, sem Docker.

**Non-Goals:**
- Arquitetura hexagonal / ports & adapters, CQRS ou modularização em múltiplos módulos Maven — custo de cerimônia alto demais para três agregados.
- Camada de cache, busca textual avançada (full-text/Elasticsearch) ou projeções materializadas.
- Versionamento evolutivo da API além do prefixo `/api/v1` já reservado.
- Observabilidade além do que o Actuator entrega por padrão.

## Decisions

### 1. Arquitetura em camadas (controller → service → repository), não hexagonal

Quatro pacotes por responsabilidade: `controller` (HTTP), `service` (regra de negócio e transação), `repository` (persistência), `domain`/`entity` (modelo). DTOs de request/response vivem em `dto`, com mapeamento manual em `mapper`.

*Por quê:* é o desenho que qualquer pessoa com Spring Boot no currículo lê sem tradução, e o volume de regras aqui (três agregados, poucas invariantes) não paga o custo de inversão de dependência da arquitetura hexagonal.
*Alternativas descartadas:* hexagonal/clean (cerimônia desproporcional); pacote por feature (bom para times grandes, mas dilui o valor didático da separação por camada neste tamanho).

### 2. DTOs separados das entidades, com mapeamento manual

Entidades JPA nunca cruzam a fronteira HTTP. `ProdutoRequest`/`ProdutoResponse` e equivalentes são `record`s Java.

*Por quê:* expor entidade acopla o contrato público ao schema do banco e abre a porta para mass assignment (cliente enviando `id` ou `saldoEstoque` em um POST). `record` dá imutabilidade e `equals` de graça.
*Alternativas descartadas:* MapStruct — bom em projetos com dezenas de mapeamentos, mas adiciona geração de código e uma camada a explicar; com três agregados, mappers manuais são mais transparentes. Expor entidade direto: descartado pelos motivos acima.

### 3. Estoque como registro de movimentações, com saldo desnormalizado no produto

`MovimentacaoEstoque` é append-only (tipo, quantidade, saldoResultante, motivo, dataHora); `Produto.saldoEstoque` guarda o saldo corrente. Os dois são escritos na mesma transação.

*Por quê:* só o saldo responde "quantos tem", mas não responde "por quê" — auditoria e conferência de inventário exigem histórico. Só o histórico responde ambas, mas obriga a somar N linhas a cada leitura de produto. Manter o saldo desnormalizado torna a leitura O(1) e o histórico continua sendo a fonte de verdade para reconciliação.
*Alternativas descartadas:* event sourcing completo (complexidade de projeções e replay sem demanda que a justifique); apenas campo de quantidade editável (perde auditoria — foi a alternativa explicitamente preterida na escolha de escopo).

### 4. Concorrência: bloqueio pessimista na linha do produto durante a movimentação

O `MovimentacaoService` carrega o produto com `@Lock(PESSIMISTIC_WRITE)` dentro da transação que grava a movimentação.

*Por quê:* a invariante "saldo nunca negativo" é um read-modify-write; sem bloqueio, duas saídas concorrentes podem ambas ler saldo 10 e ambas debitar 8. Pessimista foi escolhido sobre otimista porque a transação é curtíssima (um SELECT, um INSERT, um UPDATE) e porque, com `@Version`, o cliente receberia `409` em disputas legítimas — ruim para um endpoint de alta frequência como baixa de estoque.
*Alternativas descartadas:* bloqueio otimista com `@Version` (mais erros visíveis ao cliente); `UPDATE ... SET saldo = saldo - :qtd WHERE saldo >= :qtd` atômico (eficiente, mas espalha regra de negócio no SQL e dificulta mensagem de erro rica).
*Trade-off aceito:* contenção sob carga alta no mesmo produto; documentado como ponto de evolução.

### 5. Erros no padrão RFC 9457 via `ProblemDetail` + `@RestControllerAdvice`

Um único handler global converte `MethodArgumentNotValidException` → `400` com lista de campos, `RecursoNaoEncontradoException` → `404`, `RegraDeNegocioException` → `409`, e o resto → `500` sem vazar stack trace. Respostas em `application/problem+json`.

*Por quê:* é o formato padronizado que o Spring já suporta nativamente (`ProblemDetail`), então o cliente aprende uma única forma de ler erro. Centralizar no advice mantém os controllers com um caminho feliz só.
*Alternativas descartadas:* formato de erro próprio (reinventa um padrão existente); `try/catch` por controller (duplicação e divergência inevitável).

### 6. Filtros de produto via Specification (Criteria API)

`ProdutoSpecifications` compõe predicados opcionais (nome, categoria, faixa de preço, situação) e o repositório estende `JpaSpecificationExecutor`.

*Por quê:* os filtros são combináveis e opcionais; com derived queries seria uma explosão combinatória de métodos, e com JPQL exigiria concatenação de string condicional. Specification compõe só o que veio preenchido, com tipagem.
*Alternativas descartadas:* Querydsl (mais ergonômico, mas exige geração de código e uma dependência a mais para ganho marginal neste tamanho); múltiplos derived query methods (não escala com 5 filtros).

### 7. Paginação com `Pageable` do Spring Data, envelopada em um DTO próprio

Controllers recebem `Pageable` e devolvem um `PaginaResponse<T>` com `conteudo`, `pagina`, `tamanho`, `totalElementos`, `totalPaginas`.

*Por quê:* `Page` serializado direto produz um JSON instável entre versões do Spring (e o próprio framework emite aviso sobre isso). Um envelope próprio congela o contrato. Tamanho máximo de página limitado para evitar `?size=100000`.
*Alternativas descartadas:* serializar `Page` direto (contrato frágil); paginação por cursor (melhor para listas grandes, desnecessária aqui e mais difícil de explicar).

### 8. Schema versionado com Flyway, `ddl-auto: validate`

Migrações SQL versionadas em `db/migration`; o Hibernate valida o schema no start, nunca o cria.

*Por quê:* `ddl-auto: update` é silenciosamente destrutivo e não versiona nada — o schema de produção vira um efeito colateral do código. Com Flyway o schema é revisável em PR e reproduzível. `validate` transforma divergência entre entidade e tabela em falha no start, não em erro em produção.
*Alternativas descartadas:* `ddl-auto: update` (conveniência que se paga caro); Liquibase (equivalente; Flyway escolhido por SQL puro, mais fácil de ler para quem conhece o banco).

### 9. H2 nos testes, PostgreSQL via Docker Compose para rodar

Perfil `test` usa H2 em modo de compatibilidade PostgreSQL, com o mesmo Flyway rodando as mesmas migrações.

*Por quê:* `mvn test` precisa funcionar em CI e em máquina sem Docker. Rodando as migrações reais no H2, divergências grosseiras de DDL aparecem no teste.
*Alternativas descartadas:* Testcontainers (mais fiel — pega dialeto, tipos e comportamento de lock reais — mas exige Docker na suíte; registrado como evolução recomendada no README); H2 também em desenvolvimento (esconderia diferenças de dialeto até a produção).
*Risco assumido:* o bloqueio pessimista da decisão 4 não é exercitado com fidelidade no H2 — o teste de concorrência verifica a regra de saldo, não o comportamento de lock do PostgreSQL.

### 10. Testes em dois níveis: unitário de service com Mockito, integração de controller com MockMvc

Services testados isoladamente com repositórios mockados; controllers testados com `@SpringBootTest` + MockMvc contra H2, exercitando serialização, validação e o handler de erros.

*Por quê:* as regras de negócio (saldo negativo, SKU duplicado, categoria em uso) são baratas de cobrir no nível de service; já o formato de erro RFC 9457, os códigos HTTP e o envelope de paginação só se provam com o stack HTTP de pé.
*Alternativas descartadas:* só testes de integração (lentos e ruins para cobrir casos de borda); só unitários (não provam o contrato HTTP, que é justamente o que a spec descreve).

### 11. Exclusão restritiva em vez de cascata

Excluir categoria com produtos vinculados → `409`. Excluir produto com movimentações → `409`, com orientação para inativar.

*Por quê:* cascata apaga histórico financeiro/operacional silenciosamente. Falhar explicitamente e oferecer a inativação preserva auditoria e dá ao cliente uma mensagem acionável.
*Alternativas descartadas:* `ON DELETE CASCADE` (perda de histórico); soft delete em tudo (mais estado a gerenciar do que o necessário — a inativação já cobre o caso real do produto).

### 12. Documentação OpenAPI gerada por springdoc, não escrita à mão

*Por quê:* spec escrita à mão diverge do código na primeira semana. Gerada a partir de controllers e DTOs anotados, ela acompanha o código por construção.
*Alternativas descartadas:* contract-first com arquivo OpenAPI versionado e geração de stubs — superior quando há consumidores externos negociando contrato antes da implementação; excessivo aqui.

## Risks / Trade-offs

- **Divergência de comportamento H2 vs. PostgreSQL** (lock pessimista, tipos numéricos, `ILIKE`) → migrações idênticas nos dois, modo de compatibilidade PostgreSQL no H2, e Testcontainers indicado no README como próximo passo.
- **Contenção no bloqueio pessimista sob carga concentrada no mesmo produto** → transação mantida mínima (sem I/O externo dentro dela); alternativa de `UPDATE` condicional registrada no README como evolução.
- **Saldo desnormalizado pode divergir do histórico** se alguém escrever no banco fora da aplicação → `saldoResultante` gravado em cada movimentação permite auditar e reconciliar; a atualização de saldo só existe dentro do service transacional.
- **API sem autenticação** → escopo decidido pelo usuário; README declara o estado explicitamente e aponta Spring Security + JWT como próximo passo, para que ninguém a exponha achando que está protegida.
- **springdoc-openapi precisa de versão compatível com Spring Boot 4** → confirmar a versão compatível ao adicionar a dependência; se não houver release estável no momento da implementação, a alternativa é fixar Spring Boot 3.5.x (LTS) e registrar o motivo no README.
- **Precisão monetária** → preço em `BigDecimal` com coluna `NUMERIC(12,2)`; `double` para dinheiro fica proibido por convenção documentada.

## Migration Plan

Projeto novo, sem dados a migrar. Passos de execução local: `docker-compose up -d` sobe o PostgreSQL → `mvn spring-boot:run` aplica as migrações Flyway no start → Swagger UI disponível em `/swagger-ui.html`. Rollback é descartar o container e o volume; não há estado anterior a preservar.

## Open Questions

- Nenhuma que bloqueie a implementação. A única verificação pendente — versão do springdoc compatível com Spring Boot 4 — está coberta na primeira tarefa de setup e tem plano B registrado nos riscos.
