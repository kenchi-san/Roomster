package com.cesarhotel.roomster.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * Détail des heures d'une semaine à transmettre à la paie, par taux de majoration.
 * Calculé par CalculHeures.decompter().
 *
 * - Heures sup (au-delà de 35h, convention HCR) : +10 % de la 36e à la 39e heure,
 *   +20 % de la 40e à la 43e, +50 % à partir de la 44e.
 * - Heures complémentaires (temps partiel, entre le contrat et 35h, Code du travail) :
 *   +10 % jusqu'au dixième du contrat, +25 % au-delà.
 */
@Getter
@AllArgsConstructor
public class DecompteHeures {

    private final Duration heuresSup10;
    private final Duration heuresSup20;
    private final Duration heuresSup50;
    private final Duration heuresComplementaires10;
    private final Duration heuresComplementaires25;

    public static final DecompteHeures VIDE = new DecompteHeures(
            Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO);

    public Duration getHeuresSup() {
        return heuresSup10.plus(heuresSup20).plus(heuresSup50);
    }

    public Duration getHeuresComplementaires() {
        return heuresComplementaires10.plus(heuresComplementaires25);
    }

    /** Additionne deux décomptes (ex : les semaines d'un mois). */
    public DecompteHeures plus(DecompteHeures autre) {
        return new DecompteHeures(
                heuresSup10.plus(autre.heuresSup10),
                heuresSup20.plus(autre.heuresSup20),
                heuresSup50.plus(autre.heuresSup50),
                heuresComplementaires10.plus(autre.heuresComplementaires10),
                heuresComplementaires25.plus(autre.heuresComplementaires25));
    }
}
