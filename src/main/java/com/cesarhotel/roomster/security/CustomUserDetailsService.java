package com.cesarhotel.roomster.security;

import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.User;
import com.cesarhotel.roomster.repository.EmployeRepository;
import com.cesarhotel.roomster.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final EmployeRepository employeRepository;

    public CustomUserDetailsService(UserRepository userRepository, EmployeRepository employeRepository) {
        this.userRepository = userRepository;
        this.employeRepository = employeRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Aucun utilisateur avec cet email : " + email));

        // Révocation d'un compte = désactivation de l'employé (/liste-employe) : il ne peut plus se connecter.
        boolean actif = employeRepository.findByUserEmail(email)
                .map(Employe::isActif)
                .orElse(true);

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .disabled(!actif)
                .build();
    }
}
