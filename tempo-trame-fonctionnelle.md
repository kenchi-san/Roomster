# Tempo — Trame fonctionnelle

Gestion du personnel pour un hôtel : heures, pointages, absences, compteurs.
Chaque fonctionnalité est décrite avec son comportement attendu, ses règles et les points à ne pas oublier.

## État d'avancement (audité sur le code au 2026-08-15)

| # | Fonctionnalité | Statut |
|---|---|---|
| F1 | Gestion des employés | 🚧 Partiel (inchangé depuis le 30/07) |
| F2 | Pointage (badge entrée/sortie) | 🚧 Partiel *(nouveau : vue personnelle `/mon-pointage` depuis le 11/08)* |
| F3 | Calcul des heures | ❌ Non commencé |
| F4 | Demandes d'absence | 🚧 Partiel (logique en base non exposée, inchangé) |
| F5 | Compteurs et soldes | 🚧 Partiel *(nouveau : vue personnelle `/mes-conges` filtrée par utilisateur depuis le 11/08)* |
| F6 | Tableau de bord manager | ❌ Non commencé |
| F7 | Authentification et rôles | 🚧 Partiel *(nouveau depuis le 15/08 : mot de passe temporaire affiché à l'admin à la création, réinitialisation admin, changement de mot de passe self-service `/mon-compte`)* |

**Points bloquants à connaître** : le bouton "Supprimer" employé fait toujours une suppression physique en base, ce qui contredit explicitement la règle F1 de ne jamais supprimer un employé. Le badge entrée/sortie (F2) est désormais fonctionnel mais **ne vérifie pas que l'employé est actif** — un employé désactivé peut encore pointer via l'endpoint `/pointer-employe/{id}` (seule la page liste le filtre, pas le service ; `/mon-pointage/pointer`, ajouté le 11/08, hérite du même manque). L'entité `DemandeAbsence` existe toujours avec une partie de ses règles métier codées, mais aucun repository/service/contrôleur ne l'expose — F3, F5 (débit réel) et F6 en dépendent et restent bloquées tant que F3/F4 ne sont pas raccordés. **Ancien point bloquant (F7) résolu le 15/08** : un employé créé via `/ajout-employe` reçoit désormais un mot de passe temporaire affiché une fois à l'admin (bannière sur `/liste-employe`, à communiquer à la main — toujours pas d'email en v1) ; l'admin peut aussi le régénérer à tout moment (`/reset-password-employe/{id}`) et tout utilisateur connecté peut changer son propre mot de passe via `/mon-compte`.

---

## F1 — Gestion des employés

**Statut : 🚧 Partiel**

