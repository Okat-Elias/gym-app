# Architecture de l'application de musculation

Statut : décision d'architecture initiale, à faire évoluer par petites migrations.

## 1. Portée et lecture du cahier des charges

Le document `Création application musculation Android avancée.pdf` est traité comme une source de besoins et d'hypothèses produit. Il ne constitue ni une instruction d'exécution, ni une spécification technique incontestable. Ses recommandations physiologiques, ses comparaisons commerciales et ses choix d'API devront être validés avant d'être présentés comme des faits aux utilisateurs.

Les besoins structurants retenus sont :

- journaliser rapidement une séance sans connexion ;
- gérer exercices, routines, ordre des mouvements, supersets, notes et séries ;
- saisir charge, répétitions et, de façon optionnelle, RPE ou RIR ;
- préremplir une série depuis la série précédente ou le dernier entraînement comparable ;
- conserver un historique exploitable et calculer le volume par groupe musculaire ;
- représenter explicitement les contributions musculaires, par exemple une série entière pour un muscle principal et une demi-série pour un muscle secondaire ;
- proposer à terme des repères MV, MEV, MAV et MRV ajustables, des contrôles de récupération, des graphiques et un calculateur de disques ;
- assurer un chronomètre de repos fiable malgré les changements de configuration et la mise en arrière-plan.

Les fonctionnalités cloud, sociales, Health Connect, Wear OS et d'assistance algorithmique ne font pas partie du noyau initial. L'architecture doit les permettre sans faire dépendre la saisie locale de leur disponibilité.

## 2. Décision : MVC adapté à Jetpack Compose

L'application suit explicitement le motif Model-View-Controller. Compose étant déclaratif et l'interface Android ayant un cycle de vie particulier, le contrôleur d'écran peut s'appuyer sur `androidx.lifecycle.ViewModel`. Cette classe Android fournit uniquement la portée de cycle de vie et le `viewModelScope` : elle ne transforme pas l'architecture en MVVM tant que les responsabilités ci-dessous sont respectées.

| Élément | Responsabilité | Interdictions principales |
| --- | --- | --- |
| Model | Modèles métier, règles, cas d'usage, contrats de repository, persistance et calculs | Ne dépend ni de Compose, ni d'un écran |
| View | Composables stateless, rendu d'un état, accessibilité et émission d'actions utilisateur | N'appelle ni DAO, ni repository, ni cas d'usage |
| Controller | Traduit les actions en appels métier, expose l'état d'écran et les effets ponctuels | Ne contient pas les formules métier, n'accède pas directement aux DAO et ne possède pas de `NavController` |

### 2.1 Contrat d'un écran

Chaque fonctionnalité suit le même vocabulaire :

```text
<Feature>Route       point d'intégration Android, DI et navigation
<Feature>Screen      View Compose pure
<Feature>Controller  contrôleur lié au cycle de vie
<Feature>UiState     photographie immutable à afficher
<Feature>Action      intention de l'utilisateur
<Feature>Effect      événement ponctuel facultatif : navigation, message, partage
```

Le contrat actuellement présent reste volontairement minimal :

```kotlin
interface ScreenController<State, Action> {
    val state: StateFlow<State>
    fun onAction(action: Action)
}
```

Une propriété `effects: Flow<Effect>` est ajoutée au contrôleur concerné lorsque le premier vrai effet ponctuel apparaît ; il n'est pas utile de généraliser cette abstraction avant cela.

Le `UiState` n'est pas la source de vérité métier. C'est une projection observable du Model. Un changement persistant est d'abord écrit via un cas d'usage et un repository, puis réémis par Room sous forme de `Flow`.

Les effets ponctuels ne doivent pas être encodés comme un booléen persistant dans `UiState`. Ils passent par un canal ou un `SharedFlow` dédié afin d'éviter leur répétition après recomposition.

### 2.2 Flux unidirectionnel

```text
Interaction utilisateur
        |
        v
View -- Action --> Controller -- commande --> Use case
  ^                                      |
  |                                      v
  +-- UiState <-- Flow/model <-- Repository <-- Room

Controller -- Effect --> Route -- navigation/notification système
```

