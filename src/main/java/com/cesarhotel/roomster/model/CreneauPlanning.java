package com.cesarhotel.roomster.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Un créneau du planning : un employé travaille tel jour, de telle heure à telle heure.
 * Si l'heure de fin est avant l'heure de début (ex : 22:00 → 06:00), le créneau se termine le lendemain.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "creneau_planning", indexes = @Index(name = "idx_creneau_jour", columnList = "jour"))
public class CreneauPlanning {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    /** Le jour où le créneau commence. */
    @Column(nullable = false)
    private LocalDate jour;

    @Column(nullable = false)
    private LocalTime debut;

    @Column(nullable = false)
    private LocalTime fin;

    /** Facultative, ex : "Petit-déjeuner", "Formation". */
    private String note;

    public CreneauPlanning(Employe employe, LocalDate jour, LocalTime debut, LocalTime fin, String note) {
        this.employe = employe;
        this.jour = jour;
        this.debut = debut;
        this.fin = fin;
        this.note = note;
    }

    /** Date et heure de début, ex : lundi 21/09 22:00. */
    public LocalDateTime getDebutComplet() {
        return jour.atTime(debut);
    }

    /** Date et heure de fin : le lendemain si l'heure de fin est avant (ou égale à) l'heure de début. */
    public LocalDateTime getFinComplete() {
        if (fin.isAfter(debut)) {
            return jour.atTime(fin);
        }
        return jour.plusDays(1).atTime(fin);
    }

    /** Le créneau se termine-t-il le lendemain ? (ex : 22:00 → 06:00) */
    public boolean isDeNuit() {
        return !fin.isAfter(debut);
    }

    public Duration getDuree() {
        return Duration.between(getDebutComplet(), getFinComplete());
    }

    /** Ex : "08:00 → 16:00", "22:00 → 06:00 (lendemain)". */
    public String getHoraires() {
        String texte = debut.format(HEURE) + " → " + fin.format(HEURE);
        if (isDeNuit()) {
            texte += " (lendemain)";
        }
        return texte;
    }

    /** Les deux créneaux se chevauchent-ils dans le temps ? */
    public boolean chevauche(CreneauPlanning autre) {
        return getDebutComplet().isBefore(autre.getFinComplete())
                && autre.getDebutComplet().isBefore(getFinComplete());
    }

    /**
     * Le créneau vu comme un pointage prévu (non enregistré en base),
     * pour réutiliser les contrôles HCR (ControlesHCR) sur le planning.
     */
    public Pointage enPointagePrevu() {
        Pointage pointage = new Pointage();
        pointage.setEmploye(employe);
        pointage.setEntree(getDebutComplet());
        pointage.setSortie(getFinComplete());
        return pointage;
    }
}
