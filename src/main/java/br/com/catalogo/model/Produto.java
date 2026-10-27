package br.com.catalogo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Um produto do catálogo, mapeado para a tabela {@code produto}.
 *
 * <p>{@code @Entity} diz ao JPA que cada instância desta classe corresponde a uma
 * linha da tabela. O schema em si é criado pelo Flyway
 * ({@code V1__criar_tabelas.sql}); com {@code ddl-auto: validate}, o Hibernate
 * apenas confere no start que os campos daqui batem com as colunas de lá.
 *
 * <p>Não há setters públicos: os dados nascem por {@link #novo} e mudam só por
 * {@link #atualizar}, então não existe caminho para alterar um campo solto e
 * esquecer os outros. A entidade guarda o próprio comportamento em vez de ser um
 * saco de getters e setters manipulado de fora.
 */
@Entity
@Table(name = "produto")
public class Produto {

    // IDENTITY: o id é gerado pela coluna do banco (GENERATED ... AS IDENTITY),
    // não pela aplicação.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 500)
    private String descricao;

    // BigDecimal, nunca double: dinheiro precisa de representação decimal exata.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private boolean ativo;

    /**
     * Exigido pelo JPA, que instancia a entidade por reflexão antes de preencher
     * os campos. {@code protected} para que o código da aplicação não o use.
     */
    protected Produto() {
    }

    private Produto(String sku, String nome, String descricao, BigDecimal preco) {
        this.sku = sku;
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.ativo = true;
    }

    /**
     * Cria um produto novo, que sempre nasce ativo.
     *
     * <p>Padrão <b>Static Factory Method</b>: diferente de um construtor, o método
     * tem nome, e o nome conta a regra. {@code Produto.novo(...)} diz "produto
     * recém-cadastrado"; {@code new Produto(...)} não diria nada sobre o
     * {@code ativo = true} escondido lá dentro.
     */
    public static Produto novo(String sku, String nome, String descricao, BigDecimal preco) {
        return new Produto(sku, nome, descricao, preco);
    }

    /** Substitui todos os dados editáveis de uma vez, como pede o {@code PUT}. */
    public void atualizar(String sku, String nome, String descricao, BigDecimal preco, boolean ativo) {
        this.sku = sku;
        this.nome = nome;
        this.descricao = descricao;
        this.preco = preco;
        this.ativo = ativo;
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
}
