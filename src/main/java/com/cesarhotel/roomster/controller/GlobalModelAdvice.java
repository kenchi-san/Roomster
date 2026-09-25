package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.service.DemandeAbsenceService;
import com.cesarhotel.roomster.service.ParametresService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Données disponibles dans tous les templates (menu de navigation notamment),
 * sans dépendre du dialecte Thymeleaf de Spring Security.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final DemandeAbsenceService demandeAbsenceService;
    private final ParametresService parametresService;

    public GlobalModelAdvice(DemandeAbsenceService demandeAbsenceService, ParametresService parametresService) {
        this.demandeAbsenceService = demandeAbsenceService;
        this.parametresService = parametresService;
    }

    /** Réglage de l'hôtel : sur false, les templates masquent tout ce qui concerne les RTT. */
    @ModelAttribute("rttActives")
    public boolean rttActives() {
        return parametresService.rttActives();
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    /** Pastille "demandes en attente" dans le menu du manager (v1 : pas d'email). */
    @ModelAttribute("nbDemandesEnAttente")
    public long nbDemandesEnAttente(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return 0;
        }
        return demandeAbsenceService.compterDemandesEnAttente();
    }
}
