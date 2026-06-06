package br.com.inovatech.repository;

import br.com.inovatech.model.Movement;
import br.com.inovatech.model.MovementType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Repositório de movimentações de estoque com persistência JSON.
 * Usa {@link CopyOnWriteArrayList} para acesso concorrente seguro.
 */
public class JsonMovementRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonMovementRepository.class);

    private final File dataFile;
    private final ObjectMapper mapper;
    private final List<Movement> movements = new CopyOnWriteArrayList<>();

    private static class MovementsWrapper {
        public List<Movement> movements = new ArrayList<>();
    }

    public JsonMovementRepository(String filePath) {
        this.dataFile = new File(filePath);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        load();
    }

    private void load() {
        if (!dataFile.exists()) return;
        try {
            MovementsWrapper wrapper = mapper.readValue(dataFile, MovementsWrapper.class);
            movements.addAll(wrapper.movements);
            log.info("Movimentações carregadas: {} registros.", movements.size());
        } catch (IOException e) {
            log.error("Falha ao carregar movimentações: {}", e.getMessage(), e);
        }
    }

    public void save(Movement movement) {
        movements.add(Objects.requireNonNull(movement));
    }

    public List<Movement> findAll() {
        return Collections.unmodifiableList(movements);
    }

    public List<Movement> findBySku(String sku) {
        return movements.stream()
                .filter(m -> m.getProductSku().equalsIgnoreCase(sku))
                .collect(Collectors.toList());
    }

    public List<Movement> findByType(MovementType type) {
        return movements.stream()
                .filter(m -> m.getType() == type)
                .collect(Collectors.toList());
    }

    public List<Movement> findByPeriod(LocalDateTime from, LocalDateTime to) {
        return movements.stream()
                .filter(m -> !m.getTimestamp().isBefore(from) && !m.getTimestamp().isAfter(to))
                .collect(Collectors.toList());
    }

    public void flush() {
        try {
            dataFile.getParentFile().mkdirs();
            MovementsWrapper wrapper = new MovementsWrapper();
            wrapper.movements = new ArrayList<>(movements);
            mapper.writeValue(dataFile, wrapper);
            log.info("Movimentações persistidas: {} registros.", movements.size());
        } catch (IOException e) {
            log.error("ERRO ao salvar movimentações: {}", e.getMessage(), e);
        }
    }
}
