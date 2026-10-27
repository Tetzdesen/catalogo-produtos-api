package br.com.catalogo.exception;

/**
 * Base de todo "o recurso pedido não existe". O {@link TratadorGlobalDeErros} a
 * converte em {@code 404 Not Found}.
 *
 * <p><b>Por que abstrata?</b> Para obrigar quem lança a dizer <i>qual</i> recurso
 * faltou ({@link ProdutoNaoEncontradoException}, e amanhã uma
 * {@code CategoriaNaoEncontradaException}). O tratador continua olhando só para
 * esta base: uma subclasse nova vira {@code 404} sem que ninguém precise editá-lo.
 * É o princípio aberto/fechado (o "O" de SOLID) aplicado às exceções.
 */
public abstract class RecursoNaoEncontradoException extends RuntimeException {

    protected RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
