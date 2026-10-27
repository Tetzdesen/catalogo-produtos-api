package br.com.catalogo.dto;

import java.math.BigDecimal;

/**
 * O produto como o cliente da API o recebe.
 *
 * <p>Um tipo separado da entidade para que o JSON só mude quando alguém decidir
 * mudar o contrato: um campo novo na tabela não aparece na resposta por acidente.
 *
 * <p>A conversão a partir da entidade fica no {@code ProdutoMapper}: este record
 * só descreve o formato, sem conhecer a entidade.
 */
public record ProdutoResponse(Long id, String sku, String nome, String descricao,
                              BigDecimal preco, boolean ativo) {
}
