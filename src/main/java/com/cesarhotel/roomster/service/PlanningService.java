package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.dtos.CellulePlanningDto;
import com.cesarhotel.roomster.dtos.JourPlanningDto;
import com.cesarhotel.roomster.dtos.LignePlanningDto;
import com.cesarhotel.roomster.dtos.MonPlanningDto;
import com.cesarhotel.roomster.dtos.PlanningSemaineDto;
import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.model.CreneauPlanning;
import com.cesarhotel.roomster.model.DemandeAbsence;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Pointage;
import com.cesarhotel.roomster.model.TypeAbsence;
import com.cesarhotel.roomster.repository.CreneauPlanningRepository;
import com.cesarhotel.roomster.repository.EmployeRepository;
import com.cesarhotel.roomster.repository.PointageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import java.util.TreeMap;

/**
 * Planning prévisionnel.
 * - Le manager place des créneaux (employé, jour, début, fin) dans une grille semaine.
 * - Chaque salarié voit ses créneaux et, pour chaque jour, les collègues qui travaillent.
 * - Les règles HCR (durées maximales, repos, nuit des mineurs) sont contrôlées sur le planning,
 *   donc AVANT le travail : on réutilise ControlesHCR en convertissant les créneaux en pointages prévus.
 */
@Service
public class PlanningService {

    private static final DateTimeFormatter NOM_DU_JOUR = DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.FRENCH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CreneauPlanningRepository creneauPlanningRepository;
    private final EmployeRepository employeRepository;
    private final PointageRepository pointageRepository;
    private final DemandeAbsenceService demandeAbsenceService;
    private final HeuresService heuresService;

    public PlanningService(CreneauPlanningRepository creneauPlanningRepository, EmployeRepository employeRepository,
                           PointageRepository pointageRepository, DemandeAbsenceService demandeAbsenceService,
                           HeuresService heuresService) {
        this.creneauPlanningRepository = creneauPlanningRepository;
        this.employeRepository = employeRepository;
        this.pointageRepository = pointageRepository;
        this.demandeAbsenceService = demandeAbsenceService;
        this.heuresService = heuresService;
    }

    // --- Vue manager : la grille ---

    /**
     * Grille employés × jours, avec le maximum d'informations pour le manager :
     * créneaux, absences validées (par type), demandes en attente, conflits, statut de pointage du jour,
     * effectif prévu par jour, heures prévues comparées au contrat, alertes HCR.
     */
    public PlanningSemaineDto getPlanningSemaine(LocalDate jour) {
        LocalDate lundi = jour.with(DayOfWeek.MONDAY);
        LocalDate dimanche = lundi.plusDays(6);
        LocalDate aujourdhui = LocalDate.now();
        List<CreneauPlanning> creneaux = creneauPlanningRepository.findByJourBetweenOrderByJourAscDebutAsc(lundi, dimanche);
        List<DemandeAbsence> absences = demandeAbsenceService.getAbsences(lundi, dimanche);
        List<DemandeAbsence> enAttente = demandeAbsenceService.getDemandesEnAttente(lundi, dimanche);
        List<Pointage> pointagesDuJour = pointageRepository.findByEntreeBetween(
                aujourdhui.atStartOfDay(), aujourdhui.atTime(LocalTime.MAX));

        // En-têtes des colonnes
        List<LocalDate> dates = new ArrayList<>();
        List<String> libelles = new ArrayList<>();
        List<String> feries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = lundi.plusDays(i);
            dates.add(date);
            libelles.add(date.format(NOM_DU_JOUR));
            feries.add(JoursFeries.nom(date));
        }

