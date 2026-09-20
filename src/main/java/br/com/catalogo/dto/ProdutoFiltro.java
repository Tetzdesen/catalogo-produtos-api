package br.com.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * Filtros opcionais da listagem de produtos.
 *
 * <p>Agrupar os cinco parâmetros em um record evita uma assinatura de controller
 * com meia dúzia de {@code @RequestParam} e permite validar a coerência entre
 * eles em um único lugar. Todos os campos são opcionais: nulo significa "não
 * filtrar por isso".
 *
 * @param nome        trecho do nome, comparado sem distinção de caixa
 * @param categoriaId categoria exata
 * @param precoMinimo limite inferior de preço, inclusive
 * @param precoMaximo limite superior de preço, inclusive
 * @param ativo       situação do produto
 */
@Schema(description = "Filtros da listagem de produtos")
public record ProdutoFiltro(String nome,
                            Long categoriaId,
                            BigDecimal precoMinimo,
                            BigDecimal precoMaximo,
                            Boolean ativo) {

    /**
     * Uma faixa invertida não retorna "nenhum resultado" — é um erro do cliente,
     * e responder {@code 400} evita que ele interprete a lista vazia como ausência
     * de produtos.
     */
    public boolean faixaDePrecoInvertida() {
        return precoMinimo != null && precoMaximo != null && precoMinimo.compareTo(precoMaximo) > 0;
    }
}
