package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.dtos.EmployeFormDto;
import com.cesarhotel.roomster.mapper.EmployeMapper;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Role;
import com.cesarhotel.roomster.repository.EmployeRepository;
import org.springframework.stereotype.Service;

@Service
public class EmployeService {

    private final EmployeRepository employeRepository;
    private final EmployeMapper employeMapper;
    private final UserService userService;

    public EmployeService(EmployeRepository employeRepository, EmployeMapper employeMapper,
                          UserService userService) {
        this.employeRepository = employeRepository;
        this.employeMapper = employeMapper;
        this.userService = userService;
    }

    public EmployeCreationResult creer(EmployeFormDto dto) {
        if (employeRepository.existsByUserEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Un employé avec cet email existe déjà");
        }
        Employe employe = employeMapper.toEntity(dto);
        String motDePasse = userService.definirMotDePasseTemporaire(employe.getUser());
        employe.getUser().setRole(Role.SALARIE);
        Employe saved = employeRepository.save(employe);
        return new EmployeCreationResult(saved, motDePasse);
    }

    /**
     * Regénère un mot de passe temporaire pour un employé existant (compte bloqué, mot de passe
     * oublié...) : v1 sans email, l'admin communique la nouvelle valeur à la main.
     */
    public String reinitialiserMotDePasse(Long employeId) {
        Employe employe = employeRepository.findById(employeId)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable"));
        String motDePasse = userService.definirMotDePasseTemporaire(employe.getUser());
        employeRepository.save(employe);
        return motDePasse;
    }
}
