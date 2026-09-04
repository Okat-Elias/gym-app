# Feuille de route technique et produit

Cette feuille de route transforme le cahier des charges du PDF en livraisons vérifiables. Elle privilégie un parcours local fiable avant les analyses avancées et les intégrations externes.

## Principes de planification

- Chaque phase doit produire un parcours utilisable, démontrable et testé.
- La base Room reste la source de vérité à toutes les phases.
- Une fonctionnalité n'entre pas dans la phase suivante uniquement parce qu'elle est citée dans le PDF.
- Les données physiologiques sont configurables et présentées comme des repères, pas comme des prescriptions médicales.
- Le projet reste mono-module tant que les seuils définis dans `ARCHITECTURE.md` ne sont pas atteints.
- Une fonctionnalité différée conserve au plus un contrat métier minimal ; aucune infrastructure spéculative n'est ajoutée.

## P0 — Fondations et tranche verticale hors ligne

### Objectif

Prouver le flux MVC et permettre d'enregistrer une première séance locale de bout en bout.

### Portée

- application Compose et thème Material 3 minimal ;
- navigation racine et écrans temporaires clairement identifiés ;
- packages racines actuels `model`, `controller` et `view`, complétés progressivement par `di` ;
- contrat MVC minimal `UiState`, `Action`, `Controller` et `Screen` sur une première fonctionnalité, puis `Effect` seulement lorsqu'un événement ponctuel le nécessite ;
- modèles métier minimaux : exercice, cible musculaire, séance, exercice de séance et série ;
- base Room version 1, DAO, relations, mappers et repositories ;
- injection par constructeurs, conteneur applicatif explicite, dispatchers, horloge et générateur d'identifiants injectables ;
- catalogue local initial réduit ;
- création ou reprise d'une séance active ;
- ajout d'un exercice et validation d'une série avec poids et répétitions ;
- historique minimal de la séance terminée ;
- tests unitaires du domaine et tests Room du parcours critique.

### Critères de sortie

- une séance peut être commencée, alimentée, fermée puis relue en mode avion ;
- la fermeture ou rotation de l'activité ne perd aucune série validée ;
- aucune View n'importe un DAO ou un repository ;
- aucun Controller n'importe un DAO ;
- les charges ne sont pas stockées en `Float` ou `Double` ;
- les relations Room et la première migration de test sont en place ;
- les tests du parcours critique passent de façon déterministe.

### Hors périmètre

Synchronisation, compte, statistiques avancées, recommandations physiologiques, Health Connect et Wear OS.

## P1 — Journal d'entraînement utilisable en salle

### Objectif

Réduire la friction de saisie et couvrir un vrai entraînement structuré.

### Portée

- catalogue consultable, recherche, création et archivage d'exercices personnalisés ;
- création, modification, duplication et réordonnancement de routines ;
- prescriptions de séries facultatives ;
- supersets par identifiant de groupe local ;
- notes persistantes d'exercice et notes propres à une séance ;
- types de série : échauffement, travail, drop-set et échec ;
- RPE ou RIR facultatif, globalement et par exercice ;
- saisie numérique rapide et zones tactiles accessibles ;
- préremplissage depuis la série précédente puis depuis le dernier entraînement comparable ;
- suppression et annulation avec possibilité de récupération lorsque pertinent ;
- chronomètre restaurable et notification avec dégradation explicite si les permissions manquent ;
- historique par date et par exercice ;
- calculateur de disques tenant compte de l'inventaire ;
- export CSV local minimal si le modèle est stabilisé.

### Critères de sortie

- une routine avec superset peut être créée puis exécutée sans réseau ;
- les actions répétées ne dupliquent pas silencieusement une série ;
- une séance interrompue par destruction du processus est récupérable ;
- le timer affiche une valeur cohérente après arrière-plan et retour dans l'application ;
- le refus des notifications ou des alarmes exactes ne bloque jamais la séance ;
- le calculateur indique clairement un chargement exact, approché ou impossible ;
- le parcours principal dispose de tests Compose et d'accessibilité.

### Hors périmètre

Cloud, flux social, Health Connect, Wear OS et recommandation automatique de programme.

## P2 — Analytique explicable et configurable

### Objectif

Transformer l'historique en indicateurs compréhensibles sans présenter les heuristiques comme des vérités universelles.

### Portée

- contributions musculaires N-N avec crédits entiers ;
- instantané des contributions lors de la séance pour préserver l'historique ;
- volume hebdomadaire par muscle ;
- tonnage, évolution de charge, répétitions et meilleurs résultats ;
- filtres semaine, mois, année et gestion correcte du fuseau horaire ;
- graphiques Compose accessibles avec équivalent textuel ;
- repères MV, MEV, MAV et MRV provenant d'un jeu de valeurs versionné ;
- modification, désactivation et remise à zéro des repères par l'utilisateur ;
- contrôles subjectifs de récupération séparés des données objectives ;
- explication visible de chaque formule et des données incluses ;
- export enrichi des données et validation du format de sauvegarde.

### Critères de sortie

- un utilisateur peut remonter d'un total hebdomadaire jusqu'aux séries qui le composent ;
- modifier le catalogue d'un exercice ne modifie pas rétroactivement les séances finalisées ;
- les échauffements et séries non terminées ne gonflent pas les statistiques par défaut ;
- les limites de semaine sont testées autour des changements de fuseau et d'heure ;
- les repères physiologiques indiquent leur source, version et caractère personnalisable ;
- aucune recommandation d'augmentation ou de décharge n'est déclenchée sur un seul signal subjectif.

