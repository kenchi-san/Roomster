package com.cesarhotel.roomster.dtos;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Les pointages d'un employé sur une semaine, du lundi au dimanche, avec le calcul des heures (F3).
 * Les durées sont déjà mises en forme, ex : "38h30".
 */
@Data
public class SemaineDto {
    private LocalDate lundi;
    private LocalDate dimanche;
    private LocalDate semainePrecedente;
    private LocalDate semaineSuivante;
    private List<JourDto> jours;

    private String total;
    private String totalNuit;
    private String contrat;
    private boolean tempsPartiel;           // contrat < 35h : il peut y avoir des heures complémentaires

    // Heures sup au-delà de 35h, par taux de majoration (convention HCR)
    private String heuresSup10;             // 36e → 39e heure
    private String heuresSup20;             // 40e → 43e heure
    private String heuresSup50;             // à partir de la 44e heure

    // Heures complémentaires (temps partiel), entre le contrat et 35h
    private String heuresComplementaires10; // jusqu'au dixième du contrat
    private String heuresComplementaires25; // au-delà

    // Travail de nuit (convention HCR)
    private boolean travailleurDeNuit;      // au moins 2 jours avec 3h ou plus entre 22h et 7h
    private String reposNuit;               // repos compensateur : 1 % des heures de nuit

    /** Dépassements des durées maximales, repos trop courts… (ControlesHCR). */
    private List<String> alertes;
    /** Informations pour la paie : jours fériés travaillés. */
    private List<String> infosPaie;

    /** Heures sup au-delà du contrat ET de 35h : ce qui peut être ajouté au compteur. */
    private String heuresSupAuCompteur;
    private boolean heuresSupReportables;
    private boolean heuresSupDejaReportees;
}
