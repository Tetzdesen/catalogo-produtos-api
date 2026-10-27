package br.com.catalogo.exception;

/**
 * Base de toda regra de negócio violada: a requisição está bem formada, mas o
 * estado atual não permite atendê-la. O {@link TratadorGlobalDeErros} a converte
 * em {@code 409 Conflict}.
 *
 * <p>Abstrata pelo mesmo motivo de {@link RecursoNaoEncontradoException}: cada
 * regra tem sua própria exceção ({@link SkuEmUsoException}), e o tratador não muda
 * quando uma regra nova aparece.
 */
public abstract class RegraDeNegocioException extends RuntimeException {

    protected RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
