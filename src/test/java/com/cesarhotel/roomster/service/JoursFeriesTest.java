package com.cesarhotel.roomster.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoursFeriesTest {

    @Test
    void datesDePaques() {
        assertEquals(LocalDate.of(2025, 4, 20), JoursFeries.paques(2025));
        assertEquals(LocalDate.of(2026, 4, 5), JoursFeries.paques(2026));
        assertEquals(LocalDate.of(2027, 3, 28), JoursFeries.paques(2027));
    }

    @Test
    void feriesMobiles2026() {
        assertEquals("Lundi de Pâques", JoursFeries.nom(LocalDate.of(2026, 4, 6)));
        assertEquals("Ascension", JoursFeries.nom(LocalDate.of(2026, 5, 14)));
        assertEquals("Lundi de Pentecôte", JoursFeries.nom(LocalDate.of(2026, 5, 25)));
    }

    @Test
    void feriesFixes() {
        assertTrue(JoursFeries.estFerie(LocalDate.of(2026, 7, 14)));
        assertTrue(JoursFeries.estFerie(LocalDate.of(2026, 12, 25)));
        assertEquals(11, JoursFeries.liste(2026).size());
    }

    @Test
    void jourOrdinaire() {
        assertFalse(JoursFeries.estFerie(LocalDate.of(2026, 7, 15)));
        assertNull(JoursFeries.nom(LocalDate.of(2026, 7, 15)));
    }

    @Test
    void premierMai() {
        assertTrue(JoursFeries.estPremierMai(LocalDate.of(2026, 5, 1)));
        assertFalse(JoursFeries.estPremierMai(LocalDate.of(2026, 5, 8)));
    }
}
