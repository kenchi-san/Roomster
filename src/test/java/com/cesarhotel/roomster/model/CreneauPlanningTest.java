package com.cesarhotel.roomster.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreneauPlanningTest {

    private static final LocalDate LUNDI = LocalDate.of(2026, 9, 21);

    private CreneauPlanning creneau(LocalDate jour, String debut, String fin) {
        return new CreneauPlanning(new Employe(), jour, LocalTime.parse(debut), LocalTime.parse(fin), null);
    }

    @Test
    void creneauDeJournee() {
        CreneauPlanning c = creneau(LUNDI, "08:00", "16:00");
        assertEquals(Duration.ofHours(8), c.getDuree());
        assertEquals("08:00 → 16:00", c.getHoraires());
    }

    @Test
    void creneauDeNuitFinitLeLendemain() {
        CreneauPlanning c = creneau(LUNDI, "22:00", "06:00");
        assertEquals(LocalDateTime.parse("2026-09-22T06:00"), c.getFinComplete());
        assertEquals(Duration.ofHours(8), c.getDuree());
        assertEquals("22:00 → 06:00 (lendemain)", c.getHoraires());
    }

    @Test
    void jusquaMinuit() {
        assertEquals(Duration.ofHours(7), creneau(LUNDI, "17:00", "00:00").getDuree());
    }

    @Test
    void chevauchementLeMemeJour() {
        assertTrue(creneau(LUNDI, "08:00", "16:00").chevauche(creneau(LUNDI, "15:00", "20:00")));
    }

    @Test
    void creneauxQuiSeSuiventNeSeChevauchentPas() {
        // 08:00 → 14:00 puis 14:00 → 20:00 : se touchent sans se chevaucher (coupure possible)
        assertFalse(creneau(LUNDI, "08:00", "14:00").chevauche(creneau(LUNDI, "14:00", "20:00")));
    }

    @Test
    void nuitQuiDebordeSurLeLendemainMatin() {
        // lundi 22:00 → mardi 06:00 chevauche mardi 05:00 → 13:00
        assertTrue(creneau(LUNDI, "22:00", "06:00").chevauche(creneau(LUNDI.plusDays(1), "05:00", "13:00")));
    }

    @Test
    void enPointagePrevu() {
        Pointage p = creneau(LUNDI, "22:00", "06:00").enPointagePrevu();
        assertEquals(LocalDateTime.parse("2026-09-21T22:00"), p.getEntree());
        assertEquals(LocalDateTime.parse("2026-09-22T06:00"), p.getSortie());
    }
}
