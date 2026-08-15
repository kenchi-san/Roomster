package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.CompteurEmploye;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Poste;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeRepository extends JpaRepository<Employe, Long> {

    boolean existsByUserEmail(String email);

    boolean existsByUserEmailAndIdNot(String email, Long id);

    Optional<Employe> findByUserEmail(String email);

    List<Employe> findByActif(boolean actif);

    List<Employe> findByPoste(Poste poste);

    List<Employe> findByActifAndPoste(boolean actif, Poste poste);

}
