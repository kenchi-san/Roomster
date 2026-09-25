package com.cesarhotel.roomster.dtos;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Récapitulatif mensuel pour préparer la paie : une ligne par employé.
 */
@Data
public class RecapMoisDto {
    private String libelle;              // ex : "juillet 2026"
    private LocalDate moisPrecedent;
    private LocalDate moisSuivant;
    private List<LigneRecapDto> lignes;
}
