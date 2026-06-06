package br.com.inovatech.exception;

/**
 * Lançada quando uma operação de saída excede o saldo disponível (RN01).
 */
public class EstoqueInsuficienteException extends Exception {

    private final String productSku;
    private final int requested;
    private final int available;

    public EstoqueInsuficienteException(String productSku, int requested, int available) {
        super("Estoque insuficiente para '%s': solicitado=%d, disponível=%d"
                .formatted(productSku, requested, available));
        this.productSku = productSku;
        this.requested  = requested;
        this.available  = available;
    }

    public String getProductSku() { return productSku; }
    public int    getRequested()  { return requested; }
    public int    getAvailable()  { return available; }
}
