package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.Pointage;
import com.cesarhotel.roomster.model.Poste;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Contrôles de conformité à la convention HCR (IDCC 1979) : fonctions pures, voir ControlesHCRTest.
 * Elles ne bloquent rien : elles servent à afficher des anomalies au manager.
 *
 * - Durée maximale par jour : 11h pour un cuisinier, 12h pour un veilleur de nuit, 11h30 pour les autres.
 * - Durée maximale par semaine : 48h.
 * - Repos entre deux journées : 11h consécutives.
 * - Contingent annuel d'heures sup : 360h.
 * - Travailleur de nuit (au moins 2 jours dans la semaine avec 3h ou plus entre 22h et 7h) :
 *   repos compensateur de 1 % des heures de nuit.
 * - Moins de 18 ans : 8h par jour, 35h par semaine, 12h de repos, pas de travail entre 23h30 et 6h
 *   (dérogation du secteur hôtellerie-restauration jusqu'à 23h30).
 */
public final class ControlesHCR {

    public static final Duration MAX_JOUR_CUISINIER = Duration.ofHours(11);
    public static final Duration MAX_JOUR_VEILLEUR_NUIT = Duration.ofHours(12);
    public static final Duration MAX_JOUR_AUTRES = Duration.ofMinutes(11 * 60 + 30);
    public static final Duration MAX_JOUR_MINEUR = Duration.ofHours(8);

    public static final Duration MAX_SEMAINE = Duration.ofHours(48);
    public static final Duration MAX_SEMAINE_MINEUR = Duration.ofHours(35);

    public static final Duration REPOS_QUOTIDIEN = Duration.ofHours(11);
    public static final Duration REPOS_QUOTIDIEN_MINEUR = Duration.ofHours(12);

    public static final Duration CONTINGENT_ANNUEL = Duration.ofHours(360);

    public static final Duration NUIT_MIN_PAR_JOUR = Duration.ofHours(3);
    public static final int JOURS_MIN_TRAVAILLEUR_NUIT = 2;

    public static final LocalTime DEBUT_NUIT_MINEUR = LocalTime.of(23, 30);
    public static final LocalTime FIN_NUIT_MINEUR = LocalTime.of(6, 0);

    private ControlesHCR() {
        // uniquement des méthodes statiques
    }

    // --- Limites selon le salarié ---

    public static Duration dureeMaxJour(Poste poste, boolean mineur) {
        if (mineur) {
            return MAX_JOUR_MINEUR;
        }
        if (poste == Poste.CUISINE) {
            return MAX_JOUR_CUISINIER;
        }
        if (poste == Poste.VEILLEUR_NUIT) {
            return MAX_JOUR_VEILLEUR_NUIT;
        }
        return MAX_JOUR_AUTRES;
    }

    public static Duration dureeMaxSemaine(boolean mineur) {
        return mineur ? MAX_SEMAINE_MINEUR : MAX_SEMAINE;
    }

    public static Duration reposMinimum(boolean mineur) {
        return mineur ? REPOS_QUOTIDIEN_MINEUR : REPOS_QUOTIDIEN;
    }

    // --- Contrôles ---

    /** Total travaillé par jour (jour de l'entrée), pointages fermés seulement, dans l'ordre des jours. */
    public static Map<LocalDate, Duration> totalParJour(List<Pointage> pointages) {
        Map<LocalDate, Duration> totaux = new TreeMap<>();
        for (Pointage pointage : pointages) {
            if (pointage.getSortie() != null) {
                LocalDate jour = pointage.getEntree().toLocalDate();
                totaux.put(jour, totaux.getOrDefault(jour, Duration.ZERO).plus(CalculHeures.duree(pointage)));
            }
        }
        return totaux;
    }

    /**
     * Repos trop courts entre deux journées : pour chaque jour travaillé, on mesure le temps entre
     * la dernière sortie de la journée précédente et la première entrée du jour.
     * Renvoie les jours concernés avec le repos réellement pris. Les coupures dans une même journée ne comptent pas.
     */
    public static Map<LocalDate, Duration> reposTropCourts(List<Pointage> pointages, Duration minimum) {
        // Première entrée et dernière sortie de chaque jour (jour de l'entrée)
        Map<LocalDate, LocalDateTime> premiereEntree = new TreeMap<>();
        Map<LocalDate, LocalDateTime> derniereSortie = new TreeMap<>();
        for (Pointage pointage : pointages) {
            if (pointage.getSortie() == null) {
                continue;
            }
            LocalDate jour = pointage.getEntree().toLocalDate();
            if (!premiereEntree.containsKey(jour) || pointage.getEntree().isBefore(premiereEntree.get(jour))) {
                premiereEntree.put(jour, pointage.getEntree());
            }
            if (!derniereSortie.containsKey(jour) || pointage.getSortie().isAfter(derniereSortie.get(jour))) {
                derniereSortie.put(jour, pointage.getSortie());
            }
        }

        Map<LocalDate, Duration> tropCourts = new TreeMap<>();
        LocalDate jourPrecedent = null;
        for (LocalDate jour : premiereEntree.keySet()) {
            if (jourPrecedent != null) {
                Duration repos = Duration.between(derniereSortie.get(jourPrecedent), premiereEntree.get(jour));
                if (repos.compareTo(minimum) < 0) {
                    tropCourts.put(jour, repos);
                }
            }
            jourPrecedent = jour;
        }
        return tropCourts;
    }

    /** Travailleur de nuit cette semaine : au moins 2 jours avec 3h ou plus entre 22h et 7h. */
    public static boolean estTravailleurDeNuit(List<Pointage> pointagesDeLaSemaine) {
        Map<LocalDate, Duration> nuitParJour = new TreeMap<>();
        for (Pointage pointage : pointagesDeLaSemaine) {
            if (pointage.getSortie() != null) {
                LocalDate jour = pointage.getEntree().toLocalDate();
                Duration nuit = CalculHeures.heuresDeNuit(pointage.getEntree(), pointage.getSortie());
                nuitParJour.put(jour, nuitParJour.getOrDefault(jour, Duration.ZERO).plus(nuit));
            }
        }

        int joursDeNuit = 0;
        for (Duration nuit : nuitParJour.values()) {
            if (nuit.compareTo(NUIT_MIN_PAR_JOUR) >= 0) {
                joursDeNuit++;
            }
        }
        return joursDeNuit >= JOURS_MIN_TRAVAILLEUR_NUIT;
    }

    /** Repos compensateur d'un travailleur de nuit : 1 % des heures de nuit. Ex : 40h de nuit → 24 minutes. */
    public static Duration reposCompensateurNuit(Duration heuresDeNuit) {
        return heuresDeNuit.dividedBy(100);
    }

    /** Jours où un mineur a travaillé entre 23h30 et 6h (interdit, même avec la dérogation HCR). */
    public static List<LocalDate> nuitsInterditesMineur(List<Pointage> pointages) {
        List<LocalDate> jours = new ArrayList<>();
        for (Pointage pointage : pointages) {
            if (pointage.getSortie() == null) {
                continue;
            }
            Duration pendantLaNuit = CalculHeures.heuresDansPlage(pointage.getEntree(), pointage.getSortie(),
                    DEBUT_NUIT_MINEUR, FIN_NUIT_MINEUR);
            LocalDate jour = pointage.getEntree().toLocalDate();
            if (pendantLaNuit.isPositive() && !jours.contains(jour)) {
                jours.add(jour);
            }
        }
        return jours;
    }
}
