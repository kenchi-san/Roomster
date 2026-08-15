package com.cesarhotel.roomster.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expose isAdmin à tous les templates pour adapter la navigation (fragments/nav.html, accueil.html)
 * sans dépendre du dialecte Thymeleaf de Spring Security.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
