package com.cesarhotel.roomster.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "compteur_employe",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employe_id", "annee"})
)
public class CompteurEmploye {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @Column(nullable = false)
    private int annee;

    /**
     * Solde de congés payés en jours (les demi-journées existent).
     */
    @Column(nullable = false)
    private double soldeCongesPayes;

    @Column(nullable = false)
    private double soldeRtt;

    /**
     * Cumul d'heures supplémentaires non récupérées.
     */
    @Column(nullable = false)
    private Duration heuresSupCumulees = Duration.ZERO;

    // --- Méthodes métier ---

    public void debiterCongesPayes(double jours) {
        if (jours > soldeCongesPayes) {
            throw new IllegalArgumentException(
                    "Solde CP insuffisant : demandé %.1f, disponible %.1f"
                            .formatted(jours, soldeCongesPayes));
        }
        this.soldeCongesPayes -= jours;
    }

    public void crediterCongesPayes(double jours) {
        this.soldeCongesPayes += jours;
    }

    public void debiterRtt(double jours) {
        if (jours > soldeRtt) {
            throw new IllegalArgumentException(
                    "Solde RTT insuffisant : demandé %.1f, disponible %.1f"
                            .formatted(jours, soldeRtt));
        }
        this.soldeRtt -= jours;
    }

    public void crediterRtt(double jours) {
        this.soldeRtt += jours;
    }

    public void ajouterHeuresSup(Duration duree) {
        this.heuresSupCumulees = this.heuresSupCumulees.plus(duree);
    }
}
