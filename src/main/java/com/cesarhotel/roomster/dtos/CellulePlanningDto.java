package com.cesarhotel.roomster.dtos;

import com.cesarhotel.roomster.model.CreneauPlanning;
import com.cesarhotel.roomster.model.DemandeAbsence;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Une case de la grille du manager : tout ce qui concerne un employé un jour donné.
 */
@Data
public class CellulePlanningDto {
    private LocalDate jour;
    private List<CreneauPlanning> creneaux;
    private DemandeAbsence absence;          // absence validée ce jour-là, ou null
    private DemandeAbsence demandeEnAttente; // demande pas encore validée ce jour-là, ou null

    /** Planifié alors qu'une absence est validée (ex : CP validé après la création du planning). */
    private boolean conflit;

    /**
     * Seulement pour aujourd'hui, d'après les pointages :
     * "PRESENT" (pointage ouvert), "POINTE" (a pointé, pointage fermé),
     * "NON_POINTE" (créneau commencé sans pointage), ou null.
     */
    private String statutDuJour;
}
