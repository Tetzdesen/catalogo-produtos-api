package br.com.catalogo.controller;

import br.com.catalogo.domain.TipoMovimentacao;
import br.com.catalogo.dto.AjusteRequest;
import br.com.catalogo.dto.MovimentacaoRequest;
import br.com.catalogo.dto.MovimentacaoResponse;
import br.com.catalogo.dto.PaginaResponse;
import br.com.catalogo.service.MovimentacaoEstoqueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de movimentação de estoque.
 *
 * <p>Os caminhos são aninhados sob o produto ({@code /produtos/{id}/movimentacoes})
 * porque uma movimentação não existe sozinha: ela é sempre de um produto, e o
 * endereço deixa isso explícito.
 *
 * <p>Repare no que <b>não</b> existe aqui: nenhum PUT, PATCH ou DELETE. A
 * imutabilidade do histórico não é uma convenção documentada, é a ausência de
 * rota — corrigir um lançamento significa registrar um ajuste.
 */
@RestController
@RequestMapping("/produtos/{produtoId}/movimentacoes")
@Tag(name = "Movimentações de estoque", description = "Entradas, saídas, ajustes e histórico")
public class MovimentacaoEstoqueController {

    private final MovimentacaoEstoqueService service;

    public MovimentacaoEstoqueController(MovimentacaoEstoqueService service) {
        this.service = service;
    }

    @PostMapping("/entradas")
    @Operation(summary = "Registra uma entrada de estoque")
    public ResponseEntity<MovimentacaoResponse> registrarEntrada(
            @PathVariable Long produtoId,
            @Valid @RequestBody MovimentacaoRequest requisicao) {
        var movimentacao = MovimentacaoResponse.de(service.registrarEntrada(produtoId, requisicao));
        return ResponseEntity.status(HttpStatus.CREATED).body(movimentacao);
    }

    @PostMapping("/saidas")
    @Operation(summary = "Registra uma saída de estoque; recusa saldo insuficiente")
    public ResponseEntity<MovimentacaoResponse> registrarSaida(
            @PathVariable Long produtoId,
            @Valid @RequestBody MovimentacaoRequest requisicao) {
        var movimentacao = MovimentacaoResponse.de(service.registrarSaida(produtoId, requisicao));
        return ResponseEntity.status(HttpStatus.CREATED).body(movimentacao);
    }

    @PostMapping("/ajustes")
    @Operation(summary = "Ajusta o saldo para um valor absoluto, registrando a diferença")
    public ResponseEntity<MovimentacaoResponse> registrarAjuste(
            @PathVariable Long produtoId,
            @Valid @RequestBody AjusteRequest requisicao) {
        var movimentacao = MovimentacaoResponse.de(service.registrarAjuste(produtoId, requisicao));
        return ResponseEntity.status(HttpStatus.CREATED).body(movimentacao);
    }

    /**
     * Ordenação padrão por data decrescente: quem abre o histórico quer ver o que
     * aconteceu por último.
     */
    @GetMapping
    @Operation(summary = "Lista o histórico do produto, da movimentação mais recente para a mais antiga")
    public PaginaResponse<MovimentacaoResponse> listarHistorico(
            @PathVariable Long produtoId,
            @RequestParam(required = false) TipoMovimentacao tipo,
            @PageableDefault(size = 20, sort = "dataHora", direction = Sort.Direction.DESC) Pageable paginacao) {
        return PaginaResponse.de(service.listarHistorico(produtoId, tipo, paginacao), MovimentacaoResponse::de);
    }
}
