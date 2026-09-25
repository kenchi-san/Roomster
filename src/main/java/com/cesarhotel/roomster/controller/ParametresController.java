package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.service.ParametresService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Réglages de l'hôtel (manager). La valeur actuelle "rttActives" est fournie à toutes les pages par GlobalModelAdvice.
 */
@Controller
public class ParametresController {

    private final ParametresService parametresService;

    public ParametresController(ParametresService parametresService) {
        this.parametresService = parametresService;
    }

    @GetMapping("/parametres")
    public String page() {
        return "parametres";
    }

    /** Case à cocher : absente du formulaire quand elle est décochée, d'où la valeur par défaut false. */
    @PostMapping("/parametres")
    public String enregistrer(@RequestParam(defaultValue = "false") boolean rttActives, RedirectAttributes redirectAttributes) {
        parametresService.definirRttActives(rttActives);
        redirectAttributes.addFlashAttribute("succes", rttActives
                ? "RTT activés : le type RTT est proposé aux salariés et les soldes RTT sont affichés."
                : "RTT désactivés : le type RTT et les colonnes RTT sont masqués.");
        return "redirect:/parametres";
    }
}
