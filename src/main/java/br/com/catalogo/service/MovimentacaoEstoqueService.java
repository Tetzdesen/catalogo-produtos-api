package br.com.catalogo.service;

import br.com.catalogo.domain.MovimentacaoEstoque;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.domain.TipoMovimentacao;
import br.com.catalogo.dto.AjusteRequest;
import br.com.catalogo.dto.MovimentacaoRequest;
import br.com.catalogo.exception.RecursoNaoEncontradoException;
import br.com.catalogo.repository.MovimentacaoEstoqueRepository;
import br.com.catalogo.repository.ProdutoRepository;
import java.time.Clock;
import java.util.function.ToIntFunction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Único ponto do sistema autorizado a alterar saldo de estoque.
 *
 * <p>Toda operação segue o mesmo roteiro, dentro de uma só transação:
 * <ol>
 *   <li>carregar o produto com bloqueio exclusivo na linha;</li>
 *   <li>deixar a entidade aplicar a regra e devolver a diferença;</li>
 *   <li>gravar a movimentação com o saldo resultante.</li>
 * </ol>
 *
 * <p>O bloqueio é o que sustenta a invariante de saldo não negativo sob
 * concorrência: sem ele, duas saídas simultâneas leriam o mesmo saldo e ambas
 * passariam na verificação (ver README, decisão 4). Como tudo acontece em uma
 * transação, uma falha em qualquer etapa desfaz as duas escritas — o saldo nunca
 * fica sem a movimentação que o explica.
 */
@Service
@Transactional(readOnly = true)
public class MovimentacaoEstoqueService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final Clock relogio;

    /**
     * O {@link Clock} é injetado em vez de chamar {@code Instant.now()} direto:
     * torna o instante do registro controlável em teste.
     */
    public MovimentacaoEstoqueService(ProdutoRepository produtoRepository,
                                      MovimentacaoEstoqueRepository movimentacaoRepository,
                                      Clock relogio) {
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.relogio = relogio;
    }

    @Transactional
    public MovimentacaoEstoque registrarEntrada(Long produtoId, MovimentacaoRequest requisicao) {
        return registrar(produtoId, TipoMovimentacao.ENTRADA, requisicao.quantidade(),
                requisicao.motivo(), produto -> produto.creditar(requisicao.quantidade()));
    }

    @Transactional
    public MovimentacaoEstoque registrarSaida(Long produtoId, MovimentacaoRequest requisicao) {
        return registrar(produtoId, TipoMovimentacao.SAIDA, requisicao.quantidade(),
                requisicao.motivo(), produto -> produto.debitar(requisicao.quantidade()));
    }

    @Transactional
    public MovimentacaoEstoque registrarAjuste(Long produtoId, AjusteRequest requisicao) {
        return registrar(produtoId, TipoMovimentacao.AJUSTE, requisicao.quantidade(),
                requisicao.motivo(), produto -> produto.ajustarPara(requisicao.quantidade()));
    }

    /**
     * Roteiro comum às três operações. A única coisa que varia entre elas é como a
     * entidade aplica a mudança, recebida aqui como função — por isso a validação
     * de saldo e a gravação do histórico existem em um lugar só.
     */
    private MovimentacaoEstoque registrar(Long produtoId,
                                          TipoMovimentacao tipo,
                                          int quantidade,
                                          String motivo,
                                          ToIntFunction<Produto> operacao) {
        Produto produto = produtoRepository.findByIdParaAtualizacao(produtoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Produto", produtoId));

        // A entidade decide se a operação é possível e devolve o quanto mudou;
        // se a regra for violada, a exceção aborta a transação antes de gravar.
        int diferenca = operacao.applyAsInt(produto);

        return movimentacaoRepository.save(new MovimentacaoEstoque(
                produto, tipo, quantidade, diferenca, produto.getSaldoEstoque(),
                motivo.trim(), relogio.instant()));
    }

    /**
     * Histórico do produto, da movimentação mais recente para a mais antiga.
     *
     * <p>A existência do produto é verificada antes: sem isso, consultar um id
     * inexistente devolveria uma página vazia — indistinguível de um produto que
     * simplesmente nunca foi movimentado.
     */
    public Page<MovimentacaoEstoque> listarHistorico(Long produtoId, TipoMovimentacao tipo, Pageable paginacao) {
        if (!produtoRepository.existsById(produtoId)) {
            throw RecursoNaoEncontradoException.de("Produto", produtoId);
        }
        return tipo == null
                ? movimentacaoRepository.findByProdutoId(produtoId, paginacao)
                : movimentacaoRepository.findByProdutoIdAndTipo(produtoId, tipo, paginacao);
    }
}
