package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.DemandeAbsence;
import com.cesarhotel.roomster.model.TypeAbsence;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Calcul des congés payés : fonctions pures, aucune base de données (voir CalculCongesTest).
 *
 * Règles (Code du travail + convention HCR) :
 * - période de référence : du 1er juin au 31 mai de l'année suivante ;
 * - décompte en jours ouvrables : du lundi au samedi, sans le dimanche ni les jours fériés ;
 * - acquisition : 2,5 jours ouvrables par mois de travail effectif (30 jours par an) ;
 *   un mois d'arrêt maladie donne 2 jours (loi du 22 avril 2024, 24 jours par an au maximum) ;
 *   un mois de congé sans solde ne donne rien.
 */
public final class CalculConges {

    public static final double JOURS_PAR_MOIS = 2.5;
    public static final double JOURS_PAR_MOIS_MALADIE = 2.0;

    private CalculConges() {
        // uniquement des méthodes statiques
    }

    /**
     * La période de référence qui contient ce jour, désignée par l'année où elle commence.
     * Ex : 15/09/2026 → 2026 (juin 2026 → mai 2027) ; 15/03/2026 → 2025 (juin 2025 → mai 2026).
     */
    public static int periodeDe(LocalDate jour) {
        if (jour.getMonthValue() >= 6) {
            return jour.getYear();
        }
        return jour.getYear() - 1;
    }

    /** Nombre de jours ouvrables entre deux dates incluses : ni dimanche, ni jour férié. */
    public static long joursOuvrables(LocalDate debut, LocalDate fin) {
        long jours = 0;
        LocalDate jour = debut;
        while (!jour.isAfter(fin)) {
            if (jour.getDayOfWeek() != DayOfWeek.SUNDAY && !JoursFeries.estFerie(jour)) {
                jours++;
            }
            jour = jour.plusDays(1);
        }
        return jours;
    }

    /**
     * Jours de CP acquis sur un mois, au prorata des jours du mois :
     * - jour hors contrat (avant l'entrée ou après la sortie) : 0 ;
     * - jour de congé sans solde validé : 0 ;
     * - jour d'arrêt maladie validé : 2 jours / nombre de jours du mois ;
     * - tout autre jour (travail, CP, RTT…) : 2,5 jours / nombre de jours du mois.
     * Résultat arrondi au centième. Ex : un mois complet → 2,5 ; un mois entier de maladie → 2.
     *
     * Les plafonds (30 jours par an, 24 pour la maladie) sont atteints naturellement en 12 mois,
     * il n'y a donc pas besoin de les vérifier.
     */
    public static double joursAcquis(LocalDate premierJourDuMois, LocalDate dateEntree, LocalDate dateSortie,
                                     List<DemandeAbsence> absencesValidees) {
        int nombreDeJours = premierJourDuMois.lengthOfMonth();
        double acquis = 0;

        for (int i = 0; i < nombreDeJours; i++) {
            LocalDate jour = premierJourDuMois.plusDays(i);

            boolean sousContrat = !jour.isBefore(dateEntree) && (dateSortie == null || !jour.isAfter(dateSortie));
            if (!sousContrat) {
                continue;
            }

            TypeAbsence absence = absenceDuJour(absencesValidees, jour);
            if (absence == TypeAbsence.SANS_SOLDE) {
                continue;
            } else if (absence == TypeAbsence.MALADIE) {
                acquis += JOURS_PAR_MOIS_MALADIE / nombreDeJours;
            } else {
                acquis += JOURS_PAR_MOIS / nombreDeJours;
            }
        }
        return Math.round(acquis * 100) / 100.0;
    }

    /** Le type de l'absence qui couvre ce jour, ou null s'il n'y en a pas. */
    private static TypeAbsence absenceDuJour(List<DemandeAbsence> absences, LocalDate jour) {
        for (DemandeAbsence absence : absences) {
            if (absence.chevauche(jour, jour)) {
                return absence.getType();
            }
        }
        return null;
    }
}