**Comportement attendu**
- [x] Créer, consulter, modifier une fiche employé (nom, prénom, email, poste, type de contrat, date d'entrée, durée hebdo contractuelle).
- [x] Désactiver un employé qui quitte l'hôtel (date de sortie + actif = false) plutôt que le supprimer. ✅ *corrigé depuis le 30/07 : `toggle-actif-employe` renseigne désormais `dateSortie` à la désactivation et la remet à `null` à la réactivation.* Reste contraire à la règle : un bouton "Supprimer" fait toujours une suppression physique réelle.
- [ ] Lister les employés actifs, filtrer par poste. *(toujours pas de filtre côté serveur ; un filtre "voir les inactifs" a été ajouté côté client en JS — masque les lignes déjà chargées, ne remplace pas un vrai filtre serveur, et il n'y a toujours aucun filtre par poste)*

**Règles métier**
- [x] L'email est unique : c'est l'identifiant de connexion. ✅ *confirmé depuis le 11/08 : `User.email` est désormais le username Spring Security (`CustomUserDetailsService`). Contrainte unique en base ; toujours non re-vérifiée côté service lors d'une modification — `updateEntityFromDto` ne relance pas `existsByUserEmail`.*
- [ ] Un employé désactivé ne peut plus pointer ni soumettre de demande. *(F2 existe maintenant mais `PointageService.pointer()` ne vérifie pas `employe.isActif()` — la règle n'est pas appliquée, y compris depuis `/mon-pointage/pointer` ; F4 reste non applicable, non implémenté)*
- [x] La date de sortie ne peut pas précéder la date d'entrée. ✅ *corrigé depuis le 30/07 : validée à la fois dans `toggleActifEmploye` et `editionEmploye` (renvoie 400 sinon).*

**Points à penser**
- Ne jamais supprimer physiquement un employé : ses pointages et absences passés doivent rester consultables (historique légal de paie).
- Que se passe-t-il si on modifie la durée hebdo contractuelle en cours d'année ? (v1 : la nouvelle valeur s'applique aux semaines futures ; noter la limite.)
- Prévoir un champ "manager" plus tard pour savoir qui valide les demandes de qui.

---

## F2 — Pointage (badge entrée/sortie)

**Statut : 🚧 Partiel** *(nouveau depuis le 30/07 : `PointageController`, `PointageService`, `PointageRepository`, `PointageDto`/`PointageMapper` sont implémentés et la page `/pointages` est fonctionnelle)*

**Comportement attendu**
- [x] L'employé "pointe" : si aucun pointage ouvert → création d'un pointage avec l'heure d'entrée ; si un pointage est ouvert → il est fermé avec l'heure de sortie. *(`PointageService.pointer()` : logique correcte, mais ne vérifie pas que l'employé est actif — voir règle F1 ci-dessus)*
- [ ] Un manager peut corriger un pointage (oubli de badge) avec un commentaire obligatoire expliquant la correction. *(le champ `commentaire` existe sur l'entité mais aucune route ne permet de le renseigner ni de modifier un pointage existant)*
- [ ] Consultation des pointages du jour / de la semaine par employé. *(`/pointages` (admin) montre le statut du jour pour tous les employés + un historique global paginé ; nouveau depuis le 11/08 : `/mon-pointage` donne à chaque salarié son propre statut + son propre historique paginé — mais toujours pas d'agrégat "semaine")*

**Règles métier**
- [x] Plusieurs pointages par jour sont normaux (coupure midi/soir en hôtellerie). *(pas empêché structurellement — chaque fermeture libère un nouveau pointage possible — mais non testé explicitement)*
- [x] Un pointage peut chevaucher minuit (service de nuit) : c'est une seule séquence, pas deux. *(fonctionne nativement, `entree`/`sortie` sont des `LocalDateTime`, pas de découpage par jour)*
- [ ] La sortie doit être postérieure à l'entrée. *(aucune validation explicite ; vrai par construction tant qu'il n'existe pas de correction manuelle — deviendra un vrai risque dès que la correction manager sera ajoutée)*
- [x] Un seul pointage ouvert à la fois par employé. *(assuré par `findByEmployeIdAndSortieIsNull`, mais pas de contrainte en base — une écriture concurrente pourrait créer un doublon)*

**Points à penser**
- Pointage oublié : un pointage ouvert depuis plus de X heures (ex. 14h) doit être signalé au manager plutôt que compter comme du temps travaillé.
- Toute correction manuelle doit être tracée (qui a corrigé, quand, pourquoi) — enjeu de confiance et de conformité.
- Fuseau horaire : rester en heure locale de l'hôtel, mais attention aux changements d'heure été/hiver (une nuit de 3h à 2h existe une fois par an).
- Arrondi éventuel : certaines conventions arrondissent au quart d'heure. V1 : à la minute, sans arrondi.

---

## F3 — Calcul des heures

**Statut : ❌ Non commencé** *(dépend de F2, lui-même absent ; aucune classe/méthode/test de calcul trouvée)*

**Comportement attendu**
- [ ] Pour un employé et une semaine (lundi→dimanche) : total des heures travaillées, heures supplémentaires, heures de nuit.
- [ ] Vue récapitulative mensuelle pour préparer la paie.

**Règles métier**
- [ ] Heures sup = total travaillé − durée hebdo contractuelle, si positif.
- [ ] Heures de nuit (convention HCR) : la portion de chaque séquence comprise entre 22h et 7h. Une séquence 18h→1h contient donc 3h de nuit (22h→1h).
- [ ] Un pointage encore ouvert n'entre pas dans le calcul (ou est signalé comme anomalie).
- [ ] Une semaine à cheval sur deux mois : les heures comptent dans la semaine, l'agrégat mensuel se fait au prorata des jours ou par convention (à trancher — v1 : rattacher chaque pointage au mois de sa date d'entrée).

**Points à penser**
- C'est le module à tester le plus rigoureusement : nuit à cheval sur minuit, coupures, semaine vide, semaine incomplète (embauche le mercredi).
- Séparer clairement calcul (fonctions pures, testables) et récupération des données (repository) — le calcul ne doit dépendre que d'une liste de pointages en entrée.
- Les majorations (10 %, 20 %, 50 %) relèvent de la paie : v1 compte les volumes d'heures, pas les montants.

---

## F4 — Demandes d'absence (congés, RTT, maladie)

**Statut : 🚧 Partiel** *(règles de cycle de vie codées dans l'entité `DemandeAbsence`, mais sans repository/service/contrôleur : totalement inaccessible depuis l'application)*

**Comportement attendu**
- [ ] L'employé soumet une demande : type, date de début, date de fin. *(aucune route/formulaire)*
- [ ] Il voit la liste de ses demandes avec leur statut, et peut annuler une demande soumise ou validée (avant son début). *(aucune route)*
- [ ] Le manager voit les demandes en attente et valide ou refuse (motif obligatoire en cas de refus). *(aucune route)*

**Règles métier**
- [ ] Cycle de vie : SOUMISE → VALIDÉE ou REFUSÉE ; SOUMISE ou VALIDÉE → ANNULÉE. *(méthodes `valider()`/`refuser()`/`annuler()` existent sur l'entité mais ne sont appelées par aucun contrôleur/service)*
- [ ] À la soumission d'un CP/RTT : vérifier que le solde est suffisant. *(aucun couplage avec `CompteurEmploye`)*
- [ ] Interdire le chevauchement avec une autre demande soumise ou validée du même employé. *(la méthode utilitaire `chevauche()` existe mais n'est jamais invoquée)*
- [ ] La maladie ne débite aucun compteur et peut être saisie a posteriori (par le manager, avec justificatif hors application). *(aucune logique différenciant les types)*
- [ ] Annulation d'une demande validée : re-créditer le compteur. *(`annuler()` ne touche à aucun compteur)*

**Points à penser**
- Le débit du compteur se fait-il à la validation ou à la soumission ? Recommandation : réserver à la soumission (le solde affiché est "disponible"), débiter définitivement à la validation, libérer au refus/annulation. V1 simple : débit à la validation seulement, en acceptant qu'un employé puisse soumettre deux demandes qui ensemble dépassent son solde.
- Décompte des jours : jours ouvrés ou calendaires ? En hôtellerie on travaille le samedi/dimanche — les CP se décomptent en jours ouvrables (lundi→samedi) dans la convention HCR. V1 : jours calendaires simples, en notant la limite.
- Une demande qui commence dans le passé doit-elle être possible ? Oui pour la maladie, non pour les CP (sauf saisie manager).
- Notifier le manager qu'une demande attend (v1 : simple compteur sur sa page ; email plus tard).

---

## F5 — Compteurs et soldes

**Statut : 🚧 Partiel** *(lecture/affichage des compteurs fonctionnel, avec un bonus comparatif N-1 non demandé par la spec ; mais pas d'initialisation applicative ni de débit/crédit réellement invoqué)*

**Comportement attendu**
- [x] Chaque employé voit ses soldes : congés payés, RTT, heures sup cumulées. ✅ *nouveau depuis le 11/08 : `/mes-conges` filtre réellement par utilisateur connecté (`CompteurEmployeService.getMesCompteurs(email)`), séparé de la vue globale `/conge-paye`.*
- [x] Le manager voit les compteurs de toute l'équipe. *(fonctionne ; désormais restreint à `ROLE_ADMIN` sur `/conge-paye` depuis le 11/08, plus seulement "par défaut faute de restriction d'accès")*
- [ ] Initialisation des compteurs en début de période (saisie manuelle des soldes de départ). *(aucun endpoint ; les seules valeurs viennent de `data.sql`)*

**Règles métier**
- [x] Un compteur par employé et par période de référence (année). *(contrainte unique en base `employe_id` + `annee`)*
- [ ] Le solde ne peut pas devenir négatif (le débit échoue si insuffisant). *(logique codée dans l'entité — `debiterCongesPayes`/`debiterRtt` — mais jamais invoquée par un service applicatif)*
- [ ] Les heures sup s'accumulent semaine après semaine (calculées par F3). *(impossible : F3 non implémenté)*

**Points à penser**
- Période de référence des CP en France : juin N → mai N+1, avec acquisition de 2,5 jours/mois. V1 : solde annuel saisi à la main, acquisition automatique en v2.
- Que faire des heures sup : payées ou récupérées ? V1 : simple cumul affiché, la décision reste humaine.
- Prorata pour les arrivées en cours d'année et les temps partiels : à documenter, saisie manuelle en v1.
- Historiser les mouvements de compteur (qui a débité quoi, quand, pour quelle demande) — sinon impossible d'expliquer un solde contesté. Fortement recommandé dès la v1 : une entité MouvementCompteur.

---

## F6 — Tableau de bord manager

**Statut : ❌ Non commencé** *(la page d'accueil est un simple menu de liens ; impossible d'agréger quoi que ce soit tant que F2/F3/F4 n'existent pas)*

**Comportement attendu**
- [ ] Vue du jour : qui est présent (pointage ouvert), qui est absent (demande validée couvrant aujourd'hui), qui n'a pas pointé.
- [ ] Demandes en attente de validation.
- [ ] Anomalies : pointages ouverts trop longs, employés sans pointage un jour ouvré.

**Points à penser**
- "Qui devrait être là" suppose un planning prévisionnel, qui n'existe pas en v1 → se limiter à présent/absent/inconnu.
- C'est la page d'accueil naturelle du manager : la construire en dernier, quand toutes les données existent.

---

## F7 — Authentification et rôles (après le cœur métier)

**Statut : 🚧 Partiel** *(implémenté le 11/08 : `spring-boot-starter-security`, `User.password` + `User.role`, `CustomUserDetailsService`, `SecurityConfig`. Deux commits séparés sur `creation_auth` : authentification puis rôles.)*

**Comportement attendu**
- [x] Connexion par email + mot de passe. ✅ *formulaire de login Spring Security par défaut (`/login`), `User.email` = username, mot de passe haché en BCrypt. CSRF réactivé (cookie `XSRF-TOKEN` lisible en JS, voir points à penser). Comptes de démo dans `data.sql` : mot de passe `password123` pour tous.*
- [ ] Rôle EMPLOYE : pointe, voit ses heures, ses soldes, gère ses demandes. *(rôle implémenté sous le nom `SALARIE`, pas `EMPLOYE` — à harmoniser si la nomenclature de la trame doit rester la référence. Pointe ✅ `/mon-pointage`, voit ses soldes ✅ `/mes-conges`, voit ses heures ❌ dépend de F3 non fait, gère ses demandes ❌ dépend de F4 non exposé — aucune route "mes demandes" n'existe encore côté employé ni manager.)*
- [x] Rôle MANAGER : tout voir, valider/refuser, corriger les pointages, gérer les fiches. *(implémenté sous le nom `ADMIN`. Tout voir ✅ `/liste-employe`, `/pointages`, `/conge-paye` réservés à `hasRole("ADMIN")`. Gérer les fiches ✅ `/ajout-employe`, `/edit-employe`, `/delete-employe`, `/toggle-actif-employe` idem. Valider/refuser une demande ❌ impossible, F4 non exposé. Corriger un pointage ❌ toujours aucune route, cf. F2.)*

**Règles métier**
- [x] Un manager est aussi un employé (il pointe, il prend des congés) : le rôle s'ajoute, il ne remplace pas. *(dans le modèle de données `User.role` est un enum à valeur unique, pas un ensemble de rôles — mais fonctionnellement respecté : `/mon-pointage` et `/mes-conges` sont ouvertes à tout utilisateur authentifié, admin compris. Le compte ADMIN de démo, Sophie Bernard, reste rattaché à une fiche `Employe` et peut donc s'en servir.)*
- [ ] Chaque page doit filtrer par utilisateur connecté : un employé ne doit jamais voir les données d'un autre. *(vrai uniquement pour les 2 pages qui existent aujourd'hui — `/mon-pointage` et `/mes-conges` dérivent toujours l'employé depuis `Authentication`, jamais d'un id fourni par le client. Le principe reste à appliquer à toute future page personnelle : F3 "mes heures", F4 "mes demandes".)*

**Points à penser**
- Qui crée les comptes et réinitialise les mots de passe ? *(V1, résolu le 15/08 : le manager, à la main, mais avec UI désormais — `EmployeService.creer()` génère un mot de passe temporaire affiché une fois à l'admin via une bannière flash sur `/liste-employe` ; `EmployeService.reinitialiserMotDePasse()` / `PATCH /reset-password-employe/{id}` permet de le régénérer pour un compte existant, affiché via `prompt()` côté JS. Toujours aucun email envoyé : l'admin communique la valeur à la main.)*
- CSRF est réactivé mais **désactivé sur `/h2-console/**`** (nécessaire techniquement pour que la console fonctionne) : à retirer avant tout déploiement au-delà du poste de dev.
- Page de gestion de compte : `/mon-compte` (ajouté le 15/08) permet à tout utilisateur connecté de changer son propre mot de passe (ancien + nouveau + confirmation, `UserService.changerMotDePasse()`). Toujours pas de page pour lister/révoquer les comptes — seul levier restant : `data.sql` ou la console H2.
- `/pointer-employe/{id}` (admin) accepte toujours un id fourni par le client — volontaire (pointeuse collective pour le manager) mais à garder en tête si la route est un jour rouverte à un rôle non-admin.
- Pas de flag `mustChangePassword` : un employé peut travailler indéfiniment avec son mot de passe temporaire sans y être jamais invité à le changer (choix v1 assumé, cf. décision du 15/08).

---

## F8 — Plus tard (hors périmètre v1)

- Planning prévisionnel des shifts + contrôle des règles légales (11h de repos entre deux services, 48h max/semaine, jour de repos hebdomadaire).
- Jours fériés français et leur traitement (travaillé = récupération).
- Acquisition automatique des CP (2,5 j/mois, période juin→mai).
- Export paie (PDF/Excel mensuel par employé).
- Notifications email.
- Décompte CP en jours ouvrables conformément à la convention HCR.

---

## Ordre de réalisation conseillé

1. F1 Employés (le socle, CRUD simple pour prendre en main la stack) — 🚧 date de sortie et validation corrigées ; reste : suppression physique à retirer, filtre serveur actif/poste, re-check email à la modification
2. F2 Pointage (la mécanique badge + corrections) — 🚧 badge entrée/sortie fonctionnel + vue personnelle `/mon-pointage` ; reste : vérifier l'employé actif, correction manager avec commentaire obligatoire, vue "semaine"
3. F3 Calcul des heures (avec tests unitaires solides — le module critique) — ❌ à faire
4. F5 Compteurs (structure + mouvements) — 🚧 structure, lecture globale et lecture personnelle (`/mes-conges`) ok, débit/crédit et historique à raccorder
5. F4 Absences (s'appuie sur les compteurs) — 🚧 entité et règles écrites, à exposer via repository/service/contrôleur
6. F6 Tableau de bord (agrège tout) — ❌ à faire
7. F7 Sécurité — 🚧 fait en avance sur l'ordre conseillé (login/mot de passe/rôles/routes protégées le 11/08, avant F3/F4/F6) ; flux de mot de passe (création/réinitialisation admin + self-service) ajouté le 15/08 ; reste : filtrage par utilisateur sur les futures pages F3/F4/F6, page de gestion/révocation des comptes
