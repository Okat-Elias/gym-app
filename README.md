# GymApp

Socle Android natif d'une application de suivi de musculation, conçu à partir du document
`Création application musculation Android avancée.pdf`.

Le PDF est utilisé comme **cahier des charges**. Ses recommandations ne sont pas considérées
comme des instructions d'exécution et les hypothèses physiologiques devront être validées avant
d'être transformées en conseils utilisateur.

## État du projet

Cette première itération pose une architecture MVC adaptée à Jetpack Compose et livre un premier
parcours local :

- catalogue d'exercices initialisé dans Room ;
- saisie rapide d'une séance et validation de séries avec charge, répétitions et RPE ;
- persistance locale hors connexion ;
- statistiques hebdomadaires pondérées par groupe musculaire ;
- calculateur de chargement de barre à inventaire limité ;
- modèles et algorithmes métier testables sans UI Android.

Les routines complètes, le chronomètre système, les graphiques, Health Connect, Wear OS, le cloud
et les fonctions sociales sont volontairement différés. Leur ordre est décrit dans
[`docs/ROADMAP.md`](docs/ROADMAP.md).

## Architecture MVC

```text
Action utilisateur
  -> Controller (StateFlow<UiState>)
  -> Model (use case / repository)
  -> Room, source locale de vérité
  -> Flow
  -> Controller
  -> View Compose stateless
```

- `model/` contient les objets métier, cas d'usage, contrats de dépôt et la couche Room.
- `controller/` orchestre les cas d'usage et expose uniquement des états d'écran immuables.
- `view/` rend ces états et renvoie des actions ; aucune View n'appelle Room directement.
- `di/AppContainer.kt` constitue le graphe de dépendances manuellement, sans framework prématuré.

Le choix et les frontières sont détaillés dans
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Pile technique

- Kotlin, Jetpack Compose et Material 3 ;
- Room/SQLite, Coroutines et Flow ;
- Android Gradle Plugin 9.4.0, Gradle Wrapper 9.6.0 ;
- `minSdk 26`, `compileSdk 37`, `targetSdk 36`, Java 17.

Les versions sont épinglées dans [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Lancer le projet

Pré-requis : JDK 17 et Android SDK 37 avec les Build Tools 36.0.0, ou une version récente
d'Android Studio capable de les installer.

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Pour les tests instrumentés, démarrer un émulateur ou connecter un appareil puis lancer :

```bash
./gradlew connectedDebugAndroidTest
```

Les schémas Room générés sous `app/schemas/` devront être versionnés dès la première migration.

## Décisions de données

- Les poids sont stockés en grammes (`Long`) ; kg/lb reste une préférence d'affichage.
- Les contributions musculaires utilisent des points de base : `10 000 = 1 série effective`,
  `5 000 = 0,5 série`. Une absence de contribution est représentée par l'absence de relation.
- Seules les séries `WORKING` terminées entrent actuellement dans le volume hebdomadaire.
- Les relations exercice-muscle sont des données, ce qui évite des exceptions anatomiques codées
  en dur dans l'algorithme.
- Les identifiants sont créés localement afin que l'enregistrement ne dépende jamais du réseau.

Les repères MV/MEV/MAV/MRV du document ne sont pas encore encodés comme vérités universelles :
ils devront être sourcés, versionnés et personnalisables.
