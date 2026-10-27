package br.com.catalogo.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.dto.ProdutoResponse;
import br.com.catalogo.model.Produto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProdutoMapperTest {

    private final ProdutoMapper mapper = new ProdutoMapper();

    @Test
    void requestViraProdutoNovoAtivoIgnorandoOCampoAtivo() {
        ProdutoRequest dados = new ProdutoRequest("CAF-001", "Café Torrado", "500 g",
                new BigDecimal("24.90"), false);

        Produto produto = mapper.paraEntidade(dados);

        assertThat(produto.getId()).isNull();
        assertThat(produto.getSku()).isEqualTo("CAF-001");
        assertThat(produto.getNome()).isEqualTo("Café Torrado");
        assertThat(produto.getDescricao()).isEqualTo("500 g");
        assertThat(produto.getPreco()).isEqualByComparingTo("24.90");
        // No cadastro, todo produto nasce ativo, seja qual for o valor enviado.
        assertThat(produto.isAtivo()).isTrue();
    }

    @Test
    void produtoViraRespostaComTodosOsCampos() {
        Produto produto = Produto.novo("CAF-001", "Café Torrado", null, new BigDecimal("24.90"));

        ProdutoResponse resposta = mapper.paraResposta(produto);

        assertThat(resposta).isEqualTo(new ProdutoResponse(null, "CAF-001", "Café Torrado", null,
                new BigDecimal("24.90"), true));
    }
}
