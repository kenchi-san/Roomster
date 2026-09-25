package com.cesarhotel.roomster.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Trace d'une correction manuelle de pointage : qui a corrigé, quand, pourquoi,
 * et les valeurs avant / après (la correction écrase le pointage, l'ancienne valeur n'existe plus qu'ici).
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "correction_pointage")
public class CorrectionPointage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pointage_id", nullable = false)
    private Pointage pointage;

    /** Email du manager qui a fait la correction. */
    @Column(nullable = false)
    private String auteur;

    @Column(nullable = false)
    private LocalDateTime date;

    private LocalDateTime ancienneEntree;
    private LocalDateTime ancienneSortie;
    private LocalDateTime nouvelleEntree;
    private LocalDateTime nouvelleSortie;

    @Column(nullable = false)
    private String commentaire;

    public CorrectionPointage(Pointage pointage, String auteur, LocalDateTime nouvelleEntree,
                              LocalDateTime nouvelleSortie, String commentaire) {
        this.pointage = pointage;
        this.auteur = auteur;
        this.date = LocalDateTime.now();
        this.ancienneEntree = pointage.getEntree();
        this.ancienneSortie = pointage.getSortie();
        this.nouvelleEntree = nouvelleEntree;
        this.nouvelleSortie = nouvelleSortie;
        this.commentaire = commentaire;
    }
}
