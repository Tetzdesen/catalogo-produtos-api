package br.com.catalogo.controller;

import br.com.catalogo.dto.CategoriaRequest;
import br.com.catalogo.dto.CategoriaResponse;
import br.com.catalogo.dto.PaginaResponse;
import br.com.catalogo.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de categoria.
 *
 * <p>O controller faz três coisas e nada mais: recebe e valida a entrada
 * ({@code @Valid}), delega ao service e traduz o resultado em uma resposta HTTP.
 * Não há {@code try/catch} aqui — erros viram status code no tratador global.
 *
 * <p>O caminho não repete {@code /api/v1}: esse prefixo é aplicado a todo o pacote
 * por {@code ApiConfig}.
 */
@RestController
@RequestMapping("/categorias")
@Tag(name = "Categorias", description = "Organização do catálogo em categorias")
public class CategoriaController {

    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cria uma categoria")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "409", description = "Nome já utilizado", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<CategoriaResponse> criar(@Valid @RequestBody CategoriaRequest requisicao) {
        CategoriaResponse criada = CategoriaResponse.de(service.criar(requisicao));
        // 201 com Location: o cliente recebe o endereço do recurso que acabou de criar,
        // sem precisar montar a URL por conta própria.
        return ResponseEntity.created(URI.create("/api/v1/categorias/" + criada.id())).body(criada);
    }

    @GetMapping
    @Operation(summary = "Lista categorias de forma paginada")
    public PaginaResponse<CategoriaResponse> listar(
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable paginacao) {
        return PaginaResponse.de(service.listar(paginacao), CategoriaResponse::de);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma categoria por identificador")
    public CategoriaResponse buscar(@PathVariable Long id) {
        return CategoriaResponse.de(service.buscarPorId(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza uma categoria")
    public CategoriaResponse atualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest requisicao) {
        return CategoriaResponse.de(service.atualizar(id, requisicao));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma categoria sem produtos vinculados")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        // 204: a exclusão deu certo e não há corpo a devolver.
        return ResponseEntity.noContent().build();
    }
}
