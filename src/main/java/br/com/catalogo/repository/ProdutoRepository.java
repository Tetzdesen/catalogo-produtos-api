package br.com.catalogo.repository;

import br.com.catalogo.model.Produto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acesso ao banco para {@link Produto}.
 *
 * <p>Não existe classe implementando esta interface: o Spring Data gera a
 * implementação no start. {@code JpaRepository} já traz {@code save},
 * {@code findById}, {@code findAll(Pageable)}, {@code delete} e companhia.
 *
 * <p>Os dois primeiros métodos são <i>derived queries</i>: o Spring lê o nome do
 * método e monta a consulta a partir dele. {@code existsBySkuAndIdNot} vira algo
 * como {@code select count(*) > 0 from produto where sku = ? and id <> ?}. Um
 * nome que não corresponda a nenhum campo da entidade derruba a aplicação no
 * start, e não na primeira requisição.
 */
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /** Usado no cadastro: o SKU já pertence a algum produto? */
    boolean existsBySku(String sku);

    /** Usado na atualização: o SKU pertence a algum produto <b>que não seja este</b>? */
    boolean existsBySkuAndIdNot(String sku, Long id);

    /**
     * Produtos cujo nome contém o trecho, sem distinção de maiúsculas, minúsculas
     * e acentos: "cafe" encontra "Café Torrado" e "CAFETEIRA".
     *
     * <p>Aqui o nome do método não basta: uma derived query
     * ({@code findByNomeContainingIgnoreCase}) resolveria só as maiúsculas. Por
     * isso a consulta é escrita à mão com {@code @Query}, em JPQL.
     * {@code function('unaccent', ...)} chama a função do PostgreSQL de mesmo
     * nome, criada pela extensão na migração V1.
     *
     * <p>O trecho deve chegar com {@code %}, {@code _} e {@code \} já escapados;
     * do contrário, um {@code %} digitado pelo cliente funcionaria como curinga do
     * {@code LIKE}. Por isso o resto da aplicação chama {@link #buscarPorNome}, que
     * escapa antes de delegar para cá.
     */
    @Query("""
            select p from Produto p
            where lower(function('unaccent', p.nome))
                  like concat('%', lower(function('unaccent', :trecho)), '%') escape '\\'
            """)
    Page<Produto> buscarPorTrechoDoNome(@Param("trecho") String trecho, Pageable paginacao);

    /**
     * A porta de entrada da busca por nome: recebe o trecho como o cliente
     * digitou e cuida do escape.
     *
     * <p><b>Por que aqui, e não no service?</b> Curinga de {@code LIKE} é detalhe
     * do SQL, e quem conhece SQL é o repositório. O service pede "produtos cujo
     * nome contém X" sem saber como isso vira consulta (o "S" de SOLID: cada
     * camada com um único motivo para mudar).
     *
     * <p>No {@code LIKE}, {@code %} e {@code _} são curingas. Prefixados com
     * {@code \} (o caractere de escape declarado na consulta), passam a ser
     * procurados literalmente: {@code ?nome=50%} busca "50%", não "50" seguido
     * de qualquer coisa.
     *
     * <p>Um método {@code default} em interface tem corpo, e o Spring Data o
     * respeita: só os métodos abstratos têm a implementação gerada.
     */
    default Page<Produto> buscarPorNome(String trecho, Pageable paginacao) {
        String escapado = trecho.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return buscarPorTrechoDoNome(escapado, paginacao);
    }
}
