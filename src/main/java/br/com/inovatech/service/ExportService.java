package br.com.inovatech.service;

import br.com.inovatech.model.Movement;
import br.com.inovatech.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serviço de exportação de relatórios (RF05).
 * Gera arquivos .csv e .json usando java.nio (NIO).
 */
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final DateTimeFormatter FILE_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final String exportDir;

    public ExportService(String exportDir) {
        this.exportDir = exportDir;
    }

    // ---- Exportação de Produtos ----

    /**
     * Exporta lista de produtos para CSV.
     *
     * @return caminho do arquivo gerado
     */
    public Path exportProductsCsv(List<Product> products) throws IOException {
        String timestamp = LocalDateTime.now().format(FILE_DATE_FMT);
        Path filePath = Paths.get(exportDir, "produtos_" + timestamp + ".csv");

        StringBuilder sb = new StringBuilder();
        sb.append("ID,SKU,Nome,\"Preço Custo\",\"Preço Venda\",\"Qtd Atual\",\"Limiar Mínimo\",\"Margem %\",Status,Alerta\n");

        for (Product p : products) {
            sb.append("%d,%s,\"%s\",%s,%s,%d,%d,%s,%s,%s\n".formatted(
                    p.getId(),
                    p.getSku(),
                    p.getName().replace("\"", "\"\""),
                    p.getCostPrice().toPlainString(),
                    p.getSellingPrice().toPlainString(),
                    p.getCurrentQuantity(),
                    p.getMinThreshold(),
                    p.getProfitMargin().toPlainString() + "%",
                    p.isActive() ? "ATIVO" : "INATIVO",
                    p.isLowStock() ? "⚠ ESTOQUE BAIXO" : "OK"));
        }

        write(filePath, sb.toString());
        log.info("Produtos exportados para CSV: {}", filePath);
        return filePath;
    }

    // ---- Exportação de Movimentações ----

    /**
     * Exporta movimentações para CSV.
     */
    public Path exportMovementsCsv(List<Movement> movements) throws IOException {
        String timestamp = LocalDateTime.now().format(FILE_DATE_FMT);
        Path filePath = Paths.get(exportDir, "movimentacoes_" + timestamp + ".csv");

        StringBuilder sb = new StringBuilder();
        sb.append("Data/Hora,Tipo,SKU,Quantidade,Responsável,Motivo\n");

        DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        for (Movement m : movements) {
            sb.append("%s,%s,%s,%d,%s,\"%s\"\n".formatted(
                    m.getTimestamp().format(dtFmt),
                    m.getType().name(),
                    m.getProductSku(),
                    m.getQuantity(),
                    m.getResponsible().getUsername(),
                    m.getReason().replace("\"", "\"\"")));
        }

        write(filePath, sb.toString());
        log.info("Movimentações exportadas para CSV: {}", filePath);
        return filePath;
    }

    // ---- Utilitário ----

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
