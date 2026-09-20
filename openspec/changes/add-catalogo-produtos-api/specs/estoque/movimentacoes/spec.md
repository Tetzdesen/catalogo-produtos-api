## Purpose

Controla a quantidade disponível de cada produto por meio de um registro append-only de entradas, saídas e ajustes, de modo que o saldo atual seja sempre explicável pelo histórico que o produziu.

## ADDED Requirements

### Requirement: Registro de entrada de estoque
O sistema SHALL permitir registrar uma entrada de estoque para um produto existente, informando quantidade inteira maior que zero e motivo (obrigatório, até 120 caracteres). A entrada SHALL somar a quantidade ao saldo do produto na mesma transação em que a movimentação é gravada.

#### Scenario: Entrada registrada com sucesso
- **WHEN** uma entrada de 10 unidades é registrada para um produto com saldo 5
- **THEN** o sistema grava a movimentação do tipo entrada, atualiza o saldo do produto para 15 e responde `201 Created` com a movimentação criada e o saldo resultante

#### Scenario: Quantidade inválida
- **WHEN** uma entrada é registrada com quantidade igual a zero ou negativa
- **THEN** o sistema rejeita com `400 Bad Request` no formato `application/problem+json` e o saldo permanece inalterado

#### Scenario: Produto inexistente
- **WHEN** uma entrada é registrada para um produto que não existe
- **THEN** o sistema responde `404 Not Found` e nada é gravado

### Requirement: Registro de saída de estoque
O sistema SHALL permitir registrar uma saída de estoque para um produto existente, informando quantidade inteira maior que zero e motivo. A saída SHALL subtrair a quantidade do saldo do produto e SHALL NOT permitir que o saldo fique negativo.

#### Scenario: Saída registrada com sucesso
- **WHEN** uma saída de 3 unidades é registrada para um produto com saldo 10
- **THEN** o sistema grava a movimentação do tipo saída, atualiza o saldo para 7 e responde `201 Created` com a movimentação criada e o saldo resultante

#### Scenario: Saída maior que o saldo disponível
- **WHEN** uma saída de 12 unidades é registrada para um produto com saldo 10
- **THEN** o sistema rejeita com `409 Conflict`, informa o saldo disponível, não grava a movimentação e mantém o saldo em 10

#### Scenario: Saída para produto inativo
- **WHEN** uma saída é registrada para um produto inativo
- **THEN** o sistema rejeita com `409 Conflict` informando que o produto está inativo

### Requirement: Registro de ajuste de estoque
O sistema SHALL permitir registrar um ajuste que define o saldo do produto para uma quantidade informada (zero ou maior), acompanhado de motivo obrigatório, para corrigir divergências de inventário. O sistema SHALL registrar a diferença aplicada junto à movimentação.

#### Scenario: Ajuste para cima
- **WHEN** um ajuste define o saldo em 20 para um produto com saldo 12
- **THEN** o sistema grava a movimentação do tipo ajuste com diferença `+8`, atualiza o saldo para 20 e responde `201 Created`

#### Scenario: Ajuste para baixo
- **WHEN** um ajuste define o saldo em 5 para um produto com saldo 12
- **THEN** o sistema grava a movimentação do tipo ajuste com diferença `-7`, atualiza o saldo para 5 e responde `201 Created`

#### Scenario: Ajuste com quantidade negativa
- **WHEN** um ajuste informa quantidade negativa
- **THEN** o sistema rejeita com `400 Bad Request` e o saldo permanece inalterado

#### Scenario: Ajuste sem motivo
- **WHEN** um ajuste é enviado sem motivo
- **THEN** o sistema rejeita com `400 Bad Request` indicando que o motivo é obrigatório

### Requirement: Imutabilidade do histórico de movimentações
Uma movimentação registrada SHALL ser imutável: o sistema SHALL NOT expor operações de alteração ou exclusão de movimentações. Correções SHALL ser feitas registrando uma nova movimentação de ajuste.

#### Scenario: Correção de um lançamento equivocado
- **WHEN** um operador precisa corrigir uma movimentação lançada errada
- **THEN** a única via oferecida pela API é registrar uma nova movimentação de ajuste, e o lançamento original continua visível no histórico

### Requirement: Consulta do histórico de movimentações de um produto
O sistema SHALL expor o histórico de movimentações de um produto de forma paginada, ordenado da mais recente para a mais antiga, com filtro opcional por tipo de movimentação. Cada item SHALL conter tipo, quantidade, saldo resultante, motivo e data/hora do registro.

#### Scenario: Histórico retornado
- **WHEN** o histórico é solicitado para um produto que possui movimentações
- **THEN** o sistema responde `200 OK` com a página de movimentações ordenadas por data decrescente e os metadados de paginação

#### Scenario: Produto sem movimentações
- **WHEN** o histórico é solicitado para um produto existente que nunca foi movimentado
- **THEN** o sistema responde `200 OK` com lista vazia e total de elementos igual a zero

#### Scenario: Filtro por tipo
- **WHEN** o histórico é solicitado com filtro de tipo igual a saída
- **THEN** apenas movimentações de saída são retornadas

#### Scenario: Histórico de produto inexistente
- **WHEN** o histórico é solicitado para um produto que não existe
- **THEN** o sistema responde `404 Not Found` no formato `application/problem+json`

### Requirement: Consistência entre saldo e histórico
O saldo de estoque de um produto SHALL ser sempre igual ao resultado acumulado de suas movimentações, e a atualização do saldo SHALL ocorrer atomicamente com o registro da movimentação.

#### Scenario: Falha durante o registro
- **WHEN** ocorre uma falha ao gravar a movimentação ou ao atualizar o saldo
- **THEN** nenhuma das duas alterações é persistida e o saldo permanece igual ao valor anterior à requisição

#### Scenario: Movimentações concorrentes sobre o mesmo produto
- **WHEN** duas movimentações do mesmo produto são processadas simultaneamente
- **THEN** o saldo final reflete ambas as movimentações sem perda de atualização, ou uma delas é rejeitada com `409 Conflict` para ser reenviada
