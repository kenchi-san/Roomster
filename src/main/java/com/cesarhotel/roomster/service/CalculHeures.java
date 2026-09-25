package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.Pointage;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Calcul des heures (F3) : uniquement des fonctions "pures".
 * Elles ne lisent pas la base de données : elles reçoivent des pointages et renvoient des durées.
 * C'est ce qui permet de les tester facilement (voir CalculHeuresTest).
 *
 * Règles (convention HCR IDCC 1979 + Code du travail) :
 * - un pointage encore ouvert (sans sortie) ne compte pas ;
 * - heures de nuit = la partie d'un pointage comprise entre 22h et 7h ;
 * - heures sup = au-delà de 35h par semaine civile, en tranches +10 % / +20 % / +50 % (voir decompter) ;
 * - temps partiel : entre le contrat et 35h, ce sont des heures complémentaires (+10 % / +25 %) ;
 * - calcul à la minute, sans arrondi.
 */
public final class CalculHeures {

    public static final LocalTime DEBUT_NUIT = LocalTime.of(22, 0);
    public static final LocalTime FIN_NUIT = LocalTime.of(7, 0);

    /** Durée légale : les heures sup commencent à la 36e heure. */
    public static final Duration DUREE_LEGALE = Duration.ofHours(35);

    /** Largeur des deux premières tranches d'heures sup (36e-39e à +10 %, 40e-43e à +20 %). */
    private static final Duration TRANCHE = Duration.ofHours(4);

    private CalculHeures() {
        // uniquement des méthodes statiques
    }

    /** Durée d'un pointage. Zéro s'il est encore ouvert. */
    public static Duration duree(Pointage pointage) {
        if (pointage.getSortie() == null) {
            return Duration.ZERO;
        }
        return Duration.between(pointage.getEntree(), pointage.getSortie());
    }

    /** Somme des durées de tous les pointages fermés. */
    public static Duration totalTravaille(List<Pointage> pointages) {
        Duration total = Duration.ZERO;
        for (Pointage pointage : pointages) {
            total = total.plus(duree(pointage));
        }
        return total;
    }

    /** Somme des heures de nuit de tous les pointages fermés. */
    public static Duration totalNuit(List<Pointage> pointages) {
        Duration total = Duration.ZERO;
        for (Pointage pointage : pointages) {
            if (pointage.getSortie() != null) {
                total = total.plus(heuresDeNuit(pointage.getEntree(), pointage.getSortie()));
            }
        }
        return total;
    }

    /**
     * Partie de la séquence [entree, sortie] comprise dans une plage de nuit 22h → 7h.
     * Ex : 18h → 1h contient 3h de nuit (22h → 1h).
     *
     * On regarde chaque nuit qui peut toucher la séquence (celle qui commence la veille de l'entrée,
     * jusqu'à celle qui commence le jour de la sortie) et on additionne les chevauchements.
     */
    public static Duration heuresDeNuit(LocalDateTime entree, LocalDateTime sortie) {
        return heuresDansPlage(entree, sortie, DEBUT_NUIT, FIN_NUIT);
    }

    /**
     * Partie de la séquence [entree, sortie] comprise dans une plage horaire qui passe minuit,
     * ex : 22h → 7h (nuit) ou 23h30 → 6h (nuit d'un mineur).
     */
    public static Duration heuresDansPlage(LocalDateTime entree, LocalDateTime sortie,
                                           LocalTime debutPlage, LocalTime finPlage) {
        Duration total = Duration.ZERO;
        LocalDate jour = entree.toLocalDate().minusDays(1);

        while (!jour.isAfter(sortie.toLocalDate())) {
            LocalDateTime debut = jour.atTime(debutPlage);          // ex : lundi 22h
            LocalDateTime fin = jour.plusDays(1).atTime(finPlage);  // ex : mardi 7h
            total = total.plus(chevauchement(entree, sortie, debut, fin));
            jour = jour.plusDays(1);
        }
        return total;
    }

    // --- Calcul limité à une période (ex : une semaine) ---

    /**
     * Total travaillé à l'intérieur de [debut, fin[ : un pointage qui déborde est coupé.
     * Ex : pour la semaine du lundi, un pointage dimanche 22h → lundi 6h ne compte que pour 6h.
     */
    public static Duration totalEntre(List<Pointage> pointages, LocalDateTime debut, LocalDateTime fin) {
        Duration total = Duration.ZERO;
        for (Pointage pointage : pointages) {
            if (pointage.getSortie() != null) {
                total = total.plus(chevauchement(pointage.getEntree(), pointage.getSortie(), debut, fin));
            }
        }
        return total;
    }

