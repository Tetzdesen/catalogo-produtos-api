package br.com.catalogo.domain;

/**
 * Natureza de uma movimentação de estoque.
 *
 * <p>Gravado como texto na coluna {@code tipo} (e não como ordinal): o ordinal
 * quebraria silenciosamente se alguém reordenasse as constantes, e o valor em
 * texto é legível para quem consulta o banco direto.
 */
public enum TipoMovimentacao {

    /** Soma unidades ao saldo. */
    ENTRADA,

    /** Subtrai unidades do saldo; nunca pode deixá-lo negativo. */
    SAIDA,

    /** Define o saldo em um valor absoluto, para corrigir divergência de inventário. */
    AJUSTE
}
