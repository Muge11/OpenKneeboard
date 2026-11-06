# Feuille de route

Points validés pour la prochaine version (v0.3.0) — **non développés, non déployés**.

## À faire

- [ ] **Onglet « Fixes » indépendant du mois sélectionné**
  Les mouvements fixes sont permanents : l'onglet ne doit pas dépendre du sélecteur de mois.
  - masquer les flèches ‹ › et le nom du mois dans l'en-tête sur cet onglet (titre « Mouvements fixes ») ;
  - valeurs par défaut basées sur le **mois en cours** (début d'un nouveau fixe, « Appliquer à partir de »,
    « Arrêter à partir de »), et non sur le mois affiché ailleurs ;
  - l'état « Perpétuel / Jusqu'à … / Terminé » reste calculé par rapport à aujourd'hui.
- [ ] **Tri des mouvements fixes** (onglet « Fixes », entrées et sorties) :
  - tri par **libellé** (regroupé par libellé, ordre des libellés), **montant** (décroissant / croissant)
    ou **date de prélèvement** (jour du mois, en tenant compte des fixes « versés le mois précédent ») ;
  - sélecteur de tri en haut de la liste, choix mémorisé sur l'appareil ;
  - sous-totaux par libellé quand le tri est « par libellé ».
- [ ] **Montant 0 accepté pour les mouvements fixes** (ex. épargne prévue mais non faite ce mois-ci)
  - la fiche d'une occurrence de fixe accepte 0 € (aujourd'hui refusé : « Montant manquant ») ;
  - bouton rapide **« Non réalisé ce mois »** : met le réel à 0 € et conserve le prévu, sans supprimer la ligne ;
  - affichage « 0 € — non réalisé » (étiquette dédiée), écart prévu / réel visible dans la Synthèse et les enveloppes ;
  - un fixe peut aussi être paramétré à 0 € (ligne réservée, montant à préciser plus tard) ;
  - les mouvements ponctuels continuent d'exiger un montant non nul.
- [ ] **Changer la nature d'un mouvement après saisie (ponctuel ↔ fixe)**
  - dans la fiche d'un mouvement, choix **Ponctuel / Fixe** modifiable à tout moment ;
  - **ponctuel → fixe** : crée le mouvement fixe à partir de celui-ci (montant, libellé, description, jour,
    mois de budget, « versé le mois précédent » si besoin) avec l'échéance choisie (**perpétuel** ou **jusqu'à …**) ;
    le mouvement saisi devient la première occurrence (pas de doublon) ;
  - **fixe → ponctuel** : cette occurrence est conservée comme mouvement ponctuel et le fixe n'est plus reporté
    les mois suivants ; les occurrences futures non pointées / non modifiées sont retirées, l'historique est conservé ;
  - l'échéance (perpétuel ↔ jusqu'à …) se règle aussi directement depuis la fiche du mouvement ;
  - confirmation avant application, action tracée au journal.
- [ ] **Dupliquer un mouvement**
  - bouton « Dupliquer » dans la fiche d'un mouvement : ouvre un nouveau mouvement pré-rempli
    (libellé, montant, description), daté d'aujourd'hui, modifiable avant enregistrement ;
  - même chose pour un mouvement fixe (nouveau fixe pré-rempli, début = mois en cours) ;
  - la copie est un nouveau mouvement à part entière (« Saisi par » = la personne qui duplique), tracé au journal.
- [ ] **Refonte visuelle — thème noir obligatoire, rendu professionnel**
  - thème sombre unique (indépendant du réglage du téléphone) : fond quasi noir, cartes gris anthracite
    séparées par de fines bordures plutôt que des ombres ;
  - palette sobre : texte blanc cassé, gris secondaires, une seule couleur d'accent ;
    vert pour les entrées / restes positifs, rouge corail pour les dépassements, ambre pour les alertes ;
  - typographie soignée (police type Inter, chiffres alignés), montants mis en valeur ;
  - icônes de navigation au trait (style application bancaire) à la place des emojis de la barre du bas ;
    les emojis restent pour les libellés ;
  - en-tête épuré (plus de bandeau vert), jauges et graphiques redessinés ;
  - **maquette à valider avant développement**.
- [ ] **Graphiques de répartition des dépenses**
  - anneau (donut) de répartition des sorties du mois par libellé, et par groupe (touche = détail du libellé) ;
  - barres « réel vs enveloppe » par libellé ;
  - évolution sur 6 / 12 mois par libellé (barres empilées) et comparaison avec le mois précédent ;
  - sélection de la période (mois, trimestre, année) ; respect du thème noir.
- [ ] **Conseils budgétaires (% des revenus par rapport aux repères recommandés)**
  - chaque libellé est classé **Besoin / Envie / Épargne / Crédit** (réglable dans Libellés, valeurs proposées par défaut) ;
  - repères affichés avec jauge « dans la norme / à surveiller / au-dessus » :
    - règle **50 / 30 / 20** : besoins ≤ 50 %, envies ≤ 30 %, épargne ≥ 20 % des revenus ;
    - **taux d'endettement** (prêts + crédits) ≤ 35 % des revenus (norme des banques françaises / HCSF) ;
    - logement (prêt + charges) ≤ ~33 %, alimentation ~10–15 %, transport ~10–15 %, loisirs ~5–10 % ;
  - conseils courts et concrets (ex. « Loisirs : 12 % des revenus, repère 5–10 % — 140 € au-dessus ce mois-ci ») ;
  - calculés sur le mois et sur la moyenne 3 mois (pour lisser les mois exceptionnels) ;
  - seuils modifiables par l'administrateur ; mention : repères indicatifs, pas un conseil financier personnalisé.
- [ ] **Emoji pour les libellés** : grille de suggestions + saisie libre (clavier emoji du téléphone).
- [ ] **Rapidité des enregistrements** :
  - enregistrement en arrière-plan (formulaire fermé immédiatement, annulation si erreur) ;
  - moins d'accès à la Google Sheet par action (lecture unique par feuille, colonnes mémorisées) ;
  - réponses partielles au lieu de renvoyer toutes les données ;
  - chargement initial allégé.
- [ ] **Saisie rapide dictée** (« 45 euros Leclerc alimentaire » via le micro du clavier), analysée automatiquement.

## Plus tard (étudié, à décider)

- « Hey Google » mains libres via Google Tasks (délai 5–10 min, à tester).
- Bot Telegram (alternative recommandée à WhatsApp, jugé trop lourd : numéro dédié, compte Meta Business, serveur relais).
- Report du reliquat d'enveloppe, solde bancaire et rapprochement, import CSV du relevé, règles de classement,
  alertes, projection de fin de mois, bilan annuel, objectifs d'épargne, paiements en plusieurs fois.
