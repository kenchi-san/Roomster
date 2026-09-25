package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.DemandeAbsence;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.TypeAbsence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculCongesTest {

    private static final LocalDate JUILLET = LocalDate.of(2026, 7, 1);   // 31 jours
    private static final LocalDate SEPTEMBRE = LocalDate.of(2026, 9, 1); // 30 jours
    private static final LocalDate ENTREE_ANCIENNE = LocalDate.of(2020, 1, 1);

    private DemandeAbsence absence(TypeAbsence type, LocalDate debut, LocalDate fin) {
        return new DemandeAbsence(new Employe(), type, debut, fin);
    }

    // --- Période de référence (juin → mai) ---

    @Test
    void periodeCommenceLe1erJuin() {
        assertEquals(2025, CalculConges.periodeDe(LocalDate.of(2026, 5, 31)));
        assertEquals(2026, CalculConges.periodeDe(LocalDate.of(2026, 6, 1)));
        assertEquals(2026, CalculConges.periodeDe(LocalDate.of(2027, 3, 15)));
    }

    // --- Jours ouvrables ---

    @Test
    void semaineAvecUnJourFerie() {
        // lundi 13/07 → dimanche 19/07/2026 : 6 jours ouvrables moins le mardi 14 juillet = 5
        assertEquals(5, CalculConges.joursOuvrables(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19)));
    }

    @Test
    void semaineSansJourFerie() {
        assertEquals(6, CalculConges.joursOuvrables(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27)));
    }

    // --- Acquisition mensuelle ---

    @Test
    void moisCompletDeTravail() {
        assertEquals(2.5, CalculConges.joursAcquis(JUILLET, ENTREE_ANCIENNE, null, List.of()));
    }

    @Test
    void moisCompletDeMaladie() {
        // loi du 22 avril 2024 : 2 jours par mois d'arrêt maladie
        List<DemandeAbsence> absences = List.of(
                absence(TypeAbsence.MALADIE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        assertEquals(2.0, CalculConges.joursAcquis(SEPTEMBRE, ENTREE_ANCIENNE, null, absences));
    }

    @Test
    void demiMoisDeMaladie() {
        // 15 jours de maladie sur 30 : 15 × 2/30 + 15 × 2,5/30 = 1 + 1,25 = 2,25
        List<DemandeAbsence> absences = List.of(
                absence(TypeAbsence.MALADIE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15)));
        assertEquals(2.25, CalculConges.joursAcquis(SEPTEMBRE, ENTREE_ANCIENNE, null, absences));
    }

    @Test
    void congeSansSoldeNeDonneRien() {
        // 15 jours sans solde sur 30 : seuls les 15 autres jours comptent → 1,25
        List<DemandeAbsence> absences = List.of(
                absence(TypeAbsence.SANS_SOLDE, LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 30)));
        assertEquals(1.25, CalculConges.joursAcquis(SEPTEMBRE, ENTREE_ANCIENNE, null, absences));
    }

    @Test
    void congePayeCompteCommeDuTravail() {
        List<DemandeAbsence> absences = List.of(
                absence(TypeAbsence.CONGE_PAYE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        assertEquals(2.5, CalculConges.joursAcquis(SEPTEMBRE, ENTREE_ANCIENNE, null, absences));
    }

    @Test
    void arriveeEnCoursDeMois() {
        // arrivée le 16/09 : 15 jours sur 30 → 1,25
        assertEquals(1.25, CalculConges.joursAcquis(SEPTEMBRE, LocalDate.of(2026, 9, 16), null, List.of()));
    }

    @Test
    void departEnCoursDeMois() {
        // sortie le 15/09 inclus : 15 jours sur 30 → 1,25
        assertEquals(1.25, CalculConges.joursAcquis(SEPTEMBRE, ENTREE_ANCIENNE, LocalDate.of(2026, 9, 15), List.of()));
    }
}