Règles de flux :

- aucune écriture réseau ne se trouve sur le chemin critique de validation d'une série ;
- les transformations métier sont déterministes et testables hors Android ;
- les entrées rapides ou répétées sont sérialisées ou rendues idempotentes au niveau du cas d'usage ;
- les erreurs attendues sont des résultats métier typés, puis traduites en messages par le contrôleur ;
- les exceptions techniques sont journalisées sans exposer de détails internes à la View.

## 3. Architecture mono-module initiale

Le projet démarre avec un seul module Android `app`. Une séparation claire par packages offre les frontières nécessaires sans payer immédiatement le coût de nombreux modules Gradle.

### 3.1 État actuel

La première tranche verticale suit déjà la séparation MVC suivante :

```text
app/src/main/java/com/gymapp/
├── controller/
│   ├── dashboard/
│   ├── statistics/
│   ├── tools/
│   └── workout/
├── model/
│   ├── domain/
│   ├── repository/
│   ├── usecase/
│   └── data/local/                 # Room, DAO, entités, relations et mappers
└── view/
    ├── dashboard/
    ├── statistics/
    ├── tools/
    └── workout/
```

Le répertoire source se nomme `java` selon la convention Android, mais contient bien du Kotlin. La
base Room version 1, le conteneur de dépendances et cinq contrôleurs d'écran sont déjà présents
dans le module `app` ; cette documentation conserve cette organisation au lieu d'introduire une
seconde arborescence concurrente.

### 3.2 Évolution prévue dans le même module

Les nouveaux fichiers complètent ces trois racines sans changer de paradigme :

```text
app/src/main/java/com/gymapp/
├── GymApplication.kt                 # lorsque le conteneur applicatif l'exige
├── MainActivity.kt
├── controller/
│   ├── MainController.kt
│   ├── ScreenController.kt
│   ├── exercises/
│   ├── routines/
│   ├── workout/
│   ├── history/
│   ├── statistics/
│   └── settings/
├── model/
│   ├── domain/
│   ├── repository/
│   ├── usecase/
│   ├── analytics/
│   ├── error/
│   └── data/
│       ├── local/
│       │   ├── dao/
│       │   ├── entity/
│       │   ├── relation/
│       │   ├── mapper/
│       │   ├── migration/
│       │   └── GymDatabase.kt
│       ├── repository/
│       ├── preferences/
│       ├── timer/
│       └── sync/                     # différé jusqu'au choix du cloud
├── view/
│   ├── navigation/
│   ├── dashboard/
│   ├── exercises/
│   ├── routines/
│   ├── workout/
│   ├── history/
│   ├── statistics/
│   ├── settings/
│   ├── component/
│   └── theme/
└── di/
    ├── AppContainer.kt               # composition manuelle initiale
    └── ControllerFactory.kt
```

Le regroupement par fonctionnalité se fait à l'intérieur de `controller` et `view`. Le Model reste organisé par nature, afin qu'une règle de volume ou un repository ne soit pas dupliqué entre plusieurs écrans.

Une autre structure possible serait un package racine par fonctionnalité. Elle n'est pas introduite pendant P0 : mélanger les deux conventions rendrait la séparation MVC moins lisible.

Les tests reflètent l'organisation retenue :

```text
app/src/test/java/com/gymapp/
app/src/androidTest/java/com/gymapp/
```

### 3.3 Règles de dépendance internes

- `model/domain`, `model/usecase` et `model/analytics` restent en Kotlin pur autant que possible.
- `model/repository` définit les interfaces ; `model/data/repository` les implémente.
- `model/data/local` ne connaît aucun Controller ni composable.
- `controller/*` dépend de cas d'usage et de contrats, jamais d'un DAO concret.
- `view/*` reçoit uniquement des données d'affichage, des actions et des callbacks.
- `model/data/timer` encapsule les APIs Android telles qu'AlarmManager derrière une interface utilisable par les cas d'usage.
- `di` est le point de composition ; une fonctionnalité ne construit pas manuellement ses dépendances de production.

