package com.cesarhotel.roomster.exception;

/**
 * Erreur de validation métier renvoyée au client sous forme de texte brut (400)
 * par GlobalExceptionHandler, pour éviter le corps JSON générique de Spring Boot
 * (dont le champ "message" est masqué par défaut) sur les endpoints REST typés.
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
