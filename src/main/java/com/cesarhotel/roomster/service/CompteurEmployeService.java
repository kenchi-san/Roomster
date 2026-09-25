package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.dtos.CompteurEmployeDto;
import com.cesarhotel.roomster.exception.ValidationException;
import com.cesarhotel.roomster.mapper.CompteurEmployeMapper;
import com.cesarhotel.roomster.model.CompteurEmploye;
import com.cesarhotel.roomster.model.DemandeAbsence;
import com.cesarhotel.roomster.model.Employe;
import com.cesarhotel.roomster.model.MouvementCompteur;
import com.cesarhotel.roomster.model.StatutDemande;
import com.cesarhotel.roomster.model.TypeAbsence;
import com.cesarhotel.roomster.repository.CompteurEmployeRepository;
import com.cesarhotel.roomster.repository.DemandeAbsenceRepository;
import com.cesarhotel.roomster.repository.EmployeRepository;
import com.cesarhotel.roomster.repository.MouvementCompteurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compteurs de congés (F5). Un compteur par employé et par période de référence (juin → mai).
 * Toute modification d'un solde passe par ce service et laisse une trace (MouvementCompteur).
 */
@Service
public class CompteurEmployeService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter NOM_DU_MOIS = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    private final CompteurEmployeRepository compteurEmployeRepository;
    private final EmployeRepository employeRepository;
    private final MouvementCompteurRepository mouvementCompteurRepository;
    private final DemandeAbsenceRepository demandeAbsenceRepository;
    private final CompteurEmployeMapper compteurEmployeMapper;

    public CompteurEmployeService(CompteurEmployeRepository compteurEmployeRepository, EmployeRepository employeRepository,
                                  MouvementCompteurRepository mouvementCompteurRepository,
                                  DemandeAbsenceRepository demandeAbsenceRepository,
                                  CompteurEmployeMapper compteurEmployeMapper) {
        this.compteurEmployeRepository = compteurEmployeRepository;
        this.employeRepository = employeRepository;
        this.mouvementCompteurRepository = mouvementCompteurRepository;
        this.demandeAbsenceRepository = demandeAbsenceRepository;
        this.compteurEmployeMapper = compteurEmployeMapper;
    }

    // --- Lecture ---

    /**
     * Une ligne par employé : le compteur de la période en cours et, si disponible, celui de la période précédente.
     */
    public List<CompteurEmployeDto> getCompteursAvecHistoriqueN1() {
        List<CompteurEmploye> compteurs = compteurEmployeRepository.findAllByOrderByAnneeDesc();
        compteurs.sort(Comparator.comparing((CompteurEmploye c) -> c.getEmploye().getUser().getNom())
                .thenComparing(c -> c.getEmploye().getUser().getPrenom()));
        return avecHistoriqueN1(compteurs);
    }

    /**
     * Mêmes lignes, mais restreintes aux compteurs de l'employé associé à cet email.
     */
    public List<CompteurEmployeDto> getMesCompteurs(String email) {
        Employe employe = employeRepository.findByUserEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable"));
        List<CompteurEmploye> compteurs = compteurEmployeRepository.findByEmployeOrderByAnneeDesc(employe);
        return avecHistoriqueN1(compteurs);
    }

    public List<MouvementCompteur> getMesMouvements(String email) {
        Employe employe = employeRepository.findByUserEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable"));
        return mouvementCompteurRepository.findByCompteurEmployeOrderByDateDesc(employe);
    }

    public List<MouvementCompteur> getDerniersMouvements() {
        return mouvementCompteurRepository.findTop30ByOrderByDateDesc();
    }

    // --- Saisie manuelle ---

    /**
     * Crée le compteur de la période s'il n'existe pas, puis fixe ses soldes.
     * Le mouvement enregistré contient la différence avec les anciens soldes.
     *
     * @param periode  année où commence la période de référence (2026 = juin 2026 → mai 2027)
     * @param soldeRtt null quand l'hôtel n'accorde pas de RTT : le solde RTT n'est pas modifié
     */
    @Transactional
    public void initialiser(Long employeId, int periode, double soldeCongesPayes, Double soldeRtt, String auteur) {
        Employe employe = employeRepository.findById(employeId)
                .orElseThrow(() -> new ValidationException("Employé introuvable"));

        CompteurEmploye compteur = getOuCreerCompteur(employe, periode);
        if (soldeRtt == null) {
            soldeRtt = compteur.getSoldeRtt();
        }
        double differenceCp = soldeCongesPayes - compteur.getSoldeCongesPayes();
        double differenceRtt = soldeRtt - compteur.getSoldeRtt();
        compteur.definirSoldes(soldeCongesPayes, soldeRtt);
        compteurEmployeRepository.save(compteur);

        enregistrerMouvement(compteur, "Saisie manuelle des soldes (" + libellePeriode(periode) + ")",
                differenceCp, differenceRtt, Duration.ZERO, null, null, auteur);
    }

    // --- Acquisition mensuelle des congés payés ---

    /**
     * Crédite à chaque employé les CP acquis pendant un mois terminé (voir CalculConges.joursAcquis) :
     * 2,5 jours pour un mois complet, 2 jours pour un mois d'arrêt maladie, rien pour un congé sans solde.
     * Un même mois ne peut pas être crédité deux fois à un employé.
     *
     * @return le nombre d'employés crédités
     */
    @Transactional
    public int acquerirConges(LocalDate jourDuMois, String auteur) {
        LocalDate premierJour = jourDuMois.withDayOfMonth(1);
        LocalDate dernierJour = premierJour.plusMonths(1).minusDays(1);
        if (!dernierJour.isBefore(LocalDate.now())) {
            throw new ValidationException("Le mois n'est pas terminé");
        }

        int periode = CalculConges.periodeDe(premierJour);
        int employesCredites = 0;

        for (Employe employe : employeRepository.findAll()) {
            // Employé pas encore arrivé ou déjà parti ce mois-là : rien à acquérir
            boolean presentCeMois = !employe.getDateEntree().isAfter(dernierJour)
                    && (employe.getDateSortie() == null || !employe.getDateSortie().isBefore(premierJour));
            if (!presentCeMois) {
                continue;
            }

            CompteurEmploye compteur = getOuCreerCompteur(employe, periode);
            if (mouvementCompteurRepository.existsByCompteurAndMois(compteur, premierJour)) {
                continue; // déjà crédité
            }

            List<DemandeAbsence> absencesValidees = demandeAbsenceRepository.findByEmployeAndStatutIn(
                    employe, List.of(StatutDemande.VALIDEE));
            double jours = CalculConges.joursAcquis(premierJour, employe.getDateEntree(), employe.getDateSortie(),
                    absencesValidees);

            compteur.crediterCongesPayes(jours);
            compteurEmployeRepository.save(compteur);
            enregistrerMouvement(compteur, "CP acquis en " + premierJour.format(NOM_DU_MOIS),
                    jours, 0, Duration.ZERO, null, premierJour, auteur);
            employesCredites++;
        }
        return employesCredites;
    }

    /**
     * Un arrêt maladie ou un congé sans solde validé APRÈS l'acquisition d'un mois change les jours acquis ce mois-là
     * (2 jours au lieu de 2,5 pour un mois de maladie, 0 pour un sans solde). On recalcule chaque mois touché
     * qui a déjà été crédité et on enregistre la différence. Un mois pas encore crédité sera calculé juste plus tard.
     */
    @Transactional
    public void regulariserAcquisitions(Employe employe, LocalDate debut, LocalDate fin, String auteur) {
        LocalDate mois = debut.withDayOfMonth(1);
        while (!mois.isAfter(fin)) {
            regulariserAcquisition(employe, mois, auteur);
            mois = mois.plusMonths(1);
        }
    }

    private void regulariserAcquisition(Employe employe, LocalDate premierJourDuMois, String auteur) {
        CompteurEmploye compteur = compteurEmployeRepository
                .findByEmployeAndAnnee(employe, CalculConges.periodeDe(premierJourDuMois)).orElse(null);
        if (compteur == null) {
            return;
        }
        List<MouvementCompteur> dejaCredites = mouvementCompteurRepository.findByCompteurAndMois(compteur, premierJourDuMois);
        if (dejaCredites.isEmpty()) {
            return; // mois pas encore acquis : il sera calculé avec la bonne absence
        }

        double credite = 0;
        for (MouvementCompteur mouvement : dejaCredites) {
            credite += mouvement.getJoursCongesPayes();
        }
        List<DemandeAbsence> absencesValidees = demandeAbsenceRepository.findByEmployeAndStatutIn(
                employe, List.of(StatutDemande.VALIDEE));
        double attendu = CalculConges.joursAcquis(premierJourDuMois, employe.getDateEntree(), employe.getDateSortie(),
                absencesValidees);
        double difference = Math.round((attendu - credite) * 100) / 100.0;
        if (difference == 0) {
            return;
        }

        compteur.crediterCongesPayes(difference); // négatif : on retire des jours
        compteurEmployeRepository.save(compteur);
        enregistrerMouvement(compteur, "Régularisation des CP acquis en " + premierJourDuMois.format(NOM_DU_MOIS)
                        + " (absence déclarée après coup)",
                difference, 0, Duration.ZERO, null, premierJourDuMois, auteur);
    }

    // --- Ouverture de la période de référence (1er juin) ---

    /**
     * Ouvre la période de référence en cours pour chaque employé actif : crée son compteur s'il n'existe pas
     * et y REPORTE les soldes restants de la période précédente (CP, RTT, heures sup).
     * Rien n'est jamais supprimé automatiquement : la perte des CP non pris dépend de la situation
     * (le salarié a-t-il été mis en mesure de les prendre ?), c'est au manager d'en décider.
     * Fait une seule fois par compteur (reportEffectue).
     *
     * @return le nombre de compteurs ouverts
     */
    @Transactional
    public int ouvrirPeriode(String auteur) {
        int periode = CalculConges.periodeDe(LocalDate.now());
        int ouverts = 0;

        for (Employe employe : employeRepository.findByActif(true)) {
            CompteurEmploye nouveau = getOuCreerCompteur(employe, periode);
            if (nouveau.isReportEffectue()) {
                continue;
            }

            CompteurEmploye ancien = compteurEmployeRepository.findByEmployeAndAnnee(employe, periode - 1).orElse(null);
            if (ancien != null) {
                double cp = ancien.getSoldeCongesPayes();
                double rtt = ancien.getSoldeRtt();
                Duration heuresSup = ancien.getHeuresSupCumulees();

                if (cp != 0 || rtt != 0 || !heuresSup.isZero()) {
                    ancien.solder();
                    compteurEmployeRepository.save(ancien);
                    enregistrerMouvement(ancien, "Soldes reportés sur la période " + libellePeriode(periode),
                            -cp, -rtt, heuresSup.negated(), null, null, auteur);

                    nouveau.crediterCongesPayes(cp);
                    nouveau.crediterRtt(rtt);
                    nouveau.ajouterHeuresSup(heuresSup);
                    enregistrerMouvement(nouveau, "Report des soldes de la période " + libellePeriode(periode - 1),
                            cp, rtt, heuresSup, null, null, auteur);
                }
            }

            nouveau.marquerReportEffectue();
            compteurEmployeRepository.save(nouveau);
            ouverts++;
        }
        return ouverts;
    }

    // --- Demandes d'absence (F4) ---

    /** À la soumission : refuse la demande si le solde est déjà insuffisant. Rien n'est débité ici. */
    public void verifierSolde(DemandeAbsence demande) {
        if (!debiteUnCompteur(demande.getType())) {
            return;
        }
        CompteurEmploye compteur = getCompteur(demande);
        double solde = demande.getType() == TypeAbsence.CONGE_PAYE ? compteur.getSoldeCongesPayes() : compteur.getSoldeRtt();
        if (demande.nombreDeJours() > solde) {
            throw new ValidationException("Solde insuffisant : %d jours ouvrables demandés, %.1f disponibles"
                    .formatted(demande.nombreDeJours(), solde));
        }
    }

    /** À la validation : débite définitivement le compteur (CP ou RTT uniquement). */
    @Transactional
    public void debiterPourDemande(DemandeAbsence demande, String auteur) {
        if (!debiteUnCompteur(demande.getType())) {
            return;
        }
        CompteurEmploye compteur = getCompteur(demande);
        double jours = demande.nombreDeJours();
        if (demande.getType() == TypeAbsence.CONGE_PAYE) {
            compteur.debiterCongesPayes(jours);  // refuse si le solde devient négatif
            enregistrerMouvement(compteur, libelle(demande, "validé"), -jours, 0, Duration.ZERO, null, null, auteur);
        } else {
            compteur.debiterRtt(jours);
            enregistrerMouvement(compteur, libelle(demande, "validé"), 0, -jours, Duration.ZERO, null, null, auteur);
        }
        compteurEmployeRepository.save(compteur);
    }

    /** Annulation d'une demande déjà validée : on rend les jours. */
    @Transactional
    public void crediterPourDemande(DemandeAbsence demande, String auteur) {
        if (!debiteUnCompteur(demande.getType())) {
            return;
        }
        CompteurEmploye compteur = getCompteur(demande);
        double jours = demande.nombreDeJours();
        if (demande.getType() == TypeAbsence.CONGE_PAYE) {
            compteur.crediterCongesPayes(jours);
            enregistrerMouvement(compteur, libelle(demande, "annulé"), jours, 0, Duration.ZERO, null, null, auteur);
        } else {
            compteur.crediterRtt(jours);
            enregistrerMouvement(compteur, libelle(demande, "annulé"), 0, jours, Duration.ZERO, null, null, auteur);
        }
        compteurEmployeRepository.save(compteur);
    }

    /** Arrêt maladie pendant un congé payé : les jours de CP concernés sont rendus (reportés). */
    @Transactional
    public void rendreCongesPendantMaladie(DemandeAbsence conge, long jours, String auteur) {
        CompteurEmploye compteur = getCompteur(conge);
        compteur.crediterCongesPayes(jours);
        compteurEmployeRepository.save(compteur);
        enregistrerMouvement(compteur, "Arrêt maladie pendant le " + libelle(conge, "") + ": " + jours + " j rendus",
                jours, 0, Duration.ZERO, null, null, auteur);
    }

    // --- Heures sup (F3 → F5) ---

    public boolean heuresSupDejaReportees(Employe employe, LocalDate lundi) {
        return compteurEmployeRepository.findByEmployeAndAnnee(employe, CalculConges.periodeDe(lundi))
                .map(compteur -> mouvementCompteurRepository.existsByCompteurAndSemaine(compteur, lundi))
                .orElse(false);
    }

    /** Ajoute les heures sup d'une semaine au cumul. Une semaine ne peut être reportée qu'une fois. */
    @Transactional
    public void reporterHeuresSup(Employe employe, LocalDate lundi, Duration heuresSup, String auteur) {
        CompteurEmploye compteur = getOuCreerCompteur(employe, CalculConges.periodeDe(lundi));
        if (mouvementCompteurRepository.existsByCompteurAndSemaine(compteur, lundi)) {
            throw new ValidationException("Les heures sup de cette semaine ont déjà été reportées");
        }

        compteur.ajouterHeuresSup(heuresSup);
        compteurEmployeRepository.save(compteur);
        String libelle = "Heures sup de la semaine du " + lundi.format(DATE) + " : +" + CalculHeures.formater(heuresSup);
        enregistrerMouvement(compteur, libelle, 0, 0, heuresSup, lundi, null, auteur);
    }

    /**
     * Un pointage corrigé dans une semaine déjà reportée change ses heures sup : on enregistre la différence
     * entre les heures sup recalculées et celles déjà reportées (report + régularisations précédentes).
     */
    @Transactional
    public void regulariserHeuresSup(Employe employe, LocalDate lundi, Duration heuresSupRecalculees, String auteur) {
        CompteurEmploye compteur = compteurEmployeRepository
                .findByEmployeAndAnnee(employe, CalculConges.periodeDe(lundi)).orElse(null);
        if (compteur == null) {
            return;
        }
        List<MouvementCompteur> dejaReportes = mouvementCompteurRepository.findByCompteurAndSemaine(compteur, lundi);
        if (dejaReportes.isEmpty()) {
            return; // semaine pas encore reportée : elle le sera avec les bonnes heures
        }

        Duration reporte = Duration.ZERO;
        for (MouvementCompteur mouvement : dejaReportes) {
            reporte = reporte.plus(mouvement.getHeuresSup());
        }
        Duration difference = heuresSupRecalculees.minus(reporte);
        if (difference.isZero()) {
            return;
        }

        compteur.ajouterHeuresSup(difference); // négatif : on retire des heures
        compteurEmployeRepository.save(compteur);
        String signe = difference.isNegative() ? "" : "+";
        enregistrerMouvement(compteur, "Régularisation des heures sup de la semaine du " + lundi.format(DATE)
                        + " (pointage corrigé) : " + signe + CalculHeures.formater(difference),
                0, 0, difference, lundi, null, auteur);
    }

    // --- Méthodes internes ---

    /** Seuls les congés payés et les RTT utilisent un compteur. */
    private boolean debiteUnCompteur(TypeAbsence type) {
        return type == TypeAbsence.CONGE_PAYE || type == TypeAbsence.RTT;
    }

    /** Le compteur utilisé est celui de la période de référence du premier jour d'absence. */
    private CompteurEmploye getCompteur(DemandeAbsence demande) {
        int periode = CalculConges.periodeDe(demande.getDebut());
        return compteurEmployeRepository.findByEmployeAndAnnee(demande.getEmploye(), periode)
                .orElseThrow(() -> new ValidationException(
                        "Aucun compteur pour la période " + libellePeriode(periode) + " : le manager doit d'abord l'initialiser"));
    }

    private CompteurEmploye getOuCreerCompteur(Employe employe, int periode) {
        return compteurEmployeRepository.findByEmployeAndAnnee(employe, periode)
                .orElseGet(() -> compteurEmployeRepository.save(new CompteurEmploye(employe, periode)));
    }

    /** Ex : 2026 → "juin 2026 → mai 2027". */
    private String libellePeriode(int periode) {
        return "juin " + periode + " → mai " + (periode + 1);
    }

    private String libelle(DemandeAbsence demande, String action) {
        return demande.getType().getLibelle() + " " + demande.getPeriode() + " " + action;
    }

    private void enregistrerMouvement(CompteurEmploye compteur, String libelle, double joursCp, double joursRtt,
                                      Duration heuresSup, LocalDate semaine, LocalDate mois, String auteur) {
        mouvementCompteurRepository.save(
                new MouvementCompteur(compteur, libelle, joursCp, joursRtt, heuresSup, semaine, mois, auteur));
    }

    private List<CompteurEmployeDto> avecHistoriqueN1(List<CompteurEmploye> compteurs) {
        int periodeCourante = CalculConges.periodeDe(LocalDate.now());

        Map<Long, CompteurEmploye> comptesPeriodeCourante = new LinkedHashMap<>();
        Map<Long, CompteurEmploye> comptesPeriodePrecedente = new HashMap<>();
        for (CompteurEmploye c : compteurs) {
            if (c.getAnnee() == periodeCourante) {
                comptesPeriodeCourante.put(c.getEmploye().getId(), c);
            } else if (c.getAnnee() == periodeCourante - 1) {
                comptesPeriodePrecedente.put(c.getEmploye().getId(), c);
            }
        }
        return comptesPeriodeCourante.values().stream()
                .map(c -> {
                    CompteurEmployeDto dto = compteurEmployeMapper.toDto(c);
                    CompteurEmploye n1 = comptesPeriodePrecedente.get(c.getEmploye().getId());
                    if (n1 != null) {
                        dto.setSoldeCongesPayesN1(n1.getSoldeCongesPayes());
                        dto.setSoldeRttN1(n1.getSoldeRtt());
                        dto.setHeuresSupCumuleesN1(n1.getHeuresSupCumulees());
                    }
                    return dto;
                })
                .toList();
    }
}
