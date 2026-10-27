package br.com.catalogo.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * O único lugar da aplicação que transforma exceção em resposta HTTP.
 *
 * <p><b>{@code @RestControllerAdvice}</b> registra estes métodos para <i>todos</i>
 * os controllers de uma vez. A alternativa seria {@code try/catch} em cada rota,
 * e o primeiro endpoint em que alguém esquecesse o bloco passaria a devolver
 * erro em um formato diferente dos demais.
 *
 * <p><b>Por que estender {@code ResponseEntityExceptionHandler}?</b> Porque o
 * Spring MVC gera erros próprios antes de o nosso código rodar: JSON malformado,
 * parâmetro do tipo errado, método HTTP não suportado. Sem essa herança, esses
 * erros sairiam em um formato diferente e o cliente precisaria saber ler dois
 * tipos de resposta de erro.
 *
 * <p>Essa herança é o padrão <b>Template Method</b>: a classe-mãe define o
 * algoritmo inteiro (identificar o erro, montar o corpo, criar a resposta) e
 * deixa alguns passos como métodos {@code protected} para a filha sobrescrever.
 * Aqui sobrescrevemos só quatro deles; o resto do algoritmo vem pronto.
 *
 * <p><b>Os handlers olham para as classes-base</b>
 * ({@link RecursoNaoEncontradoException}, {@link RegraDeNegocioException}), não
 * para {@code ProdutoNaoEncontradoException} ou {@code SkuEmUsoException}. Uma
 * exceção nova que estenda uma das bases já sai com o status certo, sem editar
 * esta classe: o "O" de SOLID, aberto para extensão e fechado para modificação.
 *
 * <p>Fica no pacote {@code exception} porque é ali que vivem as exceções que ela
 * traduz. Não é um controller: não tem rotas e por isso não recebe o prefixo
 * {@code /api/v1} do {@code ApiConfig}.
 *
 * <p>O formato é o da <b>RFC 9457</b> ({@code application/problem+json}), um
 * padrão público para corpo de erro. Vale mais que um formato caseiro por um
 * motivo simples: ferramentas e bibliotecas já sabem interpretá-lo.
 */
@RestControllerAdvice
public class TratadorGlobalDeErros extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorGlobalDeErros.class);

    // O campo 'type' da RFC 9457 é um identificador estável do tipo de erro. O
    // cliente pode programar em cima dele sem depender do texto da mensagem, que
    // muda com correções de redação e tradução.
    private static final URI TIPO_VALIDACAO = URI.create("urn:catalogo:erro:validacao");
    private static final URI TIPO_REQUISICAO_INVALIDA = URI.create("urn:catalogo:erro:requisicao-invalida");
    private static final URI TIPO_NAO_ENCONTRADO = URI.create("urn:catalogo:erro:nao-encontrado");
    private static final URI TIPO_CONFLITO = URI.create("urn:catalogo:erro:conflito");
    private static final URI TIPO_INTERNO = URI.create("urn:catalogo:erro:interno");

    /** Um campo que falhou na validação, reportado dentro do corpo do erro. */
    public record CampoInvalido(String campo, String mensagem) {
    }

    /**
     * Recurso inexistente vira {@code 404 Not Found}, seja qual for a subclasse.
     *
     * <p>{@code @ExceptionHandler} diz ao Spring: "quando esta exceção escapar de
     * um controller, chame este método". O tipo declarado no parâmetro é o
     * critério de seleção.
     */
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail tratarNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado",
                excecao.getMessage(), TIPO_NAO_ENCONTRADO);
    }

    /**
     * Regra de negócio violada vira {@code 409 Conflict}: a requisição está bem
     * formada, mas o estado atual não permite atendê-la (por exemplo, SKU já usado).
     */
    @ExceptionHandler(RegraDeNegocioException.class)
    public ProblemDetail tratarRegraDeNegocio(RegraDeNegocioException excecao) {
        return problema(HttpStatus.CONFLICT, "Conflito com o estado atual",
                excecao.getMessage(), TIPO_CONFLITO);
    }

    /**
     * Pedido mal montado que só a aplicação detecta vira {@code 400 Bad Request}
     * (por exemplo, {@code ?sort=} com um campo que não existe).
     */
    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ProblemDetail tratarRequisicaoInvalida(RequisicaoInvalidaException excecao) {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida",
                excecao.getMessage(), TIPO_REQUISICAO_INVALIDA);
    }

    /**
     * Constraint do banco violada também vira {@code 409}.
     *
     * <p>A {@code SkuUnicoRegra} confere o SKU antes de gravar, mas duas requisições simultâneas
     * com o mesmo SKU podem passar juntas por essa conferência. Quem barra a
     * segunda é a constraint {@code uk_produto_sku}; sem este tratamento, ela
     * sairia como {@code 500}.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail tratarViolacaoDeIntegridade(DataIntegrityViolationException excecao) {
        log.warn("Violação de integridade no banco", excecao);
        return problema(HttpStatus.CONFLICT, "Conflito com o estado atual",
                "A operação viola uma restrição de unicidade ou integridade dos dados.", TIPO_CONFLITO);
    }

    /**
     * A rede de segurança: qualquer exceção não prevista.
     *
     * <p>O {@code log.error} guarda a pilha completa para quem opera o sistema; a
     * resposta devolve um texto genérico. Vazar a pilha para o cliente entregaria
     * nomes de classes, versões de bibliotecas e estrutura interna.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail tratarErroInesperado(Exception excecao) {
        log.error("Erro não tratado ao processar a requisição", excecao);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado ao processar a requisição.", TIPO_INTERNO);
    }

    /**
     * Falha de Bean Validation no corpo vira {@code 400 Bad Request}.
     *
     * <p>Este método sobrescreve um da classe-mãe — por isso {@code @Override} e
     * não {@code @ExceptionHandler}: o registro já vem de lá, nós só mudamos o que
     * ele produz.
     *
     * <p>A resposta lista <b>todos</b> os campos inválidos de uma vez, na
     * propriedade {@code campos}. Devolver um erro por vez obrigaria o cliente a
     * um vaivém de requisições para descobrir tudo que está errado.
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

    /** Corpo que não é JSON válido (ou tem um campo com tipo errado) vira {@code 400}. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException excecao,
                                                                  HttpHeaders cabecalhos,
                                                                  HttpStatusCode status,
                                                                  WebRequest requisicao) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "O corpo da requisição não é um JSON válido para este recurso.", TIPO_REQUISICAO_INVALIDA));
    }

    /**
     * Parâmetro que não converte para o tipo esperado vira {@code 400} — por
     * exemplo {@code /produtos/abc}, em que {@code abc} deveria ser um número.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException excecao,
                                                        HttpHeaders cabecalhos,
                                                        HttpStatusCode status,
                                                        WebRequest requisicao) {
        String detalhe = "O valor '" + excecao.getValue() + "' não é válido para o parâmetro '"
                + excecao.getPropertyName() + "'.";
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "Requisição inválida",
                detalhe, TIPO_REQUISICAO_INVALIDA));
    }

    /**
     * Passagem final de todos os erros que a classe-mãe trata (método não
     * suportado, rota inexistente e outros).
     *
     * <p>Interceptamos aqui só para acrescentar o {@code timestamp}, de modo que
     * <b>toda</b> resposta de erro da API tenha a mesma estrutura, venha ela do
     * nosso código ou do Spring MVC.
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

    /** Monta o corpo no formato RFC 9457, sempre com os mesmos campos. */
    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe, URI tipo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setType(tipo);
        problema.setProperty("timestamp", Instant.now());
        return problema;
    }
}
