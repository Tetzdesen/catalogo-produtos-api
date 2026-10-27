package br.com.catalogo.mapper;

import br.com.catalogo.dto.ProdutoRequest;
import br.com.catalogo.dto.ProdutoResponse;
import br.com.catalogo.model.Produto;
import org.springframework.stereotype.Component;

/**
 * Converte entre o que trafega em JSON (DTOs) e a entidade.
 *
 * <p><b>Por que uma classe só para isso?</b> Antes, a conversão estava espalhada:
 * um pedaço no service, outro em um método estático do próprio DTO. Com
 * ela aqui, cada classe tem um único motivo para mudar (o "S" de SOLID): o DTO
 * muda quando o contrato JSON muda, o service quando a regra muda, e o mapper
 * quando a correspondência entre os dois muda.
 *
 * <p>{@code @Component} registra o mapper como bean, e o service o recebe pelo
 * construtor como qualquer outra dependência. A conversão é escrita à mão de
 * propósito: são seis campos, e bibliotecas como o MapStruct esconderiam
 * justamente o que este projeto quer mostrar.
 */
@Component
public class ProdutoMapper {

    /** Os dados de cadastro viram um produto novo (que nasce ativo). */
    public Produto paraEntidade(ProdutoRequest dados) {
        return Produto.novo(dados.sku(), dados.nome(), dados.descricao(), dados.preco());
    }

    /**
     * A entidade vira o corpo da resposta. Só os campos escolhidos para o
     * contrato saem: uma coluna nova na tabela não aparece no JSON por acidente.
     */
    public ProdutoResponse paraResposta(Produto produto) {
        return new ProdutoResponse(produto.getId(), produto.getSku(), produto.getNome(),
                produto.getDescricao(), produto.getPreco(), produto.isAtivo());
    }
}
