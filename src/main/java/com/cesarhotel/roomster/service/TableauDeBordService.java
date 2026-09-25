package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.dtos.TableauDeBordDto;
import com.cesarhotel.roomster.model.CreneauPlanning;
import com.cesarhotel.roomster.model.DemandeAbsence;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Pointage;
import com.cesarhotel.roomster.repository.EmployeRepository;
import com.cesarhotel.roomster.repository.PointageRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Tableau de bord du manager (F6).
 *
 * - Aujourd'hui : présent / parti / absent / sans pointage, d'après les pointages et les absences ;
 * - Planning : qui est prévu aujourd'hui, et qui est prévu mais n'a pas pointé (aujourd'hui et hier).
 */
@Service
public class TableauDeBordService {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter NOM_DU_JOUR = DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.FRENCH);

    private final EmployeRepository employeRepository;
    private final PointageRepository pointageRepository;
    private final PointageService pointageService;
    private final DemandeAbsenceService demandeAbsenceService;
    private final HeuresService heuresService;
    private final PlanningService planningService;

    public TableauDeBordService(EmployeRepository employeRepository, PointageRepository pointageRepository,
                                PointageService pointageService, DemandeAbsenceService demandeAbsenceService,
                                HeuresService heuresService, PlanningService planningService) {
        this.employeRepository = employeRepository;
        this.pointageRepository = pointageRepository;
        this.pointageService = pointageService;
        this.demandeAbsenceService = demandeAbsenceService;
        this.heuresService = heuresService;
        this.planningService = planningService;
    }

    public TableauDeBordDto getTableauDeBord() {
        TableauDeBordDto tableau = new TableauDeBordDto();
        LocalDate aujourdhui = LocalDate.now();

        // --- Qui est là aujourd'hui ? ---
        List<Pointage> pointagesDuJour = getPointagesDuJour(aujourdhui);
        List<DemandeAbsence> absencesDuJour = demandeAbsenceService.getAbsences(aujourdhui);

        for (Employe employe : employeRepository.findByActif(true)) {
            String nom = employe.getUser().getPrenom() + " " + employe.getUser().getNom();
            Pointage ouvert = pointageRepository.findByEmployeIdAndSortieIsNull(employe.getId()).orElse(null);
            DemandeAbsence absence = trouverAbsence(absencesDuJour, employe);

            // Un pointage ouvert a toujours moins de 12h : au-delà, la sortie est mise automatiquement
            if (ouvert != null) {
                tableau.getPresents().add(nom + " (depuis " + ouvert.getEntree().format(HEURE) + ")");
            } else if (absence != null) {
                tableau.getAbsents().add(nom + " (" + absence.getType().getLibelle() + ")");
            } else if (aPointe(pointagesDuJour, employe)) {
                tableau.getPartis().add(nom);
            } else {
                tableau.getSansPointage().add(nom);
            }
        }

        // --- À traiter ---
        tableau.setDemandesEnAttente(demandeAbsenceService.getDemandesEnAttente());
        tableau.setSortiesAutomatiques(pointageService.getSortiesAutomatiques());

        // --- Planning : qui devait être là ? ---
        LocalDateTime maintenant = LocalDateTime.now();
        for (CreneauPlanning creneau : planningService.getCreneauxDuJour(aujourdhui)) {
            Employe employe = creneau.getEmploye();
            String nom = employe.getUser().getPrenom() + " " + employe.getUser().getNom();
            tableau.getPrevus().add(nom + " : " + creneau.getHoraires());

            // Créneau déjà commencé, sans pointage aujourd'hui et sans absence : retard ou absence imprévue
            boolean commence = creneau.getDebutComplet().isBefore(maintenant);
            if (commence && !aPointe(pointagesDuJour, employe) && trouverAbsence(absencesDuJour, employe) == null) {
                tableau.getPrevusNonPointes().add(nom + " — prévu à " + creneau.getDebut().format(HEURE) + ", pas de pointage");
            }
        }

        // Prévus hier (planning) mais sans pointage ni absence
        LocalDate hier = aujourdhui.minusDays(1);
        tableau.setHier(hier.format(NOM_DU_JOUR));
        List<Pointage> pointagesHier = getPointagesDuJour(hier);
        List<DemandeAbsence> absencesHier = demandeAbsenceService.getAbsences(hier);
        for (CreneauPlanning creneau : planningService.getCreneauxDuJour(hier)) {
            Employe employe = creneau.getEmploye();
            String texte = employe.getUser().getPrenom() + " " + employe.getUser().getNom() + " (" + creneau.getHoraires() + ")";
            if (!aPointe(pointagesHier, employe) && trouverAbsence(absencesHier, employe) == null
                    && !tableau.getPrevusHierSansPointage().contains(texte)) {
                tableau.getPrevusHierSansPointage().add(texte);
            }
        }

        // --- Conformité HCR : durées maximales, repos, nuit des mineurs, contingent annuel ---
        LocalDate lundi = aujourdhui.with(DayOfWeek.MONDAY);
        for (Employe employe : employeRepository.findByActif(true)) {
            String nom = employe.getUser().getPrenom() + " " + employe.getUser().getNom();

            for (String alerte : heuresService.getAlertes(employe, lundi.minusWeeks(1))) {
                tableau.getAlertesHCR().add(nom + " — " + alerte);
            }
            for (String alerte : heuresService.getAlertes(employe, lundi)) {
                tableau.getAlertesHCR().add(nom + " — " + alerte);
            }

            Duration heuresSupAnnee = heuresService.getHeuresSupAnnee(employe, aujourdhui.getYear());
            if (heuresSupAnnee.compareTo(ControlesHCR.CONTINGENT_ANNUEL) > 0) {
                tableau.getAlertesHCR().add(nom + " — " + CalculHeures.formater(heuresSupAnnee) + " d'heures sup en "
                        + aujourdhui.getYear() + " : contingent annuel de 360h dépassé");
            }
        }

        return tableau;
    }

    // --- Méthodes internes ---

    private List<Pointage> getPointagesDuJour(LocalDate jour) {
        return pointageRepository.findByEntreeBetween(jour.atStartOfDay(), jour.atTime(LocalTime.MAX));
    }

    private boolean aPointe(List<Pointage> pointages, Employe employe) {
        for (Pointage pointage : pointages) {
            if (pointage.getEmploye().getId().equals(employe.getId())) {
                return true;
            }
        }
        return false;
    }

    private DemandeAbsence trouverAbsence(List<DemandeAbsence> absences, Employe employe) {
        for (DemandeAbsence absence : absences) {
            if (absence.getEmploye().getId().equals(employe.getId())) {
                return absence;
            }
        }
        return null;
    }
}
