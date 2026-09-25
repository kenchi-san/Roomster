package com.cesarhotel.roomster.dtos;

import lombok.Data;

import java.util.List;

/**
 * Une ligne de la grille du planning : un employé et ses 7 jours.
 */
@Data
public class LignePlanningDto {
    private Long employeId;
    private String nom;                     // ex : "Jean Dupont"
    private String poste;
    private String totalPrevu;              // ex : "32h00"
    private String contrat;                 // ex : "35h00"
    /** "SOUS" (moins que le contrat), "EGAL", "AU_DESSUS" (heures sup prévues) : couleur de la colonne Prévu / contrat. */
    private String comparaisonContrat;
    private List<CellulePlanningDto> cellules;
    private List<String> alertes;           // contrôles HCR appliqués au planning prévu
}
