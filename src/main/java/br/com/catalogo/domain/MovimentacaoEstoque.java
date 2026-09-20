package br.com.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Registro imutável de uma alteração no estoque de um produto.
 *
 * <p>Não existe setter nem operação de update/delete exposta pela API: corrigir
 * um lançamento significa registrar um novo ajuste (ver spec de
 * {@code estoque/movimentacoes}). O histórico é a fonte de verdade que permite
 * reconciliar o saldo desnormalizado do produto.
 */
@Entity
@Table(name = "movimentacao_estoque")
public class MovimentacaoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;

    /** STRING e não ORDINAL: reordenar o enum não pode reinterpretar o histórico. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10, updatable = false)
    private TipoMovimentacao tipo;

    /** Valor informado na requisição: unidades em ENTRADA/SAIDA, saldo-alvo em AJUSTE. */
    @Column(name = "quantidade", nullable = false, updatable = false)
    private int quantidade;

    /** O que de fato foi somado ao saldo; negativo em saídas e ajustes para baixo. */
    @Column(name = "diferenca", nullable = false, updatable = false)
    private int diferenca;

    /** Saldo do produto depois desta movimentação — permite auditar sem recalcular. */
    @Column(name = "saldo_resultante", nullable = false, updatable = false)
    private int saldoResultante;

    @Column(name = "motivo", nullable = false, length = 120, updatable = false)
    private String motivo;

    /** Instant, e não LocalDateTime: o momento é absoluto, independe do fuso do servidor. */
    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    /** Exigido pelo JPA; não usar no código de aplicação. */
    protected MovimentacaoEstoque() {
    }

    public MovimentacaoEstoque(Produto produto,
                               TipoMovimentacao tipo,
                               int quantidade,
                               int diferenca,
                               int saldoResultante,
                               String motivo,
                               Instant dataHora) {
        this.produto = produto;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.diferenca = diferenca;
        this.saldoResultante = saldoResultante;
        this.motivo = motivo;
        this.dataHora = dataHora;
    }

    public Long getId() {
        return id;
    }

    public Produto getProduto() {
        return produto;
    }

    public TipoMovimentacao getTipo() {
        return tipo;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public int getDiferenca() {
        return diferenca;
    }

    public int getSaldoResultante() {
        return saldoResultante;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getDataHora() {
        return dataHora;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof MovimentacaoEstoque movimentacao)) {
            return false;
        }
        return id != null && id.equals(movimentacao.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
