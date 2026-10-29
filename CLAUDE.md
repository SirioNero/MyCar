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
- **Modello**: l'utente ha scelto **Opus 5.5** (`claude-opus-5-5`) per questo progetto. Se la
  sessione gira su un modello diverso o inferiore (cambio o ripiego automatico: controllare con
  `get_session`, `session_context.model` e `last_served_model`), non fare modifiche, commit né
  push: fermarsi subito e chiedere all'utente di riselezionare Opus 5.5.
- **Ripresa automatica**: quando la sessione si sblocca (limiti di utilizzo, crediti finiti,
  riavvii), riprendere da soli il lavoro in sospeso dalla lista delle attività, senza aspettare
  che l'utente lo richieda.

## Build

- Gradle 9.3.1 (wrapper), AGP 9.1.1, Kotlin 2.4.0, compileSdk/targetSdk 37, minSdk 31, Java 21.
- Proprietà Gradle: `-PmyCarVersionName=...`, `myCarStatsUrl` (vuoto: nessuna statistica).
- `gradle/gradle-daemon-jvm.properties` chiede un JDK 21 **JetBrains** per il daemon: se non è
  installato, Gradle lo scarica da `api.foojay.io`.
- Servono Android SDK (`ANDROID_HOME` o `local.properties`) e accesso a `dl.google.com`
  (repository Maven Google).
- Test JVM nella sessione cloud (senza Android): Maven Central risponde 429, il mirror
  `https://maven-central.storage-download.googleapis.com/maven2/` va. Un progetto Gradle JVM nella
  scratchpad (Kotlin 2.4.0, JUnit 4, `srcDir` su `app/src` con `setIncludes` dei soli file) compila i
  file che non usano Android (es. `Framing.kt` + `FramingTest.kt`) e ne fa girare i test in pochi
  secondi; il resto si prova con la CI. Con un sottoprogetto `stubs` (sostituti di
  `android.content.Context`, `Uri`, `MyCarLog`, `SessionReport`) compila anche `CertCatalog` e
  compagni (`CertFamilyTest`). Per guardare un disegno animato del telefono senza emulatore: un
  progetto Compose Desktop nella scratchpad (Kotlin 2.2.0, CMP 1.9.3, le librerie androidx per il
  desktop prese da `refs/xonos/screens` di Xonos, `libs/`, con una sostituzione verso i moduli
  `-desktop`/`-jvm`) disegna in PNG, con `ImageComposeScene`, i composable che non usano Android
  (`PirateFlag`, `PirateShip`, `UpdateFlagScene`) a momenti scelti.
- Output: `build/myCar-debug.apk` (task `renameApk`), cartella build unica alla radice.
- Firma release opzionale via `keystore.properties` (gitignored).
- Chiave di firma di myCar: alias `mycar`, PKCS12, certificato SHA-256
  `E4:77:A4:79:4B:98:49:27:36:0E:9A:E4:A3:1E:87:CB:08:A2:6C:54:E0:E2:7E:43:34:11:6D:1C:7C:DC:CC:31`.
  Sta solo nei secret di GitHub (`KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS`,
  `KEYSTORE_KEY_PASSWORD`) e nel backup dell'utente; mai nel repository.
- Icona: logo in `docs/icon/mycar-logo.jpg`; `python3 docs/icon/make_icons.py docs/icon/mycar-logo.jpg .`
  rigenera i PNG `mipmap-*/ic_launcher_foreground.png` (main: lucchetto azzurro, debug: ambra) e
  `docs/icon/mycar-icon.png`. La sagoma a tema è il vettoriale `drawable/ic_launcher_monochrome.xml`.
- Il debug ha `applicationIdSuffix = ".dev"`. Nel debug esiste `CarPreviewActivity`, che
  renderizza la UI auto in PNG senza auto (utile per verificare il restyling).
- Anteprima in CI: il workflow **Car preview** (`.github/workflows/car-preview.yml` +
  `.github/car-preview.sh`) parte a ogni push su `design/**` (o a mano), avvia un emulatore, lancia
  `CarPreviewActivity` a 800×480 e 1920×720 (Home, griglia app, dashboard) e pubblica i PNG sul
  branch `ci/car-preview` (`git fetch origin ci/car-preview`): gli artifact di Actions non sono
  scaricabili dalla sessione cloud. Installa anche, dagli ultimi rilasci, Vela Maps, Plancia
  (debug) e Xonos, le apre dalla griglia (cartelle `vela/`, `plancia/`, `xonos/`), fotografa
  telefono e impostazioni, e simula una chiamata in arrivo sul modem dell'emulatore (`call/`).
  `motion-*/` sono la griglia sopra uno sfondo animato (`--es wallpaper <id>`), scattata ogni
  2,5 s; `wallpapers/` è il selettore degli sfondi da solo (`--ez wallpapers true`).
  Le posizioni dei tocchi seguono l'ordine della griglia calcolato nello script. Con
  `--ez player true` l'anteprima mostra solo il player accanto alla mappa con un brano finto
  (`app/src/debug/.../car/PlayerPreview.kt`, cartelle `player/`): nell'emulatore non suona niente.
  `split/` è la Home con la musica: mappa vera di Vela, lettore vero accanto e brano nel dock
  (`--ez demoSong true`: un brano finto impostato in `CarMedia` dall'anteprima di debug,
  `showDemoSong`), scattata per 15 s perché la mappa ci mette qualche secondo a comparire.
  L'anteprima passa a `CarEnvironment.setDisplay` dimensioni e densità dello schermo, come una
  sessione vera: le app (mappa di Vela, Plancia) disegnano alla densità dell'auto.
  Pagine della Home: `pages/` (dal tasto mappa del dock, che risveglia i puntini, alla pagina di
  sinistra e poi a quella di destra), `pages-800x480/`, `pages-edit/` (matita e elenco delle
  scelte; parte con un'attesa: il primo tocco subito dopo l'avvio diventava una pressione lunga),
  `pages-layouts/` (matita, "Layout", elenco dei formati, "Half left"), `layout-<formato>/`
  e `layout-800x480/` (la pagina a sinistra della mappa in quel formato, `--es pageLayout <id>`); l'anteprima riparte ogni volta dalle pagine pronte (`HomePages.reset()`).
  Assistente: `assistant/` (Impostazioni al posto di un assistente-activity, `phone-screen.png`),
  `assistant-google/` (l'app Google dell'emulatore: schermo del telefono dopo il microfono e dopo lo
  scorrimento dall'angolo, `phone-mic.png` / `phone-swipe.png`), `assistant-mic/` +
  `assistant-gesture/` (l'assistente finto del debug, `debug/TestAssistant.kt`, scrive nel log
  flag e argomenti con cui Android lo mostra: il log della CI li confronta). L'assistente finto
  diventa predefinito solo dopo l'avvio dello scatto: un force-stop dell'app che fa da
  assistente fa tornare Android all'app Google. Verificato (0.5.04): dal microfono l'assistente
  riceve `invocation_type=1`, `triggered_by=83` e la sorgente "assist gesture", come dal launcher;
  l'app Google si apre in ascolto. Lo scorrimento simulato con `input` invece l'emulatore non lo
  prende per il gesto dell'assistente (né veloce né passo-passo): `assistant-gesture/` resta vuoto.
  `media/` è il player a tutto schermo col brano finto (anche il browse con la copertina), `media-800x480/`
  lo stesso a 800×480 (linguette strette); `phone-recents/` è il Telefono sui Recenti con chiamate
  scritte nel registro dell'emulatore (come root, poi `adb unroot`: gli scatti dopo restano come prima);
  `assistant-locked/` blocca l'emulatore con un PIN, tocca il microfono, fotografa la richiesta di
  sblocco, digita il PIN e controlla nel log che l'assistente di prova si apra dopo lo sblocco.
  L'assistente in un'altra lingua (`--es voiceLang it`, solo debug: `VoiceLanguages.override`, come
  col modello di quella lingua sul telefono; `--ez voiceHelp true` apre l'aiuto, `--es voiceReply
  "<risposta inglese>"` ne mostra una tradotta): scatti `voice-it/`, `voice-de/`, `voice-it-800x480/`,
  `voice-reply/`. `voice-check/` (`VoiceCheckActivity`, solo debug) chiede la misura di ogni file dei
  modelli vocali dove myCar li scarica (Hugging Face, che dalla sessione cloud non si raggiunge),
  scrive una risposta per lingua e le voci della sintesi dell'emulatore: l'emulatore (x86_64) non fa
  girare il riconoscimento, la libreria di sherpa-onnx è solo arm64.
  Lanciato a mano, il workflow accetta `shots` (nomi degli scatti separati da spazi, vuoto = tutti)
  per provare una cosa sola in pochi minuti; nello script `BEFORE` è un comando eseguito subito
  prima di aprire l'anteprima (es. accendere myCar in Accessibilità, che il force-stop spegne).
  Accetta anche `api` (livello API dell'emulatore, 34 di default) e `target` (immagine,
  `google_apis` di default): per Android 17 `api` è `37.0`, perché i pacchetti nuovi si chiamano
  `android-37.0` (con `37` l'emulatore non parte); con un livello diverso da 34 il log elenca i
  pacchetti che quel livello offre. Su Android 17 gli scatti `lnp-plancia-off/on` e
  `lnp-xonos-off/on` tolgono e ridanno a myCar il permesso della rete di casa
  (`pm revoke/grant … ACCESS_LOCAL_NETWORK`). Su API 34 quel blocco non esiste: per questo la
  CI non vedeva il bug di 0.5.10/0.5.11 (vedi "Rete di casa con Android 17").
  Plancia integrata: `.github/home-assistant.sh` avvia in Docker un Home Assistant usa e getta
  (utente, onboarding, token in `HA_TOKEN`) che l'emulatore raggiunge a `10.0.2.2:8123`; l'account
  arriva all'anteprima con `--es haUrl` / `--es haToken` (solo debug). Scatti `plancia-app/`
  (dalla griglia, poi il menu dal ☰ di Home Assistant), `plancia-page/` (pagina pronta a destra
  della mappa), `plancia-side/` (accanto alla mappa).
  Ponte: con gli ingressi `plancia_ref` / `xonos_ref` le app Plancia e Xonos si compilano da quel
  loro ramo (`.github/build-apps.sh`, build debug, Gradle 8.14.3 scaricato: le app non hanno il
  wrapper) al posto degli ultimi rilasci, e gli scatti `bridge-plancia`, `bridge-plancia-page`,
  `bridge-xonos` aprono Plancia e Xonos dentro myCar con `--ez bridge true` (tutto dal ponte: senza
  Tailscale nell'emulatore), le app configurate da root sugli stessi server; nel log le app scrivono
  "Ponte per <myCar> verso <server>" per ogni connessione. Dopo la compilazione delle app lo script
  ferma i demoni di Gradle e Kotlin: con quella memoria occupata l'emulatore cadeva con Xonos che
  suonava. Un emulatore caduto non blocca più la corsa: adb si arrende dopo 3 minuti, il log di ogni
  scatto resta in `stream.txt`, gli scatti si pubblicano comunque.
  Con `xonos_server` (e `xonos_ref`) `build-apps.sh` costruisce anche il pacchetto di Music Assistant per
  il telefono che Xonos scarica su richiesta (modulo `:server` + `make_payload.py` di Xonos, solo x86_64:
  Python 3.13 sul runner, FFmpeg e librespot compilati con NDK e Rust, una ventina di minuti), firmato
  con una chiave usa e getta che firma anche la build di Xonos (Xonos usa solo un pacchetto firmato come
  lui), in `third-party/xonos-server/`; lo scatto lo serve all'emulatore con un server HTTP sul runner
  (`10.0.2.2:8000`, file `files/server-package-url` di Xonos). Lo scatto `phone-xonos` prova Xonos
  integrato sul Music Assistant del telefono: `ServerCheckActivity --ez xonos true` di Xonos scarica il
  pacchetto e prepara server, account e album di toni, poi myCar con `--es maUrl http://127.0.0.1:8095` e
  il token di Xonos; lo scatto parte con Xonos fermo, che il ponte risveglia.
  Ogni esecuzione sovrascrive `ci/car-preview`: con più branch in CI, scaricarlo appena finisce.
- Prova della release in CI: il workflow **Release check** (`.github/workflows/release-check.yml` +
  `.github/release-check.sh`, `.github/tailscale.sh`) installa una release pubblicata (o il branch
  firmato come release, `tag=branch`) in un emulatore, accanto a un Home Assistant e a un Music
  Assistant suoi, con le app Plancia e Xonos configurate come le avrebbe un guidatore; poi apre le
  impostazioni di myCar e prova i collegamenti. Matrice `apis` (`37.0` = Android 17, `36`) × `nets`:
  `lan` (server a `10.0.2.2`), `tailscale` (l'app Tailscale da F-Droid, entrata in un headscale
  0.29.6 sul runner, server a `http://umbrel:<porta>` via MagicDNS), `tailscale-out` (lo stesso con
  myCar esclusa da Tailscale). Scatti, dump, log e preferenze sul branch
  `ci/release-check-<api>-<net>`. Verificato (0.5.13): in `tailscale-out` l'app Plancia si collega e
  myCar dice che Tailscale la lascia fuori; dentro il tunnel myCar si collega. Anche qui `plancia_ref`
  e `xonos_ref` (app dai loro rami): in `tailscale-out` myCar firmata come la release passa dal ponte.
- Screenshot del README (`docs/screenshots/`): dagli scatti dell'anteprima di `main` (non di un
  branch: devono mostrare la versione che si rilascia), coprendo il badge DEBUG BUILD in basso a
  destra. Mai scatti con la mappa ancora nera o col segnaposto "Map" del player di debug: del
  player si mostra solo il suo pannello. In cima c'è `home.png`, da `split/` (mappa vera e
  lettore): lì il badge si copre specchiando il pannello del lettore, che è simmetrico; sugli
  sfondi con linee (Waves) si riflette la parte appena a sinistra del badge, perché copiarla
  lascerebbe un gradino nell'onda.

