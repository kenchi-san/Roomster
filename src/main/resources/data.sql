-- Réinitialise les données de démo à chaque démarrage (la base est maintenant persistée sur disque)
DELETE FROM demande_absence;
DELETE FROM pointage;
DELETE FROM compteur_employe;
DELETE FROM employe;
DELETE FROM users;

-- Comptes utilisateur (nom/prenom/email), un par employé
INSERT INTO users (id, nom, prenom, email) VALUES
    (1, 'Dupont', 'Jean', 'jean.dupont@roomster.fr'),
    (2, 'Lefevre', 'Marie', 'marie.lefevre@roomster.fr'),
    (3, 'Martin', 'Paul', 'paul.martin@roomster.fr'),
    (4, 'Bernard', 'Sophie', 'sophie.bernard@roomster.fr'),
    (5, 'Petit', 'Lucas', 'lucas.petit@roomster.fr'),
    (6, 'Moreau', 'Camille', 'camille.moreau@roomster.fr'),
    (7, 'Girard', 'Nicolas', 'nicolas.girard@roomster.fr'),
    (8, 'Rousseau', 'Emma', 'emma.rousseau@roomster.fr');

ALTER TABLE users ALTER COLUMN id RESTART WITH 9;

-- Employés
-- duree_hebdo_contrat est un java.time.Duration, persisté par Hibernate en nanosecondes (BIGINT)
INSERT INTO employe (id, user_id, poste, type_contrat, date_entree, date_sortie, duree_hebdo_contrat, actif) VALUES
    (1, 1, 'RECEPTION', 'CDI', '2020-01-15', NULL, 126000000000000, true),
    (2, 2, 'HOUSEKEEPING', 'CDI', '2019-03-01', NULL, 126000000000000, true),
    (3, 3, 'CUISINE', 'CDD', '2023-06-01', NULL, 140400000000000, true),
    (4, 4, 'DIRECTION', 'CDI', '2015-09-01', NULL, 126000000000000, true),
    (5, 5, 'MAINTENANCE', 'EXTRA', '2024-01-10', NULL, 72000000000000, true),
    (6, 6, 'SALLE', 'APPRENTI', '2025-09-01', NULL, 108000000000000, true),
    (7, 7, 'RECEPTION', 'CDD', '2024-04-01', '2026-06-30', 126000000000000, false),
    (8, 8, 'HOUSEKEEPING', 'CDI', '2021-11-08', NULL, 126000000000000, true);

-- Les id ci-dessus sont fixés explicitement : on doit resynchroniser le compteur IDENTITY
-- sinon les prochains employés créés via l'appli entrent en collision avec un id déjà pris.
ALTER TABLE employe ALTER COLUMN id RESTART WITH 9;

-- Compteurs annuels (congés payés / RTT / heures sup)
-- heures_sup_cumulees en nanosecondes : 2700000000000=45min, 5400000000000=1h30, 7200000000000=2h,
-- 10800000000000=3h, 18000000000000=5h, 26100000000000=7h15
INSERT INTO compteur_employe (employe_id, annee, solde_conges_payes, solde_rtt, heures_sup_cumulees) VALUES
    (1, 2026, 9.5, 3.0, 7200000000000),
    (1, 2025, 0.0, 1.0, 0),
    (2, 2026, 25.0, 0.0, 18000000000000),
    (3, 2026, 8.0, 1.5, 2700000000000),
    (4, 2026, 20.0, 6.0, 0),
    (4, 2025, 15.0, 2.0, 5400000000000),
    (5, 2026, 4.0, 0.0, 26100000000000),
    (6, 2026, 2.5, 0.0, 0),
    (7, 2026, 0.0, 0.0, 0),
    (8, 2026, 17.5, 4.5, 10800000000000);

-- Demandes d'absence (toutes les valeurs de TypeAbsence et StatutDemande sont représentées)
INSERT INTO demande_absence (employe_id, type, debut, fin, statut, date_soumission, date_decision, validateur_id, motif_refus) VALUES
    (1, 'CONGE_PAYE', '2026-08-03', '2026-08-14', 'VALIDEE', '2026-06-01 09:00:00', '2026-06-02 10:00:00', 4, NULL),
    (3, 'MALADIE', '2026-07-20', '2026-07-22', 'SOUMISE', '2026-07-20 08:15:00', NULL, NULL, NULL),
    (2, 'RTT', '2026-07-28', '2026-07-28', 'REFUSEE', '2026-07-10 14:00:00', '2026-07-11 09:00:00', 4, 'Effectif insuffisant ce jour-là'),
    (6, 'SANS_SOLDE', '2026-09-01', '2026-09-05', 'SOUMISE', '2026-07-15 11:20:00', NULL, NULL, NULL),
    (8, 'RECUP_JOUR_FERIE', '2026-08-17', '2026-08-17', 'VALIDEE', '2026-07-01 08:00:00', '2026-07-01 18:00:00', 4, NULL),
    (5, 'CONGE_PAYE', '2026-12-22', '2027-01-02', 'ANNULEE', '2026-06-10 10:00:00', NULL, NULL, NULL),
    (3, 'RTT', '2026-05-04', '2026-05-04', 'VALIDEE', '2026-04-20 09:00:00', '2026-04-21 09:30:00', 4, NULL);

-- Pointages (scénarios variés : journée complète, encore badgé, oubli, nuit à cheval sur deux jours)
INSERT INTO pointage (employe_id, entree, sortie, commentaire) VALUES
    (1, '2026-07-24 08:02:00', '2026-07-24 16:05:00', NULL),
    (2, '2026-07-24 07:58:00', NULL, NULL),
    (3, '2026-07-23 09:00:00', '2026-07-23 17:30:00', 'Oubli de badge, corrigé par le manager'),
    (5, '2026-07-23 22:00:00', '2026-07-24 06:00:00', 'Astreinte de nuit'),
    (6, '2026-07-24 09:15:00', '2026-07-24 13:00:00', 'Demi-journée, formation l''après-midi'),
    (8, '2026-07-22 07:45:00', '2026-07-22 15:50:00', NULL),
    (4, '2026-07-24 08:30:00', NULL, NULL);
