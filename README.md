# Roomster

Application de gestion du personnel pour un hôtel : pointages, calcul des heures, absences, compteurs de congés, planning, tableau de bord du manager et exports pour le comptable. Les règles suivent la **convention collective HCR** (hôtels, cafés, restaurants — IDCC 1979).

Spring Boot 4.1 · Java 21 · Spring Security · Hibernate / H2 · Thymeleaf · Tailwind CSS

## Sommaire

- [Prérequis](#prérequis)
- [Lancer le projet](#lancer-le-projet)
- [Comptes de démo](#comptes-de-démo)
- [Fonctionnalités](#fonctionnalités)
- [Règles métier principales](#règles-métier-principales)
- [Tâches automatiques](#tâches-automatiques)
- [Frontend / Tailwind CSS](#frontend--tailwind-css)
- [Base de données (H2)](#base-de-données-h2)
- [Structure du projet](#structure-du-projet)
- [Tests](#tests)
- [Documentation](#documentation)

## Prérequis

- JDK 21 ou plus récent.
- Rien d'autre à installer pour lancer l'appli : Maven est fourni via le wrapper (`mvnw` / `mvnw.cmd`), la base de données H2 est embarquée, et le CSS (Tailwind) est déjà compilé dans `static/css/app.css`.
- Node.js (facultatif) : seulement si tu modifies les styles Tailwind (voir [Frontend / Tailwind CSS](#frontend--tailwind-css)).

## Lancer le projet

```bash
./mvnw spring-boot:run
```

Ou depuis IntelliJ : ouvre `RoomsterApplication.java` (`src/main/java/com/cesarhotel/roomster/`) et clique sur ▶️.

L'application démarre sur **http://localhost:8099**. Le port est fixé dans `application.yaml` : le 8080 par défaut de Spring Boot reste libre, car il est souvent occupé par Apache ou Laragon.

Au démarrage, tout se fait automatiquement :
1. Hibernate crée ou adapte les tables à partir des entités (`ddl-auto: update`).
2. `data.sql` réinitialise et réinsère un jeu de données de démo. Il contient 8 employés, des compteurs, des demandes d'absence, des pointages, ainsi qu'un planning et des absences **sur la semaine en cours** (calculés à partir de la date du jour).
3. Les [tâches automatiques](#tâches-automatiques) tournent une première fois pour rattraper ce qui n'a pas été fait : sorties automatiques, acquisition des CP, report des soldes, heures sup.

Le réglage de la page « Paramètres » (RTT) n'est pas remis à zéro par les données de démo.

## Comptes de démo

Mot de passe pour tous les comptes : **`password123`**

| Compte | Rôle | Particularité |
|---|---|---|
| `sophie.bernard@roomster.fr` | **Manager (ADMIN)** | Voit toutes les pages d'administration |
| `jean.dupont@roomster.fr` | Salarié | Réception, 35h |
| `paul.martin@roomster.fr` | Salarié | Cuisinier, contrat de 39h |
| `lucas.petit@roomster.fr` | Salarié | Temps partiel de 20h, nuits |
| `camille.moreau@roomster.fr` | Salarié | Apprentie **mineure** (règles des moins de 18 ans) |
| `nicolas.girard@roomster.fr` | Salarié désactivé | Ne peut plus se connecter |

## Fonctionnalités

### Pour tous les salariés

| Page | Route | Description |
|---|---|---|
| Accueil | `/` | Raccourcis (un manager est redirigé vers le tableau de bord) |
| Ma pointeuse | `/mon-pointage` | Pointer l'entrée ou la sortie, voir sa semaine (heures, nuit, heures sup, jours fériés) et son historique |
| Mon planning | `/mon-planning` | Ses horaires de la semaine, les collègues qui travaillent chaque jour, et les collègues absents (congé payé, maladie ou « absent », sans autre détail) |
| Mes congés | `/mes-conges` | Soldes CP / heures sup (et RTT s'ils sont activés), historique des mouvements |
| Mes demandes d'absence | `/mes-demandes` | Demander un congé, suivre et annuler ses demandes |
| Mon compte | `/mon-compte` | Changer son mot de passe |

### Pour le manager (Administration)

| Page | Route | Description |
|---|---|---|
| Tableau de bord | `/tableau-de-bord` | Présents, absents et sans pointage du jour ; prévus au planning et pas pointés ; demandes en attente ; alertes de la convention HCR ; sorties automatiques à valider |
| Planning | `/planning` | Grille employés × jours : ajout (+) et suppression (✕) de créneaux, copie de la semaine précédente, absences par couleur, demandes en attente, conflits, effectif du jour, heures prévues comparées au contrat, alertes HCR avant le travail |
| Demandes d'absence | `/demandes` | Valider, ou refuser avec un motif ; déclarer un arrêt maladie (même après coup) |
| Employés | `/liste-employe`, `/ajout-employe` | Fiches employés, filtres, activation et désactivation (désactiver = révoquer le compte), mot de passe temporaire |
| Congés (RH) | `/conge-paye` | Compteurs de l'équipe, saisie des soldes, acquisition mensuelle des CP, historique des mouvements |
| Pointages (RH) | `/pointages` | Pointeuse collective, historique, correction d'un pointage (motif obligatoire, tracé) |
| Semaine d'un employé | `/pointages/semaine/{id}` | Détail des heures, heures sup par tranche, report au compteur, historique des corrections |
| Récap mensuel | `/pointages/mois` | Chiffres du mois pour la paie, avec export CSV |
| Exports comptables | `/exports` | 4 fichiers CSV par mois : récap paie, pointages, absences, compteurs |
| Paramètres | `/parametres` | « L'hôtel accorde des RTT » (non par défaut) |

Les pages manager sont réservées au rôle ADMIN : un salarié qui les ouvre reçoit une erreur 403. Les pages personnelles déduisent toujours le salarié de l'utilisateur connecté.

## Règles métier principales

Le détail et les choix faits sont dans la [trame fonctionnelle](tempo-trame-fonctionnelle.md).

- **Pointage** :
  - un pointage dure **12h au maximum** ;
  - sans pointage de sortie, il est clôturé automatiquement à entrée + 12h, et une alerte reste affichée au manager jusqu'à ce qu'il la valide avec un commentaire ;
  - toute correction est tracée : qui, quand, valeurs avant et après, motif.
- **Heures** :
  - heures sup **dès la 36ᵉ heure**, par tranches : +10 % de la 36ᵉ à la 39ᵉ, +20 % de la 40ᵉ à la 43ᵉ, +50 % au-delà ;
  - pour un temps partiel, heures complémentaires : +10 % jusqu'au 1/10ᵉ du contrat, +25 % au-delà ;
  - heures de nuit : de 22h à 7h ;
  - semaine du lundi au dimanche, coupée à minuit ;
  - calcul à la minute.
- **Contrôles HCR**, affichés comme des alertes :
  - durée maximale par jour : 11h pour un cuisinier, 12h pour un veilleur de nuit, 11h30 pour les autres ;
  - 48h au maximum par semaine ;
  - repos de 11h entre deux journées ;
  - contingent de 360h d'heures sup par an ;
  - moins de 18 ans : 8h par jour, 35h par semaine, 12h de repos, pas de travail entre 23h30 et 6h.
- **Travail de nuit** : un salarié qui fait au moins 3h de nuit deux jours dans la semaine est « travailleur de nuit » et a droit à un repos compensateur de 1 % de ses heures de nuit.
- **Jours fériés** :
  - les 11 jours fériés sont calculés, y compris Pâques ;
  - le 1er mai travaillé est payé double ;
  - un autre férié travaillé est à compenser si le salarié a au moins un an d'ancienneté.
- **Congés payés** :
  - période de référence du 1er juin au 31 mai ;
  - acquisition de 2,5 jours par mois travaillé, 2 jours par mois de maladie, 0 en sans solde ;
  - décompte en jours ouvrables (sans le dimanche ni les fériés) ;
  - une maladie pendant un CP rend les jours de CP concernés.
- **RTT** : la convention HCR n'en prévoit pas. Ils sont désactivés par défaut et s'activent dans les Paramètres si l'hôtel a un accord d'entreprise.

## Tâches automatiques

Classe `TachesAutomatiques`. Chaque tâche peut être relancée sans risque : rien n'est jamais crédité deux fois. Les mouvements créés ont pour auteur « automatique ».

| Quand | Ce qui est fait |
|---|---|
| Toutes les 5 minutes | Clôture automatique des pointages ouverts depuis plus de 12h |
| Chaque nuit à 1h, et au démarrage | Clôture des pointages dépassés ; ouverture de la période au 1er juin (report des soldes restants, rien n'est supprimé) ; acquisition des CP du mois précédent ; report au compteur des heures sup des semaines terminées depuis au moins 7 jours |
| Au moment de l'action | Régularisations : arrêt maladie ou sans solde déclaré sur un mois déjà crédité, pointage corrigé dans une semaine déjà reportée |

## Frontend / Tailwind CSS

Le CSS est généré par Tailwind CLI à partir de `tailwind/input.css` vers `src/main/resources/static/css/app.css`. Le fichier généré est commité, donc **aucune étape n'est nécessaire pour lancer l'appli**. Pour modifier les styles :

```bash
npm install
npm run watch:css   # regénère app.css à chaque modification
# ou, pour une build unique minifiée :
npm run build:css
```

Tailwind ne génère que les classes écrites en toutes lettres dans les templates et le JS : n'en construis pas dynamiquement côté Java (ex : `"bg-" + couleur + "-100"`).

## Base de données (H2)

La base est un **fichier H2** persistant (pas une base en mémoire) : `data/roomster.mv.db`, créé automatiquement au premier lancement. Le dossier `data/` est ignoré par Git.

### Console H2 (dans le navigateur)

Le plus simple pour consulter les données, sans rien installer :

1. Lance l'application.
2. Va sur **http://localhost:8099/h2-console**
3. Renseigne :

| Champ | Valeur |
|---|---|
| Driver Class | `org.h2.Driver` |
| JDBC URL | `jdbc:h2:file:./data/roomster` |
| User Name | `sa` |
| Password | *(vide)* |

4. **Connect**, puis clique sur une table à gauche pour voir son contenu.

> La console H2 et l'exception CSRF qui va avec (`/h2-console/**`) sont prévues pour le poste de développement : à retirer avant tout déploiement.

### Client externe (IntelliJ, DBeaver...)

Comme la base est en mode `AUTO_SERVER=TRUE`, un outil externe peut s'y connecter **pendant que l'application tourne**, sans conflit :

| Champ | Valeur |
|---|---|
| Driver | H2 (utiliser le jar `h2-2.4.240.jar` du repo Maven local, pour éviter les erreurs de version type `unexpected status`) |
| URL | `jdbc:h2:file:C:/laragon/www/Roomster/data/roomster;AUTO_SERVER=TRUE` |
| User | `sa` |
| Password | *(vide)* |
| Port | *(aucun — ne rien saisir, `AUTO_SERVER` gère ça automatiquement)* |

Dans IntelliJ, veiller à utiliser le champ **URL unique** (et non les champs séparés Host/Port/Database) pour éviter que l'URL soit mal découpée.

## Structure du projet

```
src/main/java/com/cesarhotel/roomster/
├── controller/   contrôleurs Spring MVC (pages, formulaires, routes JSON, exports CSV)
├── model/        entités JPA (Employe, Pointage, DemandeAbsence, CompteurEmploye,
│                 MouvementCompteur, CorrectionPointage, CreneauPlanning, ParametresHotel...)
├── repository/   repositories Spring Data JPA
├── service/      logique métier
│   ├── calculs purs, sans base de données (testés) :
│   │   CalculHeures, CalculConges, JoursFeries, ControlesHCR, Csv
│   ├── services : Pointage, Heures, DemandeAbsence, CompteurEmploye, Planning,
│   │   TableauDeBord, Export, Parametres, Employe, User
│   └── TachesAutomatiques (tâches planifiées)
├── security/     Spring Security (connexion, rôles, comptes désactivés)
├── exception/    ValidationException (erreurs métier affichées à l'utilisateur)
├── dtos/         objets passés aux pages
└── mapper/       mappers MapStruct entité <-> DTO

src/main/resources/
├── application.yaml
├── data.sql              jeu de données de démo
├── templates/            vues Thymeleaf (absence/, employe/, planning/, pointage/, compte/, fragments/...)
└── static/
    ├── css/app.css       CSS généré par Tailwind
    └── js/               employe.js, pointage.js, mon-pointage.js

src/test/java/...         tests unitaires des calculs
tailwind/input.css        source Tailwind
```

Principe retenu : du code **simple à comprendre**. Les calculs sont des fonctions pures écrites avec des boucles simples, les règles sont vérifiées une par une avec un message clair, et les pages sont des formulaires HTML classiques (POST, redirection, message).

## Tests

Tous les tests :

```bash
./mvnw test
```

Seulement les tests unitaires des calculs (rapides, sans démarrer l'application) :

```bash
./mvnw test -Dtest='CalculHeuresTest,ControlesHCRTest,JoursFeriesTest,CalculCongesTest,DemandeAbsenceTest,CreneauPlanningTest,CsvTest'
```

Ils couvrent notamment la nuit à cheval sur minuit, les coupures, les tranches d'heures sup, le temps partiel, les jours fériés et Pâques, l'acquisition des CP, les durées maximales HCR, le planning et le format CSV.

## Documentation

| Document | Contenu |
|---|---|
| [tempo-trame-fonctionnelle.md](tempo-trame-fonctionnelle.md) | Chaque fonctionnalité (F1 à F11) : comportement attendu, règles, décisions prises, points ouverts, conformité HCR |
| [tempo-specification-technique.md](tempo-specification-technique.md) | Spécification complète pour refaire l'application dans un autre langage (ex : PHP/Symfony) : modèle de données, routes, algorithmes, messages, jeux de tests, correspondance Spring → Symfony, pièges rencontrés |

⚠️ Les règles de paie et de convention collective sont à faire valider par l'expert-comptable ou le gestionnaire de paie de l'hôtel. Un accord d'entreprise peut modifier certains points.
