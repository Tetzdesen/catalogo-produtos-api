package br.com.catalogo.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * O envelope de toda listagem paginada da API.
 *
 * <p>É parte do contrato público, aquilo que o cliente HTTP recebe.
 *
 * <p><b>Por que não serializar o {@code Page} do Spring Data direto?</b> Porque
 * ele é uma classe interna do framework, com campos como {@code pageable},
 * {@code sort} e {@code numberOfElements} que já mudaram de forma entre versões —
 * o próprio Spring desaconselha expô-lo. Serializá-lo significaria que uma
 * atualização de dependência poderia quebrar todos os clientes da API, sem
 * ninguém ter decidido mudar o contrato. Este {@code record} congela o formato:
 * cinco campos escolhidos por nós, que só mudam quando nós quisermos.
 *
 * <p>{@code <T>} deixa o envelope servir a qualquer conteúdo: uma página de
 * produtos e a de qualquer recurso futuro usam o mesmo formato, e quem consome a
 * API aprende a lê-lo uma vez só.
 */
public record PaginaResponse<T>(List<T> conteudo, int pagina, int tamanho,
                                long totalElementos, int totalPaginas) {

    /**
     * Converte a página do Spring Data no envelope da API, aplicando
     * {@code conversor} a cada item.
     *
     * <p>Este método é a fronteira: o {@code Page} entra, e daqui para fora só
     * existe {@code PaginaResponse}. Concentrar a conversão aqui é o que garante
     * que nenhum controller devolva o tipo do framework por descuido.
     */
    public static <O, T> PaginaResponse<T> de(Page<O> pagina, Function<O, T> conversor) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(conversor).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
