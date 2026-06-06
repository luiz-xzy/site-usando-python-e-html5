package br.com.inovatech.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Entidade principal de domínio que representa um produto no estoque.
 *
 * <p>Boas práticas aplicadas:
 * <ul>
 *   <li>currentQuantity usa {@link AtomicInteger} para thread-safety (RF03).</li>
 *   <li>Preços usam {@link BigDecimal} para evitar erros de ponto flutuante (RN01).</li>
 *   <li>editHistory é exposto apenas como lista imutável (RN02).</li>
 * </ul>
 */
public class Product {

    private Long id;
    private String sku;
    private String name;
    private BigDecimal costPrice;
    private BigDecimal sellingPrice;

    // AtomicInteger para thread-safety em operações de movimentação (RF03)
    private final AtomicInteger currentQuantity = new AtomicInteger(0);

    private Integer minThreshold;
    private boolean active;

    // Histórico encapsulado — nunca exposto como lista mutável (RN02)
    private final List<String> editHistory = new ArrayList<>();

    // Construtor padrão exigido pelo Jackson
    public Product() {}

    public Product(Long id, String sku, String name,
                   BigDecimal costPrice, BigDecimal sellingPrice,
                   int initialQuantity, int minThreshold) {
        this.id = Objects.requireNonNull(id);
        setSku(sku);
        setName(name);
        setCostPrice(costPrice);
        setSellingPrice(sellingPrice);
        this.currentQuantity.set(initialQuantity);
        setMinThreshold(minThreshold);
        this.active = true;
        addHistoryEntry("Produto criado.");
    }

    // ---- Getters ----

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getCostPrice() { return costPrice; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public Integer getMinThreshold() { return minThreshold; }
    public boolean isActive() { return active; }

    @JsonProperty("currentQuantity")
    public int getCurrentQuantity() {
        return currentQuantity.get();
    }

    /**
     * Retorna o histórico como lista IMUTÁVEL (RN02).
     */
    @JsonIgnore
    public List<String> getEditHistory() {
        return Collections.unmodifiableList(editHistory);
    }

    // Para Jackson deserializar o histórico
    @JsonProperty("editHistory")
    public List<String> getEditHistoryRaw() {
        return editHistory;
    }

    // ---- Setters com validação ----

    public void setId(Long id) { this.id = id; }

    public void setSku(String sku) {
        if (sku == null || sku.isBlank()) throw new IllegalArgumentException("SKU inválido.");
        this.sku = sku.trim().toUpperCase();
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Nome inválido.");
        this.name = name.trim();
    }

    public void setCostPrice(BigDecimal costPrice) {
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preço de custo inválido.");
        }
        this.costPrice = costPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        if (sellingPrice == null || sellingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Preço de venda deve ser positivo.");
        }
        this.sellingPrice = sellingPrice;
    }

    public void setCurrentQuantity(int qty) {
        this.currentQuantity.set(qty);
    }

    public void setMinThreshold(Integer minThreshold) {
        if (minThreshold == null || minThreshold < 0) {
            throw new IllegalArgumentException("Limiar mínimo inválido.");
        }
        this.minThreshold = minThreshold;
    }

    public void setActive(boolean active) { this.active = active; }

    // Para Jackson deserializar o histórico existente
    public void setEditHistory(List<String> history) {
        this.editHistory.clear();
        if (history != null) this.editHistory.addAll(history);
    }

    // ---- Operações thread-safe de quantidade ----

    /**
     * Incrementa a quantidade atomicamente. Thread-safe.
     */
    public int addQuantity(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Quantidade de entrada deve ser positiva.");
        return currentQuantity.addAndGet(amount);
    }

    /**
     * Decrementa a quantidade atomicamente. Thread-safe.
     * Retorna false se o estoque for insuficiente.
     */
    public boolean subtractQuantity(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Quantidade de saída deve ser positiva.");
        int current = currentQuantity.get();
        if (current < amount) return false;
        return currentQuantity.compareAndSet(current, current - amount);
    }

    // ---- Lógica de alerta ----

    /**
     * Verifica se o produto está abaixo do limiar mínimo (RF04).
     */
    public boolean isLowStock() {
        return currentQuantity.get() < minThreshold;
    }

    /**
     * Calcula a margem de lucro bruta.
     */
    public BigDecimal getProfitMargin() {
        if (costPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return sellingPrice.subtract(costPrice)
                .divide(costPrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    // ---- Histórico ----

    public void addHistoryEntry(String entry) {
        editHistory.add("[%s] %s".formatted(
                java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                entry));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product product)) return false;
        return Objects.equals(id, product.id) || Objects.equals(sku, product.sku);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sku);
    }

    @Override
    public String toString() {
        return "Product{id=%d, sku='%s', name='%s', qty=%d, minThreshold=%d, active=%s}"
                .formatted(id, sku, name, currentQuantity.get(), minThreshold, active);
    }
}
