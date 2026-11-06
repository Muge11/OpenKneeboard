# Feuille de route

Points de la v0.3.0 — **livrés** (voir CHANGELOG).

## À faire

- [x] **Onglet « Fixes » indépendant du mois sélectionné**
  Les mouvements fixes sont permanents : l'onglet ne doit pas dépendre du sélecteur de mois.
  - masquer les flèches ‹ › et le nom du mois dans l'en-tête sur cet onglet (titre « Mouvements fixes ») ;
  - valeurs par défaut basées sur le **mois en cours** (début d'un nouveau fixe, « Appliquer à partir de »,
    « Arrêter à partir de »), et non sur le mois affiché ailleurs ;
  - l'état « Perpétuel / Jusqu'à … / Terminé » reste calculé par rapport à aujourd'hui.
- [x] **Tri des mouvements fixes** (onglet « Fixes », entrées et sorties) :
  - tri par **libellé** (regroupé par libellé, ordre des libellés), **montant** (décroissant / croissant)
    ou **date de prélèvement** (jour du mois, en tenant compte des fixes « versés le mois précédent ») ;
  - sélecteur de tri en haut de la liste, choix mémorisé sur l'appareil ;
  - sous-totaux par libellé quand le tri est « par libellé ».
- [x] **Montant 0 accepté pour les mouvements fixes** (ex. épargne prévue mais non faite ce mois-ci)
  - la fiche d'une occurrence de fixe accepte 0 € (aujourd'hui refusé : « Montant manquant ») ;
  - bouton rapide **« Non réalisé ce mois »** : met le réel à 0 € et conserve le prévu, sans supprimer la ligne ;
  - affichage « 0 € — non réalisé » (étiquette dédiée), écart prévu / réel visible dans la Synthèse et les enveloppes ;
  - un fixe peut aussi être paramétré à 0 € (ligne réservée, montant à préciser plus tard) ;
  - les mouvements ponctuels continuent d'exiger un montant non nul.
- [x] **Changer la nature d'un mouvement après saisie (ponctuel ↔ fixe)**
  - dans la fiche d'un mouvement, choix **Ponctuel / Fixe** modifiable à tout moment ;
  - **ponctuel → fixe** : crée le mouvement fixe à partir de celui-ci (montant, libellé, description, jour,
    mois de budget, « versé le mois précédent » si besoin) avec l'échéance choisie (**perpétuel** ou **jusqu'à …**) ;
    le mouvement saisi devient la première occurrence (pas de doublon) ;
  - **fixe → ponctuel** : cette occurrence est conservée comme mouvement ponctuel et le fixe n'est plus reporté
    les mois suivants ; les occurrences futures non pointées / non modifiées sont retirées, l'historique est conservé ;
  - l'échéance (perpétuel ↔ jusqu'à …) se règle aussi directement depuis la fiche du mouvement ;
  - confirmation avant application, action tracée au journal.
- [x] **Dupliquer un mouvement**
  - bouton « Dupliquer » dans la fiche d'un mouvement : ouvre un nouveau mouvement pré-rempli
    (libellé, montant, description), daté d'aujourd'hui, modifiable avant enregistrement ;
  - même chose pour un mouvement fixe (nouveau fixe pré-rempli, début = mois en cours) ;
  - la copie est un nouveau mouvement à part entière (« Saisi par » = la personne qui duplique), tracé au journal.
- [x] **Refonte visuelle — thème noir obligatoire, rendu professionnel**
  - thème sombre unique (indépendant du réglage du téléphone) : fond quasi noir, cartes gris anthracite
    séparées par de fines bordures plutôt que des ombres ;
  - palette sobre : texte blanc cassé, gris secondaires, une seule couleur d'accent ;
    vert pour les entrées / restes positifs, rouge corail pour les dépassements, ambre pour les alertes ;
  - typographie soignée (police type Inter, chiffres alignés), montants mis en valeur ;
  - icônes de navigation au trait (style application bancaire) à la place des emojis de la barre du bas ;
    les emojis restent pour les libellés ;
  - en-tête épuré (plus de bandeau vert), jauges et graphiques redessinés ;
  - **maquette à valider avant développement**.
