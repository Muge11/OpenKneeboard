# 📱 Budget — appli installable (PWA)

Ce dossier contient la « coquille » de l'appli : une petite page qui affiche votre web app Apps Script
**en plein écran, sans barre de navigateur**, avec son icône, son écran de démarrage et un raccourci
« Nouvelle dépense » (appui long sur l'icône). Vos données restent dans Google Sheets.

## 1. Mettre la coquille en ligne (une seule fois, ~5 min, gratuit)

Elle doit être servie en **https**. Le plus simple : **GitHub Pages**.

1. Sur github.com, créez un dépôt **public** nommé par exemple `budget-app` (*New repository*).
2. *Add file › Upload files* : déposez les fichiers de ce dossier
   (`index.html`, `manifest.webmanifest`, `sw.js`, `icon-192.png`, `icon-512.png`, `icon-maskable-512.png`) → *Commit*.
3. *Settings › Pages* : *Source* = **Deploy from a branch**, *Branch* = `main` / `(root)` → *Save*.
4. Après 1 à 2 minutes, l'adresse s'affiche : `https://<votre-pseudo>.github.io/budget-app/`.

> Alternative : Netlify Drop (app.netlify.com/drop) — glissez le dossier, créez un compte gratuit pour garder le site.

La coquille ne contient **aucune donnée** ni l'adresse de votre appli : chacun la saisit sur son téléphone.

## 2. Installer sur chaque Pixel

1. Dans l'appli Budget (version navigateur) : **Plus › Mes préférences › Installer l'appli › Copier l'adresse**.
2. Ouvrez l'adresse GitHub Pages dans **Chrome** sur le Pixel, collez l'adresse copiée → **Ouvrir l'appli**.
3. Menu **⋮ › Installer l'application** (ou « Ajouter à l'écran d'accueil » › Installer).
4. L'icône **Budget** apparaît : l'appli s'ouvre en plein écran.

Astuce : `…/budget-app/?app=<adresse /exec>` enregistre l'adresse directement (pratique à envoyer par message).
Pour changer d'adresse plus tard : `…/budget-app/#config`.

## Bon à savoir

- Chrome doit autoriser les cookies tiers (réglage par défaut) : la connexion Google se fait dans l'appli.
  Si l'écran reste blanc : *Chrome › Paramètres › Paramètres des sites › Cookies tiers › Autoriser*,
  ou utilisez « Ouvrir dans Chrome » sur l'écran de chargement.
- Pour un utilisateur qui n'est pas le propriétaire du script, Google affiche une fine bannière
  « Cette application a été créée par un utilisateur de Google Apps Script » : elle ne peut pas être retirée.
- La web app doit être en **v0.7.0 ou plus** (elle autorise l'affichage dans l'appli installée).
