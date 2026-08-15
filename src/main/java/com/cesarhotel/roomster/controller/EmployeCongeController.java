package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.dtos.CompteurEmployeDto;
import com.cesarhotel.roomster.service.CompteurEmployeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@Controller
public class EmployeCongeController {

    private final CompteurEmployeService compteurEmployeService;

    public EmployeCongeController(  CompteurEmployeService compteurEmployeService) {
        this.compteurEmployeService = compteurEmployeService;

    }
    @GetMapping("/conge-paye")
    public String infoCompteurEmploye(Model model) {
        List<CompteurEmployeDto> compteur = compteurEmployeService.getCompteursAvecHistoriqueN1();
        model.addAttribute("compteur", compteur);
        return "employe/conge-paye";
    }

    @GetMapping("/mes-conges")
    public String mesConges(Model model, Authentication authentication) {
        List<CompteurEmployeDto> compteur = compteurEmployeService.getMesCompteurs(authentication.getName());
        model.addAttribute("compteur", compteur);
        return "employe/mes-conges";
    }

}
