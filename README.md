# Roomster

Application de gestion RH pour hôtel (employés, pointages, absences, compteurs congés/RTT/heures sup) — Spring Boot 4.1 / Java 22 / Hibernate / H2.

## Prérequis

- JDK 21+ (testé avec JDK 22)
- Rien d'autre à installer pour lancer l'appli : Maven est fourni via le wrapper (`mvnw` / `mvnw.cmd`), la base de données H2 est embarquée, et le CSS (Tailwind) est déjà compilé dans `static/css/app.css`.
- Node.js (optionnel) : uniquement nécessaire si tu modifies les styles Tailwind (voir [Frontend / Tailwind CSS](#frontend--tailwind-css)).

## Lancer le projet

```bash
./mvnw spring-boot:run
```

Ou depuis IntelliJ : ouvre `RoomsterApplication.java` (`src/main/java/com/cesarhotel/roomster/`) et clique sur ▶️.

L'application démarre sur **http://localhost:8099** (port fixé dans `application.yaml` — le 8080 par défaut de Spring Boot est laissé libre car souvent occupé par Apache/Laragon).

Au démarrage, tout se fait automatiquement, sans étape manuelle :
1. Hibernate crée/adapte les tables à partir des entités (`ddl-auto: update`).
2. `data.sql` réinitialise et réinsère un jeu de données de démo (5 employés, compteurs, demandes d'absence, pointages).

## Fonctionnalités

| Page | Route | Description |
|---|---|---|
| Accueil | `GET /` | Page d'atterrissage |
| Liste des employés | `GET /liste-employe` | Consultation (filtrable par statut actif et par poste), activation/désactivation et édition des employés |
| Ajout d'un employé | `GET/POST /ajout-employe` | Formulaire de création d'un employé (poste, type de contrat...) |
| Congés payés | `GET /conge-paye` | Compteurs congés/RTT/heures sup par employé, avec historique N-1 |
| Pointages | `GET /pointages` | Pointage des employés actifs (entrée/sortie) et historique paginé |

Endpoints REST additionnels utilisés en AJAX par les pages ci-dessus : `PATCH /toggle-actif-employe/{id}`, `PUT /edit-employe/{id}`, `POST /pointer-employe/{employeId}`.

## Frontend / Tailwind CSS

Le CSS est généré par Tailwind CLI à partir de `tailwind/input.css` vers `src/main/resources/static/css/app.css`. Le fichier généré est committé, donc **aucune étape n'est requise pour lancer l'appli**. Pour modifier les styles :

```bash
npm install
npm run watch:css   # regénère app.css à chaque modification
# ou, pour une build unique minifiée :
npm run build:css
```

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
├── controller/   contrôleurs Spring MVC (Home, Employe, EmployeConge, Pointage)
├── model/        entités JPA (Employe, Pointage, CompteurEmploye, DemandeAbsence...)
├── repository/   repositories Spring Data JPA
├── service/      logique métier
├── dtos/         objets de transfert
└── mapper/       mappers MapStruct entité <-> DTO

src/main/resources/
├── application.yaml
├── data.sql              jeu de données de démo
├── templates/            vues Thymeleaf (accueil, employe/, pointage/, fragments/)
└── static/
    ├── css/app.css       CSS généré par Tailwind (voir Frontend / Tailwind CSS)
    └── js/                employe.js, pointage.js

tailwind/input.css         source Tailwind
```

## Tests

```bash
./mvnw test
```
