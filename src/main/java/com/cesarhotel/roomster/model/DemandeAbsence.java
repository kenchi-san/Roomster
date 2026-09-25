package com.cesarhotel.roomster.model;

import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.service.CalculConges;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Entity
@Getter
@NoArgsConstructor
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

    public DemandeAbsence(Employe employe, TypeAbsence type, LocalDate debut, LocalDate fin) {
        this.employe = employe;
        this.type = type;
        this.debut = debut;
        this.fin = fin;
        this.dateSoumission = LocalDateTime.now();
    }

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
            throw new ValidationException(
                    "Impossible d'annuler une demande " + statut.getLibelle().toLowerCase());
        }
        this.statut = StatutDemande.ANNULEE;
    }

    private void exigerStatut(StatutDemande attendu, String action) {
        if (statut != attendu) {
            throw new ValidationException(
                    "Impossible de %s une demande %s".formatted(action, statut.getLibelle().toLowerCase()));
        }
    }

    // --- Méthodes métier ---

    /**
     * Nombre de jours décomptés pour cette demande (bornes incluses).
     *
     * - Congé payé et RTT : jours OUVRABLES (lundi → samedi, sans les jours fériés), comme le prévoit
     *   la convention HCR. Une semaine complète de congé (lundi → dimanche) coûte donc 6 jours, pas 7.
     * - Autres types (maladie, sans solde…) : jours calendaires, ils ne débitent aucun compteur.
     */
    public long nombreDeJours() {
        if (type == TypeAbsence.CONGE_PAYE || type == TypeAbsence.RTT) {
            return CalculConges.joursOuvrables(debut, fin);
        }
        return ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    /** L'employé peut annuler une demande en attente ou validée, tant qu'elle n'a pas commencé. */
    public boolean isAnnulable() {
        boolean statutOk = statut == StatutDemande.SOUMISE || statut == StatutDemande.VALIDEE;
        return statutOk && debut.isAfter(LocalDate.now());
    }

    /** Ex : "du 03/08/2026 au 14/08/2026 (11 j ouvrables)" pour un CP, "(3 j)" pour une maladie. */
    public String getPeriode() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String unite = (type == TypeAbsence.CONGE_PAYE || type == TypeAbsence.RTT) ? " j ouvrables" : " j";
        return "du " + debut.format(format) + " au " + fin.format(format) + " (" + nombreDeJours() + unite + ")";
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
