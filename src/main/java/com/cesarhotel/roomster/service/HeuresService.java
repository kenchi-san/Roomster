package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.dtos.JourDto;
import com.cesarhotel.roomster.dtos.LigneRecapDto;
import com.cesarhotel.roomster.dtos.RecapMoisDto;
import com.cesarhotel.roomster.dtos.SemaineDto;
import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Pointage;
import com.cesarhotel.roomster.model.Poste;
import com.cesarhotel.roomster.repository.EmployeRepository;
import com.cesarhotel.roomster.repository.PointageRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calcul des heures (F3) : vue semaine, récap mensuel, contrôles HCR, report des heures sup au compteur.
 * Les calculs eux-mêmes sont dans CalculHeures et ControlesHCR ; ici on va chercher les pointages
 * et on prépare l'affichage.
 *
 * Deux façons de rattacher un pointage :
 * - pour l'AFFICHAGE jour par jour, un pointage compte le jour de son entrée (un service de nuit = une ligne) ;
 * - pour les TOTAUX de la semaine, un pointage est coupé aux bornes de la semaine (lundi 0h → lundi suivant 0h) :
 *   un service dimanche 22h → lundi 6h compte 2h dans une semaine et 6h dans la suivante.
 */
@Service
public class HeuresService {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_ET_HEURE = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter NOM_DU_JOUR = DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.FRENCH);
    private static final DateTimeFormatter NOM_DU_MOIS = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final PointageRepository pointageRepository;
    private final EmployeRepository employeRepository;
    private final CompteurEmployeService compteurEmployeService;

    public HeuresService(PointageRepository pointageRepository, EmployeRepository employeRepository,
                         CompteurEmployeService compteurEmployeService) {
        this.pointageRepository = pointageRepository;
        this.employeRepository = employeRepository;
        this.compteurEmployeService = compteurEmployeService;
    }

    // --- Vue semaine ---

    /** Construit la vue semaine (lundi → dimanche) qui contient le jour donné. */
    public SemaineDto getSemaine(Employe employe, LocalDate jour) {
        LocalDate lundi = jour.with(DayOfWeek.MONDAY);
        LocalDate dimanche = lundi.plusDays(6);
        List<Pointage> pointages = getPointages(employe, lundi, dimanche);                    // pour l'affichage
        List<Pointage> pointagesAvecVeille = getPointages(employe, lundi.minusDays(1), dimanche); // pour les calculs

        // --- Une ligne par jour ---
        List<JourDto> jours = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = lundi.plusDays(i);
            List<Pointage> pointagesDuJour = new ArrayList<>();
            List<String> lignes = new ArrayList<>();

            for (Pointage pointage : pointages) {
                if (pointage.getEntree().toLocalDate().equals(date)) {
                    pointagesDuJour.add(pointage);
                    lignes.add(decrire(pointage, date));
                }
            }

            Duration totalJour = CalculHeures.totalTravaille(pointagesDuJour);
            jours.add(new JourDto(date.format(NOM_DU_JOUR), date.equals(LocalDate.now()), lignes,
                    CalculHeures.formater(totalJour), JoursFeries.nom(date)));
        }

        // --- Totaux de la semaine (coupés à minuit aux deux bornes) ---
        LocalDateTime debutSemaine = lundi.atStartOfDay();
        LocalDateTime finSemaine = lundi.plusWeeks(1).atStartOfDay();
        Duration total = CalculHeures.totalEntre(pointagesAvecVeille, debutSemaine, finSemaine);
        Duration totalNuit = CalculHeures.totalNuitEntre(pointagesAvecVeille, debutSemaine, finSemaine);
        Duration contrat = employe.getDureeHebdoContrat();
        DecompteHeures decompte = CalculHeures.decompter(total, contrat);
        Duration heuresSupAuCompteur = CalculHeures.heuresSupAuDelaDuContrat(total, contrat);
        boolean travailleurDeNuit = ControlesHCR.estTravailleurDeNuit(pointages);

        boolean semaineTerminee = dimanche.isBefore(LocalDate.now());
        boolean dejaReportees = compteurEmployeService.heuresSupDejaReportees(employe, lundi);

        SemaineDto semaine = new SemaineDto();
        semaine.setLundi(lundi);
        semaine.setDimanche(dimanche);
        semaine.setSemainePrecedente(lundi.minusWeeks(1));
        semaine.setSemaineSuivante(lundi.plusWeeks(1));
        semaine.setJours(jours);
        semaine.setTotal(CalculHeures.formater(total));
        semaine.setTotalNuit(CalculHeures.formater(totalNuit));
        semaine.setContrat(CalculHeures.formater(contrat));
        semaine.setTempsPartiel(contrat.compareTo(CalculHeures.DUREE_LEGALE) < 0);
        semaine.setHeuresSup10(CalculHeures.formater(decompte.getHeuresSup10()));
        semaine.setHeuresSup20(CalculHeures.formater(decompte.getHeuresSup20()));
        semaine.setHeuresSup50(CalculHeures.formater(decompte.getHeuresSup50()));
        semaine.setHeuresComplementaires10(CalculHeures.formater(decompte.getHeuresComplementaires10()));
        semaine.setHeuresComplementaires25(CalculHeures.formater(decompte.getHeuresComplementaires25()));
        semaine.setTravailleurDeNuit(travailleurDeNuit);
        semaine.setReposNuit(CalculHeures.formater(travailleurDeNuit
                ? ControlesHCR.reposCompensateurNuit(totalNuit) : Duration.ZERO));
        semaine.setAlertes(getAlertes(employe, lundi, pointages, pointagesAvecVeille, total));
        semaine.setInfosPaie(getFeriesTravailles(employe, pointages));
        semaine.setHeuresSupAuCompteur(CalculHeures.formater(heuresSupAuCompteur));
        semaine.setHeuresSupDejaReportees(dejaReportees);
        semaine.setHeuresSupReportables(semaineTerminee && !contientUnPointageOuvert(pointages)
                && heuresSupAuCompteur.isPositive() && !dejaReportees);
        return semaine;
    }

    /**
     * Alertes de conformité HCR d'un employé pour la semaine qui commence ce lundi.
     * Utilisé par la vue semaine et par le tableau de bord.
     */
    public List<String> getAlertes(Employe employe, LocalDate lundi) {
        LocalDate dimanche = lundi.plusDays(6);
        List<Pointage> pointages = getPointages(employe, lundi, dimanche);
        List<Pointage> pointagesAvecVeille = getPointages(employe, lundi.minusDays(1), dimanche);
        Duration total = CalculHeures.totalEntre(pointagesAvecVeille, lundi.atStartOfDay(), lundi.plusWeeks(1).atStartOfDay());
        return getAlertes(employe, lundi, pointages, pointagesAvecVeille, total);
    }

    // --- Récap mensuel ---

    /**
     * Récapitulatif d'un mois pour la paie.
     * - total, heures de nuit et jours fériés : chaque pointage compte dans le mois de sa date d'entrée ;
     * - heures sup, heures complémentaires et repos de nuit : ils se calculent à la semaine, on additionne donc
     *   ceux des semaines dont le lundi tombe dans le mois (une semaine à cheval compte dans le mois de son lundi).
     */
    public RecapMoisDto getRecapMois(LocalDate jour) {
        LocalDate premierJour = jour.withDayOfMonth(1);

        List<LigneRecapDto> lignes = new ArrayList<>();
        for (RecapEmploye r : calculerRecapMois(premierJour)) {
            LigneRecapDto ligne = new LigneRecapDto();
            ligne.setEmployeId(r.getEmploye().getId());
            ligne.setNom(r.getEmploye().getUser().getPrenom() + " " + r.getEmploye().getUser().getNom());
            ligne.setTotal(CalculHeures.formater(r.getTotal()));
            ligne.setTotalNuit(CalculHeures.formater(r.getTotalNuit()));
            ligne.setHeuresSup10(CalculHeures.formater(r.getDecompte().getHeuresSup10()));
            ligne.setHeuresSup20(CalculHeures.formater(r.getDecompte().getHeuresSup20()));
            ligne.setHeuresSup50(CalculHeures.formater(r.getDecompte().getHeuresSup50()));
            ligne.setHeuresComplementaires10(CalculHeures.formater(r.getDecompte().getHeuresComplementaires10()));
            ligne.setHeuresComplementaires25(CalculHeures.formater(r.getDecompte().getHeuresComplementaires25()));
            ligne.setReposNuit(CalculHeures.formater(r.getReposNuit()));
            ligne.setFeriesTravailles(r.getFeriesTravailles());
            ligne.setPointagesOuverts(r.getPointagesOuverts());
            lignes.add(ligne);
        }

        RecapMoisDto recap = new RecapMoisDto();
        recap.setLibelle(premierJour.format(NOM_DU_MOIS));
        recap.setMoisPrecedent(premierJour.minusMonths(1));
        recap.setMoisSuivant(premierJour.plusMonths(1));
        recap.setLignes(lignes);
        return recap;
    }

    /**
     * Les chiffres bruts du mois, un par employé (écran "Récap mensuel" et export comptable).
     * On ignore un employé désactivé qui n'a aucun pointage ce mois-là.
     */
    public List<RecapEmploye> calculerRecapMois(LocalDate jour) {
        LocalDate premierJour = jour.withDayOfMonth(1);
        LocalDate dernierJour = premierJour.plusMonths(1).minusDays(1);

        List<RecapEmploye> recap = new ArrayList<>();
        for (Employe employe : employeRepository.findAll()) {
            List<Pointage> pointages = getPointages(employe, premierJour, dernierJour);
            if (!employe.isActif() && pointages.isEmpty()) {
                continue; // employé parti, rien à payer ce mois-ci
            }

            // Semaines dont le lundi tombe dans le mois
            DecompteHeures decompte = DecompteHeures.VIDE;
            Duration reposNuit = Duration.ZERO;
            LocalDate lundi = premierJour.with(DayOfWeek.MONDAY);
            if (lundi.isBefore(premierJour)) {
                lundi = lundi.plusWeeks(1); // premier lundi du mois
            }
            while (!lundi.isAfter(dernierJour)) {
                List<Pointage> pointagesSemaine = getPointages(employe, lundi.minusDays(1), lundi.plusDays(6));
                LocalDateTime debut = lundi.atStartOfDay();
                LocalDateTime fin = lundi.plusWeeks(1).atStartOfDay();

                Duration totalSemaine = CalculHeures.totalEntre(pointagesSemaine, debut, fin);
                decompte = decompte.plus(CalculHeures.decompter(totalSemaine, employe.getDureeHebdoContrat()));
                if (ControlesHCR.estTravailleurDeNuit(getPointages(employe, lundi, lundi.plusDays(6)))) {
                    Duration nuitSemaine = CalculHeures.totalNuitEntre(pointagesSemaine, debut, fin);
                    reposNuit = reposNuit.plus(ControlesHCR.reposCompensateurNuit(nuitSemaine));
                }
                lundi = lundi.plusWeeks(1);
            }

            int ouverts = 0;
            for (Pointage pointage : pointages) {
                if (pointage.getSortie() == null) {
                    ouverts++;
                }
            }

            recap.add(new RecapEmploye(employe,
                    CalculHeures.totalTravaille(pointages),
                    CalculHeures.totalNuit(pointages),
                    decompte,
                    reposNuit,
                    listerFeriesTravailles(pointages),
                    ouverts));
        }
        return recap;
    }

    // --- Contingent annuel d'heures sup ---

    /** Heures sup (au-delà de 35h) de l'année civile : semaines dont le lundi tombe dans l'année. */
    public Duration getHeuresSupAnnee(Employe employe, int annee) {
        LocalDate premierLundi = LocalDate.of(annee, 1, 1).with(DayOfWeek.MONDAY);
        if (premierLundi.getYear() < annee) {
            premierLundi = premierLundi.plusWeeks(1);
        }
        LocalDate dernierJour = LocalDate.of(annee, 12, 31);

        // Une seule requête pour toute l'année (+ la veille du premier lundi et la dernière semaine entière)
        List<Pointage> pointages = getPointages(employe, premierLundi.minusDays(1), dernierJour.plusDays(7));

        Duration heuresSup = Duration.ZERO;
        LocalDate lundi = premierLundi;
        while (!lundi.isAfter(dernierJour)) {
            Duration totalSemaine = CalculHeures.totalEntre(pointages, lundi.atStartOfDay(), lundi.plusWeeks(1).atStartOfDay());
            heuresSup = heuresSup.plus(CalculHeures.decompter(totalSemaine, employe.getDureeHebdoContrat()).getHeuresSup());
            lundi = lundi.plusWeeks(1);
        }
        return heuresSup;
    }

    // --- Report des heures sup au compteur ---

    /**
     * Ajoute au compteur de l'employé les heures sup d'une semaine terminée :
     * seulement celles au-delà du contrat et de 35h (voir CalculHeures.heuresSupAuDelaDuContrat).
     */
    public void reporterHeuresSup(Employe employe, LocalDate jour, String auteur) {
        LocalDate lundi = jour.with(DayOfWeek.MONDAY);
        LocalDate dimanche = lundi.plusDays(6);
        if (!dimanche.isBefore(LocalDate.now())) {
            throw new ValidationException("La semaine n'est pas terminée");
        }
        if (contientUnPointageOuvert(getPointages(employe, lundi, dimanche))) {
            throw new ValidationException("La semaine contient un pointage sans sortie : corrigez-le d'abord");
        }

        List<Pointage> pointagesAvecVeille = getPointages(employe, lundi.minusDays(1), dimanche);
        Duration total = CalculHeures.totalEntre(pointagesAvecVeille, lundi.atStartOfDay(), lundi.plusWeeks(1).atStartOfDay());
        Duration heuresSup = CalculHeures.heuresSupAuDelaDuContrat(total, employe.getDureeHebdoContrat());
        if (heuresSup.isZero()) {
            throw new ValidationException("Aucune heure sup au-delà du contrat cette semaine");
        }
        compteurEmployeService.reporterHeuresSup(employe, lundi, heuresSup, auteur);
    }

    /** Une semaine est reportée automatiquement 7 jours après sa fin : le temps de corriger les pointages. */
    public static final int JOURS_AVANT_REPORT_AUTOMATIQUE = 7;

    /** Combien de semaines passées la tâche automatique regarde (rattrapage si le serveur était arrêté). */
    public static final int SEMAINES_RATTRAPAGE = 5;

    /**
     * Tâche automatique : reporte au compteur les heures sup des semaines terminées depuis au moins 7 jours,
     * pas encore reportées, sans pointage ouvert (une semaine avec un pointage ouvert attend sa correction).
     *
     * @return le nombre de semaines reportées
     */
    public int reporterHeuresSupAutomatiquement(String auteur) {
        LocalDate dernierLundiPossible = LocalDate.now().minusDays(JOURS_AVANT_REPORT_AUTOMATIQUE + 6L).with(DayOfWeek.MONDAY);
        LocalDate premierLundi = dernierLundiPossible.minusWeeks(SEMAINES_RATTRAPAGE - 1L);
        int reportees = 0;

        for (Employe employe : employeRepository.findByActif(true)) {
            LocalDate lundi = premierLundi;
            while (!lundi.isAfter(dernierLundiPossible)) {
                if (!compteurEmployeService.heuresSupDejaReportees(employe, lundi)) {
                    try {
                        reporterHeuresSup(employe, lundi, auteur);
                        reportees++;
                    } catch (ValidationException e) {
                        // pas d'heures sup, ou pointage ouvert à corriger d'abord : rien à faire
                    }
                }
                lundi = lundi.plusWeeks(1);
            }
        }
        return reportees;
    }

    /**
     * Après la correction d'un pointage : si sa semaine a déjà été reportée au compteur,
     * on recalcule ses heures sup et on enregistre la différence.
     */
    public void regulariserHeuresSup(Employe employe, LocalDate jour, String auteur) {
        LocalDate lundi = jour.with(DayOfWeek.MONDAY);
        if (!compteurEmployeService.heuresSupDejaReportees(employe, lundi)) {
            return;
        }
        List<Pointage> pointagesAvecVeille = getPointages(employe, lundi.minusDays(1), lundi.plusDays(6));
        Duration total = CalculHeures.totalEntre(pointagesAvecVeille, lundi.atStartOfDay(), lundi.plusWeeks(1).atStartOfDay());
        Duration heuresSup = CalculHeures.heuresSupAuDelaDuContrat(total, employe.getDureeHebdoContrat());
        compteurEmployeService.regulariserHeuresSup(employe, lundi, heuresSup, auteur);
    }

    // --- Méthodes internes ---

    /** Pointages dont l'entrée est entre le premier jour 0h et le dernier jour 23h59, triés. */
    private List<Pointage> getPointages(Employe employe, LocalDate du, LocalDate au) {
        return pointageRepository.findByEmployeIdAndEntreeBetweenOrderByEntree(
                employe.getId(), du.atStartOfDay(), au.atTime(LocalTime.MAX));
    }

    /**
     * Les contrôles ControlesHCR, traduits en phrases pour le manager.
     * Fonctionne aussi avec les créneaux du planning, convertis en pointages prévus (PlanningService).
     *
     * @param pointages           pointages dont l'entrée tombe dans la semaine
     * @param pointagesAvecVeille les mêmes plus ceux du dimanche précédent (pour le repos du lundi)
     * @param totalSemaine        total de la semaine, coupé à minuit
     */
    public List<String> getAlertes(Employe employe, LocalDate lundi, List<Pointage> pointages,
                                   List<Pointage> pointagesAvecVeille, Duration totalSemaine) {
        List<String> alertes = new ArrayList<>();
        boolean mineur = employe.estMineurLe(lundi);
        String qui = mineur ? "un salarié de moins de 18 ans" : decrirePoste(employe.getPoste());

        // Durée maximale par jour
        Duration maxJour = ControlesHCR.dureeMaxJour(employe.getPoste(), mineur);
        for (Map.Entry<LocalDate, Duration> jour : ControlesHCR.totalParJour(pointages).entrySet()) {
            if (jour.getValue().compareTo(maxJour) > 0) {
                alertes.add(jour.getKey().format(NOM_DU_JOUR) + " : " + CalculHeures.formater(jour.getValue())
                        + " travaillées (maximum " + CalculHeures.formater(maxJour) + " par jour pour " + qui + ")");
            }
        }

        // Durée maximale par semaine
        Duration maxSemaine = ControlesHCR.dureeMaxSemaine(mineur);
        if (totalSemaine.compareTo(maxSemaine) > 0) {
            alertes.add("Semaine : " + CalculHeures.formater(totalSemaine) + " travaillées (maximum "
                    + CalculHeures.formater(maxSemaine) + " pour " + qui + ")");
        }

        // Repos entre deux journées (on regarde aussi la veille du lundi)
        Duration reposMinimum = ControlesHCR.reposMinimum(mineur);
        for (Map.Entry<LocalDate, Duration> jour : ControlesHCR.reposTropCourts(pointagesAvecVeille, reposMinimum).entrySet()) {
            if (!jour.getKey().isBefore(lundi)) {
                alertes.add(jour.getKey().format(NOM_DU_JOUR) + " : seulement " + CalculHeures.formater(jour.getValue())
                        + " de repos depuis la journée précédente (minimum " + CalculHeures.formater(reposMinimum) + ")");
            }
        }

        // Travail de nuit d'un mineur
        if (mineur) {
            for (LocalDate jour : ControlesHCR.nuitsInterditesMineur(pointages)) {
                alertes.add(jour.format(NOM_DU_JOUR) + " : travail entre 23h30 et 6h, interdit pour un salarié de moins de 18 ans");
            }
        }
        return alertes;
    }

    /** Jours fériés travaillés : ce que la paie doit en faire. */
    private List<String> getFeriesTravailles(Employe employe, List<Pointage> pointages) {
        List<String> infos = new ArrayList<>();
        List<LocalDate> dejaVus = new ArrayList<>();
        for (Pointage pointage : pointages) {
            LocalDate jour = pointage.getEntree().toLocalDate();
            String ferie = JoursFeries.nom(jour);
            if (ferie == null || dejaVus.contains(jour)) {
                continue;
            }
            dejaVus.add(jour);

            String debut = ferie + " (" + jour.format(JOUR_MOIS) + ") travaillé : ";
            if (JoursFeries.estPremierMai(jour)) {
                infos.add(debut + "heures payées double.");
            } else if (employe.aUnAnDAncienneteLe(jour)) {
                infos.add(debut + "jour férié garanti HCR, à compenser (repos ou indemnité).");
            } else {
                infos.add(debut + "pas de compensation garantie (moins d'un an d'ancienneté).");
            }
        }
        return infos;
    }

    /** Ex : "01/05 (payé double), 14/07", ou "" s'il n'y en a pas. */
    private String listerFeriesTravailles(List<Pointage> pointages) {
        List<String> jours = new ArrayList<>();
        for (Pointage pointage : pointages) {
            LocalDate jour = pointage.getEntree().toLocalDate();
            if (!JoursFeries.estFerie(jour)) {
                continue;
            }
            String texte = jour.format(JOUR_MOIS) + (JoursFeries.estPremierMai(jour) ? " (payé double)" : "");
            if (!jours.contains(texte)) {
                jours.add(texte);
            }
        }
        return String.join(", ", jours);
    }

    private String decrirePoste(Poste poste) {
        if (poste == Poste.CUISINE) {
            return "un cuisinier";
        }
        if (poste == Poste.VEILLEUR_NUIT) {
            return "un veilleur de nuit";
        }
        return "ce poste";
    }

    private boolean contientUnPointageOuvert(List<Pointage> pointages) {
        for (Pointage pointage : pointages) {
            if (pointage.getSortie() == null) {
                return true;
            }
        }
        return false;
    }

    /** Ex : "08:00 → 12:00 (4h00)", "22:00 → 24/07 06:00 (8h00) — Astreinte de nuit". */
    private String decrire(Pointage pointage, LocalDate jour) {
        String ligne = pointage.getEntree().format(HEURE) + " → ";

        if (pointage.getSortie() == null) {
            // Jamais plus de 12h : au-delà, la sortie est mise automatiquement (PointageService.DUREE_MAXIMALE)
            ligne += "en cours";
        } else {
            // Sortie le lendemain (service de nuit) : on précise la date
            boolean memeJour = pointage.getSortie().toLocalDate().equals(jour);
            ligne += pointage.getSortie().format(memeJour ? HEURE : DATE_ET_HEURE);
            ligne += " (" + CalculHeures.formater(CalculHeures.duree(pointage)) + ")";
        }

        if (pointage.getCommentaire() != null) {
            ligne += " — " + pointage.getCommentaire();
        }
        return ligne;
    }
}
