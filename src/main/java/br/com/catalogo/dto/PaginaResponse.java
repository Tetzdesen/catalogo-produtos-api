package br.com.catalogo.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Envelope de paginação da API.
 *
 * <p>Existe para não serializar {@code Page} diretamente: o JSON do {@code Page}
 * do Spring Data é um detalhe interno do framework, muda entre versões e o próprio
 * Spring avisa contra expô-lo. Com um record próprio, o contrato é nosso e estável.
 *
 * @param conteudo       itens da página atual
 * @param pagina         índice da página, começando em zero
 * @param tamanho        quantidade de itens por página efetivamente aplicada
 * @param totalElementos total de itens que satisfazem a consulta
 * @param totalPaginas   total de páginas disponíveis
 */
public record PaginaResponse<T>(List<T> conteudo,
                                int pagina,
                                int tamanho,
                                long totalElementos,
                                int totalPaginas) {

    /** Converte uma página de entidades em uma página de DTOs, preservando os metadados. */
    public static <E, D> PaginaResponse<D> de(Page<E> pagina, Function<E, D> conversor) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(conversor).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
