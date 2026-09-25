package com.cesarhotel.roomster.dtos;

import com.cesarhotel.roomster.model.DemandeAbsence;
import com.cesarhotel.roomster.model.Pointage;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Tout ce qu'affiche le tableau de bord du manager (F6).
 * "Prévu" = d'après le planning.
 * Les listes de personnes contiennent des textes prêts à afficher, ex : "Jean Dupont (depuis 08:02)".
 */
@Data
public class TableauDeBordDto {

    // --- Aujourd'hui ---
    private List<String> presents = new ArrayList<>();       // pointage ouvert
    private List<String> partis = new ArrayList<>();         // ont pointé aujourd'hui, pointage fermé
    private List<String> absents = new ArrayList<>();        // absence validée qui couvre aujourd'hui
    private List<String> sansPointage = new ArrayList<>();   // ni pointage, ni absence : statut inconnu
    private List<String> prevus = new ArrayList<>();         // planning du jour, ex : "Jean Dupont : 08:00 → 16:00"

    // --- À traiter ---
    private List<DemandeAbsence> demandesEnAttente = new ArrayList<>();
    private List<Pointage> sortiesAutomatiques = new ArrayList<>();  // clôturés à 12h (pas de pointage de sortie), à valider
    private List<String> prevusNonPointes = new ArrayList<>();       // créneau commencé aujourd'hui, pas de pointage
    private List<String> prevusHierSansPointage = new ArrayList<>(); // prévu hier, ni pointage ni absence
    private String hier;                                              // ex : "jeudi 24/09"

    // --- Conformité HCR (semaine en cours et semaine précédente, contingent annuel) ---
    private List<String> alertesHCR = new ArrayList<>();
}
