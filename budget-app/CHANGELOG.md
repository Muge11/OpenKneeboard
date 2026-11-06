# Journal des versions

Format : [versionnage sémantique](https://semver.org/lang/fr/) — `MAJEUR.MINEUR.CORRECTIF`.
Le numéro est défini dans `Code.gs` (`APP_VERSION`) et affiché dans l'en-tête de l'appli.

## [0.1.0] — 2026-11-06 — *test*

Première version de test (déploiement à valider).

- Libellés paramétrables (entrées / sorties, groupe, budget mensuel), ajout libre depuis l'appli.
- Mouvements fixes (entrées et sorties) avec échéance de fin ou perpétuels,
  reportés automatiquement chaque mois ; modifiables pour un seul mois ou pour la suite.
- Utilisateurs avec niveaux d'accès (administrateur / éditeur / lecteur) et couleur ;
  chaque mouvement affiche « Saisi par … ». Partage automatique de la Google Sheet.
- Sauvegarde automatique à la fermeture de l'appli (si modifications) + chaque nuit,
  dans un fichier séparé ; restauration depuis l'appli.
- Journal des actions (qui, quoi, quand, avant/après).
- Pointage des mouvements, synthèse prévu/réel avec % du revenu, enveloppes, évolution 6 mois.
- Dépenses en attente, soldes d'épargne, import en masse des mouvements fixes.
