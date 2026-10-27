package br.com.catalogo.exception;

import java.util.Collection;

/** O {@code ?sort=} pediu um campo pelo qual a listagem não ordena. Vira {@code 400}. */
public class OrdenacaoInvalidaException extends RequisicaoInvalidaException {

    public OrdenacaoInvalidaException(String campo, Collection<String> permitidos) {
        super("Não é possível ordenar por '" + campo + "'. Campos aceitos: "
                + String.join(", ", permitidos) + ".");
    }
}
