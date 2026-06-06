package br.com.inovatech.service;

import br.com.inovatech.exception.EstoqueInsuficienteException;
import br.com.inovatech.exception.ProdutoDuplicadoException;
import br.com.inovatech.model.Movement;
import br.com.inovatech.model.MovementType;
import br.com.inovatech.model.Product;
import br.com.inovatech.model.User;
import br.com.inovatech.repository.JsonMovementRepository;
import br.com.inovatech.repository.JsonProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Camada de serviço principal — encapsula toda a lógica de negócio de inventário.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Cadastro e edição de produtos (RF01)</li>
 *   <li>Registro de entradas, saídas e ajustes (RF03)</li>
 *   <li>Disparo de alertas de estoque baixo via padrão Observer (RF04)</li>
 *   <li>Consultas e relatórios</li>
 * </ul>
 */
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final JsonProductRepository productRepo;
    private final JsonMovementRepository movementRepo;

    // Padrão Observer para alertas de estoque baixo (RF04)
    private final List<StockAlertObserver> alertObservers = new ArrayList<>();

    public InventoryService(JsonProductRepository productRepo,
                            JsonMovementRepository movementRepo) {
        this.productRepo  = Objects.requireNonNull(productRepo);
        this.movementRepo = Objects.requireNonNull(movementRepo);
    }

    // ---- Gestão de observadores ----

    public void addAlertObserver(StockAlertObserver observer) {
        alertObservers.add(observer);
    }

    private void notifyLowStock(Product product) {
        if (product.isLowStock()) {
            log.warn("⚠  ESTOQUE BAIXO: {} (SKU: {}) — atual: {}, mínimo: {}",
                    product.getName(), product.getSku(),
                    product.getCurrentQuantity(), product.getMinThreshold());
            alertObservers.forEach(obs -> obs.onLowStockAlert(product));
        }
    }

    // ---- RF01 — Cadastro de Produtos ----

    /**
     * Cadastra um novo produto, verificando duplicidade de SKU.
     */
    public Product cadastrarProduto(String sku, String name,
                                     BigDecimal costPrice, BigDecimal sellingPrice,
                                     int initialQty, int minThreshold,
                                     User responsible) {
        // Validação de preço via BigDecimal (RN01)
        if (sellingPrice.compareTo(costPrice) < 0) {
            throw new IllegalArgumentException(
                    "Preço de venda não pode ser menor que o preço de custo.");
        }

        // Verifica duplicidade de SKU
        if (productRepo.findBySku(sku).isPresent()) {
            throw new ProdutoDuplicadoException(sku);
        }

        Product product = new Product(
                productRepo.nextId(), sku, name,
                costPrice, sellingPrice,
                initialQty, minThreshold);

        productRepo.save(product);
        log.info("Produto cadastrado: {} (SKU: {}) por {}", name, sku, responsible.getUsername());

        // Verifica alerta logo após o cadastro, caso já entre com estoque baixo
        notifyLowStock(product);

        return product;
    }

    /**
     * Atualiza os dados cadastrais de um produto existente.
     */
    public Product atualizarProduto(Long id, String newName,
                                     BigDecimal newCostPrice, BigDecimal newSellingPrice,
                                     int newMinThreshold, User responsible) {
        Product product = getOrThrow(id);

        String log_entry = "Dados atualizados por %s: nome='%s'→'%s', custo=%s→%s, venda=%s→%s, limiar=%d→%d"
                .formatted(responsible.getUsername(),
                        product.getName(), newName,
                        product.getCostPrice(), newCostPrice,
                        product.getSellingPrice(), newSellingPrice,
                        product.getMinThreshold(), newMinThreshold);

        product.setName(newName);
        product.setCostPrice(newCostPrice);
        product.setSellingPrice(newSellingPrice);
        product.setMinThreshold(newMinThreshold);
        product.addHistoryEntry(log_entry);

        productRepo.save(product);
        log.info("Produto atualizado: ID={}", id);
        return product;
    }

    // ---- RF03 — Movimentações (thread-safe via AtomicInteger no model) ----

    /**
     * Registra uma entrada de estoque.
     */
    public synchronized Movement registrarEntrada(Long productId, int qty,
                                                    String reason, User responsible) {
        Product product = getOrThrow(productId);
        product.addQuantity(qty);
        product.addHistoryEntry("Entrada de %d un. por %s. Motivo: %s"
                .formatted(qty, responsible.getUsername(), reason));

        Movement movement = Movement.of(MovementType.ENTRADA, product.getSku(), qty, responsible, reason);
        movementRepo.save(movement);

        log.info("ENTRADA: {} un. → {} (SKU: {})", qty, product.getName(), product.getSku());
        return movement;
    }

    /**
     * Registra uma saída de estoque (RN01).
     *
     * @throws EstoqueInsuficienteException se o saldo for insuficiente
     */
    public synchronized Movement registrarSaida(Long productId, int qty,
                                                  String reason, User responsible)
            throws EstoqueInsuficienteException {
        Product product = getOrThrow(productId);

        // Verifica saldo antes de subtrair (RN01)
        if (qty > product.getCurrentQuantity()) {
            throw new EstoqueInsuficienteException(product.getSku(), qty, product.getCurrentQuantity());
        }

        boolean ok = product.subtractQuantity(qty);
        if (!ok) {
            // Condição de corrida improvável, mas tratada
            throw new EstoqueInsuficienteException(product.getSku(), qty, product.getCurrentQuantity());
        }

        product.addHistoryEntry("Saída de %d un. por %s. Motivo: %s"
                .formatted(qty, responsible.getUsername(), reason));

        Movement movement = Movement.of(MovementType.SAIDA, product.getSku(), qty, responsible, reason);
        movementRepo.save(movement);

        log.info("SAÍDA: {} un. ← {} (SKU: {})", qty, product.getName(), product.getSku());

        // Verifica alerta após a saída (RF04)
        notifyLowStock(product);

        return movement;
    }

    /**
     * Registra um ajuste de inventário (correção de saldo).
     */
    public synchronized Movement registrarAjuste(Long productId, int newQty,
                                                   String reason, User responsible) {
        Product product = getOrThrow(productId);
        int oldQty = product.getCurrentQuantity();
        product.setCurrentQuantity(newQty);
        product.addHistoryEntry("Ajuste de %d → %d un. por %s. Motivo: %s"
                .formatted(oldQty, newQty, responsible.getUsername(), reason));

        Movement movement = Movement.of(MovementType.AJUSTE, product.getSku(),
                newQty - oldQty, responsible, reason);
        movementRepo.save(movement);

        log.info("AJUSTE: {} → {} un. | {} (SKU: {})", oldQty, newQty, product.getName(), product.getSku());
        notifyLowStock(product);
        return movement;
    }

    // ---- RF04 — Alertas de Estoque ----

    public List<Product> listarEstoqueBaixo() {
        return productRepo.findLowStock();
    }

    // ---- RF05 — Exportação CSV ----

    /**
     * Exporta todos os produtos ativos para formato CSV.
     */
    public String exportarProdutosCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("ID,SKU,Nome,Preço Custo,Preço Venda,Qtd Atual,Limiar Mínimo,Alerta\n");
        productRepo.findAllActive().forEach(p -> sb.append(
                "%d,%s,%s,%s,%s,%d,%d,%s\n".formatted(
                        p.getId(), p.getSku(), p.getName(),
                        p.getCostPrice(), p.getSellingPrice(),
                        p.getCurrentQuantity(), p.getMinThreshold(),
                        p.isLowStock() ? "BAIXO" : "OK")));
        return sb.toString();
    }

    // ---- Consultas ----

    public Optional<Product> buscarPorId(Long id) { return productRepo.findById(id); }
    public Optional<Product> buscarPorSku(String sku) { return productRepo.findBySku(sku); }
    public List<Product> listarTodos() { return productRepo.findAll(); }
    public List<Product> listarAtivos() { return productRepo.findAllActive(); }
    public List<Product> buscarPorNome(String term) { return productRepo.findByNameContaining(term); }
    public List<Movement> listarMovimentos() { return movementRepo.findAll(); }
    public List<Movement> listarMovimentosPorSku(String sku) { return movementRepo.findBySku(sku); }

    // ---- Auto-save ----

    /**
     * Persiste todos os dados no disco. Deve ser chamado no bloco {@code finally} do main loop.
     */
    public void autoSave() {
        productRepo.flush();
        movementRepo.flush();
        log.info("Auto-save concluído.");
    }

    // ---- Utilitário privado ----

    private Product getOrThrow(Long id) {
        return productRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado: ID=" + id));
    }
}
