package br.com.catalogo.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Testes da entidade sem Spring e sem banco: {@code Produto} é uma classe Java
 * comum, e as anotações JPA só têm efeito quando um Hibernate as lê.
 */
class ProdutoTest {

    @Test
    void produtoNovoNasceAtivoESemId() {
        Produto produto = Produto.novo("CAF-001", "Café Torrado", "500 g", new BigDecimal("24.90"));

        assertThat(produto.isAtivo()).isTrue();
        assertThat(produto.getId()).isNull();
        assertThat(produto.getSku()).isEqualTo("CAF-001");
        assertThat(produto.getNome()).isEqualTo("Café Torrado");
        assertThat(produto.getDescricao()).isEqualTo("500 g");
        assertThat(produto.getPreco()).isEqualByComparingTo("24.90");
    }

    @Test
    void atualizarSubstituiTodosOsCampos() {
        Produto produto = Produto.novo("CAF-001", "Café Torrado", "500 g", new BigDecimal("24.90"));

        produto.atualizar("CAF-002", "Café Moído", null, new BigDecimal("19.90"), false);

        assertThat(produto.getSku()).isEqualTo("CAF-002");
        assertThat(produto.getNome()).isEqualTo("Café Moído");
        assertThat(produto.getDescricao()).isNull();
        assertThat(produto.getPreco()).isEqualByComparingTo("19.90");
        assertThat(produto.isAtivo()).isFalse();
    }
}
