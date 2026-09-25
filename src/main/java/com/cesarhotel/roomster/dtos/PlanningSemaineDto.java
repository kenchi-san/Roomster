package com.cesarhotel.roomster.dtos;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Planning d'une semaine pour le manager : une grille employés × jours.
 */
@Data
public class PlanningSemaineDto {
    private LocalDate lundi;
    private LocalDate dimanche;
    private LocalDate semainePrecedente;
    private LocalDate semaineSuivante;
    private List<LocalDate> dates;          // les 7 jours, pour les liens "+" et le formulaire
    private List<String> jours;             // libellés des colonnes, ex : "lundi 21/09"
    private List<String> feries;            // nom du férié de chaque colonne, ou null
    private List<LignePlanningDto> lignes;  // une ligne par employé actif

    // Effectif prévu de chaque jour (même ordre que les colonnes)
    private List<Integer> effectifs;        // nombre de personnes planifiées
    private List<String> heuresPrevues;     // ex : "38h00"
    private List<String> repartitionPostes; // ex : "RECEPTION 2 · CUISINE 1"
    private List<Integer> absents;          // nombre d'absences validées
}