### Hors périmètre

Coaching médical, diagnostic, programme généré par IA et partage public automatique.

## P3 — Intégrations et synchronisation optionnelles

### Objectif

Ajouter des services externes sans dégrader l'expérience hors ligne ni la propriété des données.

P3 doit être découpée en lots indépendants. Le cloud n'implique pas automatiquement Health Connect, Wear OS ou le social.

### P3a — Sauvegarde et synchronisation cloud

- choisir explicitement le backend, l'authentification, la région de stockage et la politique de suppression ;
- ajouter l'outbox transactionnelle, les tombstones et les révisions d'agrégat ;
- définir les conflits entre deux appareils avant d'activer l'écriture distante ;
- chiffrer les échanges et minimiser les données collectées ;
- offrir export et suppression du compte ;
- tester les reprises après coupure, doublons, changements d'ordre et suppressions concurrentes.

Critère de sortie : toute opération locale reste utilisable hors ligne et converge après reconnexion sans perte silencieuse.

### P3b — Health Connect

- définir les données réellement utiles et demander uniquement les permissions correspondantes ;
- écrire une séance terminée par lots et de façon idempotente ;
- importer les données de fréquence cardiaque uniquement avec consentement explicite ;
- éviter toute estimation calorique trompeuse ;
- prévoir révocation, absence de Health Connect et données partielles.

Critère de sortie : le refus ou le retrait des permissions ne casse aucune fonction du journal local.

### P3c — Wear OS

- créer un module et un artefact Wear distincts ;
- limiter l'interface aux actions essentielles : série suivante, répétitions, validation et repos ;
- permettre une saisie temporairement autonome sur la montre ;
- définir des identifiants idempotents et une résolution de conflit téléphone/montre ;
- mesurer batterie, reprise après rupture Bluetooth et lisibilité sur petits écrans.

Critère de sortie : une rupture de connexion ne perd ni ne duplique une série.

### P3d — Partage et fonctions sociales éventuelles

- spécifier le format versionné d'une routine partageable ;
- valider et neutraliser les charges dangereuses lors d'un import ;
- définir visibilité, blocage, signalement, modération et suppression ;
- n'ajouter flux, likes ou commentaires qu'avec une capacité opérationnelle de modération.

Cette sous-phase peut être abandonnée sans conséquence sur les autres objectifs.

## Ambiguïtés et risques à résoudre

| Sujet | Risque | Décision ou garde-fou |
| --- | --- | --- |
| MVC avec Compose | Renommer un ViewModel en Controller tout en conservant de la logique métier dans la présentation | Le Controller orchestre ; les formules et transactions résident dans des cas d'usage |
| « Série effective » | Le PDF ne définit pas précisément types, seuil RPE/RIR et cas particuliers | Politique versionnée, visible, testée et configurable ; échauffement exclu par défaut |
| Coefficients musculaires | Le couple primaire `1,0` / secondaire `0,5` simplifie fortement la physiologie | Relation N-N éditable, coefficients entiers et formulation non médicale |
| MV/MEV/MAV/MRV | Valeurs variables selon individu, source et contexte | Valeurs initiales sourcées et versionnées, modifiables et désactivables |
| Courbatures/DOMS | Un score subjectif peut conduire à une recommandation inadaptée | Ne jamais automatiser une prescription sur ce seul signal |
| Historique | Changer les cibles d'un exercice peut réécrire les anciennes statistiques | Instantané des contributions à la finalisation de la séance |
| kg/lb | Accumulation d'erreurs et valeurs impossibles sur les microcharges | Stockage canonique entier et arrondi explicite aux frontières |
| Calculateur de disques | L'algorithme glouton échoue avec inventaire limité | Recherche bornée et résultat exact/approché/impossible |
| Timer Android | Permissions, Doze, restrictions constructeur et changement d'heure | Échéance persistée, horloge monotone en processus, stratégie de repli testée |
| Alarmes exactes | Disponibilité et politiques de distribution variables | Revalidation avant implémentation ; aucune dépendance du journal à cette permission |
| Cloud | Backend et règle de conflit non définis | Différer ; introduire l'outbox seulement avec un protocole concret |
| Health Connect | Données sensibles, permissions et enregistrements partiels | Port optionnel, consentement granulaire et journal local indépendant |
| Wear OS | Deux sources d'écriture et ruptures de connexion | Artefact distinct, UUID locaux, opérations idempotentes et tests de convergence |
| Social | Modération, confidentialité et coût opérationnel sous-estimés | Sous-phase indépendante, non requise pour la valeur principale |
| Catalogue | Droits, traductions et qualité anatomique des données | Petit catalogue interne validé ; provenance et version explicites |
| Temps et semaines | Fuseaux, heure d'été et voyage faussent les agrégations | Instants UTC, zone explicite au regroupement et tests de frontières |
| Évolution Room | Une première version rapide peut rendre les migrations coûteuses | Schéma normalisé, migrations conservées et testées dès P0 |

## Ordre de travail immédiat recommandé

1. Figer les modèles P0 et les unités de stockage.
2. Créer Room, les DAO et les mappers avec tests de relations.
3. Implémenter une tranche verticale `ActiveWorkout` en MVC.
4. Sauvegarder chaque série validée et restaurer la séance active.
5. Ajouter la finalisation et l'historique minimal.
6. Valider le parcours complet en mode avion avant d'élargir la portée.

Le succès de P0 ne se mesure pas au nombre d'écrans créés, mais à l'absence de perte de données sur ce parcours critique.
