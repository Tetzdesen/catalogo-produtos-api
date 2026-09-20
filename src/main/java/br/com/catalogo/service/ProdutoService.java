package br.com.catalogo.service;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.domain.Produto;
import br.com.catalogo.dto.ProdutoFiltro;
import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.exception.RecursoNaoEncontradoException;
import br.com.catalogo.exception.RegraDeNegocioException;
import br.com.catalogo.exception.RequisicaoInvalidaException;
import br.com.catalogo.repository.MovimentacaoEstoqueRepository;
import br.com.catalogo.repository.ProdutoRepository;
import br.com.catalogo.repository.spec.ProdutoSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio dos produtos.
 *
 * <p>Não altera saldo de estoque: isso é competência exclusiva do
 * {@link MovimentacaoEstoqueService}, para que toda mudança de saldo tenha
 * obrigatoriamente uma movimentação correspondente no histórico.
 */
@Service
@Transactional(readOnly = true)
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final MovimentacaoEstoqueRepository movimentacaoRepository;
    private final CategoriaService categoriaService;

    public ProdutoService(ProdutoRepository produtoRepository,
                          MovimentacaoEstoqueRepository movimentacaoRepository,
                          CategoriaService categoriaService) {
        this.produtoRepository = produtoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.categoriaService = categoriaService;
    }

    @Transactional
    public Produto criar(ProdutoRequest requisicao) {
        String sku = requisicao.sku().trim();
        if (produtoRepository.existsBySku(sku)) {
            throw new RegraDeNegocioException("Já existe um produto com o SKU '%s'.".formatted(sku));
        }
        // Delegar ao CategoriaService, e não ao repositório, garante o 404 com a
        // mesma mensagem usada em qualquer outro ponto que busque categoria.
        Categoria categoria = categoriaService.buscarPorId(requisicao.categoriaId());

        return produtoRepository.save(new Produto(
                sku,
                requisicao.nome().trim(),
                requisicao.descricao(),
                requisicao.preco(),
                categoria));
    }

    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Produto", id));
    }

    public Page<Produto> listar(ProdutoFiltro filtro, Pageable paginacao) {
        if (filtro.faixaDePrecoInvertida()) {
            // 400 e não 409: quem errou foi o cliente ao montar a consulta.
            throw new RequisicaoInvalidaException(
                    "A faixa de preço é inconsistente: o preço mínimo (%s) é maior que o máximo (%s)."
                            .formatted(filtro.precoMinimo(), filtro.precoMaximo()));
        }
        return produtoRepository.findAll(ProdutoSpecifications.comFiltro(filtro), paginacao);
    }

    @Transactional
    public Produto atualizar(Long id, ProdutoRequest requisicao) {
        Produto produto = buscarPorId(id);

        // O SKU é a identidade externa do produto: mudá-lo quebraria integrações e
        // o histórico de movimentações. Falhar é mais honesto que ignorar o campo.
        if (!produto.getSku().equals(requisicao.sku().trim())) {
            throw new RegraDeNegocioException(
                    "O SKU é imutável: o produto %d permanece com o SKU '%s'."
                            .formatted(id, produto.getSku()));
        }

        Categoria categoria = categoriaService.buscarPorId(requisicao.categoriaId());
        produto.atualizarDados(requisicao.nome().trim(), requisicao.descricao(),
                requisicao.preco(), categoria);
        return produto;
    }

    @Transactional
    public Produto alterarSituacao(Long id, boolean ativo) {
        Produto produto = buscarPorId(id);
        if (ativo) {
            produto.ativar();
        } else {
            produto.inativar();
        }
        return produto;
    }

    @Transactional
    public void excluir(Long id) {
        Produto produto = buscarPorId(id);
        // Produto com histórico não é excluído: apagá-lo levaria junto as
        // movimentações que explicam o estoque passado (ver README, decisão 11).
        if (movimentacaoRepository.existsByProdutoId(id)) {
            throw new RegraDeNegocioException(
                    ("O produto '%s' possui movimentações de estoque e não pode ser excluído. "
                            + "Use a inativação para retirá-lo do catálogo.").formatted(produto.getSku()));
        }
        produtoRepository.delete(produto);
    }
}
