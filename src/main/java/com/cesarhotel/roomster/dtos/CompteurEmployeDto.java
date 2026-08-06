package com.cesarhotel.roomster.dtos;

import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Poste;
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
}
