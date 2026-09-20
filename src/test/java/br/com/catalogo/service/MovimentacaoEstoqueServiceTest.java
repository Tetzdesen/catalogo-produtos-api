package br.com.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.domain.MovimentacaoEstoque;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.domain.TipoMovimentacao;
import br.com.catalogo.dto.AjusteRequest;
import br.com.catalogo.dto.MovimentacaoRequest;
import br.com.catalogo.exception.RecursoNaoEncontradoException;
import br.com.catalogo.exception.RegraDeNegocioException;
import br.com.catalogo.repository.MovimentacaoEstoqueRepository;
import br.com.catalogo.repository.ProdutoRepository;
import br.com.catalogo.support.Dados;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

/**
 * Testes das regras de movimentação.
 *
 * <p>O relógio é fixo para que o instante gravado seja verificável, e o
 * repositório de movimentações devolve o objeto recebido — o que interessa aqui é
 * o que o service manda gravar, não o que o banco faz com isso.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MovimentacaoEstoqueService")
class MovimentacaoEstoqueServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-18T12:00:00Z");

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private MovimentacaoEstoqueRepository movimentacaoRepository;

    private MovimentacaoEstoqueService service;

    private final Categoria categoria = Dados.categoria("Ferramentas");

    @BeforeEach
    void preparar() {
        service = new MovimentacaoEstoqueService(produtoRepository, movimentacaoRepository,
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private Produto produtoComSaldo(int saldo) {
        Produto produto = Dados.produto("MART-001", categoria);
        if (saldo > 0) {
            produto.creditar(saldo);
        }
        return produto;
    }

    private void mockarProduto(Produto produto) {
        when(produtoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(produto));
        when(movimentacaoRepository.save(any(MovimentacaoEstoque.class)))
                .thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Nested
    @DisplayName("entrada")
    class Entrada {

        @Test
        @DisplayName("soma ao saldo e grava a movimentação com o saldo resultante")
        void registraEntrada() {
            Produto produto = produtoComSaldo(5);
            mockarProduto(produto);

            MovimentacaoEstoque movimentacao = service.registrarEntrada(1L,
                    new MovimentacaoRequest(10, "Compra do fornecedor"));

            assertThat(produto.getSaldoEstoque()).isEqualTo(15);
            assertThat(movimentacao.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
            assertThat(movimentacao.getQuantidade()).isEqualTo(10);
            assertThat(movimentacao.getDiferenca()).isEqualTo(10);
            assertThat(movimentacao.getSaldoResultante()).isEqualTo(15);
            assertThat(movimentacao.getMotivo()).isEqualTo("Compra do fornecedor");
            assertThat(movimentacao.getDataHora()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("falha quando o produto não existe, sem gravar nada")
        void produtoInexistente() {
            when(produtoRepository.findByIdParaAtualizacao(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registrarEntrada(99L, new MovimentacaoRequest(10, "Compra")))
                    .isInstanceOf(RecursoNaoEncontradoException.class);

            verify(movimentacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita quantidade não positiva sem alterar o saldo")
        void quantidadeInvalida() {
            Produto produto = produtoComSaldo(5);
            when(produtoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(produto));

            assertThatThrownBy(() -> service.registrarEntrada(1L, new MovimentacaoRequest(0, "Compra")))
                    .isInstanceOf(RegraDeNegocioException.class);

            assertThat(produto.getSaldoEstoque()).isEqualTo(5);
            verify(movimentacaoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("saída")
    class Saida {

        @Test
        @DisplayName("subtrai do saldo e registra a diferença como negativa")
        void registraSaida() {
            Produto produto = produtoComSaldo(10);
            mockarProduto(produto);

            MovimentacaoEstoque movimentacao = service.registrarSaida(1L,
                    new MovimentacaoRequest(3, "Venda"));

            assertThat(produto.getSaldoEstoque()).isEqualTo(7);
            assertThat(movimentacao.getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
            assertThat(movimentacao.getQuantidade()).isEqualTo(3);
            assertThat(movimentacao.getDiferenca()).isEqualTo(-3);
            assertThat(movimentacao.getSaldoResultante()).isEqualTo(7);
        }

        @Test
        @DisplayName("rejeita saída maior que o saldo, informando o disponível")
        void saldoInsuficiente() {
            Produto produto = produtoComSaldo(10);
            when(produtoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(produto));

            assertThatThrownBy(() -> service.registrarSaida(1L, new MovimentacaoRequest(12, "Venda")))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("disponível 10")
                    .hasMessageContaining("solicitado 12");

            assertThat(produto.getSaldoEstoque()).isEqualTo(10);
            verify(movimentacaoRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejeita saída de produto inativo")
        void produtoInativo() {
            Produto produto = produtoComSaldo(10);
            produto.inativar();
            when(produtoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(produto));

            assertThatThrownBy(() -> service.registrarSaida(1L, new MovimentacaoRequest(1, "Venda")))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("inativo");

            assertThat(produto.getSaldoEstoque()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("ajuste")
    class Ajuste {

        @Test
        @DisplayName("para cima, registra a diferença positiva")
        void ajustaParaCima() {
            Produto produto = produtoComSaldo(12);
            mockarProduto(produto);

            MovimentacaoEstoque movimentacao = service.registrarAjuste(1L,
                    new AjusteRequest(20, "Inventário"));

            assertThat(produto.getSaldoEstoque()).isEqualTo(20);
            assertThat(movimentacao.getTipo()).isEqualTo(TipoMovimentacao.AJUSTE);
            assertThat(movimentacao.getQuantidade()).isEqualTo(20);
            assertThat(movimentacao.getDiferenca()).isEqualTo(8);
            assertThat(movimentacao.getSaldoResultante()).isEqualTo(20);
        }

        @Test
        @DisplayName("para baixo, registra a diferença negativa")
        void ajustaParaBaixo() {
            Produto produto = produtoComSaldo(12);
            mockarProduto(produto);

            MovimentacaoEstoque movimentacao = service.registrarAjuste(1L,
                    new AjusteRequest(5, "Inventário"));

            assertThat(produto.getSaldoEstoque()).isEqualTo(5);
            assertThat(movimentacao.getDiferenca()).isEqualTo(-7);
            assertThat(movimentacao.getSaldoResultante()).isEqualTo(5);
        }

        @Test
        @DisplayName("aceita zerar o estoque")
        void ajustaParaZero() {
            Produto produto = produtoComSaldo(4);
            mockarProduto(produto);

            MovimentacaoEstoque movimentacao = service.registrarAjuste(1L, new AjusteRequest(0, "Perda total"));

            assertThat(produto.getSaldoEstoque()).isZero();
            assertThat(movimentacao.getDiferenca()).isEqualTo(-4);
        }

        @Test
        @DisplayName("rejeita saldo negativo")
        void rejeitaNegativo() {
            Produto produto = produtoComSaldo(4);
            when(produtoRepository.findByIdParaAtualizacao(1L)).thenReturn(Optional.of(produto));

            assertThatThrownBy(() -> service.registrarAjuste(1L, new AjusteRequest(-1, "Erro")))
                    .isInstanceOf(RegraDeNegocioException.class);

            assertThat(produto.getSaldoEstoque()).isEqualTo(4);
            verify(movimentacaoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("histórico")
    class Historico {

        @Test
        @DisplayName("consulta sem filtro de tipo usa a busca por produto")
        void semFiltroDeTipo() {
            var paginacao = PageRequest.of(0, 20);
            when(produtoRepository.existsById(1L)).thenReturn(true);
            when(movimentacaoRepository.findByProdutoId(1L, paginacao))
                    .thenReturn(org.springframework.data.domain.Page.empty());

            service.listarHistorico(1L, null, paginacao);

            verify(movimentacaoRepository).findByProdutoId(1L, paginacao);
        }

        @Test
        @DisplayName("consulta com filtro de tipo usa a busca filtrada")
        void comFiltroDeTipo() {
            var paginacao = PageRequest.of(0, 20);
            when(produtoRepository.existsById(1L)).thenReturn(true);
            when(movimentacaoRepository.findByProdutoIdAndTipo(1L, TipoMovimentacao.SAIDA, paginacao))
                    .thenReturn(org.springframework.data.domain.Page.empty());

            service.listarHistorico(1L, TipoMovimentacao.SAIDA, paginacao);

            verify(movimentacaoRepository).findByProdutoIdAndTipo(1L, TipoMovimentacao.SAIDA, paginacao);
        }

        @Test
        @DisplayName("falha quando o produto não existe, em vez de devolver página vazia")
        void produtoInexistente() {
            when(produtoRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.listarHistorico(99L, null, PageRequest.of(0, 20)))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }
    }
}
