package com.cesarhotel.roomster.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemandeAbsenceTest {

    // Le 21/09/2026 est un lundi, le 27/09/2026 un dimanche
    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 21);
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 9, 27);

    private DemandeAbsence demande(TypeAbsence type, LocalDate debut, LocalDate fin) {
        return new DemandeAbsence(new Employe(), type, debut, fin);
    }

    @Test
    void congePayeEnJoursOuvrables() {
        // une semaine complète coûte 6 jours ouvrables (lundi → samedi), pas 7
        assertEquals(6, demande(TypeAbsence.CONGE_PAYE, LUNDI, DIMANCHE).nombreDeJours());
    }

    @Test
    void deuxSemainesDeConge() {
        // du lundi 21/09 au samedi 03/10 : 12 jours ouvrables
        assertEquals(12, demande(TypeAbsence.CONGE_PAYE, LUNDI, LocalDate.of(2026, 10, 3)).nombreDeJours());
    }

    @Test
    void jourFerieNonDecompte() {
        // lundi 13/07 → dimanche 19/07/2026 : le 14 juillet (mardi) n'est pas décompté → 5 jours
        assertEquals(5, demande(TypeAbsence.CONGE_PAYE, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19)).nombreDeJours());
    }

    @Test
    void rttEnJoursOuvrables() {
        assertEquals(6, demande(TypeAbsence.RTT, LUNDI, DIMANCHE).nombreDeJours());
    }

    @Test
    void dimancheSeulNeCompteRien() {
        assertEquals(0, demande(TypeAbsence.CONGE_PAYE, DIMANCHE, DIMANCHE).nombreDeJours());
    }

    @Test
    void maladieEnJoursCalendaires() {
        // la maladie ne débite pas de compteur : on affiche simplement la durée de l'arrêt
        assertEquals(7, demande(TypeAbsence.MALADIE, LUNDI, DIMANCHE).nombreDeJours());
    }
}
