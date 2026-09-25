package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.model.CorrectionPointage;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Pointage;
import com.cesarhotel.roomster.repository.CorrectionPointageRepository;
import com.cesarhotel.roomster.repository.EmployeRepository;
import com.cesarhotel.roomster.repository.PointageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PointageService {

    private static final int TAILLE_PAGE_HISTORIQUE = 10;

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Durée maximale d'un pointage : au-delà, l'employé est obligatoirement sorti (sortie automatique
     * à entrée + 12h, à valider par le manager). La correction manuelle ne peut pas dépasser non plus.
     */
    public static final Duration DUREE_MAXIMALE = Duration.ofHours(12);

    private final PointageRepository pointageRepository;
    private final EmployeRepository employeRepository;
    private final CorrectionPointageRepository correctionPointageRepository;
    private final HeuresService heuresService;

    public PointageService(PointageRepository pointageRepository, EmployeRepository employeRepository,
                           CorrectionPointageRepository correctionPointageRepository, HeuresService heuresService) {
        this.pointageRepository = pointageRepository;
        this.employeRepository = employeRepository;
        this.correctionPointageRepository = correctionPointageRepository;
        this.heuresService = heuresService;
    }

    public List<Employe> getEmployesActifs() {
        return employeRepository.findByActif(true);
    }

    public Optional<Employe> getEmploye(Long employeId) {
        return employeRepository.findById(employeId);
    }

    public Optional<Employe> getEmployeConnecte(String email) {
        return employeRepository.findByUserEmail(email);
    }

    /** Pour chaque employé qui a un pointage ouvert : l'heure de son entrée. */
    public Map<Long, LocalDateTime> getPointagesOuverts() {
        return pointageRepository.findBySortieIsNull().stream()
                .collect(Collectors.toMap(p -> p.getEmploye().getId(), Pointage::getEntree));
    }

    /** Pointages clôturés automatiquement (pas de pointage de sortie, 12h) que le manager n'a pas encore validés. */
    public List<Pointage> getSortiesAutomatiques() {
        return pointageRepository.findBySortieAutomatiqueTrueOrderByEntreeDesc();
    }

    /**
     * Sortie obligatoire après 12h : ferme tous les pointages encore ouverts depuis plus de 12h, à entrée + 12h.
     * Appelé toutes les 5 minutes (TachesAutomatiques) et quand l'employé badge à nouveau.
     *
     * @return le nombre de pointages fermés
     */
    public synchronized int fermerPointagesDepasses() {
        int fermes = 0;
        LocalDateTime maintenant = LocalDateTime.now();
        for (Pointage pointage : pointageRepository.findBySortieIsNull()) {
            if (sortieObligatoire(pointage).isBefore(maintenant)) {
                fermerAutomatiquement(pointage);
                fermes++;
            }
        }
        return fermes;
    }

    public Page<Pointage> getHistorique(int page) {
        return pointageRepository.findAll(
                PageRequest.of(page, TAILLE_PAGE_HISTORIQUE, Sort.by(Sort.Direction.DESC, "entree")));
    }

    public Page<Pointage> getHistoriquePersonnel(Long employeId, int page) {
        return pointageRepository.findByEmployeId(employeId,
                PageRequest.of(page, TAILLE_PAGE_HISTORIQUE, Sort.by(Sort.Direction.DESC, "entree")));
    }

    /**
     * Pas de pointage ouvert : on en crée un (entrée). Un pointage ouvert : on le ferme (sortie).
     * Si le pointage ouvert a plus de 12h (oubli de badge), il est d'abord clôturé automatiquement
     * à entrée + 12h, puis ce badge compte comme une nouvelle entrée.
     *
     * "synchronized" : deux clics simultanés sont traités l'un après l'autre, ce qui évite
     * de créer deux pointages ouverts pour le même employé (il n'y a pas de contrainte en base).
     */
    public synchronized Optional<Pointage> pointer(Long employeId) {
        Optional<Employe> employeOpt = employeRepository.findById(employeId);
        if (employeOpt.isEmpty()) {
            return Optional.empty();
        }
        Employe employe = employeOpt.get();
        if (!employe.isActif()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Un employé désactivé ne peut plus pointer");
        }

        Optional<Pointage> ouvert = pointageRepository.findByEmployeIdAndSortieIsNull(employeId);
        LocalDateTime maintenant = LocalDateTime.now();
        if (ouvert.isPresent() && sortieObligatoire(ouvert.get()).isBefore(maintenant)) {
            fermerAutomatiquement(ouvert.get()); // oubli de badge : sortie mise à entrée + 12h
            ouvert = Optional.empty();           // ce badge est donc une nouvelle entrée
        }

        Pointage pointage;
        if (ouvert.isPresent()) {
            pointage = ouvert.get();
            pointage.setSortie(maintenant);
        } else {
            pointage = new Pointage();
            pointage.setEmploye(employe);
            pointage.setEntree(maintenant);
        }

        return Optional.of(pointageRepository.save(pointage));
    }

    public Optional<Pointage> pointerConnecte(String email) {
        return getEmployeConnecte(email).flatMap(employe -> pointer(employe.getId()));
    }

    /**
     * Correction manuelle par un manager. Avant d'écraser le pointage, on garde une trace
     * de l'ancienne valeur, de l'auteur, de la date et du motif (CorrectionPointage).
     */
    @Transactional
    public Optional<Pointage> corriger(Long pointageId, LocalDateTime entree, LocalDateTime sortie,
                                       String commentaire, String auteur) {
        Optional<Pointage> pointageOpt = pointageRepository.findById(pointageId);
        if (pointageOpt.isEmpty()) {
            return Optional.empty();
        }
        Pointage pointage = pointageOpt.get();

        correctionPointageRepository.save(new CorrectionPointage(pointage, auteur, entree, sortie, commentaire));

        LocalDateTime ancienneEntree = pointage.getEntree();
        LocalDateTime ancienneSortie = pointage.getSortie();
        pointage.setEntree(entree);
        pointage.setSortie(sortie);
        pointage.setCommentaire(commentaire);
        pointage.setSortieAutomatique(false); // vérifié par le manager
        Pointage corrige = pointageRepository.save(pointage);

        // Si une semaine touchée (avant ou après correction) a déjà été reportée au compteur, on recalcule
        // ses heures sup. Un pointage de nuit peut commencer un dimanche et finir un lundi : on regarde
        // la semaine de l'entrée ET celle de la sortie.
        Employe employe = pointage.getEmploye();
        heuresService.regulariserHeuresSup(employe, ancienneEntree.toLocalDate(), auteur);
        if (ancienneSortie != null) {
            heuresService.regulariserHeuresSup(employe, ancienneSortie.toLocalDate(), auteur);
        }
        heuresService.regulariserHeuresSup(employe, entree.toLocalDate(), auteur);
        if (sortie != null) {
            heuresService.regulariserHeuresSup(employe, sortie.toLocalDate(), auteur);
        }
        return Optional.of(corrige);
    }

    /**
     * Le manager valide une sortie automatique avec un commentaire obligatoire (le lendemain ou plus tard).
     * Le pointage garde ses heures ; s'il faut changer l'heure de sortie, utiliser "Corriger".
     * La validation est tracée comme une correction (qui, quand, pourquoi) et l'alerte disparaît.
     */
    @Transactional
    public void validerSortieAutomatique(Long pointageId, String commentaire, String auteur) {
        if (commentaire == null || commentaire.isBlank()) {
            throw new ValidationException("Un commentaire est obligatoire pour valider une sortie automatique");
        }
        Pointage pointage = pointageRepository.findById(pointageId)
                .orElseThrow(() -> new ValidationException("Pointage introuvable"));
        if (!pointage.isSortieAutomatique()) {
            throw new ValidationException("Ce pointage n'a pas de sortie automatique à valider");
        }

        correctionPointageRepository.save(new CorrectionPointage(pointage, auteur, pointage.getEntree(),
                pointage.getSortie(), "Sortie automatique validée : " + commentaire));
        pointage.setCommentaire(pointage.getCommentaire() + " — Validé : " + commentaire);
        pointage.setSortieAutomatique(false);
        pointageRepository.save(pointage);
    }

    /** Heure à laquelle l'employé est obligatoirement sorti : entrée + 12h. */
    private LocalDateTime sortieObligatoire(Pointage pointage) {
        return pointage.getEntree().plus(DUREE_MAXIMALE);
    }

    /** Clôture un pointage à entrée + 12h et le signale au manager, qui devra le valider avec un commentaire. */
    private void fermerAutomatiquement(Pointage pointage) {
        LocalDateTime sortie = sortieObligatoire(pointage);
        String note = "Sortie automatique à " + sortie.format(HEURE) + " (pas de pointage de sortie, 12h maximum)";
        pointage.setSortie(sortie);
        pointage.setSortieAutomatique(true);
        pointage.setCommentaire(pointage.getCommentaire() == null ? note : pointage.getCommentaire() + " — " + note);
        pointageRepository.save(pointage);
        heuresService.regulariserHeuresSup(pointage.getEmploye(), pointage.getEntree().toLocalDate(),
                TachesAutomatiques.AUTEUR);
    }

    public List<CorrectionPointage> getCorrections(Long employeId) {
        return correctionPointageRepository.findByPointageEmployeIdOrderByDateDesc(employeId);
    }
}
