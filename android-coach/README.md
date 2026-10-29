# Coach IA — appli Android trail & VTT XC

Appli Android personnelle qui planifie tes séances avec Claude (IA d'Anthropic), les place dans un calendrier,
suit ta forme (Strava, COROS, Renpho, Zwift) et te laisse discuter directement avec le coach dans l'appli.

## Installer l'APK

1. Chaque push sur la branche lance le workflow **Coach IA (Android)** (onglet *Actions* du dépôt).
2. À la fin, l'APK est publié dans la pré-release **« Coach IA (dernière version) »** (onglet *Releases*, tag `coach-ia-latest`)
   et aussi en artefact du workflow.
3. Sur le téléphone : ouvre la release, télécharge `coach-ia.apk`, ouvre-le et autorise l'installation depuis le navigateur.
   Les versions suivantes s'installent par-dessus sans perdre les données (même clé de signature).

## Premier lancement

Onglet **Réglages** :

- **Clé API Anthropic** : à créer sur <https://console.anthropic.com> (*API Keys*). L'usage est facturé par Anthropic à la consommation.
  Modèle par défaut : Claude Opus 5.5.
- **Strava** : crée une application sur <https://www.strava.com/settings/api> avec *Authorization Callback Domain* = `localhost`,
  colle le *Client ID* et le *Client Secret*, puis « Connecter Strava » et accepte toutes les autorisations.
- **Profil** : FTP, FC max/repos/seuil, poids, objectifs, minutes disponibles par jour.

## Fonctions

| Onglet | Contenu |
|---|---|
| Calendrier | Mois avec pastilles (course, club, séance, réalisée), impératifs grisés. Ajout de courses (priorité A/B/C), entraînements club, séances, impératifs/impondérables. « Planifier 2 semaines avec l'IA ». Pour chaque séance : export Zwift (`.zwo`), ajout à l'agenda du téléphone, statut fait / pas fait. |
| Coach | Conversation avec l'IA, envoi de captures d'écran, raccourcis (analyse complète, plan, prévision course, fatigue, FTP). Le coach lit et modifie lui-même le calendrier, le profil et les données de santé. |
| Forme | CTL / ATL / TSB avec prévision 28 jours d'après le calendrier, forme attendue aux prochaines courses, FTP et W/kg, poids et composition corporelle, dernières mesures. |
| Activités | Activités Strava synchronisées avec TSS estimé (puissance, FC, effort relatif ou durée). |
| Réglages | Clé API, modèle, Strava, explications COROS, profil, disponibilités. |

### Sources de données

- **Strava** : activités (trail, VTT, Zwift…) et FTP du profil Strava.
- **COROS** : pas d'API ouverte aux particuliers. Les activités arrivent via la synchro automatique COROS → Strava ;
  sommeil, VFC, récupération, VO2max s'importent par capture d'écran envoyée au coach.
- **Renpho** : capture d'écran (bouton de l'onglet Forme, ou « Partager » depuis Renpho vers Coach IA) ; le coach lit les valeurs et les enregistre.
- **Zwift Ride** : les séances home trainer ont des blocs en % de FTP, exportables en `.zwo`
  (à copier dans `Documents/Zwift/Workouts/<ton id>` sur l'ordinateur/la tablette qui fait tourner Zwift).

## Données et confidentialité

Tout est stocké sur le téléphone, chiffré avec une clé du Keystore Android. Les données ne quittent le téléphone que vers
Strava (synchro) et l'API Anthropic (quand tu parles au coach).

## Développement

- `core/` : Kotlin pur (modèles, stockage, calcul de charge, client Strava, agent Claude et ses outils). Tests : `./gradlew :core:test`.
- `app/` : interface Jetpack Compose. APK : `./gradlew :app:assembleDebug`.
