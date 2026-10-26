# CLAUDE.md — MyCar

MyCar è un fork **indipendente** di [Gearslip](https://github.com/twinksoftware/gearslip)
(AGPL-3.0, © Twink Software LLC e contributori): client Android Auto lato telefono, alternativo
all'app Android Auto di Google. MyCar non integra più gli aggiornamenti di Gearslip: si installa
e funziona da sola. Obiettivi: rinomina in MyCar, restyling stile "infotainment iOS" (senza
asset Apple), correzione bug, compatibilità 100% F-Droid.

Rispondi sempre in **italiano**.

## Regole di lavoro

- Un branch per compito: `rename/mycar`, `design/restyle`, `fix/<bug>` (es. `fix/calls`).
- Commit piccoli e descrittivi, uno per modifica logica.
- Non toccare logica estranea al compito; niente refactoring non richiesti.
- Non riformattare righe che non si stanno cambiando; codice nuovo in file nuovi quando possibile.
- Licenza AGPL-3.0: mantenere `LICENSE`, il copyright di Twink Software LLC e l'attribuzione a
  Gearslip (README, schermata Informazioni); il sorgente di ogni versione distribuita è pubblico.
- Dopo ogni modifica: `./gradlew assembleDebug` (e `./gradlew test` se si tocca logica).
- Chiedere conferma prima di aggiungere dipendenze (anche font/asset con licenza propria).
- Niente dipendenze Google Play Services / proprietarie: il fork deve restare compatibile F-Droid.
- Tenere aggiornato questo file.

## Build

- Gradle 9.3.1 (wrapper), AGP 9.1.1, Kotlin 2.4.0, compileSdk/targetSdk 37, minSdk 31, Java 21.
- `gradle/gradle-daemon-jvm.properties` chiede un JDK 21 **JetBrains** per il daemon: se non è
  installato, Gradle lo scarica da `api.foojay.io`.
- Servono Android SDK (`ANDROID_HOME` o `local.properties`) e accesso a `dl.google.com`
  (repository Maven Google).
- Output: `build/Gearslip-debug.apk` (task `renameApk`), cartella build unica alla radice.
- Firma release opzionale via `keystore.properties` (gitignored).
- Il debug ha `applicationIdSuffix = ".dev"`. Nel debug esiste `CarPreviewActivity`, che
  renderizza la UI auto in PNG senza auto (utile per verificare il restyling).

## Architettura

Modulo unico `:gearslip`, package `app.seb3thehacker.gearslip`. UI **interamente Jetpack Compose**
(XML solo per il tema della finestra).

| Package | Ruolo |
|---|---|
| radice | Protocollo AA: `GearslipRunner` (sessione, canali, video/audio), `Framing`, `Protobuf`, `PhoneTls`, `ServiceDiscovery`, `CertProvider` / `CertDownloader`, `VideoSource` (encoder), `ScreenProjector` (VirtualDisplay + `Presentation` con Compose → encoder → auto), `GearslipActivity` (possiede la sessione USB/wireless) |
| `car/` | **UI mostrata sull'auto**: `CarUi` (root: tema, navigazione, overlay, nav bar in basso), `CarHomeScreen` (mappa + player), `CarLauncher` (griglia app), `MediaScreen`, `PhoneDialerScreen`, `CallOverlay`, `CarDashboardScreen`, `CarSettingsScreen`, `CarNavigator` (stack schermate `CarScreen`), template Car App Library (`Template*`) |
| `car/theme/` | Design system attuale dei controlli: `GsTheme` / `ControlLook` (temi Flat/Tactile/Keycap), `GsControls` (`GsButton`, `GsIconButton`, `gsColors`), `GsSeekBar`, `GsSwitch` |
| `ui/` | **UI sul telefono**: onboarding (`IntroScreen`, `SetupGuideScreen`), `HomeScreen`, `SettingsScreen`, `GearslipTheme` (Material You) |
| `host/` | Host Car App Library per app di terze parti (`CarAppConnection`, `KnownApps`) |
| `audio/` | Audio verso l'auto (`AudioLink`, cattura `AudioCaptureService`, `CarMic`) |
| `call/` | Chiamate: `CarCalls` (stato), `GearslipCallScreening` (CallScreeningService) |
| `media/`, `notify/`, `speech/`, `mirror/`, `wireless/`, `update/`, `stats/` | Media, notifiche, voce (sherpa-onnx), mirroring, AA wireless, auto-aggiornamento da GitHub, statistiche d'uso opt-in |

Tema/colori/font oggi:
- Colori: Material You dinamico (`dynamicDark/LightColorScheme`) in `CarUi.kt` e `ui/GearslipTheme.kt`.
- Tipografia: `MaterialTheme.typography` + `ChromeType` (in `car/TemplateParts.kt`); nessun font custom.
- Forme/spaziature: valori `dp` sparsi (~620 in `car/`, ~190 in `ui/`).
- Le schermate usano già `MaterialTheme.colorScheme` (~225 usi) e `.typography` (~165 usi) →
  sostituire tema e tipografia alla radice ristila gran parte della UI con un diff piccolo.

## Design system MyCar (PROPOSTA, da approvare)

Obiettivo: layout e comportamento il più vicini possibile a un sistema infotainment in stile iOS,
**ma in modo legale**.
- Ammesso: proporzioni, griglie, dock laterale, spaziature, raggi, palette scura, animazioni,
  schemi di interazione; font **Inter** (SIL OFL 1.1) incluso come file in `res/font`;
  icone **Material Symbols Rounded** (Apache 2.0) copiate come vector drawable (niente nuove
  dipendenze Gradle); icone reali delle app installate; icone delle app integrate disegnate da noi.
- Vietato: i nomi "CarPlay"/"Apple"/"iOS" in app, icona, store, screenshot; font SF Pro e
  SF Symbols; icone, sfondi, suoni o screenshot Apple; copie delle icone delle app Apple.
- Niente font scaricabili via Google Play Services.
Tutti i token in un solo posto (proposta: `car/design/`, file nuovi).

- **Colori** (scuro di default): sfondo `#000000`; superfici `#1C1C1E` / `#2A2A2D`;
  separatori `#3A3A3C`; testo `#FFFFFF` / secondario `#9A9AA0`; accento `#3D8BFF`;
  positivo `#32D46B`; negativo `#FF4D45`; avviso `#FFB020`. Niente gradienti sottili o blur:
  il video verso l'auto li comprime male (banding).
- **Raggi**: icone app squircle ≈ 22% del lato; card 16dp; bottoni 14dp; pill = metà altezza.
- **Spaziature**: 4 / 8 / 12 / 16 / 24 / 32dp.
- **Tipografia (Inter)**: display 34 Bold, titolo 22 SemiBold, corpo 17 Regular,
  etichetta 13 Medium.
- **Tocco**: target minimo 64dp.
- **Movimento**: pressione 120–150ms, transizioni 200–250ms, easing decelerato; niente
  animazioni lunghe.
- **Layout**: dock laterale fisso a sinistra (orologio in alto, app recenti/principali,
  tasto Home in basso); Home = griglia di icone (≈4×2 per pagina, paginata, con indicatori);
  icone reali delle app installate dentro una cornice squircle.

## Decisioni prese

- Fork indipendente: niente più merge da Gearslip.
- applicationId e package Kotlin: `io.github.sirionero.mycar` (debug: `.dev`). Nome: "MyCar".
- MyCar è autonoma: continua a **definire** il permesso `android.car.permission.TEMPLATE_RENDERER`
  (quindi non si installa insieme a Gearslip).
- Donazioni: link dell'utente al posto di quelli di upstream (link da ricevere).
- Bug chiamate: priorità alle chiamate **in arrivo** (branch `fix/calls`).
- F-Droid: per ora variante **senza** voce offline (sherpa-onnx).
- Auto-updater delle build GitHub: rilasci di `sirionero/mycar`; disattivato nella build F-Droid.

## Backlog

- Voce offline nella build F-Droid: compilare sherpa-onnx (+ onnxruntime) da sorgente nella
  ricetta F-Droid invece del `.so` precompilato.
- Icona e logo MyCar (oggi è ancora l'icona di Gearslip).
- Lista "pacchetto + certificato" in `docs/BUILDING_APPS.md`: inserire l'hash del certificato di
  firma di MyCar quando esiste la chiave.

## Note F-Droid

Punti da risolvere (dettagli nel piano): `jniLibs/.../libsherpa-onnx-jni.so` prebuilt,
`speech-models/*.bin` non usati, auto-updater (`REQUEST_INSTALL_PACKAGES`), statistiche d'uso
verso l'endpoint upstream, voce `play-services-oss-licenses` inutilizzata in
`libs.versions.toml`, toolchain JetBrains via foojay.
