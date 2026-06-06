package br.com.inovatech.service;

import br.com.inovatech.exception.EstoqueInsuficienteException;
import br.com.inovatech.exception.ProdutoDuplicadoException;
import br.com.inovatech.model.*;
import br.com.inovatech.repository.JsonMovementRepository;
import br.com.inovatech.repository.JsonProductRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes unitários da lógica de movimentação de estoque (JUnit 5).
 * Valida as regras de negócio críticas do InventoryService.
 */
@DisplayName("InventoryService — Testes de Lógica de Negócio")
class InventoryServiceTest {

    @TempDir
    Path tempDir;

    private InventoryService service;
    private User gerente;
    private User supervisor;

    @BeforeEach
    void setUp() {
        String prodFile = tempDir.resolve("produtos.json").toString();
        String movFile  = tempDir.resolve("movimentacoes.json").toString();

        JsonProductRepository  productRepo  = new JsonProductRepository(prodFile);
        JsonMovementRepository movementRepo = new JsonMovementRepository(movFile);

        service    = new InventoryService(productRepo, movementRepo);
        gerente    = new User("admin", AuthService.hashPassword("admin123"), Role.GERENTE);
        supervisor = new User("supervisor", AuthService.hashPassword("super"), Role.SUPERVISOR);
    }

    // ---- Cadastro ----

    @Test
    @DisplayName("RF01: Deve cadastrar produto com dados válidos")
    void deveCadastrarProduto() {
        Product p = service.cadastrarProduto(
                "TEST-001", "Produto Teste",
                new BigDecimal("10.00"), new BigDecimal("15.00"),
                100, 10, gerente);

        assertNotNull(p.getId());
        assertEquals("TEST-001", p.getSku());
        assertEquals(100, p.getCurrentQuantity());
        assertFalse(p.getEditHistory().isEmpty());
    }

    @Test
    @DisplayName("RF01: Deve lançar ProdutoDuplicadoException para SKU repetido")
    void deveLancarExcecaoParaSkuDuplicado() {
        service.cadastrarProduto("DUP-001", "Produto A",
                BigDecimal.TEN, new BigDecimal("15"), 10, 2, gerente);

        assertThrows(ProdutoDuplicadoException.class, () ->
                service.cadastrarProduto("DUP-001", "Produto B",
                        BigDecimal.TEN, new BigDecimal("15"), 5, 1, gerente));
    }

    @Test
    @DisplayName("RN01: Preço de venda não pode ser menor que custo")
    void deveRejeitarVendaMenorQueCusto() {
        assertThrows(IllegalArgumentException.class, () ->
                service.cadastrarProduto("ERR-001", "Produto Inválido",
                        new BigDecimal("20.00"), new BigDecimal("10.00"),
                        5, 1, gerente));
    }

    // ---- Entrada ----

    @Test
    @DisplayName("RF03: Deve registrar entrada e incrementar quantidade")
    void deveRegistrarEntrada() {
        Product p = criarProdutoPadrao(50, 5);
        service.registrarEntrada(p.getId(), 30, "Reposição", gerente);

        Product atualizado = service.buscarPorId(p.getId()).orElseThrow();
        assertEquals(80, atualizado.getCurrentQuantity());
    }

    @Test
    @DisplayName("RF03: Deve registrar movimentação de entrada no histórico")
    void deveRegistrarMovimentacaoDeEntrada() throws Exception {
        Product p = criarProdutoPadrao(10, 2);
        Movement mov = service.registrarEntrada(p.getId(), 5, "Compra", gerente);

        assertEquals(MovementType.ENTRADA, mov.getType());
        assertEquals(5, mov.getQuantity());
        assertEquals(p.getSku(), mov.getProductSku());
    }

    // ---- Saída ----

    @Test
    @DisplayName("RN01: Deve lançar EstoqueInsuficienteException quando saldo é insuficiente")
    void deveLancarEstoqueInsuficienteException() {
        Product p = criarProdutoPadrao(5, 1);

        EstoqueInsuficienteException ex = assertThrows(EstoqueInsuficienteException.class, () ->
                service.registrarSaida(p.getId(), 10, "Venda", supervisor));

        assertEquals(10, ex.getRequested());
        assertEquals(5, ex.getAvailable());
    }

