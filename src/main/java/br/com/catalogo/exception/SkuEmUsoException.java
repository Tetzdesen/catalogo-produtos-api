package br.com.catalogo.exception;

/** O SKU informado já pertence a outro produto. Vira {@code 409}. */
public class SkuEmUsoException extends RegraDeNegocioException {

    public SkuEmUsoException(String sku) {
        super("O SKU '" + sku + "' já está em uso por outro produto.");
    }
}
