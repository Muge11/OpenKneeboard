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
