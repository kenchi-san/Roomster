package com.cesarhotel.roomster.repository;

import com.cesarhotel.roomster.model.DemandeAbsence;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.StatutDemande;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DemandeAbsenceRepository extends JpaRepository<DemandeAbsence, Long> {

    List<DemandeAbsence> findByEmployeOrderByDebutDesc(Employe employe);

    List<DemandeAbsence> findByEmployeAndStatutIn(Employe employe, List<StatutDemande> statuts);

    List<DemandeAbsence> findByStatutOrderByDebut(StatutDemande statut);

    List<DemandeAbsence> findTop20ByStatutNotOrderByDateSoumissionDesc(StatutDemande statut);

    /**
     * Demandes au statut donné qui touchent une période : debut <= debutAvantLe ET fin >= finApresLe.
     * Pour un seul jour J : (statut, J, J). Pour une période [du, au] : (statut, au, du).
     */
    List<DemandeAbsence> findByStatutAndDebutLessThanEqualAndFinGreaterThanEqual(StatutDemande statut, LocalDate debutAvantLe,
                                                                               LocalDate finApresLe);

    long countByStatut(StatutDemande statut);
}
