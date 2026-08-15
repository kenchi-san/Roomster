package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.dtos.CompteurEmployeDto;
import com.cesarhotel.roomster.dtos.EmployeDto;
import com.cesarhotel.roomster.dtos.EmployeFormDto;
import com.cesarhotel.roomster.mapper.EmployeMapper;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Poste;
import com.cesarhotel.roomster.model.TypeContrat;
import com.cesarhotel.roomster.repository.EmployeRepository;
import com.cesarhotel.roomster.service.CompteurEmployeService;
import com.cesarhotel.roomster.service.EmployeCreationResult;
import com.cesarhotel.roomster.service.EmployeService;
import jakarta.persistence.Id;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class EmployeController {

    private final EmployeRepository employeRepository;
    private final EmployeMapper employeMapper;

    private final EmployeService employeService;

    public EmployeController(EmployeRepository employeRepository, EmployeMapper employeMapper,
                             EmployeService employeService) {
        this.employeRepository = employeRepository;
        this.employeMapper = employeMapper;
        this.employeService = employeService;
    }

    @GetMapping("/ajout-employe")
    public String formulaireAjoutEmploye(Model model) {
        model.addAttribute("employe", new EmployeFormDto());
        model.addAttribute("postes", Poste.values());
        model.addAttribute("typesContrat", TypeContrat.values());
        return "employe/ajout";
    }

    @PostMapping("/ajout-employe")
    public String ajoutEmploye(@Valid @ModelAttribute("employe") EmployeFormDto dto,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("postes", Poste.values());
            model.addAttribute("typesContrat", TypeContrat.values());
            return "employe/ajout";
        }

        EmployeCreationResult result = employeService.creer(dto);
        // Aucun envoi d'email en v1 : le mot de passe temporaire n'est affiché qu'une fois ici,
        // à l'admin qui vient de créer la fiche, à charge pour lui de le communiquer à la main.
        redirectAttributes.addFlashAttribute("motDePasseTemporaire", result.motDePasseTemporaire());
        redirectAttributes.addFlashAttribute("emailNouvelEmploye", result.employe().getUser().getEmail());
        return "redirect:/liste-employe";
    }

    @GetMapping("/liste-employe")
    public String listEmploye(Model model) {
        List<EmployeDto> employes = employeMapper.toDtoList(employeRepository.findAll());
        model.addAttribute("employes", employes);
        model.addAttribute("postes", Poste.values());
        model.addAttribute("typesContrat", TypeContrat.values());
        return "employe/liste";
    }

    @DeleteMapping("/delete-employe/{id}")
    public ResponseEntity<Void> deleteEmploye(@PathVariable Long id) {
        if (!employeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        } else {
            employeRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }

    }

    @PatchMapping("/toggle-actif-employe/{id}")
    public ResponseEntity<?> toggleActifEmploye(@PathVariable Long id) {
        if (!employeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Employe employe = employeRepository.findById(id).orElseThrow();
        boolean nouveauStatutActif = !employe.isActif();
        LocalDate nouvelleDateSortie = nouveauStatutActif ? null : LocalDate.now();

        if (nouvelleDateSortie != null && nouvelleDateSortie.isBefore(employe.getDateEntree())) {
            return ResponseEntity.badRequest()
                    .body("La date de sortie ne peut pas être antérieure à la date d'entrée");
        }

        employe.setActif(nouveauStatutActif);
        employe.setDateSortie(nouvelleDateSortie);

        Employe saved = employeRepository.save(employe);

        return ResponseEntity.ok(employeMapper.toDto(saved));
    }

    @PatchMapping("/reset-password-employe/{id}")
    public ResponseEntity<?> reinitialiserMotDePasse(@PathVariable Long id) {
        if (!employeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        String motDePasse = employeService.reinitialiserMotDePasse(id);
        return ResponseEntity.ok(Map.of("motDePasseTemporaire", motDePasse));
    }

    @PutMapping("/edit-employe/{id}")
    public ResponseEntity<?> editionEmploye(@PathVariable Long id, @RequestBody EmployeDto dto) {
        if (!employeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Employe employe = employeRepository.findById(id).orElseThrow();
        employeMapper.updateEntityFromDto(dto, employe);

        if (employe.getDateSortie() != null && employe.getDateSortie().isBefore(employe.getDateEntree())) {
            return ResponseEntity.badRequest()
                    .body("La date de sortie ne peut pas être antérieure à la date d'entrée");
        }

        Employe saved = employeRepository.save(employe);

        return ResponseEntity.ok(employeMapper.toDto(saved));
    }


}
