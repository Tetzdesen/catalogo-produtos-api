package br.com.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.catalogo.domain.MovimentacaoEstoque;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.dto.AjusteRequest;
import br.com.catalogo.dto.MovimentacaoRequest;
import br.com.catalogo.exception.RegraDeNegocioException;
import br.com.catalogo.repository.CategoriaRepository;
import br.com.catalogo.repository.MovimentacaoEstoqueRepository;
import br.com.catalogo.repository.ProdutoRepository;
import br.com.catalogo.support.Dados;
import br.com.catalogo.support.TesteDeIntegracao;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

/**
 * Garante a propriedade central do controle de estoque: o saldo do produto é
 * sempre o resultado acumulado do seu histórico.
 *
 * <p>Estes testes usam o banco de verdade e transações reais, porque é exatamente
 * o comportamento transacional que está sendo verificado — um teste com mocks
 * provaria apenas que o código chama os métodos que o autor esperava.
 */
@TesteDeIntegracao
@DisplayName("Consistência entre saldo e histórico")
class ConsistenciaEstoqueIT {

    @Autowired
    private MovimentacaoEstoqueService service;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MovimentacaoEstoqueRepository movimentacaoRepository;

    private Produto produto;

    @BeforeEach
    void preparar() {
        movimentacaoRepository.deleteAll();
        produtoRepository.deleteAll();
        categoriaRepository.deleteAll();
        var categoria = categoriaRepository.save(Dados.categoria("Ferramentas"));
        produto = produtoRepository.save(Dados.produto("MART-001", categoria));
    }

    private int saldoAtual() {
        return produtoRepository.findById(produto.getId()).orElseThrow().getSaldoEstoque();
    }

    @Test
    @DisplayName("após uma sequência de movimentações, o saldo equivale à soma das diferenças")
    void saldoEquivaleAoAcumulado() {
        service.registrarEntrada(produto.getId(), new MovimentacaoRequest(100, "Compra inicial"));
        service.registrarSaida(produto.getId(), new MovimentacaoRequest(30, "Venda"));
        service.registrarEntrada(produto.getId(), new MovimentacaoRequest(15, "Devolução"));
        service.registrarAjuste(produto.getId(), new AjusteRequest(80, "Inventário"));
        service.registrarSaida(produto.getId(), new MovimentacaoRequest(25, "Venda"));

        List<MovimentacaoEstoque> historico = movimentacaoRepository
                .findByProdutoId(produto.getId(), Pageable.unpaged()).getContent();

        int acumulado = historico.stream().mapToInt(MovimentacaoEstoque::getDiferenca).sum();

        assertThat(historico).hasSize(5);
        assertThat(saldoAtual()).isEqualTo(55).isEqualTo(acumulado);
        // Cada linha do histórico também registra o saldo que valia naquele momento.
        assertThat(historico.getLast().getSaldoResultante()).isEqualTo(55);
    }

    @Test
    @DisplayName("quando a regra de negócio falha, nem a movimentação nem o saldo são persistidos")
    void falhaNaoPersisteNada() {
        service.registrarEntrada(produto.getId(), new MovimentacaoRequest(10, "Compra inicial"));

        assertThatThrownBy(() -> service.registrarSaida(produto.getId(),
                new MovimentacaoRequest(50, "Venda impossível")))
                .isInstanceOf(RegraDeNegocioException.class);

        // A transação da saída foi desfeita por inteiro: sobrou apenas a entrada.
        assertThat(saldoAtual()).isEqualTo(10);
        assertThat(movimentacaoRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("saídas concorrentes não furam o saldo: o que passa soma o saldo, o resto é recusado")
    void saidasConcorrentes() throws Exception {
        service.registrarEntrada(produto.getId(), new MovimentacaoRequest(10, "Compra inicial"));

        // Seis threads tentam tirar 3 unidades de um saldo de 10. Sem o bloqueio
        // pessimista, várias leriam o mesmo saldo e o total sairia negativo.
        int tentativas = 6;
        int quantidadePorSaida = 3;
        Callable<Boolean> saida = () -> {
            try {
                service.registrarSaida(produto.getId(),
                        new MovimentacaoRequest(quantidadePorSaida, "Venda concorrente"));
                return true;
            } catch (RuntimeException recusada) {
                // Saldo insuficiente ou conflito de concorrência: ambos são respostas válidas.
                return false;
            }
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(tentativas)) {
            List<Future<Boolean>> resultados = executor.invokeAll(
                    java.util.Collections.nCopies(tentativas, saida));
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

            long aprovadas = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) {
                    aprovadas++;
                }
            }

            // Sem este limite o teste passaria mesmo se tudo fosse recusado: com saldo
            // 10 e saídas de 3, exatamente 3 podem passar — nem mais (furaria o saldo),
            // nem menos que 1 (indicaria que o bloqueio está recusando tudo).
            assertThat(aprovadas).isEqualTo(3);
            assertThat(saldoAtual())
                    .isNotNegative()
                    .isEqualTo(10 - (int) aprovadas * quantidadePorSaida);
            assertThat(movimentacaoRepository.count()).isEqualTo(aprovadas + 1);
        }
    }
}