        // Une ligne par employé actif
        List<LignePlanningDto> lignes = new ArrayList<>();
        for (Employe employe : employeRepository.findByActif(true)) {
            List<CellulePlanningDto> cellules = new ArrayList<>();
            List<CreneauPlanning> creneauxDeLaSemaine = new ArrayList<>();

            for (LocalDate date : dates) {
                List<CreneauPlanning> creneauxDuJour = new ArrayList<>();
                for (CreneauPlanning creneau : creneaux) {
                    if (creneau.getEmploye().getId().equals(employe.getId()) && creneau.getJour().equals(date)) {
                        creneauxDuJour.add(creneau);
                    }
                }
                creneauxDeLaSemaine.addAll(creneauxDuJour);

                CellulePlanningDto cellule = new CellulePlanningDto();
                cellule.setJour(date);
                cellule.setCreneaux(creneauxDuJour);
                cellule.setAbsence(trouverDemande(absences, employe, date));
                cellule.setDemandeEnAttente(trouverDemande(enAttente, employe, date));
                cellule.setConflit(cellule.getAbsence() != null && !creneauxDuJour.isEmpty());
                if (date.equals(aujourdhui)) {
                    cellule.setStatutDuJour(statutDuJour(employe, creneauxDuJour, pointagesDuJour));
                }
                cellules.add(cellule);
            }

            Duration prevu = totalPrevu(creneauxDeLaSemaine);
            Duration contrat = employe.getDureeHebdoContrat();
            String comparaison;
            if (prevu.compareTo(contrat) < 0) {
                comparaison = "SOUS";
            } else if (prevu.equals(contrat)) {
                comparaison = "EGAL";
            } else {
                comparaison = "AU_DESSUS";
            }

            LignePlanningDto ligne = new LignePlanningDto();
            ligne.setEmployeId(employe.getId());
            ligne.setNom(employe.getUser().getPrenom() + " " + employe.getUser().getNom());
            ligne.setPoste(employe.getPoste().name());
            ligne.setTotalPrevu(CalculHeures.formater(prevu));
            ligne.setContrat(CalculHeures.formater(contrat));
            ligne.setComparaisonContrat(comparaison);
            ligne.setCellules(cellules);
            ligne.setAlertes(getAlertesPlanning(employe, lundi));
            lignes.add(ligne);
        }

