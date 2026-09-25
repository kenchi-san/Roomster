package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.model.DemandeAbsence;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.StatutDemande;
import com.cesarhotel.roomster.model.TypeAbsence;
import com.cesarhotel.roomster.repository.DemandeAbsenceRepository;
import com.cesarhotel.roomster.repository.EmployeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Demandes d'absence (F4).
 *
 * Choix v1 (voir la trame) :
 * - CP et RTT sont comptés en jours ouvrables (lundi → samedi, sans les jours fériés) ;
 * - le solde est vérifié à la soumission, mais débité seulement à la validation ;
 * - le compteur utilisé est celui de la période de référence (juin → mai) du premier jour d'absence ;
 * - un arrêt maladie peut tomber pendant un congé payé déjà commencé : les jours de CP concernés sont rendus.
 */
@Service
public class DemandeAbsenceService {

    private final DemandeAbsenceRepository demandeAbsenceRepository;
    private final EmployeRepository employeRepository;
    private final CompteurEmployeService compteurEmployeService;
    private final ParametresService parametresService;

    public DemandeAbsenceService(DemandeAbsenceRepository demandeAbsenceRepository, EmployeRepository employeRepository,
                                 CompteurEmployeService compteurEmployeService, ParametresService parametresService) {
        this.demandeAbsenceRepository = demandeAbsenceRepository;
        this.employeRepository = employeRepository;
        this.compteurEmployeService = compteurEmployeService;
        this.parametresService = parametresService;
    }

    // --- Lecture ---

    /** Les types proposés dans le formulaire du salarié : tous, sauf RTT si l'hôtel n'en accorde pas. */
    public List<TypeAbsence> getTypesProposes() {
        List<TypeAbsence> types = new ArrayList<>();
        for (TypeAbsence type : TypeAbsence.values()) {
            if (type != TypeAbsence.RTT || parametresService.rttActives()) {
                types.add(type);
            }
        }
        return types;
    }

    public List<DemandeAbsence> getMesDemandes(String email) {
        return demandeAbsenceRepository.findByEmployeOrderByDebutDesc(getEmploye(email));
    }

    public List<DemandeAbsence> getDemandesEnAttente() {
        return demandeAbsenceRepository.findByStatutOrderByDebut(StatutDemande.SOUMISE);
    }

    /** Les 20 dernières demandes déjà traitées (validées, refusées ou annulées). */
    public List<DemandeAbsence> getDemandesTraitees() {
        return demandeAbsenceRepository.findTop20ByStatutNotOrderByDateSoumissionDesc(StatutDemande.SOUMISE);
    }

    public long compterDemandesEnAttente() {
        return demandeAbsenceRepository.countByStatut(StatutDemande.SOUMISE);
    }

    /** Demandes validées qui couvrent ce jour : les personnes absentes ce jour-là. */
    public List<DemandeAbsence> getAbsences(LocalDate jour) {
        return demandeAbsenceRepository.findByStatutAndDebutLessThanEqualAndFinGreaterThanEqual(
                StatutDemande.VALIDEE, jour, jour);
    }

    /** Demandes en attente de validation qui touchent la période [du, au] (planning du manager). */
    public List<DemandeAbsence> getDemandesEnAttente(LocalDate du, LocalDate au) {
        return demandeAbsenceRepository.findByStatutAndDebutLessThanEqualAndFinGreaterThanEqual(
                StatutDemande.SOUMISE, au, du);
    }

    /** Demandes validées qui touchent la période [du, au] (planning). */
    public List<DemandeAbsence> getAbsences(LocalDate du, LocalDate au) {
        return demandeAbsenceRepository.findByStatutAndDebutLessThanEqualAndFinGreaterThanEqual(
                StatutDemande.VALIDEE, au, du);
    }

    // --- Côté employé ---

    public void soumettre(String email, TypeAbsence type, LocalDate debut, LocalDate fin) {
        Employe employe = getEmploye(email);

        if (type == null || debut == null || fin == null) {
            throw new ValidationException("Le type, la date de début et la date de fin sont obligatoires");
        }
        if (type == TypeAbsence.RTT && !parametresService.rttActives()) {
            throw new ValidationException("L'hôtel n'accorde pas de RTT");
        }
        // Seule la maladie peut être déclarée après coup
        if (type != TypeAbsence.MALADIE && debut.isBefore(LocalDate.now())) {
            throw new ValidationException("Une demande ne peut pas commencer dans le passé (sauf maladie)");
        }

        DemandeAbsence demande = new DemandeAbsence(employe, type, debut, fin);
        verifierRegles(demande);
        compteurEmployeService.verifierSolde(demande);
        demandeAbsenceRepository.save(demande);
    }

    /** L'employé annule une de ses demandes, en attente ou validée, tant qu'elle n'a pas commencé. */
    @Transactional
    public void annuler(String email, Long demandeId) {
        DemandeAbsence demande = getDemande(demandeId);

        // Un employé ne peut annuler que ses propres demandes
        if (!demande.getEmploye().getUser().getEmail().equals(email)) {
            throw new ValidationException("Cette demande ne vous appartient pas");
        }
        if (!demande.getDebut().isAfter(LocalDate.now())) {
            throw new ValidationException("Une absence déjà commencée ne peut plus être annulée");
        }

        boolean etaitValidee = demande.getStatut() == StatutDemande.VALIDEE;
        demande.annuler();
        if (etaitValidee) {
            compteurEmployeService.crediterPourDemande(demande, email);  // on rend les jours débités
        }
        demandeAbsenceRepository.save(demande);
    }

    // --- Côté manager ---

