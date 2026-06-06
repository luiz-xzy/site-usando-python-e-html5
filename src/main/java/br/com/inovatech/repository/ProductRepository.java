package br.com.inovatech.repository;

import br.com.inovatech.model.Product;

import java.util.List;
import java.util.Optional;

/**
 * Contrato específico para persistência de produtos.
 * Estende o repositório genérico com buscas especializadas.
 */
public interface ProductRepository extends Repository<Product, Long> {

    Optional<Product> findBySku(String sku);

    List<Product> findByNameContaining(String term);

    List<Product> findAllActive();

    /**
     * Retorna produtos cujo estoque está abaixo do limiar mínimo (RF04).
     */
    List<Product> findLowStock();

    /**
     * Gera e retorna o próximo ID sequencial disponível.
     */
    Long nextId();
}
