package br.com.inovatech.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Representa um usuário do sistema com suas credenciais e perfil de acesso.
 * A senha é armazenada somente como hash SHA-256 — nunca em texto plano.
 */
public class User {

    private final String username;
    private String passwordHash;
    private Role role;
    private boolean active;

    @JsonCreator
    public User(@JsonProperty("username") String username,
                @JsonProperty("passwordHash") String passwordHash,
                @JsonProperty("role") Role role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username não pode ser vazio.");
        }
        this.username = username.trim().toLowerCase();
        this.passwordHash = Objects.requireNonNull(passwordHash, "Hash de senha obrigatório.");
        this.role = Objects.requireNonNull(role, "Role obrigatória.");
        this.active = true;
    }

    // ---- Getters ----

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }

    // ---- Setters controlados ----

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    public void setRole(Role role) {
        this.role = Objects.requireNonNull(role);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username);
    }

    @Override
    public String toString() {
        return "User{username='%s', role=%s, active=%s}".formatted(username, role, active);
    }
}
