package br.com.catalogo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.support.TesteDeRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@TesteDeRepositorio
@DisplayName("CategoriaRepository")
class CategoriaRepositoryTest {

    @Autowired
    private CategoriaRepository repositorio;

    @BeforeEach
    void preparar() {
        repositorio.save(new Categoria("Ferramentas", "Itens de oficina"));
    }

    @Test
    @DisplayName("reconhece nome existente ignorando diferenças de caixa")
    void existsByNomeIgnoreCase() {
        assertThat(repositorio.existsByNomeIgnoreCase("ferramentas")).isTrue();
        assertThat(repositorio.existsByNomeIgnoreCase("FERRAMENTAS")).isTrue();
        assertThat(repositorio.existsByNomeIgnoreCase("Ferramenta")).isFalse();
    }

    @Test
    @DisplayName("ao atualizar, não acusa conflito do nome com a própria categoria")
    void existsByNomeIgnoreCaseAndIdNot() {
        Categoria existente = repositorio.findByNomeIgnoreCase("Ferramentas").orElseThrow();

        assertThat(repositorio.existsByNomeIgnoreCaseAndIdNot("ferramentas", existente.getId())).isFalse();
        assertThat(repositorio.existsByNomeIgnoreCaseAndIdNot("ferramentas", existente.getId() + 1)).isTrue();
    }

    @Test
    @DisplayName("busca por nome sem distinção de caixa")
    void findByNomeIgnoreCase() {
        assertThat(repositorio.findByNomeIgnoreCase("FeRrAmEnTaS"))
                .isPresent()
                .get()
                .extracting(Categoria::getDescricao)
                .isEqualTo("Itens de oficina");
    }
}
