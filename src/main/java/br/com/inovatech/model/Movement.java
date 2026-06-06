package br.com.inovatech.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro imutável de uma movimentação de estoque.
 * Cada instância representa um evento auditável no ciclo de vida do produto.
 */
public class Movement {

    private final LocalDateTime timestamp;
    private final MovementType type;
    private final String productSku;
    private final int quantity;
    private final User responsible;
    private final String reason;

    @JsonCreator
    public Movement(
            @JsonProperty("timestamp")  LocalDateTime timestamp,
            @JsonProperty("type")       MovementType type,
            @JsonProperty("productSku") String productSku,
            @JsonProperty("quantity")   int quantity,
            @JsonProperty("responsible") User responsible,
            @JsonProperty("reason")     String reason) {

        this.timestamp   = Objects.requireNonNullElseGet(timestamp, LocalDateTime::now);
        this.type        = Objects.requireNonNull(type, "Tipo de movimentação obrigatório.");
        this.productSku  = Objects.requireNonNull(productSku, "SKU do produto obrigatório.");
        this.quantity    = quantity;
        this.responsible = Objects.requireNonNull(responsible, "Responsável obrigatório.");
        this.reason      = (reason == null || reason.isBlank()) ? "Sem observação." : reason.trim();
    }

    // Factory method conveniente para criações no serviço
    public static Movement of(MovementType type, String productSku,
                               int quantity, User responsible, String reason) {
        return new Movement(LocalDateTime.now(), type, productSku, quantity, responsible, reason);
    }

    // ---- Getters (somente leitura — registro imutável) ----

    public LocalDateTime getTimestamp()  { return timestamp; }
    public MovementType  getType()       { return type; }
    public String        getProductSku() { return productSku; }
    public int           getQuantity()   { return quantity; }
    public User          getResponsible(){ return responsible; }
    public String        getReason()     { return reason; }

    @Override
    public String toString() {
        return "[%s] %s | SKU: %s | Qtd: %d | Por: %s | Motivo: %s".formatted(
                timestamp.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                type.getDescricao(), productSku, quantity,
                responsible.getUsername(), reason);
    }
}
