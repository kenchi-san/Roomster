package com.cesarhotel.roomster.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Mises à jour automatiques.
 *
 * Toutes les 5 minutes : sortie obligatoire des pointages ouverts depuis plus de 12h (clôture à entrée + 12h).
 *
 * Chaque nuit à 1h, et au démarrage de l'application (rattrapage si le serveur était arrêté) :
 * 1. Sortie obligatoire des pointages ouverts depuis plus de 12h (avant de calculer quoi que ce soit).
 * 2. Ouverture de la période de référence (1er juin) : report des soldes de la période précédente.
 * 3. Acquisition des CP du mois précédent (2,5 j par mois, 2 j par mois de maladie, 0 en sans solde).
 * 4. Report au compteur des heures sup des semaines terminées depuis au moins 7 jours.
 *
 * Chaque étape peut être relancée sans risque : ce qui est déjà fait n'est jamais refait.
 * Les régularisations (absence déclarée après coup, pointage corrigé) se font, elles, au moment de l'action.
 */
@Component
public class TachesAutomatiques {

    /** Auteur des mouvements créés par ces tâches. */
    public static final String AUTEUR = "automatique";

    private static final Logger LOG = LoggerFactory.getLogger(TachesAutomatiques.class);

    private final CompteurEmployeService compteurEmployeService;
    private final HeuresService heuresService;
    private final PointageService pointageService;

    public TachesAutomatiques(CompteurEmployeService compteurEmployeService, HeuresService heuresService,
                              PointageService pointageService) {
        this.compteurEmployeService = compteurEmployeService;
        this.heuresService = heuresService;
        this.pointageService = pointageService;
    }

    @Scheduled(cron = "0 */5 * * * *")              // toutes les 5 minutes
    public void fermerPointagesDepasses() {
        int fermes = pointageService.fermerPointagesDepasses();
        if (fermes > 0) {
            LOG.info("Sortie automatique (12h maximum) : {} pointage(s) clôturé(s), à valider par le manager", fermes);
        }
    }

    @Scheduled(cron = "0 0 1 * * *")                // chaque nuit à 1h00
    @EventListener(ApplicationReadyEvent.class)     // et au démarrage
    public void executer() {
        int pointagesFermes = pointageService.fermerPointagesDepasses();
        int periodesOuvertes = compteurEmployeService.ouvrirPeriode(AUTEUR);
        int acquisitions = compteurEmployeService.acquerirConges(LocalDate.now().minusMonths(1), AUTEUR);
        int semaines = heuresService.reporterHeuresSupAutomatiquement(AUTEUR);
        LOG.info("Tâches automatiques : {} sortie(s) automatique(s), {} compteur(s) ouvert(s), {} acquisition(s) de CP, "
                        + "{} semaine(s) d'heures sup reportée(s)",
                pointagesFermes, periodesOuvertes, acquisitions, semaines);
    }
}
