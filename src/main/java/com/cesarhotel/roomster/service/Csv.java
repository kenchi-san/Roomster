package com.cesarhotel.roomster.service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Petites fonctions pour écrire un fichier CSV lisible directement par Excel en français :
 * séparateur ";" , virgule décimale, et fichier en UTF-8 avec BOM (sinon Excel affiche mal les accents).
 */
public final class Csv {

    /** Caractère invisible placé au tout début du fichier pour qu'Excel le lise en UTF-8. */
    public static final String BOM = "\uFEFF";

    private static final String SEPARATEUR = ";";
    private static final String FIN_DE_LIGNE = "\r\n";

    private Csv() {
        // uniquement des méthodes statiques
    }

    /** Une ligne du fichier : les valeurs séparées par ";", terminée par un retour à la ligne. */
    public static String ligne(String... valeurs) {
        StringBuilder ligne = new StringBuilder();
        for (int i = 0; i < valeurs.length; i++) {
            if (i > 0) {
                ligne.append(SEPARATEUR);
            }
            ligne.append(echapper(valeurs[i]));
        }
        return ligne.append(FIN_DE_LIGNE).toString();
    }

    /** Même chose à partir d'une liste (pratique quand certaines colonnes sont facultatives). */
    public static String ligne(List<String> valeurs) {
        return ligne(valeurs.toArray(new String[0]));
    }

    /**
     * Une valeur qui contient ; " ou un retour à la ligne est mise entre guillemets,
     * et ses guillemets sont doublés. null devient une case vide.
     */
    public static String echapper(String valeur) {
        if (valeur == null) {
            return "";
        }
        if (valeur.contains(SEPARATEUR) || valeur.contains("\"") || valeur.contains("\n") || valeur.contains("\r")) {
            return "\"" + valeur.replace("\"", "\"\"") + "\"";
        }
        return valeur;
    }

    /** Durée en heures décimales avec une virgule, pour pouvoir additionner dans Excel. Ex : 7h30 → "7,50". */
    public static String heures(Duration duree) {
        return String.format(Locale.FRANCE, "%.2f", duree.toMinutes() / 60.0);
    }

    /** Nombre avec 2 décimales et une virgule. Ex : 2.5 → "2,50". */
    public static String nombre(double valeur) {
        return String.format(Locale.FRANCE, "%.2f", valeur);
    }
}
