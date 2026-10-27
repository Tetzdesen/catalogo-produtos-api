package br.com.catalogo.service.regra;

import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.exception.SkuEmUsoException;
import br.com.catalogo.repository.ProdutoRepository;
import org.springframework.stereotype.Component;

/**
 * O SKU identifica o produto para quem usa a API, então não pode se repetir.
 *
 * <p>Esta conferência dá a mensagem clara ao cliente. Quem garante de fato a
 * unicidade é a constraint {@code uk_produto_sku} do banco: duas requisições
 * simultâneas podem passar juntas por aqui, e a segunda é barrada lá.
 */
@Component
public class SkuUnicoRegra implements RegraDeProduto {

    private final ProdutoRepository repositorio;

    public SkuUnicoRegra(ProdutoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void validar(ProdutoRequest dados, Long idDoProduto) {
        // Na atualização, o próprio produto não conta ("AndIdNot"); senão manter o
        // SKU que ele já tem seria acusado como conflito.
        boolean emUso = (idDoProduto == null)
                ? repositorio.existsBySku(dados.sku())
                : repositorio.existsBySkuAndIdNot(dados.sku(), idDoProduto);
        if (emUso) {
            throw new SkuEmUsoException(dados.sku());
        }
    }
}
