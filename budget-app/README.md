# 💶 Gestion de Budget — web app Google Drive · v0.1.1 (test)

Appli mobile/ordinateur pour gérer le budget du foyer à plusieurs.
Les données sont dans une **Google Sheet de votre Drive** ; l'interface est une
**web app Google Apps Script** ouverte dans le navigateur (aucun serveur, gratuit).

## Installation

1. **Google Sheet** : créez une feuille « Budget » (ou réutilisez celle de la version précédente).
2. **Extensions → Apps Script**, puis copiez les 3 fichiers :
   - `Code.gs` → remplace le contenu de `Code.gs`
   - `Index.html` → fichier HTML nommé **`Index`** (＋ → HTML)
   - `appsscript.json` → visible après *⚙️ Paramètres du projet → Afficher le fichier manifeste*
3. **Exécutez `setup`** une fois (▶ Exécuter) et acceptez les autorisations. Cela :
   - crée les feuilles (Libelles, Recurrents, Mouvements, Utilisateurs, Journal…),
   - vous déclare **administrateur**,
   - crée le fichier **« Budget — Sauvegardes »** dans votre Drive,
   - installe la sauvegarde de nuit.
4. **Déployer → Nouveau déploiement → Application Web**, description `v0.1.1` :
   - Exécuter en tant que : **Utilisateur accédant à l'application Web**
   - Qui a accès : **Tout utilisateur disposant d'un compte Google**
5. Dans l'appli : **Plus → Utilisateurs & accès → Ajouter** votre femme (adresse Gmail, nom, rôle, couleur).
   La Sheet et le fichier de sauvegarde lui sont **partagés automatiquement**. Envoyez-lui l'URL de l'appli.
6. Sur chaque smartphone : ouvrir l'URL → **Ajouter à l'écran d'accueil**.

> **Mise à jour du code** : recollez les fichiers, changez `APP_VERSION` si besoin, puis
> *Déployer → Gérer les déploiements → ✏️ → Nouvelle version* (l'URL ne change pas).
> Venant de la version précédente, les anciennes feuilles `Lignes` et `Operations` ne servent plus
> et peuvent être supprimées.

## Fonctionnement

| Onglet | Rôle |
|---|---|
| **Synthèse** | Reste à vivre réel/prévu, mouvements à pointer, enveloppes budgétaires, tableau prévu/réel par groupe et libellé avec % des entrées, évolution sur 6 mois |
| **Mouvements** | Tous les mouvements du mois (filtres : sorties, entrées, fixes, ponctuels, à pointer), case de pointage, badge coloré « Saisi par … » |
| **Fixes** | Paramétrage des entrées/sorties fixes : montant, jour, début, **échéance de fin ou perpétuel**, import en masse |
| **Plus** | Dépenses en attente, épargne, libellés, utilisateurs, journal, sauvegardes, version |

### Mouvements fixes
- Créés depuis l'onglet **Fixes**, ou depuis **＋** en cochant « Mouvement fixe ».
- Ils sont **reportés automatiquement** dans chaque mois (à l'ouverture de l'appli ou en naviguant vers le mois), jusqu'à leur échéance.
- **Modifier pour un seul mois** : ouvrez le mouvement dans l'onglet Mouvements (le mois est marqué « modifié ce mois »).
  « Retirer ce mois » le supprime pour ce mois uniquement (rétablissable).
- **Modifier pour la suite** : ouvrez-le dans l'onglet Fixes ; choisissez « Appliquer à partir de ». Les mois déjà pointés ou modifiés à la main ne sont pas écrasés.
- **Arrêter** : il n'est plus reporté à partir du mois choisi.

### Date réelle et mois de budget
Chaque mouvement a une **date réelle** (celle du relevé bancaire, pour le pointage) et un
**mois de budget** (celui où il compte). Exemple : salaire versé le 28 octobre → budget de **novembre**.
- Libellé *Salaires* : compté pour le mois suivant par défaut (réglable dans Libellés, pour tout libellé).
- Mouvement fixe : case « Versé le mois précédent ».
- Saisie ponctuelle : champ « Compte pour le budget de » (mois précédent / du mois / suivant).

### Prévu / réel
- Prévu d'un libellé = somme des montants fixes prévus + son **budget mensuel** (enveloppe, réglable dans Libellés).
- Réel = somme des mouvements saisis.

### Rôles
| | Lecteur | Éditeur | Administrateur |
|---|:-:|:-:|:-:|
| Consulter | ✅ | ✅ | ✅ |
| Saisir / modifier / pointer des mouvements, gérer les fixes, attente, épargne | | ✅ | ✅ |
| Créer un libellé | | ✅ | ✅ |
| Modifier / supprimer un libellé, gérer les utilisateurs, journal, sauvegardes, restauration | | | ✅ |

### Sauvegardes
- **À chaque fermeture de l'appli** (ou passage à une autre appli sur le téléphone), s'il y a eu des modifications.
- **Chaque nuit** (vers 3 h) en filet de sécurité.
- Stockées dans le fichier « Budget — Sauvegardes » (100 dernières) ; **Plus → Sauvegardes → Restaurer**
  (l'état actuel est sauvegardé avant toute restauration).

### Journal
Toutes les actions (ajout, modification avec valeur précédente, suppression, pointage, report auto,
sauvegardes, utilisateurs…) sont tracées dans la feuille `Journal`, consultable dans **Plus → Journal**.

## Versionnage
Voir [CHANGELOG.md](CHANGELOG.md). Version courante : **0.1.1 — test**.
