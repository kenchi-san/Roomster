package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.dtos.CompteurEmployeDto;
import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.service.CalculConges;
import com.cesarhotel.roomster.service.CompteurEmployeService;
import com.cesarhotel.roomster.service.PointageService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
public class EmployeCongeController {

    private final CompteurEmployeService compteurEmployeService;
    private final PointageService pointageService;

    public EmployeCongeController(CompteurEmployeService compteurEmployeService, PointageService pointageService) {
        this.compteurEmployeService = compteurEmployeService;
        this.pointageService = pointageService;
    }

    @GetMapping("/conge-paye")
    public String infoCompteurEmploye(Model model) {
        List<CompteurEmployeDto> compteur = compteurEmployeService.getCompteursAvecHistoriqueN1();
        model.addAttribute("compteur", compteur);
        model.addAttribute("mouvements", compteurEmployeService.getDerniersMouvements());
        model.addAttribute("employes", pointageService.getEmployesActifs());
        model.addAttribute("periodeCourante", CalculConges.periodeDe(LocalDate.now()));
        model.addAttribute("moisPrecedent", LocalDate.now().minusMonths(1).toString().substring(0, 7)); // ex : "2026-08"
        return "employe/conge-paye";
    }

    /**
     * Acquisition mensuelle des CP pour tous les employés.
     * mois : valeur d'un champ HTML type="month", ex : "2026-08".
     */
    @PostMapping("/compteurs/acquisition")
    public String acquerir(@RequestParam String mois, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            LocalDate premierJour = LocalDate.parse(mois + "-01");
            int nombre = compteurEmployeService.acquerirConges(premierJour, authentication.getName());
            redirectAttributes.addFlashAttribute("succes", "CP acquis crédités à " + nombre + " employé(s).");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/conge-paye";
    }

    /** Saisie manuelle des soldes de départ (ou correction RH) d'un compteur. annee = début de la période (juin). */
    @PostMapping("/compteurs/initialiser")
    public String initialiser(@RequestParam Long employeId, @RequestParam int annee,
                              @RequestParam double soldeCongesPayes,
                              @RequestParam(required = false) Double soldeRtt, // absent si l'hôtel n'accorde pas de RTT
                              Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            compteurEmployeService.initialiser(employeId, annee, soldeCongesPayes, soldeRtt, authentication.getName());
            redirectAttributes.addFlashAttribute("succes", "Compteur enregistré.");
        } catch (ValidationException e) {
            redirectAttributes.addFlashAttribute("erreur", e.getMessage());
        }
        return "redirect:/conge-paye";
    }

    @GetMapping("/mes-conges")
    public String mesConges(Model model, Authentication authentication) {
        List<CompteurEmployeDto> compteur = compteurEmployeService.getMesCompteurs(authentication.getName());
        model.addAttribute("compteur", compteur);
        model.addAttribute("mouvements", compteurEmployeService.getMesMouvements(authentication.getName()));
        return "employe/mes-conges";
    }

}
