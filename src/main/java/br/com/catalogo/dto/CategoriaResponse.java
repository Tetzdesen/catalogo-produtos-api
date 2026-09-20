package br.com.catalogo.dto;

import br.com.catalogo.domain.Categoria;
import io.swagger.v3.oas.annotations.media.Schema;

/** Representação de uma categoria devolvida pela API. */
@Schema(description = "Categoria do catálogo")
public record CategoriaResponse(Long id, String nome, String descricao) {

    public static CategoriaResponse de(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome(), categoria.getDescricao());
    }
}
