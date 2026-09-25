# Tempo — Trame fonctionnelle

Gestion du personnel pour un hôtel : heures, pointages, absences, compteurs.
Chaque fonctionnalité est décrite avec son comportement attendu, ses règles et les points à ne pas oublier.

## État d'avancement (audité sur le code au 2026-09-25)

| # | Fonctionnalité | Statut |
|---|---|---|
| F1 | Gestion des employés | ✅ Terminé *(finalisé le 15/08)* |
| F2 | Pointage (badge entrée/sortie) | ✅ Terminé *(correction manager le 23/08 ; vue semaine, traçabilité des corrections, **12h maximum avec sortie automatique à valider** le 25/09)* |
| F3 | Calcul des heures | ✅ Terminé le 25/09 *(vue semaine, récap mensuel, conformité HCR complète : heures sup dès la 36e heure par tranches, heures complémentaires, travail de nuit, jours fériés, durées maximales)* |
| F4 | Demandes d'absence | ✅ Terminé le 25/09 *(`/mes-demandes` côté employé, `/demandes` côté manager ; CP/RTT en jours ouvrables hors fériés ; maladie pendant un congé)* |
| F5 | Compteurs et soldes | ✅ Terminé le 25/09 *(période juin → mai, **mises à jour automatiques** : ouverture de période avec report, acquisition mensuelle des CP, report des heures sup, régularisations ; historique `MouvementCompteur`)* |
| F6 | Tableau de bord manager | ✅ Terminé le 25/09 *(`/tableau-de-bord`, page d'accueil du manager, alertes de conformité HCR)* |
| F7 | Authentification et rôles | ✅ Terminé *(le 25/09 : compte révoqué = employé désactivé, qui ne peut plus se connecter)* |
| F9 | Planning | ✅ Terminé le 25/09 *(`/mon-planning` pour les salariés, `/planning` pour le manager, contrôles HCR avant le travail)* |
| F10 | Exports comptables | ✅ Terminé le 25/09 *(`/exports` : 4 fichiers CSV par mois, lisibles directement dans Excel)* |
| F11 | Paramètres de l'hôtel | ✅ Terminé le 25/09 *(`/parametres` : RTT activés ou non, **non** par défaut conformément à la convention HCR)* |

**Toute la v1 est couverte, y compris la conformité à la convention HCR** (section "Conformité HCR" en bas). Restent les limites v1 assumées, listées dans les "Points à penser" de chaque section, et la section F8 (hors périmètre v1).

**Pour refaire l'application dans un autre langage (ex : PHP/Symfony)** : voir `tempo-specification-technique.md`. Elle contient le modèle de données, les routes, les algorithmes en pseudo-code, les messages exacts, les jeux de tests et la correspondance Spring → Symfony.

**Principe de code retenu le 25/09 : la simplicité pour la compréhension.**
- Les calculs sont des fonctions pures, sans base de données, écrites avec des boucles `for` : `CalculHeures`, `CalculConges`, `JoursFeries`, `ControlesHCR`, et les méthodes de `CreneauPlanning`. Elles sont couvertes par 61 tests unitaires (avec `Csv`).
- Les services contiennent les règles métier, vérifiées une par une avec un message clair.
- Les pages sont des formulaires HTML classiques : POST, puis redirection avec un message succès/erreur (`fragments/messages.html`). Pas de JavaScript pour les nouvelles pages.
- Une erreur métier est une `ValidationException(message)`. Les entités (`DemandeAbsence`, `CompteurEmploye`) la lèvent aussi, ce qui permet aux contrôleurs de n'attraper qu'un seul type d'exception.

---

## F1 — Gestion des employés

**Statut : ✅ Terminé** *(finalisé le 15/08)*

**Comportement attendu**
- [x] Créer, consulter, modifier une fiche employé (nom, prénom, email, poste, type de contrat, date d'entrée, durée hebdo contractuelle). *25/09 : date de naissance facultative, qui sert aux règles des moins de 18 ans. Nouveau poste `VEILLEUR_NUIT`, dont la durée maximale est de 12h par jour.*
- [x] Désactiver un employé qui quitte l'hôtel (date de sortie + actif = false) plutôt que le supprimer. *`toggle-actif-employe` renseigne `dateSortie` à la désactivation et la remet à `null` à la réactivation. Pas de suppression physique (bouton et endpoint retirés le 15/08).*
- [x] Lister les employés actifs, filtrer par poste. *`GET /liste-employe?actif=…&poste=…`, filtré côté serveur.*

**Règles métier**
- [x] L'email est unique : c'est l'identifiant de connexion. *Contrainte unique en base, revérifiée à la modification (`existsByUserEmailAndIdNot`, 400 sinon).*
- [x] Un employé désactivé ne peut plus pointer ni soumettre de demande. *Pointage : 403 dans `PointageService.pointer()`. Demande : refus dans `DemandeAbsenceService` (25/09). Depuis le 25/09, il ne peut même plus se connecter (cf. F7).*
- [x] La date de sortie ne peut pas précéder la date d'entrée. *Validée dans `toggleActifEmploye` et `editionEmploye` (400 sinon).*

**Points à penser**
- Ne jamais supprimer physiquement un employé : ses pointages et absences passés doivent rester consultables (historique légal de paie). ✅ respecté.
- Modifier la durée hebdo contractuelle en cours d'année : la nouvelle valeur s'applique à **toutes** les semaines affichées, passées comprises (le calcul lit la valeur actuelle de la fiche). Limite v1 assumée. Les heures sup déjà reportées au compteur ne bougent pas.
- Prévoir un champ "manager" plus tard pour savoir qui valide les demandes de qui (v1 : tout ADMIN valide tout).

---

## F2 — Pointage (badge entrée/sortie)

**Statut : ✅ Terminé**

**Comportement attendu**
- [x] L'employé "pointe" : si aucun pointage ouvert → création avec l'heure d'entrée ; si un pointage est ouvert → fermeture avec l'heure de sortie. *`PointageService.pointer()`.*
- [x] Un manager peut corriger un pointage (oubli de badge) avec un commentaire obligatoire. *`PATCH /pointer-employe/{id}/corriger`, bouton "Corriger" dans `/pointages`. Validations dans `PointageController.corriger()` : commentaire obligatoire, entrée obligatoire, sortie postérieure à l'entrée, **12h maximum** (400 sinon).*
- [x] Consultation des pointages du jour / de la semaine par employé. *25/09 : vue semaine (lundi → dimanche) avec navigation. Salarié : section "Ma semaine" sur `/mon-pointage`. Manager : `/pointages/semaine/{employeId}`, en cliquant sur le nom dans `/pointages` ou dans le récap mensuel. Affichage partagé : `fragments/semaine-pointages.html`.*

**Règles métier**
- [x] Plusieurs pointages par jour sont normaux (coupure midi/soir). *Couvert par le test `coupureMidiEtSoir`.*
- [x] Un pointage peut chevaucher minuit (service de nuit) : c'est une seule séquence. *Il compte pour le jour de son entrée.*
- [x] La sortie doit être postérieure à l'entrée. *Vraie par construction pour `pointer()`, validée pour la correction.*
- [x] **Un pointage dure 12h au maximum** (décision du 25/09). Au-delà, le salarié est **obligatoirement sorti** :
  - sans pointage de sortie, le pointage est **clôturé automatiquement à entrée + 12h** (tâche toutes les 5 minutes, et au démarrage) et marqué `sortieAutomatique` ;
  - s'il badge alors qu'un pointage de plus de 12h est encore ouvert, celui-ci est d'abord clôturé à entrée + 12h, puis ce badge compte comme une **nouvelle entrée** ;
  - une **alerte** apparaît au manager sur le tableau de bord (« Sorties automatiques à valider »). Elle y reste jusqu'à ce qu'il la **valide avec un commentaire obligatoire**, le lendemain ou plus tard. S'il faut changer l'heure réelle, il utilise « Corriger », ce qui fait aussi disparaître l'alerte ;
  - la validation est tracée comme une correction (« Sortie automatique validée : <commentaire> »).

  *Cette règle remplace l'ancienne détection des « pointages ouverts depuis plus de 14h », qui laissait un oubli ouvert indéfiniment. Un pointage refermé des semaines plus tard donnait par exemple 1509h42 d'un coup.*
- [x] Un seul pointage ouvert à la fois par employé. *25/09 : `pointer()` est `synchronized`, donc deux clics simultanés sont traités l'un après l'autre. Toujours pas de contrainte en base : suffisant tant que l'appli tourne sur un seul serveur.*

**Points à penser**
- ✅ *25/09* — **Oubli de pointage** :
  - pas de sortie : clôture automatique à 12h + alerte « à valider » (ci-dessus) ;
  - pas d'entrée : alertes du planning sur le tableau de bord (« Prévus mais pas pointés » aujourd'hui, « Prévus hier mais sans pointage »).

  Un pointage encore ouvert (moins de 12h) n'est pas compté dans les heures.
- ✅ *25/09* — Toute correction manuelle est tracée dans l'entité `CorrectionPointage` : auteur (email), date, entrée/sortie avant et après, motif. L'historique est affiché en bas de `/pointages/semaine/{id}`.
- Fuseau horaire : on reste en heure locale de l'hôtel (`LocalDateTime`). Limite v1 : lors du passage à l'heure d'hiver, une nuit 3h → 2h peut être comptée avec 1h d'écart.
- Arrondi : v1 à la minute, sans arrondi.

---

## F3 — Calcul des heures

**Statut : ✅ Terminé le 25/09**

**Comportement attendu**
- [x] Pour un employé et une semaine (lundi → dimanche) : total des heures travaillées, heures supplémentaires, heures de nuit. *En bas de la vue semaine (salarié et manager) : total, dont nuit, durée du contrat, heures sup par tranche, et heures complémentaires pour un temps partiel.*
- [x] Vue récapitulative mensuelle pour préparer la paie. *`/pointages/mois?mois=AAAA-MM-JJ` (ADMIN, lien "Récap mensuel (paie)" dans le menu) : une ligne par employé avec heures travaillées, dont nuit, heures sup +10/+20/+50 %, heures complémentaires +10/+25 %, pointages sans sortie à vérifier.*

**Règles métier**
- [x] ~~Heures sup = total travaillé − durée hebdo contractuelle~~ **corrigé le 25/09 (conformité HCR)**. La règle est maintenant la suivante :
  - **Heures sup = au-delà de 35h**, quel que soit le contrat. Pour un contrat de 39h, les heures 36 à 39 sont des heures sup "structurelles", déjà prévues et payées au contrat.
  - **Tranches de majoration HCR** : +10 % de la 36e à la 39e heure, +20 % de la 40e à la 43e, +50 % à partir de la 44e (`CalculHeures.decompter()` → `DecompteHeures`).
  - **Temps partiel** (contrat < 35h) : les heures entre le contrat et 35h sont des **heures complémentaires** (Code du travail), +10 % jusqu'au dixième du contrat et +25 % au-delà. Au-delà de 35h, ce sont des heures sup.
  - **Compteur "heures sup cumulées"** : seules les heures au-delà du contrat ET de 35h y sont reportées (`CalculHeures.heuresSupAuDelaDuContrat()`). Les heures structurelles sont déjà payées ; les heures complémentaires sont payées et ne vont pas au compteur.
- [x] Heures de nuit (convention HCR) : la portion de chaque séquence comprise entre 22h et 7h. *`CalculHeures.heuresDeNuit()` : 18h → 1h donne bien 3h (test `exempleDeLaTrame18hA1h`).*
- [x] Un pointage encore ouvert n'entre pas dans le calcul, et il est signalé comme anomalie.
- [x] Semaine à cheval sur deux mois. *Décision v1 : le total et les heures de nuit d'un pointage comptent dans le mois de sa date d'entrée. Les heures sup se calculent à la semaine, et une semaine compte entièrement dans le mois de son lundi.*
- [x] **Semaine coupée à minuit** (25/09) : pour les totaux de la semaine, un pointage est coupé aux bornes lundi 0h / lundi suivant 0h. Un service du dimanche 22h au lundi 6h compte 2h dans une semaine et 6h dans la suivante. L'affichage jour par jour garde le pointage entier, le jour de son entrée. *`CalculHeures.totalEntre()` / `totalNuitEntre()`.*
- [x] **Travail de nuit** (25/09) : un salarié est « travailleur de nuit » une semaine donnée s'il a au moins 2 jours avec 3h ou plus entre 22h et 7h. Il a droit à un repos compensateur de **1 % de ses heures de nuit**, affiché dans la vue semaine et le récap mensuel. *`ControlesHCR.estTravailleurDeNuit()` / `reposCompensateurNuit()`.*
- [x] **Jours fériés travaillés** (25/09) : les 11 jours fériés sont calculés pour chaque année (y compris Pâques, Ascension, Pentecôte) par `JoursFeries` ; ils apparaissent en badge dans la vue semaine. Un férié travaillé est signalé « Pour la paie » :
  - 1er mai : heures payées double ;
  - autre férié, avec au moins un an d'ancienneté : garanti HCR, à compenser (repos ou indemnité) ;
  - autre férié, moins d'un an d'ancienneté : pas de compensation garantie.

  Le récap mensuel a une colonne « Fériés travaillés ».
- [x] **Durées maximales et repos** (25/09) : ce sont des alertes « Convention HCR », affichées dans la vue semaine et sur le tableau de bord ; elles ne bloquent rien (`ControlesHCR`).
  - par jour : 11h pour un cuisinier, 12h pour un veilleur de nuit, 11h30 pour les autres ;
  - par semaine : 48h ;
  - repos entre deux journées : 11h ;
  - moins de 18 ans : 8h par jour, 35h par semaine, 12h de repos, pas de travail entre 23h30 et 6h ;
  - contingent annuel : 360h d'heures sup par année civile (tableau de bord).

**Où est le code**
- `service/CalculHeures.java` : fonctions pures (`duree`, `totalTravaille`, `totalNuit`, `heuresDeNuit`, `heuresDansPlage`, `totalEntre`, `totalNuitEntre`, `decompter`, `heuresSupAuDelaDuContrat`, `formater`). Elles ne lisent pas la base : elles reçoivent une liste de pointages.
- `service/ControlesHCR.java` : durées maximales, repos, travail de nuit, mineurs (fonctions pures).
- `service/JoursFeries.java` : les 11 jours fériés et la date de Pâques (fonctions pures).
- `service/HeuresService.java` : charge les pointages et prépare l'affichage (`getSemaine`, `getAlertes`, `getRecapMois`, `getHeuresSupAnnee`, `reporterHeuresSup`).
- `service/DecompteHeures.java` : le détail d'une semaine par taux de majoration.
- Tests : `CalculHeuresTest` (18), `ControlesHCRTest` (9), `JoursFeriesTest` (5), `CalculCongesTest` (10), `DemandeAbsenceTest` (6), `CreneauPlanningTest` (7), soit 55 tests. Dans `CalculHeuresTest` :
  - Durées : journée simple, pointage ouvert, coupures, semaine vide.
  - Heures de nuit : 18h → 1h, nuit à cheval sur minuit, début à 5h, séquence sur deux nuits.
  - Heures sup : moins de 35h, contrat 39h (4h à +10 %), tranches à 46h (4h + 4h + 3h), report au compteur.
  - Temps partiel : 20h de contrat pour 25h travaillées (2h à +10 %, 3h à +25 %), puis au-delà de 35h.
  - Formatage.

  - Semaine coupée à minuit, plage de nuit des mineurs.

  Lancer avec `mvnw test -Dtest='CalculHeuresTest,ControlesHCRTest,JoursFeriesTest,CalculCongesTest,DemandeAbsenceTest,CreneauPlanningTest'`.

**Points à penser**
- L'appli donne les **volumes d'heures par taux** (+10/+20/+50 %, +10/+25 %). Le calcul des montants reste à la paie. C'est rappelé en bas du récap mensuel.

---

## F4 — Demandes d'absence (congés, RTT, maladie)

**Statut : ✅ Terminé le 25/09** *(`DemandeAbsenceRepository`, `DemandeAbsenceService`, `DemandeAbsenceController`, templates `absence/`)*

**Comportement attendu**
- [x] L'employé soumet une demande : type, date de début, date de fin. *Formulaire sur `/mes-demandes`.*
- [x] Il voit ses demandes avec leur statut, et peut annuler une demande soumise ou validée avant son début. *Bouton "Annuler" sur `/mes-demandes`, affiché seulement si `DemandeAbsence.isAnnulable()`. Un employé ne peut annuler que ses propres demandes : l'employé vient toujours de `Authentication`.*
- [x] Le manager voit les demandes en attente et valide ou refuse, avec un motif obligatoire en cas de refus. *`/demandes` : en attente (Valider / Refuser + motif) et 20 dernières demandes traitées.*

**Règles métier**
- [x] Cycle de vie : SOUMISE → VALIDÉE ou REFUSÉE ; SOUMISE ou VALIDÉE → ANNULÉE. *Méthodes `valider()`/`refuser()`/`annuler()` de l'entité, désormais appelées par le service.*
- [x] À la soumission d'un CP/RTT : vérifier que le solde est suffisant. *`CompteurEmployeService.verifierSolde()`.*
- [x] Interdire le chevauchement avec une autre demande soumise ou validée du même employé. *`chevauche()` est appelée dans `DemandeAbsenceService.verifierRegles()`.* **Exception (25/09, jurisprudence)** : un arrêt maladie peut chevaucher un congé payé validé et déjà commencé. À la validation de l'arrêt, les jours ouvrables de CP qui tombent pendant l'arrêt sont rendus au salarié (mouvement « Arrêt maladie pendant le congé payé… : n j rendus »). Un congé pas encore commencé, lui, peut simplement être annulé.
- [x] La maladie ne débite aucun compteur et peut être saisie a posteriori par le manager. *Formulaire "Déclarer un arrêt maladie" sur `/demandes` : la demande est créée et validée directement. Un employé peut aussi déclarer une maladie passée.*
- [x] Annulation d'une demande validée : re-créditer le compteur. *`CompteurEmployeService.crediterPourDemande()`.*

**Décisions v1 (points à penser tranchés)**
- **RTT seulement si l'hôtel en accorde** (F11) : la convention HCR n'en prévoit pas. Réglage « non » par défaut : le type RTT n'est pas proposé et une demande de RTT est refusée (« L'hôtel n'accorde pas de RTT »).
- **Débit à la validation seulement.** Le solde est vérifié à la soumission, mais pas réservé. Deux demandes soumises peuvent donc ensemble dépasser le solde : la seconde validation sera alors refusée avec "Solde CP insuffisant".
- **Jours ouvrables pour les CP et RTT** (conformité HCR, 25/09) : `DemandeAbsence.nombreDeJours()` → `CalculConges.joursOuvrables()` compte du lundi au samedi, **sans le dimanche ni les jours fériés**. Une semaine complète coûte 6 jours, et 5 si elle contient un férié (ex : semaine du 14 juillet). Une demande qui ne contient aucun jour ouvrable est refusée. Les autres types (maladie, sans solde…) restent en jours calendaires, car ils ne débitent aucun compteur.
- **Demande qui commence dans le passé** : autorisée seulement pour la maladie.
- **Compteur débité** : celui de la **période de référence** (juin → mai) du premier jour d'absence. Une demande du 25/05 au 05/06 débite donc entièrement la période qui se termine le 31/05.
- **Notification du manager** : pastille avec le nombre de demandes en attente dans le menu ("Demandes d'absence") et bloc dédié sur le tableau de bord. Pas d'email (F8).

---

## F5 — Compteurs et soldes

**Statut : ✅ Terminé le 25/09**

**Comportement attendu**
- [x] Chaque employé voit ses soldes : congés payés, RTT, heures sup cumulées. *`/mes-conges`, avec l'historique de ses mouvements. Les heures sup sont affichées lisiblement ("7h15" au lieu de "PT7H15M").*
- [x] Le manager voit les compteurs de toute l'équipe. *`/conge-paye` (ADMIN), avec les 30 derniers mouvements.*
- [x] Initialisation des compteurs en début de période (saisie manuelle des soldes de départ). *Formulaire en haut de `/conge-paye` → `POST /compteurs/initialiser` : crée le compteur de la période s'il n'existe pas, sinon remplace ses soldes. La différence est enregistrée comme mouvement.*
- [x] **Acquisition mensuelle des CP** (25/09). Formulaire « Acquisition des CP du mois » sur `/conge-paye` → `POST /compteurs/acquisition` (mois terminé seulement). Chaque employé présent ce mois-là est crédité au prorata des jours du mois :
  - **2,5 jours** par mois travaillé (les CP, RTT, récupérations comptent comme du travail) ;
  - **2 jours** par mois d'arrêt maladie (loi du 22 avril 2024) ;
  - **rien** pendant un congé sans solde ;
  - arrivée ou départ en cours de mois : au prorata.

  Un mois déjà crédité ne l'est pas une deuxième fois (`MouvementCompteur.mois`). Le compteur de la période est créé s'il n'existe pas. *`CalculConges.joursAcquis()`.* **Depuis le 25/09, c'est automatique** (voir « Mises à jour automatiques ») ; le bouton sert seulement à relancer un mois.

**Règles métier**
- [x] Un compteur par employé et par période de référence. *Contrainte unique `employe_id` + `annee`. Depuis le 25/09, `annee` désigne **l'année où commence la période** : 2026 = du 1er juin 2026 au 31 mai 2027 (`CalculConges.periodeDe()`). Les pages affichent « juin 2026 → mai 2027 ».*
- [x] Le solde ne peut pas devenir négatif. *`debiterCongesPayes`/`debiterRtt` refusent un débit trop grand, et `definirSoldes` refuse une saisie négative.*
- [x] Les heures sup s'accumulent semaine après semaine. *Report **automatique** 7 jours après la fin de la semaine (voir ci-dessous). Sur `/pointages/semaine/{id}`, le bouton « Reporter les heures sup au compteur » permet de le faire tout de suite ; il apparaît quand la semaine est terminée, sans pointage ouvert, avec des heures sup. Une semaine n'est reportée qu'une fois (`MouvementCompteur.semaine`).*

**Mises à jour automatiques** (25/09, `TachesAutomatiques`)

Elles tournent **chaque nuit à 1h** et **au démarrage de l'application**, ce qui rattrape les nuits où le serveur était arrêté. Chaque étape peut être relancée sans risque : ce qui est déjà fait n'est jamais refait. Les mouvements créés ont pour auteur « automatique ».
1. **Ouverture de la période de référence** (effective à partir du 1er juin) : pour chaque employé actif, création du compteur de la nouvelle période et **report des soldes restants** de la période précédente (CP, RTT, heures sup). L'ancien compteur est mis à zéro, et chaque côté reçoit un mouvement. **Rien n'est jamais supprimé automatiquement** : la perte des CP non pris dépend de la situation (le salarié a-t-il été mis en mesure de les prendre ?), c'est au manager d'en décider. Fait une seule fois par compteur (`CompteurEmploye.reportEffectue`).
2. **Acquisition des CP du mois précédent** pour tous les employés présents ce mois-là (2,5 j / 2 j maladie / 0 sans solde, au prorata).
3. **Report des heures sup** des semaines terminées depuis au moins **7 jours** (le temps de corriger les pointages), sur les 5 dernières semaines possibles. Une semaine avec un pointage sans sortie attend sa correction.

**Régularisations automatiques**, faites au moment de l'action, car certaines informations arrivent après coup :
- **Absence déclarée après coup** : quand un arrêt maladie ou un congé sans solde est validé sur un mois **déjà crédité**, l'acquisition de ce mois est recalculée et la différence enregistrée (« Régularisation des CP acquis en août 2026 (absence déclarée après coup) »). Ex : 15 jours de maladie sur un mois de 31 jours → −0,24 j.
- **Pointage corrigé** : quand un pointage est corrigé dans une semaine **déjà reportée**, les heures sup de la semaine sont recalculées et la différence enregistrée (« Régularisation des heures sup de la semaine du 20/07/2026 (pointage corrigé) : −4h00 »). On regarde la semaine d'avant et la semaine d'après la correction.

**Points à penser**
- ✅ *25/09* — Historisation des mouvements : entité `MouvementCompteur`. Chaque mouvement enregistre la date, un libellé, la variation CP/RTT/heures sup, la semaine reportée et l'auteur. Tout changement de solde passe par `CompteurEmployeService` et crée un mouvement : validation, annulation, saisie manuelle, report d'heures sup.
- ✅ *25/09* — Période de référence juin → mai et acquisition de 2,5 j/mois : faites et **automatiques** (voir ci-dessus).
- Une régularisation peut rendre un solde légèrement négatif (ex : des CP déjà pris puis un arrêt maladie déclaré sur un mois déjà crédité). C'est volontaire : le solde reflète la réalité, et le mouvement explique pourquoi.
- Heures sup payées ou récupérées : v1 = simple cumul affiché, la décision reste humaine.
- **RTT** : jamais crédités automatiquement. Il n'existe pas de règle générale : tout dépend de l'accord d'entreprise. Les soldes sont saisis à la main, et seulement si le réglage « L'hôtel accorde des RTT » est activé (F11). Si l'hôtel a un accord, on pourra automatiser son calcul comme pour les CP, une fois sa règle connue.
- Prorata (arrivée en cours d'année, temps partiel) : saisie manuelle en v1, via le formulaire d'initialisation.

---

## F6 — Tableau de bord manager

**Statut : ✅ Terminé le 25/09** *(`TableauDeBordService`, `/tableau-de-bord`. C'est la page d'accueil de l'admin : `/` redirige un ADMIN vers le tableau de bord, les salariés gardent l'accueil. Depuis le planning (F9), il sait aussi qui **devait** être là.)*

**Comportement attendu**
- [x] Vue du jour. Chaque employé actif apparaît dans une seule des quatre colonnes :
  - **Présents** : pointage ouvert (il a forcément moins de 12h) ;
  - **Absents** : demande validée couvrant aujourd'hui ;
  - **Journée terminée** : a pointé aujourd'hui, pointage fermé ;
  - **Sans pointage** : aucune des trois situations précédentes.
- [x] Demandes en attente de validation, avec un lien vers `/demandes`.
- [x] **Planning du jour** (F9) :
  - « Prévus aujourd'hui au planning » ;
  - « Prévus mais pas pointés » : le créneau a commencé, sans pointage ni absence validée, donc un retard ou une absence imprévue.
- [x] Anomalies :
  - **Sorties automatiques à valider** : pointages clôturés à 12h faute de pointage de sortie. Chaque ligne a un champ commentaire (obligatoire) et un bouton « Valider », et un lien vers la semaine de l'employé pour corriger l'heure. L'alerte reste tant qu'elle n'est pas traitée ;
  - « Prévus hier mais sans pointage » : créneau au planning hier, ni pointage ni absence validée. *Cette règle remplace l'ancienne (« sans pointage ni absence hier, si c'était un jour de semaine »), qui listait aussi les jours de repos normaux.*
- [x] **Convention HCR** (25/09) : les alertes de durées maximales, de repos et de nuit des mineurs pour la semaine en cours et la précédente, et le dépassement du contingent annuel de 360h, pour chaque employé actif.

**Points à penser**
- ✅ *25/09* — Le planning (F9) indique maintenant qui *devrait* être là. La colonne « Sans pointage » (d'après les pointages) reste affichée : un employé sans créneau prévu ce jour-là est simplement en repos.

---

## F7 — Authentification et rôles

**Statut : ✅ Terminé**

**Comportement attendu**
- [x] Connexion par email + mot de passe. *Formulaire Spring Security (`/login`), mot de passe BCrypt. Comptes de démo dans `data.sql`, mot de passe `password123`.*
- [x] Rôle EMPLOYE : pointe, voit ses heures, ses soldes, gère ses demandes. *Implémenté sous le nom `SALARIE` (décision : on garde ce nom). Pointe → `/mon-pointage`. Ses heures → "Ma semaine" sur `/mon-pointage`. Ses soldes → `/mes-conges`. Ses demandes → `/mes-demandes`.*
- [x] Rôle MANAGER : tout voir, valider/refuser, corriger les pointages, gérer les fiches. *Implémenté sous le nom `ADMIN` (décision : on garde ce nom). Tout voir → `/tableau-de-bord`, `/pointages`, `/pointages/mois`, `/conge-paye`. Valider/refuser → `/demandes`. Corriger → `/pointages`. Fiches → `/liste-employe`, `/ajout-employe`.*

**Règles métier**
- [x] Un manager est aussi un employé : le rôle s'ajoute, il ne remplace pas. *Les pages personnelles sont ouvertes à tout utilisateur connecté, admin compris.*
- [x] Chaque page filtre par utilisateur connecté : un employé ne voit jamais les données d'un autre. *Les pages personnelles (`/mon-pointage`, `/mes-conges`, `/mes-demandes`) déduisent toujours l'employé de `Authentication`, jamais d'un id fourni par le client. Annuler une demande qui n'est pas la sienne est refusé ("Cette demande ne vous appartient pas"). Toutes les pages manager sont réservées à `ROLE_ADMIN` dans `SecurityConfig` ; un salarié reçoit 403.*

**Points à penser**
- Qui crée les comptes et réinitialise les mots de passe : le manager, avec un mot de passe temporaire affiché une fois (création, et `PATCH /reset-password-employe/{id}`). Pas d'email en v1.
- ✅ *25/09* — Lister / révoquer les comptes :
  - `/liste-employe` sert de liste des comptes (un compte par employé) ;
  - **révoquer un compte = désactiver l'employé** : `CustomUserDetailsService` marque le compte `disabled` si l'employé est inactif, et la connexion échoue (`/login?error`) ;
  - réactiver l'employé rétablit l'accès.
- CSRF est désactivé sur `/h2-console/**` (nécessaire pour la console) : **à retirer avant tout déploiement** au-delà du poste de dev.
- `/pointer-employe/{id}` (admin) accepte un id fourni par le client : c'est volontaire (pointeuse collective du manager), mais à garder en tête si la route est un jour ouverte à un rôle non-admin.
- Pas de flag `mustChangePassword` : un employé peut garder son mot de passe temporaire indéfiniment (choix v1 assumé).

---

## F9 — Planning

**Statut : ✅ Terminé le 25/09** *(`CreneauPlanning`, `PlanningService`, `PlanningController`, templates `planning/`)*

**Comportement attendu**
- [x] **Côté salarié** (`/mon-planning`) : la semaine jour par jour, avec navigation. Pour chaque jour :
  - ses horaires et la note du créneau, ou « Repos » ;
  - un badge si le jour est férié ou si le salarié est absent ;
  - « Travaillent aussi ce jour-là » : nom, poste et horaires des collègues ;
  - « Absents ce jour-là » : les collègues absents (absence validée), avec **seulement** « Congé payé », « Maladie », ou « Absent » pour les autres types (RTT, sans solde, récupération). Aucune autre information : ni dates, ni motif, ni demande en attente.

  En haut : le total prévu de la semaine et le contrat.
- [x] **Côté manager** (`/planning`), une interface simple et **très visuelle** qui donne le maximum d'informations : une grille **employés × 7 jours** pour la semaine choisie, avec une légende en haut.
  - **absences validées** en badge, **une couleur par type** : congé payé vert, maladie rouge, RTT bleu, récupération rose, sans solde gris ;
  - **demandes en attente** : même badge en **pointillé**, « (en attente) ». On peut quand même planifier, mais le manager est prévenu ;
  - **conflit** : case en rouge « ⚠ planifié pendant l'absence », quand un créneau existe sur un jour d'absence validée (ex : CP validé après la création du planning) ;
  - **créneaux de nuit** (qui finissent le lendemain) en bleu foncé ; la note du créneau est affichée en dessous ;
  - **aujourd'hui** : colonne surlignée, et pour chacun « ● présent » (pointage ouvert), « ✓ pointé » ou « ✗ pas pointé » (créneau commencé sans pointage) ;
  - **jours fériés** en violet dans l'en-tête ;
  - **effectif prévu** en bas de chaque colonne : nombre de personnes, heures prévues, répartition par poste (ex : « RECEPTION 1 · CUISINE 1 »), nombre d'absents ;
  - chaque case montre les créneaux ;
  - **+** dans une case : préremplit le formulaire « Ajouter un créneau » avec l'employé et le jour ;
  - **✕** sur un créneau : le supprime ;
  - **Copier la semaine précédente** : recopie tous les créneaux de la semaine d'avant, en ignorant ceux qui ne respectent pas les règles ;
  - colonne « Prévu / contrat » (ex : 32h00 / 35h00), en couleur : orange « sous le contrat », vert « conforme au contrat », bleu « heures sup prévues » ;
  - sous chaque employé, les **alertes HCR du planning**, affichées avant le travail.

  Pour modifier un créneau : on le supprime (✕) puis on l'ajoute à nouveau.

**Règles métier**
- [x] Un créneau = un employé, un jour, une heure de début, une heure de fin, une note facultative. **Si la fin est avant ou égale au début, le créneau se termine le lendemain** (ex : 22:00 → 06:00 = 8h).
- [x] Refusé si l'employé est désactivé.
- [x] Refusé si la fin est égale au début.
- [x] Refusé s'il dure plus de **12h** (même durée maximale qu'un pointage).
- [x] Refusé s'il chevauche un autre créneau du même employé. On regarde aussi la veille et le lendemain, pour les créneaux de nuit. Deux créneaux qui se touchent (14:00 puis 14:00) ne se chevauchent pas : c'est une coupure.
- [x] Refusé si l'employé a une **absence validée** ce jour-là.
- [x] **Contrôles HCR avant le travail** : les mêmes règles que sur les pointages (durée maximale par jour et par semaine, repos de 11h, mineurs), appliquées aux créneaux prévus. Chaque créneau est converti en « pointage prévu » (`CreneauPlanning.enPointagePrevu()`) et passe par `HeuresService.getAlertes()`, ce qui garantit les mêmes messages. Ces alertes ne bloquent pas l'enregistrement.
- [x] Tableau de bord : qui est prévu aujourd'hui, et qui est prévu mais n'a pas pointé (voir F6).

**Points à penser**
- Un salarié voit les horaires de ses collègues : c'est voulu (« qui travaille ce jour-là »), et seuls le nom, le poste et les horaires sont affichés.
- **Décision du 25/09** : les salariés voient les collègues en congé payé ou en maladie, sans aucun autre détail. Les autres types s'affichent « Absent ». Le manager, lui, voit tout : type en couleur, demandes en attente et conflits.
- Pas de notion de « semaine publiée » : un créneau ajouté est visible tout de suite par le salarié.
- Le planning est prévisionnel ; la paie reste calculée sur les **pointages**.

---

## F10 — Exports comptables

**Statut : ✅ Terminé le 25/09** *(`ExportService`, `ExportController`, `Csv`, page `exports.html`)*

**Comportement attendu**
- [x] Une page **« Exports comptables »** (menu Administration, `/exports`) : on choisit un mois (défaut : le mois précédent), puis on télécharge un des 4 fichiers.
- [x] Un bouton **« ⬇ Exporter pour le comptable (CSV) »** sur le récap mensuel exporte directement le mois affiché.
- [x] Les 4 fichiers :
  1. **Récap paie** : une ligne par employé. Colonnes : matricule, nom, prénom, poste, contrat, heures travaillées, dont nuit, HS +10 / +20 / +50 %, heures complémentaires +10 / +25 %, repos compensateur de nuit, fériés travaillés, jours de CP, RTT, maladie, sans solde et récup. férié **dans le mois**, pointages sans sortie. *Mêmes chiffres que l'écran « Récap mensuel » : les deux utilisent `HeuresService.calculerRecapMois()`.*
  2. **Détail des pointages** : chaque pointage du mois, trié par employé puis date. Colonnes : date, entrée, date et heure de sortie (« SANS SORTIE » si ouvert), durée, dont nuit, nom du férié, commentaire.
  3. **Absences** : les absences **validées** qui touchent le mois. Colonnes : type, du, au, jours au total, jours dans le mois, unité (jours ouvrables pour CP/RTT, calendaires sinon), validée par, validée le.
  4. **Compteurs** : les soldes CP, RTT et heures sup de la période de référence (juin → mai) qui contient le mois, **à la date du jour**.

**Règles de format** (pour qu'Excel en français ouvre le fichier sans manipulation)
- Séparateur `;`, fin de ligne `\r\n`, fichier en **UTF-8 avec BOM**. Sans BOM, Excel affiche mal les accents.
- Heures en **heures décimales** avec une virgule : 7h30 → `7,50`, 8h03 → `8,05`. Le comptable peut ainsi additionner.
- Nombres de jours de solde avec 2 décimales (`9,50`) ; dates `dd/MM/yyyy`, heures `HH:mm`.
- Une valeur qui contient `;`, `"` ou un retour à la ligne est mise entre guillemets, et ses guillemets sont doublés.
- Nom du fichier : `<type>-AAAA-MM.csv`, ex : `recap-paie-2026-09.csv`.
- Réservé au manager (`ROLE_ADMIN`) ; un salarié reçoit 403.

**Points à penser**
- Les soldes du fichier « Compteurs » sont ceux du jour de l'export, pas ceux de la fin du mois.
- Corriger les pointages sans sortie **avant** d'exporter : ils ne sont pas comptés dans les heures, et le récap les signale dans sa dernière colonne.

---

## F11 — Paramètres de l'hôtel

**Statut : ✅ Terminé le 25/09** *(`ParametresHotel`, `ParametresService`, `ParametresController`, page `parametres.html`)*

**Comportement attendu**
- [x] Une page **« Paramètres »** (menu Administration, `/parametres`, manager seulement), avec pour l'instant un réglage : **« L'hôtel accorde des RTT »**, une case à cocher.
- [x] **Décoché (valeur par défaut)**, conformément à la convention HCR qui ne prévoit pas de RTT :
  - le type « RTT » n'est plus proposé dans « Mes demandes d'absence », et une demande de RTT envoyée quand même est refusée : « L'hôtel n'accorde pas de RTT » ;
  - les soldes et colonnes RTT sont masqués : Mes congés, Congés (RH), formulaire de saisie des soldes, historique des mouvements, légende du planning ;
  - les exports n'ont plus de colonne RTT : « Jours RTT » disparaît du récap paie, « Solde RTT » des compteurs ;
  - les titres et textes ne parlent plus de RTT (« Compteurs congés payés », « Mes congés payés »…).
- [x] **Coché** : tout est affiché et proposé ; les soldes de RTT se saisissent à la main dans « Congés (RH) ».

**Règles**
- **Rien n'est effacé** en désactivant : les soldes, demandes et mouvements RTT existants restent en base et réapparaissent si on réactive.
- La saisie des soldes sans champ RTT (réglage décoché) **ne touche pas** le solde RTT existant.
- L'ouverture de période (1er juin) reporte aussi le solde RTT, même s'il est masqué, pour ne rien perdre.
- Une absence RTT déjà validée reste visible dans le planning (badge « RTT ») et vue « Absent » par les collègues.

**Points à penser**
- Le réglage est enregistré en base (table `parametres_hotel`, une seule ligne). Il n'est pas remis à zéro par les données de démo : il garde la valeur choisie d'un redémarrage à l'autre.
- D'autres réglages pourront s'ajouter ici : durée légale, seuil d'oubli de badge, etc.

---

## F8 — Plus tard (hors périmètre v1)

- Planning : modifier un créneau directement (aujourd'hui, on le supprime puis on le recrée), publier ou verrouiller une semaine, et contrôler le jour de repos hebdomadaire.
- Suivi du quota de jours fériés garantis (10 par an dont 6 garantis) : aujourd'hui, chaque férié travaillé est signalé, mais le quota n'est pas décompté.
- Exports : un format propre à un logiciel de paie précis (ex : Silae, Sage), ou un PDF par employé. Aujourd'hui : CSV génériques (F10).
- Notifications email (nouvelle demande, décision, mot de passe temporaire).
- Durée hebdo contractuelle historisée (valeur applicable à chaque semaine).
- Champ "manager" sur l'employé pour savoir qui valide les demandes de qui.

---

## Conformité HCR (convention IDCC 1979), revue le 25/09

À faire valider par l'expert-comptable ou le gestionnaire de paie avant d'utiliser l'appli pour la paie. Un accord d'entreprise peut modifier certains points.

**✅ Conforme**
- Plage de nuit de 22h à 7h.
- Heures sup calculées par semaine civile (lundi → dimanche), au-delà de 35h.
- Tranches de majoration +10 % / +20 % / +50 % (36e-39e, 40e-43e, 44e et +).
- Heures complémentaires pour un temps partiel (+10 % jusqu'au 1/10 du contrat, +25 % au-delà).
- CP et RTT décomptés en jours ouvrables.
- Calcul à la minute, sans arrondi.
- La maladie ne débite rien.
- Historique complet des pointages, des corrections et des mouvements de compteur.

**✅ Ajouté le 25/09 (les 8 points qui restaient ouverts)**
1. **Période de référence des CP** : du 1er juin au 31 mai (`CalculConges.periodeDe`).
2. **Durées maximales et repos**, sous forme d'alertes sur la vue semaine et le tableau de bord :
   - par jour : 11h pour un cuisinier, 12h pour un veilleur de nuit, 11h30 pour les autres ;
   - par semaine : 48h ;
   - repos entre deux journées : 11h ;
   - contingent annuel : 360h.
3. **Travail de nuit** : un travailleur de nuit (au moins 2 jours avec 3h ou plus entre 22h et 7h dans la semaine) a droit à 1 % de ses heures de nuit en repos compensateur.
4. **Jours fériés** : les 11 fériés sont calculés et ne sont pas décomptés des CP. Le 1er mai travaillé est signalé « payé double » ; un autre férié travaillé est « à compenser » si le salarié a au moins un an d'ancienneté. *Limite : le quota de 10 fériés dont 6 garantis n'est pas suivi (F8).*
5. **Maladie pendant des CP** : autorisée sur un congé validé et commencé ; les jours ouvrables concernés sont rendus.
6. **Acquisition de CP** : 2,5 j par mois, 2 j par mois d'arrêt maladie (loi de 2024), 0 pendant un sans solde.
7. **Salariés de moins de 18 ans** (date de naissance facultative sur la fiche) : 8h par jour, 35h par semaine, 12h de repos, pas de travail entre 23h30 et 6h (dérogation du secteur jusqu'à 23h30).
8. **Semaine coupée à minuit** : un pointage du dimanche soir au lundi matin compte dans les deux semaines.

**À faire valider en priorité par le gestionnaire de paie**
- La définition du travailleur de nuit est appliquée semaine par semaine, alors que le texte parle de l'horaire « habituel ».
- Le 1 % de repos compensateur de nuit.
- La plage de nuit interdite aux mineurs (23h30 → 6h, avec la dérogation HCR).
- Le contingent de 360h par année civile.

## Ordre de réalisation conseillé

1. F1 Employés — ✅ terminé le 15/08
2. F2 Pointage — ✅ terminé le 25/09 (correction le 23/08 ; vue semaine, traçabilité, oublis le 25/09)
3. F3 Calcul des heures — ✅ terminé le 25/09 (conformité HCR complète ; 55 tests unitaires en tout avec le planning)
4. F5 Compteurs — ✅ terminé le 25/09 (période juin → mai, acquisition, débit/crédit, heures sup, mouvements)
5. F4 Absences — ✅ terminé le 25/09 (jours ouvrables hors fériés, maladie pendant un congé)
6. F6 Tableau de bord — ✅ terminé le 25/09
7. F7 Sécurité — ✅ terminé (login/rôles le 11/08, mots de passe le 15/08, révocation et pages personnelles le 25/09)
8. F9 Planning — ✅ terminé le 25/09
9. F10 Exports comptables — ✅ terminé le 25/09
10. F11 Paramètres (RTT oui/non) — ✅ terminé le 25/09

## Plan des pages (v1)

| Page | Qui | Contenu |
|---|---|---|
| `/` | Tous | Accueil salarié ; un ADMIN est redirigé vers `/tableau-de-bord` |
| `/mon-pointage` | Tous | Pointer, ma semaine (heures, nuit, heures sup), mon historique |
| `/mon-planning` | Tous | Mes horaires de la semaine et, pour chaque jour, qui travaille avec moi |
| `/mes-conges` | Tous | Mes soldes CP/RTT/heures sup + historique de mes compteurs |
| `/mes-demandes` | Tous | Demander une absence, suivre et annuler mes demandes |
| `/mon-compte` | Tous | Changer mon mot de passe |
| `/tableau-de-bord` | ADMIN | Présents/absents du jour, prévus au planning et pas pointés, demandes en attente, alertes HCR, anomalies |
| `/planning` | ADMIN | Grille employés × jours : ajouter (+), supprimer (✕), copier la semaine précédente, heures prévues / contrat, alertes HCR |
| `/demandes` | ADMIN | Valider/refuser, déclarer un arrêt maladie, demandes traitées |
| `/liste-employe`, `/ajout-employe` | ADMIN | Fiches employés, activation/désactivation (= révocation du compte) |
| `/conge-paye` | ADMIN | Compteurs de l'équipe (par période juin → mai), saisie des soldes, acquisition mensuelle, derniers mouvements |
| `/pointages` | ADMIN | Pointeuse collective, historique, correction |
| `/pointages/semaine/{id}` | ADMIN | Semaine d'un employé (heures, tranches, nuit, fériés, alertes HCR), report des heures sup, corrections tracées |
| `/pointages/mois` | ADMIN | Récap mensuel pour la paie (tranches, heures complémentaires, repos de nuit, fériés travaillés) + bouton « Exporter pour le comptable » |
| `/exports` | ADMIN | Exports comptables : récap paie, pointages, absences, compteurs (CSV) |
| `/parametres` | ADMIN | Paramètres de l'hôtel : l'hôtel accorde-t-il des RTT ? |
