package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.Employe;

/**
 * motDePasseTemporaire est en clair, à afficher une seule fois à l'admin qui vient de créer la
 * fiche (aucun envoi d'email en v1) : ne jamais le loguer ni le persister ailleurs que haché sur User.
 */
public record EmployeCreationResult(Employe employe, String motDePasseTemporaire) {
}
