package br.com.inovatech.repository;

import br.com.inovatech.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Implementação do repositório de produtos com persistência em arquivo JSON.
 *
 * <p>Usa {@link ConcurrentHashMap} para armazenamento em memória thread-safe
 * e Jackson para serialização/desserialização (RF05 / Stack Tecnológica).
 */
public class JsonProductRepository implements ProductRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonProductRepository.class);

    private final File dataFile;
    private final ObjectMapper mapper;
    private final Map<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    // Wrapper para serialização raiz { "products": [...] }
    private static class ProductsWrapper {
        public List<Product> products = new ArrayList<>();
    }

    public JsonProductRepository(String filePath) {
        this.dataFile = new File(filePath);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        load();
    }

    // ---- Carregamento inicial ----

    private void load() {
        if (!dataFile.exists()) {
            log.info("Arquivo de dados não encontrado. Iniciando com repositório vazio: {}", dataFile.getAbsolutePath());
            return;
        }
        try {
            ProductsWrapper wrapper = mapper.readValue(dataFile, ProductsWrapper.class);
            long maxId = 0L;
            for (Product p : wrapper.products) {
                store.put(p.getId(), p);
                if (p.getId() > maxId) maxId = p.getId();
            }
            idSequence.set(maxId + 1);
            log.info("Repositório carregado: {} produto(s) do arquivo '{}'.", store.size(), dataFile.getName());
        } catch (IOException e) {
            log.error("Falha ao carregar produtos do JSON: {}", e.getMessage(), e);
        }
    }

    // ---- CRUD ----

    @Override
    public void save(Product product) {
        Objects.requireNonNull(product, "Produto não pode ser nulo.");
        if (product.getId() == null) {
            product.setId(nextId());
        }
        store.put(product.getId(), product);
        log.debug("Produto salvo em memória: {}", product.getSku());
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        if (sku == null) return Optional.empty();
        String normalized = sku.trim().toUpperCase();
        return store.values().stream()
                .filter(p -> p.getSku().equals(normalized))
                .findFirst();
    }

    @Override
    public List<Product> findByNameContaining(String term) {
        if (term == null || term.isBlank()) return findAll();
        String lowerTerm = term.toLowerCase();
        return store.values().stream()
                .filter(p -> p.getName().toLowerCase().contains(lowerTerm))
                .sorted(Comparator.comparing(Product::getName))
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(Product::getId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findAllActive() {
        return store.values().stream()
                .filter(Product::isActive)
                .sorted(Comparator.comparing(Product::getName))
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findLowStock() {
        return store.values().stream()
                .filter(p -> p.isActive() && p.isLowStock())
                .sorted(Comparator.comparing(Product::getName))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        // Exclusão lógica (soft delete) — preserva histórico
        findById(id).ifPresent(p -> {
            p.setActive(false);
            p.addHistoryEntry("Produto desativado (exclusão lógica).");
            log.info("Produto desativado: ID={}", id);
        });
    }

    @Override
    public Long nextId() {
        return idSequence.getAndIncrement();
    }

    // ---- Persistência ----

    @Override
    public void flush() {
        try {
            dataFile.getParentFile().mkdirs();
            ProductsWrapper wrapper = new ProductsWrapper();
            wrapper.products = findAll();
            mapper.writeValue(dataFile, wrapper);
            log.info("Dados persistidos com sucesso: {} produto(s) → '{}'.", wrapper.products.size(), dataFile.getName());
        } catch (IOException e) {
            log.error("ERRO CRÍTICO ao salvar produtos no JSON: {}", e.getMessage(), e);
        }
    }
}
