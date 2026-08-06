package com.cesarhotel.roomster.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@Table(name = "demande_absence")
public class DemandeAbsence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeAbsence type;

    @Column(nullable = false)
    private LocalDate debut;

    /** Dernier jour d'absence, inclus. */
    @Column(nullable = false)
    private LocalDate fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDemande statut = StatutDemande.SOUMISE;

    @Column(nullable = false)
    private LocalDateTime dateSoumission;

    private LocalDateTime dateDecision;

    /** Le manager qui a validé ou refusé la demande. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validateur_id")
    private Employe validateur;

    private String motifRefus;

    public void valider(Employe validateur) {
        exigerStatut(StatutDemande.SOUMISE, "valider");
        this.statut = StatutDemande.VALIDEE;
        this.validateur = validateur;
        this.dateDecision = LocalDateTime.now();
    }

    public void refuser(Employe validateur, String motif) {
        exigerStatut(StatutDemande.SOUMISE, "refuser");
        this.statut = StatutDemande.REFUSEE;
        this.validateur = validateur;
        this.motifRefus = motif;
        this.dateDecision = LocalDateTime.now();
    }

    public void annuler() {
        if (statut != StatutDemande.SOUMISE && statut != StatutDemande.VALIDEE) {
            throw new IllegalStateException(
                    "Impossible d'annuler une demande au statut " + statut);
        }
        this.statut = StatutDemande.ANNULEE;
    }

    private void exigerStatut(StatutDemande attendu, String action) {
        if (statut != attendu) {
            throw new IllegalStateException(
                    "Impossible de %s une demande au statut %s".formatted(action, statut));
        }
    }

    // --- Méthodes métier ---

    /** Nombre de jours calendaires couverts par la demande (bornes incluses). */
    public long nombreDeJours() {
        return ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    /** La demande chevauche-t-elle la période donnée ? */
    public boolean chevauche(LocalDate autreDebut, LocalDate autreFin) {
        return !debut.isAfter(autreFin) && !fin.isBefore(autreDebut);
    }

    @Override
    public String toString() {
        return "DemandeAbsence{id=%d, type=%s, du %s au %s, statut=%s}"
                .formatted(id, type, debut, fin, statut);
    }
}
