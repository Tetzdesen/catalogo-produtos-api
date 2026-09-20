package br.com.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.dto.ProdutoFiltro;
import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.exception.RecursoNaoEncontradoException;
import br.com.catalogo.exception.RegraDeNegocioException;
import br.com.catalogo.exception.RequisicaoInvalidaException;
import br.com.catalogo.repository.MovimentacaoEstoqueRepository;
import br.com.catalogo.repository.ProdutoRepository;
import br.com.catalogo.support.Dados;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProdutoService")
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private MovimentacaoEstoqueRepository movimentacaoRepository;

    @Mock
    private CategoriaService categoriaService;

    @InjectMocks
    private ProdutoService service;

    private final Categoria categoria = Dados.categoria("Ferramentas");

    private ProdutoRequest requisicao(String sku, String preco) {
        return new ProdutoRequest(sku, "Martelo de unha", "Cabo de madeira", new BigDecimal(preco), 1L);
    }

    @Nested
    @DisplayName("ao criar")
    class AoCriar {

        @Test
        @DisplayName("cria o produto ativo e com saldo zero")
        void criaComSucesso() {
            when(produtoRepository.existsBySku("MART-001")).thenReturn(false);
            when(categoriaService.buscarPorId(1L)).thenReturn(categoria);
            when(produtoRepository.save(any(Produto.class))).thenAnswer(chamada -> chamada.getArgument(0));

            Produto criado = service.criar(requisicao("MART-001", "49.90"));

            assertThat(criado.getSku()).isEqualTo("MART-001");
            assertThat(criado.isAtivo()).isTrue();
            assertThat(criado.getSaldoEstoque()).isZero();
            assertThat(criado.getCategoria()).isSameAs(categoria);
        }

        @Test
        @DisplayName("rejeita SKU já cadastrado")
        void rejeitaSkuDuplicado() {
            when(produtoRepository.existsBySku("MART-001")).thenReturn(true);

            assertThatThrownBy(() -> service.criar(requisicao("MART-001", "49.90")))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("SKU");

            verify(produtoRepository, never()).save(any());
        }

        @Test
        @DisplayName("propaga o 404 quando a categoria não existe")
        void rejeitaCategoriaInexistente() {
            when(produtoRepository.existsBySku("MART-001")).thenReturn(false);
            when(categoriaService.buscarPorId(1L))
                    .thenThrow(RecursoNaoEncontradoException.de("Categoria", 1L));

            assertThatThrownBy(() -> service.criar(requisicao("MART-001", "49.90")))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            verify(produtoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("ao consultar")
    class AoConsultar {

        @Test
        @DisplayName("falha quando o produto não existe")
        void falhaQuandoNaoEncontra() {
            when(produtoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscarPorId(99L))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining("Produto");
        }

        @Test
        @DisplayName("rejeita faixa de preço invertida antes de consultar o banco")
        void rejeitaFaixaInvertida() {
            var filtro = new ProdutoFiltro(null, null, new BigDecimal("100.00"), new BigDecimal("10.00"), null);

            assertThatThrownBy(() -> service.listar(filtro, Pageable.unpaged()))
                    .isInstanceOf(RequisicaoInvalidaException.class)
                    .hasMessageContaining("faixa de preço");

            verify(produtoRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                    any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("ao atualizar")
    class AoAtualizar {

        @Test
        @DisplayName("altera os dados e preserva o saldo de estoque")
        void atualizaPreservandoSaldo() {
            Produto produto = Dados.produto("MART-001", categoria);
            produto.creditar(15);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
            when(categoriaService.buscarPorId(1L)).thenReturn(categoria);

            Produto atualizado = service.atualizar(1L, new ProdutoRequest(
                    "MART-001", "Martelo novo", "Nova descrição", new BigDecimal("59.90"), 1L));

            assertThat(atualizado.getNome()).isEqualTo("Martelo novo");
            assertThat(atualizado.getPreco()).isEqualByComparingTo("59.90");
            assertThat(atualizado.getSaldoEstoque()).isEqualTo(15);
        }

        @Test
        @DisplayName("rejeita tentativa de alterar o SKU")
        void rejeitaAlteracaoDeSku() {
            Produto produto = Dados.produto("MART-001", categoria);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            assertThatThrownBy(() -> service.atualizar(1L, requisicao("OUTRO-SKU", "59.90")))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("imutável");

            assertThat(produto.getSku()).isEqualTo("MART-001");
        }
    }

    @Nested
    @DisplayName("ao alterar a situação")
    class AoAlterarSituacao {

        @Test
        @DisplayName("inativa o produto")
        void inativa() {
            Produto produto = Dados.produto("MART-001", categoria);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            assertThat(service.alterarSituacao(1L, false).isAtivo()).isFalse();
        }

        @Test
        @DisplayName("reativa o produto")
        void ativa() {
            Produto produto = Dados.produto("MART-001", categoria);
            produto.inativar();
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));

            assertThat(service.alterarSituacao(1L, true).isAtivo()).isTrue();
        }
    }

    @Nested
    @DisplayName("ao excluir")
    class AoExcluir {

        @Test
        @DisplayName("remove o produto sem histórico")
        void excluiSemHistorico() {
            Produto produto = Dados.produto("MART-001", categoria);
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(produto));
            when(movimentacaoRepository.existsByProdutoId(1L)).thenReturn(false);

            service.excluir(1L);

            verify(produtoRepository).delete(produto);
        }

        @Test
        @DisplayName("bloqueia a exclusão de produto com movimentações e orienta a inativação")
        void bloqueiaComHistorico() {
            when(produtoRepository.findById(1L)).thenReturn(Optional.of(Dados.produto("MART-001", categoria)));
            when(movimentacaoRepository.existsByProdutoId(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.excluir(1L))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("inativação");

            // any(Produto.class) e não any(): o repositório tem dois métodos 'delete'
            // (por entidade e por Specification) e o tipo desfaz a ambiguidade.
            verify(produtoRepository, never()).delete(any(Produto.class));
        }
    }
}
