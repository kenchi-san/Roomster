package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.CompteurEmploye;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.MouvementCompteur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MouvementCompteurRepository extends JpaRepository<MouvementCompteur, Long> {

    List<MouvementCompteur> findByCompteurEmployeOrderByDateDesc(Employe employe);

    List<MouvementCompteur> findTop30ByOrderByDateDesc();

    boolean existsByCompteurAndSemaine(CompteurEmploye compteur, LocalDate semaine);

    boolean existsByCompteurAndMois(CompteurEmploye compteur, LocalDate mois);

    /** Les mouvements d'une semaine (report + régularisations), pour savoir combien d'heures sup ont déjà été reportées. */
    List<MouvementCompteur> findByCompteurAndSemaine(CompteurEmploye compteur, LocalDate semaine);

    /** Les mouvements d'un mois (acquisition + régularisations), pour savoir combien de CP ont déjà été crédités. */
    List<MouvementCompteur> findByCompteurAndMois(CompteurEmploye compteur, LocalDate mois);
}
