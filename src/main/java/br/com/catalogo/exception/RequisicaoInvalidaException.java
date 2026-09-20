package br.com.catalogo.exception;

/**
 * Lançada quando a requisição é inválida por um motivo que a Bean Validation não
 * consegue expressar — tipicamente a relação entre dois campos, como uma faixa de
 * preço cujo mínimo é maior que o máximo.
 *
 * <p>Traduzida em {@code 400 Bad Request}, e não em {@code 409}: o problema está
 * no que o cliente enviou, não no estado do servidor. A distinção importa para
 * quem consome a API — {@code 400} significa "corrija o pedido", {@code 409}
 * significa "o pedido está correto, mas não cabe no estado atual".
 */
public class RequisicaoInvalidaException extends RuntimeException {

    public RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
