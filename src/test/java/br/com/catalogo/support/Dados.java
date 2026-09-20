package br.com.catalogo.support;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.domain.Produto;
import java.math.BigDecimal;

/**
 * Fábricas de objetos de teste.
 *
 * <p>Concentra a construção das entidades para que um campo novo não obrigue a
 * editar dezenas de testes, e para que cada teste mostre apenas o que é relevante
 * para ele.
 */
public final class Dados {

    private Dados() {
    }

    public static Categoria categoria(String nome) {
        return new Categoria(nome, "Descrição de " + nome);
    }

    public static Produto produto(String sku, String nome, String preco, Categoria categoria) {
        return new Produto(sku, nome, "Descrição de " + nome, new BigDecimal(preco), categoria);
    }

    public static Produto produto(String sku, Categoria categoria) {
        return produto(sku, "Produto " + sku, "10.00", categoria);
    }
}
