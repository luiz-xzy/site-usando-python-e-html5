package br.com.inovatech.service;

import br.com.inovatech.exception.AutenticacaoException;
import br.com.inovatech.model.Role;
import br.com.inovatech.model.User;
import br.com.inovatech.repository.JsonUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Serviço de autenticação e gestão de usuários (RF02).
 *
 * <p>Senhas são armazenadas exclusivamente como hash SHA-256.
 * Em produção, recomenda-se substituir por BCrypt (via lib externa como
 * Spring Security Crypto ou jBCrypt), que inclui salt e fator de custo.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ALGORITHM = "SHA-256";

    private final JsonUserRepository userRepository;
    private User currentUser;

    public AuthService(JsonUserRepository userRepository) {
        this.userRepository = userRepository;
        seedDefaultUsers();
    }

    // ---- Criação dos usuários padrão do sistema ----

    private void seedDefaultUsers() {
        if (userRepository.findAll().isEmpty()) {
            log.info("Nenhum usuário encontrado. Criando usuários padrão...");
            userRepository.save(new User("admin",   hashPassword("admin123"),   Role.GERENTE));
            userRepository.save(new User("supervisor", hashPassword("super123"), Role.SUPERVISOR));
            userRepository.flush();
            log.info("Usuários padrão criados: admin (GERENTE), supervisor (SUPERVISOR).");
        }
    }

    // ---- Autenticação ----

    /**
     * Autentica o usuário e inicia a sessão (RF02).
     *
     * @throws AutenticacaoException se credenciais forem inválidas
     */
    public User login(String username, String rawPassword) throws AutenticacaoException {
        if (username == null || rawPassword == null) {
            throw new AutenticacaoException("Usuário e senha são obrigatórios.");
        }

        User user = userRepository.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new AutenticacaoException("Usuário não encontrado: " + username));

        if (!user.isActive()) {
            throw new AutenticacaoException("Conta desativada. Contate o administrador.");
        }

        if (!user.getPasswordHash().equals(hashPassword(rawPassword))) {
            log.warn("Tentativa de login com senha incorreta para o usuário: {}", username);
            throw new AutenticacaoException("Senha incorreta.");
        }

        this.currentUser = user;
        log.info("Login realizado com sucesso: {} ({})", username, user.getRole());
        return user;
    }

    public void logout() {
        if (currentUser != null) {
            log.info("Logout: {}", currentUser.getUsername());
        }
        currentUser = null;
    }

    public User getCurrentUser() { return currentUser; }
    public boolean isLoggedIn()  { return currentUser != null; }

    // ---- Gestão de usuários (apenas GERENTE) ----

    public User createUser(String username, String rawPassword, Role role)
            throws AutenticacaoException {
        requireRole(Role.GERENTE);
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Usuário já existe: " + username);
        }
        User user = new User(username, hashPassword(rawPassword), role);
        userRepository.save(user);
        userRepository.flush();
        log.info("Novo usuário criado: {} ({})", username, role);
        return user;
    }

    public void changePassword(String username, String newRawPassword)
            throws AutenticacaoException {
        requireRole(Role.GERENTE);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + username));
        user.setPasswordHash(hashPassword(newRawPassword));
        userRepository.save(user);
        userRepository.flush();
        log.info("Senha alterada para usuário: {}", username);
    }

    // ---- Utilitário de hashing ----

    /**
     * Gera o hash SHA-256 da senha fornecida.
     */
    public static String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é garantido pela JVM — nunca deve ocorrer
            throw new IllegalStateException("Algoritmo de hash indisponível: " + ALGORITHM, e);
        }
    }

    // ---- Controle de acesso ----

    private void requireRole(Role required) throws AutenticacaoException {
        if (!isLoggedIn() || currentUser.getRole() != required) {
            throw new AutenticacaoException(
                    "Acesso negado. Operação restrita ao perfil: " + required.getDescricao());
        }
    }
}