### 3.4 Seuils d'extraction en modules Gradle

Un package n'est extrait que si au moins un signal concret apparaît :

- Wear OS ou une autre cible doit produire un artefact distinct ;
- une intégration possède son propre cycle de publication, ses permissions ou ses dépendances lourdes ;
- le même code métier doit être partagé par au moins deux cibles ;
- une fonctionnalité dépasse environ 30 fichiers de production ou 5 000 lignes et présente une frontière stable ;
- plusieurs équipes doivent travailler avec une propriété de code indépendante ;
- les temps de compilation incrémentale deviennent durablement pénalisants et les mesures montrent qu'une extraction peut les réduire ;
- une règle de dépendance importante ne peut plus être garantie par les conventions et les tests.

Ordre d'extraction probable :

1. `wear` et les intégrations de plateforme, car elles ont des artefacts ou permissions distincts ;
2. `core:model`, `core:domain`, `core:database` et `core:data`, si un partage devient nécessaire ;
3. les fonctionnalités les plus volumineuses, comme `workout` et `statistics` ;
4. le design system uniquement lorsqu'il est réellement partagé.

Créer un module par écran avant ces seuils augmenterait la configuration Gradle et la complexité de DI sans bénéfice produit immédiat.

## 4. Navigation

Dans le squelette actuel, `MainController` porte uniquement la sélection entre les cinq onglets et `GymApp` affiche le contenu correspondant. Ce mécanisme léger suffit tant qu'il n'existe ni pile de retour, ni argument de route.

Lorsque les écrans de détail apparaîtront, la navigation sera centralisée dans un `AppNavHost`. Les contrôleurs pourront émettre une destination métier ou un effet ; seule la `Route` exécutera la navigation Android.

```text
Root
├── Onboarding
└── Main
    ├── Dashboard
    ├── Exercises
    ├── Routines
    │   └── RoutineEditor/{routineId}
    ├── Workout
    │   └── ActiveWorkout/{workoutId}
    ├── History
    ├── Statistics
    │   └── ExerciseProgress/{exerciseId}
    └── Settings
```

La barre inférieure actuelle expose Accueil, Routines, Séance, Statistiques et Outils. Le profil et les réglages peuvent rester sous Outils jusqu'à justifier une destination dédiée. Une séance active reste accessible en revenant dans l'onglet Séance ; une bannière persistante pourra compléter cette navigation.

L'onglet Routines enregistre un modèle réutilisable : nom, notes et exercices ordonnés. L'onglet Séance lance une copie indépendante de ce plan, avec de nouveaux identifiants, une heure de début et des séries initialement vides. Chaque série validée est persistée ; la finalisation enregistre l'heure de fin et les notes. Le bilan et l'historique calculent le tonnage depuis les séries enregistrées, sans modifier la routine d'origine.

Une routine peut être modifiée avec le même identifiant ou supprimée. La clé étrangère de `workouts` passe alors à `NULL` et conserve les séances passées. Chaque séance terminée peut aussi être supprimée séparément de l'historique. Au lancement d'une nouvelle séance, les séries de la dernière séance terminée de cette routine sont affichées comme références et proposées successivement dans le formulaire ; aucune n'est copiée dans la nouvelle séance avant validation. Le type de série est déjà stocké dans `workout_sets`, sans migration du schéma.

Le choix d'un exercice passe par une liste ouverte à la demande, depuis l'éditeur de routine ou la séance active. Ajouter un exercice à une séance change seulement cette séance. Remplacer un exercice de séance est autorisé tant qu'aucune série n'y a été validée, afin de ne jamais rattacher des séries passées au mauvais mouvement.

Lorsqu'une séance se termine avec une liste d'exercices différente de sa routine liée, le contrôleur propose de remplacer le plan de la routine pour les prochains lancements. La séance réelle est finalisée avant l'écriture de la routine ; les entraînements passés restent des instantanés. Si la seconde écriture échoue, le bilan reste disponible et l'utilisateur peut relancer seulement la mise à jour du plan. Le volume hebdomadaire lit les exercices et les séries des séances réelles, indépendamment du plan réutilisable.

