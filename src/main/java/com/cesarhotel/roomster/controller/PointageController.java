package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.dtos.PointageDto;
import com.cesarhotel.roomster.mapper.PointageMapper;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.service.PointageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PointageController {

    private final PointageService pointageService;
    private final PointageMapper pointageMapper;

    public PointageController(PointageService pointageService, PointageMapper pointageMapper) {
        this.pointageService = pointageService;
        this.pointageMapper = pointageMapper;
    }

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

    @GetMapping("/mon-pointage")
    public String monPointage(@RequestParam(defaultValue = "0") int page, Model model, Authentication authentication) {
        Employe moi = pointageService.getEmployeConnecte(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucune fiche employé associée à ce compte"));

        model.addAttribute("moi", moi);
        model.addAttribute("pointageOuvert", pointageService.getPointagesOuverts().get(moi.getId()));
        model.addAttribute("historique", pointageService.getHistoriquePersonnel(moi.getId(), page));
        return "pointage/mon-pointage";
    }

    // Aucun id d'employé pris en paramètre : on pointe uniquement pour l'utilisateur connecté,
    // pour qu'un salarié ne puisse jamais pointer à la place d'un collègue.
    @PostMapping("/mon-pointage/pointer")
    public ResponseEntity<PointageDto> pointerMoi(Authentication authentication) {
        return ResponseEntity.of(pointageService.pointerConnecte(authentication.getName()).map(pointageMapper::toDto));
    }
}
