package com.cesarhotel.roomster.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** Le tableau de bord est la page d'accueil naturelle du manager ; les salariés gardent l'accueil. */
    @GetMapping("/")
    public String accueil(Model model) {
        if (Boolean.TRUE.equals(model.getAttribute("isAdmin"))) {
            return "redirect:/tableau-de-bord";
        }
        return "accueil";
    }
}
