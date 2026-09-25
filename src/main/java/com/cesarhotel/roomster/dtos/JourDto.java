package com.cesarhotel.roomster.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Une ligne de la vue semaine : un jour et ses pointages.
 */
@AllArgsConstructor
@Data
public class JourDto {
    private String libelle;        // ex : "lundi 21/09"
    private boolean aujourdhui;
    private List<String> lignes;   // ex : "08:00 → 12:00 (4h00)"
    private String total;          // ex : "7h30"
    private String ferie;          // nom du jour férié, ex : "Fête nationale" (null sinon)
}
