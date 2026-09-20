package br.com.catalogo.repository;

import br.com.catalogo.domain.Produto;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * Acesso a {@link Produto}.
 *
 * <p>Estende {@code JpaSpecificationExecutor} porque os filtros da listagem são
 * opcionais e combináveis: com métodos derivados seriam dezenas de assinaturas
 * para cobrir todas as combinações (ver README, decisão 6).
 */
public interface ProdutoRepository extends JpaRepository<Produto, Long>, JpaSpecificationExecutor<Produto> {

    boolean existsBySku(String sku);

    Optional<Produto> findBySku(String sku);

    boolean existsByCategoriaId(Long categoriaId);

    /**
     * Carrega o produto com bloqueio exclusivo na linha ({@code SELECT ... FOR UPDATE}).
     *
     * <p>Toda movimentação de estoque é um ler-decidir-gravar: sem o bloqueio, duas
     * saídas concorrentes podem ambas ler o mesmo saldo e ambas aprovar a baixa,
     * furando a invariante de saldo não negativo. Só usar dentro de uma transação
     * curta — o bloqueio dura até o commit (ver README, decisão 4).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id = :id")
    Optional<Produto> findByIdParaAtualizacao(Long id);
}
