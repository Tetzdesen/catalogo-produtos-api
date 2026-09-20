## Purpose

Organiza o catálogo em categorias nomeadas, permitindo que produtos sejam agrupados e filtrados por área de negócio, e garantindo que uma categoria em uso não desapareça sem aviso.

## ADDED Requirements

### Requirement: Cadastro de categoria
O sistema SHALL permitir criar uma categoria informando nome (obrigatório, 2 a 60 caracteres) e descrição (opcional, até 255 caracteres). O nome SHALL ser único, ignorando diferenças de caixa e espaços nas extremidades.

#### Scenario: Categoria criada com sucesso
- **WHEN** uma requisição de criação é enviada com nome válido e ainda não utilizado
- **THEN** o sistema persiste a categoria, responde `201 Created` com o corpo da categoria criada (id, nome, descrição) e o cabeçalho `Location` apontando para o recurso

#### Scenario: Nome ausente ou fora do tamanho permitido
- **WHEN** uma requisição de criação é enviada sem nome ou com nome fora da faixa de 2 a 60 caracteres
- **THEN** o sistema rejeita com `400 Bad Request` no formato `application/problem+json`, listando o campo inválido e a mensagem correspondente

#### Scenario: Nome duplicado
- **WHEN** uma requisição de criação usa um nome que já existe (comparação sem distinção de caixa)
- **THEN** o sistema rejeita com `409 Conflict` e uma mensagem indicando que já existe categoria com esse nome

### Requirement: Consulta de categorias
O sistema SHALL expor a listagem paginada de categorias e a consulta individual por identificador.

#### Scenario: Listagem paginada
- **WHEN** a listagem é solicitada com parâmetros de página e tamanho válidos
- **THEN** o sistema responde `200 OK` com o conteúdo da página e os metadados de paginação (número da página, tamanho, total de elementos e total de páginas)

#### Scenario: Consulta por identificador existente
- **WHEN** a consulta é feita para um identificador existente
- **THEN** o sistema responde `200 OK` com os dados da categoria

#### Scenario: Consulta por identificador inexistente
- **WHEN** a consulta é feita para um identificador que não existe
- **THEN** o sistema responde `404 Not Found` no formato `application/problem+json`

### Requirement: Atualização de categoria
O sistema SHALL permitir atualizar nome e descrição de uma categoria existente, aplicando as mesmas validações do cadastro.

#### Scenario: Atualização válida
- **WHEN** uma atualização é enviada para categoria existente com dados válidos
- **THEN** o sistema persiste as alterações e responde `200 OK` com a categoria atualizada

#### Scenario: Atualização para nome já usado por outra categoria
- **WHEN** a atualização define um nome que pertence a outra categoria
- **THEN** o sistema rejeita com `409 Conflict` e mantém os dados anteriores inalterados

### Requirement: Exclusão de categoria sem produtos vinculados
O sistema SHALL permitir excluir uma categoria apenas quando nenhum produto estiver vinculado a ela, preservando a integridade do catálogo.

#### Scenario: Exclusão de categoria sem produtos
- **WHEN** a exclusão é solicitada para uma categoria sem produtos vinculados
- **THEN** o sistema remove a categoria e responde `204 No Content`

#### Scenario: Exclusão bloqueada por produtos vinculados
- **WHEN** a exclusão é solicitada para uma categoria que possui ao menos um produto vinculado
- **THEN** o sistema rejeita com `409 Conflict`, informa que a categoria está em uso e não remove nada
