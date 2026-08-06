package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.dtos.EmployeFormDto;
import com.cesarhotel.roomster.mapper.EmployeMapper;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.repository.EmployeRepository;
import org.springframework.stereotype.Service;

@Service
public class EmployeService {

    private final EmployeRepository employeRepository;
    private final EmployeMapper employeMapper;

    public EmployeService(EmployeRepository employeRepository, EmployeMapper employeMapper) {
        this.employeRepository = employeRepository;
        this.employeMapper = employeMapper;
    }

    public Employe creer(EmployeFormDto dto) {
        if (employeRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Un employé avec cet email existe déjà");
        }
        Employe employe = employeMapper.toEntity(dto);
        return employeRepository.save(employe);
    }
}
