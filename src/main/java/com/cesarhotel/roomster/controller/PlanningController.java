package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.service.PlanningService;
import com.cesarhotel.roomster.service.PointageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Planning prévisionnel.
 * ?semaine=2026-09-21 : n'importe quel jour de la semaine voulue. Sans paramètre : la semaine en cours.
 */
@Controller
public class PlanningController {

    private final PlanningService planningService;
    private final PointageService pointageService;

    public PlanningController(PlanningService planningService, PointageService pointageService) {
        this.planningService = planningService;
        this.pointageService = pointageService;
    }

    // --- Salarié ---

    @GetMapping("/mon-planning")
    public String monPlanning(@RequestParam(name = "semaine", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
                              Model model, Authentication authentication) {
        Employe moi = pointageService.getEmployeConnecte(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucune fiche employé associée à ce compte"));
        if (jour == null) {
            jour = LocalDate.now();
        }

        model.addAttribute("planning", planningService.getMonPlanning(moi, jour));
        return "planning/mon-planning";
    }

    // --- Manager ---

    /** employeId et jour (facultatifs) préremplissent le formulaire d'ajout : c'est le "+" d'une case de la grille. */
    @GetMapping("/planning")
    public String planning(@RequestParam(name = "semaine", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
                           @RequestParam(required = false) Long employeId,
                           @RequestParam(name = "jour", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jourChoisi,
                           Model model) {
        if (jour == null) {
            jour = jourChoisi != null ? jourChoisi : LocalDate.now();
        }

        model.addAttribute("planning", planningService.getPlanningSemaine(jour));
        model.addAttribute("employes", pointageService.getEmployesActifs());
        model.addAttribute("employeChoisi", employeId);
        model.addAttribute("jourChoisi", jourChoisi);
        return "planning/gestion";
    }

    @PostMapping("/planning/creneaux")
    public String ajouter(@RequestParam Long employeId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime debut,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime fin,
                          @RequestParam(required = false) String note,
                          RedirectAttributes redirectAttributes) {
        try {
            planningService.ajouterCreneau(employeId, jour, debut, fin, note);
            redirectAttributes.addFlashAttribute("succes", "Créneau ajouté.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/planning" + (jour != null ? "?semaine=" + jour : "");
    }

    @PostMapping("/planning/creneaux/{id}/supprimer")
    public String supprimer(@PathVariable Long id, @RequestParam(name = "semaine") String semaine,
                            RedirectAttributes redirectAttributes) {
        try {
            planningService.supprimerCreneau(id);
            redirectAttributes.addFlashAttribute("succes", "Créneau supprimé.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/planning?semaine=" + semaine;
    }

    @PostMapping("/planning/copier-semaine")
    public String copierSemaine(@RequestParam(name = "semaine") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lundi,
                                RedirectAttributes redirectAttributes) {
        int copies = planningService.copierSemainePrecedente(lundi);
        redirectAttributes.addFlashAttribute("succes", copies + " créneau(x) copié(s) depuis la semaine précédente.");
        return "redirect:/planning?semaine=" + lundi;
    }
}
