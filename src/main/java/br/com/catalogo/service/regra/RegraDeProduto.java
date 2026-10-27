package br.com.catalogo.service.regra;

import br.com.catalogo.dto.ProdutoRequest;

/**
 * Uma regra de negócio conferida antes de cadastrar ou alterar um produto.
 *
 * <p>Padrão <b>Strategy</b>: cada regra é uma classe que implementa esta
 * interface e é registrada como bean ({@code @Component}). O
 * {@code ProdutoServiceImpl} recebe {@code List<RegraDeProduto>} no construtor
 * (o Spring injeta <i>todos</i> os beans deste tipo) e executa uma por uma, sem
 * saber quais existem.
 *
 * <p>É o princípio aberto/fechado (o "O" de SOLID): uma regra nova (por exemplo,
 * "nome não pode conter palavrão") é uma classe nova, e o service não é editado.
 * Hoje existe uma única regra ({@link SkuUnicoRegra}); o ponto de extensão
 * custa uma interface de um método.
 *
 * <p>A regra recebe os <b>dados de entrada</b>, e não a entidade já alterada. Se
 * rodasse depois de {@code produto.atualizar(...)}, a consulta feita pela regra
 * obrigaria o Hibernate a gravar a alteração pendente antes de consultar (o
 * <i>auto-flush</i>), o SKU duplicado bateria na constraint do banco e o
 * cliente receberia uma mensagem genérica em vez da explicação da regra.
 */
public interface RegraDeProduto {

    /**
     * Lança uma {@code RegraDeNegocioException} se a regra for violada.
     *
     * @param dados       os dados enviados pelo cliente
     * @param idDoProduto o produto sendo alterado, ou {@code null} no cadastro
     */
    void validar(ProdutoRequest dados, Long idDoProduto);
}
