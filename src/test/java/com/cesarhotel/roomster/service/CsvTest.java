package com.cesarhotel.roomster.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvTest {

    @Test
    void ligneSimple() {
        assertEquals("Dupont;Jean;7,50\r\n", Csv.ligne("Dupont", "Jean", "7,50"));
    }

    @Test
    void valeurVide() {
        assertEquals("a;;c\r\n", Csv.ligne("a", null, "c"));
    }

    @Test
    void valeurAvecPointVirgule() {
        assertEquals("\"Oubli; corrigé\"", Csv.echapper("Oubli; corrigé"));
    }

    @Test
    void valeurAvecGuillemets() {
        assertEquals("\"Dit \"\"urgent\"\"\"", Csv.echapper("Dit \"urgent\""));
    }

    @Test
    void heuresDecimales() {
        assertEquals("7,50", Csv.heures(Duration.ofMinutes(450)));
        assertEquals("0,25", Csv.heures(Duration.ofMinutes(15)));
        assertEquals("151,67", Csv.heures(Duration.ofMinutes(9100)));
    }

    @Test
    void nombre() {
        assertEquals("2,50", Csv.nombre(2.5));
    }
}
