package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.CompteurEmploye;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompteurEmployeRepository extends JpaRepository<CompteurEmploye, Long> {

    List<CompteurEmploye> findAllByOrderByEmploye_NomAscEmploye_PrenomAscAnneeDesc();

}
