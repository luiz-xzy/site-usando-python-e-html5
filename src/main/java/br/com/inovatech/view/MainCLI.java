package br.com.inovatech.view;

import br.com.inovatech.exception.AutenticacaoException;
import br.com.inovatech.exception.EstoqueInsuficienteException;
import br.com.inovatech.exception.ProdutoDuplicadoException;
import br.com.inovatech.model.Movement;
import br.com.inovatech.model.Product;
import br.com.inovatech.model.Role;
import br.com.inovatech.model.User;
import br.com.inovatech.repository.JsonMovementRepository;
import br.com.inovatech.repository.JsonProductRepository;
import br.com.inovatech.repository.JsonUserRepository;
import br.com.inovatech.service.AuthService;
import br.com.inovatech.service.ExportService;
import br.com.inovatech.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Interface de linha de comando (CLI) do sistema Inova Tech.
 *
 * <p>Utiliza o padrão {@code while(true) / switch expressions} do Java 17 para
 * navegação no menu, com auto-save garantido no bloco {@code finally} (PRD §8).
 */
public class MainCLI {

    private static final Logger log = LoggerFactory.getLogger(MainCLI.class);

    // Configuração de caminhos de dados
    private static final String DATA_DIR    = "data/";
    private static final String EXPORT_DIR  = "exports/";
    private static final String PRODUCTS_FILE   = DATA_DIR + "produtos.json";
    private static final String MOVEMENTS_FILE  = DATA_DIR + "movimentacoes.json";
    private static final String USERS_FILE      = DATA_DIR + "usuarios.json";

    private final Scanner scanner = new Scanner(System.in);
    private final AuthService authService;
    private final InventoryService inventoryService;
    private final ExportService exportService;

    public MainCLI() {
        // Inicialização dos repositórios e serviços
        JsonUserRepository     userRepo     = new JsonUserRepository(USERS_FILE);
        JsonProductRepository  productRepo  = new JsonProductRepository(PRODUCTS_FILE);
        JsonMovementRepository movementRepo = new JsonMovementRepository(MOVEMENTS_FILE);

        this.authService      = new AuthService(userRepo);
        this.inventoryService = new InventoryService(productRepo, movementRepo);
        this.exportService    = new ExportService(EXPORT_DIR);

        // Registra o observer CLI para alertas de estoque (RF04)
        inventoryService.addAlertObserver(product ->
                printAlert("⚠  ALERTA: Estoque baixo para '%s' (SKU: %s) — %d/%d unidades!"
                        .formatted(product.getName(), product.getSku(),
                                product.getCurrentQuantity(), product.getMinThreshold())));
    }

    // ---- Entry point ----

    public static void main(String[] args) {
        new MainCLI().run();
    }

    // ---- Main Loop ----

    public void run() {
        printBanner();

        // Autenticação inicial
        if (!doLogin()) {
            System.out.println("Encerrando sistema.");
            return;
        }

        // Main loop com auto-save garantido no finally (PRD §8)
        try {
            mainMenuLoop();
        } finally {
            inventoryService.autoSave();
            authService.logout();
            System.out.println("\n✅  Dados salvos. Sistema encerrado. Até logo!");
        }
    }

    private boolean doLogin() {
        System.out.println("\n=== LOGIN ===");
        for (int tentativas = 0; tentativas < 3; tentativas++) {
            try {
                System.out.print("Usuário: ");
                String username = scanner.nextLine().trim();
                System.out.print("Senha  : ");
                String password = scanner.nextLine().trim();
                authService.login(username, password);
                System.out.printf("✅  Bem-vindo, %s! (%s)%n",
                        authService.getCurrentUser().getUsername(),
                        authService.getCurrentUser().getRole());
                return true;
            } catch (AutenticacaoException e) {
                System.out.println("❌  " + e.getMessage());
            }
        }
        System.out.println("Número máximo de tentativas atingido.");
        return false;
    }

    private void mainMenuLoop() {
        // Java 17 switch expressions (PRD §8)
        while (true) {
            printMainMenu();
            String opcao = scanner.nextLine().trim();

            boolean continuar = switch (opcao) {
                case "1" -> menuCadastro();
                case "2" -> menuMovimentacoes();
                case "3" -> menuConsultas();
                case "4" -> menuRelatorios();
                case "5" -> menuExportacao();
                case "0" -> false;  // Encerra o loop
                default  -> { printError("Opção inválida: " + opcao); yield true; }
            };

            if (!continuar) break;
        }
    }

    // ==============================
    //  MENU 1 — Cadastro de Produtos
    // ==============================

