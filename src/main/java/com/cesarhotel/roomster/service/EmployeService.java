package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.dtos.EmployeFormDto;
import com.cesarhotel.roomster.mapper.EmployeMapper;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.repository.EmployeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class EmployeService {

    private final EmployeRepository employeRepository;
    private final EmployeMapper employeMapper;
    private final PasswordEncoder passwordEncoder;

    public EmployeService(EmployeRepository employeRepository, EmployeMapper employeMapper,
                          PasswordEncoder passwordEncoder) {
        this.employeRepository = employeRepository;
        this.employeMapper = employeMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public Employe creer(EmployeFormDto dto) {
        if (employeRepository.existsByUserEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Un employé avec cet email existe déjà");
        }
        Employe employe = employeMapper.toEntity(dto);
        // Mot de passe temporaire tant qu'il n'y a pas de flux d'invitation/définition de mot de passe.
        employe.getUser().setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        return employeRepository.save(employe);
    }
}
