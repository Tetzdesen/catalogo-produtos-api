package br.com.catalogo.controller;

import br.com.catalogo.config.OpenApiConfig;
import br.com.catalogo.dto.PaginaResponse;
import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.dto.ProdutoResponse;
import br.com.catalogo.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Os endpoints HTTP de produto.
 *
 * <p>Só traduz HTTP para chamadas ao service e de volta: nenhuma regra de
 * negócio mora aqui. O prefixo {@code /api/v1} não aparece no mapeamento porque
 * é aplicado a todo o pacote pela {@code ApiConfig}.
 *
 * <p>{@code @RestController} = {@code @Controller} + {@code @ResponseBody}: o
 * retorno de cada método é serializado em JSON no corpo da resposta.
 *
 * <p>O campo {@code service} é do tipo da <b>interface</b> {@link ProdutoService}.
 * O controller não sabe que existe um {@code ProdutoServiceImpl}: o Spring
 * encontra o único bean que implementa a interface e o entrega no construtor.
 *
 * <p>Quem pode chamar cada rota não é decidido aqui, e sim na {@code SegurancaConfig}.
 * O {@code @SecurityRequirement} só documenta, no Swagger, quais exigem token.
 */
@RestController
@RequestMapping("/produtos")
@Tag(name = "Produtos", description = "Cadastro e consulta de produtos do catálogo")
public class ProdutoController {

    private final ProdutoService service;

    public ProdutoController(ProdutoService service) {
        this.service = service;
    }

    /**
     * {@code 201 Created} com o cabeçalho {@code Location} apontando para o
     * produto criado: é assim que o HTTP diz "criei, e ele está aqui".
     */
    @PostMapping
    @Operation(summary = "Cadastra um produto (nasce ativo)")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
    public ResponseEntity<ProdutoResponse> criar(@Valid @RequestBody ProdutoRequest dados) {
        ProdutoResponse criado = service.criar(dados);
        URI local = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(local).body(criado);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um produto pelo identificador")
    public ProdutoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    /**
     * {@code Pageable} é montado pelo Spring a partir de {@code ?page=}, {@code ?size=}
     * e {@code ?sort=}, já respeitando o padrão e o teto do {@code application.yml}.
     */
    @GetMapping
    @Operation(summary = "Lista produtos paginados, com filtro opcional por trecho do nome")
    public PaginaResponse<ProdutoResponse> listar(
            @Parameter(description = "Trecho do nome, sem distinção de maiúsculas e minúsculas")
            @RequestParam(required = false) String nome,
            @ParameterObject Pageable paginacao) {
        return service.listar(nome, paginacao);
    }

    /**
     * {@code @Validated} com os grupos {@code Default} e {@code Atualizacao}
     * confere as regras de sempre e também as que só valem aqui ({@code ativo}
     * obrigatório). O {@code @Valid} do cadastro confere só o grupo {@code Default}.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Substitui os dados de um produto")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
    public ProdutoResponse atualizar(@PathVariable Long id,
                                     @Validated({Default.class, ProdutoRequest.Atualizacao.class})
                                     @RequestBody ProdutoRequest dados) {
        return service.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um produto definitivamente")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
