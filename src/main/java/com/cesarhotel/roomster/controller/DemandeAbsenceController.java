package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.model.TypeAbsence;
import com.cesarhotel.roomster.service.DemandeAbsenceService;
import com.cesarhotel.roomster.service.PointageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Demandes d'absence (F4).
 * Chaque action est un formulaire classique : on traite, on met un message (succès ou erreur)
 * dans les "flash attributes", puis on redirige vers la page de la liste.
 */
@Controller
public class DemandeAbsenceController {

    private final DemandeAbsenceService demandeAbsenceService;
    private final PointageService pointageService;

    public DemandeAbsenceController(DemandeAbsenceService demandeAbsenceService, PointageService pointageService) {
        this.demandeAbsenceService = demandeAbsenceService;
        this.pointageService = pointageService;
    }

    // --- Côté employé : uniquement ses propres demandes (l'employé vient toujours de Authentication) ---

    @GetMapping("/mes-demandes")
    public String mesDemandes(Model model, Authentication authentication) {
        model.addAttribute("demandes", demandeAbsenceService.getMesDemandes(authentication.getName()));
        model.addAttribute("types", demandeAbsenceService.getTypesProposes());
        return "absence/mes-demandes";
    }

    @PostMapping("/mes-demandes")
    public String soumettre(@RequestParam(required = false) TypeAbsence type,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
                            Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            demandeAbsenceService.soumettre(authentication.getName(), type, debut, fin);
            redirectAttributes.addFlashAttribute("succes", "Demande envoyée à votre manager.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/mes-demandes";
    }

    @PostMapping("/mes-demandes/{id}/annuler")
    public String annuler(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            demandeAbsenceService.annuler(authentication.getName(), id);
            redirectAttributes.addFlashAttribute("succes", "Demande annulée.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/mes-demandes";
    }

    // --- Côté manager ---

    @GetMapping("/demandes")
    public String demandes(Model model) {
        model.addAttribute("enAttente", demandeAbsenceService.getDemandesEnAttente());
        model.addAttribute("traitees", demandeAbsenceService.getDemandesTraitees());
        model.addAttribute("employes", pointageService.getEmployesActifs());
        return "absence/demandes";
    }

    @PostMapping("/demandes/{id}/valider")
    public String valider(@PathVariable Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            demandeAbsenceService.valider(id, authentication.getName());
            redirectAttributes.addFlashAttribute("succes", "Demande validée.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/demandes";
    }

    @PostMapping("/demandes/{id}/refuser")
    public String refuser(@PathVariable Long id, @RequestParam(required = false) String motif,
                          Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            demandeAbsenceService.refuser(id, authentication.getName(), motif);
            redirectAttributes.addFlashAttribute("succes", "Demande refusée.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/demandes";
    }

    @PostMapping("/demandes/maladie")
    public String saisirMaladie(@RequestParam Long employeId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
                                Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            demandeAbsenceService.saisirMaladie(employeId, debut, fin, authentication.getName());
            redirectAttributes.addFlashAttribute("succes", "Arrêt maladie enregistré.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/demandes";
    }
}
