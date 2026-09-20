package br.com.catalogo.exception;

/**
 * Lançada quando um recurso referenciado por id (ou por outra chave) não existe.
 *
 * <p>Traduzida pelo handler global em {@code 404 Not Found}. Existe para que o
 * service possa sinalizar a ausência sem conhecer nada de HTTP — quem conhece
 * status code é a camada de borda, não a regra de negócio.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public static RecursoNaoEncontradoException de(String recurso, Object identificador) {
        return new RecursoNaoEncontradoException(
                "%s não encontrado(a) para o identificador '%s'.".formatted(recurso, identificador));
    }
}
