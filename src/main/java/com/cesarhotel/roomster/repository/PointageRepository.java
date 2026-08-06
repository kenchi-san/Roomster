package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.CompteurEmploye;
import com.cesarhotel.roomster.model.Pointage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface PointageRepository extends JpaRepository<Pointage, Long> {

    Optional <Pointage> findByEmployeIdAndSortieIsNull(Long employeId);

    List<Pointage> findBySortieIsNull();

}
