package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.CompteurEmploye;
import com.cesarhotel.roomster.model.DemandeAbsence;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.Pointage;
import com.cesarhotel.roomster.model.TypeAbsence;
import com.cesarhotel.roomster.repository.CompteurEmployeRepository;
import com.cesarhotel.roomster.repository.PointageRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exports pour le comptable : fichiers CSV d'un mois, qui s'ouvrent directement dans Excel (voir Csv).
 * Les heures sont en heures décimales (7h30 → 7,50) pour pouvoir les additionner.
 */
@Service
public class ExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm");

    private final HeuresService heuresService;
    private final DemandeAbsenceService demandeAbsenceService;
    private final PointageRepository pointageRepository;
    private final CompteurEmployeRepository compteurEmployeRepository;
    private final ParametresService parametresService;

    public ExportService(HeuresService heuresService, DemandeAbsenceService demandeAbsenceService,
                         PointageRepository pointageRepository, CompteurEmployeRepository compteurEmployeRepository,
                         ParametresService parametresService) {
        this.heuresService = heuresService;
        this.demandeAbsenceService = demandeAbsenceService;
        this.pointageRepository = pointageRepository;
        this.compteurEmployeRepository = compteurEmployeRepository;
        this.parametresService = parametresService;
    }

    /**
     * 1. Récap paie : une ligne par employé, mêmes chiffres que l'écran "Récap mensuel",
     *    plus les jours d'absence du mois par type.
     */
    public String exporterRecapPaie(LocalDate jourDuMois) {
        LocalDate premierJour = jourDuMois.withDayOfMonth(1);
        LocalDate dernierJour = premierJour.plusMonths(1).minusDays(1);
        List<DemandeAbsence> absences = demandeAbsenceService.getAbsences(premierJour, dernierJour);

        boolean rtt = parametresService.rttActives(); // colonne "Jours RTT" seulement si l'hôtel accorde des RTT

        List<String> entete = new ArrayList<>(List.of("Matricule", "Nom", "Prénom", "Poste", "Contrat (h/sem.)",
                "Heures travaillées", "dont heures de nuit",
                "HS +10 %", "HS +20 %", "HS +50 %", "H. compl. +10 %", "H. compl. +25 %",
                "Repos compensateur nuit", "Fériés travaillés", "Jours CP"));
        if (rtt) {
            entete.add("Jours RTT");
        }
        entete.addAll(List.of("Jours maladie", "Jours sans solde", "Jours récup. férié", "Pointages sans sortie (non comptés)"));

        StringBuilder csv = new StringBuilder(Csv.BOM);
        csv.append(Csv.ligne(entete));

        for (RecapEmploye r : heuresService.calculerRecapMois(premierJour)) {
            Employe e = r.getEmploye();
            List<String> ligne = new ArrayList<>(List.of(
                    String.valueOf(e.getId()), e.getUser().getNom(), e.getUser().getPrenom(), e.getPoste().name(),
                    Csv.heures(e.getDureeHebdoContrat()),
                    Csv.heures(r.getTotal()), Csv.heures(r.getTotalNuit()),
                    Csv.heures(r.getDecompte().getHeuresSup10()), Csv.heures(r.getDecompte().getHeuresSup20()),
                    Csv.heures(r.getDecompte().getHeuresSup50()),
                    Csv.heures(r.getDecompte().getHeuresComplementaires10()),
                    Csv.heures(r.getDecompte().getHeuresComplementaires25()),
                    Csv.heures(r.getReposNuit()), r.getFeriesTravailles(),
                    String.valueOf(joursDuMois(absences, e, TypeAbsence.CONGE_PAYE, premierJour, dernierJour))));
            if (rtt) {
                ligne.add(String.valueOf(joursDuMois(absences, e, TypeAbsence.RTT, premierJour, dernierJour)));
            }
            ligne.addAll(List.of(
                    String.valueOf(joursDuMois(absences, e, TypeAbsence.MALADIE, premierJour, dernierJour)),
                    String.valueOf(joursDuMois(absences, e, TypeAbsence.SANS_SOLDE, premierJour, dernierJour)),
                    String.valueOf(joursDuMois(absences, e, TypeAbsence.RECUP_JOUR_FERIE, premierJour, dernierJour)),
                    String.valueOf(r.getPointagesOuverts())));
            csv.append(Csv.ligne(ligne));
        }
        return csv.toString();
    }

    /** 2. Pointages : le détail de chaque pointage du mois (date d'entrée dans le mois), trié par employé puis date. */
    public String exporterPointages(LocalDate jourDuMois) {
        LocalDate premierJour = jourDuMois.withDayOfMonth(1);
        LocalDate dernierJour = premierJour.plusMonths(1).minusDays(1);

        List<Pointage> pointages = new ArrayList<>(pointageRepository.findByEntreeBetween(
                premierJour.atStartOfDay(), dernierJour.atTime(LocalTime.MAX)));
        pointages.sort(Comparator.comparing((Pointage p) -> p.getEmploye().getUser().getNom())
                .thenComparing(p -> p.getEmploye().getUser().getPrenom())
                .thenComparing(Pointage::getEntree));

        StringBuilder csv = new StringBuilder(Csv.BOM);
        csv.append(Csv.ligne("Matricule", "Nom", "Prénom", "Date", "Entrée", "Date de sortie", "Sortie",
                "Durée (h)", "dont nuit (h)", "Jour férié", "Commentaire"));
        for (Pointage p : pointages) {
            Employe e = p.getEmploye();
            boolean ferme = p.getSortie() != null;
            csv.append(Csv.ligne(
                    String.valueOf(e.getId()), e.getUser().getNom(), e.getUser().getPrenom(),
                    p.getEntree().format(DATE), p.getEntree().format(HEURE),
                    ferme ? p.getSortie().format(DATE) : "", ferme ? p.getSortie().format(HEURE) : "SANS SORTIE",
                    Csv.heures(CalculHeures.duree(p)),
                    ferme ? Csv.heures(CalculHeures.heuresDeNuit(p.getEntree(), p.getSortie())) : Csv.heures(Duration.ZERO),
                    JoursFeries.nom(p.getEntree().toLocalDate()),
                    p.getCommentaire()));
        }
        return csv.toString();
    }

    /** 3. Absences : les absences validées qui touchent le mois, avec les jours comptés dans ce mois. */
    public String exporterAbsences(LocalDate jourDuMois) {
        LocalDate premierJour = jourDuMois.withDayOfMonth(1);
        LocalDate dernierJour = premierJour.plusMonths(1).minusDays(1);

        List<DemandeAbsence> absences = new ArrayList<>(demandeAbsenceService.getAbsences(premierJour, dernierJour));
        absences.sort(Comparator.comparing((DemandeAbsence a) -> a.getEmploye().getUser().getNom())
                .thenComparing(DemandeAbsence::getDebut));

        StringBuilder csv = new StringBuilder(Csv.BOM);
        csv.append(Csv.ligne("Matricule", "Nom", "Prénom", "Type", "Du", "Au",
                "Jours au total", "Jours dans le mois", "Unité", "Validée par", "Validée le"));
        for (DemandeAbsence a : absences) {
            Employe e = a.getEmploye();
            csv.append(Csv.ligne(
                    String.valueOf(e.getId()), e.getUser().getNom(), e.getUser().getPrenom(),
                    a.getType().getLibelle(), a.getDebut().format(DATE), a.getFin().format(DATE),
                    String.valueOf(a.nombreDeJours()),
                    String.valueOf(joursDansLaPeriode(a, premierJour, dernierJour)),
                    compteEnJoursOuvrables(a.getType()) ? "jours ouvrables" : "jours calendaires",
                    a.getValidateur() != null ? a.getValidateur().getUser().getPrenom() + " " + a.getValidateur().getUser().getNom() : "",
                    a.getDateDecision() != null ? a.getDateDecision().format(DATE) : ""));
        }
        return csv.toString();
    }

    /** 4. Compteurs : les soldes de la période de référence (juin → mai) qui contient le mois. Soldes à la date du jour. */
    public String exporterCompteurs(LocalDate jourDuMois) {
        int periode = CalculConges.periodeDe(jourDuMois.withDayOfMonth(1));

        List<CompteurEmploye> compteurs = new ArrayList<>();
        for (CompteurEmploye compteur : compteurEmployeRepository.findAllByOrderByAnneeDesc()) {
            if (compteur.getAnnee() == periode) {
                compteurs.add(compteur);
            }
        }
        compteurs.sort(Comparator.comparing((CompteurEmploye c) -> c.getEmploye().getUser().getNom())
                .thenComparing(c -> c.getEmploye().getUser().getPrenom()));

        boolean rtt = parametresService.rttActives(); // colonne "Solde RTT" seulement si l'hôtel accorde des RTT

        List<String> entete = new ArrayList<>(List.of("Matricule", "Nom", "Prénom", "Période", "Solde CP (jours ouvrables)"));
        if (rtt) {
            entete.add("Solde RTT (jours)");
        }
        entete.addAll(List.of("Heures sup cumulées (h)", "Soldes au"));

        StringBuilder csv = new StringBuilder(Csv.BOM);
        csv.append(Csv.ligne(entete));
        for (CompteurEmploye c : compteurs) {
            Employe e = c.getEmploye();
            List<String> ligne = new ArrayList<>(List.of(
                    String.valueOf(e.getId()), e.getUser().getNom(), e.getUser().getPrenom(),
                    "juin " + periode + " → mai " + (periode + 1),
                    Csv.nombre(c.getSoldeCongesPayes())));
            if (rtt) {
                ligne.add(Csv.nombre(c.getSoldeRtt()));
            }
            ligne.addAll(List.of(Csv.heures(c.getHeuresSupCumulees()), LocalDate.now().format(DATE)));
            csv.append(Csv.ligne(ligne));
        }
        return csv.toString();
    }

    // --- Méthodes internes ---

    /** Total des jours d'un type d'absence pour cet employé, comptés dans le mois seulement. */
    private long joursDuMois(List<DemandeAbsence> absences, Employe employe, TypeAbsence type,
                             LocalDate premierJour, LocalDate dernierJour) {
        long jours = 0;
        for (DemandeAbsence absence : absences) {
            if (absence.getEmploye().getId().equals(employe.getId()) && absence.getType() == type) {
                jours += joursDansLaPeriode(absence, premierJour, dernierJour);
            }
        }
        return jours;
    }

    /**
     * Jours de l'absence qui tombent entre deux dates : jours ouvrables pour un CP ou un RTT (comme le décompte
     * du compteur), jours calendaires pour les autres types.
     */
    private long joursDansLaPeriode(DemandeAbsence absence, LocalDate du, LocalDate au) {
        LocalDate debut = absence.getDebut().isAfter(du) ? absence.getDebut() : du;
        LocalDate fin = absence.getFin().isBefore(au) ? absence.getFin() : au;
        if (fin.isBefore(debut)) {
            return 0;
        }
        if (compteEnJoursOuvrables(absence.getType())) {
            return CalculConges.joursOuvrables(debut, fin);
        }
        return ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    private boolean compteEnJoursOuvrables(TypeAbsence type) {
        return type == TypeAbsence.CONGE_PAYE || type == TypeAbsence.RTT;
    }
}
