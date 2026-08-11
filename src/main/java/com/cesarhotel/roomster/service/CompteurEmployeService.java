package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.dtos.CompteurEmployeDto;
import com.cesarhotel.roomster.mapper.CompteurEmployeMapper;
import com.cesarhotel.roomster.model.CompteurEmploye;
import com.cesarhotel.roomster.repository.CompteurEmployeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CompteurEmployeService {

    private final CompteurEmployeRepository compteurEmployeRepository;
    private final CompteurEmployeMapper compteurEmployeMapper;

    public CompteurEmployeService(CompteurEmployeRepository compteurEmployeRepository, CompteurEmployeMapper compteurEmployeMapper) {
        this.compteurEmployeRepository = compteurEmployeRepository;
        this.compteurEmployeMapper = compteurEmployeMapper;
    }

    /**
     * Une ligne par employé, avec le compteur de l'année courante et, si disponible, celui de l'année N-1.
     */
    public List<CompteurEmployeDto> getCompteursAvecHistoriqueN1() {
        int anneeCourante = LocalDate.now().getYear();

        List<CompteurEmploye> compteurs = compteurEmployeRepository.findAllByOrderByEmploye_User_NomAscEmploye_User_PrenomAscAnneeDesc();

        Map<Long, CompteurEmploye> comptesAnneeCourante = new LinkedHashMap<>();
        Map<Long, CompteurEmploye> comptesAnneeN1 = new HashMap<>();
        for (CompteurEmploye c : compteurs) {
            if (c.getAnnee() == anneeCourante) {
                comptesAnneeCourante.put(c.getEmploye().getId(), c);
            } else if (c.getAnnee() == anneeCourante - 1) {
                comptesAnneeN1.put(c.getEmploye().getId(), c);
            }
        }
        return comptesAnneeCourante.values().stream()
                .map(c -> {
                    CompteurEmployeDto dto = compteurEmployeMapper.toDto(c);
                    CompteurEmploye n1 = comptesAnneeN1.get(c.getEmploye().getId());
                    if (n1 != null) {
                        dto.setSoldeCongesPayesN1(n1.getSoldeCongesPayes());
                        dto.setSoldeRttN1(n1.getSoldeRtt());
                        dto.setHeuresSupCumuleesN1(n1.getHeuresSupCumulees());
                    }
                    return dto;
                })
                .toList();
    }
}
