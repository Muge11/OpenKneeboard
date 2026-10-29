# Coach IA — appli Android trail & VTT XC

Appli Android personnelle et gratuite : un planificateur intégré place tes séances dans un calendrier, suit ta forme à partir
de Strava (dont tes activités COROS et Zwift) et, si tu ajoutes une clé gratuite Google Gemini, te laisse discuter avec un coach IA.

## Installer l'APK

1. Chaque push sur la branche lance le workflow **Coach IA (Android)** (onglet *Actions* du dépôt).
2. À la fin, l'APK est publié dans la pré-release **« Coach IA (dernière version) »** (onglet *Releases*, tag `coach-ia-latest`)
   et aussi en artefact du workflow.
3. Sur le téléphone : ouvre la release, télécharge `coach-ia.apk`, ouvre-le et autorise l'installation depuis le navigateur.
   Les versions suivantes s'installent par-dessus sans perdre les données (même clé de signature).

## Premier lancement

Onglet **Réglages** :

- **Gemini (optionnel, gratuit)** : clé à créer sur <https://aistudio.google.com/apikey> (sans carte bancaire). Quotas gratuits
  limités par minute et par jour ; en offre gratuite, Google peut utiliser les échanges pour améliorer ses modèles.
  Sans clé, le planificateur et le bilan fonctionnent quand même.
- **Strava** : crée une application sur <https://www.strava.com/settings/api> avec *Authorization Callback Domain* = `localhost`,
  colle le *Client ID* et le *Client Secret*, puis « Connecter Strava » et accepte toutes les autorisations.
- **Profil** : FTP, FC max/repos/seuil, poids, objectifs, minutes disponibles par jour.

## Fonctions

| Onglet | Contenu |
|---|---|
| Calendrier | Mois avec pastilles (course, club, séance, réalisée), impératifs grisés. Ajout de courses (priorité A/B/C), entraînements club, séances, impératifs/impondérables. « Planifier 2 semaines » (planificateur intégré, gratuit) et « Avec l'IA » si une clé Gemini est saisie. Pour chaque séance : export Zwift (`.zwo`), agenda du téléphone, statut fait / pas fait. |
| Coach | Discussion avec Gemini (optionnelle). Le coach lit et modifie lui-même le calendrier et le profil, et peut lancer le planificateur. |
| Forme | CTL / ATL / TSB avec prévision 28 jours, forme attendue aux prochaines courses, FTP et W/kg, bilan complet calculé localement. |
| Activités | Activités Strava synchronisées avec TSS estimé (puissance, FC, effort relatif ou durée). |
| Réglages | Clé Gemini, Strava, explications COROS, profil, disponibilités. |

### Planificateur intégré

Blocs de 3 semaines de développement + 1 semaine allégée, affûtage les 10 jours avant une course A (4 jours pour une B),
veille de course « déblocage » et lendemain libre, au moins un jour de repos par semaine, jamais deux séances dures d'affilée.
Il respecte tes disponibilités, tes impératifs (indisponible, temps limité, fatigue, blessure…) et ne touche jamais aux
évènements que tu as saisis. Les séances home trainer ont des blocs en % de FTP exportables vers Zwift.

### Sources de données

- **Strava** : activités (trail, VTT, Zwift…), FTP et poids du profil Strava.
- **COROS** : pas d'API ouverte aux particuliers ; les activités arrivent via la synchro automatique COROS → Strava.
- **Zwift Ride** : les `.zwo` exportés se copient dans `Documents/Zwift/Workouts/<ton id>` sur l'appareil qui fait tourner Zwift.

## Données et confidentialité

Tout est stocké sur le téléphone, chiffré avec une clé du Keystore Android. Les données ne quittent le téléphone que vers
Strava (synchro) et, si tu l'utilises, l'API Gemini de Google (quand tu parles au coach).

## Développement

- `core/` : Kotlin pur (modèles, stockage, calcul de charge, planificateur, bilan, client Strava, coach Gemini et ses outils). Tests : `./gradlew :core:test`.
- `app/` : interface Jetpack Compose. APK : `./gradlew :app:assembleDebug`.
