# Journal des versions

Format : [versionnage sémantique](https://semver.org/lang/fr/) — `MAJEUR.MINEUR.CORRECTIF`.
Le numéro est défini dans `Code.gs` (`APP_VERSION`) et affiché dans l'en-tête de l'appli.

## [0.7.0] — 2026-11-06 — *test*

- **Appli installable (PWA)** : coquille `pwa/` (manifeste, service worker, icônes, écran de démarrage,
  raccourci « Nouvelle dépense ») qui affiche la web app en plein écran, sans barre de navigateur ;
  la barre d'état suit le thème clair / sombre. La web app autorise désormais cet affichage.
- **Ouverture instantanée** : dernières données mémorisées sur le téléphone, affichées tout de suite puis actualisées.
- **Gestes** : tirer pour actualiser, glisser à gauche / droite pour changer de mois, glisser une fiche vers le bas
  pour la fermer ; **bouton retour Android** (ferme fiche / page / revient à l'onglet de départ) ; vibrations légères.
- **Smartphone (Pixel)** : barre de navigation type Material (indicateur), zones tactiles ≥ 56 px, sélecteur de mois
  natif (toucher le mois), écran de chargement en squelette, hauteurs adaptées à l'écran (dvh).
- **Rapidité** : comptes d'épargne mis en cache côté serveur (10 min, vidé à chaque modification).
- **Ordinateur** : en-tête, navigation et bouton + alignés sur la colonne de contenu sur grand écran ;
  survol à la souris ; **Échap** ferme une fiche, **← / →** changent de mois ; gestes tactiles inactifs à la souris.
- **Mise en page ordinateur (≥ 1100 px)** : barre latérale (nom, « Nouveau mouvement », onglets), contenu réparti
  en 2 colonnes (3 au-delà de 1700 px), listes (Mouvements, Journal…) en une colonne large, fiches dans un panneau
  latéral droit pleine hauteur. Le téléphone garde sa mise en page.
- Correctif : l'étiquette « véhicule ? » s'affichait aussi sur les mouvements déjà rattachés à un véhicule.
- **Saisie vocale « Hey Google » supprimée** (et autorisation Google Tasks retirée) ; les anciens déclencheurs
  se suppriment d'eux-mêmes. La saisie rapide au clavier reste.

## [0.6.0] — 2026-11-06 — *test*

- **Commande vocale « Hey Google »** (via Google Tasks) : « Hey Google, ajoute une tâche : budget 45 euros
  Leclerc » → mouvement créé automatiquement dans les 5 minutes (libellé, véhicule, compte d'épargne reconnus ;
  « Divers » si le libellé n'est pas compris), tâche cochée avec un récapitulatif ; phrase sans montant marquée ⚠️.
  Prises en compte : tâches commençant par budget / dépense / achat / reçu / retrait / épargne, ou toutes celles
  d'une liste « Budget ». Activation par chaque utilisateur (Plus › Mes préférences › Saisie vocale) :
  le déclencheur s'exécute avec son propre compte.
- Mouvements dictés marqués **🎤 à vérifier** (bandeau dans la Synthèse, filtre dédié) jusqu'à leur enregistrement.
- **Raccourci « Saisie rapide »** (lien `?saisie=1` à poser sur l'écran d'accueil) et lien `?q=texte` pré-rempli.
- Bouton micro dans la fiche de saisie (reconnaissance vocale du navigateur si disponible, sinon micro du clavier).
- Nouvelle autorisation et service : Google Tasks.

## [0.5.0] — 2026-11-06 — *test*

- **Épargne par compte** : versements (libellé Epargne…) et retraits (nouveau libellé *Retrait épargne*)
  rattachés à un compte ; solde suivi automatiquement (solde de référence + versements − retraits) ;
  fiche compte (solde, versements du mois / de l'année, évolution, historique) ; reconnu à la dictée.
- **Confidentialité réelle** : chaque compte est un fichier Google séparé, partagé uniquement avec les
  administrateurs et les personnes autorisées ; gestion des comptes et de la visibilité réservée à l'admin ;
  journal sans nom ni montant ; comptes exclus des sauvegardes communes. Migration : les anciens soldes
  deviennent des comptes privés visibles par l'administrateur seul.
- **Compte rendu mensuel par e-mail** dès que le salaire du nouveau mois est saisi ou pointé (sinon le 5),
  filtré par destinataire ; aperçu et envoi manuel (Plus › Compte rendu mensuel).
- **Détail des coûts par véhicule** : périodes, répartition par poste, tableau mois par mois,
  comparaisons, prêt restant.
- **Sections repliables** partout, filtre déroulant dans Mouvements, « Plus d'options » dans la fiche.
- **Thème clair / sombre / automatique** et **préférences par utilisateur** enregistrées sur le serveur.
- **Nouveautés** affichées après chaque mise à jour (Plus › Nouveautés).
- Nouvelles autorisations : envoi d'e-mails (`script.send_mail`).

## [0.4.0] — 2026-11-06 — *test*

- **Imputation par véhicule** : véhicules Partner et Pajero (Plus › Véhicules, modifiables) ;
  option « À imputer à un véhicule » par libellé (Carburant, Prêt auto, nouveau libellé *Véhicules*) ;
  choix du véhicule dans les fiches (mémorisé pour les fixes), reconnu à la dictée
  (« plein Pajero 62 euros ») ; coût par véhicule (mois, année, moyenne, détail par libellé,
  évolution 12 mois), mouvements non imputés signalés ; filtre par véhicule.
  Assurances non imputées (prélèvement unique).
- **Recherche** dans Mouvements (ce mois / tous les mois, résultats groupés par mois avec totaux)
  et dans Fixes : description, libellé, montant, personne, véhicule, date.
- **Synthèse** : toucher un libellé ou un groupe ouvre sa fiche détail (prévu / réel / reste et
  liste des mouvements), avec « Modifier le prévu » et « Voir dans Mouvements ».
- **Conseils** : type **Impôts & taxes** ; règle 50/30/20 calculée sur le revenu disponible
  (revenus − impôts & taxes) ; indicateur « Poids des impôts et taxes » (mois et 12 mois).
- Migration automatique : libellé Véhicules ajouté, fixes / mouvements citant un véhicule rattachés
  (ex. « Prêt Pajero »), libellé Impôts passé en type Impôts & taxes.

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
