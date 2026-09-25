package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.Pointage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculHeuresTest {

    /** Crée un pointage à partir de dates au format "2026-09-21T08:00". sortie = null pour un pointage ouvert. */
    private Pointage pointage(String entree, String sortie) {
        Pointage pointage = new Pointage();
        pointage.setEntree(LocalDateTime.parse(entree));
        pointage.setSortie(sortie == null ? null : LocalDateTime.parse(sortie));
        return pointage;
    }

    // --- Durée et total ---

    @Test
    void journeeSimple() {
        Pointage p = pointage("2026-09-21T08:00", "2026-09-21T16:30");
        assertEquals(Duration.ofMinutes(8 * 60 + 30), CalculHeures.duree(p));
    }

    @Test
    void pointageOuvertNeComptePas() {
        Pointage p = pointage("2026-09-21T08:00", null);
        assertEquals(Duration.ZERO, CalculHeures.duree(p));
        assertEquals(Duration.ZERO, CalculHeures.totalNuit(List.of(p)));
    }

    @Test
    void coupureMidiEtSoir() {
        List<Pointage> pointages = List.of(
                pointage("2026-09-21T10:00", "2026-09-21T14:00"),   // 4h
                pointage("2026-09-21T18:00", "2026-09-21T22:30"));  // 4h30
        assertEquals(Duration.ofMinutes(8 * 60 + 30), CalculHeures.totalTravaille(pointages));
    }

    @Test
    void semaineVide() {
        assertEquals(Duration.ZERO, CalculHeures.totalTravaille(List.of()));
        assertEquals(Duration.ZERO, CalculHeures.totalNuit(List.of()));
    }

    // --- Heures de nuit ---

    @Test
    void journeeSansHeuresDeNuit() {
        assertEquals(Duration.ZERO, CalculHeures.heuresDeNuit(
                LocalDateTime.parse("2026-09-21T08:00"), LocalDateTime.parse("2026-09-21T16:00")));
    }

    @Test
    void exempleDeLaTrame18hA1h() {
        // 18h → 1h contient 3h de nuit (22h → 1h)
        assertEquals(Duration.ofHours(3), CalculHeures.heuresDeNuit(
                LocalDateTime.parse("2026-09-21T18:00"), LocalDateTime.parse("2026-09-22T01:00")));
    }

    @Test
    void nuitCompleteAChevalSurMinuit() {
        // 22h → 6h : tout est de nuit
        assertEquals(Duration.ofHours(8), CalculHeures.heuresDeNuit(
                LocalDateTime.parse("2026-09-21T22:00"), LocalDateTime.parse("2026-09-22T06:00")));
    }

    @Test
    void debutTresTot() {
        // 5h → 13h : 2h de nuit (5h → 7h)
        assertEquals(Duration.ofHours(2), CalculHeures.heuresDeNuit(
                LocalDateTime.parse("2026-09-21T05:00"), LocalDateTime.parse("2026-09-21T13:00")));
    }

    @Test
    void longueSequenceSurDeuxNuits() {
        // 20h lundi → 8h mercredi : nuit lundi→mardi (9h) + nuit mardi→mercredi (9h)
        assertEquals(Duration.ofHours(18), CalculHeures.heuresDeNuit(
                LocalDateTime.parse("2026-09-21T20:00"), LocalDateTime.parse("2026-09-23T08:00")));
    }

    // --- Semaine coupée à minuit ---

    @Test
    void serviceDuDimancheAuLundiCoupeAMinuit() {
        // dimanche 20/09 22h → lundi 21/09 6h : 2h dans la semaine du 14/09, 6h dans celle du 21/09
        List<Pointage> pointages = List.of(pointage("2026-09-20T22:00", "2026-09-21T06:00"));
        LocalDateTime lundi14 = LocalDateTime.parse("2026-09-14T00:00");
        LocalDateTime lundi21 = LocalDateTime.parse("2026-09-21T00:00");
        LocalDateTime lundi28 = LocalDateTime.parse("2026-09-28T00:00");

        assertEquals(Duration.ofHours(2), CalculHeures.totalEntre(pointages, lundi14, lundi21));
        assertEquals(Duration.ofHours(6), CalculHeures.totalEntre(pointages, lundi21, lundi28));
        assertEquals(Duration.ofHours(6), CalculHeures.totalNuitEntre(pointages, lundi21, lundi28));
    }

    @Test
    void plageDeNuitDesMineurs() {
        // 18h → 0h30 : 1h entre 23h30 et 6h
        assertEquals(Duration.ofHours(1), CalculHeures.heuresDansPlage(
                LocalDateTime.parse("2026-09-21T18:00"), LocalDateTime.parse("2026-09-22T00:30"),
                LocalTime.of(23, 30), LocalTime.of(6, 0)));
    }

    // --- Heures sup (au-delà de 35h, tranches HCR) ---

    private static final Duration H35 = Duration.ofHours(35);
    private static final Duration H39 = Duration.ofHours(39);
    private static final Duration H20 = Duration.ofHours(20);

    @Test
    void pasDHeuresSupSousLes35h() {
        // semaine incomplète (embauche le mercredi) : pas d'heures sup négatives
        DecompteHeures d = CalculHeures.decompter(Duration.ofHours(20), H35);
        assertEquals(Duration.ZERO, d.getHeuresSup());
        assertEquals(Duration.ZERO, d.getHeuresComplementaires());
    }

    @Test
    void contrat39hLes4HeuresDu36eAu39eSontDesHeuresSup() {
        // les heures sup commencent à la 36e heure, même si le contrat prévoit 39h
        DecompteHeures d = CalculHeures.decompter(H39, H39);
        assertEquals(Duration.ofHours(4), d.getHeuresSup10());
        assertEquals(Duration.ZERO, d.getHeuresSup20());
        // ... mais elles sont déjà payées dans le salaire : rien à ajouter au compteur
        assertEquals(Duration.ZERO, CalculHeures.heuresSupAuDelaDuContrat(H39, H39));
    }

    @Test
    void tranchesDeMajoration46h() {
        // 46h = 4h à +10 % (36e-39e) + 4h à +20 % (40e-43e) + 3h à +50 % (44e-46e)
        DecompteHeures d = CalculHeures.decompter(Duration.ofHours(46), H35);
        assertEquals(Duration.ofHours(4), d.getHeuresSup10());
        assertEquals(Duration.ofHours(4), d.getHeuresSup20());
        assertEquals(Duration.ofHours(3), d.getHeuresSup50());
    }

    @Test
    void heuresSupAuCompteurAuDelaDuContrat() {
        assertEquals(Duration.ofHours(2), CalculHeures.heuresSupAuDelaDuContrat(Duration.ofHours(41), H39));
        assertEquals(Duration.ofHours(3), CalculHeures.heuresSupAuDelaDuContrat(Duration.ofHours(38), H35));
    }

    // --- Temps partiel : heures complémentaires ---

    @Test
    void tempsPartielHeuresComplementaires() {
        // contrat 20h, 25h travaillées : 5h complémentaires, dont 2h (1/10 du contrat) à +10 % et 3h à +25 %
        DecompteHeures d = CalculHeures.decompter(Duration.ofHours(25), H20);
        assertEquals(Duration.ofHours(2), d.getHeuresComplementaires10());
        assertEquals(Duration.ofHours(3), d.getHeuresComplementaires25());
        assertEquals(Duration.ZERO, d.getHeuresSup());
        // payées, jamais ajoutées au compteur d'heures sup
        assertEquals(Duration.ZERO, CalculHeures.heuresSupAuDelaDuContrat(Duration.ofHours(25), H20));
    }

    @Test
    void tempsPartielAuDelaDe35h() {
        // contrat 20h, 38h travaillées : 15h complémentaires (20h → 35h) puis 3h sup (36e → 38e)
        DecompteHeures d = CalculHeures.decompter(Duration.ofHours(38), H20);
        assertEquals(Duration.ofHours(15), d.getHeuresComplementaires());
        assertEquals(Duration.ofHours(3), d.getHeuresSup10());
    }

    // --- Affichage ---

    @Test
    void formatage() {
        assertEquals("7h30", CalculHeures.formater(Duration.ofMinutes(450)));
        assertEquals("0h45", CalculHeures.formater(Duration.ofMinutes(45)));
        assertEquals("-2h00", CalculHeures.formater(Duration.ofHours(-2)));
    }
}