    @Test
    @DisplayName("RF03: Deve registrar saída e decrementar quantidade corretamente")
    void deveRegistrarSaida() throws EstoqueInsuficienteException {
        Product p = criarProdutoPadrao(20, 5);
        service.registrarSaida(p.getId(), 7, "Venda ao cliente", supervisor);

        assertEquals(13, service.buscarPorId(p.getId()).orElseThrow().getCurrentQuantity());
    }

    // ---- Alerta de Estoque Baixo ----

    @Test
    @DisplayName("RF04: Deve disparar alerta quando quantidade cai abaixo do mínimo")
    void deveDispararAlertaEstoqueBaixo() throws EstoqueInsuficienteException {
        boolean[] alertDispatched = {false};
        service.addAlertObserver(product -> alertDispatched[0] = true);

        Product p = criarProdutoPadrao(5, 5);  // Já está no limiar
        service.registrarSaida(p.getId(), 1, "Saída de teste", supervisor);

        assertTrue(alertDispatched[0], "Observer deve ser notificado quando estoque fica abaixo do mínimo");
    }

    @Test
    @DisplayName("RF04: listarEstoqueBaixo deve retornar apenas produtos abaixo do limiar")
    void deveListarApenasEstoqueBaixo() {
        criarProdutoPadrao(100, 5);   // OK
        Product baixo = criarProdutoComSku("BAIXO-01", 3, 10); // Baixo

        List<Product> alertas = service.listarEstoqueBaixo();

        assertEquals(1, alertas.size());
        assertEquals("BAIXO-01", alertas.get(0).getSku());
    }

    // ---- Ajuste ----

    @Test
    @DisplayName("RF03: Deve ajustar quantidade para o valor correto")
    void deveAjustarQuantidade() {
        Product p = criarProdutoPadrao(20, 5);
        service.registrarAjuste(p.getId(), 35, "Contagem física", gerente);

        assertEquals(35, service.buscarPorId(p.getId()).orElseThrow().getCurrentQuantity());
    }

    // ---- Thread-safety ----

    @Test
    @DisplayName("RF03: Deve processar saídas concorrentes sem ultrapassar o saldo disponível")
    void deveSerThreadSafeEmSaidasConcorrentes() throws InterruptedException {
        Product p = criarProdutoPadrao(100, 0);
        int threads = 20;
        int qtdPorThread = 6; // 20 × 6 = 120 > 100 → algumas devem falhar

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        int[] sucessos = {0};
        int[] falhas   = {0};

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    service.registrarSaida(p.getId(), qtdPorThread, "Concorrência", supervisor);
                    synchronized (sucessos) { sucessos[0]++; }
                } catch (EstoqueInsuficienteException e) {
                    synchronized (falhas) { falhas[0]++; }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        int saldoFinal = service.buscarPorId(p.getId()).orElseThrow().getCurrentQuantity();
        assertTrue(saldoFinal >= 0, "Saldo não pode ser negativo: " + saldoFinal);
        assertEquals(100, sucessos[0] * qtdPorThread + saldoFinal,
                "Saldo final deve ser consistente com as saídas realizadas.");
    }

    // ---- Exportação ----

    @Test
    @DisplayName("RF05: CSV deve conter cabeçalho e dados dos produtos")
    void deveGerarCsvComDados() {
        criarProdutoPadrao(10, 2);
        String csv = service.exportarProdutosCsv();

        assertTrue(csv.contains("SKU"), "CSV deve conter cabeçalho");
        assertTrue(csv.contains("TEST-DEFAULT"), "CSV deve conter produto cadastrado");
    }

    // ---- RN02 — Imutabilidade do histórico ----

    @Test
    @DisplayName("RN02: editHistory deve ser imutável externamente")
    void historicoDeveSerImutavel() {
        Product p = criarProdutoPadrao(10, 2);

        assertThrows(UnsupportedOperationException.class,
                () -> p.getEditHistory().add("tentativa ilegal"));
    }

    // ---- Helpers ----

    private Product criarProdutoPadrao(int qty, int threshold) {
        return criarProdutoComSku("TEST-DEFAULT", qty, threshold);
    }

    private Product criarProdutoComSku(String sku, int qty, int threshold) {
        return service.cadastrarProduto(
                sku, "Produto " + sku,
                new BigDecimal("5.00"), new BigDecimal("9.99"),
                qty, threshold, gerente);
    }
}
