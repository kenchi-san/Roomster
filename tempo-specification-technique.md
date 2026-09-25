# Tempo (Roomster) — Spécification technique pour un portage

Ce document décrit **exactement** ce que fait l'application Java/Spring, pour pouvoir la refaire à l'identique dans un autre langage, par exemple PHP/Symfony.
Il complète `tempo-trame-fonctionnelle.md`, qui explique le *pourquoi* des règles.

État décrit : code au **25/09/2026**, planning (F9), exports comptables (F10) et paramètres (F11) compris.

**Contenu**
1. Architecture et principes
2. Modèle de données (dont le planning)
3. Sécurité et rôles
4. Routes
5. Algorithmes (pseudo-code)
6. Services : règles et enchaînements
7. Pages et affichage
8. Messages exacts
9. Jeux de tests à reproduire
10. Données de démo
11. Correspondance Spring → Symfony
12. Pièges rencontrés

---

## 1. Architecture et principes

```
Contrôleur (HTTP)  →  Service (règles métier)  →  Repository (base de données)
                          ↓
                   Fonctions pures (calculs, aucune base) : CalculHeures, CalculConges, JoursFeries, ControlesHCR,
                   méthodes de CreneauPlanning, Csv
```

- **Fonctions pures** : elles reçoivent des données (pointages, dates, durées) et renvoient un résultat. Elles ne lisent jamais la base. C'est ce qui les rend testables : 61 tests unitaires, listés en section 9. **Portez-les en premier, avec leurs tests.**
- **Services** : ils chargent les données, appliquent les règles une par une et lèvent une `ValidationException(message)` au premier problème.
- **Contrôleurs** :
  - pages de formulaire : ils attrapent la `ValidationException`, mettent le message en *flash* (`succes` ou `erreur`) et redirigent (motif POST → redirection → GET) ;
  - routes JSON (appelées en `fetch` depuis le JS) : une `ValidationException` devient une réponse **400** avec le message en texte brut (gestionnaire global).
- **Dates** : toutes en heure locale de l'hôtel, sans fuseau horaire (`LocalDate`, `LocalDateTime`).
- **Durées** : `java.time.Duration`. En PHP, stockez des **minutes (entier)** : c'est plus simple, et tous les calculs sont à la minute.

---

## 2. Modèle de données

### Énumérations (stockées en texte, par leur nom)

| Enum | Valeurs (libellé affiché) |
|---|---|
| `Role` | `ADMIN`, `SALARIE` |
| `Poste` | `RECEPTION`, `HOUSEKEEPING`, `CUISINE`, `SALLE`, `MAINTENANCE`, `DIRECTION`, `VEILLEUR_NUIT` |
| `TypeContrat` | `CDI`, `CDD`, `EXTRA`, `APPRENTI` |
| `TypeAbsence` | `CONGE_PAYE` (Congé payé), `RTT` (RTT), `MALADIE` (Maladie), `SANS_SOLDE` (Sans solde), `RECUP_JOUR_FERIE` (Récup. jour férié) |
| `StatutDemande` | `SOUMISE` (En attente), `VALIDEE` (Validée), `REFUSEE` (Refusée), `ANNULEE` (Annulée) |

### Tables

**`users`** — un compte de connexion
| Champ | Type | Contraintes |
|---|---|---|
| id | entier | clé |
| nom, prenom | texte | obligatoires |
| email | texte | obligatoire, **unique**, c'est l'identifiant de connexion |
| password | texte | obligatoire, haché en **BCrypt** |
| role | `Role` | obligatoire |

**`employe`** — une fiche salarié (1 employé = 1 compte)
| Champ | Type | Contraintes / sens |
|---|---|---|
| id | entier | clé |
| user_id | → users | obligatoire, unique ; créé et enregistré avec l'employé (cascade) |
| poste | `Poste` | obligatoire |
| type_contrat | `TypeContrat` | obligatoire |
| date_entree | date | obligatoire |
| date_sortie | date | null tant qu'il est en poste |
| duree_hebdo_contrat | durée | obligatoire (ex : 35h, 39h, 20h) |
| actif | booléen | défaut `true` |
| date_naissance | date | facultative (règles des moins de 18 ans) |

Méthodes :
- `estMineurLe(jour)` = `date_naissance != null` et `jour < date_naissance + 18 ans` ;
- `aUnAnDAncienneteLe(jour)` = `jour >= date_entree + 1 an`.

**`pointage`**
| Champ | Type | Sens |
|---|---|---|
| id | entier | clé |
| employe_id | → employe | obligatoire |
| entree | date+heure | obligatoire |
| sortie | date+heure | **null tant que le pointage est ouvert** |
| commentaire | texte | ex : motif de correction |
| sortie_automatique | booléen | défaut `false` ; `true` si la sortie a été mise automatiquement à entrée + 12h. Alerte au manager jusqu'à validation ou correction |

Index sur `entree` décroissant.

**`correction_pointage`** — trace d'une correction manuelle
| Champ | Type |
|---|---|
| id, pointage_id (→ pointage, obligatoire) | |
| auteur | texte, email du manager, obligatoire |
| date | date+heure de la correction |
| ancienne_entree, ancienne_sortie | valeurs **avant** correction |
| nouvelle_entree, nouvelle_sortie | valeurs **après** |
| commentaire | obligatoire |

**`demande_absence`**
| Champ | Type | Sens |
|---|---|---|
| id, employe_id | | |
| type | `TypeAbsence` | obligatoire |
| debut, fin | date | obligatoires, **fin incluse** |
| statut | `StatutDemande` | défaut `SOUMISE` |
| date_soumission | date+heure | à la création |
| date_decision | date+heure | à la validation / au refus |
| validateur_id | → employe | le manager qui a décidé |
| motif_refus | texte | |

**`compteur_employe`** — un compteur par employé et par **période de référence**
| Champ | Type | Sens |
|---|---|---|
| id, employe_id | | |
| annee | entier | **année où commence la période** : 2026 = 1er juin 2026 → 31 mai 2027 |
| solde_conges_payes | décimal | en jours ouvrables (demi-journées possibles) |
| solde_rtt | décimal | en jours |
| heures_sup_cumulees | durée | défaut 0 |
| report_effectue | booléen | défaut `false` ; `true` quand les soldes de la période précédente ont été reportés (une seule fois) |

Contrainte **unique (employe_id, annee)**.

**`mouvement_compteur`** — historique : toute modification de solde crée une ligne
| Champ | Type | Sens |
|---|---|---|
| id, compteur_id (→ compteur_employe) | | |
| date | date+heure | |
| libelle | texte | ex : « Congé payé du 03/08/2026 au 14/08/2026 (11 j ouvrables) validé » |
| jours_conges_payes, jours_rtt | décimal | **variations** (−3 = débit, +3 = crédit) |
| heures_sup | durée | variation |
| semaine | date | lundi de la semaine, **seulement** pour un report ou une régularisation d'heures sup (anti-doublon + somme déjà reportée) |
| mois | date | 1er du mois, **seulement** pour une acquisition ou une régularisation de CP (anti-doublon + somme déjà créditée) |
| auteur | texte | email |

**`creneau_planning`** — un créneau du planning prévisionnel
| Champ | Type | Sens |
|---|---|---|
| id | entier | clé |
| employe_id | → employe | obligatoire |
| jour | date | le jour où le créneau **commence** (index) |
| debut | heure | obligatoire |
| fin | heure | obligatoire ; si `fin <= debut`, le créneau finit **le lendemain** |
| note | texte | facultative (vide → null) |

