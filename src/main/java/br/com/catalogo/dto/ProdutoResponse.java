package br.com.catalogo.dto;

import br.com.catalogo.domain.Produto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Representação de um produto devolvida pela API.
 *
 * <p>Inclui a categoria aninhada para que o cliente não precise de uma segunda
 * requisição só para descobrir o nome dela.
 */
@Schema(description = "Produto do catálogo")
public record ProdutoResponse(Long id,
                              String sku,
                              String nome,
                              String descricao,
                              BigDecimal preco,
                              boolean ativo,
                              int saldoEstoque,
                              CategoriaResponse categoria) {

    /**
     * Só pode ser chamado dentro da transação que carregou o produto: a categoria
     * é LAZY e o acesso a ela dispara o carregamento.
     */
    public static ProdutoResponse de(Produto produto) {
        return new ProdutoResponse(
                produto.getId(),
                produto.getSku(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getPreco(),
                produto.isAtivo(),
                produto.getSaldoEstoque(),
                CategoriaResponse.de(produto.getCategoria()));
    }
}
