package br.com.catalogo.exception;

/**
 * Lançada quando a requisição é sintaticamente válida, mas conflita com o estado
 * atual do sistema: SKU repetido, saldo insuficiente, categoria em uso.
 *
 * <p>Traduzida pelo handler global em {@code 409 Conflict}. A distinção em relação
 * ao {@code 400} é proposital: {@code 400} diz "seu payload está errado", enquanto
 * {@code 409} diz "seu payload está certo, mas o estado do servidor não permite".
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
