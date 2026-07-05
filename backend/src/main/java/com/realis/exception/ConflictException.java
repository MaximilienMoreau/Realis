package com.realis.exception;

/**
 * Conflit métier explicite (ex. suppression d'un enregistrement déjà supprimé).
 * Distinct de IllegalStateException, qui peut aussi être levée par des erreurs internes
 * (crypto, JVM) ne devant pas être présentées comme un 409 au client.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
