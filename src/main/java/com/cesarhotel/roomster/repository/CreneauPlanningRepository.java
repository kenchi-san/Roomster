package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.CreneauPlanning;
import com.cesarhotel.roomster.model.Employe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CreneauPlanningRepository extends JpaRepository<CreneauPlanning, Long> {

    /** Tous les créneaux entre deux jours inclus, dans l'ordre (jour puis heure de début). */
    List<CreneauPlanning> findByJourBetweenOrderByJourAscDebutAsc(LocalDate du, LocalDate au);

    /** Les créneaux d'un employé entre deux jours inclus. */
    List<CreneauPlanning> findByEmployeAndJourBetweenOrderByJourAscDebutAsc(Employe employe, LocalDate du, LocalDate au);
}
