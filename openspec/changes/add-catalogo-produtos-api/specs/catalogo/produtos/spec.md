## Purpose

Mantém o registro dos itens comercializáveis do catálogo — identificação, preço, categoria e situação — e oferece consulta filtrável para que clientes da API encontrem produtos sem varrer a base inteira.

## ADDED Requirements

### Requirement: Cadastro de produto
O sistema SHALL permitir criar um produto informando SKU, nome, descrição (opcional), preço e categoria. O SKU SHALL ser único no catálogo. O preço SHALL ser maior que zero e ter no máximo duas casas decimais. O produto SHALL ser criado como ativo e com saldo de estoque igual a zero.

#### Scenario: Produto criado com sucesso
- **WHEN** uma requisição de criação é enviada com SKU inédito, nome válido, preço positivo e categoria existente
- **THEN** o sistema persiste o produto com situação ativa e saldo zero, responde `201 Created` com o corpo do produto criado e o cabeçalho `Location` apontando para o recurso

#### Scenario: Campos obrigatórios inválidos
- **WHEN** uma requisição de criação omite SKU, nome, preço ou categoria, ou envia preço menor ou igual a zero
- **THEN** o sistema rejeita com `400 Bad Request` no formato `application/problem+json`, listando cada campo inválido com sua mensagem

#### Scenario: SKU já cadastrado
- **WHEN** uma requisição de criação usa um SKU que já existe
- **THEN** o sistema rejeita com `409 Conflict` informando que o SKU já está em uso

#### Scenario: Categoria inexistente
- **WHEN** uma requisição de criação referencia uma categoria que não existe
- **THEN** o sistema rejeita com `404 Not Found` informando que a categoria não foi encontrada

### Requirement: Consulta de produto por identificador
O sistema SHALL expor a consulta individual de um produto, retornando seus dados atuais incluindo o saldo de estoque e o nome da categoria.

#### Scenario: Produto encontrado
- **WHEN** a consulta é feita para um identificador existente
- **THEN** o sistema responde `200 OK` com id, SKU, nome, descrição, preço, situação, saldo de estoque e dados da categoria

#### Scenario: Produto inexistente
- **WHEN** a consulta é feita para um identificador que não existe
- **THEN** o sistema responde `404 Not Found` no formato `application/problem+json`

### Requirement: Listagem paginada com filtros
O sistema SHALL expor a listagem de produtos de forma paginada e ordenável, aceitando os filtros opcionais e combináveis: trecho do nome (sem distinção de caixa), categoria, preço mínimo, preço máximo e situação (ativo/inativo).

#### Scenario: Listagem sem filtros
- **WHEN** a listagem é solicitada sem filtros
- **THEN** o sistema responde `200 OK` com a página de produtos e os metadados de paginação (número da página, tamanho, total de elementos e total de páginas)

#### Scenario: Filtros combinados
- **WHEN** a listagem é solicitada com trecho de nome, categoria e faixa de preço simultaneamente
- **THEN** o sistema retorna apenas os produtos que satisfazem todos os filtros informados

#### Scenario: Nenhum produto corresponde aos filtros
- **WHEN** os filtros informados não correspondem a nenhum produto
- **THEN** o sistema responde `200 OK` com a lista vazia e total de elementos igual a zero

#### Scenario: Faixa de preço inconsistente
- **WHEN** o preço mínimo informado é maior que o preço máximo
- **THEN** o sistema rejeita com `400 Bad Request` explicando a inconsistência da faixa

### Requirement: Atualização de produto
O sistema SHALL permitir atualizar nome, descrição, preço e categoria de um produto existente, aplicando as mesmas validações do cadastro. O saldo de estoque SHALL NOT ser alterado por esta operação.

#### Scenario: Atualização válida
- **WHEN** uma atualização é enviada para produto existente com dados válidos
- **THEN** o sistema persiste as alterações, mantém o saldo de estoque inalterado e responde `200 OK` com o produto atualizado

#### Scenario: Tentativa de alterar SKU
- **WHEN** a atualização informa um SKU diferente do atual
- **THEN** o sistema rejeita com `409 Conflict` informando que o SKU é imutável

### Requirement: Ativação e inativação de produto
O sistema SHALL permitir inativar e reativar um produto, sendo a inativação a forma de retirar um item do catálogo sem perder seu histórico de movimentações.

#### Scenario: Produto inativado
- **WHEN** a inativação é solicitada para um produto ativo
- **THEN** o sistema marca o produto como inativo e responde `200 OK` com a situação atualizada

#### Scenario: Produto reativado
- **WHEN** a ativação é solicitada para um produto inativo
- **THEN** o sistema marca o produto como ativo e responde `200 OK` com a situação atualizada

#### Scenario: Produto inativo excluído dos resultados padrão de filtro por situação
- **WHEN** a listagem é solicitada com o filtro de situação igual a ativo
- **THEN** produtos inativos não aparecem no resultado

### Requirement: Exclusão de produto sem movimentações
O sistema SHALL permitir excluir definitivamente um produto apenas quando ele não possuir movimentações de estoque registradas; caso possua, a retirada do catálogo SHALL ser feita por inativação.

#### Scenario: Exclusão de produto sem histórico
- **WHEN** a exclusão é solicitada para um produto sem movimentações registradas
- **THEN** o sistema remove o produto e responde `204 No Content`

#### Scenario: Exclusão bloqueada por histórico existente
- **WHEN** a exclusão é solicitada para um produto que possui movimentações registradas
- **THEN** o sistema rejeita com `409 Conflict`, orienta o uso da inativação e não remove nada
