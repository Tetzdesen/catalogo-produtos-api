## Purpose

Organiza os produtos do catálogo em grupos nomeados, garantindo que cada categoria tenha nome único e que nenhuma categoria em uso desapareça levando consigo o vínculo dos produtos que a referenciam.

## ADDED Requirements

### Requirement: Cadastro de categoria
O sistema SHALL permitir criar uma categoria informando nome (obrigatório) e descrição (opcional). O nome SHALL ser único no catálogo, sem distinção de caixa, e SHALL ter entre 2 e 60 caracteres.

#### Scenario: Categoria criada com sucesso
- **WHEN** uma requisição de criação é enviada com nome inédito e válido
- **THEN** o sistema persiste a categoria, responde `201 Created` com o corpo da categoria criada (identificador, nome e descrição) e o cabeçalho `Location` apontando para o recurso

#### Scenario: Nome ausente ou fora do tamanho permitido
- **WHEN** uma requisição de criação omite o nome, envia nome em branco, ou envia nome com menos de 2 ou mais de 60 caracteres
- **THEN** o sistema rejeita com `400 Bad Request` no formato `application/problem+json`, indicando o campo inválido e a mensagem correspondente

#### Scenario: Nome já cadastrado
- **WHEN** uma requisição de criação usa um nome já existente, ainda que com diferença de caixa
- **THEN** o sistema rejeita com `409 Conflict` informando que já existe categoria com esse nome

### Requirement: Consulta de categoria por identificador
O sistema SHALL expor a consulta individual de uma categoria pelo seu identificador.

#### Scenario: Categoria encontrada
- **WHEN** a consulta é feita para um identificador existente
- **THEN** o sistema responde `200 OK` com identificador, nome e descrição da categoria

#### Scenario: Categoria inexistente
- **WHEN** a consulta é feita para um identificador que não existe
- **THEN** o sistema responde `404 Not Found` no formato `application/problem+json`

### Requirement: Listagem paginada de categorias
O sistema SHALL expor a listagem de categorias de forma paginada, aceitando número da página e tamanho da página. O tamanho de página SHALL ser limitado a um teto máximo definido pelo sistema, mesmo que o cliente solicite valor maior.

#### Scenario: Listagem padrão
- **WHEN** a listagem é solicitada sem parâmetros
- **THEN** o sistema responde `200 OK` com a página de categorias e os metadados de paginação: conteúdo, número da página, tamanho, total de elementos e total de páginas

#### Scenario: Tamanho de página acima do teto
- **WHEN** a listagem é solicitada com tamanho de página acima do teto permitido
- **THEN** o sistema atende a requisição aplicando o teto, sem erro, e informa no metadado de tamanho o valor efetivamente aplicado

#### Scenario: Catálogo sem categorias
- **WHEN** a listagem é solicitada e não existe nenhuma categoria
- **THEN** o sistema responde `200 OK` com conteúdo vazio e total de elementos igual a zero

### Requirement: Atualização de categoria
O sistema SHALL permitir atualizar nome e descrição de uma categoria existente, aplicando as mesmas validações do cadastro. A unicidade de nome SHALL desconsiderar a própria categoria sendo atualizada.

#### Scenario: Atualização válida
- **WHEN** uma atualização é enviada para categoria existente com nome válido e ainda não usado por outra categoria
- **THEN** o sistema persiste os novos dados e responde `200 OK` com a categoria atualizada

#### Scenario: Atualização mantendo o mesmo nome
- **WHEN** uma atualização reenvia o nome que a própria categoria já possui
- **THEN** o sistema aceita a atualização normalmente, sem acusar conflito de nome

#### Scenario: Nome já usado por outra categoria
- **WHEN** uma atualização tenta usar um nome que pertence a outra categoria
- **THEN** o sistema rejeita com `409 Conflict` informando que já existe categoria com esse nome

#### Scenario: Categoria inexistente
- **WHEN** a atualização é enviada para um identificador que não existe
- **THEN** o sistema responde `404 Not Found`

### Requirement: Exclusão restritiva de categoria
O sistema SHALL permitir excluir uma categoria apenas quando nenhum produto a referenciar. A exclusão SHALL NOT propagar em cascata para produtos.

#### Scenario: Categoria sem produtos vinculados
- **WHEN** a exclusão é solicitada para uma categoria que não possui produtos
- **THEN** o sistema remove a categoria e responde `204 No Content`

#### Scenario: Categoria com produtos vinculados
- **WHEN** a exclusão é solicitada para uma categoria referenciada por ao menos um produto
- **THEN** o sistema rejeita com `409 Conflict`, preserva a categoria e informa que existem produtos vinculados

#### Scenario: Categoria inexistente
- **WHEN** a exclusão é solicitada para um identificador que não existe
- **THEN** o sistema responde `404 Not Found`
