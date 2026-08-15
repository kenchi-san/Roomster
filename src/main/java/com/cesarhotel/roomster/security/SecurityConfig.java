package com.cesarhotel.roomster.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/h2-console/**").permitAll()
                        // Gestion RH : réservée à l'admin. /pointer-employe/{id} y est rattaché car
                        // l'id y est fourni par le client (pointeuse collective, pas d'auto-restriction possible).
                        .requestMatchers("/liste-employe", "/ajout-employe",
                                "/delete-employe/**", "/edit-employe/**", "/toggle-actif-employe/**",
                                "/reset-password-employe/**",
                                "/pointages", "/pointer-employe/**", "/conge-paye").hasRole("ADMIN")
                        // Espace personnel : accessible à tout utilisateur connecté (admin compris).
                        .requestMatchers("/mon-pointage", "/mon-pointage/**", "/mes-conges", "/mon-compte").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form.permitAll())
                .logout(logout -> logout.permitAll())
                // Le token doit être lisible en JS (cookie) pour les appels fetch() des pages employé/pointage.
                // CsrfTokenRequestAttributeHandler (plutôt que le Xor... par défaut depuis Security 6.4+) :
                // le JS renvoie la valeur brute du cookie, il ne faut donc pas de masquage BREACH ici.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/h2-console/**"))
                // La console H2 s'affiche dans une frame : sameOrigin() plutôt que le déni par défaut.
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));

        return http.build();
    }
}