    @Transactional
    public void valider(Long demandeId, String emailManager) {
        DemandeAbsence demande = getDemande(demandeId);
        demande.valider(getEmploye(emailManager));
        compteurEmployeService.debiterPourDemande(demande, emailManager);  // refuse si le solde est insuffisant
        if (demande.getType() == TypeAbsence.MALADIE) {
            rendreCongesPendantMaladie(demande, emailManager);
        }
        demandeAbsenceRepository.save(demande);
        regulariserAcquisitionSiBesoin(demande, emailManager);
    }

    public void refuser(Long demandeId, String emailManager, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new ValidationException("Un motif est obligatoire pour refuser une demande");
        }
        DemandeAbsence demande = getDemande(demandeId);
        demande.refuser(getEmploye(emailManager), motif);
        demandeAbsenceRepository.save(demande);
    }

    /**
     * Le manager déclare un arrêt maladie, éventuellement après coup (justificatif géré hors application).
     * La demande est validée directement et ne débite aucun compteur.
     */
    @Transactional
    public void saisirMaladie(Long employeId, LocalDate debut, LocalDate fin, String emailManager) {
        Employe employe = employeRepository.findById(employeId)
                .orElseThrow(() -> new ValidationException("Employé introuvable"));
        if (debut == null || fin == null) {
            throw new ValidationException("Les dates de début et de fin sont obligatoires");
        }

        DemandeAbsence demande = new DemandeAbsence(employe, TypeAbsence.MALADIE, debut, fin);
        verifierRegles(demande);
        demande.valider(getEmploye(emailManager));
        rendreCongesPendantMaladie(demande, emailManager);
        demandeAbsenceRepository.save(demande);
        regulariserAcquisitionSiBesoin(demande, emailManager);
    }

    // --- Méthodes internes ---

    /**
     * Maladie et sans solde changent les CP acquis (2 j/mois au lieu de 2,5 ; 0 en sans solde).
     * Si l'absence tombe sur un mois déjà crédité (déclarée après coup), on régularise ce mois.
     * À appeler APRÈS avoir enregistré la demande validée.
     */
    private void regulariserAcquisitionSiBesoin(DemandeAbsence demande, String auteur) {
        if (demande.getType() == TypeAbsence.MALADIE || demande.getType() == TypeAbsence.SANS_SOLDE) {
            compteurEmployeService.regulariserAcquisitions(demande.getEmploye(), demande.getDebut(), demande.getFin(), auteur);
        }
    }

    /** Règles communes à toute nouvelle demande. */
    private void verifierRegles(DemandeAbsence demande) {
        if (!demande.getEmploye().isActif()) {
            throw new ValidationException("Un employé désactivé ne peut plus soumettre de demande");
        }
        if (demande.getFin().isBefore(demande.getDebut())) {
            throw new ValidationException("La date de fin doit être après la date de début");
        }
        if (demande.nombreDeJours() == 0) {
            throw new ValidationException("Cette période ne contient aucun jour ouvrable (ni le dimanche ni les jours fériés ne sont décomptés)");
        }

        // Pas de chevauchement avec une autre demande en attente ou validée du même employé
        List<DemandeAbsence> demandesEnCours = demandeAbsenceRepository.findByEmployeAndStatutIn(
                demande.getEmploye(), List.of(StatutDemande.SOUMISE, StatutDemande.VALIDEE));
        for (DemandeAbsence autre : demandesEnCours) {
            if (!autre.chevauche(demande.getDebut(), demande.getFin())) {
                continue;
            }
            if (maladiePendantConge(demande, autre)) {
                continue; // autorisé : les jours de CP seront rendus à la validation de l'arrêt
            }
            throw new ValidationException("Cette période chevauche une autre demande : "
                    + autre.getType().getLibelle() + " " + autre.getPeriode());
        }
    }

    /**
     * Jurisprudence : un salarié malade pendant ses congés payés peut les reporter.
     * Cas autorisé : un arrêt maladie qui chevauche un congé payé validé et déjà commencé
     * (un congé pas encore commencé peut simplement être annulé par l'employé).
     */
    private boolean maladiePendantConge(DemandeAbsence maladie, DemandeAbsence conge) {
        return maladie.getType() == TypeAbsence.MALADIE
                && conge.getType() == TypeAbsence.CONGE_PAYE
                && conge.getStatut() == StatutDemande.VALIDEE
                && !conge.getDebut().isAfter(LocalDate.now());
    }

    /** À la validation d'un arrêt maladie : rend les jours ouvrables de CP qui tombent pendant l'arrêt. */
    private void rendreCongesPendantMaladie(DemandeAbsence maladie, String auteur) {
        List<DemandeAbsence> congesValides = demandeAbsenceRepository.findByEmployeAndStatutIn(
                maladie.getEmploye(), List.of(StatutDemande.VALIDEE));

        for (DemandeAbsence conge : congesValides) {
            if (!maladiePendantConge(maladie, conge) || !conge.chevauche(maladie.getDebut(), maladie.getFin())) {
                continue;
            }
            // Jours communs aux deux périodes
            LocalDate debut = conge.getDebut().isAfter(maladie.getDebut()) ? conge.getDebut() : maladie.getDebut();
            LocalDate fin = conge.getFin().isBefore(maladie.getFin()) ? conge.getFin() : maladie.getFin();
            long jours = CalculConges.joursOuvrables(debut, fin);
            if (jours > 0) {
                compteurEmployeService.rendreCongesPendantMaladie(conge, jours, auteur);
            }
        }
    }

    private Employe getEmploye(String email) {
        return employeRepository.findByUserEmail(email)
                .orElseThrow(() -> new ValidationException("Aucune fiche employé associée à ce compte"));
    }

    private DemandeAbsence getDemande(Long demandeId) {
        return demandeAbsenceRepository.findById(demandeId)
                .orElseThrow(() -> new ValidationException("Demande introuvable"));
    }
}
