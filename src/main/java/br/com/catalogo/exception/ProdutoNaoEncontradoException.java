package br.com.catalogo.exception;

/**
 * Não existe produto com o identificador informado. Vira {@code 404}.
 *
 * <p>A mensagem é montada aqui, e não em quem lança: o texto fica em um lugar só
 * e a chamada se reduz a {@code new ProdutoNaoEncontradoException(id)}.
 */
public class ProdutoNaoEncontradoException extends RecursoNaoEncontradoException {

    public ProdutoNaoEncontradoException(Long id) {
        super("Produto " + id + " não encontrado.");
    }
}
