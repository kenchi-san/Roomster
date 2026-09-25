package com.cesarhotel.roomster.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Les 11 jours fériés français (métropole), calculés pour n'importe quelle année.
 * Fonctions pures : aucune base de données.
 */
public final class JoursFeries {

    private JoursFeries() {
        // uniquement des méthodes statiques
    }

    /** Le nom du jour férié, ou null si ce jour n'est pas férié. Ex : 14/07 → "Fête nationale". */
    public static String nom(LocalDate jour) {
        return liste(jour.getYear()).get(jour);
    }

    public static boolean estFerie(LocalDate jour) {
        return nom(jour) != null;
    }

    /** Les 11 jours fériés de l'année, dans l'ordre, avec leur nom. */
    public static Map<LocalDate, String> liste(int annee) {
        LocalDate paques = paques(annee);

        Map<LocalDate, String> feries = new LinkedHashMap<>();
        feries.put(LocalDate.of(annee, 1, 1), "Jour de l'an");
        feries.put(paques.plusDays(1), "Lundi de Pâques");
        feries.put(LocalDate.of(annee, 5, 1), "1er mai");
        feries.put(LocalDate.of(annee, 5, 8), "Victoire 1945");
        feries.put(paques.plusDays(39), "Ascension");
        feries.put(paques.plusDays(50), "Lundi de Pentecôte");
        feries.put(LocalDate.of(annee, 7, 14), "Fête nationale");
        feries.put(LocalDate.of(annee, 8, 15), "Assomption");
        feries.put(LocalDate.of(annee, 11, 1), "Toussaint");
        feries.put(LocalDate.of(annee, 11, 11), "Armistice 1918");
        feries.put(LocalDate.of(annee, 12, 25), "Noël");
        return feries;
    }

    /** Le 1er mai : seul jour férié obligatoirement chômé, payé double s'il est travaillé. */
    public static boolean estPremierMai(LocalDate jour) {
        return jour.getMonthValue() == 5 && jour.getDayOfMonth() == 1;
    }

    /**
     * Dimanche de Pâques (algorithme de Meeus / Jones / Butcher, calendrier grégorien).
     * Ex : 2026 → 5 avril, 2027 → 28 mars.
     */
    public static LocalDate paques(int annee) {
        int a = annee % 19;
        int b = annee / 100;
        int c = annee % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mois = (h + l - 7 * m + 114) / 31;
        int jour = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(annee, mois, jour);
    }
}
