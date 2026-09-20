package br.com.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dados de entrada para criar ou atualizar um produto.
 *
 * <p>Na atualização o SKU continua sendo aceito no corpo — e comparado com o atual
 * — para que uma tentativa de alterá-lo receba uma resposta explícita
 * ({@code 409}) em vez de ser ignorada em silêncio.
 *
 * <p>{@code @Digits(integer = 10, fraction = 2)} espelha a coluna
 * {@code NUMERIC(12,2)}: preço com três casas é rejeitado na borda, e não pelo
 * arredondamento do banco.
 */
@Schema(description = "Dados de criação ou atualização de um produto")
public record ProdutoRequest(

        @Schema(example = "MART-001", description = "Identificador de negócio, único e imutável")
        @NotBlank(message = "O SKU é obrigatório.")
        @Size(max = 40, message = "O SKU deve ter no máximo 40 caracteres.")
        String sku,

        @Schema(example = "Martelo de unha 25mm")
        @NotBlank(message = "O nome é obrigatório.")
        @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres.")
        String nome,

        @Schema(example = "Cabo de madeira, 500g")
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.")
        String descricao,

        @Schema(example = "49.90")
        @NotNull(message = "O preço é obrigatório.")
        @Positive(message = "O preço deve ser maior que zero.")
        @Digits(integer = 10, fraction = 2, message = "O preço deve ter no máximo duas casas decimais.")
        BigDecimal preco,

        @Schema(example = "1")
        @NotNull(message = "A categoria é obrigatória.")
        Long categoriaId) {
}
