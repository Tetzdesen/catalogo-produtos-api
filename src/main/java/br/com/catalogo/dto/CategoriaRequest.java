package br.com.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para criar ou atualizar uma categoria.
 *
 * <p>É um {@code record} e não a entidade: se o cliente enviasse a entidade,
 * poderia tentar definir o {@code id} e o contrato público ficaria amarrado ao
 * schema do banco. As restrições aqui são as mesmas da coluna correspondente —
 * validar antes de chegar ao banco devolve {@code 400} com a lista de campos em
 * vez de um erro de constraint.
 *
 * <p>{@code @NotBlank} (e não {@code @NotNull}) porque um nome com apenas espaços
 * é tão inválido quanto um nome ausente.
 */
@Schema(description = "Dados de criação ou atualização de uma categoria")
public record CategoriaRequest(

        @Schema(example = "Ferramentas")
        @NotBlank(message = "O nome é obrigatório.")
        @Size(min = 2, max = 60, message = "O nome deve ter entre 2 e 60 caracteres.")
        String nome,

        @Schema(example = "Itens de oficina e manutenção")
        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
        String descricao) {
}
