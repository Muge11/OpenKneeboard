# 💶 Gestion de Budget — web app Google Drive

Appli mobile/ordinateur pour gérer le budget du foyer à deux.
Les données sont dans une **Google Sheet de votre Drive** ; l'interface est une
**web app Google Apps Script** ouverte dans le navigateur (aucun serveur, gratuit).

## Installation (≈ 10 min, une seule fois)

1. **Créer la Google Sheet** : drive.google.com → Nouveau → Google Sheets → nommez-la « Budget ».
2. **Ouvrir l'éditeur de script** : dans la Sheet, menu *Extensions → Apps Script*.
3. **Copier les fichiers** :
   - Remplacez le contenu de `Code.gs` par celui de [`Code.gs`](Code.gs).
   - *Fichier ＋ → HTML*, nommez-le **`Index`** (sans .html), collez [`Index.html`](Index.html).
   - *Paramètres du projet ⚙️* → cochez « Afficher le fichier manifeste appsscript.json »,
     puis remplacez son contenu par [`appsscript.json`](appsscript.json).
4. **Initialiser** : sélectionnez la fonction `setup` → *Exécuter* → acceptez les autorisations
   (« Google n'a pas validé cette appli » → *Paramètres avancés* → *Accéder à Budget*).
   Les feuilles `Lignes`, `Operations`, `EnAttente`, `Epargne`, `Config` sont créées.
5. **Déployer** : *Déployer → Nouveau déploiement* → type **Application Web** :
   - Exécuter en tant que : **Utilisateur accédant à l'application Web**
   - Qui a accès : **Tout utilisateur disposant d'un compte Google**
   → *Déployer* et copiez l'URL (`https://script.google.com/macros/s/…/exec`).
6. **Partager avec votre femme** : partagez la **Google Sheet** avec son adresse Gmail
   en tant qu'**Éditeur**, puis envoyez-lui l'URL de l'appli.
   Seules les personnes ayant accès à la Sheet peuvent lire/écrire les données.
7. **Sur le smartphone** : ouvrez l'URL dans Chrome/Safari (connecté au compte Google)
   → menu *Partager / ⋮* → **Ajouter à l'écran d'accueil**.

> Après une modification du code : *Déployer → Gérer les déploiements → ✏️ → Version : Nouvelle version*
> (l'URL reste la même).

## Utilisation

| Onglet | Rôle |
|---|---|
| **Synthèse** | Reste à vivre réel/prévu, enveloppes (Alimentation, Carburant, Loisirs…), tableau « Synthèse & Répartition » avec % du revenu, évolution sur 6 mois |
| **Dépenses** | Liste des dépenses du mois (qui, quand, quoi), filtre par catégorie, modification/suppression |
| **Budget** | Les lignes du mois par catégorie (prévu / réel / écart) avec case **pointé** comme dans le tableur |
| **Plus** | Dépenses en attente, soldes d'épargne, « Je suis » (Guillaume/Elise), import de lignes, lien vers la Sheet |

- Bouton **＋** : saisie rapide d'une dépense (montant, catégorie, description, date, qui).
  Les descriptions déjà utilisées sont proposées et retrouvent leur catégorie.
- Les lignes **Carburant / Alimentation / Loisirs / Divers** sont « auto » : leur réel = somme
  des dépenses saisies de cette catégorie (comme le tableau de droite de l'ancien fichier).
- **Nouveau mois** : flèche › puis « Créer ce mois » → recopie les lignes du dernier mois
  (prévu repris, réel = prévu pour les charges fixes, rien de pointé).
- Montant négatif = remboursement.
- L'appli se rafraîchit quand on y revient (pour voir les saisies de l'autre) ; bouton ⟳ sinon.

## Paramètres (feuille `Config`)

- `auteurs` : `Guillaume,Elise`
- `categoriesOperations` : `Alimentation,Carburant,Loisirs,Divers` — une nouvelle catégorie
  (ex. `Santé`) apparaît dans la saisie ; ajoutez une ligne de budget liée en remplissant
  sa colonne `lien` avec le même nom dans la feuille `Lignes`, sinon elle est comptée dans
  « Autres » des Dépenses Variables.

## Pré-remplir les montants

Onglet **Plus → Importer des lignes**, collez des lignes `Catégorie;Libellé;Prévu`
(catégories : Revenus, Besoins Fixes, Transports, Dépenses Variables, Épargne).
