package com.cesarhotel.roomster.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Un jour du planning d'un salarié.
 */
@AllArgsConstructor
@Data
public class JourPlanningDto {
    private String libelle;                 // ex : "lundi 21/09"
    private boolean aujourdhui;
    private String ferie;                   // nom du jour férié, ou null
    private String absence;                 // ex : "Congé payé", ou null
    private List<String> mesCreneaux;       // ex : "08:00 → 16:00 — Petit-déjeuner"
    private List<String> collegues;         // ex : "Marie Lefevre (HOUSEKEEPING) : 07:00 → 15:00"
    /** Collègues absents ce jour-là, sans détail : ex "Emma Rousseau (Maladie)", "Paul Martin (Congé payé)", "Lucas Petit (Absent)". */
    private List<String> absents;
}
