package br.com.catalogo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.catalogo.domain.MovimentacaoEstoque;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.domain.TipoMovimentacao;
import br.com.catalogo.support.Dados;
import br.com.catalogo.support.TesteDeRepositorio;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@TesteDeRepositorio
@DisplayName("MovimentacaoEstoqueRepository")
class MovimentacaoEstoqueRepositoryTest {

    @Autowired
    private MovimentacaoEstoqueRepository repositorio;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Produto produto;
    private Produto produtoSemMovimentacao;

    @BeforeEach
    void preparar() {
        var categoria = categoriaRepository.save(Dados.categoria("Ferramentas"));
        produto = produtoRepository.save(Dados.produto("MART-001", categoria));
        produtoSemMovimentacao = produtoRepository.save(Dados.produto("SERR-002", categoria));

        Instant agora = Instant.now();
        repositorio.save(new MovimentacaoEstoque(produto, TipoMovimentacao.ENTRADA, 10, 10, 10,
                "Compra inicial", agora.minus(2, ChronoUnit.HOURS)));
        repositorio.save(new MovimentacaoEstoque(produto, TipoMovimentacao.SAIDA, 4, -4, 6,
                "Venda", agora.minus(1, ChronoUnit.HOURS)));
        repositorio.save(new MovimentacaoEstoque(produto, TipoMovimentacao.AJUSTE, 8, 2, 8,
                "Inventário", agora));
    }

    @Test
    @DisplayName("lista as movimentações do produto da mais recente para a mais antiga")
    void findByProdutoId() {
        var paginacao = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "dataHora"));

        var pagina = repositorio.findByProdutoId(produto.getId(), paginacao);

        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getContent())
                .extracting(MovimentacaoEstoque::getTipo)
                .containsExactly(TipoMovimentacao.AJUSTE, TipoMovimentacao.SAIDA, TipoMovimentacao.ENTRADA);
    }

    @Test
    @DisplayName("filtra as movimentações do produto por tipo")
    void findByProdutoIdAndTipo() {
        var pagina = repositorio.findByProdutoIdAndTipo(produto.getId(), TipoMovimentacao.SAIDA,
                PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().getFirst().getMotivo()).isEqualTo("Venda");
    }

    @Test
    @DisplayName("devolve página vazia para produto sem movimentações")
    void findByProdutoIdSemMovimentacoes() {
        var pagina = repositorio.findByProdutoId(produtoSemMovimentacao.getId(), PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isZero();
        assertThat(pagina.getContent()).isEmpty();
    }

    @Test
    @DisplayName("reconhece produto com histórico")
    void existsByProdutoId() {
        assertThat(repositorio.existsByProdutoId(produto.getId())).isTrue();
        assertThat(repositorio.existsByProdutoId(produtoSemMovimentacao.getId())).isFalse();
    }
}
