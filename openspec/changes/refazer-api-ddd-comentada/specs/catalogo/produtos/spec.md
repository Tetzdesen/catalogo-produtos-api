## Purpose

Mantém o registro dos itens do catálogo — identificação externa (SKU), nome, descrição, preço, situação e categoria — e oferece consulta filtrável para que clientes da API localizem produtos sem varrer a base inteira.

## ADDED Requirements

### Requirement: Cadastro de produto
O sistema SHALL permitir criar um produto informando SKU, nome, descrição (opcional), preço e identificador de categoria. O SKU SHALL ser único no catálogo. O preço SHALL ser maior que zero e ter no máximo duas casas decimais. O produto SHALL ser criado como ativo.

#### Scenario: Produto criado com sucesso
- **WHEN** uma requisição de criação é enviada com SKU inédito, nome válido, preço positivo e categoria existente
- **THEN** o sistema persiste o produto como ativo, responde `201 Created` com o corpo do produto criado e o cabeçalho `Location` apontando para o recurso

#### Scenario: Campos obrigatórios inválidos
- **WHEN** uma requisição de criação omite SKU, nome, preço ou categoria, ou envia preço menor ou igual a zero
- **THEN** o sistema rejeita com `400 Bad Request` no formato `application/problem+json`, listando cada campo inválido com sua mensagem

#### Scenario: Preço com mais de duas casas decimais
- **WHEN** uma requisição de criação envia preço com três ou mais casas decimais
- **THEN** o sistema rejeita com `400 Bad Request`, sem arredondar o valor silenciosamente

#### Scenario: SKU já cadastrado
- **WHEN** uma requisição de criação usa um SKU que já existe
- **THEN** o sistema rejeita com `409 Conflict` informando que o SKU já está em uso

#### Scenario: Categoria inexistente
- **WHEN** uma requisição de criação referencia uma categoria que não existe
- **THEN** o sistema rejeita com `404 Not Found` informando que a categoria não foi encontrada

### Requirement: Consulta de produto por identificador
O sistema SHALL expor a consulta individual de um produto, retornando seus dados atuais e a identificação da categoria a que pertence.

#### Scenario: Produto encontrado
- **WHEN** a consulta é feita para um identificador existente
- **THEN** o sistema responde `200 OK` com identificador, SKU, nome, descrição, preço, situação (ativo ou inativo), identificador da categoria e nome da categoria

#### Scenario: Produto inexistente
- **WHEN** a consulta é feita para um identificador que não existe
- **THEN** o sistema responde `404 Not Found` no formato `application/problem+json`

### Requirement: Listagem paginada com filtros simples
O sistema SHALL expor a listagem de produtos de forma paginada, aceitando dois filtros opcionais e combináveis: trecho do nome (sem distinção de caixa) e identificador de categoria. O tamanho de página SHALL ser limitado a um teto máximo definido pelo sistema.

#### Scenario: Listagem sem filtros
- **WHEN** a listagem é solicitada sem filtros
- **THEN** o sistema responde `200 OK` com a página de produtos e os metadados de paginação: conteúdo, número da página, tamanho, total de elementos e total de páginas

#### Scenario: Filtro por trecho do nome
- **WHEN** a listagem é solicitada informando um trecho do nome
- **THEN** o sistema retorna apenas os produtos cujo nome contém aquele trecho, sem distinção entre maiúsculas e minúsculas

#### Scenario: Filtro por categoria
- **WHEN** a listagem é solicitada informando um identificador de categoria
- **THEN** o sistema retorna apenas os produtos vinculados àquela categoria

#### Scenario: Filtros combinados
- **WHEN** a listagem é solicitada informando trecho de nome e categoria simultaneamente
- **THEN** o sistema retorna apenas os produtos que satisfazem os dois filtros

#### Scenario: Nenhum produto corresponde aos filtros
- **WHEN** os filtros informados não correspondem a nenhum produto
- **THEN** o sistema responde `200 OK` com conteúdo vazio e total de elementos igual a zero

#### Scenario: Tamanho de página acima do teto
- **WHEN** a listagem é solicitada com tamanho de página acima do teto permitido
- **THEN** o sistema atende a requisição aplicando o teto, sem erro, e informa no metadado de tamanho o valor efetivamente aplicado

