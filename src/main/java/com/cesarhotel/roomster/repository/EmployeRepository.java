package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.CompteurEmploye;
import com.cesarhotel.roomster.model.Employe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeRepository extends JpaRepository<Employe, Long> {

    boolean existsByEmail(String email);

    List<Employe> findByActifOrderByNomAscPrenomAsc(boolean actif);

}
