package br.com.catalogo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.support.Dados;
import br.com.catalogo.support.TesteDeRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@TesteDeRepositorio
@DisplayName("ProdutoRepository")
class ProdutoRepositoryTest {

    @Autowired
    private ProdutoRepository repositorio;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Categoria categoria;
    private Produto produto;

    @BeforeEach
    void preparar() {
        categoria = categoriaRepository.save(Dados.categoria("Ferramentas"));
        produto = repositorio.save(Dados.produto("MART-001", categoria));
    }

    @Test
    @DisplayName("reconhece SKU já cadastrado")
    void existsBySku() {
        assertThat(repositorio.existsBySku("MART-001")).isTrue();
        assertThat(repositorio.existsBySku("INEXISTENTE")).isFalse();
    }

    @Test
    @DisplayName("busca por SKU")
    void findBySku() {
        assertThat(repositorio.findBySku("MART-001"))
                .isPresent()
                .get()
                .extracting(Produto::getId)
                .isEqualTo(produto.getId());
    }

    @Test
    @DisplayName("reconhece categoria em uso por algum produto")
    void existsByCategoriaId() {
        Categoria semProdutos = categoriaRepository.save(Dados.categoria("Jardinagem"));

        assertThat(repositorio.existsByCategoriaId(categoria.getId())).isTrue();
        assertThat(repositorio.existsByCategoriaId(semProdutos.getId())).isFalse();
    }

    @Test
    @DisplayName("carrega o produto com bloqueio para atualização")
    void findByIdParaAtualizacao() {
        assertThat(repositorio.findByIdParaAtualizacao(produto.getId()))
                .isPresent()
                .get()
                .extracting(Produto::getSku)
                .isEqualTo("MART-001");

        assertThat(repositorio.findByIdParaAtualizacao(999_999L)).isEmpty();
    }
}
