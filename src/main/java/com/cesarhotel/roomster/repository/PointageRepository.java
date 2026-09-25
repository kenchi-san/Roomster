package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.Pointage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointageRepository extends JpaRepository<Pointage, Long> {

    Optional <Pointage> findByEmployeIdAndSortieIsNull(Long employeId);

    List<Pointage> findBySortieIsNull();

    /** Pointages clôturés automatiquement à 12h, pas encore validés par le manager. */
    List<Pointage> findBySortieAutomatiqueTrueOrderByEntreeDesc();

    Page<Pointage> findByEmployeId(Long employeId, Pageable pageable);

    List<Pointage> findByEntreeBetween(LocalDateTime debut, LocalDateTime fin);

    List<Pointage> findByEmployeIdAndEntreeBetweenOrderByEntree(Long employeId, LocalDateTime debut, LocalDateTime fin);

}
