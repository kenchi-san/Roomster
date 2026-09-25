package com.cesarhotel.roomster.dtos;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Planning d'une semaine vu par un salarié : ses créneaux et qui travaille chaque jour.
 */
@Data
public class MonPlanningDto {
    private LocalDate lundi;
    private LocalDate dimanche;
    private LocalDate semainePrecedente;
    private LocalDate semaineSuivante;
    private String totalPrevu;              // ex : "35h00"
    private String contrat;
    private List<JourPlanningDto> jours;
}
