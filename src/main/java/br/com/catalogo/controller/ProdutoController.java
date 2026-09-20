package br.com.catalogo.controller;

import br.com.catalogo.dto.PaginaResponse;
import br.com.catalogo.dto.ProdutoFiltro;
import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.dto.ProdutoResponse;
import br.com.catalogo.service.ProdutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de produto.
 *
 * <p>Os métodos de leitura são {@code @Transactional(readOnly = true)} porque o
 * {@code ProdutoResponse} acessa a categoria, que é LAZY: sem uma transação aberta
 * na conversão, o acesso falharia ({@code open-in-view} está desligado de
 * propósito). Manter a transação aqui, e não reabri-la globalmente na view, deixa
 * explícito quem precisa dela.
 */
@RestController
@RequestMapping("/produtos")
@Tag(name = "Produtos", description = "Cadastro e consulta de produtos do catálogo")
public class ProdutoController {

    private final ProdutoService service;

    public ProdutoController(ProdutoService service) {
        this.service = service;
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Cria um produto, ativo e com saldo de estoque zero")
    public ResponseEntity<ProdutoResponse> criar(@Valid @RequestBody ProdutoRequest requisicao) {
        ProdutoResponse criado = ProdutoResponse.de(service.criar(requisicao));
        return ResponseEntity.created(URI.create("/api/v1/produtos/" + criado.id())).body(criado);
    }

    /**
     * Os filtros chegam como parâmetros de query e o Spring os agrupa no record
     * {@link ProdutoFiltro} — todos opcionais.
     */
    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Lista produtos com filtros opcionais e combináveis")
    public PaginaResponse<ProdutoResponse> listar(
            ProdutoFiltro filtro,
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable paginacao) {
        return PaginaResponse.de(service.listar(filtro, paginacao), ProdutoResponse::de);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Busca um produto por identificador")
    public ProdutoResponse buscar(@PathVariable Long id) {
        return ProdutoResponse.de(service.buscarPorId(id));
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Atualiza um produto; o SKU e o saldo de estoque não mudam")
    public ProdutoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ProdutoRequest requisicao) {
        return ProdutoResponse.de(service.atualizar(id, requisicao));
    }

    /**
     * PATCH e não PUT: ativar ou inativar altera um aspecto do recurso, não o
     * substitui. São dois caminhos distintos em vez de um corpo com a nova
     * situação — a intenção fica no endereço e o endpoint dispensa payload.
     */
    @PatchMapping("/{id}/ativacao")
    @Transactional
    @Operation(summary = "Reativa um produto")
    public ProdutoResponse ativar(@PathVariable Long id) {
        return ProdutoResponse.de(service.alterarSituacao(id, true));
    }

    @PatchMapping("/{id}/inativacao")
    @Transactional
    @Operation(summary = "Inativa um produto, retirando-o do catálogo sem perder o histórico")
    public ProdutoResponse inativar(@PathVariable Long id) {
        return ProdutoResponse.de(service.alterarSituacao(id, false));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um produto que ainda não teve movimentações de estoque")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
