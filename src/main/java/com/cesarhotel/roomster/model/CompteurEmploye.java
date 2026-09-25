package com.cesarhotel.roomster.model;

import com.cesarhotel.roomster.exception.ValidationException;
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

    /**
     * Période de référence des congés payés, désignée par l'année où elle commence :
     * annee = 2026 → du 1er juin 2026 au 31 mai 2027 (règle légale et convention HCR).
     * Voir CalculConges.periodeDe().
     */
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

    /**
     * Vrai quand les soldes de la période précédente ont été reportés sur ce compteur
     * (fait une seule fois, à l'ouverture de la période le 1er juin : voir CompteurEmployeService.ouvrirPeriode).
     * "default false" : la colonne peut être ajoutée à une table qui contient déjà des compteurs.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reportEffectue;

    public CompteurEmploye(Employe employe, int annee) {
        this.employe = employe;
        this.annee = annee;
    }

    // --- Méthodes métier ---

    /** Remet les trois soldes à zéro (ils viennent d'être reportés sur la période suivante). */
    public void solder() {
        this.soldeCongesPayes = 0;
        this.soldeRtt = 0;
        this.heuresSupCumulees = Duration.ZERO;
    }

    public void marquerReportEffectue() {
        this.reportEffectue = true;
    }

    /** Saisie manuelle des soldes (début de période, correction RH). */
    public void definirSoldes(double soldeCongesPayes, double soldeRtt) {
        if (soldeCongesPayes < 0 || soldeRtt < 0) {
            throw new ValidationException("Un solde ne peut pas être négatif");
        }
        this.soldeCongesPayes = soldeCongesPayes;
        this.soldeRtt = soldeRtt;
    }

    public void debiterCongesPayes(double jours) {
        if (jours > soldeCongesPayes) {
            throw new ValidationException(
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
            throw new ValidationException(
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