    private boolean menuCadastro() {
        while (true) {
            System.out.println("""
                    
                    ── CADASTRO DE PRODUTOS ──────────────────
                    [1] Cadastrar novo produto
                    [2] Editar produto existente
                    [3] Desativar produto
                    [0] Voltar
                    ──────────────────────────────────────────""");
            return switch (scanner.nextLine().trim()) {
                case "1" -> { cadastrarProduto(); yield true; }
                case "2" -> { editarProduto();    yield true; }
                case "3" -> { desativarProduto(); yield true; }
                case "0" -> true;
                default  -> { printError("Opção inválida."); yield true; }
            };
        }
    }

    private void cadastrarProduto() {
        System.out.println("\n── Novo Produto ──");
        try {
            System.out.print("SKU          : "); String sku  = scanner.nextLine().trim();
            System.out.print("Nome         : "); String name = scanner.nextLine().trim();
            System.out.print("Preço custo  : "); BigDecimal cost  = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Preço venda  : "); BigDecimal sell  = new BigDecimal(scanner.nextLine().trim());
            System.out.print("Qtd inicial  : "); int qty  = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("Limiar mínimo: "); int thr  = Integer.parseInt(scanner.nextLine().trim());

            Product p = inventoryService.cadastrarProduto(
                    sku, name, cost, sell, qty, thr, authService.getCurrentUser());
            System.out.printf("✅  Produto cadastrado com ID=%d%n", p.getId());

        } catch (ProdutoDuplicadoException e) {
            printError("SKU já existe: " + e.getMessage());
        } catch (Exception e) {
            printError("Erro: " + e.getMessage());
        }
    }

    private void editarProduto() {
        System.out.print("ID do produto: ");
        try {
            Long id = Long.parseLong(scanner.nextLine().trim());
            Optional<Product> opt = inventoryService.buscarPorId(id);
            if (opt.isEmpty()) { printError("Produto não encontrado."); return; }
            Product p = opt.get();
            System.out.printf("Editando: %s (SKU: %s)%n", p.getName(), p.getSku());
            System.out.printf("Nome atual [%s]: ", p.getName());
            String name = scanner.nextLine().trim();
            if (name.isEmpty()) name = p.getName();
            System.out.printf("Preço custo atual [%s]: ", p.getCostPrice());
            String costStr = scanner.nextLine().trim();
            BigDecimal cost = costStr.isEmpty() ? p.getCostPrice() : new BigDecimal(costStr);
            System.out.printf("Preço venda atual [%s]: ", p.getSellingPrice());
            String sellStr = scanner.nextLine().trim();
            BigDecimal sell = sellStr.isEmpty() ? p.getSellingPrice() : new BigDecimal(sellStr);
            System.out.printf("Limiar mínimo atual [%d]: ", p.getMinThreshold());
            String thrStr = scanner.nextLine().trim();
            int thr = thrStr.isEmpty() ? p.getMinThreshold() : Integer.parseInt(thrStr);

            inventoryService.atualizarProduto(id, name, cost, sell, thr, authService.getCurrentUser());
            System.out.println("✅  Produto atualizado.");
        } catch (Exception e) {
            printError("Erro: " + e.getMessage());
        }
    }

