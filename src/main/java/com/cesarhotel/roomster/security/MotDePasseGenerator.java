package com.cesarhotel.roomster.security;

import java.security.SecureRandom;

/**
 * Génère des mots de passe temporaires à communiquer à la main par un admin (pas d'envoi d'email en v1).
 * Alphabet restreint aux caractères non ambigus (pas de 0/O, 1/l/I) pour rester lisible/retapable.
 */
public final class MotDePasseGenerator {

    private static final String CARACTERES = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int LONGUEUR = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private MotDePasseGenerator() {
    }

    public static String genererMotDePasseTemporaire() {
        StringBuilder motDePasse = new StringBuilder(LONGUEUR);
        for (int i = 0; i < LONGUEUR; i++) {
            motDePasse.append(CARACTERES.charAt(RANDOM.nextInt(CARACTERES.length())));
        }
        return motDePasse.toString();
    }
}
