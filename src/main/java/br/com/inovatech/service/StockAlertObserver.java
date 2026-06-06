package br.com.inovatech.service;

import br.com.inovatech.model.Product;

/**
 * Interface Observer para o padrão de alerta de estoque baixo (RF04).
 * Qualquer componente interessado em alertas de estoque deve implementar esta interface.
 */
@FunctionalInterface
public interface StockAlertObserver {

    /**
     * Chamado quando um produto atinge ou cai abaixo do limiar mínimo.
     *
     * @param product o produto em situação de alerta
     */
    void onLowStockAlert(Product product);
}
