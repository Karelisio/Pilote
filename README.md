# Pilote

Pilote est une appli Android native (Kotlin, Jetpack Compose, Material 3 Expressive). Elle bascule automatiquement les réglages du téléphone (son, Ne pas déranger, luminosité, Wi-Fi…) selon le lieu, l'heure, le Bluetooth, le Wi-Fi, la charge, l'appli ouverte ou une action manuelle.

Tout reste sur le téléphone (Room). Pas de compte, pas de serveur, pas de Play Store.

- Package : `com.karelisio.pilote`
- minSdk 31 (Android 12), targetSdk 36, compileSdk 37
- APK signé construit par GitHub Actions, mises à jour intégrées à l'appli

---

## Étapes manuelles (à faire une seule fois)

### 1. Keystore et secrets GitHub

Pilote doit être signée avec **le même keystore que Status**, pour deux raisons :
- la permission de signature partagée entre les deux applis ;
- les mises à jour : Android refuse d'installer un APK signé avec une autre clé.

1. Encodez le keystore existant de Status en base64 :
   ```bash
   base64 -w0 status-release.jks > keystore.b64      # Linux
   base64 -i status-release.jks -o keystore.b64      # macOS
   ```
2. Dans **GitHub → Settings → Secrets and variables → Actions → Repository secrets**, créez :

   | Secret | Contenu |
   |---|---|
   | `KEYSTORE_BASE64` | contenu de `keystore.b64` |
   | `KEYSTORE_PASSWORD` | mot de passe du keystore |
   | `KEY_ALIAS` | alias de la clé |
   | `KEY_PASSWORD` | mot de passe de la clé |

3. Supprimez `keystore.b64`. Le `.gitignore` exclut déjà `*.jks`, `*.keystore` et `*.b64` : ne commitez jamais de clé.

### 2. Publier une version

La version est dans le fichier `VERSION` (ex. `1.0.0`). Pour publier : incrémentez `VERSION`, puis poussez sur `main`.

Le workflow `Release` se lance à chaque push sur `main` :
1. si le tag `vX.Y.Z` existe déjà, il ne fait rien ;
2. sinon il lance les tests ;
3. construit l'APK signé (`versionName` = `VERSION`, `versionCode` = `github.run_number`) ;
4. vérifie la signature ;
5. publie la **GitHub Release** `vX.Y.Z` avec `pilote-X.Y.Z.apk` et le changelog (commits depuis la release précédente).

Le workflow `CI` compile et teste les autres branches.

> **Dépôt privé ?** Le dépôt est public, donc les APK sont publiés ici. S'il devient privé, créez un dépôt **public** dédié (ex. `Karelisio/Pilote-releases`), puis ajoutez :
> - la variable `RELEASES_REPO` = `Karelisio/Pilote-releases` ;
> - le secret `RELEASES_TOKEN` : jeton avec le droit *Contents: write* sur ce dépôt.
>
> L'appli interroge alors ce dépôt (valeur intégrée au build via `UPDATE_REPO`).

### 3. Première installation

1. Téléchargez `pilote-X.Y.Z.apk` depuis la page **Releases**.
2. Autorisez l'installation d'applis inconnues pour votre navigateur ou gestionnaire de fichiers, puis installez.
3. Suivez l'onboarding : chaque autorisation a sa page.

Les versions suivantes s'installent depuis l'appli. Au lancement, au plus toutes les 6 h, Pilote interroge `releases/latest`. Si une version plus récente existe, elle affiche une feuille avec le changelog, télécharge l'APK et l'installe via PackageInstaller.

### 4. Autorisations (onboarding, ou Réglages → Autorisations)

| Autorisation | Sert à |
|---|---|
| Notifications | notification « Pilote actif », notifications des profils, MAJ |
| Localisation précise | géorepérage, nom du Wi-Fi |
| Localisation en arrière-plan (« Toujours autoriser ») | géorepérage appli fermée |
| Appareils à proximité | voiture, écouteurs Bluetooth |
| Accès Ne pas déranger | Ne pas déranger, mode silencieux |
| Modifier les paramètres système | luminosité, veille, rotation |
| Alarmes exactes | plages horaires à la minute |
| Données d'utilisation | déclencheur « Appli ouverte » |
| Pas d'optimisation de batterie | **obligatoire sur Honor** |
| Superposition (optionnel) | ouvrir une appli (ex. Rushio) automatiquement ; sinon une notification le propose |
| Shizuku / Shevery (optionnel) | Wi-Fi, Bluetooth, données, mode sombre, DNS privé |
| Installer des applis (optionnel) | mises à jour intégrées |

Les autorisations qui passent par les réglages système sont revérifiées à chaque retour dans l'appli.

### 5. Honor / MagicOS (indispensable)

Réglages → **Batterie et Honor** :
1. **Exclure Pilote de l'optimisation de batterie.**
2. **Lancement d'applis** : trouvez Pilote, désactivez « Gérer automatiquement », puis activez les 3 interrupteurs (lancement automatique, lancement secondaire, exécution en arrière-plan).
3. Dans les applis récentes, **verrouillez Pilote** (cadenas).

### 6. Accès avancé (Shizuku, Shevery ou ADB)

