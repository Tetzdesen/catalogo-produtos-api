package br.com.catalogo.service.regra;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.exception.SkuEmUsoException;
import br.com.catalogo.repository.ProdutoRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * O repositório é um <i>mock</i> do Mockito: um objeto falso que responde o que o
 * teste mandar. Assim a regra é testada sem banco, e cada cenário diz
 * explicitamente qual resposta do banco está simulando.
 */
class SkuUnicoRegraTest {

    private final ProdutoRepository repositorio = mock(ProdutoRepository.class);
    private final SkuUnicoRegra regra = new SkuUnicoRegra(repositorio);

    private final ProdutoRequest dados = new ProdutoRequest("CAF-001", "Café Torrado", null,
            new BigDecimal("24.90"), true);

    @Test
    void cadastroComSkuLivrePassa() {
        when(repositorio.existsBySku("CAF-001")).thenReturn(false);

        assertThatCode(() -> regra.validar(dados, null)).doesNotThrowAnyException();
    }

    @Test
    void cadastroComSkuOcupadoFalha() {
        when(repositorio.existsBySku("CAF-001")).thenReturn(true);

        assertThatThrownBy(() -> regra.validar(dados, null))
                .isInstanceOf(SkuEmUsoException.class)
                .hasMessage("O SKU 'CAF-001' já está em uso por outro produto.");
    }

    @Test
    void atualizacaoMantendoOProprioSkuPassa() {
        // existsBySkuAndIdNot ignora o produto 7: só ele tem o SKU, então "false".
        when(repositorio.existsBySkuAndIdNot("CAF-001", 7L)).thenReturn(false);

        assertThatCode(() -> regra.validar(dados, 7L)).doesNotThrowAnyException();
    }

    @Test
    void atualizacaoComSkuDeOutroProdutoFalha() {
        when(repositorio.existsBySkuAndIdNot("CAF-001", 7L)).thenReturn(true);

        assertThatThrownBy(() -> regra.validar(dados, 7L)).isInstanceOf(SkuEmUsoException.class);
    }
}
