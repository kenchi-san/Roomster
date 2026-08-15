package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.CompteurEmploye;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompteurEmployeRepository extends JpaRepository<CompteurEmploye, Long> {

    List<CompteurEmploye> findAllByOrderByEmploye_User_NomAscEmploye_User_PrenomAscAnneeDesc();

    List<CompteurEmploye> findByEmploye_User_EmailOrderByAnneeDesc(String email);

}
