# Journal des versions

Format : [versionnage sémantique](https://semver.org/lang/fr/) — `MAJEUR.MINEUR.CORRECTIF`.
Le numéro est défini dans `Code.gs` (`APP_VERSION`) et affiché dans l'en-tête de l'appli.

## [0.2.0] — 2026-11-06 — *test*

- **Enveloppes budgétaires mensuelles par libellé** (nouvel onglet 🎯 Enveloppes) :
  - reportées automatiquement de mois en mois ;
  - réglables « à partir de ce mois » ou « ce mois uniquement » (l'historique n'est pas modifié) ;
  - suggestions : dépense du mois dernier, moyenne sur 3 mois, total des fixes ;
  - les fixes d'un libellé sont compris dans son enveloppe ;
  - « Reste à répartir » = entrées prévues − sorties prévues.
- Migration : l'ancien « budget mensuel » des libellés devient une enveloppe par défaut.
- Nouvelle feuille `Enveloppes`, incluse dans les sauvegardes.

## [0.1.1] — 2026-11-06 — *test*

- **Mois de budget distinct de la date réelle** : un salaire versé le 28 octobre peut compter
  pour le budget de novembre (champ « Compte pour le budget de » dans la saisie).
- Libellés : option « Compte pour le mois suivant par défaut » (activée pour *Salaires*).
- Mouvements fixes : option « Versé le mois précédent ».
- Le mois suivant est préparé dès l'ouverture de l'appli (pour pointer un salaire anticipé).
- Migration automatique : les fixes *Salaires* existants sont décalés (occurrences non pointées
  à partir du mois en cours redatées au mois précédent).
- Correctif : autorisations `appsscript.json` (fichier de sauvegarde, sauvegarde de nuit).

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
