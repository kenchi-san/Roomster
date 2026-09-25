package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.CorrectionPointage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectionPointageRepository extends JpaRepository<CorrectionPointage, Long> {

    List<CorrectionPointage> findByPointageEmployeIdOrderByDateDesc(Long employeId);
}
