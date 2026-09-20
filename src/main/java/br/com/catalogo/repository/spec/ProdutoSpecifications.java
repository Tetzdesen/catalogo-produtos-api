package br.com.catalogo.repository.spec;

import br.com.catalogo.domain.Produto;
import br.com.catalogo.dto.ProdutoFiltro;
import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;

/**
 * Predicados combináveis para a listagem de produtos.
 *
 * <p>Com cinco filtros opcionais, métodos derivados exigiriam uma assinatura para
 * cada combinação (32 no total) e JPQL exigiria montar string condicionalmente.
 * Aqui cada filtro é um predicado independente e {@link #comFiltro} compõe todos
 * eles: um filtro não informado devolve {@code Specification.unrestricted()},
 * que não acrescenta nada ao WHERE gerado (ver README, decisão 6).

 * <p>Devolver {@code unrestricted} em vez de {@code null} não é detalhe de estilo:
 * desde o Spring Data JPA 4 a composição rejeita elementos nulos.
 */
public final class ProdutoSpecifications {

    private ProdutoSpecifications() {
    }

    public static Specification<Produto> comFiltro(ProdutoFiltro filtro) {
        return Specification.allOf(
                nomeContendo(filtro.nome()),
                daCategoria(filtro.categoriaId()),
                comPrecoMinimo(filtro.precoMinimo()),
                comPrecoMaximo(filtro.precoMaximo()),
                comSituacao(filtro.ativo()));
    }

    /** LOWER nos dois lados: 'martelo' encontra 'Martelo de unha'. */
    public static Specification<Produto> nomeContendo(String nome) {
        if (nome == null || nome.isBlank()) {
            return Specification.unrestricted();
        }
        String padrao = "%" + nome.trim().toLowerCase() + "%";
        return (raiz, consulta, construtor) ->
                construtor.like(construtor.lower(raiz.get("nome")), padrao);
    }

    public static Specification<Produto> daCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return Specification.unrestricted();
        }
        return (raiz, consulta, construtor) ->
                construtor.equal(raiz.get("categoria").get("id"), categoriaId);
    }

    public static Specification<Produto> comPrecoMinimo(BigDecimal precoMinimo) {
        if (precoMinimo == null) {
            return Specification.unrestricted();
        }
        return (raiz, consulta, construtor) ->
                construtor.greaterThanOrEqualTo(raiz.get("preco"), precoMinimo);
    }

    public static Specification<Produto> comPrecoMaximo(BigDecimal precoMaximo) {
        if (precoMaximo == null) {
            return Specification.unrestricted();
        }
        return (raiz, consulta, construtor) ->
                construtor.lessThanOrEqualTo(raiz.get("preco"), precoMaximo);
    }

    public static Specification<Produto> comSituacao(Boolean ativo) {
        if (ativo == null) {
            return Specification.unrestricted();
        }
        return (raiz, consulta, construtor) -> construtor.equal(raiz.get("ativo"), ativo);
    }
}
