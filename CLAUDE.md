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
  scelte); l'anteprima riparte ogni volta dalle pagine pronte (`HomePages.reset()`).
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
  `media/` è il player a tutto schermo col brano finto (anche il browse con la copertina);
  `assistant-locked/` blocca l'emulatore con un PIN, tocca il microfono, fotografa la richiesta di
  sblocco, digita il PIN e controlla nel log che l'assistente di prova si apra dopo lo sblocco.
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
| radice | Protocollo AA: `MyCarRunner` (sessione, canali, video/audio), `SessionService` (servizio in primo piano del viaggio), `Tailnet` (Tailscale/VPN per i messaggi di Plancia e Xonos), `Framing`, `Protobuf`, `PhoneTls`, `ServiceDiscovery`, `CertProvider` / `CertDownloader`, `VideoSource` (encoder), `ScreenProjector` (VirtualDisplay + `Presentation` con Compose → encoder → auto), `MyCarActivity` (possiede la sessione USB/wireless), `MyCarLog` |
| `car/` | **UI mostrata sull'auto**: `CarUi` (root: tema, navigazione, overlay, transizioni), `CarDock` (dock a sinistra), `CarHomeScreen` (mappa + player), `HomePager` / `HomePages` / `HomeWidgets` (pagine della Home ai lati della mappa, widget meteo e agenda), `GlassPlayer` (player accanto alla mappa), `CarLauncher` (griglia app a pagine), `PhoneAssistants` (assistente vocale a scelta), `WallpaperPicker`, `MediaScreen`, `PhoneDialerScreen`, `CallOverlay`, `CarDashboardScreen`, `CarSettingsScreen`, `PlanciaApp` / `PlanciaWeb` / `PlanciaScripts` (Plancia integrata), `CarNavigator` (stack schermate `CarScreen`), template Car App Library (`Template*`) |
| `car/theme/` | Design system attuale dei controlli: `GsTheme` / `ControlLook` (temi Flat/Tactile/Keycap), `GsControls` (`GsButton`, `GsIconButton`, `gsColors`), `GsSeekBar`, `GsSwitch` |
| `ui/` | **UI sul telefono**: onboarding (`IntroScreen`, `SetupScreen`; le vecchie `CertSetupScreen` / `SetupGuideScreen`), `HomeScreen`, `SettingsScreen`, `PlanciaSettings` (account di Home Assistant per Plancia), `XonosSettings` (account di Music Assistant per Xonos), `MyCarTheme` (Material You), `AboutLinks` (link, donazioni, avvisi di licenza) |
| `host/` | Host Car App Library per app di terze parti (`CarAppConnection`, `KnownApps`) |
| `home/` | Home Assistant di Plancia integrata: `HomeAssistant` (account, collegamento per tutto il viaggio, plance, import dall'app Plancia), `HaClient` (WebSocket, OkHttp), `Panels` (plance e titoli) |
| `audio/` | Audio verso l'auto (`AudioLink`, cattura `AudioCaptureService`, `DirectFeed` per la musica di myCar stesso, `CarMic`) |
| `bridge/` | Il ponte verso le app Plancia e Xonos per myCar lasciata fuori da Tailscale: `AppBridge` (legame e richiesta di una connessione), `Bridges` (quali connessioni passano dal ponte), `BridgedSockets` (`BridgeDns`, `BridgeSocketFactory` per OkHttp), `BridgeProxy` (la pagina di Plancia: proxy SOCKS5 nel WebView) |
| `xonos/` | Xonos integrato: `Xonos` (account, connessione, libreria), `XonosAccount` (import dall'app Xonos), il motore di Xonos (`data/`, `sendspin/`, `playback/`: Music Assistant, Sendspin, lettore Media3 `PlaybackService`), `sendspin/CarLinkOutput` (uscita dritta verso l'auto) |
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
  invariata) e quelle del guidatore ai suoi lati, ognuna in **due metà** o **quattro parti**. Ogni
  parte: meteo, agenda, browser di myCar, Plancia integrata o un'app per auto con template non di
  navigazione (es. l'app Plancia: mostra la sua plancia predefinita, coi comandi). Pagine pronte
  finché il guidatore non ne cambia una (`readyMade`): Meteo + Agenda a sinistra, Plancia + Browser
  a destra se Plancia è configurata in myCar o installata; la prima modifica le salva tutte. Si cambia pagina scorrendo dove il dito non sposta
  già qualcosa (mappa, browser e app per auto lo prendono per sé: si scorre su widget, barre e
  spazi) o con i puntini in basso (sulla mappa svaniscono coi suoi tasti, `MapControlsWake`); la
  matita accanto ai puntini apre la modifica: due metà / quattro parti, sposta a sinistra/destra
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
  dell'emulatore 320×640). Verificato (0.5.14): lista dal titolo, certificati scaricati e
  controllati, Android Auto tenuto, un "Allow" con sei domande di Android in fila, tutto spuntato,
  Home senza "Finish setting up", ritorno dalla Guida, e con i contatti tolti di nuovo "Allow".
- **Certificati** (`CertCatalog`, `CertChecks`, `CertRotation`, `CertRotationPolicy`): due
  certificati pubblici, scaricati solo dopo un tocco e mai dentro l'APK: aasdk (consigliato, chiave
  PKCS#1, SHA-256 `1C0E0EF9…3C35`) e Headunit Revived (riserva, valido fino al 2044, chiave PKCS#8,
  `E323CC6D…ED6D`), da `raw.githubusercontent.com` a un commit fissato, poi dai rami. Prima di
  tenerli: impronta, chiave che corrisponde, RSA ≥ 2048, validità, stretta di mano TLS sul
  telefono. Ognuno in `identity-catalog/<id>.p12`; quello in uso si copia nello slot che
  `CertProvider` legge (`identity-downloaded/phone.p12`). `MyCarRunner` manda il verdetto
  dell'auto (`onCertVerdict`) solo dopo averle mostrato il certificato: accettato, rifiutato
  (allarme TLS, AuthComplete −2/−3), riaggancio durante la verifica (debole), data dell'auto
  (−23/−24, "Check the car's date"). Rifiutato: si passa al successivo; tutti rifiutati: si torna al
  primo e ci si ferma fino a "Try again from the first"; un riaggancio non conta per un certificato
  già accettato; quello del guidatore non si cambia mai. Avvisi su Home (`CertNoticeCard`), scelta a
  mano in Impostazioni (aasdk · Recommended, Headunit Revived · Spare). jsDelivr come terza fonte non
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
- **Cinema** (nome scelto dall'utente): video solo a macchina ferma (freno a mano, velocità e
  stato di guida dall'auto, GPS come riserva) con fonti senza DRM: YouTube, Twitch, RaiPlay senza
  DRM, Jellyfin/Plex/Emby, video del telefono, IPTV. Netflix, DAZN, Prime, Disney+ esclusi: il
  video protetto su un display virtuale non sicuro esce nero, più i loro termini e F-Droid.

## Note F-Droid

Punti da risolvere (branch dedicato): `app/src/main/jniLibs/.../libsherpa-onnx-jni.so` prebuilt,
`speech-models/*.bin` non usati, auto-updater (`REQUEST_INSTALL_PACKAGES`) da disattivare nella
build F-Droid, voce `play-services-oss-licenses` inutilizzata in `libs.versions.toml`, toolchain
JetBrains via foojay. Già fatto: nessun server di statistiche (`myCarStatsUrl` vuoto).