**`parametres_hotel`** — réglages de l'hôtel, **une seule ligne**, d'id 1
| Champ | Type | Sens |
|---|---|---|
| id | entier | toujours 1 (pas d'auto-incrément) |
| rtt_actives | booléen | défaut `false` : l'hôtel accorde-t-il des RTT ? (convention HCR : non) |

La ligne est créée avec les valeurs par défaut la première fois qu'on la lit (`ParametresService`).

### Méthodes métier des entités

**`CompteurEmploye`**
- `debiterCongesPayes(j)` : si `j > solde` → erreur « Solde CP insuffisant : demandé %.1f, disponible %.1f ». Sinon `solde -= j`.
- `debiterRtt(j)` : pareil, avec « Solde RTT insuffisant… ».
- `crediterCongesPayes(j)`, `crediterRtt(j)` : `+= j`.
- `ajouterHeuresSup(d)` : `+= d`.
- `definirSoldes(cp, rtt)` : si l'un est négatif → « Un solde ne peut pas être négatif ».
- `solder()` : met CP, RTT et heures sup à 0 (après report sur la période suivante).
- `marquerReportEffectue()` : `report_effectue = true`.

**`DemandeAbsence`**
- `valider(manager)` : exige `SOUMISE`, sinon « Impossible de valider une demande <statut en minuscules> ». Passe à `VALIDEE`, avec validateur et date de décision.
- `refuser(manager, motif)` : exige `SOUMISE` (même message avec « refuser »). Passe à `REFUSEE`, avec motif et date de décision.
- `annuler()` : exige `SOUMISE` ou `VALIDEE`, sinon « Impossible d'annuler une demande <statut> ». Passe à `ANNULEE`.
- `nombreDeJours()` : CP ou RTT → `CalculConges.joursOuvrables(debut, fin)` ; autres types → jours calendaires (`fin − debut + 1`).
- `isAnnulable()` = statut `SOUMISE` ou `VALIDEE` **et** `debut > aujourd'hui`.
- `getPeriode()` = « du dd/MM/yyyy au dd/MM/yyyy (n j ouvrables) » pour un CP ou un RTT, « (n j) » sinon.
- `chevauche(d, f)` = `debut <= f` **et** `fin >= d`.

**`CreneauPlanning`**
- `debutComplet` = `jour` à `debut`.
- `finComplete` = `fin > debut` ? `jour` à `fin` : `(jour + 1)` à `fin`.
- `duree` = `finComplete − debutComplet`. Ex : 22:00 → 06:00 = 8h ; 17:00 → 00:00 = 7h.
- `deNuit` = `fin <= debut` (le créneau finit le lendemain).
- `horaires` = « HH:mm → HH:mm », suivi de « (lendemain) » si `deNuit`.
- `chevauche(autre)` = `debutComplet < autre.finComplete` **et** `autre.debutComplet < finComplete` (des bornes égales ne se chevauchent pas).
- `enPointagePrevu()` = un pointage **non enregistré** avec `entree = debutComplet`, `sortie = finComplete` : c'est ce qui permet de réutiliser les contrôles HCR.

---

## 3. Sécurité et rôles

- Formulaire de connexion : `/login`. Identifiant = email, mot de passe en BCrypt. Déconnexion : POST `/logout`.
- Rôles : `ROLE_ADMIN` (manager) et `ROLE_SALARIE`.
- **Compte révoqué** : un employé `actif = false` est marqué *disabled*, sa connexion échoue (`/login?error`).
- **CSRF actif** :
  - formulaires HTML : champ caché `_csrf` ;
  - appels `fetch` : en-tête `X-XSRF-TOKEN`, lu dans le cookie `XSRF-TOKEN` (non HttpOnly, valeur brute sans masquage).
- Les pages personnelles déduisent **toujours** l'employé de l'utilisateur connecté (son email), jamais d'un id envoyé par le navigateur.

| Accès | Chemins |
|---|---|
| Public | `/login`, `/css/**`, `/js/**`, `/h2-console/**` (dev uniquement) |
| ADMIN | `/liste-employe`, `/ajout-employe`, `/edit-employe/**`, `/toggle-actif-employe/**`, `/reset-password-employe/**`, `/pointages`, `/pointages/**`, `/pointer-employe/**`, `/conge-paye`, `/compteurs/**`, `/demandes`, `/demandes/**`, `/tableau-de-bord`, `/planning`, `/planning/**`, `/exports`, `/exports/**`, `/parametres` |
| Tout utilisateur connecté | tout le reste, dont `/`, `/mon-pointage`, `/mon-pointage/**`, `/mon-planning`, `/mes-conges`, `/mes-demandes`, `/mes-demandes/**`, `/mon-compte` |

Un salarié qui ouvre une page ADMIN reçoit **403**.

**Données communes à toutes les pages** (équivalent d'un listener Twig global) :
- `isAdmin` : l'utilisateur a-t-il `ROLE_ADMIN` ?
- `nbDemandesEnAttente` : nombre de demandes `SOUMISE` (0 si l'utilisateur n'est pas admin) ; c'est la pastille du menu.
- `rttActives` : le réglage de l'hôtel. Les templates s'en servent pour masquer tout ce qui concerne les RTT.

---

## 4. Routes

Paramètres de date : format ISO `AAAA-MM-JJ`.
- `?semaine=` : n'importe quel jour de la semaine voulue ;
- `?mois=` : n'importe quel jour du mois voulu.

Sans ce paramètre, la page prend la date du jour.

### Pages et formulaires (POST → redirection + message flash)

| Méthode | Chemin | Rôle | Paramètres | Effet / redirection |
|---|---|---|---|---|
| GET | `/` | tous | | admin → redirige vers `/tableau-de-bord` ; sinon page d'accueil |
| GET | `/tableau-de-bord` | ADMIN | | voir 6.6 |
| GET | `/mon-pointage` | tous | `page` (0), `semaine` | statut, vue semaine, historique personnel paginé (10 par page, `entree` décroissante) |
| GET | `/pointages` | ADMIN | `page` (0) | employés actifs + statut du jour, historique global paginé (10) |
| GET | `/pointages/semaine/{employeId}` | ADMIN | `semaine` | vue semaine + corrections de l'employé ; 404 si l'employé est inconnu |
| POST | `/pointages/semaine/{employeId}/reporter-heures-sup` | ADMIN | `semaine` | → `/pointages/semaine/{id}?semaine=…` |
| GET | `/pointages/mois` | ADMIN | `mois` | récap mensuel |
| POST | `/pointages/{id}/valider-sortie` | ADMIN | `commentaire` | valider une sortie automatique → `/tableau-de-bord` + flash « Sortie automatique validée. » |
| GET | `/mon-planning` | tous | `semaine` | mon planning + collègues (voir 6.10) |
| GET | `/planning` | ADMIN | `semaine`, `employeId` et `jour` (facultatifs : préremplissent le formulaire, c'est le lien « + ») | grille (voir 6.10) ; si `semaine` est absent, la semaine de `jour`, sinon la semaine en cours |
| POST | `/planning/creneaux` | ADMIN | `employeId`, `jour`, `debut` (HH:mm), `fin` (HH:mm), `note` | ajouter → `/planning?semaine=<jour>` |
| POST | `/planning/creneaux/{id}/supprimer` | ADMIN | `semaine` | supprimer → `/planning?semaine=…` |
| POST | `/planning/copier-semaine` | ADMIN | `semaine` (lundi) | copier la semaine précédente → `/planning?semaine=…` |
| GET | `/exports` | ADMIN | | page des exports (champ mois, défaut = mois précédent `AAAA-MM`) |
| GET | `/exports/recap-paie` | ADMIN | `mois` (`AAAA-MM`, défaut = mois précédent) | **fichier CSV** (voir 6.11) |
| GET | `/exports/pointages` | ADMIN | `mois` | fichier CSV |
| GET | `/exports/absences` | ADMIN | `mois` | fichier CSV |
| GET | `/exports/compteurs` | ADMIN | `mois` | fichier CSV |
| GET | `/parametres` | ADMIN | | page des paramètres |
| POST | `/parametres` | ADMIN | `rttActives` (case à cocher : absente = `false`) | enregistre → `/parametres` + flash |
| GET | `/mes-demandes` | tous | | mes demandes + formulaire |
| POST | `/mes-demandes` | tous | `type`, `debut`, `fin` | soumettre → `/mes-demandes` |
| POST | `/mes-demandes/{id}/annuler` | tous | | annuler → `/mes-demandes` |
| GET | `/demandes` | ADMIN | | en attente, 20 dernières traitées, employés actifs |
| POST | `/demandes/{id}/valider` | ADMIN | | → `/demandes` |
| POST | `/demandes/{id}/refuser` | ADMIN | `motif` | → `/demandes` |
| POST | `/demandes/maladie` | ADMIN | `employeId`, `debut`, `fin` | → `/demandes` |
| GET | `/conge-paye` | ADMIN | | compteurs, 30 derniers mouvements, formulaires |
| POST | `/compteurs/initialiser` | ADMIN | `employeId`, `annee` (période), `soldeCongesPayes`, `soldeRtt` (**facultatif** : absent quand les RTT sont désactivés → solde RTT inchangé) | → `/conge-paye` |
| POST | `/compteurs/acquisition` | ADMIN | `mois` au format `AAAA-MM` (champ `type="month"`) | → `/conge-paye` |
| GET | `/mes-conges` | tous | | mes compteurs + mes mouvements |
| GET/POST | `/ajout-employe` | ADMIN | nom, prenom, email, poste, typeContrat, dateEntree, dureeHebdoHeures (décimal), dateNaissance (facultative) | erreurs de validation affichées sous les champs ; succès → `/liste-employe` avec le mot de passe temporaire en flash |
| GET | `/liste-employe` | ADMIN | `actif` (true/false), `poste` | filtres côté serveur |
| GET/POST | `/mon-compte` | tous | ancienMotDePasse, nouveauMotDePasse, confirmationMotDePasse | voir 6.8 |

### Routes JSON (appelées en `fetch` depuis le JS)

| Méthode | Chemin | Rôle | Corps | Réponse |
|---|---|---|---|---|
| POST | `/pointer-employe/{employeId}` | ADMIN | — | `PointageDto` ; 404 si employé inconnu ; 403 si inactif |
| POST | `/mon-pointage/pointer` | tous | — | `PointageDto` de l'utilisateur connecté |
| PATCH | `/pointer-employe/{id}/corriger` | ADMIN | `{entree, sortie, commentaire}` | `PointageDto` ; 400 (texte) si invalide ; 404 si inconnu |
| PUT | `/edit-employe/{id}` | ADMIN | `EmployeDto` | `EmployeDto` ; 400 (texte) : email déjà pris, sortie avant entrée |
| PATCH | `/toggle-actif-employe/{id}` | ADMIN | — | `EmployeDto` ; active ⇄ désactive ; désactiver met `date_sortie` = aujourd'hui, réactiver la remet à null ; 400 si sortie avant entrée |
| PATCH | `/reset-password-employe/{id}` | ADMIN | — | `{"motDePasseTemporaire": "..."}` |

- `PointageDto` = `{id, employeId, entree, sortie, commentaire}`.
- `EmployeDto` = `{id, nom, prenom, email, poste, typeContrat, dateEntree, dateSortie, dateNaissance, actif}`.

---

## 5. Algorithmes (pseudo-code)

Notation : les durées sont en minutes, `max(a, 0)` s'écrit `positif(a)`.

### 5.1 CalculHeures

```
duree(p)            = p.sortie == null ? 0 : p.sortie - p.entree
totalTravaille(ps)  = somme de duree(p)
totalNuit(ps)       = somme de heuresDeNuit(p.entree, p.sortie) pour les p fermés
heuresDeNuit(e, s)  = heuresDansPlage(e, s, 22:00, 07:00)

heuresDansPlage(e, s, debutPlage, finPlage):      // plage qui passe minuit
    total = 0
    pour jour de (date(e) - 1 jour) à date(s):
        total += chevauchement(e, s, jour à debutPlage, (jour + 1) à finPlage)
    renvoyer total

chevauchement(d1, f1, d2, f2):
    debut = le plus tard de d1, d2 ; fin = le plus tôt de f1, f2
    renvoyer debut < fin ? fin - debut : 0

totalEntre(ps, debut, fin)     = somme de chevauchement(p.entree, p.sortie, debut, fin) pour les p fermés
totalNuitEntre(ps, debut, fin) = pour chaque p fermé : e = max(p.entree, debut), s = min(p.sortie, fin),
                                 si e < s : total += heuresDeNuit(e, s)

decompter(total, contrat):                        // DUREE_LEGALE = 35h, TRANCHE = 4h
    sup   = positif(total - 35h)
    sup10 = min(sup, 4h)
    sup20 = min(positif(sup - 4h), 4h)
    sup50 = positif(sup - 8h)
    comp10 = comp25 = 0
    si contrat < 35h:                             // temps partiel
        comp    = positif(min(total, 35h) - contrat)
        dixieme = contrat / 10
        comp10  = min(comp, dixieme)
        comp25  = positif(comp - dixieme)
    renvoyer {sup10, sup20, sup50, comp10, comp25}

heuresSupAuDelaDuContrat(total, contrat) = positif(total - max(contrat, 35h))   // → compteur

formater(d) = signe + heures + "h" + minutes sur 2 chiffres     // 450 min → "7h30", -120 → "-2h00"
```

### 5.2 JoursFeries

```
paques(a):                                        // Meeus / Jones / Butcher, divisions entières
    a19 = a % 19 ; b = a / 100 ; c = a % 100 ; d = b / 4 ; e = b % 4
    f = (b + 8) / 25 ; g = (b - f + 1) / 3
    h = (19*a19 + b - d - g + 15) % 30
    i = c / 4 ; k = c % 4
    l = (32 + 2*e + 2*i - h - k) % 7
    m = (a19 + 11*h + 22*l) / 451
    mois = (h + l - 7*m + 114) / 31
    jour = ((h + l - 7*m + 114) % 31) + 1

liste(a) = 01/01 « Jour de l'an », Pâques+1 « Lundi de Pâques », 01/05 « 1er mai », 08/05 « Victoire 1945 »,
           Pâques+39 « Ascension », Pâques+50 « Lundi de Pentecôte », 14/07 « Fête nationale »,
           15/08 « Assomption », 01/11 « Toussaint », 11/11 « Armistice 1918 », 25/12 « Noël »
nom(jour) = liste(année du jour)[jour] ou null ; estFerie(jour) = nom != null
estPremierMai(jour) = mois 5 et jour 1
```

### 5.3 CalculConges

```
periodeDe(jour) = mois(jour) >= 6 ? année(jour) : année(jour) - 1

joursOuvrables(debut, fin) = nombre de jours de debut à fin (inclus) qui ne sont ni un dimanche ni un férié

joursAcquis(premierJourDuMois, dateEntree, dateSortie, absencesValidees):
    n = nombre de jours du mois ; acquis = 0
    pour chaque jour du mois:
        si jour < dateEntree ou (dateSortie != null et jour > dateSortie): continuer
        type = type de la 1re absence validée qui couvre ce jour (ou aucun)
        si type == SANS_SOLDE: continuer
        si type == MALADIE: acquis += 2.0 / n
        sinon:              acquis += 2.5 / n        // travail, CP, RTT, récup…
    renvoyer arrondi(acquis * 100) / 100
```

### 5.4 ControlesHCR

```
dureeMaxJour(poste, mineur) = mineur ? 8h : poste == CUISINE ? 11h : poste == VEILLEUR_NUIT ? 12h : 11h30
dureeMaxSemaine(mineur)     = mineur ? 35h : 48h
reposMinimum(mineur)        = mineur ? 12h : 11h
CONTINGENT_ANNUEL           = 360h

totalParJour(ps): pour chaque p fermé, total[date(p.entree)] += duree(p)       // triés par jour

reposTropCourts(ps, minimum):
    pour chaque jour (date d'entrée) des p fermés : premiereEntree[jour], derniereSortie[jour]
    pour chaque jour J dans l'ordre, à partir du 2e :
        repos = premiereEntree[J] - derniereSortie[jour précédent qui a des pointages]
        si repos < minimum : resultat[J] = repos
    // les coupures dans une même journée ne sont pas des repos

estTravailleurDeNuit(pointagesDeLaSemaine):
    nuit[jour] = somme de heuresDeNuit des p fermés, par jour d'entrée
    renvoyer (nombre de jours où nuit >= 3h) >= 2

reposCompensateurNuit(heuresDeNuit) = heuresDeNuit / 100                        // 1 %

nuitsInterditesMineur(ps) = jours d'entrée des p fermés tels que heuresDansPlage(e, s, 23:30, 06:00) > 0
```

---

## 6. Services : règles et enchaînements

### 6.1 Pointage
- **Durée maximale d'un pointage : 12h** (`PointageService.DUREE_MAXIMALE`). `sortieObligatoire(p) = p.entree + 12h`.
- **Pointer** (`pointer(employeId)`), exécuté **l'un après l'autre** (verrou) pour éviter deux pointages ouverts :
  1. employé inconnu → 404 ;
  2. employé inactif → 403 « Un employé désactivé ne peut plus pointer » ;
  3. s'il y a un pointage ouvert et que `sortieObligatoire < maintenant` : on le **clôture automatiquement** (ci-dessous), et on considère qu'il n'y a plus de pointage ouvert ;
  4. un pointage ouvert existe → `sortie = maintenant` ; sinon, nouveau pointage avec `entree = maintenant`.
- **Clôture automatique** (`fermerAutomatiquement(p)`) :
  - `sortie = entree + 12h` et `sortieAutomatique = true` ;
  - on ajoute au commentaire « Sortie automatique à HH:mm (pas de pointage de sortie, 12h maximum) », séparé par « — » s'il y en avait déjà un ;
  - puis on régularise les heures sup de la semaine si elle était déjà reportée.
- **Fermer les pointages dépassés** (`fermerPointagesDepasses()`, verrou) : clôture automatique de chaque pointage ouvert dont `sortieObligatoire < maintenant`. Appelée **toutes les 5 minutes** et au début des tâches de nuit / de démarrage.
- **Valider une sortie automatique** (`validerSortieAutomatique(id, commentaire, auteur)`, transaction) :
  1. commentaire vide → « Un commentaire est obligatoire pour valider une sortie automatique » ;
  2. pointage inconnu → « Pointage introuvable » ;
  3. pas de sortie automatique → « Ce pointage n'a pas de sortie automatique à valider » ;
  4. on enregistre une `CorrectionPointage` (mêmes entrée et sortie, commentaire « Sortie automatique validée : <commentaire> »), on ajoute « — Validé : <commentaire> » au commentaire du pointage, et `sortieAutomatique = false`.
- **Corriger** (validation dans le contrôleur, dans cet ordre) :
  1. commentaire vide → 400 « Un commentaire est obligatoire pour corriger un pointage » ;
  2. entrée absente → 400 « La date d'entrée est obligatoire » ;
  3. sortie non postérieure à l'entrée → 400 « La sortie doit être postérieure à l'entrée » ;
  4. sortie − entrée > 12h → 400 « Un pointage ne peut pas dépasser 12h ».

  Puis, dans une transaction :
  - on enregistre une `CorrectionPointage` (anciennes et nouvelles valeurs, auteur = email connecté) ;
  - on écrase entrée, sortie et commentaire, et `sortieAutomatique = false` (le manager a vérifié) ;
  - on régularise les heures sup des semaines de l'**ancienne entrée**, de l'**ancienne sortie**, de la **nouvelle entrée** et de la **nouvelle sortie** (un pointage de nuit peut passer d'une semaine à l'autre).
- Statut du jour : pour chaque employé ayant un pointage ouvert, l'heure d'entrée (« Entré à HH:mm »), sinon « Non pointé ».

### 6.2 Vue semaine (`getSemaine(employe, jour)`)
1. `lundi = lundi de la semaine de jour`, `dimanche = lundi + 6`.
2. `pointages` = entrée entre lundi 00:00 et dimanche 23:59:59.999, triés (pour l'**affichage**).
3. `pointagesAvecVeille` = même chose mais depuis **dimanche précédent** 00:00 (pour les **calculs**).
4. Pour chacun des 7 jours :
   - libellé « lundi 21/09 » (en français, affiché avec une majuscule) ;
   - `aujourdhui` ;
   - nom du férié (ou null) ;
   - lignes des pointages dont l'entrée tombe ce jour-là ;
   - total du jour = `totalTravaille(pointages du jour)`.

   Format d'une ligne :
   - `HH:mm → HH:mm (durée)` ;
   - si la sortie tombe un autre jour : `HH:mm → dd/MM HH:mm (durée)` ;
   - pointage ouvert : « en cours » (il a toujours moins de 12h) ;
   - avec un commentaire : ` — commentaire` à la fin.
5. Totaux, calculés **entre lundi 00:00 et lundi suivant 00:00** sur `pointagesAvecVeille` : `total = totalEntre`, `nuit = totalNuitEntre`, `decompte = decompter(total, contrat)`, `auCompteur = heuresSupAuDelaDuContrat(total, contrat)`.
6. `tempsPartiel = contrat < 35h` : afficher les lignes d'heures complémentaires.
7. `travailleurDeNuit = estTravailleurDeNuit(pointages)` ; `reposNuit = travailleurDeNuit ? nuit / 100 : 0`.
8. Alertes (voir 6.3) et infos paie (jours fériés travaillés, voir 6.4).
9. `heuresSupReportables` = semaine terminée (dimanche < aujourd'hui) **et** aucun pointage ouvert **et** `auCompteur > 0` **et** pas déjà reportée.

### 6.3 Alertes HCR d'une semaine (`getAlertes`)
`mineur = employe.estMineurLe(lundi)`. `qui` vaut :
- « un salarié de moins de 18 ans » pour un mineur ;
- sinon « un cuisinier », « un veilleur de nuit » ou « ce poste ».

| Contrôle | Condition | Message |
|---|---|---|
| Jour | `totalParJour[J] > dureeMaxJour` | « lundi 21/09 : 14h00 travaillées (maximum 11h30 par jour pour ce poste) » |
| Semaine | `total > dureeMaxSemaine` | « Semaine : 50h00 travaillées (maximum 48h00 pour ce poste) » |
| Repos | `reposTropCourts(pointagesAvecVeille, reposMinimum)`, jours ≥ lundi | « mardi 22/09 : seulement 8h00 de repos depuis la journée précédente (minimum 11h00) » |
| Nuit mineur | `nuitsInterditesMineur(pointages)` | « vendredi 24/07 : travail entre 23h30 et 6h, interdit pour un salarié de moins de 18 ans » |

### 6.4 Jours fériés travaillés
Pour chaque jour d'entrée férié (sans doublon), le message commence par « <nom> (dd/MM) travaillé : » puis :
- 1er mai : « heures payées double. » ;
- au moins un an d'ancienneté ce jour-là : « jour férié garanti HCR, à compenser (repos ou indemnité). » ;
- sinon : « pas de compensation garantie (moins d'un an d'ancienneté). »

### 6.5 Récap mensuel (`getRecapMois(jour)`)
Pour chaque employé (on ignore un employé inactif sans pointage ce mois-là) :
- `pointages` = entrées du 1er au dernier jour du mois ;
- total et nuit : `totalTravaille` et `totalNuit` de ces pointages (rattachés au **mois de l'entrée**) ;
- pour chaque **lundi qui tombe dans le mois** :
  - `decompte += decompter(totalEntre(semaine coupée), contrat)` ;
  - si `estTravailleurDeNuit(pointages de la semaine)` : `reposNuit += totalNuitEntre(semaine) / 100` ;
- fériés travaillés : « 01/05 (payé double), 14/07 » ;
- nombre de pointages ouverts : texte « n pointage(s) sans sortie, non compté(s) ».

### 6.6 Tableau de bord
- **Aujourd'hui**, pour chaque employé actif, **une seule** colonne, dans cet ordre de priorité :
  1. pointage ouvert → **Présents** « Prénom Nom (depuis HH:mm) » ;
  2. absence validée couvrant aujourd'hui → **Absents** « Prénom Nom (Type) » ;
  3. a pointé aujourd'hui → **Journée terminée** ;
  4. sinon → **Sans pointage**.
- **Planning du jour** : pour chaque créneau d'aujourd'hui (trié par heure de début), une ligne « Prénom Nom : HH:mm → HH:mm » dans **Prévus aujourd'hui**. Si le créneau a déjà commencé (`debutComplet < maintenant`), sans pointage aujourd'hui ni absence validée : **Prévus mais pas pointés** « Prénom Nom — prévu à HH:mm, pas de pointage ».
- **Demandes en attente** : les demandes `SOUMISE`, triées par date de début.
- **Convention HCR**, pour chaque employé actif :
  - les alertes de la semaine précédente, puis celles de la semaine en cours, préfixées « Prénom Nom — » ;
  - si les heures sup de l'année civile dépassent 360h : « Prénom Nom — 372h00 d'heures sup en 2026 : contingent annuel de 360h dépassé ».

  Heures sup de l'année = somme de `decompter(totalEntre(semaine)).heuresSup` pour les semaines dont le lundi tombe dans l'année.
- **Anomalies** :
  - **Sorties automatiques à valider** : les pointages avec `sortieAutomatique = true`, du plus récent au plus ancien. Chaque ligne affiche « Prénom Nom — entré le dd/MM à HH:mm, clôturé à HH:mm » (lien vers sa semaine), avec un formulaire POST `/pointages/{id}/valider-sortie` (champ `commentaire` obligatoire, bouton « Valider ») ;
  - **Prévus hier mais sans pointage** : pour chaque créneau d'hier, « Prénom Nom (horaires) » si l'employé n'a ni pointage ni absence validée hier (sans doublon).

### 6.7 Demandes d'absence
- **Types proposés** dans le formulaire du salarié (`getTypesProposes()`) : tous les `TypeAbsence`, **sauf RTT** si `rttActives` est faux.
- **Soumettre** (employé connecté), dans cet ordre :
  1. type, début ou fin manquant → « Le type, la date de début et la date de fin sont obligatoires » ;
  2. type RTT et `rttActives` faux → « L'hôtel n'accorde pas de RTT » ;
  3. type ≠ MALADIE et début < aujourd'hui → « Une demande ne peut pas commencer dans le passé (sauf maladie) » ;
  4. règles communes (ci-dessous) ;
  5. vérification du solde : CP/RTT, compteur de `periodeDe(debut)`, sinon « Aucun compteur pour la période juin X → mai X+1 : le manager doit d'abord l'initialiser » ; si `nombreDeJours > solde` → « Solde insuffisant : %d jours ouvrables demandés, %.1f disponibles ». **Rien n'est débité à ce stade.**
- **Règles communes** (soumission et saisie d'une maladie), dans cet ordre :
  1. employé inactif → « Un employé désactivé ne peut plus soumettre de demande » ;
  2. fin < début → « La date de fin doit être après la date de début » ;
  3. `nombreDeJours == 0` → « Cette période ne contient aucun jour ouvrable (ni le dimanche ni les jours fériés ne sont décomptés) » ;
  4. pour chaque autre demande `SOUMISE` ou `VALIDEE` du même employé qui chevauche : **sauf** si c'est une maladie qui chevauche un **CP validé et déjà commencé** (`debut <= aujourd'hui`) → « Cette période chevauche une autre demande : <Type> <période> ».
- **Annuler** (employé), en transaction :
  1. la demande n'est pas à lui → « Cette demande ne vous appartient pas » ;
  2. début ≤ aujourd'hui → « Une absence déjà commencée ne peut plus être annulée » ;
  3. `annuler()` ; si elle était validée, recréditer (mouvement « … annulé », variation positive).
- **Valider** (manager), en transaction :
  1. `valider(manager)` ;
  2. CP/RTT : débit (peut échouer, « Solde CP insuffisant… »), mouvement « … validé », variation négative ;
  3. si MALADIE : rendre les CP (voir ci-dessous).
- **Refuser** : motif vide → « Un motif est obligatoire pour refuser une demande » ; sinon `refuser()`.
- **Saisir une maladie** (manager) : dates manquantes → « Les dates de début et de fin sont obligatoires » ; puis règles communes, validation directe (pas de débit), et on rend les CP.
- **Rendre les CP pendant une maladie** : pour chaque CP validé et commencé qui chevauche l'arrêt :
  - `jours = joursOuvrables(max(débuts), min(fins))` ;
  - si `jours > 0` : crédit CP, mouvement « Arrêt maladie pendant le <libellé du CP> : n j rendus ».
- Tous les compteurs utilisés sont ceux de `periodeDe(debut de la demande)`.

### 6.8 Compteurs
- **Initialiser** (période, CP, RTT) : crée le compteur s'il n'existe pas, remplace les soldes. **RTT absent (null) → on garde le solde RTT actuel.** Mouvement « Saisie manuelle des soldes (juin X → mai X+1) », avec la **différence** par rapport à l'ancien solde.
- **Acquisition** (mois `AAAA-MM`) :
  1. si le dernier jour du mois ≥ aujourd'hui → « Le mois n'est pas terminé » ;
  2. pour chaque employé présent ce mois-là (entrée ≤ fin du mois et (pas de sortie ou sortie ≥ début du mois)) :
     - compteur de `periodeDe(1er du mois)`, créé s'il manque ;
     - si un mouvement existe déjà avec ce `mois` → ignoré ;
     - sinon `joursAcquis(...)` avec ses absences **validées**, crédit CP, mouvement « CP acquis en <mois année> » (avec `mois` = 1er du mois).
  3. Flash : « CP acquis crédités à n employé(s). »
- **Ouvrir la période** (`ouvrirPeriode(auteur)`) : `periode = periodeDe(aujourd'hui)`. Pour chaque employé **actif** :
  1. `nouveau` = son compteur de `periode`, créé s'il n'existe pas ;
  2. si `nouveau.reportEffectue` → on passe à l'employé suivant ;
  3. `ancien` = son compteur de `periode − 1` ; s'il existe et qu'un de ses soldes n'est pas nul :
     - `ancien.solder()` + mouvement « Soldes reportés sur la période juin P → mai P+1 » (−CP, −RTT, −HS) ;
     - `nouveau` crédité des mêmes valeurs + mouvement « Report des soldes de la période juin P-1 → mai P » (+CP, +RTT, +HS) ;
  4. `nouveau.marquerReportEffectue()`.

  Renvoie le nombre de compteurs ouverts. **Aucune suppression automatique.**
- **Régulariser les acquisitions** (`regulariserAcquisitions(employe, debut, fin, auteur)`), pour chaque mois de `debut` à `fin` :
  - compteur de `periodeDe(1er du mois)` ; s'il n'existe pas, ou s'il n'y a **aucun** mouvement avec ce `mois` → rien (mois pas encore acquis) ;
  - `credite` = somme des `jours_conges_payes` des mouvements de ce `mois` ;
  - `attendu = joursAcquis(mois, entrée, sortie, absences validées)` ;
  - `difference = arrondi(attendu − credite, 2 décimales)` ; si ≠ 0 : `crediterCongesPayes(difference)` (négatif possible) + mouvement « Régularisation des CP acquis en <mois année> (absence déclarée après coup) » avec `mois`.

  Appelée par `DemandeAbsenceService` **après enregistrement** d'une demande validée de type **MALADIE ou SANS_SOLDE** (dans `valider` et `saisirMaladie`).
- **Régulariser les heures sup** :
  - `HeuresService.regulariserHeuresSup(employe, jour, auteur)` : si la semaine de `jour` a déjà été reportée, recalcule `auCompteur = heuresSupAuDelaDuContrat(totalEntre(semaine coupée), contrat)` et appelle `CompteurEmployeService.regulariserHeuresSup(employe, lundi, auCompteur, auteur)` ;
  - celle-ci calcule `reporte` = somme des `heures_sup` des mouvements de cette `semaine` et `difference = auCompteur − reporte` ; si ≠ 0 : `ajouterHeuresSup(difference)` + mouvement « Régularisation des heures sup de la semaine du dd/MM/yyyy (pointage corrigé) : ±XhYY » avec `semaine`.

  Appelée par `PointageService.corriger` **après** enregistrement, pour la semaine de l'**ancienne** entrée puis celle de la **nouvelle**.
- **Report automatique des heures sup** (`reporterHeuresSupAutomatiquement(auteur)`) :
  - `dernierLundi` = lundi de la semaine de `aujourd'hui − 13 jours` (semaine finie depuis au moins 7 jours) ;
  - `premierLundi = dernierLundi − 4 semaines` (5 semaines de rattrapage) ;
  - pour chaque employé actif et chaque lundi de cette plage : si la semaine n'est pas encore reportée, on appelle `reporterHeuresSup`. Une `ValidationException` (pas d'heures sup, pointage ouvert…) est ignorée.
- **Report des heures sup**, dans cet ordre :
  1. semaine non terminée → « La semaine n'est pas terminée » ;
  2. pointage ouvert → « La semaine contient un pointage sans sortie : corrigez-le d'abord » ;
  3. `auCompteur == 0` → « Aucune heure sup au-delà du contrat cette semaine » ;
  4. déjà reportée → « Les heures sup de cette semaine ont déjà été reportées ».

  Puis on ajoute au compteur de `periodeDe(lundi)`, créé s'il manque, avec le mouvement « Heures sup de la semaine du dd/MM/yyyy : +XhYY » (`semaine` = lundi).
- **Affichage des compteurs** : une ligne par compteur de la **période courante** (`periodeDe(aujourd'hui)`), avec les valeurs de la période précédente si elles existent. Admin : triés par nom puis prénom.

### 6.9 Employés et comptes
- **Création** :
  1. validations : nom, prénom, email (format), poste, type de contrat, date d'entrée et durée (> 0) obligatoires ; email déjà pris → erreur sur le champ « Un employé avec cet email existe déjà » ;
  2. durée hebdo : heures décimales converties en minutes (arrondi) ;
  3. rôle `SALARIE` ;
  4. mot de passe temporaire : **12 caractères** tirés au hasard (générateur cryptographique) dans `ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789`, sans caractère ambigu. Il est affiché **une seule fois** à l'admin (flash sur `/liste-employe`) et stocké haché.
- **Modification** (PUT JSON) : email déjà pris par un **autre** employé → 400 « Un employé avec cet email existe déjà » ; date de sortie avant l'entrée → 400 « La date de sortie ne peut pas être antérieure à la date d'entrée ».
- **Réinitialisation du mot de passe** : un nouveau mot de passe temporaire, renvoyé en JSON.
- **Mon compte**, dans cet ordre :
  1. champs obligatoires ;
  2. confirmation différente → « La confirmation ne correspond pas au nouveau mot de passe » ;
  3. ancien mot de passe faux → « Le mot de passe actuel est incorrect » ;
  4. nouveau mot de passe de moins de 8 caractères → « Le nouveau mot de passe doit contenir au moins 8 caractères » ;
  5. succès → flash « Mot de passe mis à jour. »

### 6.10 Planning
- **Grille manager** (`getPlanningSemaine(jour)`). Données chargées :
  - `lundi` et `dimanche` de la semaine ;
  - créneaux de la semaine, triés par jour puis heure de début ;
  - demandes **validées** qui touchent la semaine (`absences`) ;
  - demandes **en attente** (`SOUMISE`) qui touchent la semaine (`enAttente`) ;
  - pointages dont l'entrée est aujourd'hui.

  Colonnes : 7 dates, libellé « lundi 21/09 », nom du férié ou null.

  Une ligne par **employé actif** : nom, poste, 7 cases, total prévu (somme des `duree`), contrat, alertes (ci-dessous) et `comparaisonContrat` : `SOUS` si prévu < contrat, `EGAL` si prévu = contrat, sinon `AU_DESSUS`.

  Chaque **case** contient :
  - `creneaux` : les créneaux de l'employé ce jour-là ;
  - `absence` : la demande validée qui couvre ce jour, ou null ;
  - `demandeEnAttente` : la demande en attente qui couvre ce jour, ou null ;
  - `conflit` = `absence != null` et au moins un créneau ;
  - `statutDuJour`, **seulement pour aujourd'hui** :
    - `PRESENT` s'il a un pointage ouvert entré aujourd'hui ;
    - sinon `POINTE` s'il a un pointage fermé aujourd'hui ;
    - sinon `NON_POINTE` si un de ses créneaux du jour a commencé (`debutComplet < maintenant`) ;
    - sinon null.

  **Effectif prévu**, pour chacun des 7 jours :
  - nombre d'employés ayant au moins un créneau ;
  - somme des heures prévues ;
  - répartition par poste : « POSTE n » triés par nom de poste, séparés par « · » ;
  - nombre de cases avec une absence validée.
- **Alertes du planning d'un employé** :
  - `prevusAvecVeille` = ses créneaux de `lundi − 1` à `dimanche`, convertis par `enPointagePrevu()` ;
  - `prevus` = les mêmes, sans le dimanche précédent ;
  - `total = totalEntre(prevusAvecVeille, lundi 0h, lundi suivant 0h)` ;
  - puis **les mêmes alertes que 6.3** : `getAlertes(employe, lundi, prevus, prevusAvecVeille, total)`.
- **Mon planning** (`getMonPlanning(moi, jour)`), pour chacun des 7 jours :
  - libellé, aujourd'hui, férié, absence ;
  - `mesCreneaux` : « horaires », ou « horaires — note » s'il y a une note ;
  - `collegues` : les autres créneaux du jour, « Prénom Nom (POSTE) : horaires », dans l'ordre de début.

  - `absents` : pour chaque absence **validée** d'un **autre** employé qui couvre ce jour, « Prénom Nom (<libellé>) ». Le libellé vaut « Congé payé » pour `CONGE_PAYE`, « Maladie » pour `MALADIE`, **« Absent »** pour tout autre type. Aucune autre information : pas de dates, pas de motif, pas de demande en attente.

  En plus : le total prévu de la semaine et le contrat. Un jour sans créneau ni absence s'affiche « Repos ».
- **Ajouter un créneau**, dans cet ordre :
  1. employé inconnu → « Employé introuvable » ;
  2. jour, début ou fin manquant → « Le jour, l'heure de début et l'heure de fin sont obligatoires » ;
  3. note vide → null ;
  4. règles du créneau (ci-dessous) ;
  5. enregistrement, flash « Créneau ajouté. »
- **Règles d'un créneau**, dans cet ordre :
  1. employé inactif → « Un employé désactivé ne peut pas être planifié » ;
  2. `debut == fin` → « L'heure de fin doit être différente de l'heure de début » ;
  2 bis. `duree > 12h` → « Un créneau ne peut pas dépasser 12h » ;
  3. pour chaque créneau du même employé de `jour − 1` à `jour + 1` qui `chevauche` → « Ce créneau chevauche un autre créneau de Prénom Nom : <horaires> le dd/MM/yyyy » ;
  4. absence validée ce jour-là → « Absence validée pour Prénom Nom le dd/MM/yyyy (<Type>) ».
- **Supprimer** : id inconnu → « Créneau introuvable » ; sinon suppression, flash « Créneau supprimé. »
- **Copier la semaine précédente** (en transaction) : pour chaque créneau de `lundi − 7` à `lundi − 1`, on crée la même chose à `jour + 7`. On applique les règles du créneau ; un créneau refusé est **ignoré en silence**. Flash « n créneau(x) copié(s) depuis la semaine précédente. »
- **Créneaux du jour** (tableau de bord) : créneaux dont `jour` = ce jour.

### 6.11 Exports comptables
**Calcul partagé avec l'écran.** `HeuresService.calculerRecapMois(jour)` renvoie, pour chaque employé, des durées **brutes** (`RecapEmploye` : employé, total, nuit, décompte HS/HC, repos de nuit, texte des fériés travaillés, nombre de pointages ouverts).
- `getRecapMois()` (écran) les met en forme en « 7h30 ».
- `exporterRecapPaie()` les met en forme en heures décimales « 7,50 ».

Les deux affichent donc toujours les mêmes chiffres.

**Outil `Csv`** (fonctions pures) :
```
BOM = "\uFEFF"                                  // en tête de chaque fichier
ligne(v1, v2, …) = echapper(v1) + ";" + echapper(v2) + … + "\r\n"
ligne(liste)     = pareil à partir d'une liste (colonnes facultatives, ex : RTT)
echapper(v) = v == null ? "" : (v contient ; " \n ou \r ? '"' + v avec " doublés + '"' : v)
heures(d)  = format fr "%.2f" de minutes(d) / 60      // 450 min → "7,50" ; 9100 min → "151,67"
nombre(x)  = format fr "%.2f" de x                     // 2.5 → "2,50"
```

**Réponse HTTP** : `200`, `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="<type>-AAAA-MM.csv"`. Le corps est le texte encodé en UTF-8, BOM compris.

**Paramètre `mois`** : `AAAA-MM` → 1er jour du mois. Absent ou vide → 1er jour du mois précédent.

**1. Récap paie** (`recap-paie`) : en-tête, puis une ligne par `RecapEmploye` (même ordre que l'écran).

| Colonne | Valeur |
|---|---|
| Matricule | id de l'employé |
| Nom, Prénom, Poste | |
| Contrat (h/sem.) | `heures(contrat)` |
| Heures travaillées | `heures(total)` |
| dont heures de nuit | `heures(nuit)` |
| HS +10 %, HS +20 %, HS +50 % | `heures(...)` |
| H. compl. +10 %, H. compl. +25 % | `heures(...)` |
| Repos compensateur nuit | `heures(...)` |
| Fériés travaillés | texte |
| Jours CP, Jours RTT (**seulement si `rttActives`**), Jours maladie, Jours sans solde, Jours récup. férié | voir « jours dans le mois » ci-dessous |
| Pointages sans sortie (non comptés) | nombre |

**Jours d'un type dans le mois** : pour chaque absence **validée** de l'employé et de ce type qui touche le mois, on prend l'intersection `[max(début, 1er du mois), min(fin, dernier jour)]` :
- CP ou RTT : `joursOuvrables(intersection)` ;
- autres types : jours calendaires de l'intersection.

**2. Pointages** (`pointages`) : pointages dont l'entrée tombe dans le mois, triés par nom, prénom, entrée.

Colonnes :
- Matricule, Nom, Prénom ;
- Date (de l'entrée, dd/MM/yyyy) et Entrée (HH:mm) ;
- Date de sortie (vide si ouvert) et Sortie (« SANS SORTIE » si ouvert) ;
- Durée (h) = `heures(duree)` ; dont nuit (h) = `heures(heuresDeNuit)`, 0,00 si ouvert ;
- Jour férié (nom ou vide) ;
- Commentaire.

**3. Absences** (`absences`) : absences **validées** qui touchent le mois, triées par nom puis date de début.

Colonnes :
- Matricule, Nom, Prénom, Type (libellé), Du, Au ;
- Jours au total = `nombreDeJours()` ;
- Jours dans le mois (règle ci-dessus) ;
- Unité : « jours ouvrables » pour CP/RTT, « jours calendaires » sinon ;
- Validée par (prénom nom du validateur) et Validée le (dd/MM/yyyy).

**4. Compteurs** (`compteurs`) : compteurs de la période `periodeDe(1er du mois)`, triés par nom puis prénom.

Colonnes :
- Matricule, Nom, Prénom ;
- Période (« juin X → mai X+1 ») ;
- Solde CP (jours ouvrables) = `nombre(solde)` ; Solde RTT (jours) = `nombre(solde)`, **seulement si `rttActives`** ;
- Heures sup cumulées (h) = `heures(...)` ;
- Soldes au (date du jour).

### 6.12 Tâches automatiques (`TachesAutomatiques`)
**Toutes les 5 minutes** (`@Scheduled(cron = "0 */5 * * * *")`) : `pointageService.fermerPointagesDepasses()`, la sortie obligatoire après 12h.

Déclenchées **chaque nuit à 1h00** (`@Scheduled(cron = "0 0 1 * * *")`) **et au démarrage** de l'application (`ApplicationReadyEvent`), avec l'auteur « automatique ». Dans l'ordre :
0. `pointageService.fermerPointagesDepasses()`, avant tout calcul ;
1. `compteurEmployeService.ouvrirPeriode("automatique")` ;
2. `compteurEmployeService.acquerirConges(aujourd'hui − 1 mois, "automatique")` : le mois précédent, déjà terminé ;
3. `heuresService.reporterHeuresSupAutomatiquement("automatique")`.

Une ligne de journal résume ce qui a été fait. Chaque étape est **idempotente** :
- `reportEffectue` pour l'ouverture de période ;
- mouvement avec `mois` pour l'acquisition ;
- mouvement avec `semaine` pour les heures sup.

Nécessite `@EnableScheduling` sur la classe de l'application.

### 6.13 Paramètres de l'hôtel (RTT)
- `ParametresService.rttActives()` lit la ligne d'id 1, créée avec `rtt_actives = false` si elle n'existe pas.
- `definirRttActives(bool)` enregistre. Flash :
  - activation : « RTT activés : le type RTT est proposé aux salariés et les soldes RTT sont affichés. »
  - désactivation : « RTT désactivés : le type RTT et les colonnes RTT sont masqués. »
- Quand `rttActives` est **faux**, masquer (`th:if="${rttActives}"`) :
  - dans Mes congés et Congés (RH) : colonnes « Solde RTT » (période en cours et précédente), le `colspan` des en-têtes de groupe passe de 3 à 2 ;
  - dans les deux historiques de mouvements : colonne « RTT » (le `colspan` de la ligne vide diminue de 1) ;
  - le champ « Solde RTT » du formulaire de saisie ;
  - le badge « RTT » de la légende du planning ;
  - les titres, qui deviennent « Compteurs congés payés » et « Mes congés payés » ;
  - dans l'accueil, les textes sans « RTT » ;
  - dans l'aide du formulaire de demande : « Les congés payés sont décomptés… » au lieu de « Congés payés et RTT… » ;
  - dans les exports : les colonnes « Jours RTT » et « Solde RTT ».
- Ne change **pas** : les calculs, l'ouverture de période (le RTT est reporté quand même), l'affichage d'une absence RTT déjà validée (planning, « Absent » pour les collègues).

---

## 7. Pages et affichage

- Mise en page : menu vertical à gauche. Section personnelle : Ma pointeuse, Mes congés, Mes demandes d'absence, Mon compte. Section « Administration », pour l'admin seulement : Tableau de bord, Demandes d'absence (avec pastille), Employés, Ajouter un employé, Congés (RH), Pointages (RH), Récap mensuel (paie). En bas : Déconnexion.
- Style : Tailwind CSS 4, palette *slate*. Pour une reproduction exacte de l'apparence, copiez les classes des templates Thymeleaf dans les templates Twig.
- Messages : bloc vert (`succes`) et bloc rouge (`erreur`) en haut de page (`fragments/messages.html`).
- **Vue semaine** (`fragments/semaine-pointages.html`, utilisée par `/mon-pointage` et `/pointages/semaine/{id}`) :
  - en-tête « Du … au … » et boutons ← Semaine précédente / Cette semaine / Semaine suivante → ;
  - tableau Jour / Pointages / Total, jour courant surligné, badge violet pour un férié, total vide pour un jour sans pointage ;
  - pied de tableau : Total de la semaine, dont heures de nuit, Durée du contrat, [travailleur de nuit : repos compensateur], [temps partiel : heures complémentaires +10 % / +25 %], heures sup +10 % / +20 % / +50 % ;
  - encadré orange « ⚠ Convention HCR » (alertes) et encadré violet « Pour la paie » (fériés travaillés) ;
  - note explicative en bas.
- **Page semaine manager** : vue semaine, puis le bouton « Reporter les heures sup au compteur (XhYY) » (ou « ✓ Les heures sup de cette semaine ont été reportées au compteur », ou une explication), puis le tableau des corrections (Le / Par / Avant / Après / Motif).
- **Récap mensuel** : colonnes Employé (lien vers sa semaine), Heures travaillées, dont nuit, HS +10 %, HS +20 %, HS +50 %, H. compl. +10 %, H. compl. +25 %, Repos nuit, Fériés travaillés, À vérifier.
- **Planning manager** (`planning/gestion.html`) :
  - navigation entre les semaines + bouton « Copier la semaine précédente » ;
  - **légende** : créneau, nuit, chaque type d'absence, en attente, jour férié, conflit, présent / pointé / pas pointé.
  - **Couleurs des absences** (fragment `fragments/absence.html`, `badge(demande, enAttente)`) :

    | Type | Couleur |
    |---|---|
    | Congé payé | vert (emerald) |
    | Maladie | rouge (rose) |
    | RTT | bleu (sky) |
    | Récup. jour férié | rose (fuchsia) |
    | Sans solde et autres | gris (slate) |

    En attente : même couleur, fond blanc, **bordure pointillée**, texte « <Type> (en attente) ».
  - grille : colonne Employé (nom + poste), une colonne par jour (libellé + badge férié violet ; colonne d'aujourd'hui sur fond bleu clair), colonne « Prévu / contrat ».
  - Contenu d'une case, dans l'ordre :
    1. badge de l'absence validée ;
    2. badge pointillé de la demande en attente ;
    3. en cas de conflit : case sur fond rouge clair, bordée de rouge, avec « ⚠ planifié pendant l'absence » ;
    4. chaque créneau : étiquette « horaires » (gris clair, ou **bleu foncé / texte blanc** si `deNuit`), bouton ✕, note en italique dessous ;
    5. aujourd'hui : « ● présent » (vert), « ✓ pointé » (gris) ou « ✗ pas pointé » (rouge) ;
    6. le lien « + » vers `/planning?semaine=<lundi>&employeId=<id>&jour=<date>#ajout` (pas de « + » un jour d'absence validée).
  - « Prévu / contrat » : pastille orange + « sous le contrat », verte + « conforme au contrat », ou bleue + « heures sup prévues » ;
  - pied de tableau **« Effectif prévu »** : par jour, « n pers. · XhYY », la répartition par poste, et « n absent(s) » en rouge s'il y en a ;
  - sous la ligne d'un employé qui a des alertes : une ligne orange « ⚠ Prénom Nom — <alerte> » ;
  - formulaire « Ajouter un créneau » (id `ajout`) : Employé (select, présélection de `employeId`), Jour (défaut : `jour` sinon le lundi), Début (défaut 08:00), Fin (défaut 16:00), Note, puis une aide.
- **Mon planning** (`planning/mon-planning.html`) :
  - en-tête « Du … au … — XhYY prévues (contrat XhYY) » et navigation ;
  - un bloc par jour (bordure bleue aujourd'hui), avec le libellé, les badges férié / absence, mes horaires ou « Repos », puis « Travaillent aussi ce jour-là », puis « Absents ce jour-là » (texte gris, sans couleur par type).
- **Exports comptables** (`exports.html`) : un formulaire GET avec le champ `mois` (`type="month"`) et 4 cartes, une par fichier (titre, description, bouton « ⬇ Télécharger » avec `formaction` vers la route du fichier). Sur le récap mensuel : un bouton « ⬇ Exporter pour le comptable (CSV) » vers `/exports/recap-paie?mois=<mois affiché>`.
- **Paramètres** (`parametres.html`) : un formulaire POST avec la case « L'hôtel accorde des RTT » (`name="rttActives" value="true"`, cochée si `rttActives`), un texte qui explique la convention HCR et l'effet du réglage, et un bouton « Enregistrer ». Lien « Paramètres » en bas du menu Administration.
- **Statut d'une demande** : pastille ambre (En attente), verte (Validée), rouge (Refusée) ou grise (Annulée).
- Durées toujours affichées au format « 7h30 ». Dates au format « dd/MM/yyyy », heures « HH:mm ».

---

## 8. Messages exacts

**Succès (flash)**
- « Demande envoyée à votre manager. »
- « Demande annulée. »
- « Demande validée. »
- « Demande refusée. »
- « Arrêt maladie enregistré. »
- « Compteur enregistré. »
- « CP acquis crédités à n employé(s). »
- « Heures sup reportées au compteur. »
- « Mot de passe mis à jour. »
- « RTT activés : le type RTT est proposé aux salariés et les soldes RTT sont affichés. »
- « RTT désactivés : le type RTT et les colonnes RTT sont masqués. »
- « Sortie automatique validée. »
- « Créneau ajouté. »
- « Créneau supprimé. »
- « n créneau(x) copié(s) depuis la semaine précédente. »

**Erreurs** : elles sont toutes citées dans la section 6. En plus :
- « Employé introuvable » ;
- « Demande introuvable » ;
- « Aucune fiche employé associée à ce compte ».

---

## 9. Jeux de tests à reproduire (61 tests)

À porter tels quels (PHPUnit). Dates au format `2026-09-21T08:00`. Le 21/09/2026 est un lundi.

**CalculHeures (18)**
| Test | Entrée | Attendu |
|---|---|---|
| journée simple | 08:00 → 16:30 | 8h30 |
| pointage ouvert | 08:00 → null | durée 0, nuit 0 |
| coupure | 10:00→14:00 + 18:00→22:30 | 8h30 |
| semaine vide | [] | 0 et 0 |
| pas de nuit | 08:00 → 16:00 | nuit 0 |
| exemple de la trame | 18:00 → 01:00 le lendemain | nuit 3h |
| nuit complète | 22:00 → 06:00 | nuit 8h |
| début tôt | 05:00 → 13:00 | nuit 2h |
| deux nuits | lun 20:00 → mer 08:00 | nuit 18h |
| semaine coupée | dim 20/09 22:00 → lun 21/09 06:00 | 2h (semaine du 14/09), 6h (semaine du 21/09), nuit 6h (semaine du 21/09) |
| nuit mineur | 18:00 → 00:30, plage 23:30–06:00 | 1h |
| moins de 35h | total 20h, contrat 35h | 0 sup, 0 comp |
| contrat 39h | 39h / 39h | sup10 = 4h, sup20 = 0, au compteur 0 |
| tranches | 46h / 35h | 4h / 4h / 3h |
| au compteur | 41h/39h → 2h ; 38h/35h → 3h | |
| temps partiel | 25h / 20h | comp10 = 2h, comp25 = 3h, sup 0, au compteur 0 |
| partiel > 35h | 38h / 20h | comp = 15h, sup10 = 3h |
| formatage | 450 min, 45 min, −120 min | « 7h30 », « 0h45 », « -2h00 » |

**ControlesHCR (9)**
- Maximum par jour : cuisine 11h, veilleur 12h, réception 11h30, mineur 8h.
- Coupure 09–15 + 18–23:30 → total 11h30.
- Repos 15–23 puis 07–12 le lendemain → 8h le 22/09.
- Coupure 09–14, 17–21 puis 09–14 le lendemain → aucun repos trop court.
- Deux nuits 22→06 → travailleur de nuit.
- Une nuit 22→06 + 18→23 → pas travailleur de nuit.
- 40h de nuit → repos compensateur de 24 min.
- Mineur 17:00→23:30 → aucune alerte ; 18:00→00:30 → alerte le 21/09.

**JoursFeries (5)**
- Pâques : 2025 = 20/04, 2026 = 05/04, 2027 = 28/03.
- 2026 : Lundi de Pâques 06/04, Ascension 14/05, Lundi de Pentecôte 25/05.
- 14/07 et 25/12 sont fériés, 11 fériés au total ; le 15/07 ne l'est pas (nom null).
- 1er mai = oui, 8 mai = non (pour `estPremierMai`).

**CalculConges (10)**
- Période : 31/05/2026 → 2025 ; 01/06/2026 → 2026 ; 15/03/2027 → 2026.
- Jours ouvrables : du 13/07 au 19/07/2026 → 5 (14 juillet) ; du 21/09 au 27/09/2026 → 6.
- Acquisition (entrée le 01/01/2020) :

  | Cas | Mois | Attendu |
  |---|---|---|
  | juillet complet | juillet | 2,5 |
  | septembre entier en maladie | septembre | 2,0 |
  | maladie du 01/09 au 15/09 | septembre | 2,25 |
  | sans solde du 16/09 au 30/09 | septembre | 1,25 |
  | CP tout le mois | septembre | 2,5 |
  | arrivée le 16/09 | septembre | 1,25 |
  | sortie le 15/09 | septembre | 1,25 |

**DemandeAbsence (6)**
- CP du lundi 21/09 au dimanche 27/09 → 6.
- CP du 21/09 au 03/10 → 12.
- CP du 13/07 au 19/07 → 5.
- RTT du lundi au dimanche → 6.
- CP un dimanche seul → 0.
- Maladie du lundi au dimanche → 7.

**CreneauPlanning (7)** — le 21/09/2026 est un lundi
| Test | Entrée | Attendu |
|---|---|---|
| journée | 08:00 → 16:00 | 8h, « 08:00 → 16:00 » |
| nuit | 22:00 → 06:00 | fin le 22/09 à 06:00, 8h, « 22:00 → 06:00 (lendemain) » |
| jusqu'à minuit | 17:00 → 00:00 | 7h |
| chevauchement | 08–16 et 15–20 le même jour | oui |
| se suivent | 08–14 et 14–20 | non |
| nuit / lendemain | lun 22:00 → 06:00 et mar 05:00 → 13:00 | oui |
| pointage prévu | 22:00 → 06:00 | entrée 21/09 22:00, sortie 22/09 06:00 |

**Csv (6)**
| Test | Entrée | Attendu |
|---|---|---|
| ligne | "Dupont", "Jean", "7,50" | `Dupont;Jean;7,50\r\n` |
| valeur vide | "a", null, "c" | `a;;c\r\n` |
| point-virgule | `Oubli; corrigé` | `"Oubli; corrigé"` |
| guillemets | `Dit "urgent"` | `"Dit ""urgent"""` |
| heures | 450 min, 15 min, 9100 min | `7,50`, `0,25`, `151,67` |
| nombre | 2.5 | `2,50` |

**Scénarios manuels vérifiés dans l'application** (à rejouer après le portage) :
- Jean (solde 9,5) demande 3 j de CP → OK ; un RTT qui chevauche → refusé ; 20 jours → solde insuffisant ; un RTT passé → refusé ; une maladie passée → OK ; fin avant début → refusé.
- Validation d'un CP → solde −3 ; annulation → +3 ; deux mouvements tracés.
- Refus sans motif → refusé.
- Acquisition août 2026 → Jean passe de 9,5 à 12,0 ; deuxième fois → 0 employé ; septembre (en cours) → « Le mois n'est pas terminé ».
- Maladie de Jean du 10 au 12/08 pendant son CP du 03 au 14/08 → 3 j rendus.
- Paul (cuisinier, 39h) avec 46h → 4h / 4h / 3h, 7h reportables au compteur.
- Lucas (20h) avec 25h → heures complémentaires 2h + 3h, pas de report.
- Camille (mineure) de 15h à 1h → alertes « 8h » et « 23h30 → 6h ».
- 14 juillet travaillé par Jean → « à compenser ».
- Nicolas (inactif) ne peut pas se connecter.
- Un salarié reçoit 403 sur les pages manager.
- **Planning** :
  - Jean voit ses horaires et ses collègues de chaque jour, et « Marie Lefevre (Congé payé) », « Paul Martin (Maladie) », « Lucas Petit (Absent) » dans « Absents ce jour-là » ;
  - grille manager : badges CP / maladie / RTT en couleur, 2 cases en conflit pour Marie, CP d'Emma « (en attente) » en pointillé la semaine suivante, créneaux de nuit de Lucas en bleu foncé, effectif et répartition par poste par jour, « Prévu / contrat » en 3 couleurs ;
  - alerte « seulement 10h00 de repos » pour Paul (fin à 23h, reprise à 9h) ;
  - alerte « 23h30 → 6h » pour Camille (créneau jusqu'à minuit) ;
  - ajout OK ; chevauchement avec une nuit de la veille refusé ; début = fin refusé ; créneau un jour de maladie refusé ;
  - copie de la semaine : les créneaux en conflit sont ignorés ;
  - suppression OK ; le lien « + » présélectionne l'employé et le jour ;
  - un créneau commencé sans pointage apparaît dans « Prévus mais pas pointés ».
- **Sortie automatique (12h)** :
  - au démarrage, les pointages de démo jamais fermés (Sophie entrée le 24/07 à 08:30, Marie à 07:58) sont clôturés à 20:30 et 19:58 et listés dans « Sorties automatiques à valider » ;
  - valider sans commentaire → refusé ; avec commentaire → l'alerte disparaît, trace « Sortie automatique validée : … » dans les corrections ;
  - Jean a un pointage ouvert depuis la veille 06:00 et badge : l'ancien est clôturé à 18:00 (sortie automatique), le badge ouvre un nouveau pointage ;
  - correction de 06:00 à 19:00 (13h) → « Un pointage ne peut pas dépasser 12h » ; créneau de 13h → « Un créneau ne peut pas dépasser 12h » ;
  - plus aucune alerte de contingent absurde (1159h) due à un oubli refermé des semaines plus tard.
- **Mises à jour automatiques** (au démarrage, le 25/09/2026) :
  - acquisition d'août : 2,5 j crédités aux 7 employés présents (Nicolas, parti fin juin, exclu) ; relancer août à la main → « 0 employé(s) » ;
  - ouverture de la période 2026 : Sophie voit 15 j de CP, 2 RTT et 1h30 d'heures sup reportés depuis la période 2025, Jean 1 RTT ; les compteurs 2025 sont mis à zéro ;
  - arrêt maladie d'Emma du 1er au 15 août déclaré après coup → « Régularisation des CP acquis en août 2026 » de −0,24 j ;
  - Paul : semaine du 20/07 à 46h reportée (+7h), puis pointage corrigé à 42h → régularisation de −4h00, compteur 7h45 → 3h45.
- **Paramètres (RTT)** :
  - réglage « non » par défaut : le formulaire du salarié propose CONGE_PAYE, MALADIE, SANS_SOLDE, RECUP_JOUR_FERIE ; aucune colonne « Solde RTT » ; pas de RTT dans la légende ; exports sans colonnes RTT ;
  - une demande de RTT envoyée quand même → « L'hôtel n'accorde pas de RTT » ;
  - saisie des soldes sans champ RTT → mouvement avec une variation RTT de 0 (solde inchangé) ;
  - réglage « oui » : RTT proposé, colonnes et exports RTT de retour, soldes existants intacts ;
  - un salarié sur `/parametres` → 403.
- **Exports** :
  - les 4 fichiers se téléchargent avec le bon nom et commencent par le BOM (octets `EF BB BF`) ;
  - juillet 2026 : les 8h03 de Jean donnent `8,05`, comme sur l'écran ; le pointage ouvert de Sophie donne « SANS SORTIE », `0,00` ;
  - septembre 2026 : Marie a 2 jours de CP (jours ouvrables), Paul 1 jour de maladie (calendaire), Lucas 1 RTT ;
  - un salarié reçoit 403.

---

## 10. Données de démo

`src/main/resources/data.sql`, rejoué à chaque démarrage. Mot de passe de tous les comptes : `password123`.

| id | Nom | Email | Rôle | Poste | Contrat | Entrée | Durée | Actif | Naissance |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Jean Dupont | jean.dupont@roomster.fr | SALARIE | RECEPTION | CDI | 2020-01-15 | 35h | oui | |
| 2 | Marie Lefevre | marie.lefevre@roomster.fr | SALARIE | HOUSEKEEPING | CDI | 2019-03-01 | 35h | oui | |
| 3 | Paul Martin | paul.martin@roomster.fr | SALARIE | CUISINE | CDD | 2023-06-01 | 39h | oui | |
| 4 | Sophie Bernard | sophie.bernard@roomster.fr | **ADMIN** | DIRECTION | CDI | 2015-09-01 | 35h | oui | |
| 5 | Lucas Petit | lucas.petit@roomster.fr | SALARIE | MAINTENANCE | EXTRA | 2024-01-10 | 20h | oui | |
| 6 | Camille Moreau | camille.moreau@roomster.fr | SALARIE | SALLE | APPRENTI | 2025-09-01 | 30h | oui | 2009-03-15 |
| 7 | Nicolas Girard | nicolas.girard@roomster.fr | SALARIE | RECEPTION | CDD | 2024-04-01 (sortie 2026-06-30) | 35h | **non** | |
| 8 | Emma Rousseau | emma.rousseau@roomster.fr | SALARIE | HOUSEKEEPING | CDI | 2021-11-08 | 35h | oui | |

**Planning de démo** : environ 35 créneaux sur la semaine en cours et le début de la suivante, **calculés à partir de la date du jour**. Le lundi de la semaine en cours est `CURRENT_DATE + (1 − jour ISO de la semaine)`, puis on ajoute un décalage de 0 à 8 jours.
- Jean : réception 07:00–15:00 ;
- Emma : 14:30–22:30 ;
- Paul : coupures 09–15 / 18–23, pour montrer l'alerte de repos ;
- Marie : 08–15 ;
- Lucas : nuits 22:00–06:00 ;
- Camille : 17–23 et 17–00:00, pour montrer l'alerte des mineurs ;
- Sophie : 09–17.

**Absences de démo sur la semaine en cours** (mêmes dates relatives) :
- Marie : CP **validé** jeudi-vendredi, alors qu'elle est planifiée, ce qui montre le conflit ;
- Lucas : RTT validé samedi (vu « Absent » par les collègues) ;
- Paul : maladie validée dimanche ;
- Emma : CP **en attente** lundi-mardi de la semaine suivante (badge pointillé).

En Symfony, recréez-les dans une fixture avec `new DateTimeImmutable('monday this week')`.

Compteurs, demandes et pointages : voir `data.sql`. Il y a 10 compteurs sur les périodes 2025 et 2026, 7 demandes (tous les types et statuts) et 7 pointages de juillet 2026 : journée complète, pointage ouvert, correction, nuit à cheval…

---

## 11. Correspondance Spring → Symfony

| Spring (Java) | Symfony (PHP) |
|---|---|
| `@Entity` JPA + repository Spring Data | Entité Doctrine + `ServiceEntityRepository` (méthodes `findBy…` ou QueryBuilder) |
| `findByEmployeIdAndEntreeBetweenOrderByEntree` | QueryBuilder : `p.employe = :e AND p.entree BETWEEN :debut AND :fin ORDER BY p.entree` |
| `LocalDate` / `LocalDateTime` | `DateTimeImmutable` (types Doctrine `date_immutable` / `datetime_immutable`) |
| `Duration` | **entier en minutes** (colonne `integer`) ; `formater()` fait l'affichage |
| enum Java | enum PHP 8.1 « backed » (`enum Poste: string`) + `enumType` Doctrine, colonne `VARCHAR` |
| `@Service` + injection par constructeur | service autowiré (`services.yaml` par défaut) |
| classes de calcul `final` avec méthodes `static` | classes `final` avec méthodes `static` (ou services sans état) |
| `@Transactional` | `$entityManager->wrapInTransaction(fn() => …)` ou un seul `flush()` en fin de méthode |
| `synchronized` sur `pointer()` | verrou : `LockFactory` (composant Lock) ou `SELECT … FOR UPDATE` |
| `ValidationException` + `@ControllerAdvice` | exception métier + listener `kernel.exception` → `Response(400, message)` pour les routes JSON |
| `RedirectAttributes.addFlashAttribute` | `$this->addFlash('succes', …)` puis `redirectToRoute` |
| `@ModelAttribute` global (`isAdmin`, `nbDemandesEnAttente`) | `is_granted('ROLE_ADMIN')` dans Twig + extension Twig ou variable globale pour le compteur |
| Thymeleaf (`th:each`, `th:if`, fragments) | Twig (`for`, `if`, `include`/`embed`) |
| `@RequestParam @DateTimeFormat(iso = DATE) LocalDate` | `$request->query->get('semaine')` puis `new DateTimeImmutable(...)`, ou `#[MapQueryParameter]` |
| Spring Security (`SecurityConfig`) | `security.yaml` : `form_login`, `access_control`, hacheur `bcrypt` ou `auto`, rôles `ROLE_ADMIN` / `ROLE_SALARIE` |
| compte `disabled` si employé inactif | `UserChecker::checkPreAuth()` qui lève une exception si l'employé est inactif |
| CSRF (cookie `XSRF-TOKEN` + en-tête) | formulaires : `csrf_token()` ; `fetch` : jeton en `<meta>` ou cookie + vérification `isCsrfTokenValid` |
| MapStruct (DTO ↔ entité) | conversion manuelle ou Serializer Symfony (groupes) |
| Bean Validation (`@NotBlank`, `@Email`…) | contraintes Validator (`#[Assert\NotBlank]`, `#[Assert\Email]`…) sur un DTO de formulaire |
| `data.sql` | Fixtures (`DoctrineFixturesBundle`) |
| JUnit | PHPUnit |
| `LocalDate.with(DayOfWeek.MONDAY)` | `$date->modify('monday this week')` (vérifier le dimanche : semaine ISO) |
| `LocalTime` (créneaux) | colonne `time_immutable` ; ou minutes depuis minuit (entier), plus simple pour « fin <= debut → lendemain » |
| lien « + » avec ancre `#ajout` | `path('planning', {...}) ~ '#ajout'` dans Twig |
| fragment `absence :: badge(demande, enAttente)` avec `th:switch` | macro Twig `badge_absence(demande, enAttente)` avec un `if` par type (écrire les classes Tailwind en entier) |
| entité `ParametresHotel` (une ligne, id 1) | entité Doctrine avec id fixe, ou un fichier de config si le réglage n'a pas besoin d'être modifié depuis l'écran |
| `@ModelAttribute("rttActives")` global | variable globale Twig via une extension (`getGlobals()`), alimentée par `ParametresService` |
| `@RequestParam(defaultValue = "false") boolean rttActives` | `$request->request->getBoolean('rttActives')` (false si la case est décochée) |
| `@RequestParam(required = false) Double soldeRtt` | `$request->request->get('soldeRtt')` puis `null` si absent ou vide |
| `ResponseEntity<byte[]>` + en-têtes (export CSV) | `Response` (ou `StreamedResponse`) + `HeaderUtils::makeDisposition('attachment', 'recap-paie-2026-09.csv')` et `Content-Type: text/csv; charset=UTF-8` |
| classe `Csv` | classe PHP `Csv` avec les mêmes fonctions (ou `fputcsv($f, $valeurs, ';')` puis `"\xEF\xBB\xBF"` en tête) ; `number_format($minutes / 60, 2, ',', '')` pour les heures |
| `@Scheduled(cron = "0 0 1 * * *")` + `@EventListener(ApplicationReadyEvent)` | commande Symfony (`app:taches-automatiques`) lancée par le **cron** du serveur chaque nuit (`0 1 * * * php bin/console app:taches-automatiques`), ou le composant **Scheduler** (`#[AsCronTask('0 1 * * *')]`) ; lancez-la aussi après chaque déploiement pour le rattrapage |
| champ `type="month"` → `LocalDate.parse(mois + "-01")` | `DateTimeImmutable::createFromFormat('!Y-m-d', $mois . '-01')` |
| `#temporals.createToday()` | `'today'\|date('Y-m-d')` comparé à `jour\|date('Y-m-d')` |
| `DateTimeFormatter.ofPattern("EEEE dd/MM", FRENCH)` | `IntlDateFormatter` (fr_FR, motif `EEEE dd/MM`) |

**Ordre de portage conseillé**
1. Enums et entités.
2. Fonctions pures et leurs 55 tests.
3. Sécurité.
4. Pointage.
5. Vue semaine.
6. Demandes et compteurs.
7. Planning (il réutilise les contrôles HCR et les absences).
8. Récap mensuel et tableau de bord.
9. Exports (ils réutilisent le calcul du récap mensuel).
10. Tâches automatiques et régularisations (en dernier : elles appellent des services déjà portés).
11. Pages restantes.

---

## 12. Pièges rencontrés

- **Enum en base (H2 + Hibernate 6)** : la colonne a été créée avec un type `ENUM` figé ; ajouter `VEILLEUR_NUIT` a provoqué « Value not permitted ». Contournement : `ALTER TABLE employe ALTER COLUMN poste VARCHAR(30)` au démarrage. **En Symfony, utilisez des `VARCHAR`** pour les enums.
- **Apostrophes dans Thymeleaf** : hors d'une expression `${…}`, `''` ne fonctionne pas pour échapper une apostrophe. La page a été coupée en plein rendu (réponse 200 mais HTML tronqué). Pour tester une page, vérifiez qu'elle se termine bien par `</html>`.
- **Flash et redirection** : un message flash n'est lu que par la page de redirection exacte (mêmes paramètres). Redirigez vers la même URL que celle affichée.
- **Semaine ISO** : le lundi de la semaine d'un dimanche est le lundi **précédent**.
- **Arrondis** : l'acquisition est arrondie au centième ; les soldes gardent les demi-journées.
- **Apostrophe dans un texte Thymeleaf** (bis) : « Prévus aujourd'hui » a dû être écrit avec l'apostrophe typographique ’. En Twig, pas de problème, mais pensez à `|e` et à l'échappement dans les attributs.
- **Classes Tailwind générées dynamiquement** : une couleur calculée en Java (ex : `"bg-" + couleur + "-100"`) n'est pas générée par Tailwind, qui ne lit que les templates. D'où un cas par type, avec les classes écrites en entier, dans le fragment `absence.html`. Même règle en Twig.
- **BOM dans le code source** : en Java, écrivez la constante `"\uFEFF"` (séquence d'échappement) et pas le caractère lui-même, qui est invisible. En PHP : `"\xEF\xBB\xBF"`.
- **Virgule décimale** : ne formatez pas les nombres avec la locale par défaut du serveur ; forcez le format français (`Locale.FRANCE` ; en PHP `number_format(..., 2, ',', '')`).
- **Nouvelle colonne non nulle sur une table existante** (`report_effectue`) : sans valeur par défaut, l'ajout échoue si la table contient déjà des lignes. D'où `columnDefinition = "boolean default false"`. En Doctrine : `options: ['default' => false]` dans la migration.
- **Régularisation après enregistrement** : les requêtes de recalcul relisent la base ; enregistrez (et flushez, en Doctrine) la demande validée ou le pointage corrigé **avant** de régulariser.
- **Oubli de sortie** : ne jamais refermer un vieux pointage « à l'heure actuelle » quand l'employé badge à nouveau. Ça crée des durées absurdes (1509h), qui polluent les heures sup, le contingent et les exports. D'où la clôture automatique à entrée + 12h.
- **Case à cocher décochée** : le navigateur **n'envoie pas** le champ. Il faut une valeur par défaut `false` côté serveur, sinon on obtient une erreur « paramètre manquant ».
- **Masquer une colonne dans un tableau** : pensez aux `colspan` des en-têtes de groupe et des lignes « Aucun… », sinon le tableau se décale.
- **Créneau jusqu'à minuit** : saisi 17:00 → 00:00, il est traité comme « finit le lendemain à 00:00 » (7h). C'est voulu.
- **Durées négatives** : `formater` affiche le signe ; un décompte ne descend jamais sous 0 (`positif`).
