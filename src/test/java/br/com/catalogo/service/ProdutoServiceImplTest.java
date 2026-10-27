package br.com.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.catalogo.dto.PaginaResponse;
import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.dto.ProdutoResponse;
import br.com.catalogo.exception.ProdutoNaoEncontradoException;
import br.com.catalogo.exception.SkuEmUsoException;
import br.com.catalogo.mapper.ProdutoMapper;
import br.com.catalogo.model.Produto;
import br.com.catalogo.repository.ProdutoRepository;
import br.com.catalogo.service.regra.RegraDeProduto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * O service montado com {@code new}, sem Spring: o repositório e a regra são
 * mocks, o mapper é o de verdade. Só é possível porque todas as dependências
 * chegam pelo construtor (inversão de dependência na prática).
 */
class ProdutoServiceImplTest {

    private final ProdutoRepository repositorio = mock(ProdutoRepository.class);
    private final RegraDeProduto regra = mock(RegraDeProduto.class);
    private final ProdutoService service =
            new ProdutoServiceImpl(repositorio, new ProdutoMapper(), List.of(regra));

    private final ProdutoRequest dados = new ProdutoRequest("CAF-001", "Café Torrado", null,
            new BigDecimal("24.90"), true);

    @Test
    void criarExecutaAsRegrasESalva() {
        when(repositorio.save(any(Produto.class))).thenAnswer(chamada -> chamada.getArgument(0));

        ProdutoResponse criado = service.criar(dados);

        verify(regra).validar(dados, null);
        verify(repositorio).save(any(Produto.class));
        assertThat(criado.sku()).isEqualTo("CAF-001");
        assertThat(criado.ativo()).isTrue();
    }

    @Test
    void regraVioladaImpedeOSave() {
        doThrow(new SkuEmUsoException("CAF-001")).when(regra).validar(dados, null);

        assertThatThrownBy(() -> service.criar(dados)).isInstanceOf(SkuEmUsoException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    void buscarIdInexistenteLancaNaoEncontrado() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(99L))
                .isInstanceOf(ProdutoNaoEncontradoException.class)
                .hasMessage("Produto 99 não encontrado.");
    }

    @Test
    void atualizarValidaAntesDeAlterarAEntidade() {
        Produto existente = Produto.novo("CAF-001", "Café", null, new BigDecimal("10.00"));
        when(repositorio.findById(7L)).thenReturn(Optional.of(existente));
        ProdutoRequest novosDados = new ProdutoRequest("CAF-002", "Café Moído", null,
                new BigDecimal("19.90"), false);
        doThrow(new SkuEmUsoException("CAF-002")).when(regra).validar(novosDados, 7L);

        assertThatThrownBy(() -> service.atualizar(7L, novosDados)).isInstanceOf(SkuEmUsoException.class);
        // A entidade não foi tocada: a regra falhou antes do produto.atualizar(...).
        assertThat(existente.getSku()).isEqualTo("CAF-001");
    }

    @Test
    void atualizarIdInexistenteLancaNaoEncontrado() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(99L, dados))
                .isInstanceOf(ProdutoNaoEncontradoException.class);
    }

    @Test
    void excluirIdInexistenteLancaNaoEncontrado() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(99L)).isInstanceOf(ProdutoNaoEncontradoException.class);
        verify(repositorio, never()).delete(any());
    }

    @Test
    void listarSemTrechoUsaFindAll() {
        Pageable paginacao = PageRequest.of(0, 20);
        when(repositorio.findAll(paginacao)).thenReturn(Page.empty(paginacao));

        PaginaResponse<ProdutoResponse> pagina = service.listar("   ", paginacao);

        verify(repositorio).findAll(paginacao);
        verify(repositorio, never()).buscarPorNome(anyString(), any());
        assertThat(pagina.totalElementos()).isZero();
    }

    @Test
    void listarComTrechoUsaBuscaPorNomeSemEspacosNasPontas() {
        Pageable paginacao = PageRequest.of(0, 20);
        Produto cafe = Produto.novo("CAF-001", "Café Torrado", null, new BigDecimal("24.90"));
        when(repositorio.buscarPorNome("cafe", paginacao))
                .thenReturn(new PageImpl<>(List.of(cafe), paginacao, 1));

        PaginaResponse<ProdutoResponse> pagina = service.listar("  cafe ", paginacao);

        assertThat(pagina.conteudo()).extracting(ProdutoResponse::nome).containsExactly("Café Torrado");
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }
}
