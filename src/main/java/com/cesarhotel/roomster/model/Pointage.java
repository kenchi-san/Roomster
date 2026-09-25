package com.cesarhotel.roomster.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pointage", indexes = @Index(name = "idx_pointage_entree", columnList = "entree DESC"))
public class Pointage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @Column(nullable = false)
    private LocalDateTime entree;

    /** Null tant que l'employé n'a pas dépointé. */
    private LocalDateTime sortie;

    /** Ex : "oubli de badge, corrigé par le manager". */
    private String commentaire;

    /**
     * Vrai si la sortie a été mise automatiquement (entrée + 12h) parce que l'employé n'a pas pointé sa sortie.
     * Alerte pour le manager, qui doit la valider avec un commentaire (ou corriger l'heure) : repasse alors à faux.
     * "default false" : la colonne peut être ajoutée à une table qui contient déjà des pointages.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean sortieAutomatique;
}
