# Roomster

Application de gestion RH pour hôtel (employés, pointages, absences, compteurs congés/RTT/heures sup) — Spring Boot 4.1 / Java 22 / Hibernate / H2.

## Prérequis

- JDK 22
- Rien d'autre à installer : Maven est fourni via le wrapper (`mvnw` / `mvnw.cmd`), et la base de données H2 est embarquée dans l'application.

## Lancer le projet

```bash
./mvnw spring-boot:run
```

Ou depuis IntelliJ : ouvre `RoomsterApplication.java` (`src/main/java/com/cesarhotel/roomster/`) et clique sur ▶️.

L'application démarre sur **http://localhost:8099** (port fixé dans `application.yaml` — le 8080 par défaut de Spring Boot est laissé libre car souvent occupé par Apache/Laragon).

Au démarrage, tout se fait automatiquement, sans étape manuelle :
1. Hibernate crée/adapte les tables à partir des entités (`ddl-auto: update`).
2. `data.sql` réinitialise et réinsère un jeu de données de démo (5 employés, compteurs, demandes d'absence, pointages).

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
├── controller/   contrôleurs Spring MVC
├── model/        entités JPA (Employe, Pointage, CompteurEmploye, DemandeAbsence...)
├── repository/   repositories Spring Data JPA
├── service/      logique métier
└── dtos/         objets de transfert
```

## Tests

```bash
./mvnw test
```
