package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.dtos.PointageCorrectionDto;
import com.cesarhotel.roomster.dtos.PointageDto;
import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.mapper.PointageMapper;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.service.HeuresService;
import com.cesarhotel.roomster.service.PointageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.LocalDate;

@Controller
public class PointageController {

    private final PointageService pointageService;
    private final HeuresService heuresService;
    private final PointageMapper pointageMapper;

    public PointageController(PointageService pointageService, HeuresService heuresService,
                              PointageMapper pointageMapper) {
        this.pointageService = pointageService;
        this.heuresService = heuresService;
        this.pointageMapper = pointageMapper;
    }

    // --- Pointeuse du manager ---

    @GetMapping("/pointages")
    public String listePointages(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("employes", pointageService.getEmployesActifs());
        model.addAttribute("pointagesOuverts", pointageService.getPointagesOuverts());
        model.addAttribute("historique", pointageService.getHistorique(page));
        return "pointage/liste";
    }

    @PostMapping("/pointer-employe/{employeId}")
    public ResponseEntity<PointageDto> pointer(@PathVariable Long employeId) {
        return ResponseEntity.of(pointageService.pointer(employeId).map(pointageMapper::toDto));
    }

    @PatchMapping("/pointer-employe/{id}/corriger")
    public ResponseEntity<PointageDto> corriger(@PathVariable Long id, @RequestBody PointageCorrectionDto dto,
                                                Authentication authentication) {
        if (dto.getCommentaire() == null || dto.getCommentaire().isBlank()) {
            throw new ValidationException("Un commentaire est obligatoire pour corriger un pointage");
        }
        if (dto.getEntree() == null) {
            throw new ValidationException("La date d'entrée est obligatoire");
        }
        if (dto.getSortie() != null && !dto.getSortie().isAfter(dto.getEntree())) {
            throw new ValidationException("La sortie doit être postérieure à l'entrée");
        }
        // 12h maximum : au-delà, l'employé est obligatoirement sorti
        if (dto.getSortie() != null
                && Duration.between(dto.getEntree(), dto.getSortie()).compareTo(PointageService.DUREE_MAXIMALE) > 0) {
            throw new ValidationException("Un pointage ne peut pas dépasser 12h");
        }

        return pointageService.corriger(id, dto.getEntree(), dto.getSortie(), dto.getCommentaire(), authentication.getName())
                .map(pointageMapper::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Le manager valide une sortie automatique (pas de pointage de sortie, clôture à 12h) avec un commentaire.
     * Depuis le tableau de bord, le lendemain ou plus tard ; l'alerte disparaît ensuite.
     */
    @PostMapping("/pointages/{id}/valider-sortie")
    public String validerSortie(@PathVariable Long id, @RequestParam(required = false) String commentaire,
                                Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            pointageService.validerSortieAutomatique(id, commentaire, authentication.getName());
            redirectAttributes.addFlashAttribute("succes", "Sortie automatique validée.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/tableau-de-bord";
    }

    // --- Heures d'un employé (manager) ---

    // ?semaine=2026-09-21 : n'importe quel jour de la semaine voulue. Sans paramètre : la semaine en cours.
    @GetMapping("/pointages/semaine/{employeId}")
    public String semaineEmploye(@PathVariable Long employeId,
                                 @RequestParam(name = "semaine", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
                                 Model model) {
        Employe employe = getEmploye(employeId);
        if (jour == null) {
            jour = LocalDate.now();
        }

        model.addAttribute("employe", employe);
        model.addAttribute("semaine", heuresService.getSemaine(employe, jour));
        model.addAttribute("corrections", pointageService.getCorrections(employeId));
        return "pointage/semaine";
    }

    @PostMapping("/pointages/semaine/{employeId}/reporter-heures-sup")
    public String reporterHeuresSup(@PathVariable Long employeId,
                                    @RequestParam(name = "semaine") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
                                    Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            heuresService.reporterHeuresSup(getEmploye(employeId), jour, authentication.getName());
            redirectAttributes.addFlashAttribute("succes", "Heures sup reportées au compteur.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/pointages/semaine/" + employeId + "?semaine=" + jour;
    }

    // ?mois=2026-07-01 : n'importe quel jour du mois voulu. Sans paramètre : le mois en cours.
    @GetMapping("/pointages/mois")
    public String recapMois(@RequestParam(name = "mois", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
                            Model model) {
        if (jour == null) {
            jour = LocalDate.now();
        }
        model.addAttribute("recap", heuresService.getRecapMois(jour));
        return "pointage/mois";
    }

    // --- Espace personnel ---

    @GetMapping("/mon-pointage")
    public String monPointage(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(name = "semaine", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
                              Model model, Authentication authentication) {
        Employe moi = pointageService.getEmployeConnecte(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucune fiche employé associée à ce compte"));
        if (jour == null) {
            jour = LocalDate.now();
        }

        model.addAttribute("moi", moi);
        model.addAttribute("pointageOuvert", pointageService.getPointagesOuverts().get(moi.getId()));
        model.addAttribute("semaine", heuresService.getSemaine(moi, jour));
        model.addAttribute("historique", pointageService.getHistoriquePersonnel(moi.getId(), page));
        return "pointage/mon-pointage";
    }

    // Aucun id d'employé pris en paramètre : on pointe uniquement pour l'utilisateur connecté,
    // pour qu'un salarié ne puisse jamais pointer à la place d'un collègue.
    @PostMapping("/mon-pointage/pointer")
    public ResponseEntity<PointageDto> pointerMoi(Authentication authentication) {
        return ResponseEntity.of(pointageService.pointerConnecte(authentication.getName()).map(pointageMapper::toDto));
    }

    private Employe getEmploye(Long employeId) {
        return pointageService.getEmploye(employeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employé introuvable"));
    }
}
