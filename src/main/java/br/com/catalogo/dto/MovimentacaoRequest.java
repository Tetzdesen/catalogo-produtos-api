package br.com.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Entrada ou saída de estoque.
 *
 * <p>O motivo é obrigatório de propósito: um histórico de movimentações sem
 * justificativa responde "quanto mudou", mas não "por quê" — que é a pergunta que
 * se faz quando o inventário não bate.
 */
@Schema(description = "Entrada ou saída de estoque")
public record MovimentacaoRequest(

        @Schema(example = "10", description = "Unidades movimentadas; deve ser maior que zero")
        @NotNull(message = "A quantidade é obrigatória.")
        @Positive(message = "A quantidade deve ser maior que zero.")
        Integer quantidade,

        @Schema(example = "Compra do fornecedor XYZ")
        @NotBlank(message = "O motivo é obrigatório.")
        @Size(max = 120, message = "O motivo deve ter no máximo 120 caracteres.")
        String motivo) {
}
