package br.com.inovatech.exception;

/**
 * Lançada quando tentamos cadastrar um produto com SKU já existente.
 */
public class ProdutoDuplicadoException extends RuntimeException {
    public ProdutoDuplicadoException(String sku) {
        super("Já existe um produto cadastrado com o SKU: " + sku);
    }
}