- [x] **Graphiques de répartition des dépenses**
  - anneau (donut) de répartition des sorties du mois par libellé, et par groupe (touche = détail du libellé) ;
  - barres « réel vs enveloppe » par libellé ;
  - évolution sur 6 / 12 mois par libellé (barres empilées) et comparaison avec le mois précédent ;
  - sélection de la période (mois, trimestre, année) ; respect du thème noir.
- [x] **Conseils budgétaires (% des revenus par rapport aux repères recommandés)**
  - chaque libellé est classé **Besoin / Envie / Épargne / Crédit** (réglable dans Libellés, valeurs proposées par défaut) ;
  - repères affichés avec jauge « dans la norme / à surveiller / au-dessus » :
    - règle **50 / 30 / 20** : besoins ≤ 50 %, envies ≤ 30 %, épargne ≥ 20 % des revenus ;
    - **taux d'endettement** (prêts + crédits) ≤ 35 % des revenus (norme des banques françaises / HCSF) ;
    - logement (prêt + charges) ≤ ~33 %, alimentation ~10–15 %, transport ~10–15 %, loisirs ~5–10 % ;
  - conseils courts et concrets (ex. « Loisirs : 12 % des revenus, repère 5–10 % — 140 € au-dessus ce mois-ci ») ;
  - calculés sur le mois et sur la moyenne 3 mois (pour lisser les mois exceptionnels) ;
  - seuils modifiables par l'administrateur ; mention : repères indicatifs, pas un conseil financier personnalisé.
- [x] **Emoji pour les libellés** : grille de suggestions + saisie libre (clavier emoji du téléphone).
- [x] **Rapidité des enregistrements** :
  - enregistrement en arrière-plan (formulaire fermé immédiatement, annulation si erreur) ;
  - moins d'accès à la Google Sheet par action (lecture unique par feuille, colonnes mémorisées) ;
  - réponses partielles au lieu de renvoyer toutes les données ;
  - chargement initial allégé.
- [x] **Saisie rapide dictée** (« 45 euros Leclerc alimentaire » via le micro du clavier), analysée automatiquement.

## Plus tard (étudié, à décider)

- « Hey Google » mains libres via Google Tasks (délai 5–10 min, à tester).
- Bot Telegram (alternative recommandée à WhatsApp, jugé trop lourd : numéro dédié, compte Meta Business, serveur relais).
- Report du reliquat d'enveloppe, solde bancaire et rapprochement, import CSV du relevé, règles de classement,
  alertes, projection de fin de mois, bilan annuel, objectifs d'épargne, paiements en plusieurs fois.

---

# v0.4.0 — livrée (voir CHANGELOG)

