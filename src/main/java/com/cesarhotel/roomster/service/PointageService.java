package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Pointage;
import com.cesarhotel.roomster.repository.EmployeRepository;
import com.cesarhotel.roomster.repository.PointageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PointageService {

    private static final int TAILLE_PAGE_HISTORIQUE = 10;

    private final PointageRepository pointageRepository;
    private final EmployeRepository employeRepository;

    public PointageService(PointageRepository pointageRepository, EmployeRepository employeRepository) {
        this.pointageRepository = pointageRepository;
        this.employeRepository = employeRepository;
    }

    public List<Employe> getEmployesActifs() {
        return employeRepository.findAll().stream()
                .filter(Employe::isActif)
                .toList();
    }

    public Map<Long, LocalDateTime> getPointagesOuverts() {
        return pointageRepository.findBySortieIsNull().stream()
                .collect(Collectors.toMap(p -> p.getEmploye().getId(), Pointage::getEntree));
    }

    public Page<Pointage> getHistorique(int page) {
        return pointageRepository.findAll(
                PageRequest.of(page, TAILLE_PAGE_HISTORIQUE, Sort.by(Sort.Direction.DESC, "entree")));
    }

    public Optional<Pointage> pointer(Long employeId) {
        Optional<Employe> employeOpt = employeRepository.findById(employeId);
        if (employeOpt.isEmpty()) {
            return Optional.empty();
        }
        Employe employe = employeOpt.get();
        if (!employe.isActif()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Un employé désactivé ne peut plus pointer");
        }

        Optional<Pointage> ouvert = pointageRepository.findByEmployeIdAndSortieIsNull(employeId);
        Pointage pointage;
        if (ouvert.isPresent()) {
            pointage = ouvert.get();
            pointage.setSortie(LocalDateTime.now());
        } else {
            pointage = new Pointage();
            pointage.setEmploye(employe);
            pointage.setEntree(LocalDateTime.now());
        }

        return Optional.of(pointageRepository.save(pointage));
    }

    public Optional<Employe> getEmployeConnecte(String email) {
        return employeRepository.findByUserEmail(email);
    }

    public Page<Pointage> getHistoriquePersonnel(Long employeId, int page) {
        return pointageRepository.findByEmployeId(employeId,
                PageRequest.of(page, TAILLE_PAGE_HISTORIQUE, Sort.by(Sort.Direction.DESC, "entree")));
    }

    public Optional<Pointage> pointerConnecte(String email) {
        return getEmployeConnecte(email).flatMap(employe -> pointer(employe.getId()));
    }
}
