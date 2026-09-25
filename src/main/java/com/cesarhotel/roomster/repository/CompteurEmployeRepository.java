package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.CompteurEmploye;
import com.cesarhotel.roomster.model.Employe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompteurEmployeRepository extends JpaRepository<CompteurEmploye, Long> {

    List<CompteurEmploye> findAllByOrderByAnneeDesc();

    List<CompteurEmploye> findByEmployeOrderByAnneeDesc(Employe employe);

    Optional<CompteurEmploye> findByEmployeAndAnnee(Employe employe, int annee);

}