- [x] **Imputation des dépenses par véhicule**
  - liste des véhicules du foyer (Plus › Véhicules) : nom, emoji, actif / vendu
    — **véhicules du foyer : Partner et Pajero** (créés d'office) ;
  - option par libellé **« À imputer à un véhicule »**, activée par défaut pour *Carburant*, *Prêt auto*
    et le nouveau libellé *Véhicules* (entretien, réparations, contrôle technique, pneus…) ;
  - **assurances exclues** : un seul prélèvement couvre tous les contrats, il n'est pas imputé à un véhicule ;
  - dans la fiche d'un mouvement ou d'un fixe de ces libellés : choix du véhicule (puces), mémorisé
    pour les fixes (ex. Prêt Pajero → Pajero, chaque mois) ;
  - saisie dictée : le nom du véhicule est reconnu (« plein Pajero 62 euros ») ;
  - filtre par véhicule dans l'onglet Mouvements ;
  - **coût par véhicule** : par mois et par an, détail par libellé (carburant, prêt, entretien),
    graphique d'évolution, comparaison entre véhicules ;
  - mouvements existants « non imputés » signalés, à rattacher en un geste ;
  - option ultérieure : kilométrage pour calculer le coût au kilomètre.
- [x] **Barre de recherche** dans les onglets *Mouvements* et *Fixes*
  - recherche instantanée pendant la frappe (sans accents ni majuscules) sur : description, libellé,
    montant (« 45 », « 45,50 »), personne (« Elise »), véhicule, date (« 28/10 ») ;
  - *Mouvements* : portée **ce mois** ou **tous les mois** (résultats groupés par mois, avec total
    et nombre de résultats — ex. « Leclerc : 23 mouvements, 2 140 € sur l'année ») ;
  - *Fixes* : filtre la liste (actifs et terminés), compatible avec le tri choisi ;
  - se combine avec les filtres existants (sorties, entrées, à pointer…) ; bouton ✕ pour effacer.
- [x] **Synthèse : détail d'un libellé au clic**
  - toucher une ligne de libellé (tableau, enveloppes, graphiques) ouvre une **fiche détail** :
    prévu / réel / reste, jauge, et la **liste des mouvements du libellé pour le mois** (date, description,
    montant, personne, fixe ou ponctuel, pointé) ;
  - chaque mouvement s'ouvre d'un toucher (modifier, pointer, dupliquer…) ;
  - boutons **« Modifier le prévu »** (enveloppe / montant prévu, comme aujourd'hui) et
    **« Voir dans Mouvements »** (onglet Mouvements filtré sur ce libellé) ;
  - ligne de groupe (ex. « Vie courante ») : même fiche avec les mouvements de tout le groupe.
- [x] **Conseils : catégorie « Impôts & taxes » (obligations)**
  - nouveau type de libellé **Impôts & taxes** (en plus de besoin / envie / crédit / épargne),
    attribué par défaut à *Impôts* et aux libellés type *Taxe foncière* ;
  - la règle 50/30/20 se calcule sur le **revenu disponible** = revenus − impôts & taxes
    (la règle s'applique au revenu après impôts : compter les impôts en « besoins » fausse le résultat) ;
  - indicateur dédié **« Poids des impôts et taxes »** (en % des revenus, sur le mois et sur 12 mois,
    car ces prélèvements sont souvent irréguliers : soldes, échéances 1/4…) — informatif, sans repère imposé ;
  - nouvelle couleur dans les graphiques (répartition, évolution) ;
  - migration : le libellé *Impôts* passe automatiquement dans ce type (modifiable).

---

# Version suivante (v0.5.0) — à développer

- [ ] **Imputation de l'épargne sur chaque compte d'épargne**
  - les mouvements du libellé *Epargne* (et des libellés de type « épargne ») se rattachent à un compte
    (Trade Republic, Crédit Agricole LLD, Assurance vie Elise, Epargne Elia, Epargne Thomas…), comme les véhicules ;
  - choix du compte dans la fiche (mémorisé pour les fixes : « Epargne Elia 25 € » → compte Epargne Elia),
    reconnu à la dictée ;
  - **solde suivi automatiquement** : solde de référence (saisi à une date) + versements − retraits ;
    libellé d'entrée « Retrait épargne » pour les retraits ; mise à jour manuelle du solde toujours possible ;
  - fiche compte : versements du mois / de l'année, historique, évolution du solde.
- [ ] **Comptes d'épargne réservés à l'administrateur**
  - création, modification, suppression et réglage de la visibilité des comptes : **administrateur uniquement**.
- [ ] **Visibilité des comptes par utilisateur**
  - pour chaque compte : « visible par tous » ou liste des personnes autorisées ;
  - un utilisateur non autorisé ne voit ni le compte, ni son solde, ni l'imputation des versements
    (il voit seulement « Épargne » dans le budget commun) ; totaux épargne calculés sur ses comptes visibles ;
  - **niveau de confidentialité à choisir** (voir ci-dessous).

### Point à trancher : confidentialité réelle
Tous les utilisateurs ont accès en modification à la Google Sheet (nécessaire au fonctionnement de l'appli).
- **Option A — masquage dans l'appli** : simple ; mais les données restent lisibles par quelqu'un qui ouvre
  directement la Google Sheet.
- **Option B — confidentialité réelle** : chaque compte restreint est stocké dans un fichier Google séparé,
  partagé uniquement avec les personnes autorisées (géré automatiquement par l'appli) ; personne d'autre ne peut
  le lire, ni par l'appli ni par Drive.