### Requirement: Atualização de produto
O sistema SHALL permitir atualizar nome, descrição, preço, situação e categoria de um produto existente, aplicando as mesmas validações do cadastro.

#### Scenario: Atualização válida
- **WHEN** uma atualização é enviada para produto existente com dados válidos e categoria existente
- **THEN** o sistema persiste os novos dados e responde `200 OK` com o produto atualizado

#### Scenario: Alteração da situação
- **WHEN** uma atualização altera a situação do produto de ativo para inativo, ou de inativo para ativo
- **THEN** o sistema registra a nova situação e a reflete nas consultas e listagens subsequentes

#### Scenario: Dados inválidos na atualização
- **WHEN** uma atualização envia nome em branco ou preço menor ou igual a zero
- **THEN** o sistema rejeita com `400 Bad Request` e preserva os dados anteriores do produto

#### Scenario: Categoria inexistente na atualização
- **WHEN** uma atualização referencia uma categoria que não existe
- **THEN** o sistema rejeita com `404 Not Found` e preserva os dados anteriores do produto

#### Scenario: Produto inexistente
- **WHEN** a atualização é enviada para um identificador que não existe
- **THEN** o sistema responde `404 Not Found`

### Requirement: SKU imutável após o cadastro
O sistema SHALL tratar o SKU como identidade externa permanente do produto. Uma tentativa de alterá-lo SHALL ser rejeitada explicitamente, e NOT ignorada em silêncio.

#### Scenario: Tentativa de alterar o SKU
- **WHEN** uma atualização envia um SKU diferente do SKU atual do produto
- **THEN** o sistema rejeita com `409 Conflict` informando que o SKU não pode ser alterado, e preserva o produto inalterado

#### Scenario: Atualização reenviando o mesmo SKU
- **WHEN** uma atualização reenvia exatamente o SKU que o produto já possui
- **THEN** o sistema aceita a atualização normalmente

### Requirement: Exclusão de produto
O sistema SHALL permitir excluir um produto existente pelo seu identificador.

#### Scenario: Produto excluído
- **WHEN** a exclusão é solicitada para um produto existente
- **THEN** o sistema remove o produto e responde `204 No Content`

#### Scenario: Produto inexistente
- **WHEN** a exclusão é solicitada para um identificador que não existe
- **THEN** o sistema responde `404 Not Found`

### Requirement: Contrato uniforme de erros
O sistema SHALL responder todo erro no formato RFC 9457 (`application/problem+json`), incluindo erros gerados pela própria camada HTTP, como corpo JSON malformado ou parâmetro de tipo inválido. A resposta de erro SHALL NOT expor rastro de pilha nem detalhes internos.

#### Scenario: Corpo JSON malformado
- **WHEN** uma requisição de escrita envia um corpo JSON sintaticamente inválido
- **THEN** o sistema responde `400 Bad Request` no formato `application/problem+json`, sem rastro de pilha

#### Scenario: Parâmetro de caminho com tipo inválido
- **WHEN** uma consulta é feita com identificador que não é um número
- **THEN** o sistema responde `400 Bad Request` no formato `application/problem+json`

#### Scenario: Erro inesperado no servidor
- **WHEN** ocorre uma falha não prevista durante o processamento
- **THEN** o sistema responde `500 Internal Server Error` com corpo `application/problem+json` genérico, registrando o detalhe técnico apenas no log do servidor

### Requirement: Documentação interativa do contrato
O sistema SHALL publicar a especificação OpenAPI da API e uma interface interativa de navegação, derivadas do próprio código, de modo que a documentação acompanhe o contrato por construção.

#### Scenario: Especificação OpenAPI disponível
- **WHEN** a especificação OpenAPI é solicitada
- **THEN** o sistema responde `200 OK` com um documento válido descrevendo todos os endpoints de produtos e categorias

#### Scenario: Interface interativa disponível
- **WHEN** a interface de documentação é acessada pelo navegador
- **THEN** o sistema apresenta a lista de endpoints com seus parâmetros, exemplos de corpo e códigos de resposta
