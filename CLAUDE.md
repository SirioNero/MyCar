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
  Ogni esecuzione sovrascrive `ci/car-preview`: con più branch in CI, scaricarlo appena finisce.
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
- Una versione verificata ma non ancora pubblicata quando `main` va avanti resta su
  `release/v<versione>` (es. `release/v0.4.00-alpha`), da usare come destinazione del rilascio.

## Architettura

Modulo unico `:app` (cartella `app/`), package `io.github.sirionero.mycar`. UI **interamente Jetpack Compose**
(XML solo per il tema della finestra).

| Package | Ruolo |
|---|---|
| radice | Protocollo AA: `MyCarRunner` (sessione, canali, video/audio), `Framing`, `Protobuf`, `PhoneTls`, `ServiceDiscovery`, `CertProvider` / `CertDownloader`, `VideoSource` (encoder), `ScreenProjector` (VirtualDisplay + `Presentation` con Compose → encoder → auto), `MyCarActivity` (possiede la sessione USB/wireless), `MyCarLog` |
| `car/` | **UI mostrata sull'auto**: `CarUi` (root: tema, navigazione, overlay, transizioni), `CarDock` (dock a sinistra), `CarHomeScreen` (mappa + player), `HomePager` / `HomePages` / `HomeWidgets` (pagine della Home ai lati della mappa, widget meteo e agenda), `GlassPlayer` (player accanto alla mappa), `CarLauncher` (griglia app a pagine), `PhoneAssistants` (assistente vocale a scelta), `WallpaperPicker`, `MediaScreen`, `PhoneDialerScreen`, `CallOverlay`, `CarDashboardScreen`, `CarSettingsScreen`, `CarNavigator` (stack schermate `CarScreen`), template Car App Library (`Template*`) |
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
  parte: meteo, agenda, browser di myCar o un'app per auto con template non di navigazione (es.
  Plancia: mostra la sua plancia predefinita, coi comandi). Pagine pronte finché il guidatore non ne
  cambia una (`readyMade`): Meteo + Agenda a sinistra, Plancia + Browser a destra se Plancia è
  installata; la prima modifica le salva tutte. Si cambia pagina scorrendo dove il dito non sposta
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
- **Schermo condiviso**: con la musica accesa il player sta da solo accanto alla mappa
  (`CarNavigator.sidePanel` parte da `CONTROLS`), come nella vecchia barra in basso; la X lo
  chiude per il viaggio, un tocco sul brano nel dock lo riporta o lo toglie, tenerlo premuto apre
  il player a tutto schermo. Non nasconderlo dietro gesti poco scopribili.
- **Dimensioni**: Extra small 83%, Small 90%, Normal, Large 125%, Extra large 150% (scala la
  densità di tutta la UI dell'auto, `CarSettings.scale`).

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
- App fatte per Gearslip: Plancia (`it.plancia.ha`, repository `SirioNero/Home-Assistant-on-Gearslip`)
  e Xonos (`it.xonos.ma`, `SirioNero/Music-Assistant-on-Gearslip`) funzionano con myCar. Xonos
  accetta qualsiasi app che lo comanda; Plancia **debug** accetta qualsiasi host, la **release** solo
  Gearslip ufficiale: per usarla con myCar va aggiunta alla sua `hosts_allowlist` la riga
  `e477a4794b984927360e9ae4a31e87cb08a26c54e0e27e4334116d1c7cdccc31,io.github.sirionero.mycar`.
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
- Condivisione schermo (`mirror/`): mentre il telefono è copiato in auto, `TouchRelayService`
  (accessibilità, già usato per i tocchi) aggiunge un pixel trasparente
  `TYPE_ACCESSIBILITY_OVERLAY` con `screenOrientation = LANDSCAPE` (fisso, non sensore) che gira il
  telefono in orizzontale; lo toglie a fine condivisione. I tocchi si mappano sulle dimensioni
  reali del display (`Display.getRealSize`), non sulla finestra di myCar, che non riceve la rotazione.
- Sfondi "stile umbrelOS" ma **nostri**: le immagini di umbrelOS hanno una licenza non libera.
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