## Rilasci

- Versione in `app/build.gradle.kts` (`versionCode`, `versionName` di default), novità in
  `Changelog.kt` (schermata "Novità" sull'auto) e in `.github/release-notes.md` (testo del rilascio).
- Il workflow **Release** parte dai tag `v*`, firma l'APK con i secret e lo allega al rilascio
  (non pre-release, così Obtainium lo trova).
- Dalla sessione cloud di Claude il push dei tag è rifiutato (403): il rilascio lo crea l'utente su
  GitHub (Releases → nuovo tag `v<versionName>` → branch di destinazione → Publish).
- All'utente si dà **sempre il link diretto** per pubblicare, con tag, destinazione e titolo già
  scritti: `https://github.com/SirioNero/MyCar/releases/new?tag=v<versionName>&target=<branch>&title=myCar%20<versionName>`
  (es. `…?tag=v0.5.14-alpha&target=main&title=myCar%200.5.14-alpha`): gli resta solo Publish.
  L'utente l'ha chiesto più volte: **ogni volta che c'è qualcosa da rilasciare, i link**, senza
  aspettare che li chieda. Anche per le app Plancia e Xonos, che si pubblicano da sole a ogni push
  su `main` che cambia l'app: il link per unire il ramo
  (`https://github.com/SirioNero/<repo>/compare/main...<ramo>`, poi Create pull request e Merge),
  oppure, se l'utente lo chiede, il push in `main` lo fa Claude e gli dà il link della release.
- Una versione verificata ma non ancora pubblicata quando `main` va avanti resta su
  `release/v<versione>` (es. `release/v0.4.00-alpha`), da usare come destinazione del rilascio.

## Architettura

Modulo unico `:app` (cartella `app/`), package `io.github.sirionero.mycar`. UI **interamente Jetpack Compose**
(XML solo per il tema della finestra).

| Package | Ruolo |
|---|---|
| radice | Protocollo AA: `MyCarRunner` (sessione, canali, video/audio), `SessionService` (servizio in primo piano del viaggio), `Tailnet` (Tailscale/VPN per i messaggi di Plancia e Xonos), `Framing`, `Protobuf`, `PhoneTls`, `ServiceDiscovery`, `CertProvider` / `CertDownloader`, `VideoSource` (encoder), `ScreenProjector` (VirtualDisplay + `Presentation` con Compose → encoder → auto), `MyCarActivity` (possiede la sessione USB/wireless), `PermissionActivity` (la domanda di Android sul telefono per un tocco sull'auto), `MyCarLog` |
| `car/` | **UI mostrata sull'auto**: `CarUi` (root: tema, navigazione, overlay, transizioni), `CarDock` (dock a sinistra), `CarHomeScreen` (mappa + player), `HomePager` / `HomePages` / `HomeWidgets` (pagine della Home ai lati della mappa, widget meteo e agenda), `GlassPlayer` (player accanto alla mappa), `CarLauncher` (griglia app a pagine), `PhoneAssistants` (assistente vocale a scelta), `AssistantLanguage` + frasari (l'assistente nella lingua del telefono), `WallpaperPicker`, `MediaScreen`, `PhoneDialerScreen` / `PhoneRecents` (Telefono: Recenti, contatti, tastierino), `QuickReplyTexts` (risposte rapide nella lingua del telefono), `CallOverlay`, `CarDashboardScreen`, `CarSettingsScreen`, `PlanciaApp` / `PlanciaWeb` / `PlanciaScripts` (Plancia integrata), `CinemaApp` / `CinemaShelves` / `CinemaPlayer` / `CinemaContinue` (Cinema in auto), `CarDriving` (stato di guida detto dall'auto, per il video), `CarWebContext` (il contesto di ogni WebView sullo schermo dell'auto), `WebSelect` (menu a tendina delle pagine), `AppLinks` (link alle app nelle pagine), `TemplateBack` / `BackCloses` (tasto Indietro dell'auto), `CarNavigator` (stack schermate `CarScreen`), template Car App Library (`Template*`) |
| `car/theme/` | Design system attuale dei controlli: `GsTheme` / `ControlLook` (temi Flat/Tactile/Keycap), `GsControls` (`GsButton`, `GsIconButton`, `gsColors`), `GsSeekBar`, `GsSwitch` |
| `ui/` | **UI sul telefono**: onboarding (`IntroScreen` col veliero `PirateShip`, `SetupScreen`; le vecchie `CertSetupScreen` / `SetupGuideScreen`), `UpdateSplash` (la bandiera dopo ogni aggiornamento), `HomeScreen`, `SettingsScreen`, `PlanciaSettings` (account di Home Assistant per Plancia), `XonosSettings` (account di Music Assistant per Xonos), `CinemaSettings` (siti di Cinema e "Video only when parked"), `MyCarTheme` (Material You), `AboutLinks` (link, donazioni, avvisi di licenza) |
| `host/` | Host Car App Library per app di terze parti (`CarAppConnection`, `KnownApps`) |
| `cinema/` | Cinema: `Cinema` (i siti, "solo a macchina ferma", `MORE` suggeriti), `SiteIcons` (l'icona che ogni sito dà di sé, scaricata e tenuta sul telefono), `PhoneVideos` (video del telefono), `TvLists` + `M3u` (liste IPTV), `ChannelLogos` |
| `home/` | Home Assistant di Plancia integrata: `HomeAssistant` (account, collegamento per tutto il viaggio, plance, import dall'app Plancia), `HaClient` (WebSocket, OkHttp), `Panels` (plance e titoli) |
| `audio/` | Audio verso l'auto (`AudioLink`, cattura `AudioCaptureService`, `DirectFeed` per la musica di myCar stesso, `CarMic`) |
| `bridge/` | Il ponte verso le app Plancia e Xonos per myCar lasciata fuori da Tailscale: `AppBridge` (legame e richiesta di una connessione), `Bridges` (quali connessioni passano dal ponte), `BridgedSockets` (`BridgeDns`, `BridgeSocketFactory` per OkHttp), `BridgeProxy` (la pagina di Plancia: proxy SOCKS5 nel WebView) |
| `xonos/` | Xonos integrato: `Xonos` (account, connessione, libreria), `XonosAccount` (import dall'app Xonos), il motore di Xonos (`data/`, `sendspin/`, `playback/`: Music Assistant, Sendspin, lettore Media3 `PlaybackService`), `sendspin/CarLinkOutput` (uscita dritta verso l'auto) |
| `call/` | Chiamate: `CarCalls` (stato da `TelephonyCallback` + numero dal broadcast `PHONE_STATE`; risponde/rifiuta/chiude con `TelecomManager`, serve `ANSWER_PHONE_CALLS`), `MyCarCallScreening` (filtro facoltativo: trattiene le chiamate da numeri fuori rubrica solo con l'auto collegata; ignora quelle in uscita) |
| `media/`, `notify/`, `speech/`, `mirror/`, `wireless/`, `update/`, `stats/` | Media, notifiche, voce (sherpa-onnx), mirroring, AA wireless (`WirelessLink`; collegamento da solo: `WirelessAuto`, `CarPresenceService`), auto-aggiornamento da GitHub, statistiche d'uso opt-in |

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
- `Glass.kt`: l'effetto vetro: `glassFinish` (riflesso, ombra in fondo, bordo di luce sopra
  un'icona), `GlassTile` (icone di myCar: colore a gradiente + simbolo bianco), `glassPanel`
  (pannello di vetro smerigliato, es. comandi del player sopra la copertina sfocata).
- `Wallpapers.kt`: sfondi nostri (Aurora default, Sunset, Ocean, Nebula, Ember, Forest, Graphite,
  o nessuno) disegnati dal codice con una grana anti-banding; `WallpaperSetting` (chiave
  `car_wallpaper`); `wallpaperBackground` li disegna allineati all'intero schermo, così dock e
  schermata sono un'unica immagine. Dietro griglia app e dashboard; il dock ci fa da vetro.
  Sfondi animati, nell'elenco "Moving" sotto quelli fermi ("Still"): Lava e Polar (bagliori che
  vagano, `Motion.Drift`), Waves (onde, `Motion.Waves`), Fireflies (luci che salgono,
  `Motion.Lights`). Ridisegnati ogni 33 ms nella sola fase di disegno, sull'uptime del telefono
  così le copie restano allineate. Movimento ben visibile ma calmo e continuo, niente lampi; ogni
  fotogramma che cambia costa al video, per questo il default resta fermo (Aurora). Il primo
  tentativo (0.5.03, interruttore "Wallpaper motion" con giri di 40–80 s) era troppo lento: in
  auto non si vedeva muoversi.
- Font: `res/font/inter_*.ttf` (OFL); icone: `res/drawable/sym_*.xml` (Material Symbols
  Rounded, Apache 2.0). Licenze in `licenses/` e `NOTICE.md`.

- **Colori** (scuro di default): sfondo `#000000`; superfici `#1C1C1E` / `#2A2A2D`;
  separatori `#3A3A3C`; testo `#FFFFFF` / secondario `#9A9AA0`; accento `#3D8BFF`;
  positivo `#32D46B`; negativo `#FF4D45`; avviso `#FFB020`. Selezione: blu pieno `#2B6BE6`;
  bottoni secondari grigi `#3A3A3C`. Gradienti ampi solo con grana (il video verso l'auto li
  spezza in bande); sfocatura solo dove serve (vetro del player, sulla copertina).
- **Raggi**: icone app squircle ≈ 22% del lato; card 16dp; bottoni 14dp; pill = metà altezza.
- **Spaziature**: 4 / 8 / 12 / 16 / 24 / 32dp.
- **Tipografia (Inter)**: tutta la scala Material in Inter, con le dimensioni di Material (le
  schermate ereditate sono impaginate su quelle) e spaziatura tra lettere un po' più stretta.
- **Tocco**: target minimo 64dp.
- **Movimento**: pressione 120–150ms, transizioni 200–250ms, easing decelerato; niente
  animazioni lunghe.
- **Layout**: dock laterale fisso a sinistra (`CarDock`: orologio in alto → dashboard, mappa,
  brano in riproduzione, app fissate e aperte; in basso, fissi, il microfono dell'assistente e
  sotto di lui il tasto della griglia app). Il microfono non sta nella lista delle app: resta
  sempre visibile allo stesso posto, quante che siano le app (prima, a dock pieno, spariva);
  Home resta mappa + player; la griglia app (≈4×2 per pagina, a pagine, con indicatori) si apre
  dal tasto del dock; icone reali delle app installate dentro una cornice squircle. Sulla griglia
  il tasto in fondo al dock diventa "mappa e musica" (`sym_space_dashboard`) e riporta allo
  schermo condiviso (`CarNavigator.homeWithPlayer()`).
- **Accanto alla mappa** (`SidePane.kt`, chiave `car_side_pane`, impostazione "Beside the map"):
  la musica (default: il player mentre qualcosa suona) oppure un'app scelta, sempre lì: un'app
  con template (es. Plancia) gira sul collegamento `browse` dentro il pannello, con la barra in
  alto e "Close" (`CarAppStage(endLabel)`); un'app musicale (es. Xonos) mostra il suo player e
  diventa l'app musicale solo se non ne suona già un'altra. Il pannello di un'app esiste solo
  mentre la Home è visibile: un'app aperta a tutto schermo usa lo stesso collegamento.
  Anteprima: `--es sidePane <pacchetto>`, cartelle `split-plancia/`, `split-xonos/`.
- **Pagine accanto alla mappa** (`HomePager.kt`, `HomePages.kt`, `HomeWidgets.kt`, chiave
  `car_home_pages`): la Home è una fila di pagine; quella della mappa (mappa + musica o app scelta,
  invariata) e quelle del guidatore ai suoi lati, ognuna in un **formato** (`PageLayout`, disegni e
  nomi in `PageLayouts.kt`): pagina intera, due metà, quattro parti, metà a sinistra o a destra
  accanto a due quarti, striscia larga sopra o sotto due quarti (0.5.19). Ogni
  parte: meteo, agenda, browser di myCar, Plancia integrata o un'app per auto con template non di
  navigazione (es. l'app Plancia: mostra la sua plancia predefinita, coi comandi). Pagine pronte
  finché il guidatore non ne cambia una (`readyMade`): Meteo + Agenda a sinistra, Plancia + Browser
  a destra se Plancia è configurata in myCar o installata; la prima modifica le salva tutte. Si cambia pagina scorrendo dove il dito non sposta
  già qualcosa (mappa, browser e app per auto lo prendono per sé: si scorre su widget, barre e
  spazi) o con i puntini in basso (sulla mappa svaniscono coi suoi tasti, `MapControlsWake`); la
  matita accanto ai puntini apre la modifica: "Layout" (col disegno del formato attuale: apre
  l'elenco dei formati a riquadri grandi, `LayoutChooser`; quello che la pagina mostrava resta, in
  ordine, finché c'è posto, `withLayout`), sposta a sinistra/destra
  (anche oltre la mappa), nuova pagina, elimina (secondo tocco di conferma), Fatto; un tocco su una
  parte apre l'elenco di cosa metterci. Tenere premuto un widget o la barra di un riquadro apre la
  modifica. Tutte le pagine restano composte (`beyondViewportPageCount`): la mappa non perde mai la
  superficie e il browser resta dov'era; un'app per auto gira solo nella pagina ferma in vista
  (`settledPage`), una per pagina, perché il collegamento `browse` disegna in un posto solo (fuori
  vista: icona e nome, `AppPlaceholder`). Il tasto mappa del dock, il tasto mappa dell'auto e
  Indietro su una pagina del guidatore tornano alla pagina della mappa (`CarNavigator.home()` /
  `mapPageRequests`). La tastiera del browser in un riquadro occupa tutta la larghezza della pagina.
  Il browser di un riquadro si crea solo alla prima apertura della sua pagina: il primo WebView della
  sessione blocca lo schermo dell'auto per qualche secondo (in anteprima 4-5 s e un'app ferma),
  quindi mai al collegamento dell'auto.
  Impostazioni dell'auto → "Pages beside the map": aggiungi una pagina (si apre in modifica) o
  torna a quelle pronte.
- **Plancia integrata** (0.5.10; `home/`, `car/Plancia*.kt`): Home Assistant dentro myCar, col
  nome Plancia. Si configura solo sul telefono (`ui/PlanciaSettings.kt`: indirizzo a casa, fuori
  casa, token di lunga durata, "Save and test") o dall'app Plancia (sotto). `HomeAssistant` si
  collega all'inizio del viaggio (`CarServices`), prima all'indirizzo di casa, e tiene l'elenco
  delle plance. La pagina è **un solo WebView per tutto il viaggio** (`PlanciaWeb`), creato 2,5 s
  dopo l'avvio della UI dell'auto (`PLANCIA_AFTER_MS`: il primo WebView ferma lo schermo per un
  momento, mai mentre l'auto si collega) e spostato dove Plancia si vede: a tutto schermo dalla
  griglia (barra sottile "Plancia"), in una parte di pagina (`PlanciaPart`, solo nella pagina ferma
  in vista) e accanto alla mappa (`PlanciaPane`); vince l'ultimo posto composto. Il motore web si
  carica mentre myCar si aggancia all'auto (`WebViewWarmUp`). Barra laterale di Home Assistant
  nascosta: il suo ☰ apre il menu di Plancia (`PlanciaScripts`): plance, quella che si apre per
  prima, dimensione 60–150% (riscrive il viewport), Ricarica; dove la plancia è piccola il menu
  apre Plancia a tutto schermo. Accesso col ponte `externalApp` di Home Assistant: il token va solo
  alle pagine dell'host di Home Assistant. Indirizzi `http` permessi (`usesCleartextTraffic`): a
  casa Home Assistant è spesso `http://…:8123`. In auto compare solo se configurata, e allora prende
  il posto dell'app Plancia ovunque (griglia, pagine, accanto alla mappa: `withPlancia`,
  `withPlanciaInside`); tolto l'account, torna l'app.
  Import dall'app Plancia (da 0.1.15): `content://it.plancia.ha.account`, metodo `account`;
  risponde solo ai pacchetti myCar della sua `hosts_allowlist` firmati col certificato di myCar
  (non alla build debug, firmata con la chiave di debug). myCar lo chiede da sé finché non è
  configurata, all'avvio e a ogni viaggio; sul telefono c'è "Use the Plancia app's account".
- **Xonos integrato** (0.5.11; `xonos/`): Music Assistant dentro myCar, col nome Xonos. Lo stesso
  motore dell'app Xonos (`SirioNero/Music-Assistant-on-Gearslip`: `data/`, `sendspin/`, `playback/`
  copiati, senza le schermate del telefono di Xonos; le correzioni fatte là vanno portate qui), in
  inglese come il resto dello schermo dell'auto, lettore "myCar" in Music Assistant, servizio Media3
  `PlaybackService` e provider delle copertine `${applicationId}.xonos.art` non esportati. In auto è
  una delle app musicali (`MediaCatalog` lo aggiunge solo se configurato, e allora toglie l'app Xonos;
  la scelta "accanto alla mappa" e l'ultima app musicale salvate con l'app passano a lui:
  `withXonosInside`, `CarServices.xonosFor`). Si configura sul telefono (`ui/XonosSettings.kt`:
  indirizzo, utente e password → un token suo, la password non si tiene) o dall'app Xonos (da 0.1.39:
  `content://it.xonos.ma.account`, metodo `account`, risponde solo a myCar firmata col suo
  certificato), da sola a ogni viaggio finché myCar non ha un account (`XonosAccount.adoptIfUnset`).
  **Audio**: con l'audio tramite myCar il PCM va dritto nel canale musicale di Android Auto
  (`CarLinkOutput` → `CarAudio.openDirect` → `audio/DirectFeed.kt`): pezzi da 20 ms al ritmo
  dell'orologio del telefono, 60 ms avanti, 250 ms in coda, silenzio quando non c'è musica (come la
  cattura di un telefono muto, così l'auto tiene le casse); la cattura cede finché Xonos suona e per
  2 s dopo; le scritture nel canale sono una alla volta (AAC); conversione di frequenza e canali se
  l'auto non prende 48 kHz stereo. Se la strada cambia a metà brano `AudioEngine.reroute()` apre
  un'uscita nuova senza buttare l'audio in coda. Finché la musica in auto è Xonos (aperto, ultimo
  usato o nessun altro) Android non fa la domanda (`CarAudio.consentWaits`); la fa quando si apre
  un'altra app musicale. Col Bluetooth Xonos suona con l'AudioTrack, come l'app.
  Anteprima: `.github/music-assistant.sh` (Music Assistant 2.10.8 in Docker, un album di tre toni
  fatti con ffmpeg, token in `MA_TOKEN`), `--es maUrl` / `--es maToken`, scatti `xonos-inside/`
  (griglia, Browse, Albums, play dell'album, Now playing) e `xonos-side/`; log con il tag `Xonos`.
  Verificato in emulatore (0.5.11): si collega, sfoglia, suona l'album (Opus 48 kHz, AudioTrack) e
  avanza; accanto alla mappa la scelta fatta con l'app passa a lui. L'uscita diretta verso l'auto
  ha solo i test (`DirectFeedTest`): l'emulatore non ha un canale Android Auto. Lo scatto ferma la
  musica alla fine: una volta l'emulatore è caduto un minuto dopo, con l'album ancora in corso.
  **Music Assistant sul telefono** (Xonos con il server, ramo `claude/server-ma` di Xonos): l'account che
  l'app Xonos passa a myCar punta a `127.0.0.1:8095`, e quel server vive dentro l'app Xonos. Allora le
  connessioni di Xonos integrato passano sempre dal ponte (`Bridges.through`, `onThePhone`): il legame
  sveglia l'app (che riavvia il suo server) e la tiene sveglia per il viaggio; myCar riprova finché il
  server non risponde (al primo avvio una quindicina di secondi).
  Il ripiego sull'app (`InsideOrApp`) qui non scatta: l'app suonerebbe dallo stesso server, che un'app appena
  risvegliata fa partire in una quindicina di secondi; il ponte lo dice ("on this phone is still starting").
  Verificato (scatto `phone-xonos`, Xonos da `claude/server-ma` con il server): myCar sveglia l'app Xonos, si
  collega dopo 6 s attraverso il ponte e suona l'album dal Music Assistant del telefono. Di nuovo con Xonos 0.1.42,
  che il server lo scarica come pacchetto e lo avvia come processo a sé: Xonos scarica il pacchetto dal runner e
  suona; poi myCar sveglia l'app Xonos, che avvia Music Assistant, dice "still starting" e riprova, si collega
  attraverso il ponte dopo circa 14 s e Music Assistant suona sul lettore "myCar". Subito dopo l'emulatore è
  caduto: le immagini dello scatto mancano, il log è in `stream.txt`.

- **Cinema** (dopo la 0.5.13; `cinema/`, `car/CinemaApp.kt`, `car/CarDriving.kt`, `ui/CinemaSettings.kt`,
  nome scelto dall'utente): l'app video, che in realtà è un browser fatto per il video. In auto i
  siti come riquadri con l'icona che il sito dà di sé (manifest, apple-touch-icon, `<link rel=icon>`,
  `/apple-touch-icon.png`, o quella che il WebView riceve; tenute in `filesDir/cinema-icons`):
  nessun logo nel pacchetto; finché non c'è, l'iniziale su un colore. Un sito si apre in un WebView
  con barra sottile (indietro, nome, Close), schermo intero quando il player lo chiede
  (`onShowCustomView`), la tastiera dell'auto per cercare, e a scelta il layout da computer (user
  agent Linux con la stessa versione di Chrome). Siti suggeriti finché il guidatore non cambia
  l'elenco: YouTube, RaiPlay, Twitch; si aggiungono e tolgono sul telefono (chiave `cinema_sites`).
  Video protetto (DRM: Netflix, Prime Video, Disney+, DAZN…): si lascia provare
  (`RESOURCE_PROTECTED_MEDIA_ID`) ma quasi certamente esce nero (il display virtuale non è sicuro),
  e i riquadri e le impostazioni lo dicono. Camera e microfono ai siti mai.
  **"Video only when parked"** (chiave `cinema_parked_only`) è **acceso di predefinito**: mentre
  l'auto si muove l'immagine è coperta (`ParkedCover`) e il suono continua. Quando si muove lo dice
  l'auto stessa, come ad Android Auto: `CarDriving` legge il DrivingStatus (campo 13 degli eventi
  dei sensori, bit NO_VIDEO); se l'auto non lo dice, la velocità GPS (`CarMotion`). Si può spegnere
  solo dalle impostazioni del telefono, e prima compare un avviso: myCar lo sconsiglia vivamente,
  guardare video guidando è pericoloso e in molti paesi è illegale, la responsabilità è solo del
  conducente ("Turn it off" / "Keep it on"). Decisione dell'utente: solo l'impostazione predefinita
  e l'avviso; **non scrivere da nessuna parte (app, README, note) frasi sulla "filosofia" di myCar**
  (tipo "myCar è del proprietario dell'auto"). I siti di casa (Jellyfin) hanno bisogno del permesso
  della rete di casa (vedi "Rete di casa con Android 17"): chiesto aggiungendo il sito, e in auto
  una copertura dice cosa concedere. Anteprima: `--ez moving true` (`CarDriving.override`), scatti
  `cinema/` (griglia, siti, YouTube) e `cinema-moving/`.
  **Scaffali di myCar** (0.5.19), prima dei siti: "On this phone" (`cinema/PhoneVideos.kt`: video dal
  media store, i più nuovi prima, anteprime con `loadThumbnail`; permesso `READ_MEDIA_VIDEO` con
  l'accesso limitato di Android 14, `READ_EXTERNAL_STORAGE` fino al 32, chiesto in auto con
  `PermissionActivity` e sul telefono con "Videos on this phone") e un riquadro per ogni **lista TV**
  (`cinema/TvLists.kt`, chiave `cinema_tv_lists`; formato M3U letto da `cinema/M3u.kt` + `M3uTest`:
  `#EXTINF` con tvg-logo e group-title, `#EXTGRP`, intestazioni da `#EXTVLCOPT` e dopo "|", indirizzi
  relativi; fuori i canali con licenza DRM in `#KODIPROP` e quelli udp/rtmp). Una lista si scarica
  quando si apre il suo riquadro, al massimo ogni 6 ore, e resta in `filesDir/cinema-tv` (canali
  subito, anche senza rete); con più gruppi prima i gruppi. Loghi: `ChannelLogos` (quattro alla
  volta, in memoria). Si suonano col **MediaPlayer di Android** su una TextureView (`car/CinemaPlayer.kt`,
  niente ExoPlayer: sarebbe una dipendenza nuova): comandi al tocco (-10 s, play/pausa, +10 s, linea),
  via dopo 4 s; i video ripartono da dove erano (`cinema_resume`, ultimi 30); un film in una lista
  (mp4, mkv...) si sposta e riparte come un video, un canale è "LIVE"; il focus audio del telefono
  mette in pausa per una chiamata e riprende. "Video only when parked" copre anche qui, con la barra
  (Indietro, Chiudi) sopra. Sul telefono "More sites" (`Cinema.MORE`: Dailymotion, Vimeo, La7, TED,
  Arte, NASA+, Internet Archive, un tocco) e la sezione "Cinema: TV". Anteprima: un video di prova
  nei Movies dell'emulatore e una lista dal runner (`10.0.2.2:8001`, HLS + mp4 + logo, fatti con
  ffmpeg), `--es tvList <indirizzo>`, scatti `cinema-videos/`, `cinema-tv/`, `cinema-videos-moving/`.
  Verificato in emulatore (API 34): anteprime, video che suona, gruppi e loghi, canale HLS in diretta,
  copertura in movimento. Non provato: in auto, con liste IPTV vere.
  **La musica dopo Cinema** (0.5.21, richiesta dell'utente): un film prende le casse e l'app musicale
  si ferma per sempre (perdita del focus, come con ogni lettore); uscendo da Cinema la musica che
  suonava quando si è aperta riparte da sola (`car/CinemaMusic.kt`: mezzo secondo dopo, se ferma, se
  l'app musicale dell'auto è ancora quella e collegata).
- **Schermo condiviso**: con la musica accesa il player sta da solo accanto alla mappa
  (`CarNavigator.sidePanel` parte da `CONTROLS`), come nella vecchia barra in basso; la X lo
  chiude per il viaggio, un tocco sul brano nel dock lo riporta o lo toglie, tenerlo premuto apre
  il player a tutto schermo. Non nasconderlo dietro gesti poco scopribili.
- **Dimensioni**: Extra small 83%, Small 90%, Normal, Large 125%, Extra large 150% (scala la
  densità di tutta la UI dell'auto, `CarSettings.scale`).
- **Player in stile telefono recente** (0.5.09): comandi semplici senza bottoni sotto
  (`PlayerGlyphs.kt`: `PlainButton`, che si stringe alla pressione e mostra un quadrato tenue per
  casuale/ripeti accesi, e `ThinSeekBar`, linea sottile che si ingrossa trascinando), tempo
  trascorso e "-restante"; il player a tutto schermo (`MediaScreen`) è sempre scuro, sopra la
  copertina sfocata con velo e grana (`CoverBackdrop.kt`), e nel browse la copertina resta in
  vista (`CompactPlayer`). Il player accanto alla mappa forza il bianco sui comandi
  (`LocalContentColor`), perché il vetro è scuro anche col tema chiaro.

## Decisioni prese

- Fork indipendente: niente più merge da Gearslip.
- applicationId e package Kotlin: `io.github.sirionero.mycar` (debug: `.dev`). Nome visibile: **myCar**
  (scritto esattamente così, m minuscola e C maiuscola) in app, README e documenti; i nomi nel
  codice restano `MyCar…` (es. `MyCarActivity`).
- myCar è autonoma: continua a **definire** il permesso `android.car.permission.TEMPLATE_RENDERER`
  (quindi non si installa insieme a Gearslip).
- Donazioni: solo Bitcoin, `bc1qu8q2sc5kuccjj3zpzzfpj85xn7szp73nae9xlx` (in `ui/AboutLinks.kt` e nel README).
- Bug chiamate: priorità alle chiamate **in arrivo** (branch `fix/calls`).
- Audio delle chiamate: arriva in auto **solo via Bluetooth** (vivavoce HFP), come con Android Auto.
  Un'app non di sistema non può metterlo sul collegamento USB né spostarlo in vivavoce (da
  Android 12 l'instradamento lo decide chi possiede la chiamata). myCar quindi avvisa:
  `BluetoothAudioCheck` controlla il vivavoce anche con l'audio USB, e `car/CallVoice.kt` fa dire
  alla schermata della chiamata quando la voce resta sul telefono (branch `fix/call-audio`).
- Audio verso l'auto (`audio/CarAudioRoute.kt`, chiave `car_audio_route`): **Automatico**
  (default), Bluetooth, **Tramite myCar** (l'ex "USB": cattura dell'audio + canale musica di
  Android Auto, col cavo o senza fili). Automatico decide a ogni viaggio: se il telefono ha l'audio
  Bluetooth (A2DP) collegato all'auto (all'indirizzo dell'auto, se lei lo dichiara) usa il
  Bluetooth; altrimenti dopo 5 s passa a myCar, e ci passa anche se l'A2DP cade a metà viaggio.
  Nato dal VW Tayron: si collega a myCar ma col Bluetooth porta solo le chiamate, e la musica
  restava sul telefono. Migrazione: il vecchio "USB" diventa Tramite myCar, il vecchio
  "Bluetooth" (quasi tutti: era il default del setup) diventa Automatico. In Automatico sul
  Bluetooth le indicazioni vocali di myCar tacciono: la voce del navigatore arriva già per quella via.
- Focus audio con l'auto (`AudioLink.onFocus`, 0.5.09): LOSS_TRANSIENT (chiamata, assistente in
  Bluetooth, voce dell'auto) ferma lo stream **tenendo** il focus e il GAIN dell'auto lo riprende;
  LOSS_TRANSIENT_CAN_DUCK lascia suonare; solo LOSS (l'autista sceglie la radio) restituisce il
  focus e mette in pausa il telefono, e la musica che riparte dopo almeno 1,5 s di silenzio
  (misurati sull'audio catturato) richiede il focus all'auto. Prima ogni interruzione, anche un
  avviso che abbassa, restituiva il focus per sempre e la musica non tornava fino al viaggio dopo.
  Test: `AudioLinkFocusTest`.
- Domanda di Android per l'audio tramite myCar: myCar chiede l'**intero schermo** come unica
  scelta (`MediaProjectionConfig.createConfigForDefaultDisplay()`, Android 14+): un tocco su
  Avvia, nessuna app da scegliere, passa l'audio di tutte. **Non** si fa premere Avvia a myCar
  (accessibilità o altro): è una protezione di sicurezza, ed è il trucco delle app spia; il
  tentativo (`fix/audio-consent`) è abbandonato e non va unito. Per non avere la domanda: il
  comando adb una tantum (PROJECT_MEDIA) già spiegato nell'aiuto, il Bluetooth, o la musica
  suonata da myCar stesso (Xonos integrato, 0.5.11: nessuna domanda finché si ascolta lui).
- App fatte per Gearslip: Plancia (`it.plancia.ha`, repository `SirioNero/Home-Assistant-on-Gearslip`)
  e Xonos (`it.xonos.ma`, `SirioNero/Music-Assistant-on-Gearslip`) funzionano con myCar. Xonos
  accetta qualsiasi app che lo comanda; Plancia **debug** accetta qualsiasi host, la **release** (da
  0.1.13) Gearslip ufficiale e myCar: nella sua `hosts_allowlist` le righe
  `e477a4794b984927360e9ae4a31e87cb08a26c54e0e27e4334116d1c7cdccc31,io.github.sirionero.mycar` (e
  `.dev`). Con Plancia integrata configurata, in auto l'app Plancia non compare più.
  Plancia lascia in alto 44dp per la barra di myCar (`CHROME_BAR` in `CarAppStage`): non cambiarla.
- Vela Maps (`app.vela`, open source, OpenStreetMap) funziona con myCar: si collega, disegna e
  naviga. Niente più segno giallo (`KnownApps.partial`): la mappa che avanza a scatti è come Vela
  la disegna in auto (immagini ferme), non un difetto dell'host. Restano `partial` Spotify e Deezer.
- Assistente vocale a scelta (`AssistantChoice`, chiave `car_assistant`): myCar (default),
  l'assistente del telefono o un'app assistente installata. Gli esterni si aprono sul telefono
  (ASSIST / VOICE_ASSIST / VOICE_COMMAND / VOICE_SEARCH_HANDS_FREE, in `<queries>`): Android non
  permette di disegnarli sull'auto; la loro voce arriva in auto solo via Bluetooth. Per
  l'assistente predefinito (Gemini, Claude, Google…) myCar lo sveglia **come lo scorrimento da
  un angolo in basso** (`AssistGesture`): chiama `launchAssist` del servizio di ricerca via
  `ServiceManager` + `Parcel` (transazione 6, dopo cinque getter; uguale da Android 12 a 16; non
  chiede permessi), che passa per l'interfaccia di sistema e mostra l'assistente in ascolto, non
  in chat scritta. Gli argomenti sono quelli che il launcher dà per lo scorrimento
  (`triggered_by` = 83, `invocation_type` = 1). È così che Claude si apre in voce dove un ASSIST
  aprirebbe la tastiera. Se non
  va, ripiega sugli intent: prima VOICE_SEARCH_HANDS_FREE, poi VOICE_COMMAND / VOICE_ASSIST,
  ASSIST per ultimo (ora con gli stessi extra del gesto). "L'assistente del telefono" è l'app
  letta da `Settings.Secure` "assistant"; l'app di Gemini (`com.google.android.apps.bard`) la
  voce la dà tramite l'app Google (`com.google.android.googlequicksearchbox`).
  Col telefono bloccato o spento (`car/AssistantUnlock.kt`, 0.5.09) gli assistenti (Claude come
  quasi tutti) non ascoltano, e nessuna app può superare il blocco: myCar apre una schermata
  trasparente sopra il blocco (`showWhenLocked`, `turnScreenOn`) che chiede ad Android di aprire
  il telefono (`requestDismissKeyguard`: subito senza blocco o con un dispositivo attendibile,
  altrimenti volto/impronta/PIN) e, aperto, fa ripartire `CarAssistant.start()`, che ora trova il
  telefono sbloccato. Aprire una schermata dal background lo permette il servizio di
  accessibilità di myCar (Android lo collega con `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`): senza,
  dopo 2,5 s l'auto dice di sbloccare e ritoccare il microfono. La richiesta di sblocco parte
  quando la schermata ha il focus (schermo già acceso) o dopo 1,5 s: chiesta a schermo ancora
  spento, Android la annulla subito (lo ha mostrato la prima prova); se la scarta entro 1,5 s la
  si ripete una volta. Verificato (0.5.09, scatto `assistant-locked`): PIN sul telefono, avviso
  sull'auto, e dopo lo sblocco l'assistente di prova si apre con `invocation_type=1`,
  `triggered_by=83`.
- **L'assistente nella lingua del telefono** (0.5.21, richiesta dell'utente: "il tts e stt devono
  essere italiani, lo stesso per spagnolo etc"; myCar non ha una scelta della lingua, la UI resta
  inglese e l'assistente segue il telefono): italiano, spagnolo, francese, tedesco, portoghese
  (`speech/VoiceLanguage.kt`). La lingua vale solo con un modello finale che la sente
  (`VoiceLanguages.effective`; o il riconoscitore del telefono, se scelto): senza, l'assistente resta
  in inglese come prima. **Modelli**: Canary sente en/es/de/fr (ora gli si dice la lingua, srcLang =
  tgtLang, non traduce); **Parakeet multilingue** (TDT 0.6B v3 int8, 640 MB, CC BY 4.0, 25 lingue
  europee) anche it e pt; `SpeechModels.finalFor` sceglie per lingua (quello scelto se la sa, poi
  Canary, poi Parakeet multilingue), `recommended()` per la lingua del telefono (Kroko + Canary, o
  Kroko + Parakeet multilingue per it/pt, che dal setup diventa anche la scelta). Kroko (solo
  inglese) in un'altra lingua sente solo quando si smette di parlare: le sue parole non si mostrano,
  si aspetta il modello finale (anche se sta caricando) e lo si usa anche quando Kroko non ha scritto
  niente. **Comandi e risposte** (`car/AssistantLanguage.kt` + `AssistantItalian/Spanish/French/
  German/Portuguese.kt`): `AssistantCommands` resta inglese; `toEnglish` trasforma la frase nel
  comando inglese che vuol dire (regole in ordine, come in `AssistantCommands`), `answer` traduce ogni
  frase inglese (`Said`: tutte quelle di AssistantCommands, CarAssistant, SpeechEngine, VoiceReply),
  ridicendo la destinazione come detta ("Ti porto alla stazione", `remember`/`asSaid`, anche quando
  AssistantCommands toglie l'app in fondo), ore ("È l'1:05", "Es ist 14:05 Uhr"), tempo rimasto,
  meteo; i messaggi letti non passano da `answer` (il loro testo potrebbe somigliare a una risposta).
  Portoghese del Portogallo o del Brasile secondo il paese del telefono. "con/su/mit…" prima del nome
  di un'app (`appWords`) come "with/on". Sintesi vocale nella lingua (`speakingLocale`: pt-PT/pt-BR
  come il telefono); prima le risposte inglesi si sentivano con la voce della lingua del telefono.
  Sullo schermo dell'auto nella lingua solo le parole dell'assistente (aiuto, "Ti ascolto…", "Di'
  «annulla» o «cambia»"); sul telefono "Voice: Italian" nelle impostazioni (`ui/VoiceLanguageNote.kt`)
  e gli esempi dell'intro. Verificato: `AssistantLanguageTest` sulle frasi come le scrive Parakeet v3
  (trascritte nella sessione cloud con sherpa-onnx per Python da voci sintetiche pico2wave/MBROLA:
  quasi perfette in it/es/fr/de, il portoghese di MBROLA è peggiore); in CI i file dei modelli (anche
  Parakeet multilingue: 622 + 11 + 6 MB) e le voci della sintesi (it, es, fr, de, pt disponibili
  nell'emulatore); aiuto e risposte in auto negli scatti `voice-*`. Non provato: il riconoscimento sul
  telefono vero in un'altra lingua (la libreria è solo arm64).
- **La bandiera e il veliero** (0.5.21, richiesta dell'utente: "la bandiera deve sventolare dopo ogni
  aggiornamento", e con una build grossa "appare anche il veliero, con la bandiera attaccata", animazioni
  "graficamente fighe e ben fatte"): dopo ogni aggiornamento (versionCode più alto di quello annotato,
  `UpdateFlag`, chiave `version_code` in `update_flag`) una pagina sola, `UpdateSplash` /
  `UpdateFlagScene`: la bandiera di `PirateFlag` issata e al vento, una luce che respira, stelline,
  "Updated to 0.5.21"; tocco, Indietro o 7 s vanno avanti. Con una versione nuova di primo livello
  (l'intro, una per `Intro.release`, es. 0.6) al suo posto c'è l'intro, la cui prima pagina ha il
  **veliero** (`ui/PirateShip.kt`): galeone disegnato dal codice (scafo a venature con assi e oro,
  sportelli, finestre accese e lanterna, tre alberi con coffa, vele quadre gonfie con cuciture e
  teschio, fiocco, randa, sartiame) che arriva da sinistra, cavalca l'onda e issa il Jolly Roger (lo
  stesso telo, `drawCloth`) sull'albero maestro; mare a tre strati, scia, spruzzi, ombra, gabbiani,
  sole e luccichio; col tema scuro luna, stelle e mare cupo. Tutto nel solo disegno (orologio di 120 s
  su cui tornano tutti i periodi). Una nuova installazione non vede né l'una né l'altro.
- Condivisione schermo (`mirror/`): mentre il telefono è copiato in auto, `TouchRelayService`
  (accessibilità, già usato per i tocchi) aggiunge un pixel trasparente
  `TYPE_ACCESSIBILITY_OVERLAY` con `screenOrientation = LANDSCAPE` (fisso, non sensore) che gira il
  telefono in orizzontale; lo toglie a fine condivisione. I tocchi si mappano sulle dimensioni
  reali del display (`Display.getRealSize`), non sulla finestra di myCar, che non riceve la rotazione.
- **Rete di casa con Android 17** (0.5.12, `LocalNetwork.kt`, `ui/LocalNetworkAccess.kt`): myCar ha
  targetSdk 37, e Android 17 tiene un'app così fuori dalla rete di casa (IP privati, link-local,
  nomi `.local`, anche nei WebView) finché non ha il permesso runtime `ACCESS_LOCAL_NETWORK`, del
  gruppo "Dispositivi nelle vicinanze" come il Bluetooth (concesso quello, Android lo dà senza
  dialogo, ma va chiesto). Senza, le connessioni TCP vanno in timeout. Era il bug "Plancia e Xonos
  dentro myCar non si collegano agli indirizzi http con cui le app funzionano": le app Plancia e
  Xonos hanno target 34 e il permesso implicito; non c'entrava https (`usesCleartextTraffic` c'è).
  myCar lo chiede: nella guida (pagina del Bluetooth, "Nearby devices." da Android 17),
  all'apertura sulla Home una volta per avvio se Plancia o Xonos hanno un indirizzo di casa, prima
  delle prove nelle impostazioni (Save and test, account dalle app, Sign in), e una riga "Allow the
  home network" resta in quelle sezioni finché manca. In auto, se manca, Plancia e Xonos lo dicono
  (`LocalNetwork.HINT`). Il wireless lo chiedeva già (`WirelessButton`). Da ricordare per Cinema e
  il browser: un sito di casa (Jellyfin, il router) ha bisogno dello stesso permesso.
- **Tailscale e VPN** (0.5.13, `Tailnet.kt`): con Home Assistant o Music Assistant raggiunti via
  Tailscale (`http://umbrel:8123`, MagicDNS, 100.64.0.0/10, `fd7a:115c:a1e0::/48`) myCar dice il
  motivo vero: Tailscale che la lascia fuori (Settings → App split tunneling), un nome che Tailscale
  non conosce (Use Tailscale DNS), Android 17 fuori dalla rete di casa. La VPN di Tailscale non si
  scavalca: dentro la VPN Android rifiuta a myCar il legame alla rete Wi-Fi dell'auto (EPERM), e il
  senza fili passa allora dalla strada normale (`WirelessLink`, `bindRefused`); con myCar dentro
  Tailscale conviene il cavo. Gli indirizzi tailnet non sono "rete di casa" (`LocalNetwork.isLocal`).
  Lo split tunneling per il senza fili non si evita da myCar: con una VPN non scavalcabile netd
  rifiuta a un'app dentro la VPN il legame a un'altra rete, e a un'app fuori la VPN stessa
  (`checkUserNetworkAccess`); serve un secondo programma con un altro uid. **Ripiego** (0.5.15,
  `car/InsideOrApp.kt`, regola pura `car/TakeOver.kt` + `TakeOverTest`): per tutto il viaggio si
  guardano i collegamenti di Plancia e Xonos dentro myCar (Xonos ora resta collegato a Music
  Assistant per tutto il viaggio, come Plancia); se non si collegano e l'app è sul telefono, l'app
  prende il loro posto fino a fine viaggio (`HomeAssistant.appTakesOver`, `Xonos.appTakesOver`):
  subito se gli indirizzi sono tutti su una tailnet che lascia fuori myCar (`Tailnet.outOfReach`) o
  se il token è rifiutato, dopo 10 s senza collegamento a inizio viaggio, 30 s se c'era e si è perso
  (il tempo senza rete non conta). Griglia, pagine, accanto alla mappa, icone fissate e Plancia a
  tutto schermo passano all'app (`withPlancia`, `withPlanciaInside`, `withXonosInside`, `xonosFor`,
  nei due versi); la musica che era Xonos dentro myCar passa all'app senza cambiare l'ultima app
  ricordata; la pagina web di Plancia non si crea. Sul telefono, se l'indirizzo è su una tailnet,
  Plancia e Xonos dicono che senza fili passano dall'app (stesso indirizzo), o che basta aggiornarla
  (`ui/TailscaleCableRow.kt`).
  Scatto in CI `standin` (indirizzi che non rispondono, app installate). Il secondo programma sono
  le app Plancia e Xonos: vedi "Il ponte".
- **Il ponte** (0.5.16, `bridge/`): con myCar lasciata fuori da Tailscale (per il senza fili) Plancia
  e Xonos dentro myCar raggiungono i server attraverso le app Plancia e Xonos, che restano dentro
  Tailscale. myCar si lega al loro servizio `MyCarBridge` (azione `io.github.sirionero.mycar.BRIDGE`,
  `BIND_IMPORTANT` + `BIND_INCLUDE_CAPABILITIES`: l'app resta sveglia e con la rete quanto myCar;
  lasciata 5 minuti dopo l'ultima connessione) e chiede con una transazione Binder (`OPEN`,
  interfaccia `io.github.sirionero.mycar.Bridge`, host e porta) una connessione: l'app la apre e
  passa l'altro capo di un `socketpair` (`AppBridge`). Le app rispondono solo a myCar firmata col suo
  certificato (le loro build di prova anche a `.dev`) e solo verso gli indirizzi scritti nelle loro
  impostazioni: serve lo stesso indirizzo in myCar e nell'app. I rifiuti hanno un codice (OK 1,
  NOT_MYCAR 2, NOT_OURS 3, NO_ANSWER 4, OTHER 0) e myCar li dice con parole sue ("Through the Plancia
  app: …", che `Tailnet.why` lascia come sono). Passano dal ponte (`Bridges.through`) solo con myCar
  fuori dalla VPN, verso un indirizzo della tailnet o un IPv4 di casa mentre il telefono è fuori casa
  (route di Tailscale); tutto il resto va per la strada solita. OkHttp (HomeAssistant, MaRepository:
  API, Sendspin, copertine, accesso): `BridgeDns` lascia il nome all'app (segnaposto TEST-NET-1) e
  `BridgedSocket` è un socket vero collegato a un ascolto su 127.0.0.1 per quella sola connessione,
  poi copiato su quello dell'app; TLS ci passa sopra come sempre. La pagina di Plancia (WebView) passa
  da `BridgeProxy`: un proxy SOCKS5 su 127.0.0.1 che il WebView usa (`ProxyController` di
  androidx.webkit, "reverse bypass") solo per gli indirizzi di Home Assistant, deciso prima di ogni
  caricamento (`PlanciaWeb.prepareWay`); la pagina resta sul suo indirizzo vero (TLS, certificato, il
  nome con cui un reverse proxy la smista). Il primo tentativo, la pagina caricata da un indirizzo
  locale al posto di quello di Home Assistant, rompeva i reverse proxy che smistano per nome: scartato.
  Con il ponte una tailnet fuori portata non fa più passare subito l'auto alle app (`InsideOrApp`): il
  ripiego resta dopo 10 s. Lato app: `it/plancia/ha/data/MyCarBridge.kt` e Xonos 0.1.40
  `data/MyCarBridge.kt` (rami `claude/ponte-mycar`). Gli ascolti locali sono su 127.0.0.1
  (`LOOPBACK`): su Android `InetAddress.getLoopbackAddress()` è ::1, e il WebView, mandato al proxy
  su 127.0.0.1, non trovava niente (`ERR_PROXY_CONNECTION_FAILED`). Verificato (0.5.16): Release check
  `tailscale-out` su Android 17 (Tailscale vero, myCar esclusa e firmata come la release): Plancia e
  Xonos dentro myCar si collegano attraverso le app ("Ponte per io.github.sirionero.mycar verso
  umbrel:8123" e ":8095"); anteprima `bridge-*`: la pagina di Plancia e il suo menu attraverso proxy
  e ponte, Xonos suona l'album con Sendspin dal ponte.
- **Il viaggio in primo piano** (0.5.13, `SessionService`): dal collegamento dell'auto alla fine un
  servizio in primo piano `connectedDevice` (notifica "myCar is connected to the car") tiene vivo
  il viaggio col telefono bloccato, con un wake lock parziale e, senza fili, un Wi-Fi lock. Si
  avvia con myCar in vista; se Android lo rifiuta si riprova in `onResume`. Fermato mentre parte,
  si ferma da sé dopo `startForeground` (come `LocationKeepAlive`), altrimenti Android chiude myCar.
  I servizi del viaggio (`CarServices.init/shutdown`) sono contati: li tiene la sessione
  (`MyCarActivity.holdsServices`) e ogni `CarUi`, così condivisione schermo, encoder nuovo e
  anteprima non li smontano. La musica si sceglie una volta per viaggio (`mediaDecided`); le app
  chiuse dal guidatore restano chiuse (`closedBrowse`, `closedMedia`). Le domande di Android
  (audio, condivisione schermo) chieste a telefono bloccato aspettano myCar aperta (`onResume`) e
  l'auto lo dice. Una domanda alla volta (`questionUp`): Android tiene una sola proiezione, e la
  seconda accettata chiudeva la prima. Durante la condivisione schermo la domanda dell'audio non
  si fa (`MirrorService.sharing`, `CarAudio.askLater`) e la cattura fermata dalla condivisione
  non riparte né richiede: a fine condivisione la chiede `CarUi`. Accanto alla mappa l'app musicale
  prende il posto solo della scelta automatica del viaggio quando non si collega
  (`mediaAutoPicked`), mai di un'app scelta dal guidatore.
- **Motore da Gearslip** (0.5.18, branch `fix/engine`): delle 116 modifiche di Gearslip dopo il fork
  (0.3.01 → 0.3.31) si portano solo quelle che non possono cambiare come si collega un'auto che oggi va
  (richiesta dell'utente: "porta quello che ha senso, non cose che potrebbero rompere il nostro
  set-up"). Riscritte a mano su myCar, non unite: i diff si leggono rinominando gli alberi di Gearslip
  (percorsi, package, Gearslip → MyCar/myCar) e facendone il cherry-pick a 3 vie. Portate: messaggi
  lunghi in pezzi della misura di Android Auto (0x3f00 compresa l'intestazione: 16120 + 16124…,
  `Frames.writeMessage`, solo il lato che invia); ping con i dati del campo 3 rimandati nel campo 2;
  ByeBye in chiaro prima che l'auto accetti il certificato (prima `PhoneTls.encrypt` girava a vuoto per
  sempre: `SSLEngine.wrap` prima della stretta di mano non consuma niente) e, a viaggio avviato, fine
  della sessione dopo la risposta (durante l'impostazione il riaggancio resta il verdetto per
  `CertRotation`); margini dell'auto convertiti con la densità scalata; senza fili da solo (sotto);
  dettagli Wi-Fi ripetuti ignorati mentre un viaggio usa quella rete (ZLink; dopo la fine di una
  sessione l'auto che ricomincia sullo stesso canale la richiede ancora); `VideoSource.stop`
  interrotto; audio: Start una volta (`startLock`), AudioSpecificConfig (messaggio 1) alle auto solo
  AAC-LC, margine dopo `AudioUnderflow` (0x800B: 60 ms di silenzio a passo, massimo 240, da zero a
  ogni Start); canale musica chiuso finché il suono non passa da myCar (`CarAudio.openSink`: subito con
  Tramite myCar, mai con Bluetooth, con Automatico quando decide per myCar); permessi dello schermo
  Telefono chiesti sul telefono (`PermissionActivity`; senza finestra entro 400 ms apre le impostazioni
  dell'app); app per auto senza risposta a `getAppInfo` 10 s dopo il legame → rifiutata; span
  `DurationSpan`/`DistanceSpan` nei testi delle app. **Non portate**: la versione 1.7 (myCar risponde
  sempre 1.3: le auto che vanno sono state provate così); i controlli severi su frammenti, messaggi
  fuori stato e protobuf in arrivo; la fine della sessione sugli errori di protocollo 255/65535 (ora
  solo un nome nel log); la domanda delle casse prima di ogni indicazione (dc61607: una risposta di
  focus in ritardo andrebbe al canale musica e lo farebbe partire); il canale PhoneStatus per gli
  schermi dell'auto (f4b8391: due giorni di vita, possibili doppioni con le chiamate Bluetooth); i
  dati dell'auto alle app (b168eb7); il quadro strumenti video, il bitrate adattivo e lo schermo
  dalla misura sbagliata (sperimentali o con impostazioni nuove); la scelta dell'app musicale
  (c888d41: myCar la fa a modo suo). Da altri fork niente per ora: jorgecanut ha concluso che quasi
  tutte le auto chiamano il telefono da sole quando sono pronte, cioè l'attesa che myCar fa già.
- **Senza fili da solo** (0.5.18, `wireless/WirelessAuto.kt`, `CarPresenceService`): Android lascia
  aprire la sua schermata dal background solo a un'app che gestisce il dispositivo come "companion".
  Quando l'immagine di un'auto arriva senza cavo (`onSurfaceReady` con `wirelessSocket`,
  `WirelessLink.reachedCar`) myCar chiede l'associazione (`CompanionDeviceManager.associate`, filtro
  sull'indirizzo Bluetooth): Android mostra una volta il suo "Consenti". Poi `startObservingDevicePresence`,
  e quando l'auto si collega in Bluetooth Android chiama `CarPresenceService.onDeviceAppeared`, che apre
  `MyCarActivity` con `CAR_NEARBY` → `WirelessLink.start` come un tocco. Solo auto che hanno funzionato;
  una risposta (sì o no) conta, una finestra mai mostrata no; niente seconda domanda nello stesso
  viaggio; un'auto che si collega e scollega apre myCar una volta ogni 15 s. Col cavo resta il cavo, un
  viaggio in corso non si tocca. Impostazioni → Connection → "Connect wirelessly by itself" (chiave
  `on` in `mycar_wireless_auto`): spento smette di guardare, riacceso richiede le auto a cui si era
  detto di no. `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` per il `SessionService`
  col telefono in tasca. Non provato con un'auto vera.
- **Setaccio** (0.5.19, branch `fix/bug-scan-3`): quattro letture del codice (sessione e protocollo,
  audio e Xonos, interfaccia dell'auto, resto), ogni segnalazione verificata nel codice prima di
  correggerla. Corretti: la freccia di Vela ferma sulla prima svolta (`CarIcon.equals` confronta le
  icone bitmap solo per tipo: `pictureKey()` per identità); un `<select>` in una pagina chiudeva myCar
  ("Window type mismatch" del dialogo sulla Presentation: `carWebContext` dà al WebView il
  WindowManager semplice del display, dove il dialogo fallisce con la `BadTokenException` che
  Chromium gestisce; i date picker Chromium li salta già senza Activity; scatto `browser-select`);
  `CAR_NEARBY` accettato da qualsiasi app (`WirelessAuto.mayConnect`: interruttore acceso e auto
  approvata); un'auto che ricomincia sullo stesso canale ignorata per il filtro ZLink (fine della
  connessione segnata subito, `WirelessLink.watched` / `tcpEnded`, e per identità del socket); una
  seconda `MyCarActivity` (singleTop con un dialogo sopra) che si prendeva gli agganci e chiudendosi
  fermava la cattura (`current` / `handedOver`: passa l'intento alla prima); `configChanges` senza
  keyboard/navigation/mcc…; encoder che restava preso se non partiva; attesa delle conferme video
  sull'ora del telefono; fotogrammi chiave forzati a schermo dell'auto; **2 s di buco a ogni Start con
  le auto che non confermano l'audio** (`AudioLink.carAcks`: la finestra solo dopo la prima conferma;
  pezzo contato prima dell'invio); PCM a un'auto solo AAC senza encoder; `DirectFeed` che dopo una
  scrittura trattenuta mandava tutto di colpo e dormiva altrettanto; AudioTrack morto in Xonos
  (`PcmOutput.DEAD`); sintesi vocale delle indicazioni mai ritentata; chiamata che squilla ancora "nel
  filtro" (Decline a vuoto); 401/403/404 di passaggio che davano via Xonos (`MISSET` solo
  `BAD_ADDRESS`/`BAD_TOKEN`); nomi senza punti presi per Tailscale dietro altre VPN
  (`Tailnet.onTailnetBehind`); data dell'agenda dopo mezzanotte; bagliore dell'assistente che
  ricomponeva a ogni fotogramma; un orologio solo per gli sfondi animati (`MotionClock`) e niente
  sfondo sotto altri schermi; copertina rifatta a ogni play/pausa (`lastMetadata`); tempi del player
  accanto alla mappa fermi quando non si vede (`LocalPlayerInView`); tasti del player stretti a
  Large; anello del fuoco perso nei temi dei player (`MyCarCarTheme` tiene l'indicazione attorno);
  setup che diceva "bloccato" dopo un Indietro (`FirstSetup.blocked`: solo rifiuto dopo spiegazione o
  risposta senza dialogo). Non provato in un'auto vera. Lasciati (rischio più alto del guadagno, o
  casi rari): le copie dei byte per fotogramma video e pezzo audio, sospendere l'encoder mentre
  l'auto mostra il suo schermo, una LOSS in ritardo che risponde al nostro RELEASE (solo dopo un
  riavvio dell'audio server), `drawWithCache` per gli sfondi. La correzione di `PcmOutput.DEAD` va
  portata anche nell'app Xonos.
- **Setaccio 2** (0.5.20, branch `fix/bug-scan-4`): altre quattro letture (video e tocchi, schermo
  dell'auto, app del telefono, browser/Cinema/Plancia), ogni segnalazione verificata prima. In auto:
  **menu a tendina delle pagine** (`car/WebSelect.kt`: script in ogni pagina e frame con
  `addDocumentStartJavaScript` + `addWebMessageListener` di androidx.webkit, che prende il `mousedown`
  di un `<select>` (Blink apre lì il suo menu) e lo mostra come elenco sopra lo schermo dell'auto;
  la scelta torna alla pagina con `input` e `change`; una pagina può chiedere solo subito dopo un
  tocco o la manopola, `car/CarInput.kt`; scatto `browser-select` con "change Two" nel log); tasto
  Indietro dell'auto all'app per auto che mostra la freccia (`car/TemplateBack.kt`) e prima ai
  pannelli aperti (`car/BackCloses.kt`, CarUi); "Stop navigating" in due tocchi; il vetro del player
  accanto alla mappa tiene i tocchi tra i tasti; tasti più grandi (Indietro 56 dp, player 52);
  avvisi Bluetooth/posizione solo da fermi; rubrica che carica e preferiti in cima; accesso alle
  notifiche aperto sull'interruttore di myCar (`notify/NotificationAccess.kt`); ora d'arrivo nei
  tocchi (`ScreenProjector.dispatchTouch(at)`); livelli H.264 5–5.2 solo oltre 1080p. Cinema: niente
  più chiusura di myCar se cade il processo delle pagine; film che riprende dopo un errore
  (Android dice "finito" anche dopo un errore), canali che riprovano da soli; ricerca nelle liste
  TV; link alle app (`car/AppLinks.kt`); campi già attivi e nei componenti web; pagina che si
  ricarica al ritorno della rete. Telefono: conferme prima di togliere certificati/account/siti,
  tasti che aprono le impostazioni quando Android non chiede più, schermo acceso solo col
  collegamento, frasi senza "head unit", e altro (vedi note di rilascio). **Lasciati** per non
  toccare come l'auto riceve l'immagine o perché incerti: GOP e ripetizione dell'ultimo fotogramma,
  encoder nuovo senza rifare la UI, prima immagine anticipata, collegamento della mappa prima della
  scansione delle app, SurfaceView per Cinema, video coperto in marcia (setSurface/INVISIBLE),
  browser fermo fuori vista (YouTube si metterebbe in pausa); da decidere con l'utente: sfondo
  animato nel dock accanto alla mappa. Il nastro dell'intro (ereditato) è diventato, su richiesta
  dell'utente, una bandiera pirata che sventola, disegnata dal codice: teschio, "100% LOCAL", "WE
  DEFEND OUR DATA" (`ui/PirateFlag.kt`; scatti `setup-phone/09-intro-*` con le animazioni accese).
- **Setaccio 3** (notte, 0.5.20, `fix/bug-scan-4`): letture di musica/messaggi/chiamate/assistente
  in auto e di robustezza, ogni punto verificato prima. **Recenti** nel Telefono dell'auto
  (`car/PhoneRecents.kt`: registro chiamate, READ_CALL_LOG già chiesto dal setup; chiamate di fila
  con lo stesso numero in una riga col conteggio, perse in rosso, nome dalla rubrica se il registro
  non l'ha, numeri formattati; tocco → `CallPrompt`; si rilegge col `ContentObserver` del registro; il
  Telefono si apre sull'ultima scheda usata, Recenti all'inizio); "Call back" nel popup di una
  chiamata persa (`CarNotification.missedCall`, letta come "Missed call from…"). Assistente: "call
  back"/"redial" (registro una chiamata alla volta, `loadRecents(grouped = false)`), "read my
  messages"/"reply to…"/"text …" (`messageCommand`, prima di "play X"), "call X on mobile/home/work"
  (numero chiesto, poi predefinito, poi cellulare; `Contact.type/typeLabel/primary`), "play" e il ▶
  del volante senza app aperta riaprono l'ultima e la fanno suonare (`CarServices.noMusicApp`,
  `reopenLastMedia(autoplay)`, `ScreenKey.PLAY` solo dopo `mediaDecided`), "play the next song" / "play
  it again" non sono ricerche e "call Skip" non salta il brano, errori che vanno via in 6 s, risposte
  senza sintesi vocale visibili 5 s, "play X" ignorato detto anche fuori dal player. Risposte rapide
  nella lingua del telefono (`car/QuickReplyTexts.kt`: it, es, fr, de, pt; orario come lo scrive il
  telefono). Svolta nel dock durante lo squillo; Reply senza "Do you want to reply?"; pallino dei non
  letti per chat (`CarNotifications.seen`); un passo in ogni schermata d'errore (app accanto alla
  mappa, elenco musicale, app che rifiuta, ricerca vuota); linguette del player su due righe sotto
  92 dp, nomi delle app come sono; nel dock barre al posto del simbolo pausa; la X del player chiude
  anche con l'app chiusa. Cinema: "Continue" riapre l'ultima cosa (`car/CinemaContinue.kt`, azzerato
  dall'anteprima). Test nuovi: `VideoLevelTest`, `QuickReplyTextsTest`, `TemplateBackTest`. Scatti
  nuovi: `phone-recents`, `media-800x480`, `cinema-continue`. "When do I arrive?" dal percorso della
  mappa. Robustezza: `CarBluetooth.init` già in `MyCarActivity.onCreate` (al primo viaggio di un
  processo la scheda "Pair" non compariva: l'auto offre l'abbinamento prima che partano i servizi del
  viaggio), radice "" di un'app musicale (Android lancia su `subscribe("")`: ora niente elenco),
  rubrica e comandi vocali protetti (risposta "Something went wrong"), sensori formattati solo con
  "Vehicle data" acceso, gestore dei crash una volta per processo, indice dei colori di Cinema mai
  negativo. Lasciato: `startForeground` senza try in cattura audio e condivisione (nessun percorso
  che fallisca trovato, e tocca la cattura). Revisione indipendente dei commit della notte, corretti:
  "reply to X" per livelli di nome come "call" (prima "Anna" trovava Giovanna) e il destinatario
  detto nella domanda; "call back" dopo un numero privato; il numero migliore anche nella conferma;
  "read my messages" con tutti i nuovi della chat (`CarNotifications.unreadCount`) e chat letta sul
  telefono non più nuova (`gone` → `seen`); chiamata persa riconosciuta anche dal canale "missed" del
  dialer predefinito o di Telecom (il dialer AOSP non mette la categoria); "Call back" del popup solo
  sulla sua chiamata (stessa ora, con numero) e con uno scope che sopravvive al popup; ▶ del volante
  senza app solo se il telefono non suona già altro (`isMusicActive`); "Continue" che segue
  `doUpdateVisitedHistory`; Telefono su Contatti senza registro, linguette strette sotto 600 dp; testi
  delle canzoni "non raggiunti" con Try again. Da decidere con l'utente: "Fuck you too." detto ad alta
  voce dall'assistente (anche con passeggeri).
- **Setaccio 4** (notte, 0.5.20, `fix/bug-scan-4`): app del telefono e schermate dell'auto meno
  guardate, ogni punto verificato nel codice. Telefono: tasti sopra la barra a tre tasti e che crescono
  coi caratteri grandi, tastiera che non copre i campi delle Impostazioni (`imePadding`), Plancia che
  non perde l'account con un campo vuoto, avviso del certificato che va via, Disconnect con conferma a
  viaggio avviato, conferme per liste TV, siti propri di Cinema e modelli vocali, "Connect wirelessly"
  con myCar spenta, gruppi "Phone" e "Call logs" nel setup (come li chiama Android), Indietro
  dall'intro rivista, http per gli indirizzi di casa scritti senza schema in Cinema. Auto:
  `CarNavigator.close()` per Stop e "Close app" (il tasto Indietro di un'app, `AppBackKey`, si prendeva
  il passo e lasciava uno schermo nero), "Open on connect" sopra Home, tasto mappa che chiude la
  modifica delle pagine, Done sempre in vista e menu dell'app che scorre a Extra large, `ChoiceRow` che
  scorre sotto 110 dp a scelta, secondo tocco per "Ready-made pages", tastiera del browser chiusa a
  pagina nuova (`onPageStarted`), menu dell'app chiuso allo squillo, agenda senza permesso, "Beside the
  map" acceso con Plancia/Xonos dentro myCar, icone fissate lette come quelle mostrate (`pinShown`, la
  regola di pagine e "Beside the map"; il limite conta solo quelle visibili), "Clear all" della
  Dashboard col secondo tocco, svolta del dock e app aperte lette solo quando servono (meno
  ricomposizioni in guida). Da decidere con l'utente: i puntini delle pagine sempre
  sopra il fondo di browser, Plancia e app (svanire come sulla mappa, o una striscia riservata).
  Lasciati: il conto alla rovescia di "Start" che continua con la mappa fuori vista (scelta di
  design), le schede di `TabTemplate` senza scorrimento (non visto con un'app vera).
- **Prima connessione** (`ui/SetupScreen.kt`, `setup/`): una lista sola, "Set up myCar", al primo
  avvio (al posto di `CertSetupScreen` + le pagine di `SetupGuideScreen`, ancora nel codice finché
  la nuova non è provata sul telefono) e ogni volta che si torna con "Take me back to the initial
  setup" (in cima a "How to connect", `BackToSetupCard`) o da Impostazioni → "Initial setup". Tre
  gruppi: per collegarsi (certificato, myCar accesa se era spenta, Android Auto da spegnere o da
  tenere scegliendo myCar quando Android chiede), per il viaggio (chiamate, mappa e musica: un solo
  "Allow" con tutti i permessi in una richiesta; bloccati: "Open settings"), "More" facoltativi
  (messaggi, calendario, notifiche, filtro chiamate, audio in auto, note d'uso). Un tasto grande
  fa sempre il prossimo passo. Lo stato si legge dal telefono ogni volta (`SetupSignals`;
  `SetupSteps` senza Android, `SetupStepsTest`): tornarci non cancella né azzera niente.
  `FirstSetup` ricorda solo Android Auto tenuto, passi saltati, permessi chiesti; all'uscita la
  guida conta come vista (le Novità al primo viaggio le salta già `CarSettings.init`). Su Home
  "Finish setting up" (`SetupLeftCard`) finché manca qualcosa per collegarsi. Tolta la domanda delle
  notifiche a ogni avvio. La scheda del passo successivo scorre in vista quando lo diventa (non
  all'apertura); aperta la pagina di Android Auto, al ritorno "Keep Android Auto" è già aperto.
  Nelle schede i tasti stanno uno sotto l'altro: affiancati, su un telefono stretto il secondo
  diventava una colonna di lettere. Scatti in CI: `setup-phone` (installa senza permessi, scarica
  davvero i certificati, tocca i tasti per testo con uiautomator, scorre a piccoli passi; schermo
  dell'emulatore 320×640; in fondo `09-intro-1…4`, il veliero dell'intro con le animazioni accese, e
  `10-update-1…4`, la bandiera dopo un aggiornamento, con le preferenze scritte via `run-as`). Verificato (0.5.14): lista dal titolo, certificati scaricati e
  controllati, Android Auto tenuto, un "Allow" con sei domande di Android in fila, tutto spuntato,
  Home senza "Finish setting up", ritorno dalla Guida, e con i contatti tolti di nuovo "Allow".
- **Certificati** (`CertCatalog`, `CertChecks`, `CertRotation`, `CertRotationPolicy`): tre
  certificati pubblici, scaricati solo dopo un tocco e mai dentro l'APK, tutti firmati dalla radice
  Google Automotive Link: aasdk (consigliato, chiave PKCS#1, SHA-256 `1C0E0EF9…3C35`, fino al 2045),
  Headunit Revived (riserva, fino al 2044, chiave PKCS#8, `E323CC6D…ED6D`), da
  `raw.githubusercontent.com` a un commit fissato, poi dai rami; e **CarService** (0.5.21, "Newest
  cars", richiesta dell'utente: va sulla sua Tayron), quello di Android Auto stesso, dall'archivio
  `Iscle/android-auto-certificates` (`android-auto/latest/`, `certificate.pem` con la radice dopo e
  `private-key.pem` PKCS#8; prima il ramo `main`, poi il commit `c20cc85`). Android Auto lo cambia a
  ogni versione e dura pochi mesi (quello di ottobre 2026 fino al 3/2/2027), e l'archivio si aggiorna
  da solo ogni ~10 giorni: niente impronta fissa ma una **famiglia** (`CertCatalog.Family`): soggetto
  O=CarService, emesso e firmato (`verify`) dalla radice che sta nel file dopo di lui e che deve avere
  l'impronta fissata `49E52EFC…D17F`. Riconosciuto per nomi (`CertChecks.inFamily`, per dire quale è
  in uso). A un mese dalla fine si riscarica da solo (`refreshIfDue`, una volta per avvio da
  `MyCarApp`) e, se era in uso, il nuovo va nello slot; un'auto che lo trova scaduto (−23/−24 con il
  certificato scaduto anche per il telefono) conta come rifiuto e si riscarica. Test con certificati
  fatti nei test (`TestChain`, `CertFamilyTest`), mai quelli veri. Prima di tenerli: impronta (o
  famiglia), chiave che corrisponde, RSA ≥ 2048, validità, stretta di mano TLS sul
  telefono. Ognuno in `identity-catalog/<id>.p12`; quello in uso si copia nello slot che
  `CertProvider` legge (`identity-downloaded/phone.p12`). `MyCarRunner` manda il verdetto
  dell'auto (`onCertVerdict`) solo dopo averle mostrato il certificato: accettato, rifiutato
  (allarme TLS, AuthComplete −2/−3), riaggancio durante la verifica (debole), data dell'auto
  (−23/−24, "Check the car's date"). Rifiutato: si passa al successivo; tutti rifiutati: si torna al
  primo e ci si ferma fino a "Try again from the first"; un riaggancio non conta per un certificato
  già accettato; quello del guidatore non si cambia mai. Avvisi su Home (`CertNoticeCard`), scelta a
  mano in Impostazioni (aasdk · Recommended, Headunit Revived · Spare, CarService · Newest cars, con la
  data di scadenza). jsDelivr come terza fonte non
  usato: non raggiungibile dalla sessione cloud per verificarlo. Senza fili non riprova da solo col
  successivo: va provato in auto.
- Dipendenze approvate dall'utente: OkHttp e androidx.webkit (Plancia integrata), Media3 `session`
  e `common` (Xonos integrato: Apache 2.0, nessuna telemetria).
- Sfondi "stile umbrelOS" ma **nostri**: le immagini di umbrelOS hanno una licenza non libera.
- F-Droid: per ora variante **senza** voce offline (sherpa-onnx).
- Auto-updater delle build GitHub: rilasci di `sirionero/mycar`; disattivato nella build F-Droid.

## Backlog

- Voce offline nella build F-Droid: compilare sherpa-onnx (+ onnxruntime) da sorgente nella
  ricetta F-Droid invece del `.so` precompilato.
- **Comandi rapidi di Home Assistant** come widget nelle pagine (cancello, garage), sul
  collegamento di Plancia integrata (`HomeAssistant`).
- **Xonos integrato, il resto**: server trovati in rete (mDNS `_mass._tcp`, `NsdFinder` di Xonos)
  e primo account di un Music Assistant nuovo nelle impostazioni del telefono; mettere in pausa
  Xonos mentre l'auto tiene le casse per un momento (LOSS_TRANSIENT), invece di perdere quei secondi.
  Xonos e Plancia restano app a sé per Gearslip.
- **Spotify con Client ID personale**: Web API (PKCE) per libreria, salvati e ricerca nell'auto
  e per far suonare l'app Spotify del telefono; l'audio arriva via myCar o Bluetooth. Regole di
  Spotify da febbraio 2026: Premium del proprietario, massimo 5 utenti per Client ID, alcuni
  endpoint tolti. Il motore ufficiale per terze parti (Spotify Soloist, quello di Music
  Assistant) esiste solo per Linux: non gira sul telefono. librespot non suona con gli account
  creati da dicembre 2024.

## Note F-Droid

Punti da risolvere (branch dedicato): `app/src/main/jniLibs/.../libsherpa-onnx-jni.so` prebuilt,
`speech-models/*.bin` non usati, auto-updater (`REQUEST_INSTALL_PACKAGES`) da disattivare nella
build F-Droid, voce `play-services-oss-licenses` inutilizzata in `libs.versions.toml`, toolchain
JetBrains via foojay. Già fatto: nessun server di statistiche (`myCarStatsUrl` vuoto).
