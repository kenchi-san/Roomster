package com.cesarhotel.roomster.dtos;

import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Poste;
import com.cesarhotel.roomster.service.CalculHeures;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CompteurEmployeDto {

    private Long id;

    private String nom;
    private String prenom;
    private Poste poste;

    private int annee;

    private double soldeCongesPayes;

    private double soldeRtt;

    private Duration heuresSupCumulees = Duration.ZERO;

    // --- Année N-1 (null si aucun compteur n'existe pour l'année précédente) ---

    private Double soldeCongesPayesN1;

    private Double soldeRttN1;

    private Duration heuresSupCumuleesN1;

    // --- Affichage ---

    /** Ex : "juin 2026 → mai 2027" pour annee = 2026. */
    public String getPeriode() {
        return "juin " + annee + " → mai " + (annee + 1);
    }

    // "7h15" plutôt que "PT7H15M"

    public String getHeuresSupTexte() {
        return CalculHeures.formater(heuresSupCumulees);
    }

    public String getHeuresSupN1Texte() {
        return heuresSupCumuleesN1 == null ? "-" : CalculHeures.formater(heuresSupCumuleesN1);
    }
}
