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
- création de routines nommées avec sélection et ordre des exercices ;
- lancement d'une routine dans une séance indépendante avec chronomètre ;
- saisie manuelle du poids (virgule ou point), des répétitions et du RPE facultatif ;
- bilan et historique des séances : durée, séries, répétitions, tonnage et notes ;
- persistance locale hors connexion ;
- statistiques hebdomadaires pondérées par groupe musculaire ;
- calculateur de chargement de barre à inventaire limité ;
- modèles et algorithmes métier testables sans UI Android.

L'édition des routines enregistrées, le chronomètre de repos avec notifications, les graphiques, Health Connect, Wear OS, le cloud
et les fonctions sociales sont volontairement différés. Leur ordre est décrit dans
[`docs/ROADMAP.md`](docs/ROADMAP.md).

## Utiliser routines et séances

1. Dans **Routines**, saisir un nom, cocher les exercices et régler leur ordre, puis enregistrer.
2. Dans **Séance**, sélectionner la routine et toucher **Lancer la séance**. Une séance avec ses
   propres identifiants et son heure de début est sauvegardée dès le démarrage.
3. Pour chaque exercice, saisir le poids en kg et les répétitions, puis **Valider la série**.
   Les charges de 0 à 1 000 kg sont acceptées avec trois décimales maximum ; le RPE est facultatif.
   Une série incorrecte peut être retirée et ressaisie.
4. Toucher **Fin de l'entraînement**, puis confirmer. Les saisies de séries encore présentes
   doivent d'abord être validées ou vidées. Les exercices sans série peuvent être ignorés.
5. Le bilan conserve les séries, notes, début et fin de la séance. Il reste consultable dans
   **Séances enregistrées**, depuis l'onglet Séance.

Chaque série validée est sauvegardée hors ligne. Après fermeture du processus, la séance active
est rechargée et sa durée recalculée depuis son heure de début. Les champs non validés ne sont pas
persistés ; les notes sont sauvegardées à la prochaine écriture de séance ou à sa finalisation.
Le tonnage est la somme `charge × répétitions` de toutes les séries validées, calculée à partir
des valeurs persistées. Il ne double pas automatiquement la charge des haltères et n'est pas
une estimation de la masse corporelle. Le volume musculaire suit sa propre règle (séries de travail).

Les tables de la version 1 couvraient déjà routines et séances : aucune migration ni suppression
des anciennes séances n'est nécessaire pour ce parcours. Le catalogue initial contient quatre exercices.

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
