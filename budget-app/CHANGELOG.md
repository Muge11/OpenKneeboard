# Journal des versions

Format : [versionnage sémantique](https://semver.org/lang/fr/) — `MAJEUR.MINEUR.CORRECTIF`.
Le numéro est défini dans `Code.gs` (`APP_VERSION`) et affiché dans l'en-tête de l'appli.

## [0.3.0] — 2026-11-06 — *test*

- **Correctif** : le prévu d'une entrée (ex. salaire saisi en ponctuel) restait à 0 € sans moyen de le modifier.
  Les lignes de la Synthèse sont maintenant cliquables pour régler le montant prévu ; les entrées sans prévu
  sont signalées « définir ».
- **Refonte visuelle** : thème noir unique, police Inter, icônes au trait, cartes et graphiques redessinés.
- **Graphiques** (Synthèse › Graphiques) : répartition des sorties par type (anneau), réel vs prévu par libellé,
  évolution sur 6 / 12 mois avec repère des entrées ; périodes mois / 3 mois / 12 mois.
- **Conseils** (Synthèse › Conseils) : règle 50/30/20, taux d'endettement, logement, repères par libellé,
  conseils chiffrés ; repères modifiables par l'administrateur.
- **Libellés** : emoji personnalisable et type (besoin / envie / crédit / épargne) ; migration automatique.
- **Saisie rapide dictée** : « 45 euros Leclerc alimentaire » → montant, libellé et description remplis.
- **Rapidité** : enregistrement en arrière-plan (fiche fermée immédiatement) et 2 fois moins d'accès
  à la Google Sheet (31 → 15 par enregistrement, 80 → 42 au chargement).
- **Dupliquer** un mouvement ou un mouvement fixe.
- **Ponctuel ↔ fixe** depuis la fiche d'un mouvement ; échéance réglable depuis la fiche.
- **Montant 0 €** accepté pour un fixe ; bouton « Non réalisé ce mois ».
- **Onglet Fixes** indépendant du mois affiché ; **tri** par libellé (avec sous-totaux), montant ou date de prélèvement.

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
