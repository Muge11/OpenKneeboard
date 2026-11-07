# Journal des versions

Format : [versionnage sémantique](https://semver.org/lang/fr/) — `MAJEUR.MINEUR.CORRECTIF`.
Le numéro est défini dans `Code.gs` (`APP_VERSION`) et affiché dans l'en-tête de l'appli.

## [1.8.0] — 2026-11-07 — écarts avec la banque, relevés en lot

- **Nouveau modèle des soldes** : les soldes réels (relevés importés, solde saisi depuis l'appli bancaire) sont des
  points fixes ; les soldes de départ des autres mois sont calculés depuis le point réel le plus proche. Le recalage
  ne réécrit plus les soldes de départ : plus de double compte quand un relevé est importé après un recalage.
- **Analyse de l'écart** (tuile « Compte en banque › Analyser », ou après un recalage) : doublons probables
  (avec suppression d'un appui), opérations récentes pas encore passées, fixes non confirmés, recherche de la
  combinaison d'opérations la plus vraisemblable ; seul le reste « non expliqué » est compté dans le reste à vivre,
  et il se résorbe dès que les opérations manquantes sont importées.
- **Solde de départ recalé** : la ligne « Solde de départ » de la Synthèse (et la pastille « report ») suit le solde
  réel saisi, avec la mention « recalé sur la banque (± écart) » ; un appui ouvre l'analyse.
- Une saisie à la main pointée par un relevé prend la date de l'opération bancaire.
- **Plusieurs relevés d'un coup** (PDF et CSV mélangés) : lus en parallèle sur l'appareil, fusionnés par date,
  lignes communes à deux relevés gardées une seule fois, contrôle des totaux par fichier, un seul envoi au serveur.
- **Relevés importés** (Plus › Importer un relevé) : liste par fichier (mois couverts, nombre d'opérations, date
  d'import), sélection multiple ou « Tout sélectionner », « Supprimer N relevés » en une fois ; les fixes et pointages
  sont remis comme avant, les saisies à la main conservées. Serveur `annulerImports` ; suppression des lignes en une
  seule réécriture de la feuille (beaucoup plus rapide).
- Sur ordinateur, le logo « Budget » de la barre latérale ramène à la synthèse du mois en cours.

## [1.7.0] — 2026-11-07 — recaler sur le solde réel

- **Recaler sur le solde réel** (tuile « Compte en banque », fiche du solde de départ, ou Plus › Paye et compte
  bancaire) : saisie à tout moment du solde affiché par l'appli bancaire (créditeur / débiteur, à une date au choix) ;
  le solde de départ de chaque mois, depuis le mois choisi, est recalculé en remontant les mouvements datés ;
  aperçu avant validation (solde estimé par l'appli, écart, tableau avant / après par mois) ; bouton « Annuler »
  après le recalage ; historique des 24 derniers recalages ; serveur `saveSoldesMany`.
- La tuile « Solde de votre compte » de la Synthèse ouvre directement le recalage.

## [1.6.0] — 2026-11-07 — import calé sur les payes, annulation par mois

- **Mois de budget d'après les payes du relevé** : les payes sont repérées par leur libellé (montant libre, ≥ 300 €) ;
  la plus tôt de celles qui financent un mois fixe le début de la période ; toute opération datée de ce jour ou après
  (prélèvements, achats de fin de mois) est comptée sur le mois suivant. Sans paye repérée : réglage « Jour de paye »,
  sinon même jour que la paye la plus proche. Écran « Payes repérées » ; option « Utiliser ces dates dans l'appli »
  (règle le jour de paye et les dates réelles par mois).
- **Payes de montant variable** rattachées à leur fixe (même libellé et même mois, écart jusqu'à 40 %) : le montant
  réel remplace le prévu, sans double compte.
- **Annuler l'import d'un mois à tout moment** (bas de la liste des Mouvements du mois, ou Plus › Importer un relevé ›
  Mois déjà importés) : supprime uniquement les opérations importées de ce mois, remet fixes et pointages comme avant,
  retire le solde de départ importé ; les saisies à la main sont conservées. Registre `Imports` (Google Sheet),
  serveur `annulerImportMois`.
- Pointage d'une saisie à la main par le relevé plus strict : même montant **et** (même jour, même libellé ou nom en commun).
- Saisie à la main : mois de budget proposé d'après le jour de paye quand il est réglé.

## [1.5.1] — 2026-11-07

- Écran d'accueil : affiché 2,5 s au lieu de 1,5 s (pendant le chargement) ; plus aucun montant (date du jour et
  avancement du mois à la place du reste à vivre).

## [1.5.0] — 2026-11-07 — compte en banque, bilan annuel, import intelligent

- **Compte en banque** (Synthèse) : solde estimé aujourd'hui et point le plus bas avant la paye, d'après le solde de
  départ et les mouvements datés (prélèvements à venir compris) ; **découvert autorisé** réglable (Plus › Paye et compte
  bancaire) ; tuile bleue / jaune (passage à découvert) / rouge (au-delà de l'autorisé). Notification « Risque de
  découvert » (une fois par période et par niveau), réglable ; serveur `accountForecast_`.
- **Bilan de l'année** (Synthèse › Année) : solde de l'année et moyenne mensuelle, entrées / sorties comparées à
  l'année précédente, barres mois par mois (un mois s'ouvre d'un appui), tableau par libellé (total, moyenne / mois,
  évolution vs N-1).
- **Virements internes** : type de libellé « hors budget » et libellés « Virements internes » créés automatiquement ;
  exclus des totaux, du reste à vivre, des graphiques, des conseils et des alertes, mais comptés dans le solde du compte ;
  reconnus à l'import (virement vers / depuis un membre du foyer).
- **Import** : une dépense déjà saisie à la main est **pointée** par la ligne du relevé (rapprochement) au lieu d'être
  doublée ; **cartes bancaires** (X5480…) associées à une personne (achats attribués) ; **prélèvements réguliers**
  proposés comme fixes après l'import (frais et intérêts exclus ; ceux vus un autre mois pré-cochés) ; serveur
  `saveCartes`, `toFixeMany` ; l'annulation d'import rétablit aussi les pointages.
- **Annuler** : bouton dans le message après une suppression ou une modification de mouvement (6 s).

## [1.4.0] — 2026-11-07 — thèmes, relevés CSV, solde du compte

- **Cinq thèmes** (Bento, Minuit, Ardoise, Forêt, Terracotta), chacun en clair et en sombre, choisis dans Mes préférences
  (galerie d'aperçus) ; couleur d'accent « celle du thème » ou au choix. Toutes les couleurs passent par des variables ;
  550 contrôles de contraste WCAG réussis (texte ≥ 7:1, textes secondaires ≥ 4,5:1).
- **Barre du bas** : 5 cases de même largeur (Synthèse · Mouvements · **+** · Budget · Épargne/Plus), le + au centre exact
  et plus grand ; « Plus » s'ouvre aussi avec l'initiale en haut à gauche (mesuré : écarts identiques à 0,1 px près sur
  Pixel 7, iPhone SE et tablette).
- **Import de relevés bancaires CSV** (Plus › Importer un relevé) : séparateur, encodage (UTF-8 / Windows-1252),
  préambule et colonnes détectés (Date, Libellé, Montant ou Débit/Crédit, Solde), libellés bancaires nettoyés
  (« CB », « PRLV SEPA », « VIREMENT EN VOTRE FAVEUR »…), libellé proposé d'après vos saisies puis par mots-clés,
  correction appliquée à tout le même commerce, salaires de fin de mois comptés le mois suivant, occurrences de fixes
  complétées et pointées, doublons repérés (ré-import sans effet), mouvements importés pointés ; serveur `importReleve`.
- **Relevés PDF** (format des relevés mensuels, ex. Crédit Agricole) : lus sur l'appareil avec pdf.js (chargé à la demande
  depuis cdnjs), colonnes Date / Libellé / Débit / Crédit repérées par position, lignes de détail rattachées,
  année déduite de la date d'arrêté, solde courant recalculé depuis l'« ancien solde » ; **contrôle** : ancien solde +
  opérations = nouveau solde (affiché ✓ ou ⚠).
- **Vérifier avant d'importer** : écran récapitulatif mois par mois et libellé par libellé (ce qui sera rempli, fixes
  complétés, soldes de départ) ; chaque ligne s'ouvre et se corrige (montant, sens, description, date, mois de budget,
  libellé, rattachement au fixe, inclusion). Après l'import : bandeau « Annuler l'import » (serveur `undoImport` :
  supprime les mouvements ajoutés, rétablit les fixes complétés et les soldes ajoutés).
- **Classement des opérations bancaires** : vocabulaire des banques (prêts, assurances et mutuelles, payes,
  parkings et péages, enseignes, abonnements, impôts…) ; proposition de créer un libellé manquant (ex. « Assurances »)
  et d'y reclasser les lignes ; descriptions complétées par le détail pour les prélèvements génériques ;
  rattachement aux fixes strict (montant identique, même libellé ou nom du fixe dans le libellé bancaire) ;
  deux opérations identiques d'un même relevé sont conservées.
- **Solde réel en début de mois** (juste avant la paye) : découvert ou supplément reporté dans le reste à vivre,
  la courbe, le tableau et le compte rendu e-mail ; tuile « Renseigner » sur la Synthèse, écart avec le reste calculé ;
  rempli automatiquement depuis la colonne Solde d'un relevé ; serveur `saveSolde`.
- **Finitions** : emoji centrés optiquement (mesure de l'encre, ±0,5 px), icônes tirelire et étincelle recentrées,
  texte des pastilles et boutons remonté de 0,07 em (métriques de la police), échelle typographique resserrée,
  en-tête « initiale · ‹ mois › · actualiser », libellés en tuiles dans la saisie, fondu des rangées de filtres.

## [1.3.0] — 2026-11-06 — notifications

- **Vraies notifications (Web Push)** sur l'appli installée, même fermée : rappel de saisie (délai et heure au choix),
  enveloppe presque vide (seuil 70 / 80 / 90 %), enveloppe dépassée, rythme de dépense inhabituel (≤ 1 / semaine),
  montants affichés ou non. Réglages par utilisateur dans **Plus › Notifications** ; bouton « Envoyer un test » ;
  liste des appareils (retrait). Un appareil disparu (404 / 410) est retiré automatiquement.
- **Serveur** : signature VAPID (ECDSA P-256, nonce RFC 6979) et chiffrement aes128gcm (RFC 8291) écrits en JavaScript
  (Apps Script n'a ni ECDSA ni AES), vérifiés contre les primitives de Node ; clés VAPID créées une fois (propriétés
  du script) ; feuille `Push` (appareils, hors sauvegardes) ; `savePushSub`, `deletePushSub`, `sendTestPush` ;
  vérification horaire `notifyCheck_` appelée par `reportCheck` (anti-doublon par sujet, 9 h–21 h, rappel à l'heure choisie).
  Nouvelle autorisation : `script.external_request` (envoi vers le service de notifications).
- **Coquille PWA** : abonnement demandé par l'appli via `postMessage` (origines Google uniquement), bandeau
  « Autoriser » si l'invite ne peut pas s'afficher ; `sw.js` affiche les notifications et ouvre l'appli
  (action « + Saisir » sur le rappel) ; cache `budget-shell-v3`.
- **Enveloppes affichées au choix** (Budget › Choisir les enveloppes affichées) : les enveloppes masquées restent dans
  les totaux mais disparaissent des listes, de la Synthèse, des conseils et des alertes (réglage personnel).
- Interrupteurs pour les réglages.

## [1.2.0] — 2026-11-06

- **Écran d'accueil** « Bonjour, Prénom. » (prénom seul, même si la fiche contient « prénom.nom ») : prénom écrit lettre par lettre (bascule 3D), trait d'accent,
  reste à vivre du jour, puis lever de rideau sur l'appli ; suit le thème (noir en sombre, crème en clair) et la couleur d'accent.
- **Navigation A** : Synthèse · Mouvements · Budget · Épargne · Plus en icônes, bouton + surélevé au centre
  (trou toujours centré à l'écran) ; « Budget » regroupe Enveloppes et Fixes en sous-onglets.
- **Onglet Épargne** (sorti de « Plus ») : total, versé / retiré du mois, comptes ; masqué si aucun compte autorisé.
- **Jour de paye** (Plus › Jour de paye) : jour habituel, option « avancée au vendredi si week-end », date réelle
  modifiable pour un mois ; la période de budget va d'une paye à la suivante (jours restants, reste par jour,
  courbe, conseils, mois affiché à l'ouverture). Serveur : `savePaie`, champ `paie` dans `getAll`.
- **Conseil du jour calibré** : uniquement les dépenses ponctuelles ; fréquence inhabituelle d'un libellé
  (avec le commerce le plus fréquent), rythme de dépense comparé aux 4 mois précédents au même jour de la période,
  dépense plus tôt que d'habitude (ex. carburant), enveloppe dépassée, reste par jour.
- **Synthèse & répartition** : plus de lien « détail » ; la ligne d'un groupe ouvre son détail, la flèche le déplie.
- **À pointer** : liste dans l'ordre du relevé (plus ancien d'abord) reprenant après le dernier pointage,
  mouvements à venir repliés ; repère « Pointé jusqu'ici » dans la liste complète.
- **Pages allégées** : filtres de Mouvements en puces, lignes réduites à l'essentiel (libellé, initiale, icône fixe),
  Enveloppes (libellés sans enveloppe regroupés à part, aide repliée), Fixes et Enveloppes avec tuiles de résumé.
- **Rigueur typographique** : hauteurs fixes et centrage des puces, pastilles, étiquettes et boutons ; grille de 4 px ;
  tuiles réparties (titre en haut, chiffre, détail calé en bas).
- **Glisser plus fiable** : geste horizontal réservé à l'appli (`touch-action: pan-y`), seuils revus, prise en compte
  de la vitesse, rangées défilantes respectées, annulation gérée.
- **Couleur du thème au choix** : citron, menthe, corail, lavande, ciel, ambre (accent et tuile Conseil).

## [1.1.1] — 2026-11-06

- **Glisser = changer de page** (téléphone) : gauche/droite passe d'un onglet à l'autre ; résistance en bout de liste.
  Le mois se change avec les flèches ou le sélecteur de mois.
- **Transitions de page** : glissement en perspective dans le sens de la navigation (onglets, mois, sous-onglets
  de la Synthèse), tuiles et cartes qui apparaissent en cascade ; respect du réglage « réduire les animations ».
- **Effet 3D** : inclinaison des tuiles de la Synthèse vers la souris (ou le doigt à l'appui), reflet lumineux, ombre portée.
- **Survol souris** : boutons, puces, cartes, lignes, icônes et bouton + animés.

## [1.1.0] — 2026-11-06 — nouvelle présentation « Bento »

- **Refonte graphique complète** : police Plus Jakarta Sans, thème clair crème et thème sombre chaud,
  tuiles pastel (sauge, pêche, lavande, beurre, ciel) et accent vert citron.
- **Synthèse en tuiles** : reste à vivre (centimes en petit, écart au prévu, montant par jour restant,
  courbe du mois, avancement du mois), entrées, sorties, répartition par type, 3 enveloppes les plus entamées.
- **Conseil du jour** : enveloppe dépassée, reste par jour de l'enveloppe la plus entamée, mouvements à pointer.
- **Navigation** : pilule flottante avec le nom de l'onglet actif, bouton + rond à côté ; sur PC, barre latérale
  avec logo, bouton « Nouveau mouvement » vert citron, onglet actif en noir.
- Icônes de libellés colorées selon le type, initiale de la personne dans la pastille « saisi par ».
- Fiches, champs, boutons, puces et listes harmonisés ; couleur de la barre d'état adaptée au thème.

## [1.0.0] — 2026-11-06 — version stable

- **Ouverture plus rapide** : le serveur ne prend plus de verrou à l'ouverture s'il n'y a pas de fixes à reporter
  (plus d'attente quand un autre utilisateur enregistre au même moment).
- **Pointage groupé** : les cases cochées à la suite partent en un seul envoi (une ligne au journal).
- **Calculs mémorisés** : chaque mois n'est calculé qu'une fois par affichage (graphiques, conseils, évolution).
- **Hors connexion** : bandeau, consultation seule, saisie remise en route au retour du réseau (rechargement automatique).
- **Messages d'erreur compréhensibles** (connexion perdue, session expirée, serveur occupé).
- **Sauvegardes** : au plus une toutes les 10 min à la fermeture ; conservation des 90 dernières sauvegardes de nuit
  (≈ 3 mois) et des 40 dernières autres. **Journal** plafonné aux 15 000 dernières actions.
- Code public : exemples anonymisés (aucun montant ni compte réel).

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
