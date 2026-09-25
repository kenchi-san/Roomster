package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.Employe;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * Les chiffres bruts d'un employé pour un mois (durées non mises en forme).
 * Utilisé par l'écran "Récap mensuel" (affiché en "7h30") et par l'export comptable (en heures décimales "7,50"),
 * pour que les deux donnent exactement les mêmes chiffres.
 */
@Getter
@AllArgsConstructor
public class RecapEmploye {
    private final Employe employe;
    private final Duration total;
    private final Duration totalNuit;
    private final DecompteHeures decompte;     // heures sup et complémentaires par taux
    private final Duration reposNuit;
    private final String feriesTravailles;     // ex : "01/05 (payé double), 14/07"
    private final int pointagesOuverts;
}
