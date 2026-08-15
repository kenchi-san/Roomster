package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.dtos.ChangementMotDePasseDto;
import com.cesarhotel.roomster.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MonCompteController {

    private final UserService userService;

    public MonCompteController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/mon-compte")
    public String formulaire(Model model) {
        model.addAttribute("changementMotDePasse", new ChangementMotDePasseDto());
        return "compte/mon-compte";
    }

    @PostMapping("/mon-compte")
    public String changerMotDePasse(@Valid @ModelAttribute("changementMotDePasse") ChangementMotDePasseDto dto,
                                     BindingResult bindingResult, Authentication authentication,
                                     Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "compte/mon-compte";
        }

        if (!dto.getNouveauMotDePasse().equals(dto.getConfirmationMotDePasse())) {
            model.addAttribute("erreur", "La confirmation ne correspond pas au nouveau mot de passe");
            return "compte/mon-compte";
        }

        try {
            userService.changerMotDePasse(authentication.getName(), dto.getAncienMotDePasse(), dto.getNouveauMotDePasse());
        } catch (IllegalArgumentException e) {
            model.addAttribute("erreur", e.getMessage());
            return "compte/mon-compte";
        }

        redirectAttributes.addFlashAttribute("succes", "Mot de passe mis à jour.");
        return "redirect:/mon-compte";
    }
}