    /** Heures de nuit à l'intérieur de [debut, fin[, même découpage que totalEntre. */
    public static Duration totalNuitEntre(List<Pointage> pointages, LocalDateTime debut, LocalDateTime fin) {
        Duration total = Duration.ZERO;
        for (Pointage pointage : pointages) {
            if (pointage.getSortie() == null) {
                continue;
            }
            LocalDateTime entree = pointage.getEntree().isAfter(debut) ? pointage.getEntree() : debut;
            LocalDateTime sortie = pointage.getSortie().isBefore(fin) ? pointage.getSortie() : fin;
            if (entree.isBefore(sortie)) {
                total = total.plus(heuresDeNuit(entree, sortie));
            }
        }
        return total;
    }

    /**
     * Répartit les heures d'une semaine par taux de majoration (voir DecompteHeures).
     *
     * Heures sup : elles commencent à la 36e heure, QUEL QUE SOIT le contrat.
     * Ex : contrat 39h, 39h travaillées → 4h sup à +10 % (heures "structurelles", déjà prévues au contrat).
     *
     * Heures complémentaires : seulement pour un temps partiel (contrat < 35h), entre le contrat et 35h.
     * Ex : contrat 20h, 25h travaillées → 2h à +10 % (le dixième de 20h) et 3h à +25 %.
     */
    public static DecompteHeures decompter(Duration totalSemaine, Duration dureeContrat) {
        // --- Heures sup, au-delà de 35h ---
        Duration heuresSup = positif(totalSemaine.minus(DUREE_LEGALE));
        Duration sup10 = min(heuresSup, TRANCHE);                              // 36e → 39e heure
        Duration sup20 = min(positif(heuresSup.minus(TRANCHE)), TRANCHE);       // 40e → 43e heure
        Duration sup50 = positif(heuresSup.minus(TRANCHE.multipliedBy(2)));     // à partir de la 44e

        // --- Heures complémentaires, entre le contrat et 35h (temps partiel) ---
        Duration complementaires10 = Duration.ZERO;
        Duration complementaires25 = Duration.ZERO;
        if (dureeContrat.compareTo(DUREE_LEGALE) < 0) {
            Duration complementaires = positif(min(totalSemaine, DUREE_LEGALE).minus(dureeContrat));
            Duration dixiemeDuContrat = dureeContrat.dividedBy(10);
            complementaires10 = min(complementaires, dixiemeDuContrat);
            complementaires25 = positif(complementaires.minus(dixiemeDuContrat));
        }

        return new DecompteHeures(sup10, sup20, sup50, complementaires10, complementaires25);
    }

    /**
     * Heures sup à ajouter au compteur "heures sup cumulées" : celles qui dépassent à la fois 35h ET le contrat.
     * Les heures sup prévues au contrat (36e à 39e pour un 39h) sont déjà payées dans le salaire mensuel.
     * Les heures complémentaires d'un temps partiel sont payées, elles ne vont pas au compteur.
     */
    public static Duration heuresSupAuDelaDuContrat(Duration totalSemaine, Duration dureeContrat) {
        Duration seuil = dureeContrat.compareTo(DUREE_LEGALE) > 0 ? dureeContrat : DUREE_LEGALE;
        return positif(totalSemaine.minus(seuil));
    }

    /** Ex : 7h30 → "7h30", 45 minutes → "0h45". */
    public static String formater(Duration duree) {
        String signe = duree.isNegative() ? "-" : "";
        Duration positive = duree.abs();
        return signe + positive.toHours() + "h" + String.format("%02d", positive.toMinutesPart());
    }

    /** La durée si elle est positive, sinon zéro. */
    private static Duration positif(Duration duree) {
        return duree.isNegative() ? Duration.ZERO : duree;
    }

    /** La plus petite des deux durées. */
    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /** Durée commune aux deux plages [debut1, fin1] et [debut2, fin2]. Zéro si elles ne se touchent pas. */
    private static Duration chevauchement(LocalDateTime debut1, LocalDateTime fin1,
                                          LocalDateTime debut2, LocalDateTime fin2) {
        LocalDateTime debut = debut1.isAfter(debut2) ? debut1 : debut2;  // le plus tard des deux débuts
        LocalDateTime fin = fin1.isBefore(fin2) ? fin1 : fin2;          // la plus tôt des deux fins
        if (debut.isBefore(fin)) {
            return Duration.between(debut, fin);
        }
        return Duration.ZERO;
    }
}
