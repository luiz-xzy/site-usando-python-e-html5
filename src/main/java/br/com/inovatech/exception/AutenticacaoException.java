package br.com.inovatech.exception;

/**
 * Lançada quando credenciais de login são inválidas.
 */
public class AutenticacaoException extends Exception {
    public AutenticacaoException(String message) {
        super(message);
    }
}
