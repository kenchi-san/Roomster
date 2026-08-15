package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.User;
import com.cesarhotel.roomster.repository.UserRepository;
import com.cesarhotel.roomster.security.MotDePasseGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Génère un mot de passe temporaire, le hache sur l'utilisateur donné (non persisté ici : à la
     * charge de l'appelant, l'entité est généralement sauvegardée via l'Employe qui la porte),
     * et retourne la valeur en clair pour affichage unique à l'admin qui la communiquera à la main.
     */
    public String definirMotDePasseTemporaire(User user) {
        String motDePasse = MotDePasseGenerator.genererMotDePasseTemporaire();
        user.setPassword(passwordEncoder.encode(motDePasse));
        return motDePasse;
    }

    public void changerMotDePasse(String email, String ancienMotDePasse, String nouveauMotDePasse) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));

        if (!passwordEncoder.matches(ancienMotDePasse, user.getPassword())) {
            throw new IllegalArgumentException("Le mot de passe actuel est incorrect");
        }
        if (nouveauMotDePasse == null || nouveauMotDePasse.length() < 8) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit contenir au moins 8 caractères");
        }

        user.setPassword(passwordEncoder.encode(nouveauMotDePasse));
        userRepository.save(user);
    }
}
