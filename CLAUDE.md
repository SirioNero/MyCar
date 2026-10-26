# CLAUDE.md — myCar

myCar è un fork **indipendente** di [Gearslip](https://github.com/twinksoftware/gearslip)
(AGPL-3.0, © Twink Software LLC e contributori): client Android Auto lato telefono, alternativo
all'app Android Auto di Google. myCar non integra più gli aggiornamenti di Gearslip: si installa
e funziona da sola. Obiettivi: rinomina in myCar, restyling stile "infotainment iOS" (senza
asset Apple), correzione bug, compatibilità 100% F-Droid.

Rispondi sempre in **italiano**.

## Regole di lavoro

- Un branch per compito: `rename/mycar`, `design/restyle`, `fix/<bug>` (es. `fix/calls`).
- Commit piccoli e descrittivi, uno per modifica logica.
- Non toccare logica estranea al compito; niente refactoring non richiesti.
- Non riformattare righe che non si stanno cambiando; codice nuovo in file nuovi quando possibile.
- Licenza AGPL-3.0: mantenere `LICENSE`, il copyright di Twink Software LLC e l'attribuzione a
  Gearslip (README, schermata Informazioni); il sorgente di ogni versione distribuita è pubblico.
- Dopo ogni modifica: `./gradlew assembleDebug` (e `./gradlew test` se si tocca logica). Nell'ambiente
  cloud di Claude la rete non raggiunge `dl.google.com`: la verifica si fa con il workflow **Build**
  di GitHub Actions (`.github/workflows/build.yml`), che parte a ogni push e allega l'APK di debug.
- Chiedere conferma prima di aggiungere dipendenze (anche font/asset con licenza propria).
- Niente dipendenze Google Play Services / proprietarie: il fork deve restare compatibile F-Droid.
- Tenere aggiornato questo file.

## Build

- Gradle 9.3.1 (wrapper), AGP 9.1.1, Kotlin 2.4.0, compileSdk/targetSdk 37, minSdk 31, Java 21.
- Proprietà Gradle: `-PmyCarVersionName=...`, `myCarStatsUrl` (vuoto: nessuna statistica).
- `gradle/gradle-daemon-jvm.properties` chiede un JDK 21 **JetBrains** per il daemon: se non è
  installato, Gradle lo scarica da `api.foojay.io`.
- Servono Android SDK (`ANDROID_HOME` o `local.properties`) e accesso a `dl.google.com`
  (repository Maven Google).
- Output: `build/myCar-debug.apk` (task `renameApk`), cartella build unica alla radice.
- Firma release opzionale via `keystore.properties` (gitignored).
- Chiave di firma di myCar: alias `mycar`, PKCS12, certificato SHA-256
  `E4:77:A4:79:4B:98:49:27:36:0E:9A:E4:A3:1E:87:CB:08:A2:6C:54:E0:E2:7E:43:34:11:6D:1C:7C:DC:CC:31`.
  Sta solo nei secret di GitHub (`KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS`,
  `KEYSTORE_KEY_PASSWORD`) e nel backup dell'utente; mai nel repository.
- Icona: logo in `docs/icon/mycar-logo.jpg`; `python3 docs/icon/make_icons.py docs/icon/mycar-logo.jpg .`
  rigenera i PNG `mipmap-*/ic_launcher_foreground.png` (main: arco blu, debug: arco ambra) e
  `docs/icon/mycar-icon.png`. La sagoma a tema è il vettoriale `drawable/ic_launcher_monochrome.xml`.
- Il debug ha `applicationIdSuffix = ".dev"`. Nel debug esiste `CarPreviewActivity`, che
  renderizza la UI auto in PNG senza auto (utile per verificare il restyling).
- Anteprima in CI: il workflow **Car preview** (`.github/workflows/car-preview.yml` +
  `.github/car-preview.sh`) parte a ogni push su `design/**`, avvia un emulatore, lancia
  `CarPreviewActivity` a 800×480 e 1920×720 (Home, griglia app, dashboard) e pubblica i PNG sul
  branch `ci/car-preview` (`git fetch origin ci/car-preview`): gli artifact di Actions non sono
  scaricabili dalla sessione cloud.

## Rilasci

- Versione in `app/build.gradle.kts` (`versionCode`, `versionName` di default), novità in
  `Changelog.kt` (schermata "Novità" sull'auto) e in `.github/release-notes.md` (testo del rilascio).
- Il workflow **Release** parte dai tag `v*`, firma l'APK con i secret e lo allega al rilascio
  (non pre-release, così Obtainium lo trova).
- Dalla sessione cloud di Claude il push dei tag è rifiutato (403): il rilascio lo crea l'utente su
  GitHub (Releases → nuovo tag `v<versionName>` → branch di destinazione → Publish).

## Architettura

Modulo unico `:app` (cartella `app/`), package `io.github.sirionero.mycar`. UI **interamente Jetpack Compose**
(XML solo per il tema della finestra).

| Package | Ruolo |
|---|---|
| radice | Protocollo AA: `MyCarRunner` (sessione, canali, video/audio), `Framing`, `Protobuf`, `PhoneTls`, `ServiceDiscovery`, `CertProvider` / `CertDownloader`, `VideoSource` (encoder), `ScreenProjector` (VirtualDisplay + `Presentation` con Compose → encoder → auto), `MyCarActivity` (possiede la sessione USB/wireless), `MyCarLog` |
| `car/` | **UI mostrata sull'auto**: `CarUi` (root: tema, navigazione, overlay, transizioni), `CarDock` (dock a sinistra), `CarHomeScreen` (mappa + player), `CarLauncher` (griglia app a pagine), `MediaScreen`, `PhoneDialerScreen`, `CallOverlay`, `CarDashboardScreen`, `CarSettingsScreen`, `CarNavigator` (stack schermate `CarScreen`), template Car App Library (`Template*`) |
| `car/theme/` | Design system attuale dei controlli: `GsTheme` / `ControlLook` (temi Flat/Tactile/Keycap), `GsControls` (`GsButton`, `GsIconButton`, `gsColors`), `GsSeekBar`, `GsSwitch` |
| `ui/` | **UI sul telefono**: onboarding (`IntroScreen`, `SetupGuideScreen`), `HomeScreen`, `SettingsScreen`, `MyCarTheme` (Material You), `AboutLinks` (link, donazioni, avvisi di licenza) |
| `host/` | Host Car App Library per app di terze parti (`CarAppConnection`, `KnownApps`) |
| `audio/` | Audio verso l'auto (`AudioLink`, cattura `AudioCaptureService`, `CarMic`) |
| `call/` | Chiamate: `CarCalls` (stato da `TelephonyCallback` + numero dal broadcast `PHONE_STATE`; risponde/rifiuta/chiude con `TelecomManager`, serve `ANSWER_PHONE_CALLS`), `MyCarCallScreening` (filtro facoltativo: trattiene le chiamate da numeri fuori rubrica solo con l'auto collegata; ignora quelle in uscita) |
| `media/`, `notify/`, `speech/`, `mirror/`, `wireless/`, `update/`, `stats/` | Media, notifiche, voce (sherpa-onnx), mirroring, AA wireless, auto-aggiornamento da GitHub, statistiche d'uso opt-in |

Tema/colori/font:
- UI dell'auto: `MyCarCarTheme` (in `car/design/MyCarDesign.kt`) alla radice di `CarUi`: colori
  myCar (scuri di default), tipografia Material in Inter, forme. Le schermate leggono
  `MaterialTheme.colorScheme` / `.typography` / `.shapes`, quindi seguono il tema senza modifiche.
- Controlli (`GsButton`, `GsIconButton`…): stile `GsThemes.MyCar` di default (piatto, 14dp, si
  stringe alla pressione); Tactile, Keycap e Flat restano selezionabili.
- UI del telefono (`ui/`): ancora Material You (`ui/MyCarTheme.kt`).
- Restano valori `dp` sparsi nelle schermate ereditate: il codice nuovo usa i token.

## Design system myCar (implementato in `car/design/`)

Obiettivo: layout e comportamento il più vicini possibile a un sistema infotainment in stile iOS,
**ma in modo legale**.
- Ammesso: proporzioni, griglie, dock laterale, spaziature, raggi, palette scura, animazioni,
  schemi di interazione; font **Inter** (SIL OFL 1.1) incluso come file in `res/font`;
  icone **Material Symbols Rounded** (Apache 2.0) copiate come vector drawable (niente nuove
  dipendenze Gradle); icone reali delle app installate; icone delle app integrate disegnate da noi.
- Vietato: i nomi "CarPlay"/"Apple"/"iOS" in app, icona, store, screenshot; font SF Pro e
  SF Symbols; icone, sfondi, suoni o screenshot Apple; copie delle icone delle app Apple.
- Niente font scaricabili via Google Play Services.
Tutti i token in un solo posto, `car/design/`:
- `MyCarDesign.kt`: `MyCarPalette`, `MyCarRadius`, `MyCarSpace`, `MyCarSize`, `MyCarMotion`,
  `InterFamily`, schemi colori scuro/chiaro, tipografia, forme, `MyCarCarTheme`.
- `SquircleShape.kt`: cornice delle icone (superellisse, `AppIconShape`).
- `AppIcons.kt`: `AppIcon` (icona di un'app nella cornice), `squareAppIcon` (icona adattiva
  disegnata dai livelli, senza la maschera del telefono), colori e glifi delle app integrate.
- Font: `res/font/inter_*.ttf` (OFL); icone: `res/drawable/sym_*.xml` (Material Symbols
  Rounded, Apache 2.0). Licenze in `licenses/` e `NOTICE.md`.

- **Colori** (scuro di default): sfondo `#000000`; superfici `#1C1C1E` / `#2A2A2D`;
  separatori `#3A3A3C`; testo `#FFFFFF` / secondario `#9A9AA0`; accento `#3D8BFF`;
  positivo `#32D46B`; negativo `#FF4D45`; avviso `#FFB020`. Niente gradienti sottili o blur:
  il video verso l'auto li comprime male (banding).
- **Raggi**: icone app squircle ≈ 22% del lato; card 16dp; bottoni 14dp; pill = metà altezza.
- **Spaziature**: 4 / 8 / 12 / 16 / 24 / 32dp.
- **Tipografia (Inter)**: tutta la scala Material in Inter, con le dimensioni di Material (le
  schermate ereditate sono impaginate su quelle) e spaziatura tra lettere un po' più stretta.
- **Tocco**: target minimo 64dp.
- **Movimento**: pressione 120–150ms, transizioni 200–250ms, easing decelerato; niente
  animazioni lunghe.
- **Layout**: dock laterale fisso a sinistra (`CarDock`: orologio in alto → dashboard, mappa,
  brano in riproduzione, app fissate e aperte, assistente; tasto della griglia app in basso);
  Home resta mappa + player; la griglia app (≈4×2 per pagina, a pagine, con indicatori) si apre
  dal tasto del dock; icone reali delle app installate dentro una cornice squircle.

## Decisioni prese

- Fork indipendente: niente più merge da Gearslip.
- applicationId e package Kotlin: `io.github.sirionero.mycar` (debug: `.dev`). Nome visibile: **myCar**
  (scritto esattamente così, m minuscola e C maiuscola) in app, README e documenti; i nomi nel
  codice restano `MyCar…` (es. `MyCarActivity`).
- myCar è autonoma: continua a **definire** il permesso `android.car.permission.TEMPLATE_RENDERER`
  (quindi non si installa insieme a Gearslip).
- Donazioni: solo Bitcoin, `bc1qu8q2sc5kuccjj3zpzzfpj85xn7szp73nae9xlx` (in `ui/AboutLinks.kt` e nel README).
- Bug chiamate: priorità alle chiamate **in arrivo** (branch `fix/calls`).
- F-Droid: per ora variante **senza** voce offline (sherpa-onnx).
- Auto-updater delle build GitHub: rilasci di `sirionero/mycar`; disattivato nella build F-Droid.

## Backlog

- Voce offline nella build F-Droid: compilare sherpa-onnx (+ onnxruntime) da sorgente nella
  ricetta F-Droid invece del `.so` precompilato.

## Note F-Droid

Punti da risolvere (branch dedicato): `app/src/main/jniLibs/.../libsherpa-onnx-jni.so` prebuilt,
`speech-models/*.bin` non usati, auto-updater (`REQUEST_INSTALL_PACKAGES`) da disattivare nella
build F-Droid, voce `play-services-oss-licenses` inutilizzata in `libs.versions.toml`, toolchain
JetBrains via foojay. Già fatto: nessun server di statistiche (`myCarStatsUrl` vuoto).
