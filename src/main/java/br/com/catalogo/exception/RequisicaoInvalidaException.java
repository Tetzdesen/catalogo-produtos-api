package br.com.catalogo.exception;

/**
 * Base de todo pedido que o cliente montou errado e que só a aplicação consegue
 * perceber (o Bean Validation e o Spring MVC já cobrem o corpo e os tipos). O
 * {@link TratadorGlobalDeErros} a converte em {@code 400 Bad Request}.
 *
 * <p>Abstrata pelo mesmo motivo de {@link RecursoNaoEncontradoException}: cada
 * caso tem sua própria exceção ({@link OrdenacaoInvalidaException}), e o tratador
 * não muda quando um caso novo aparece.
 */
public abstract class RequisicaoInvalidaException extends RuntimeException {

    protected RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
