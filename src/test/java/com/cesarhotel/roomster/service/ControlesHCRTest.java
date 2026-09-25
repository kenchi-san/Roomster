package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.Pointage;
import com.cesarhotel.roomster.model.Poste;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlesHCRTest {

    /** Pointage à partir de dates au format "2026-09-21T08:00". */
    private Pointage pointage(String entree, String sortie) {
        Pointage pointage = new Pointage();
        pointage.setEntree(LocalDateTime.parse(entree));
        pointage.setSortie(sortie == null ? null : LocalDateTime.parse(sortie));
        return pointage;
    }

    // --- Limites selon le poste ---

    @Test
    void dureesMaximalesParJour() {
        assertEquals(Duration.ofHours(11), ControlesHCR.dureeMaxJour(Poste.CUISINE, false));
        assertEquals(Duration.ofHours(12), ControlesHCR.dureeMaxJour(Poste.VEILLEUR_NUIT, false));
        assertEquals(Duration.ofMinutes(690), ControlesHCR.dureeMaxJour(Poste.RECEPTION, false));
        assertEquals(Duration.ofHours(8), ControlesHCR.dureeMaxJour(Poste.CUISINE, true));
    }

    @Test
    void totalParJourAvecCoupure() {
        List<Pointage> pointages = List.of(
                pointage("2026-09-21T09:00", "2026-09-21T15:00"),   // 6h
                pointage("2026-09-21T18:00", "2026-09-21T23:30"));  // 5h30
        assertEquals(Duration.ofMinutes(690), ControlesHCR.totalParJour(pointages).get(LocalDate.of(2026, 9, 21)));
    }

    // --- Repos entre deux journées ---

    @Test
    void reposTropCourt() {
        // sortie lundi 23h, reprise mardi 7h : 8h de repos seulement
        List<Pointage> pointages = List.of(
                pointage("2026-09-21T15:00", "2026-09-21T23:00"),
                pointage("2026-09-22T07:00", "2026-09-22T12:00"));
        Map<LocalDate, Duration> tropCourts = ControlesHCR.reposTropCourts(pointages, ControlesHCR.REPOS_QUOTIDIEN);
        assertEquals(Duration.ofHours(8), tropCourts.get(LocalDate.of(2026, 9, 22)));
    }

    @Test
    void coupureDansLaJourneeNestPasUnRepos() {
        // coupure de 3h le même jour, puis 12h de repos avant le lendemain : rien à signaler
        List<Pointage> pointages = List.of(
                pointage("2026-09-21T09:00", "2026-09-21T14:00"),
                pointage("2026-09-21T17:00", "2026-09-21T21:00"),
                pointage("2026-09-22T09:00", "2026-09-22T14:00"));
        assertTrue(ControlesHCR.reposTropCourts(pointages, ControlesHCR.REPOS_QUOTIDIEN).isEmpty());
    }

    // --- Travail de nuit ---

    @Test
    void travailleurDeNuitDeuxNuitsDe3h() {
        List<Pointage> pointages = List.of(
                pointage("2026-09-21T22:00", "2026-09-22T06:00"),
                pointage("2026-09-23T22:00", "2026-09-24T06:00"));
        assertTrue(ControlesHCR.estTravailleurDeNuit(pointages));
    }

    @Test
    void uneSeuleNuitNeSuffitPas() {
        List<Pointage> pointages = List.of(
                pointage("2026-09-21T22:00", "2026-09-22T06:00"),
                pointage("2026-09-23T18:00", "2026-09-23T23:00"));  // 1h de nuit seulement
        assertFalse(ControlesHCR.estTravailleurDeNuit(pointages));
    }

    @Test
    void reposCompensateurDe1Pourcent() {
        // 40h de nuit → 24 minutes de repos
        assertEquals(Duration.ofMinutes(24), ControlesHCR.reposCompensateurNuit(Duration.ofHours(40)));
    }

    // --- Mineurs ---

    @Test
    void mineurJusqua23h30Autorise() {
        List<Pointage> pointages = List.of(pointage("2026-09-21T17:00", "2026-09-21T23:30"));
        assertTrue(ControlesHCR.nuitsInterditesMineur(pointages).isEmpty());
    }

    @Test
    void mineurApres23h30Interdit() {
        List<Pointage> pointages = List.of(pointage("2026-09-21T18:00", "2026-09-22T00:30"));
        assertEquals(List.of(LocalDate.of(2026, 9, 21)), ControlesHCR.nuitsInterditesMineur(pointages));
    }
}