Les identifiants passés dans les routes sont sauvegardés dans `SavedStateHandle`. Les objets complets ne transitent pas dans les arguments de navigation : ils sont relus depuis le Model.

Les liens profonds d'import de routine sont différés jusqu'à la définition du format d'échange et de la politique de sécurité.

## 5. Modèle de données

### 5.1 Principes

- Room est la source de vérité locale.
- Les identifiants sont générés localement, idéalement sous forme d'UUID, afin de rester créables hors ligne.
- Les dates persistantes sont en UTC ; le fuseau de l'utilisateur est appliqué pour les semaines et les graphiques.
- Les masses sont stockées dans une unité canonique entière, par exemple en grammes, puis converties pour l'affichage.
- Les métriques dérivées comme le tonnage total ne sont pas dupliquées en base tant qu'une mesure de performance ne le justifie pas.
- Un exercice déjà utilisé est archivé plutôt que supprimé physiquement.
- Chaque table de jonction et chaque clé étrangère fréquemment interrogée possède un index.
- Les migrations Room sont écrites et testées dès la première version publiée.

### 5.2 Schéma logique

| Table | Champs structurants | Rôle |
| --- | --- | --- |
| `athlete_profile` | `id`, préférences biométriques facultatives | Profil local ; ne doit pas bloquer l'onboarding |
| `exercise` | `id`, `name`, `equipment`, `isCustom`, `archivedAt` | Catalogue d'exercices |
| `muscle_group` | `id`, clé stable, libellé localisable | Référentiel musculaire |
| `exercise_muscle_contribution` | `exerciseId`, `muscleId`, `contributionBasisPoints` | Relation N-N et coefficient explicite |
| `exercise_settings` | `exerciseId`, repos, mode d'effort, note persistante | Surcharge des préférences globales |
| `routine` | `id`, `name`, `notes`, `createdAt`, `updatedAt` | Modèle réutilisable |
| `routine_exercise` | `id`, `routineId`, `exerciseId`, `orderIndex`, `supersetGroupId`, `notes` | Même exercice autorisé plusieurs fois dans une routine |
| `routine_set_template` | `id`, `routineExerciseId`, cible de charge/répétitions/effort | Prescription facultative |
| `workout` | `id`, `routineId?`, `status`, `startedAt`, `endedAt?`, `notes` | Agrégat de séance réelle |
| `workout_exercise` | `id`, `workoutId`, `exerciseId`, `orderIndex`, `supersetGroupId`, instantané du nom/note | Exercice tel qu'exécuté |
| `workout_set` | `id`, `workoutExerciseId`, `orderIndex`, `type`, `weightGrams?`, `reps?`, `rpeTenths?`, `rir?`, `completedAt?` | Mesure atomique de l'effort |
| `workout_exercise_muscle_snapshot` | `workoutExerciseId`, `muscleId`, `contributionBasisPoints` | Empêche une modification du catalogue de réécrire l'historique |
| `volume_landmark` | `profileId`, `muscleId`, MV/MEV/MAV/MRV, source et version | Repères configurables, ajoutés en phase analytique |
| `recovery_check_in` | date, muscle, score, note | Donnée subjective séparée de la séance |
| `plate_inventory` | unité, masse, quantité disponible | Inventaire utilisé par le calculateur |
| `outbox_operation` | type, agrégat, version, état, tentatives | Ajouté uniquement avec la synchronisation cloud |

`contributionBasisPoints` utilise une échelle entière documentée : `10 000` pour une série complète
et `5 000` pour une demi-série. Ce choix évite d'accumuler des erreurs de flottants et permet
ultérieurement d'autres coefficients.

Les contributions sont attachées à l'exercice, et non déduites uniquement du nom du muscle principal. Ainsi, un mouvement de tirage sans flexion du coude peut ne comporter aucune contribution biceps sans créer d'exception conditionnelle dans le calcul.

