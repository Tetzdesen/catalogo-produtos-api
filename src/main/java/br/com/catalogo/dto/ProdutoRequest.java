package br.com.catalogo.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * O corpo aceito no cadastro ({@code POST}) e na atualização ({@code PUT}).
 *
 * <p>As anotações são do Bean Validation: com {@code @Valid} no parâmetro do
 * controller, o Spring confere cada regra antes de chamar o método e, se alguma
 * falhar, responde {@code 400} sem que o service chegue a rodar.
 *
 * <p>{@code ativo} só é lido na atualização. No cadastro é ignorado, porque todo
 * produto nasce ativo; na atualização é obrigatório. Para a mesma classe servir
 * aos dois casos, o {@code @NotNull} de {@code ativo} pertence ao grupo
 * {@link Atualizacao}: ele só é conferido quando o controller pede esse grupo,
 * o que acontece apenas no {@code PUT}.
 */
public record ProdutoRequest(

        @NotBlank @Size(max = 40)
        String sku,

        @NotBlank @Size(max = 120)
        String nome,

        @Size(max = 500)
        String descricao,

        // @Digits recusa 10.999 em vez de arredondar para 11.00 sem avisar.
        @NotNull @Positive
        @Digits(integer = 10, fraction = 2, message = "deve ter no máximo 10 dígitos inteiros e 2 casas decimais")
        BigDecimal preco,

        @NotNull(groups = Atualizacao.class)
        Boolean ativo) {

    /**
     * Grupo de validação das regras que só valem na atualização. Uma interface
     * vazia: serve apenas de etiqueta para o Bean Validation.
     */
    public interface Atualizacao {
    }
}
