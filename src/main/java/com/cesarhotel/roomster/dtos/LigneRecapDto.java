package com.cesarhotel.roomster.dtos;

import lombok.Data;

/**
 * Une ligne du récapitulatif mensuel (une par employé). Durées déjà mises en forme, ex : "151h40".
 */
@Data
public class LigneRecapDto {
    private Long employeId;
    private String nom;                     // ex : "Jean Dupont"
    private String total;
    private String totalNuit;
    private String heuresSup10;
    private String heuresSup20;
    private String heuresSup50;
    private String heuresComplementaires10;
    private String heuresComplementaires25;
    private String reposNuit;               // repos compensateur des semaines où l'employé est travailleur de nuit
    private String feriesTravailles;        // ex : "01/05 (payé double), 14/07"
    private int pointagesOuverts;           // non comptés dans le total : à corriger
}