### 5.3 Intégrité des agrégats

- `RoutineExercise` et `WorkoutExercise` ont leur propre identifiant : la même référence d'exercice peut apparaître plusieurs fois.
- `orderIndex` est unique à l'intérieur de son parent et réordonné dans une transaction.
- `supersetGroupId` n'a de sens qu'à l'intérieur d'une routine ou d'une séance.
- une séance possède un statut explicite : `DRAFT`, `ACTIVE`, `COMPLETED` ou `CANCELLED` ;
- seules les séries terminées sont prises en compte dans les statistiques publiées ;
- la finalisation d'une séance, la création de ses instantanés et une éventuelle entrée d'outbox sont atomiques.

## 6. Règles métier et cas d'usage

Cas d'usage de base :

```text
ObserveExercises              CreateRoutine
ObserveRoutine                DuplicateRoutine
StartWorkout                  ObserveActiveWorkout
AddExerciseToWorkout          LogWorkoutSet
CompleteWorkoutSet            DeleteWorkoutSet
PrefillSetFromHistory         FinishWorkout
ObserveWorkoutHistory         CalculatePlateLoading
CalculateWeeklyMuscleVolume   EvaluateVolumeLandmarks
```

### 6.1 Volume musculaire

La formule initiale est une politique métier explicite :

```text
volume(muscle, période)
  = somme des crédits musculaires
    des séries de travail terminées pendant la période
```

Les séries d'échauffement sont exclues par défaut. Le seuil éventuel de RPE/RIR à partir duquel une série devient « effective » n'est pas imposé par le schéma : il appartient à une stratégie versionnée et testable. Le résultat reste une aide descriptive, pas un diagnostic médical.

Les limites de semaine utilisent le fuseau choisi par l'utilisateur. Un changement de fuseau ne modifie pas les instants enregistrés, seulement leur regroupement pour l'affichage.

### 6.2 Repères physiologiques

MV, MEV, MAV et MRV ne sont pas des constantes universelles. Ils doivent :

- être fournis comme valeurs initiales identifiées et versionnées ;
- être modifiables par l'utilisateur ;
- conserver leur unité et leur définition visibles ;
- ne jamais augmenter automatiquement une prescription sur le seul fondement des courbatures ;
- afficher une formulation prudente et permettre la désactivation des recommandations.

### 6.3 Calculateur de disques

Le calcul prend en entrée le poids cible, le poids de la barre, l'unité et l'inventaire disponible. Un algorithme glouton n'est pas suffisant avec un inventaire limité ou des dénominations atypiques. Le contrat doit distinguer :

```text
ExactLoading
ClosestLoading(actualWeight, difference)
Impossible(reason)
```

Le calcul est pur, symétrique par côté et testé sur les microcharges et les conversions kg/lb.

## 7. Persistance et stratégie offline-first

### 7.1 Écriture locale

Chaque action critique écrit d'abord dans Room, dans une transaction courte, puis l'interface observe le nouvel état. Le réseau n'est jamais requis pour commencer une séance, valider une série, terminer une séance ou consulter l'historique local.

Room stocke les données relationnelles. DataStore stocke les préférences non relationnelles : unité, thème, fin d'onboarding, mode RPE/RIR et durée de repos par défaut.

### 7.2 Synchronisation différée

La synchronisation n'est pas implémentée tant qu'un backend et une politique de conflit ne sont pas choisis. Lorsqu'elle le sera :

- une écriture locale et son événement d'outbox seront enregistrés dans la même transaction ;
- WorkManager videra l'outbox avec des contraintes réseau appropriées ;
- les requêtes seront idempotentes grâce à l'identifiant local de l'opération ;
- suppressions logiques, révisions et dates de modification seront explicites ;
- les conflits seront résolus au niveau des agrégats `Workout` et `Routine`, pas par un « dernier écrivain gagne » silencieux sur chaque ligne ;
- l'interface continuera de lire Room, jamais directement la réponse distante.

