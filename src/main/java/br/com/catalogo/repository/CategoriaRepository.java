package br.com.catalogo.repository;

import br.com.catalogo.domain.Categoria;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a {@link Categoria}.
 *
 * <p>As consultas de unicidade são derivadas do nome do método
 * ({@code IgnoreCase}) em vez de escritas em JPQL: são simples o bastante para
 * que o nome do método seja a documentação, e o Spring Data valida a assinatura
 * na subida da aplicação.
 */
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    /** Usado na criação: nenhuma outra categoria pode ter esse nome. */
    boolean existsByNomeIgnoreCase(String nome);

    /** Usado na atualização: só conflita se o nome pertencer a OUTRA categoria. */
    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);

    Optional<Categoria> findByNomeIgnoreCase(String nome);
}
