package br.com.catalogo.domain;

import br.com.catalogo.exception.RegraDeNegocioException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Item comercializável do catálogo.
 *
 * <p>O saldo de estoque é mantido aqui de forma desnormalizada para que ler um
 * produto não exija somar seu histórico (ver README, decisão 3). Ele só pode ser
 * alterado pelos métodos {@code creditar}, {@code debitar} e {@code ajustarPara}
 * — não há setter — e cada um deles protege a invariante "saldo nunca negativo".
 * Quem chama é sempre o serviço de movimentação, dentro de uma transação.
 */
@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", nullable = false, length = 40, updatable = false)
    private String sku;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    /** BigDecimal e nunca double: dinheiro em ponto flutuante binário acumula erro. */
    @Column(name = "preco", nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "saldo_estoque", nullable = false)
    private int saldoEstoque;

    /**
     * LAZY de propósito: a listagem paginada de produtos não pode disparar um
     * SELECT de categoria por linha. Quem precisa do nome da categoria carrega
     * junto via {@code JOIN FETCH} ou dentro da transação do service.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    /** Exigido pelo JPA; não usar no código de aplicação. */
    protected Produto() {
    }

    /** Produto nasce ativo e com saldo zero: estoque só entra por movimentação. */
    public Produto(String sku, String nome, String descricao, BigDecimal preco, Categoria categoria) {
        this.sku = sku;
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.categoria = categoria;
        this.ativo = true;
        this.saldoEstoque = 0;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public int getSaldoEstoque() {
        return saldoEstoque;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    /** O SKU não entra aqui: é a identidade externa do produto e é imutável. */
    public void atualizarDados(String nome, String descricao, BigDecimal preco, Categoria categoria) {
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.categoria = categoria;
    }

    public void ativar() {
        this.ativo = true;
    }

    public void inativar() {
        this.ativo = false;
    }

    /**
     * Soma unidades ao saldo.
     *
     * @return a diferença aplicada, sempre positiva
     */
    public int creditar(int quantidade) {
        exigirQuantidadePositiva(quantidade);
        this.saldoEstoque += quantidade;
        return quantidade;
    }

    /**
     * Subtrai unidades do saldo.
     *
     * @return a diferença aplicada, sempre negativa
     * @throws RegraDeNegocioException se o produto estiver inativo ou o saldo for insuficiente
     */
    public int debitar(int quantidade) {
        exigirQuantidadePositiva(quantidade);
        if (!ativo) {
            throw new RegraDeNegocioException(
                    "Produto '%s' está inativo e não aceita saída de estoque.".formatted(sku));
        }
        if (quantidade > saldoEstoque) {
            throw new RegraDeNegocioException(
                    "Saldo insuficiente para o produto '%s': disponível %d, solicitado %d."
                            .formatted(sku, saldoEstoque, quantidade));
        }
        this.saldoEstoque -= quantidade;
        return -quantidade;
    }

    /**
     * Define o saldo em um valor absoluto.
     *
     * @return a diferença aplicada, positiva ou negativa conforme o ajuste
     */
    public int ajustarPara(int novoSaldo) {
        if (novoSaldo < 0) {
            throw new RegraDeNegocioException("O saldo ajustado não pode ser negativo.");
        }
        int diferenca = novoSaldo - saldoEstoque;
        this.saldoEstoque = novoSaldo;
        return diferenca;
    }

    private void exigirQuantidadePositiva(int quantidade) {
        if (quantidade <= 0) {
            throw new RegraDeNegocioException("A quantidade movimentada deve ser maior que zero.");
        }
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Produto produto)) {
            return false;
        }
        return id != null && id.equals(produto.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