        // Effectif prévu de chaque jour (une colonne = un jour)
        List<Integer> effectifs = new ArrayList<>();
        List<String> heuresPrevues = new ArrayList<>();
        List<String> repartitionPostes = new ArrayList<>();
        List<Integer> nombreAbsents = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            int personnes = 0;
            int absentsDuJour = 0;
            Duration heures = Duration.ZERO;
            Map<String, Integer> parPoste = new TreeMap<>();
            for (LignePlanningDto ligne : lignes) {
                CellulePlanningDto cellule = ligne.getCellules().get(i);
                if (!cellule.getCreneaux().isEmpty()) {
                    personnes++;
                    heures = heures.plus(totalPrevu(cellule.getCreneaux()));
                    parPoste.put(ligne.getPoste(), parPoste.getOrDefault(ligne.getPoste(), 0) + 1);
                }
                if (cellule.getAbsence() != null) {
                    absentsDuJour++;
                }
            }
            List<String> postes = new ArrayList<>();
            for (Map.Entry<String, Integer> poste : parPoste.entrySet()) {
                postes.add(poste.getKey() + " " + poste.getValue());
            }
            effectifs.add(personnes);
            heuresPrevues.add(CalculHeures.formater(heures));
            repartitionPostes.add(String.join(" · ", postes));
            nombreAbsents.add(absentsDuJour);
        }

        PlanningSemaineDto planning = new PlanningSemaineDto();
        planning.setEffectifs(effectifs);
        planning.setHeuresPrevues(heuresPrevues);
        planning.setRepartitionPostes(repartitionPostes);
        planning.setAbsents(nombreAbsents);
        planning.setLundi(lundi);
        planning.setDimanche(dimanche);
        planning.setSemainePrecedente(lundi.minusWeeks(1));
        planning.setSemaineSuivante(lundi.plusWeeks(1));
        planning.setDates(dates);
        planning.setJours(libelles);
        planning.setFeries(feries);
        planning.setLignes(lignes);
        return planning;
    }

    // --- Vue salarié : mon planning et mes collègues ---

    public MonPlanningDto getMonPlanning(Employe moi, LocalDate jour) {
        LocalDate lundi = jour.with(DayOfWeek.MONDAY);
        LocalDate dimanche = lundi.plusDays(6);
        List<CreneauPlanning> creneaux = creneauPlanningRepository.findByJourBetweenOrderByJourAscDebutAsc(lundi, dimanche);
        List<DemandeAbsence> absences = demandeAbsenceService.getAbsences(lundi, dimanche);

        List<JourPlanningDto> jours = new ArrayList<>();
        List<CreneauPlanning> mesCreneauxDeLaSemaine = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = lundi.plusDays(i);
            List<String> mesCreneaux = new ArrayList<>();
            List<String> collegues = new ArrayList<>();

            for (CreneauPlanning creneau : creneaux) {
                if (!creneau.getJour().equals(date)) {
                    continue;
                }
                if (creneau.getEmploye().getId().equals(moi.getId())) {
                    mesCreneauxDeLaSemaine.add(creneau);
                    mesCreneaux.add(creneau.getHoraires() + (creneau.getNote() != null ? " — " + creneau.getNote() : ""));
                } else {
                    Employe collegue = creneau.getEmploye();
                    collegues.add(collegue.getUser().getPrenom() + " " + collegue.getUser().getNom()
                            + " (" + collegue.getPoste() + ") : " + creneau.getHoraires());
                }
            }

            // Collègues absents ce jour-là : seulement le type ("Congé payé", "Maladie", ou "Absent"), aucun autre détail
            List<String> colleguesAbsents = new ArrayList<>();
            for (DemandeAbsence absence : absences) {
                Employe collegue = absence.getEmploye();
                if (!collegue.getId().equals(moi.getId()) && absence.chevauche(date, date)) {
                    colleguesAbsents.add(collegue.getUser().getPrenom() + " " + collegue.getUser().getNom()
                            + " (" + libellePourLesCollegues(absence.getType()) + ")");
                }
            }

            jours.add(new JourPlanningDto(date.format(NOM_DU_JOUR), date.equals(LocalDate.now()), JoursFeries.nom(date),
                    absenceDuJour(absences, moi, date), mesCreneaux, collegues, colleguesAbsents));
        }

        MonPlanningDto planning = new MonPlanningDto();
        planning.setLundi(lundi);
        planning.setDimanche(dimanche);
        planning.setSemainePrecedente(lundi.minusWeeks(1));
        planning.setSemaineSuivante(lundi.plusWeeks(1));
        planning.setTotalPrevu(CalculHeures.formater(totalPrevu(mesCreneauxDeLaSemaine)));
        planning.setContrat(CalculHeures.formater(moi.getDureeHebdoContrat()));
        planning.setJours(jours);
        return planning;
    }

    /** Les créneaux d'un jour, pour le tableau de bord ("qui devrait être là"). */
    public List<CreneauPlanning> getCreneauxDuJour(LocalDate jour) {
        return creneauPlanningRepository.findByJourBetweenOrderByJourAscDebutAsc(jour, jour);
    }

    // --- Gestion par le manager ---

    public void ajouterCreneau(Long employeId, LocalDate jour, LocalTime debut, LocalTime fin, String note) {
        Employe employe = employeRepository.findById(employeId)
                .orElseThrow(() -> new ValidationException("Employé introuvable"));
        if (jour == null || debut == null || fin == null) {
            throw new ValidationException("Le jour, l'heure de début et l'heure de fin sont obligatoires");
        }
        if (note != null && note.isBlank()) {
            note = null;
        }

        CreneauPlanning creneau = new CreneauPlanning(employe, jour, debut, fin, note);
        verifierRegles(creneau);
        creneauPlanningRepository.save(creneau);
    }

    public void supprimerCreneau(Long creneauId) {
        if (!creneauPlanningRepository.existsById(creneauId)) {
            throw new ValidationException("Créneau introuvable");
        }
        creneauPlanningRepository.deleteById(creneauId);
    }

    /**
     * Recopie les créneaux de la semaine précédente sur la semaine du lundi donné (même jour de la semaine,
     * mêmes heures). Un créneau qui ne respecte pas les règles (chevauchement, absence, employé désactivé)
     * est ignoré.
     *
     * @return le nombre de créneaux copiés
     */
    @Transactional
    public int copierSemainePrecedente(LocalDate jour) {
        LocalDate lundi = jour.with(DayOfWeek.MONDAY);
        List<CreneauPlanning> precedents = creneauPlanningRepository.findByJourBetweenOrderByJourAscDebutAsc(
                lundi.minusWeeks(1), lundi.minusDays(1));

        int copies = 0;
        for (CreneauPlanning precedent : precedents) {
            CreneauPlanning copie = new CreneauPlanning(precedent.getEmploye(), precedent.getJour().plusWeeks(1),
                    precedent.getDebut(), precedent.getFin(), precedent.getNote());
            try {
                verifierRegles(copie);
                creneauPlanningRepository.save(copie);
                copies++;
            } catch (ValidationException e) {
                // créneau ignoré : déjà présent, absence, employé désactivé…
            }
        }
        return copies;
    }

    // --- Méthodes internes ---

    /** Règles d'un nouveau créneau. */
    private void verifierRegles(CreneauPlanning creneau) {
        Employe employe = creneau.getEmploye();
        String nom = employe.getUser().getPrenom() + " " + employe.getUser().getNom();

        if (!employe.isActif()) {
            throw new ValidationException("Un employé désactivé ne peut pas être planifié");
        }
        if (creneau.getDebut().equals(creneau.getFin())) {
            throw new ValidationException("L'heure de fin doit être différente de l'heure de début");
        }
        // 12h maximum, comme pour un pointage
        if (creneau.getDuree().compareTo(PointageService.DUREE_MAXIMALE) > 0) {
            throw new ValidationException("Un créneau ne peut pas dépasser 12h");
        }

        // Pas de chevauchement avec un autre créneau du même employé (on regarde la veille et le lendemain
        // à cause des créneaux de nuit qui passent minuit)
        List<CreneauPlanning> autres = creneauPlanningRepository.findByEmployeAndJourBetweenOrderByJourAscDebutAsc(
                employe, creneau.getJour().minusDays(1), creneau.getJour().plusDays(1));
        for (CreneauPlanning autre : autres) {
            if (autre.chevauche(creneau)) {
                throw new ValidationException("Ce créneau chevauche un autre créneau de " + nom + " : "
                        + autre.getHoraires() + " le " + autre.getJour().format(DATE));
            }
        }

        // Pas de créneau un jour d'absence validée (congé, maladie…)
        String absence = absenceDuJour(demandeAbsenceService.getAbsences(creneau.getJour(), creneau.getJour()),
                employe, creneau.getJour());
        if (absence != null) {
            throw new ValidationException("Absence validée pour " + nom + " le " + creneau.getJour().format(DATE) + " (" + absence + ")");
        }
    }

    /** Contrôles HCR sur le planning prévu d'un employé (mêmes règles et messages que sur les pointages). */
    private List<String> getAlertesPlanning(Employe employe, LocalDate lundi) {
        List<Pointage> prevus = new ArrayList<>();
        List<Pointage> prevusAvecVeille = new ArrayList<>();
        for (CreneauPlanning creneau : creneauPlanningRepository.findByEmployeAndJourBetweenOrderByJourAscDebutAsc(
                employe, lundi.minusDays(1), lundi.plusDays(6))) {
            prevusAvecVeille.add(creneau.enPointagePrevu());
            if (!creneau.getJour().isBefore(lundi)) {
                prevus.add(creneau.enPointagePrevu());
            }
        }
        Duration total = CalculHeures.totalEntre(prevusAvecVeille, lundi.atStartOfDay(), lundi.plusWeeks(1).atStartOfDay());
        return heuresService.getAlertes(employe, lundi, prevus, prevusAvecVeille, total);
    }

    private Duration totalPrevu(List<CreneauPlanning> creneaux) {
        Duration total = Duration.ZERO;
        for (CreneauPlanning creneau : creneaux) {
            total = total.plus(creneau.getDuree());
        }
        return total;
    }

    /** La demande de cet employé qui couvre ce jour, ou null. */
    private DemandeAbsence trouverDemande(List<DemandeAbsence> demandes, Employe employe, LocalDate jour) {
        for (DemandeAbsence demande : demandes) {
            if (demande.getEmploye().getId().equals(employe.getId()) && demande.chevauche(jour, jour)) {
                return demande;
            }
        }
        return null;
    }

    /** Le type de l'absence validée de cet employé ce jour-là (ex : "Congé payé"), ou null. */
    private String absenceDuJour(List<DemandeAbsence> absences, Employe employe, LocalDate jour) {
        DemandeAbsence absence = trouverDemande(absences, employe, jour);
        return absence != null ? absence.getType().getLibelle() : null;
    }

    /** Ce que les collègues voient : le congé payé et la maladie, sinon simplement "Absent". */
    private String libellePourLesCollegues(TypeAbsence type) {
        if (type == TypeAbsence.CONGE_PAYE || type == TypeAbsence.MALADIE) {
            return type.getLibelle();
        }
        return "Absent";
    }

    /**
     * Statut du jour d'un employé, d'après ses pointages d'aujourd'hui :
     * "PRESENT" (pointage ouvert), "POINTE" (a pointé, pointage fermé),
     * "NON_POINTE" (un créneau a commencé mais aucun pointage), ou null (rien à signaler).
     */
    private String statutDuJour(Employe employe, List<CreneauPlanning> creneauxDuJour, List<Pointage> pointagesDuJour) {
        boolean aPointe = false;
        for (Pointage pointage : pointagesDuJour) {
            if (pointage.getEmploye().getId().equals(employe.getId())) {
                if (pointage.getSortie() == null) {
                    return "PRESENT";
                }
                aPointe = true;
            }
        }
        if (aPointe) {
            return "POINTE";
        }
        for (CreneauPlanning creneau : creneauxDuJour) {
            if (creneau.getDebutComplet().isBefore(LocalDateTime.now())) {
                return "NON_POINTE";
            }
        }
        return null;
    }
}
