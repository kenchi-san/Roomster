package com.cesarhotel.roomster.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Une ligne de l'historique d'un compteur : ce qui a changé, quand, par qui et pourquoi.
 * Les valeurs sont des variations : -5 jours de CP pour un congé validé, +5 s'il est annulé.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "mouvement_compteur")
public class MouvementCompteur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "compteur_id", nullable = false)
    private CompteurEmploye compteur;

    @Column(nullable = false)
    private LocalDateTime date;

    /** Ex : "Congé payé du 03/08 au 14/08 validé". */
    @Column(nullable = false)
    private String libelle;

    @Column(nullable = false)
    private double joursCongesPayes;

    @Column(nullable = false)
    private double joursRtt;

    @Column(nullable = false)
    private Duration heuresSup = Duration.ZERO;

    /** Lundi de la semaine reportée, uniquement pour un report d'heures sup (évite de la reporter deux fois). */
    private LocalDate semaine;

    /** 1er jour du mois, uniquement pour une acquisition mensuelle de CP (évite de l'enregistrer deux fois). */
    private LocalDate mois;

    /** Email de la personne à l'origine du mouvement. */
    @Column(nullable = false)
    private String auteur;

    public MouvementCompteur(CompteurEmploye compteur, String libelle, double joursCongesPayes,
                             double joursRtt, Duration heuresSup, LocalDate semaine, LocalDate mois, String auteur) {
        this.compteur = compteur;
        this.date = LocalDateTime.now();
        this.libelle = libelle;
        this.joursCongesPayes = joursCongesPayes;
        this.joursRtt = joursRtt;
        this.heuresSup = heuresSup;
        this.semaine = semaine;
        this.mois = mois;
        this.auteur = auteur;
    }
}