- **Shizuku officiel, Shevery (`com.hamondev.shevery`) ou un fork** : démarrez le service, puis touchez « Autoriser Pilote » dans Réglages → Accès avancé. Pilote détecte le service uniquement par le binder (`Shizuku.pingBinder()` et écouteurs de binder), jamais par un nom de paquet. Commandes utilisées :
  - `cmd wifi` / `svc wifi`
  - `cmd bluetooth_manager` / `svc bluetooth`
  - `svc data`
  - `cmd uimode night`
  - `settings put global private_dns_*`
- **Sans Shizuku** : accordez `WRITE_SECURE_SETTINGS` une fois depuis un ordinateur. Pilote applique alors directement le DNS privé :
  ```bash
  adb shell pm grant com.karelisio.pilote android.permission.WRITE_SECURE_SETTINGS
  ```
  La commande est aussi copiable depuis l'écran Accès avancé.

Les actions indisponibles apparaissent grisées, avec la raison.

### 7. Intégration Status (à implémenter côté Status)

Pilote parle à Status (`com.karelisio.status`) ainsi :

1. **Permission de signature**, déclarée à l'identique dans les deux manifestes (Pilote la déclare déjà) :
   ```xml
   <permission
       android:name="com.karelisio.status.permission.APPLY_PRESET"
       android:protectionLevel="signature" />
   ```
2. **Récepteur** dans Status, protégé par cette permission :
   ```xml
   <receiver
       android:name=".ApplyPresetReceiver"
       android:exported="true"
       android:permission="com.karelisio.status.permission.APPLY_PRESET">
       <intent-filter>
           <action android:name="com.karelisio.status.action.APPLY_PRESET" />
       </intent-filter>
   </receiver>
   <uses-permission android:name="com.karelisio.status.permission.APPLY_PRESET" />
   ```
   Pilote envoie un broadcast explicite (`setPackage("com.karelisio.status")`) avec l'action `com.karelisio.status.action.APPLY_PRESET` et l'extra `"preset"` (String). Le broadcast est restreint aux destinataires qui détiennent la permission.
3. **ContentProvider** pour lister les préréglages :
   - autorité : `com.karelisio.status.presets` ;
   - URI : `content://com.karelisio.status.presets/presets` ;
   - colonnes : `id` (ou `_id`) et `name` ;
   - à protéger par `android:readPermission="com.karelisio.status.permission.APPLY_PRESET"`.

Si Status n'est pas installée, l'action « Préréglage Status » est masquée.

### 8. Rushio

Le modèle **Voiture** contient l'action « Ouvrir Rushio ». Le paquet par défaut est `com.karelisio.rushio`, modifiable dans Réglages → Intégrations.

### 9. Tuiles et widget

- **Tuiles** : modifiez les réglages rapides et ajoutez la tuile **Pilote** (pause globale) et les tuiles **Profil manuel 1 à 5**. Chaque tuile prend le nom du profil manuel correspondant, par ordre de priorité.
- **Widget** : appui long sur l'écran d'accueil → Widgets → Pilote. Il affiche le profil actif et un bouton par profil manuel, et se redimensionne.

---

## Fonctionnement

- **Profils** = déclencheurs (ET/OU) + conditions (ET/OU) + actions. Chaque déclencheur peut être inversé (ex. « chargeur débranché »).
- **Priorités** : pour chaque réglage, le profil actif le plus prioritaire l'emporte. Quand il se termine, le profil précédent reprend la main.
- **Restauration** : avant qu'un profil prenne le contrôle d'un réglage, Pilote mémorise la valeur (snapshot). Quand plus aucun profil ne contrôle ce réglage, la valeur est restaurée.
- **Anti-rebond** : délai avant de quitter un profil. Par exemple, avec 10 s, le profil Voiture survit à une coupure brève du Bluetooth.
- **Mode test** : appui long sur un profil (ou bouton fiole dans l'éditeur) pour le simuler 30 s, puis restaurer. L'éditeur montre en direct quels déclencheurs sont remplis.
- **Historique** : chaque activation, désactivation, test ou erreur, avec la raison.
- **Pause globale** : depuis l'accueil, la tuile ou la notification.
- **Export / import JSON** : Réglages → Profils.
- **Fond** : service de premier plan léger (`specialUse`) avec une notification discrète. Il redémarre au boot et réenregistre géorepérages et alarmes.

## Build local

```bash
# JDK 21 et Android SDK (platforms;android-37.0, build-tools;37.0.0)
./gradlew testDebugUnitTest assembleDebug
```

Sans les variables `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`, l'APK release est signé avec la clé de debug. Le build debug s'installe à côté (`com.karelisio.pilote.debug`).

## Pile technique

- Kotlin 2.4, AGP 9.5, Gradle 9.8
- Compose BOM 2026.09 et Material 3 1.5 (Expressive : `MaterialShapes`, `MotionScheme.expressive()`, barres flexibles, indicateurs ondulés)
- Room, DataStore, Navigation Compose (routes typées, transitions à élément partagé, retour prédictif)
- Glance (widget), Quick Settings Tiles
- Play Services Location (géorepérage), MapLibre + tuiles OpenStreetMap
- Shizuku API 13 (`api` + `provider`), service utilisateur AIDL
