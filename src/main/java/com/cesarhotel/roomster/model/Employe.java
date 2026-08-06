package com.cesarhotel.roomster.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "employe")
public class Employe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Poste poste;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeContrat typeContrat;

    @Column(nullable = false)
    private LocalDate dateEntree;

    private LocalDate dateSortie; // null si toujours en poste

    /**
     * Durée hebdomadaire prévue au contrat (ex : 35h, 39h, 24h pour un temps partiel).
     * Persistée par Hibernate en nanosecondes (BIGINT).
     */
    @Column(nullable = false)
    private Duration dureeHebdoContrat;

    @Column(nullable = false)
    private boolean actif = true;

}
