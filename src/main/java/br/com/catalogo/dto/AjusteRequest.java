package br.com.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Correção de inventário.
 *
 * <p>Diferente de {@link MovimentacaoRequest}, a quantidade aqui é o saldo final
 * desejado, não um incremento — é assim que se registra uma contagem física. Por
 * isso {@code @PositiveOrZero}: zerar o estoque é um ajuste legítimo, enquanto
 * uma entrada ou saída de zero unidades não significa nada.
 */
@Schema(description = "Ajuste de inventário: define o saldo em um valor absoluto")
public record AjusteRequest(

        @Schema(example = "20", description = "Saldo final desejado; zero ou maior")
        @NotNull(message = "A quantidade é obrigatória.")
        @PositiveOrZero(message = "A quantidade ajustada não pode ser negativa.")
        Integer quantidade,

        @Schema(example = "Contagem física de inventário")
        @NotBlank(message = "O motivo é obrigatório.")
        @Size(max = 120, message = "O motivo deve ter no máximo 120 caracteres.")
        String motivo) {
}