WorkManager est réservé à ce travail différable. Il n'est pas utilisé pour un chronomètre de repos de quelques minutes.

## 8. Chronomètre et travail en arrière-plan

Le chronomètre persiste une échéance, pas une valeur décrémentée chaque seconde.

- en premier plan, le temps restant est dérivé d'une horloge monotone ;
- une échéance absolue permet de restaurer l'état après destruction du processus ;
- la View anime l'affichage, sans effectuer d'écriture Room à chaque seconde ;
- AlarmManager peut programmer l'alerte si le niveau de précision, les permissions et les règles de distribution Android le permettent ;
- sans autorisation d'alarme exacte, l'application dégrade clairement vers une alerte non exacte ou un timer visible ;
- un Foreground Service n'est démarré que pendant une activité utilisateur réelle qui le justifie et avec une notification conforme ;
- les changements d'heure, le redémarrage du téléphone, les restrictions constructeur et le refus des notifications sont couverts par des scénarios de test.

Les APIs Android de fond évoluent. Leur choix doit être revalidé au moment de l'implémentation et avant chaque hausse de `targetSdk`.

## 9. Injection de dépendances

L'injection se fait d'abord explicitement par constructeurs. Un `AppContainer` créé au niveau Application possède les dépendances de portée application, et une factory fournit les Controllers ayant des paramètres. Cette solution garde le démarrage lisible et ne nécessite pas d'ajouter Hilt au build avant que son coût soit justifié.

- une instance Room et ses DAO ont une portée application ;
- les repositories sont construits une fois à partir des DAO et exposés par leur contrat ;
- les contrôleurs restent créés par la mécanique `ViewModel` et une factory, donc liés au cycle de vie de l'écran ;
- les dispatchers sont injectés via une petite interface ou un objet de fournisseurs ;
- l'horloge, le générateur d'identifiants et le planificateur de timer sont des interfaces injectables ;
- Health Connect, cloud et Wear OS dépendront de ports optionnels, sans contaminer le Model principal.

Aucun Controller ne doit lire un conteneur global : toutes ses dépendances sont visibles dans son constructeur. Hilt pourra remplacer la composition manuelle lorsque le nombre de factories, les intégrations ou les scopes rendent celle-ci difficile à maintenir. Cette migration ne changera pas les contrats MVC.

Aucune classe métier ne doit appeler directement `Dispatchers.IO`, `Instant.now()` ou `UUID.randomUUID()` si ce comportement empêche un test déterministe.

## 10. Stratégie de tests

### Tests unitaires JVM

- règles de contribution musculaire et exclusion des échauffements ;
- limites de période et fuseaux horaires ;
- RPE/RIR, arrondis et validation des séries ;
- calculateur de disques et conversions d'unité ;
- contrôleurs avec faux repositories, fausse horloge et coroutines de test ;
- séquences `Action -> UiState` et `Action -> Effect`.

### Tests instrumentés

- DAO et relations Room avec base en mémoire ;
- transactions de finalisation et de réordonnancement ;
- migrations Room avec `MigrationTestHelper` ;
- restauration d'une séance active après recréation ;
- rendu Compose, sémantique d'accessibilité et actions essentielles ;
- navigation vers une séance ou un exercice identifié.

### Tests d'intégration différés

- reprise et idempotence de l'outbox ;
- comportement sans permission de notification ou d'alarme exacte ;
- contrats Health Connect ;
- synchronisation téléphone/montre et résolution des conflits.

## 11. Décisions volontairement différées

- fournisseur d'identité et backend cloud ;
- format public de partage ou d'import de routine ;
- réseau social, modération et visibilité des profils ;
- Health Connect et estimation calorique ;
- application Wear OS et protocole de synchronisation ;
- bibliothèque définitive de graphiques ;
- moteur de recommandation ou d'IA.

Ces décisions sont coûteuses, sensibles à la vie privée ou dépendantes d'APIs externes. Elles ne doivent pas retarder le parcours local « créer une routine, démarrer une séance, enregistrer une série, retrouver son historique ».
