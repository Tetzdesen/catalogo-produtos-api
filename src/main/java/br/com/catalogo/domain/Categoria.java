package br.com.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Agrupamento usado para organizar o catálogo.
 *
 * <p>A entidade não expõe a coleção de produtos: um {@code @OneToMany} aqui
 * convidaria a carregar todos os produtos ao ler uma categoria. Quando é preciso
 * saber se a categoria está em uso, o service pergunta ao repositório de produtos.
 */
@Entity
@Table(name = "categoria")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 60)
    private String nome;

    @Column(name = "descricao", length = 255)
    private String descricao;

    /** Exigido pelo JPA; não usar no código de aplicação. */
    protected Categoria() {
    }

    public Categoria(String nome, String descricao) {
        this.nome = nome;
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void atualizar(String nome, String descricao) {
        this.nome = nome;
        this.descricao = descricao;
    }

    /**
     * Identidade baseada apenas no id, e nunca em todos os campos: duas instâncias
     * carregadas em momentos diferentes representam a mesma linha.
     */
    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Categoria categoria)) {
            return false;
        }
        return id != null && id.equals(categoria.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
