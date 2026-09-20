package br.com.catalogo.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte exceções em respostas de erro no formato RFC 9457
 * ({@code application/problem+json}).
 *
 * <p>Centralizar aqui mantém os controllers com um único caminho — o feliz — e
 * garante que todo erro da API tenha a mesma forma. Estender
 * {@code ResponseEntityExceptionHandler} permite reescrever também os erros que o
 * próprio Spring MVC produz (payload malformado, parâmetro ausente, método não
 * suportado), que de outra forma sairiam em um formato diferente do nosso.
 */
@RestControllerAdvice
public class TratadorGlobalDeErros extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorGlobalDeErros.class);

    private static final URI TIPO_VALIDACAO = URI.create("urn:catalogo:erro:validacao");
    private static final URI TIPO_NAO_ENCONTRADO = URI.create("urn:catalogo:erro:nao-encontrado");
    private static final URI TIPO_CONFLITO = URI.create("urn:catalogo:erro:conflito");
    private static final URI TIPO_INTERNO = URI.create("urn:catalogo:erro:interno");

    /** Campo de um DTO que falhou na validação, reportado junto do problema. */
    public record CampoInvalido(String campo, String mensagem) {
    }

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ProblemDetail tratarRequisicaoInvalida(RequisicaoInvalidaException excecao) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                excecao.getMessage(), TIPO_VALIDACAO);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail tratarNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado",
                excecao.getMessage(), TIPO_NAO_ENCONTRADO);
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ProblemDetail tratarRegraDeNegocio(RegraDeNegocioException excecao) {
        return problema(HttpStatus.CONFLICT, "Conflito com o estado atual",
                excecao.getMessage(), TIPO_CONFLITO);
    }

    /**
     * Rede de segurança. O log guarda a stack trace completa para quem opera o
     * sistema; a resposta não vaza nada dela para o cliente.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail tratarErroInesperado(Exception excecao) {
        log.error("Erro não tratado ao processar a requisição", excecao);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado ao processar a requisição.", TIPO_INTERNO);
    }

    /**
     * Falha de Bean Validation nos DTOs de request. Devolve todos os campos
     * inválidos de uma vez — corrigir um erro por requisição seria hostil com
     * quem consome a API.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException excecao,
                                                                  HttpHeaders cabecalhos,
                                                                  HttpStatusCode status,
                                                                  WebRequest requisicao) {
        List<CampoInvalido> camposInvalidos = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> new CampoInvalido(erro.getField(), erro.getDefaultMessage()))
                .toList();

        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "A requisição contém campos inválidos.", TIPO_VALIDACAO);
        problema.setProperty("campos", camposInvalidos);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problema);
    }

    /**
     * Demais erros tratados pelo Spring MVC (JSON malformado, parâmetro ausente,
     * método não suportado). Passam a carregar {@code timestamp} como todos os
     * outros, para que o cliente leia sempre a mesma estrutura.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object corpo,
                                                          HttpHeaders cabecalhos,
                                                          HttpStatusCode status,
                                                          WebRequest requisicao) {
        if (corpo instanceof ProblemDetail problema) {
            problema.setProperty("timestamp", Instant.now());
        }
        return super.createResponseEntity(corpo, cabecalhos, status, requisicao);
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe, URI tipo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setType(tipo);
        problema.setProperty("timestamp", Instant.now());
        return problema;
    }
}