    private void desativarProduto() {
        System.out.print("ID do produto a desativar: ");
        try {
            Long id = Long.parseLong(scanner.nextLine().trim());
            inventoryService.buscarPorId(id).ifPresentOrElse(
                    p -> {
                        // ProductRepo não está exposto diretamente — usar service
                        System.out.printf("Confirma desativar '%s'? (s/N): ", p.getName());
                        if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                            p.setActive(false);
                            p.addHistoryEntry("Desativado por " + authService.getCurrentUser().getUsername());
                            System.out.println("✅  Produto desativado.");
                        }
                    },
                    () -> printError("Produto não encontrado."));
        } catch (NumberFormatException e) {
            printError("ID inválido.");
        }
    }

    // =================================
    //  MENU 2 — Movimentações de Estoque
    // =================================

    private boolean menuMovimentacoes() {
        System.out.println("""
                
                ── MOVIMENTAÇÕES ─────────────────────────
                [1] Registrar Entrada
                [2] Registrar Saída
                [3] Registrar Ajuste
                [0] Voltar
                ──────────────────────────────────────────""");
        return switch (scanner.nextLine().trim()) {
            case "1" -> { registrarEntrada(); yield true; }
            case "2" -> { registrarSaida();   yield true; }
            case "3" -> { registrarAjuste();  yield true; }
            case "0" -> true;
            default  -> { printError("Opção inválida."); yield true; }
        };
    }

    private void registrarEntrada() {
        try {
            Product p = selecionarProduto(); if (p == null) return;
            System.out.print("Quantidade a entrada: "); int qty = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("Motivo              : "); String reason = scanner.nextLine().trim();
            inventoryService.registrarEntrada(p.getId(), qty, reason, authService.getCurrentUser());
            System.out.printf("✅  Entrada registrada. Saldo atual: %d%n", inventoryService.buscarPorId(p.getId()).map(Product::getCurrentQuantity).orElse(0));
        } catch (Exception e) { printError(e.getMessage()); }
    }

    private void registrarSaida() {
        try {
            Product p = selecionarProduto(); if (p == null) return;
            System.out.print("Quantidade a sair: "); int qty = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("Motivo           : "); String reason = scanner.nextLine().trim();
            inventoryService.registrarSaida(p.getId(), qty, reason, authService.getCurrentUser());
            System.out.printf("✅  Saída registrada. Saldo atual: %d%n", inventoryService.buscarPorId(p.getId()).map(Product::getCurrentQuantity).orElse(0));
        } catch (EstoqueInsuficienteException e) {
            printError("Estoque insuficiente: disponível=%d, solicitado=%d".formatted(e.getAvailable(), e.getRequested()));
        } catch (Exception e) { printError(e.getMessage()); }
    }

    private void registrarAjuste() {
        try {
            Product p = selecionarProduto(); if (p == null) return;
            System.out.printf("Saldo atual: %d%n", p.getCurrentQuantity());
            System.out.print("Novo saldo  : "); int qty = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("Motivo      : "); String reason = scanner.nextLine().trim();
            inventoryService.registrarAjuste(p.getId(), qty, reason, authService.getCurrentUser());
            System.out.println("✅  Ajuste registrado.");
        } catch (Exception e) { printError(e.getMessage()); }
    }

    // ==========================
    //  MENU 3 — Consultas
    // ==========================

    private boolean menuConsultas() {
        System.out.println("""
                
                ── CONSULTAS ─────────────────────────────
                [1] Listar todos os produtos
                [2] Buscar por SKU
                [3] Buscar por nome
                [4] Estoque baixo ⚠
                [5] Histórico de movimentações
                [0] Voltar
                ──────────────────────────────────────────""");
        return switch (scanner.nextLine().trim()) {
            case "1" -> { listarProdutos(inventoryService.listarAtivos()); yield true; }
            case "2" -> { buscarPorSku(); yield true; }
            case "3" -> { buscarPorNome(); yield true; }
            case "4" -> { listarEstoqueBaixo(); yield true; }
            case "5" -> { listarMovimentos(); yield true; }
            case "0" -> true;
            default  -> { printError("Opção inválida."); yield true; }
        };
    }

    private void listarProdutos(List<Product> produtos) {
        if (produtos.isEmpty()) { System.out.println("Nenhum produto encontrado."); return; }
        System.out.println("\n%-5s %-15s %-30s %8s %8s %6s %6s %s"
                .formatted("ID", "SKU", "Nome", "Custo", "Venda", "Qtd", "Mín", "Alerta"));
        System.out.println("─".repeat(95));
        produtos.forEach(p -> System.out.printf("%-5d %-15s %-30s %8.2f %8.2f %6d %6d %s%n",
                p.getId(), p.getSku(), p.getName(),
                p.getCostPrice(), p.getSellingPrice(),
                p.getCurrentQuantity(), p.getMinThreshold(),
                p.isLowStock() ? "⚠ BAIXO" : ""));
    }

    private void buscarPorSku() {
        System.out.print("SKU: ");
        inventoryService.buscarPorSku(scanner.nextLine().trim())
                .ifPresentOrElse(
                        p -> listarProdutos(List.of(p)),
                        () -> printError("Produto não encontrado."));
    }

    private void buscarPorNome() {
        System.out.print("Termo de busca: ");
        List<Product> result = inventoryService.buscarPorNome(scanner.nextLine().trim());
        if (result.isEmpty()) printError("Nenhum produto encontrado.");
        else listarProdutos(result);
    }

    private void listarEstoqueBaixo() {
        List<Product> baixos = inventoryService.listarEstoqueBaixo();
        if (baixos.isEmpty()) System.out.println("✅  Nenhum produto em situação de estoque baixo.");
        else {
            System.out.println("\n⚠  PRODUTOS COM ESTOQUE ABAIXO DO MÍNIMO:");
            listarProdutos(baixos);
        }
    }

    private void listarMovimentos() {
        List<Movement> movs = inventoryService.listarMovimentos();
        if (movs.isEmpty()) { System.out.println("Nenhuma movimentação registrada."); return; }
        System.out.println("\n── Movimentações ──────────────────────────────────────────────────────");
        movs.stream().limit(50).forEach(m -> System.out.println(m));
        if (movs.size() > 50) System.out.printf("... e mais %d registros (exporte para ver todos).%n", movs.size() - 50);
    }

    // ==========================
    //  MENU 4 — Relatórios
    // ==========================

    private boolean menuRelatorios() {
        System.out.println("\n── RELATÓRIOS ────────────────────────────────");
        User user = authService.getCurrentUser();
        System.out.printf("Usuário: %s | Perfil: %s%n", user.getUsername(), user.getRole());
        System.out.printf("Total de produtos ativos : %d%n", inventoryService.listarAtivos().size());
        System.out.printf("Produtos com estoque baixo: %d%n", inventoryService.listarEstoqueBaixo().size());
        System.out.printf("Total de movimentações    : %d%n", inventoryService.listarMovimentos().size());
        System.out.println("──────────────────────────────────────────────");
        return true;
    }

    // ==========================
    //  MENU 5 — Exportação (RF05)
    // ==========================

    private boolean menuExportacao() {
        System.out.println("""
                
                ── EXPORTAÇÃO ────────────────────────────
                [1] Exportar Produtos (CSV)
                [2] Exportar Movimentações (CSV)
                [0] Voltar
                ──────────────────────────────────────────""");
        return switch (scanner.nextLine().trim()) {
            case "1" -> { exportarProdutos(); yield true; }
            case "2" -> { exportarMovimentos(); yield true; }
            case "0" -> true;
            default  -> { printError("Opção inválida."); yield true; }
        };
    }

    private void exportarProdutos() {
        try {
            Path file = exportService.exportProductsCsv(inventoryService.listarTodos());
            System.out.println("✅  Exportado para: " + file.toAbsolutePath());
        } catch (IOException e) { printError("Falha na exportação: " + e.getMessage()); }
    }

    private void exportarMovimentos() {
        try {
            Path file = exportService.exportMovementsCsv(inventoryService.listarMovimentos());
            System.out.println("✅  Exportado para: " + file.toAbsolutePath());
        } catch (IOException e) { printError("Falha na exportação: " + e.getMessage()); }
    }

    // ---- Utilitários de UI ----

    private Product selecionarProduto() {
        System.out.print("ID ou SKU do produto: ");
        String input = scanner.nextLine().trim();
        Optional<Product> opt;
        try {
            opt = inventoryService.buscarPorId(Long.parseLong(input));
        } catch (NumberFormatException e) {
            opt = inventoryService.buscarPorSku(input);
        }
        if (opt.isEmpty()) { printError("Produto não encontrado."); return null; }
        Product p = opt.get();
        System.out.printf("→ %s (SKU: %s) | Saldo: %d%n", p.getName(), p.getSku(), p.getCurrentQuantity());
        return p;
    }

    private void printMainMenu() {
        System.out.println("""
                
                ╔══════════════════════════════════════════╗
                ║     INOVA TECH — INVENTORY MANAGEMENT    ║
                ╠══════════════════════════════════════════╣
                ║  [1] Cadastro de Produtos                ║
                ║  [2] Movimentações de Estoque            ║
                ║  [3] Consultas                           ║
                ║  [4] Relatório Geral                     ║
                ║  [5] Exportação                          ║
                ║  [0] Sair                                ║
                ╚══════════════════════════════════════════╝
                Opção: \s""");
    }

    private void printBanner() {
        System.out.println("""
                
                ██╗███╗   ██╗ ██████╗ ██╗   ██╗ █████╗     ████████╗███████╗ ██████╗██╗  ██╗
                ██║████╗  ██║██╔═══██╗██║   ██║██╔══██╗    ╚══██╔══╝██╔════╝██╔════╝██║  ██║
                ██║██╔██╗ ██║██║   ██║██║   ██║███████║       ██║   █████╗  ██║     ███████║
                ██║██║╚██╗██║██║   ██║╚██╗ ██╔╝██╔══██║       ██║   ██╔══╝  ██║     ██╔══██║
                ██║██║ ╚████║╚██████╔╝ ╚████╔╝ ██║  ██║       ██║   ███████╗╚██████╗██║  ██║
                ╚═╝╚═╝  ╚═══╝ ╚═════╝   ╚═══╝  ╚═╝  ╚═╝       ╚═╝   ╚══════╝ ╚═════╝╚═╝  ╚═╝
                           Inova Tech — Inventory Management System v1.0.0
                """);
    }

    private void printError(String msg) {
        System.out.println("❌  " + msg);
    }

    private void printAlert(String msg) {
        System.out.println("\n🔔  " + msg);
    }
}
