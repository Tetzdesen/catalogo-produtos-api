package br.com.catalogo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.dto.ProdutoFiltro;
import br.com.catalogo.repository.spec.ProdutoSpecifications;
import br.com.catalogo.support.Dados;
import br.com.catalogo.support.TesteDeRepositorio;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

/**
 * Exercita os filtros contra o banco de verdade.
 *
 * <p>Specification produz SQL; testar com mock não provaria nada. Aqui a consulta
 * roda de fato e o que se verifica é o conjunto retornado.
 */
@TesteDeRepositorio
@DisplayName("ProdutoSpecifications")
class ProdutoSpecificationsTest {

    @Autowired
    private ProdutoRepository repositorio;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Categoria ferramentas;
    private Categoria jardinagem;

    @BeforeEach
    void preparar() {
        ferramentas = categoriaRepository.save(Dados.categoria("Ferramentas"));
        jardinagem = categoriaRepository.save(Dados.categoria("Jardinagem"));

        repositorio.save(Dados.produto("MART-001", "Martelo de unha", "49.90", ferramentas));
        repositorio.save(Dados.produto("SERR-002", "Serrote 20 polegadas", "89.90", ferramentas));
        Produto inativo = Dados.produto("ALIC-003", "Alicate universal", "35.00", ferramentas);
        inativo.inativar();
        repositorio.save(inativo);
        repositorio.save(Dados.produto("REGA-004", "Regador de metal", "120.00", jardinagem));
    }

    private java.util.List<String> buscar(ProdutoFiltro filtro) {
        return repositorio.findAll(ProdutoSpecifications.comFiltro(filtro), Pageable.unpaged())
                .map(Produto::getSku)
                .getContent();
    }

    @Test
    @DisplayName("sem filtros, devolve todos os produtos")
    void semFiltros() {
        assertThat(buscar(new ProdutoFiltro(null, null, null, null, null)))
                .containsExactlyInAnyOrder("MART-001", "SERR-002", "ALIC-003", "REGA-004");
    }

    @Test
    @DisplayName("filtra por trecho do nome sem distinção de caixa")
    void filtraPorNome() {
        assertThat(buscar(new ProdutoFiltro("MARTELO", null, null, null, null)))
                .containsExactly("MART-001");
        // Trecho no meio do nome, não só no começo: "Martelo de unha" e "Regador de metal".
        assertThat(buscar(new ProdutoFiltro("de", null, null, null, null)))
                .containsExactlyInAnyOrder("MART-001", "REGA-004");
    }

    @Test
    @DisplayName("filtra por categoria")
    void filtraPorCategoria() {
        assertThat(buscar(new ProdutoFiltro(null, jardinagem.getId(), null, null, null)))
                .containsExactly("REGA-004");
    }

    @Test
    @DisplayName("filtra por preço mínimo e máximo, incluindo os limites")
    void filtraPorFaixaDePreco() {
        assertThat(buscar(new ProdutoFiltro(null, null, new BigDecimal("49.90"), null, null)))
                .containsExactlyInAnyOrder("MART-001", "SERR-002", "REGA-004");
        assertThat(buscar(new ProdutoFiltro(null, null, null, new BigDecimal("49.90"), null)))
                .containsExactlyInAnyOrder("MART-001", "ALIC-003");
        assertThat(buscar(new ProdutoFiltro(null, null, new BigDecimal("40.00"), new BigDecimal("90.00"), null)))
                .containsExactlyInAnyOrder("MART-001", "SERR-002");
    }

    @Test
    @DisplayName("filtra por situação")
    void filtraPorSituacao() {
        assertThat(buscar(new ProdutoFiltro(null, null, null, null, true)))
                .containsExactlyInAnyOrder("MART-001", "SERR-002", "REGA-004");
        assertThat(buscar(new ProdutoFiltro(null, null, null, null, false)))
                .containsExactly("ALIC-003");
    }

    @Test
    @DisplayName("combina todos os filtros ao mesmo tempo")
    void combinaTodosOsFiltros() {
        var filtro = new ProdutoFiltro("o", ferramentas.getId(),
                new BigDecimal("40.00"), new BigDecimal("90.00"), true);

        assertThat(buscar(filtro)).containsExactlyInAnyOrder("MART-001", "SERR-002");
    }

    @Test
    @DisplayName("devolve vazio quando nada corresponde")
    void semCorrespondencia() {
        assertThat(buscar(new ProdutoFiltro("inexistente", null, null, null, null))).isEmpty();
    }
}
