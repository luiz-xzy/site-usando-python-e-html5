package br.com.inovatech.repository;

import br.com.inovatech.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositório de usuários com persistência JSON.
 */
public class JsonUserRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonUserRepository.class);

    private final File dataFile;
    private final ObjectMapper mapper;
    private final Map<String, User> store = new ConcurrentHashMap<>();

    private static class UsersWrapper {
        public List<User> users = new ArrayList<>();
    }

    public JsonUserRepository(String filePath) {
        this.dataFile = new File(filePath);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    private void load() {
        if (!dataFile.exists()) return;
        try {
            UsersWrapper wrapper = mapper.readValue(dataFile, UsersWrapper.class);
            wrapper.users.forEach(u -> store.put(u.getUsername(), u));
            log.info("Usuários carregados: {} registros.", store.size());
        } catch (IOException e) {
            log.error("Falha ao carregar usuários: {}", e.getMessage(), e);
        }
    }

    public void save(User user) {
        store.put(user.getUsername(), Objects.requireNonNull(user));
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username.toLowerCase()));
    }

    public List<User> findAll() {
        return new ArrayList<>(store.values());
    }

    public boolean existsByUsername(String username) {
        return store.containsKey(username.toLowerCase());
    }

    public void flush() {
        try {
            dataFile.getParentFile().mkdirs();
            UsersWrapper wrapper = new UsersWrapper();
            wrapper.users = new ArrayList<>(store.values());
            mapper.writeValue(dataFile, wrapper);
            log.info("Usuários persistidos: {} registros.", wrapper.users.size());
        } catch (IOException e) {
            log.error("ERRO ao salvar usuários: {}", e.getMessage(), e);
        }
    }
}
