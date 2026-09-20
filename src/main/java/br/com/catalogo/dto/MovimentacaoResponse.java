package br.com.catalogo.dto;

import br.com.catalogo.domain.MovimentacaoEstoque;
import br.com.catalogo.domain.TipoMovimentacao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Registro de movimentação devolvido pela API.
 *
 * <p>Traz {@code saldoResultante} para que o cliente que acabou de registrar uma
 * movimentação já saiba o saldo novo, sem uma segunda requisição, e para que o
 * histórico seja auditável linha a linha.
 *
 * @param diferenca o que foi somado ao saldo; negativo em saídas e ajustes para baixo
 */
@Schema(description = "Movimentação registrada no histórico de estoque")
public record MovimentacaoResponse(Long id,
                                   Long produtoId,
                                   TipoMovimentacao tipo,
                                   int quantidade,
                                   int diferenca,
                                   int saldoResultante,
                                   String motivo,
                                   Instant dataHora) {

    public static MovimentacaoResponse de(MovimentacaoEstoque movimentacao) {
        return new MovimentacaoResponse(
                movimentacao.getId(),
                movimentacao.getProduto().getId(),
                movimentacao.getTipo(),
                movimentacao.getQuantidade(),
                movimentacao.getDiferenca(),
                movimentacao.getSaldoResultante(),
                movimentacao.getMotivo(),
                movimentacao.getDataHora());
    }
}
