package br.com.catalogo.repository;

import br.com.catalogo.domain.MovimentacaoEstoque;
import br.com.catalogo.domain.TipoMovimentacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso ao histórico de movimentações.
 *
 * <p>Só existem operações de leitura e de inserção: a ausência de qualquer método
 * de alteração ou remoção é o que torna o histórico imutável na prática.
 */
public interface MovimentacaoEstoqueRepository extends JpaRepository<MovimentacaoEstoque, Long> {

    Page<MovimentacaoEstoque> findByProdutoId(Long produtoId, Pageable paginacao);

    /** Mesma consulta com o filtro opcional de tipo aplicado. */
    Page<MovimentacaoEstoque> findByProdutoIdAndTipo(Long produtoId, TipoMovimentacao tipo, Pageable paginacao);

    boolean existsByProdutoId(Long produtoId);
}
