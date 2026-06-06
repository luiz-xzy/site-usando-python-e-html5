package br.com.inovatech.model;

/**
 * Tipos de movimentação de estoque.
 */
public enum MovementType {
    ENTRADA("Entrada de estoque"),
    SAIDA("Saída de estoque"),
    AJUSTE("Ajuste de inventário");

    private final String descricao;

    MovementType(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
