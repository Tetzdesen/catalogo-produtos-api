package br.com.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.catalogo.domain.Categoria;
import br.com.catalogo.dto.CategoriaRequest;
import br.com.catalogo.exception.RecursoNaoEncontradoException;
import br.com.catalogo.exception.RegraDeNegocioException;
import br.com.catalogo.repository.CategoriaRepository;
import br.com.catalogo.repository.ProdutoRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Testes unitários das regras de categoria.
 *
 * <p>Com os repositórios mockados, cada cenário da spec vira um teste que roda em
 * milissegundos e não depende de banco. O contrato HTTP dessas mesmas regras é
 * verificado separadamente, em {@code CategoriaControllerIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategoriaService")
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private ProdutoRepository produtoRepository;

    @InjectMocks
    private CategoriaService service;

    @Nested
    @DisplayName("ao criar")
    class AoCriar {

        @Test
        @DisplayName("persiste a categoria quando o nome é inédito")
        void criaComSucesso() {
            var requisicao = new CategoriaRequest("Ferramentas", "Itens de oficina");
            when(categoriaRepository.existsByNomeIgnoreCase("Ferramentas")).thenReturn(false);
            when(categoriaRepository.save(any(Categoria.class))).thenAnswer(chamada -> chamada.getArgument(0));

            Categoria criada = service.criar(requisicao);

            assertThat(criada.getNome()).isEqualTo("Ferramentas");
            assertThat(criada.getDescricao()).isEqualTo("Itens de oficina");
            verify(categoriaRepository).save(any(Categoria.class));
        }

        @Test
        @DisplayName("remove espaços nas extremidades do nome antes de persistir")
        void normalizaONome() {
            when(categoriaRepository.existsByNomeIgnoreCase("Ferramentas")).thenReturn(false);
            when(categoriaRepository.save(any(Categoria.class))).thenAnswer(chamada -> chamada.getArgument(0));

            Categoria criada = service.criar(new CategoriaRequest("  Ferramentas  ", null));

            assertThat(criada.getNome()).isEqualTo("Ferramentas");
        }

        @Test
        @DisplayName("rejeita nome já usado, ignorando a caixa")
        void rejeitaNomeDuplicado() {
            when(categoriaRepository.existsByNomeIgnoreCase("ferramentas")).thenReturn(true);

            assertThatThrownBy(() -> service.criar(new CategoriaRequest("ferramentas", null)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Já existe uma categoria");

            verify(categoriaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("ao consultar")
    class AoConsultar {

        @Test
        @DisplayName("devolve a categoria existente")
        void buscaPorId() {
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(new Categoria("Ferramentas", null)));

            assertThat(service.buscarPorId(1L).getNome()).isEqualTo("Ferramentas");
        }

        @Test
        @DisplayName("falha quando o id não existe")
        void falhaQuandoNaoEncontra() {
            when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.buscarPorId(99L))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining("Categoria");
        }
    }

    @Nested
    @DisplayName("ao atualizar")
    class AoAtualizar {

        @Test
        @DisplayName("altera nome e descrição")
        void atualizaComSucesso() {
            var existente = new Categoria("Ferramentas", "Antiga");
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(categoriaRepository.existsByNomeIgnoreCaseAndIdNot("Ferramentas Elétricas", 1L)).thenReturn(false);

            Categoria atualizada = service.atualizar(1L, new CategoriaRequest("Ferramentas Elétricas", "Nova"));

            assertThat(atualizada.getNome()).isEqualTo("Ferramentas Elétricas");
            assertThat(atualizada.getDescricao()).isEqualTo("Nova");
        }

        @Test
        @DisplayName("rejeita nome que pertence a outra categoria e não altera os dados")
        void rejeitaNomeDeOutraCategoria() {
            var existente = new Categoria("Ferramentas", "Antiga");
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(existente));
            when(categoriaRepository.existsByNomeIgnoreCaseAndIdNot("Jardinagem", 1L)).thenReturn(true);

            assertThatThrownBy(() -> service.atualizar(1L, new CategoriaRequest("Jardinagem", "Nova")))
                    .isInstanceOf(RegraDeNegocioException.class);

            assertThat(existente.getNome()).isEqualTo("Ferramentas");
            assertThat(existente.getDescricao()).isEqualTo("Antiga");
        }

        @Test
        @DisplayName("falha quando a categoria não existe")
        void falhaQuandoNaoEncontra() {
            when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.atualizar(99L, new CategoriaRequest("Qualquer", null)))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }
    }

    @Nested
    @DisplayName("ao excluir")
    class AoExcluir {

        @Test
        @DisplayName("remove a categoria sem produtos vinculados")
        void excluiComSucesso() {
            var categoria = new Categoria("Jardinagem", null);
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
            when(produtoRepository.existsByCategoriaId(1L)).thenReturn(false);

            service.excluir(1L);

            verify(categoriaRepository).delete(categoria);
        }

        @Test
        @DisplayName("bloqueia a exclusão quando existem produtos vinculados")
        void bloqueiaExclusaoComProdutos() {
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(new Categoria("Ferramentas", null)));
            when(produtoRepository.existsByCategoriaId(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.excluir(1L))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("produtos vinculados");

            verify(categoriaRepository, never()).delete(any());
        }
    }
}
