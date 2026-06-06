package br.com.inovatech.repository;

import java.util.List;
import java.util.Optional;

/**
 * Interface genérica do padrão DAO.
 * Define as operações CRUD básicas para qualquer entidade.
 *
 * @param <T>  Tipo da entidade
 * @param <ID> Tipo do identificador
 */
public interface Repository<T, ID> {

    void save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    void delete(ID id);

    /**
     * Persiste todas as alterações em memória no meio de armazenamento (arquivo JSON).
     */
    void flush();
}
