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
  `--es pageParts plancia,agenda,weather` (con `pageLayout`) dice cosa mostra ogni parte; il tocco
  `"x,y,swipe,x2,y2"` è uno scorrimento rapido (8 passi da 16 ms). Scatti `plancia-swipe/`,
  `plancia-swipe-800x480/`, `plancia-swipe-spotify/`: pagina in tre parti (Plancia, agenda, meteo),
  aperta dal puntino e lasciata scorrendo verso la mappa otto volte; `alive.txt` (funzione `alive`)
  dice se myCar gira ancora a fine scatto e, se no, crash, tombstone, uscite registrate da Android,
  memoria. Verificato (0.7, API 37.0 e 34): nessuna caduta. `last-exit-crash/` + `last-exit/`:
  `--ei crashAfterMs` (solo debug) manda a myCar un SIGSEGV, lo scatto dopo scrive come è finito
  (`LastExit`): verificato su Android 17, segnale, frame e righe dal tombstone.
  Crash del LayoutNode (`.github/car-preview-crash.sh`, inclusa da `car-preview.sh`; scatti `crash-*`
  solo se nominati in `shots`): `--es script` (solo debug, `PreviewScript.kt`) manda tocchi,
  trascinamenti e fling coi tempi di un'auto (`tap`, `drag`, `fling`, `tapText`, `focus`, `key`,
  `focusweb` / `unfocus` per il fuoco del WebView, `rep:n … end`, `snap`), e tiene gli ultimi
  fotogrammi in `ring/`; `CrashProbe.kt` scrive i cambi di fuoco col loro stack, il nodo a metà
  rimozione e quello che cade. `crash-webfocus` (fuoco al WebView di Plancia, poi alla mappa),
  `crash-focus`, `crash-focus-grid`, `crash-webfocus-edit` (pagina in modifica), `crash-webfocus-grid`
  provano la correzione; `crash-webfocus-cleared` era il controllo A/B; `crash-swipe-*`,
  `crash-grid-*`, `crash-cinema`, `crash-plancia-*` rifanno i viaggi dei log. Home Assistant parte
  anche per gli scatti `crash-`.
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
  `voice-reply/`. Home Assistant al posto dell'assistente (`--es voiceHeard "<frase>"`, solo debug: come
  se il guidatore l'avesse detta 8 s dopo l'avvio, `CarAssistant.heard`): `brain-ha/` ("what is the date
  today" → la risposta di Assist), `brain-ha-it/` (in italiano, che la pipeline inglese della CI non
  parla: l'agente chiesto in italiano), `brain-locked/` (telefono bloccato col PIN, "Phone's assistant"
  scelto: myCar ascolta da sé, niente richiesta di sblocco, `phone.png`).
  `voice-check/` (`VoiceCheckActivity`, solo debug) chiede la misura di ogni file dei
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
  loro ramo o tag (`.github/build-apps.sh`, build debug, Gradle 8.14.3 scaricato: le app non hanno il
  wrapper; Xonos dalla 0.1.49 compila proot con talloc da samba.org, che a volte non risponde ai runner:
  `.github/talloc-curl.sh`, un curl davanti a quello del runner, prova allora altre copie dello stesso file,
  tenute solo con lo SHA-256 di `proot.sh`) al posto degli ultimi rilasci, e gli scatti `bridge-plancia`, `bridge-plancia-page`,
  `bridge-xonos` aprono Plancia e Xonos dentro myCar con `--ez bridge true` (tutto dal ponte: senza
  Tailscale nell'emulatore), le app configurate da root sugli stessi server; nel log le app scrivono
  "Ponte per <myCar> verso <server>" per ogni connessione. Dopo la compilazione delle app lo script
  ferma i demoni di Gradle e Kotlin: con quella memoria occupata l'emulatore cadeva con Xonos che
  suonava. Un emulatore caduto non blocca più la corsa: adb si arrende dopo 3 minuti, il log di ogni
  scatto resta in `stream.txt`, gli scatti si pubblicano comunque. Dopo una caduta gli scatti che seguono
  sono PNG vuoti (0 byte) e i loro log sono vuoti: vanno rifatti (contare i PNG sopra 1 KB). Xonos dentro
  myCar fa cadere l'emulatore ogni tanto: meglio in una corsa a parte (`shots: xonos-inside xonos-side`).
  Con `xonos_server` (e `xonos_ref`) `build-apps.sh` costruisce anche il pacchetto di Music Assistant per
  il telefono che Xonos scarica su richiesta (modulo `:server` + `make_payload.py` di Xonos, solo x86_64:
  Python 3.13 sul runner, FFmpeg e librespot compilati con NDK e Rust, una ventina di minuti), firmato
  con una chiave usa e getta che firma anche la build di Xonos (Xonos usa solo un pacchetto firmato come
  lui), in `third-party/xonos-server/`; lo scatto lo serve all'emulatore con un server HTTP sul runner
  (`10.0.2.2:8000`, file `files/server-package-url` di Xonos). Lo scatto `phone-xonos` prova Xonos
  integrato sul Music Assistant del telefono: `ServerCheckActivity --ez xonos true` di Xonos scarica il
  pacchetto e prepara server, account e album di toni, poi myCar con `--es maUrl http://127.0.0.1:8095` e
  il token di Xonos; lo scatto parte con Xonos fermo, che il ponte risveglia.
  Impostazioni del telefono: `phone-settings` (l'anteprima dell'auto in cima, aperta e chiusa, il gruppo
  "Car screen" con la finestra di una scelta, una pagina della Home aperta coi formati e cosa può andare in
  una parte, e "Voice"); `phone-music` (la sezione Musica, l'accesso a Spotify, "Add a service"; dentro le pagine d'accesso di
  Spotify e Deezer `.github/signin-probe.py` guarda, attraverso il socket devtools della build debug
  inoltrato con adb, altezze di html/body/main e se un tocco al centro del primo campo lo raggiunge:
  "sign-in field: reachable"). Servizi
  musicali web (`--es webMusic <id>`, `--ez webMusicPlay true`, `--es webMusicSearch <parole>`, solo debug):
  `music-<servizio>` (pagina, user agent, Widevine, Media Session nel log), `music-spotify-800x480`,
  `music-<servizio>-play`, `music-<servizio>-search`, `music-grid`.
  0.7.4: `portrait-*` (schermi verticali, `shots: portrait`), `charging-no-car` (la scheda della carica su
  Home: Home scorre solo sopra la scheda in fondo, il trascinamento parte dalla scheda di stato),
  `setup-phone/04b-04d` (il passo dell'audio).
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
  Dalla 0.6 le impostazioni del telefono sono in gruppi: lo script apre Plancia per "Save and test" e Musica per
  "Use the Xonos app's account". Prima non li trovava ("not found on screen") e il ponte non veniva provato, pur
  finendo in verde: guardare sempre le righe "At home:" e "Ponte per" nel log del job. Verificato di nuovo (0.7.3,
  Android 17, `tailscale-out`, Xonos dal tag `v0.1.55`): "Ponte per io.github.sirionero.mycar verso umbrel:8123" e
  "verso Music Assistant (porta 8095)", "At home: Home Assistant 2026.10.4, 17 entities" e "At home: Music
  Assistant 2.10.8, playing as myCar".
- Screenshot del README (`docs/screenshots/`): dagli scatti dell'anteprima di `main` (non di un
  branch: devono mostrare la versione che si rilascia), coprendo il badge DEBUG BUILD in basso a
  destra. Mai scatti con la mappa ancora nera o col segnaposto "Map" del player di debug: del
  player si mostra solo il suo pannello. In cima c'è `home.png`, da `split/` (mappa vera e
  lettore): lì il badge si copre specchiando il pannello del lettore, che è simmetrico; sugli
  sfondi con linee (Waves) si riflette la parte appena a sinistra del badge, perché copiarla
  lascerebbe un gradino nell'onda. Gli altri: `launcher.png` da `motion-waves/`, `call.png` da
  `call/`, `settings.png` da `settings/` (il terzo scatto), `wallpapers.png` da `wallpapers/` (lì il
  badge non c'è); tutti 1600×600, ridotti da 1920×720. Rifatti per la 0.6.8 con la Car preview
  lanciata a mano su `main`, `shots` = `vela split motion-waves wallpapers call settings`: `vela`
  prima di `split`, perché con Vela appena installata la mappa era ancora nera dopo 15 s. Il
  pannello del lettore piccolo a 1920×720 è simmetrico attorno a x = 1635 (il seek va da 1384 a 1886).

## Rilasci

- Versione in `app/build.gradle.kts` (`versionCode`, `versionName` di default), novità in
  `res/values/strings_changelog.xml` (un `string-array` `changelog_<versionCode>` per versione, scritto
  anche nelle 8 cartelle `values-xx`, più la riga `Release(code, nome, R.array.changelog_<code>)` in
  `Changelog.kt`: schermata "Novità" sull'auto e sul telefono) e in `.github/release-notes.md` (testo
  del rilascio, in inglese). Dalla 0.7.0 le novità si scrivono anche in **piratesco** (richiesta
  dell'utente: col piratesco scelto, anche le descrizioni dei prossimi aggiornamenti sono pirata, più
  colorite, ogni termine un emblema della libertà): lo stesso `changelog_<versionCode>` in ognuna delle
  cartelle `values-<lingua>-rXP` (vedi "Il piratesco").
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
| radice | Protocollo AA: `MyCarRunner` (sessione, canali, video/audio), `SessionService` (servizio in primo piano del viaggio), `Tailnet` (Tailscale/VPN per i messaggi di Plancia e Xonos), `Framing`, `Protobuf`, `PhoneTls`, `ServiceDiscovery`, `CertProvider` / `CertDownloader`, `VideoSource` (encoder), `ScreenProjector` (VirtualDisplay + `Presentation` con Compose → encoder → auto), `MyCarActivity` (possiede la sessione USB/wireless), `PermissionActivity` (la domanda di Android sul telefono per un tocco sull'auto), `MyCarLog`, `AppLanguage` (la lingua di myCar) e `Words` (i suoi testi fuori dalle schermate), `MyCarApplication` |
| `car/` | **UI mostrata sull'auto**: `CarUi` (root: tema, navigazione, overlay, transizioni), `CarDock` (dock a sinistra), `CarHomeScreen` (mappa + player), `HomePager` / `HomePages` / `HomeWidgets` (pagine della Home ai lati della mappa, widget meteo e agenda), `GlassPlayer` (player accanto alla mappa), `CarLauncher` (griglia app a pagine), `PhoneAssistants` (assistente vocale a scelta), `AssistantLanguage` + frasari (l'assistente nella lingua di myCar), `AssistantBrain` (quello che non è un comando va a Home Assistant), `ReplyWords` / `PlayOrHouse` / `WholePhrases` (regole pure dell'assistente: risposte nel conto alla rovescia, «metti» per la casa o per la musica, domande intere), `WallpaperPicker`, `MediaScreen`, `PhoneDialerScreen` / `PhoneRecents` (Telefono: Recenti, contatti, tastierino), `QuickReplyTexts` (risposte rapide nella lingua di myCar), `CallOverlay`, `CarDashboardScreen`, `CarSettingsScreen`, `PlanciaApp` / `PlanciaWeb` / `PlanciaScripts` (Plancia integrata), `CinemaApp` / `CinemaShelves` / `CinemaPlayer` / `CinemaContinue` (Cinema in auto), `CarDriving` (stato di guida detto dall'auto, per il video), `CarWebContext` (il contesto di ogni WebView sullo schermo dell'auto), `WebSelect` (menu a tendina delle pagine), `AppLinks` (link alle app nelle pagine), `TemplateBack` / `BackCloses` (tasto Indietro dell'auto), `WebMusicApp` / `WebMusicPage` / `WebMusicScripts` (i lettori web dei servizi musicali), `CarClipboard` (appunti tra telefono e auto nella tastiera dell'auto), `CarNavigator` (stack schermate `CarScreen`), template Car App Library (`Template*`) |
| `car/theme/` | Design system attuale dei controlli: `GsTheme` / `ControlLook` (temi Flat/Tactile/Keycap), `GsControls` (`GsButton`, `GsIconButton`, `gsColors`), `GsSeekBar`, `GsSwitch` |
| `ui/` | **UI sul telefono**: onboarding (`IntroScreen` col veliero `PirateShip`, `SetupScreen`; le vecchie `CertSetupScreen` / `SetupGuideScreen`), `UpdateSplash` (la bandiera dopo ogni aggiornamento), `HomeScreen`, `SettingsScreen`, `LanguageSettings` (la lingua), `IntroBadges` (i distintivi animati delle pagine dell'introduzione della 0.7 e la fila dei servizi musicali), `CarScreenSettings` (le impostazioni dell'auto sul telefono, coi pezzi `Pick`/`PickRow`/`PickDialog`/`SwitchRow`), `CarPagesSettings` (le pagine accanto alla mappa dal telefono), `AssistantSettings` (l'assistente vocale, in Voce), `MusicSettings` (la sezione Musica: Xonos, Spotify e gli altri servizi), `PlanciaSettings` (account di Home Assistant per Plancia), `XonosSettings` (account di Music Assistant per Xonos, nella sezione Musica), `CinemaSettings` (siti di Cinema e "Video only when parked"), `MyCarTheme` (Material You), `AboutLinks` (link, donazioni, avvisi di licenza) |
| `host/` | Host Car App Library per app di terze parti (`CarAppConnection`, `KnownApps`) |
| `cinema/` | Cinema: `Cinema` (i siti, "solo a macchina ferma", `MORE` suggeriti), `SiteIcons` (l'icona che ogni sito dà di sé, scaricata e tenuta sul telefono), `PhoneVideos` (video del telefono), `TvLists` + `M3u` (liste IPTV), `ChannelLogos` |
| `home/` | Home Assistant di Plancia integrata: `HomeAssistant` (account, collegamento per tutto il viaggio, plance, import dall'app Plancia), `HaClient` (WebSocket, OkHttp), `Panels` (plance e titoli), `Assist` + `AssistPlan` (le domande all'assistente vocale di Home Assistant) |
| `audio/` | Audio verso l'auto (`AudioLink`, cattura `AudioCaptureService`, `DirectFeed` per la musica di myCar stesso, `CarMic`) |
| `bridge/` | Il ponte verso le app Plancia e Xonos per myCar lasciata fuori da Tailscale: `AppBridge` (legame e richiesta di una connessione), `Bridges` (quali connessioni passano dal ponte), `BridgedSockets` (`BridgeDns`, `BridgeSocketFactory` per OkHttp), `BridgeProxy` (la pagina di Plancia: proxy SOCKS5 nel WebView) |
| `webmusic/` | Servizi musicali web dentro myCar: `WebMusic` (catalogo, interruttori), `WebMusicMediaService` (lettore Media3 per servizio), `WebMusicPlayer`, `WebPlayerState`, `WebMusicSignIn` (accesso sul telefono) |
| `xonos/` | Xonos integrato: `Xonos` (account, connessione, libreria), `XonosAccount` (import dall'app Xonos), il motore di Xonos (`data/`, `sendspin/`, `playback/`: Music Assistant, Sendspin, lettore Media3 `PlaybackService`), `sendspin/CarLinkOutput` (uscita dritta verso l'auto) |
| `call/` | Chiamate: `CarCalls` (stato da `TelephonyCallback` + numero dal broadcast `PHONE_STATE`; risponde/rifiuta/chiude con `TelecomManager`, serve `ANSWER_PHONE_CALLS`), `MyCarCallScreening` (filtro facoltativo: trattiene le chiamate da numeri fuori rubrica solo con l'auto collegata; ignora quelle in uscita) |
| `media/`, `notify/`, `speech/`, `mirror/`, `wireless/`, `update/`, `stats/` | Media (`MusicAsks`: conta ciò che si chiede alla musica, per la pausa rimandata dell'assistente), notifiche, voce (sherpa-onnx), mirroring, AA wireless (`WirelessLink`; collegamento da solo: `WirelessAuto`, `CarPresenceService`), auto-aggiornamento da GitHub, statistiche d'uso opt-in |

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
  **Dimensione del lettore** (0.6.8, richiesta dell'utente; chiave `car_player_size`, "Size of the player
  beside the map" in auto e sul telefono): Piccola (com'era), Media 1,1, Grande 1,2, Molto grande 1,4 (0.6.9,
  `PLAYER_EXTRA`, scatti `split-extra*`; `sideScaleFor` in `car/SideScale.kt` + `SideScaleTest` la limita a quanto
  ci sta: la mappa resta almeno `MAP_PANE_MIN_EDGE` + 24 dp e la colonna alta abbastanza; la X della colonna sopra
  il vetro). Il lato accanto alla
  mappa ha la densità moltiplicata per quel valore e prende `MAP_SHARE`(3,1)/dimensione − 1 parti di mappa
  (`CarHomeScreen`); solo per la musica (lettore, testo): un'app con template o Plancia resta com'è.
  Anteprima `--es playerSize 1.1|1.2`, cartelle `split-medium/`, `split-large/`, `split-large-800x480/`.
  Una scelta nuova per "Accanto alla mappa" riapre la colonna chiusa con la X (`showSidePane`); la X del
  testo riporta il lettore (`showSideControls`).
- **Autoplay** (chiave `car_autoplay`) acceso di default dalla 0.6.9 (richiesta dell'utente); chi l'ha spento lo trova spento.
  Dalla 0.7.2 (log del 3 novembre: con la strada Bluetooth salvata per l'auto e il suo A2DP caduto col reset
  dell'auto, al ricollegamento Spotify Web suonava 27 s dall'altoparlante in tasca) sulla strada Bluetooth aspetta
  un'uscita che non sia il telefono (`CarAudioRoute.bluetoothOut`: A2DP, LE Audio, cavo, USB, AUX, apparecchi
  acustici, seguiti con un `AudioDeviceCallback` dall'immagine alla fine del viaggio; vero fuori da un viaggio), al
  massimo un minuto (`AUTOPLAY_BLUETOOTH_MS`, guardato per primo a ogni chiamata: il ritorno del Bluetooth dopo il
  minuto non fa suonare). Una richiesta del guidatore alla musica dopo che l'app è collegata (`MusicAsks`: ▶, pausa,
  anche a voce, un brano; il segno si prende alla prima `autoplayIfDue`, a READY) lascia stare l'Autoplay; con la
  musica già in `STATE_PLAYING` l'Autoplay è fatto (`AutoplayWhenReady` lo guarda). Due revisioni indipendenti: la
  prima ha trovato sette punti (limite mai applicato, ▶ durante il collegamento, pausa a voce, AUX, pausa nella
  pagina), tutti corretti. Un'altra revisione (scettici) quattro, corretti: l'Autoplay della scelta del viaggio
  (`autoplayAutoPicked`) non suona sopra l'assistente su (fase non IDLE: aspetta, e `AutoplayWhenReady` lo richiama
  quando si chiude); Cinema aperto lo lascia stare (`CinemaMusic.opened` → `CarServices.dropAutoplay`); il passaggio
  all'app Xonos (`xonosAppTakesOver`) lo tiene del viaggio (`autoplayPending && autoplayAutoPicked`: il lettore di
  Xonos azzera `mediaAutoPicked`) e nell'anteprima sul telefono (`phonePreview`) non fa suonare da solo.
- **Mini lettore sulla mappa intera** (0.6.9, richiesta dell'utente: "una nuvoletta piccola, spostabile tenendola
  premuta, con la locandina, indietro, pausa e avanti, che non occupi la mappa e si possa nascondere";
  `car/MapMiniPlayer.kt`, regola pura `car/MiniPlace.kt` + `MiniPlaceTest`, chiavi `car_mini_player` e
  `car_mini_player_at`): con la colonna della musica chiusa (X, brano del dock, Close di un'app accanto) e la
  musica accesa, una pillola 276×64 dp sulla mappa; non in pan mode, solo nella pagina della mappa in vista,
  mai durante la colonna riservata d'avvio, né sotto un template che copre la mappa (`isContentTemplate`: la
  manopola la raggiungerebbe sotto l'elenco). Si muove fuori dalla striscia dei puntini delle pagine
  (`MINI_DOTS_STRIP` 60 dp); finché il guidatore non la sposta la partenza la calcola `MiniPlace.start`: in
  basso a destra sulle mappe grandi, sulle piccole (800x480 a Large/Extra large) a sinistra dei tasti della
  mappa ma mai sotto la scheda a sinistra (42% della mappa, almeno 244 dp); dove accanto alla scheda non ci sta
  (800x480 a Extra large: 173 dp per 276) si disegna in scala (`MiniPlace.fitScale`, mai sotto 0,6). Lasciata
  sotto la riga delle azioni dell'app o accanto ai tasti della mappa (che le stanno sopra: Pausa diventava
  Search) si sposta appena fuori al rilascio (`MiniPlace.clearOfButtons`, 0.7.0). Scatti `mini-large-800x480`,
  `mini-xl-800x480` (`--es carScale`). Sta nello slot `overMap` di `CarAppStage`, fra la
  superficie e la chrome del template: schede e tasti dell'app restano sopra, la superficie non cambia, ogni
  tocco fuori dalla pillola va alla mappa. Gesto nostro sul passaggio Initial (`dragAfterHold`): tocco breve al
  tasto; uno scorrimento prima della pressione lunga preso e lasciato cadere (niente pagina); dopo la pressione
  lunga solo un movimento oltre lo slop la sposta (un tocco lento resta un tocco). Posto salvato come frazione
  della mappa al rilascio. La X la nasconde per il viaggio (`CarNavigator.miniPlayerHidden`, finché lo schermo
  dell'auto non si rifà); la riportano brano del dock, piede del dock, nuova scelta accanto alla mappa e
  l'impostazione riaccesa. Copertina = lettore a tutto schermo. Niente barra d'avanzamento (nessun fotogramma).
  Anteprima: tocchi `x,y,drag,x2,y2`, `--es miniAt x,y`, `--ez miniPlayer false`, `--es carTheme LIGHT`, scatti
  `mini*`. Verificato (0.6.9): trascinamento al posto previsto, tocco lento, X e avviso, tema chiaro, 800x480.
- **App nascoste** (0.6.8, richiesta dell'utente: "nascondere app dal menu"; `car/HiddenApps.kt`, chiave
  `car_hidden_apps`): nel menu di un'app (tenerla premuta, griglia o dock) "Hide from the app grid" /
  "Show on the app grid"; tornano da Impostazioni dell'auto > "Hidden apps" (con Show) o dal telefono
  (Schermo dell'auto > "Apps on the car's app grid", elenco a spunte, `ui/CarGridSettings.kt`). Gli id
  sono quelli delle icone fissate (`componentId`) e valgono anche per il gemello Plancia/Xonos
  (`hiddenAsShown`). Settings non si nasconde (`canHide`). Escono solo dalla griglia: fissata nel dock
  un'app resta lì, la voce la apre ancora; spostare un'app salva l'ordine intero, così una nascosta tiene
  il suo posto. Anteprima: tocchi "x,y,long" (900 ms), `--es hideApps`, `--ez hiddenApps true`, cartelle
  `grid-hide/`, `hidden-apps/`, `hidden-apps-none/`.
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
  copiati, senza le schermate del telefono di Xonos; le correzioni fatte là vanno portate qui), nella
  lingua di myCar come il resto (anche Music Assistant la riceve, `AppLanguage.maLocale`), lettore "myCar" in Music Assistant, servizio Media3
  `PlaybackService` e provider delle copertine `${applicationId}.xonos.art` non esportati. In auto è
  una delle app musicali (`MediaCatalog` lo aggiunge solo se configurato, e allora toglie l'app Xonos;
  la scelta "accanto alla mappa" e l'ultima app musicale salvate con l'app passano a lui:
  `withXonosInside`, `CarServices.xonosFor`). Si configura sul telefono (`ui/XonosSettings.kt`:
  indirizzo, utente e password → un token suo, la password non si tiene) o dall'app Xonos (da 0.1.39:
  `content://it.xonos.ma.account`, metodo `account`, risponde solo a myCar firmata col suo
  certificato), da sola a ogni viaggio finché myCar non ha un account (`XonosAccount.adoptIfUnset`).
  Dalla 0.6.6 sta nella sezione Musica del telefono con un interruttore (vedi "Musica: i servizi web").
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
  **Motore di Xonos portato: v0.1.55** (0.7.3, branch `feature/xonos-0.1.55`; il repository di Xonos ora si
  chiama anche `SirioNero/MusicAssistantServer-on_Android`, tag `v0.1.55` = commit `568041a`). Confronto file
  per file di `data/`, `sendspin/`, `playback/` con `app/src/main/java/it/xonos/ma/{data,sendspin,playback}` di
  Xonos: identici a parte il package Account, ArtCache, MaClient, Model, SkipSender, Menu, AndroidAudio e i nuovi
  `Logged`, `SkipTrace`, `TokenInfo`, `SoloistUnlock`, `SkipPlan`; portati tenendo le parti di myCar Library
  (`lyrics(uri, timeoutMs)`), MaRepository (ponte, `locale()`), AudioEngine (`reroute()`), SendspinClient
  (`start()` riparte se l'audio non è READY), MaPlayer (`pauseWanted`, `heldByCall`, `onUserPause`,
  `pauseFromSystem(transient)`: le pause del sistema passano da `pause()` con `SystemPause` CALL/OTHER, così una
  telefonata tiene la ripresa dopo un calo e non conta come pausa del guidatore), PlaybackService (uscita
  diretta nel canale dell'auto, focus e chiamate, `linkGone`, notifica che apre `MyCarActivity`). Restano in
  Xonos: AccountProvider, MyCarBridge e ServerKeeper (lato app), PhoneServer e `server/`, Devices, Services,
  PlaylistRemover, ScreenCache, il rientro con la password su BAD_TOKEN (myCar non ha la password: c'è "Use the
  Xonos app's account"). Il Sendspin cifrato non c'è nemmeno in Xonos. Quello che conta in myCar:
  - **Logcat**: `Logged.details = BuildConfig.DEBUG` nell'init di `Xonos`; nella release il motore scrive solo
    la forma di id, uri ed errori (`Logged.id`, `Logged.error`, `Logged.trace`), mai titoli, ricerche, nomi,
    indirizzi, token o testi d'errore del server; il JSON rotto di `MaClient` non va nel log.
  - **Server sul telefono** (`Xonos.onPhoneServer`: indirizzo di casa su 127.0.0.1, `Bridges.onThePhone`): dalla
    0.1.55 l'app Xonos lo ferma quando nessuno lo usa più (`ServerKeeper`: ogni bisogno lo tiene acceso ancora un
    po' dopo averlo lasciato, MYCAR 1 minuto dallo stacco del ponte, CAR e ACCOUNT 2, SCREEN 3, PLAYING 8), e lo
    tiene acceso finché myCar tiene legato il ponte (`MyCarBridge.onCreate` → `keeper.hold(MYCAR)`). myCar lo tiene legato per il
    viaggio: `CarServices` tiene `repo.acquire()`, ogni tentativo passa dal ponte e rimanda di 5 minuti lo
    stacco; su NO_ANSWER l'app lo riavvia (`keeper.touch`) e myCar riprova da sola (2-20 s, e
    `Xonos.wakePhoneServer()` riprova subito, al massimo ogni 2 s). `MaPlayer.wake()` dà vero solo con quel
    server, l'app Xonos col ponte e nessun rifiuto definitivo: play, salti e scelte aspettano fino a 60 s
    (`PHONE_READY_TIMEOUT_MS`), il menu dell'auto 45 s (`PHONE_BROWSE_WAIT_MS`), `play_media` 180 s
    (`Library.playTimeoutMs`, 60 s con gli altri). Una pausa durante l'attesa di un play lo annulla senza errori
    (`pauses`); un salto o una scelta in attesa partono appena il server risponde e poi si fermano (`pauseAfterSkip`
    e `pauseAfterChoice` → stop; fino alla 0.7.2 e in Xonos 0.1.55 la pausa durante una scelta si perdeva, perché
    `pauseFromSystem()` leggeva `playWhenReady` già falso dal segnaposto di Media3). Uno stop durante l'attesa non li
    ferma, e il suo errore (Music Assistant non collegato) va all'auto (`onFailure`). Col rifiuto già noto
    (`phoneServerFailed()`) anche le scelte falliscono subito (`awaitReady`), e dopo un tentativo fallito del
    viaggio l'app Xonos prende il posto di Xonos dentro myCar (`InsideOrApp`, `phoneRefused`). `phoneServerFailed()` = `Xonos.phoneServerRefused()`: un rifiuto che non
    passa da solo di Music Assistant (token, versione, permessi…) o dell'app Xonos (`AppBridge.refusedForGood`:
    NOT_MYCAR / NOT_OURS per quell'host, l'app passata a un altro Music Assistant o senza il suo).
  - **Salti**: `SkipPlan` (avanti senza l'indice di Media3, cioè con la finestra ridotta al brano in corso subito
    dopo una coda nuova, va con play_index invece di next, che Music Assistant fa partire dopo 1 s, se ripeti è
    spento; con l'indice avanti e indietro andavano già con play_index nella 0.7.2; con ripeti tutto avanti
    dall'ultimo brano va al primo, prima al brano current−3); `SkipSender` coi servizi a un flusso alla volta
    raccoglie i tocchi per 400 ms, 250 col server sul telefono (con gli altri salta subito); righe "Salto:"
    (`SkipTrace`).
  - **Correzioni delle revisioni** (due giri: tre revisori indipendenti sulla port, poi tre su correzioni, testi e
    CI; ogni punto verificato da uno scettico): con l'audio tramite myCar l'auto che se ne va (`CarAudio.linkGone`)
    chiama anche `forgetDrop()`, come il Bluetooth che si stacca, così un calo di rete visto dopo non fa ripartire la
    musica dall'altoparlante del telefono al ritorno della rete, e toglie la ripresa a fine telefonata
    (`resumeAfterInterruption`, col volume della chiamata rimesso: `setDuck(1f)`); i rifiuti NOT_OURS/NOT_MYCAR del
    ponte non si aspettano più (sopra); la pausa durante una scelta vale (sopra); ogni brano pubblicato ha una copertina,
    l'icona dei brani di Xonos quando manca (`artwork.uriFor(...) ?: artwork.iconUri("track")` in `PlaybackService`, come
    il menu in `BrowseTree`): Media3 1.11.1 (`MediaSessionLegacyStub.updateMetadataIfChanged`, verificato nel bytecode)
    per i controller vecchi come l'auto carica la copertina e annulla il caricamento di prima solo se il brano nuovo ne
    ha una; col segnaposto di una scelta (con copertina) seguito da un brano senza, l'immagine finita dopo rimetteva i
    metadati della voce scelta fino al brano dopo ("Preview Album" per oltre 15 s negli scatti `xonos-lyrics` e
    `bridge-xonos`, 2 volte su 5; il meccanismo c'era già nella 0.7.2: stesso Media3, stesso segnaposto), e lo stesso
    col brano di prima dopo due "avanti" veloci. Un primo tentativo toglieva la copertina al segnaposto
    (`getPlaceholderMediaItemData`): copriva solo quella strada, e toglieva la copertina della voce scelta durante la
    preparazione (con Soloist circa 7 s). Una telefonata cominciata e finita durante una scelta non lascia la musica
    ferma (`resumeFromSystem` toglie `pauseAfterChoice`); finché una pausa è voluta (`pauseWanted`, non per una
    telefonata) la musica partita non conta come ascolto recente (`apply`) e un calo di rete non si segna (`onLost`). Righe "Scelta:" (tempi
    di una scelta) e "Lettore:" (cosa vede l'auto) nel registro. **Da portare in Xonos**: la pausa durante una scelta
    (`pauseAfterChoice` → stop, `resumeFromSystem`, `apply`/`onLost` con `pauseWanted`), `awaitReady` che non aspetta col
    server fallito, la copertina per ogni brano (l'icona quando manca: Gearslip è un controller vecchio anche lui), e per il Bluetooth la ripresa a fine telefonata rimandata finché non
    torna un'uscita esterna (non cancellata: alcune auto staccano l'A2DP durante la chiamata).
  - Prove: il pacchetto `xonos` compilato contro android-all 17 di Robolectric e Media3 1.11.1 (scratchpad,
    stub del resto di myCar) e i banchi JVM di Xonos sul motore di myCar (SkipCheck, LoggedCheck, DecoderCheck,
    SilenceCheck, StartCheck, SlowListCheck) passano; quelli con un Music Assistant vero (MenuCheck, PlayCheck,
    TokenCheck, HomeCheck) vogliono Docker. Docker nella sessione parte (`dockerd &`, mirror `mirror.gcr.io` in
    `/etc/docker/daemon.json`): Music Assistant 2.10.8 si costruisce dal sorgente con `tools/ma-test` di Xonos
    (riporta "0.0.0": per le prove con `modernWire` la versione va scritta nel suo `METADATA`). Lì
    `harness.ReplayCheckKt` (banco JVM della scratchpad) misura play_media dello stesso album mentre suona: ~100 ms,
    con e senza `modernWire`.

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
- **Musica: i servizi web dentro myCar** (0.6.6; richiesta dell'utente: l'app Spotify non lascia
  catturare il suo audio e sulla Tayron il Bluetooth porta solo le chiamate, "fai l'app" con Spotify Web
  preinstallato, poi "nel menu fai la sezione Music"; `webmusic/`, `car/WebMusicPage.kt`,
  `car/WebMusicScripts.kt`, `car/WebMusicApp.kt`, `ui/MusicSettings.kt`). Sul telefono la sezione
  **Musica** (`SettingsPage.MUSIC`, dopo "Car screen", al posto del gruppo Xonos): Xonos con un
  interruttore (chiave `xonos_in_the_car`; spento di predefinito ma acceso alla prima apertura se Xonos è
  configurato in myCar o l'app Xonos è installata, così chi lo usava non lo perde; spento, in auto non c'è
  né Xonos dentro myCar né l'app Xonos e il viaggio non si collega a Music Assistant; cambiato a viaggio
  iniziato: `CarServices.xonosSwitched`), Spotify con un interruttore e "Add a service" (YouTube Music,
  Apple Music, Amazon Music, Deezer, TIDAL, SoundCloud, Qobuz; chiavi `web_music_added`, `web_music_on`;
  tutto spento di predefinito). Acceso, un servizio ha "Sign in on the phone" (`WebMusicSignInActivity`:
  lo stesso motore web dell'auto, cookie scritti subito) e "Sign out" (cookie dei suoi domini, storage
  delle sue origini; per YouTube Music anche google.com, quindi anche YouTube in Cinema), e in auto è
  un'app musicale come le altre: riquadro "<nome> · Web" con l'icona del sito (`SiteIcons`), lettore di
  myCar, dock, tasti del volante, assistente, accanto alla mappa; spento non c'è, anche a viaggio
  iniziato. **Una pagina per servizio per tutto il viaggio** (`WebMusicPages`: fatta alla prima apertura
  o al primo tasto, anche fuori vista, mai in pausa; va via con lo schermo dell'auto, quindi con
  condivisione schermo ed encoder nuovo, come Plancia), user agent da computer, larga quanto il suo posto
  (minimo 800 px css), nera prima di disegnare; di myCar solo un piccolo Indietro in alto a sinistra
  (prima indietro nella pagina; per lui la barra di Spotify, YouTube Music, Apple Music e Amazon Music si
  sposta di 52 px), la tastiera dell'auto e, per un video a schermo intero in movimento, la copertura di
  Cinema. **Lettore**: per servizio un `MediaLibraryService` Media3 non esportato e senza notifica
  (`webmusic/WebMusicMediaService.kt`, una classe per servizio perché l'auto distingue le app per
  componente) con un `SimpleBasePlayer` che mostra quello che la pagina dice e le manda i tasti
  (`WebMusicPlayer`); senza titolo il brano ha il nome del servizio. Nella pagina uno script dall'inizio
  (`addDocumentStartJavaScript` + `addWebMessageListener` "MyCarMusic", solo sulle origini del servizio):
  **il WebView di Android non ha `navigator.mediaSession`** (visto in CI), quindi myCar ne mette uno
  sostitutivo prima degli script della pagina; dove la pagina non dice niente, la barra del servizio
  (Spotify: `data-testid`) o i suoi elementi audio. "Metti X": la sessione non offre la ricerca nella
  libreria (Media3 non risponderebbe mai e `playBestMatch` aspettava), arriva come `playFromSearch` →
  `handleSetMediaItems`; la pagina apre la ricerca del servizio e preme il Play del primo risultato
  (`firstPlay`, segni indipendenti dalla lingua, poi `ANY_PLAY`); myCar aspetta 15 s
  (`CarMedia.WEB_SEARCH_WAIT_MS`). Una pagina che suona diventa la musica dell'auto e mette in pausa le
  altre e Xonos. Audio catturato come Cinema (`CarAudio.captureNow`). Nel log niente parole cercate né
  titoli delle pagine (solo nella build di prova). **Appunti** (`car/CarClipboard.kt`): Incolla e Copia
  nella tastiera dell'auto in tutte le pagine (browser, Cinema, servizi); Android lascia leggere gli
  appunti solo all'app col fuoco, quindi una schermata trasparente sul display del telefono
  (`CarClipboardActivity`) li legge e si chiude; bloccato o negato: avviso in auto e riga nel log.
  Verificato in CI senza account: le otto pagine col user agent da computer e Widevine
  (`com.widevine.alpha`); "metti lofi" su YouTube Music arriva al lettore accanto alla mappa (copertina,
  comandi, dock); su SoundCloud e Spotify la ricerca arriva al Play, poi il servizio chiede di accedere;
  TIDAL e YouTube Music fermano il runner con un controllo anti-bot. Avvertenze: l'accesso Google nel
  WebView può essere rifiutato (YouTube Music senza account); Apple Music, Amazon Music, TIDAL e Qobuz
  vogliono un abbonamento; restrizioni per paese; i popup (`window.open`) non hanno una gestione loro.
  Non provato: con un account, l'audio verso l'auto, gli appunti sul telefono vero, in auto.
  0.6.8, "Nascondi l'app %s del telefono" (richiesta dell'utente: "così non ci saranno due Spotify"): ogni
  servizio ha `appPackage` (Spotify `com.spotify.music`, …); con il servizio acceso e l'app installata, una
  spunta nella sua sezione (chiave `web_music_hide_app`, nessuna di predefinito). Spuntata, l'app del
  telefono non c'è nella griglia né negli elenchi "Accanto alla mappa" (`WebMusic.replacing`,
  `rememberReplacedByWeb`), e le sue icone fissate, la scelta accanto alla mappa e l'ultima musica passano
  al servizio web (`pinShown` → `withWebMusicInside`, `CarServices.webFor`); se a inizio viaggio suona
  l'app del telefono, va in pausa e suona il servizio web. Dopo l'accesso sul telefono, tornati sul lettore
  del servizio (accesso su un altro sito), la barra ha "Done".
  0.6.7: l'accesso a Spotify sul telefono era nero (vedi "WebView dentro Compose"); Incolla chiudeva la
  tastiera perché la schermata trasparente degli appunti toglie il fuoco alla pagina dell'auto e il campo
  mandava `focusout`: ora il campo lasciato si dice dopo 250 ms e solo se nessun campo è attivo
  (`FOCUS_TRACKING_JS`). Accesso fatto sul telefono a viaggio in corso: la pagina in auto si ricarica
  (`WebMusicPlayer.signedIn`); pagina creata senza rete: il primo tasto la ricarica.
- **Schermo condiviso**: con la musica accesa il player sta da solo accanto alla mappa
  (`CarNavigator.sidePanel` parte da `CONTROLS`), come nella vecchia barra in basso; la X lo
  chiude per il viaggio, un tocco sul brano nel dock lo riporta o lo toglie, tenerlo premuto apre
  il player a tutto schermo. Non nasconderlo dietro gesti poco scopribili.
- **Riquadri rialzati** (0.7.4, `design/raised-panels`; richiesta dell'utente: "le plance che si dividono sono
  troppo squadrate, i riquadri siano tipo nuvolette rialzate dallo sfondo sotto"; `car/design/Panels.kt`, valori
  in `MyCarDesign.kt`): negli schermi divisi ogni riquadro è un pannello rialzato sopra lo sfondo
  (`Modifier.raisedPanel()`, `PanelShape`): angoli da 24 dp (`MyCarRadius.Panel`), 8 dp di sfondo fra i pannelli
  e attorno (`MyCarSpace.Panel`), un'ombra corta disegnata solo fuori dal pannello, 2 dp più in basso
  (`MyCarLift`: 8 dp, 0,5 sul tema scuro, 0,2 sul chiaro; niente sfocatura) e un bordo di luce in alto. Dove: la
  Home con qualcosa accanto alla mappa (mappa e colonna; la mappa da sola resta da bordo a bordo; `HomePager`
  disegna lo sfondo anche dietro la pagina della mappa divisa, `mapSplit`) e tutte le parti delle pagine (la
  parte vuota e la copertura della modifica hanno la forma ma non l'ombra). Stessa impaginazione di prima. Il
  pannello della colonna sta alla densità dello schermo, fuori dalla dimensione del lettore (`sideScaleFor`
  riceve lo spazio senza i margini). **Angoli**: la superficie di un'app (SurfaceView) è un livello sotto la
  finestra visto dal buco che la vista apre, e il buco segue il ritaglio arrotondato: niente da ridipingere
  (verificato in CI). Le pagine web sì (`coverCorners`: Plancia e browser), perché con Vulkan il WebView riceve
  solo il rettangolo del ritaglio: sopra i loro angoli `wallpaperOutside` ridipinge lo sfondo allineato allo
  schermo (stesso orologio degli sfondi animati). Il video della copertina sta nel pannello del lettore,
  arrotondato dal suo ritaglio. **Ombra negli angoli**: gradiente fra due cerchi (`RadialGradient` di API 31) a
  tre colori; con il radiale a quattro colori SwiftShader disegnava puntini scuri dove finisce l'ombra e
  l'emulatore della CI cadeva al primo disegno della Home divisa (7 corse su 9; con tre colori una caduta sola
  in 6 corse, la causa non è dimostrata). Con uno sfondo animato i margini fra i pannelli si muovono: più
  fotogrammi da mandare al video. Gli angoli tagliati della mappa (meno di 24 dp) non prendono tocchi.
  **Manopola**: l'anello in un angolo di un pannello prende l'angolo del pannello (`PanelCornersNode`). Scatti
  `split-800x480`, `split-xs-800x480`, `split-1920x1080`, `split-light`, `split-waves`, `split-none`,
  `pages-light`. Per `home.png`: a 1920x720 il pannello del lettore va da x 1347 a 1908 e da y 12 a 708 (asse x
  1627,5); il badge copre anche i 12 px di sfondo a destra e in basso. Annullare la Car preview automatica (un
  push su `design/**` fa partire tutti gli scatti, più di 120 minuti) pubblica comunque un `ci/car-preview`
  vuoto (Publish `if: always()`): i risultati sovrascritti si recuperano con l'API `activity` del ramo (`GET
  /repos/SirioNero/MyCar/activity?ref=refs/heads/ci/car-preview`) e `git fetch origin <sha>`. Da guardare: la
  pillola della chiamata, al suo posto di partenza, su una pagina del guidatore copre l'intestazione della parte
  destra ("Coming up"); si sposta tenendola premuta.
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
- **Strada dell'audio per auto e tasto nel lettore** (0.6.2, richiesta dell'utente): una scelta
  fatta con un'auto collegata (tasto dell'audio nel lettore, `car/SoundRouteButton.kt`, accanto alla
  mappa e a tutto schermo; impostazioni dell'auto o del telefono) resta per quell'auto
  (`audio/CarRoutes.kt`, preferenze `mycar_car_routes`, chiave l'indirizzo Bluetooth dell'auto o, se
  non lo dà, i dati della sua unità) e torna al suo viaggio dopo (`CarSettings.carIdentified`, prima
  che si apra il canale musicale); una scelta sul telefono senza auto vale per tutte e cancella
  quelle per auto. In Automatico un Bluetooth della musica che cade aspetta 10 s, a chiamata finita,
  prima di spostare l'audio (`CarAudioRoute.checkDropped`); trattenuto dall'auto (LOSS_TRANSIENT)
  senza GAIN, dopo 20 s di suono myCar richiede il focus (`AudioLink`, `HELD_SOUND_MS`).
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
  niente. Dalla 0.6.4 la fine della frase in un'altra lingua non è più solo di Kroko, che in italiano
  può stare un secondo senza scrivere mentre il guidatore parla ancora e tagliava la frase: vale quando
  anche il microfono è tornato al rumore dell'auto (`speech/SpeechEnd.kt` + `SpeechEndTest`: il livello
  più basso dalla pressione, che sale piano; non contano i primi 300 ms, la coda del bip e un microfono
  che sale piano, né gli zeri di un microfono dell'auto che parte; "ha parlato" vuol dire 200 ms di
  voce di fila, non qualche colpo sparso),
  al massimo 2,5 s dopo; senza parole di Kroko bastano 1,2 s di quiete dopo aver parlato. Il log dice
  lingua, modello e come è finita la frase (non le parole). Con myCar in una lingua senza il suo modello
  l'assistente ascolta in inglese e in auto lo dice sopra "Listening…" (`assistant_needs_model`, con il
  modello da scaricare). **Comandi e risposte** (`car/AssistantLanguage.kt` + `AssistantItalian/Spanish/French/
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
- **Lingue** (0.6.0, richiesta dell'utente: "tutta l'applicazione, non solo l'assistente", le lingue
  di Xonos): myCar in inglese, italiano, spagnolo, tedesco, francese, portoghese (brasiliano, come
  Xonos), greco, russo e cinese semplificato (`AppLanguage`): quella del telefono finché non se ne
  sceglie una in Impostazioni > Lingua (`ui/LanguageSettings.kt`; l'introduzione della 0.6 aveva anche
  la pagina "La tua lingua.", tolta nella 0.7). Da Android 13 la tiene Android
  (`LocaleManager`, `res/xml/locales_config.xml`: si sceglie anche dalle impostazioni di Android per
  l'app) e vale per tutto il processo senza riavvii (MyCarActivity gestisce il cambio di `locale`); su
  Android 12 la applica myCar (`MyCarApplication` → `applyToProcess`, `wrap` nelle activity,
  `localized` per lo schermo dell'auto, che è un display a sé, `resources` per `Words`), e la
  schermata riparte solo senza un viaggio in corso. **Testi**: tutti in `res/values/strings_*.xml`
  (common, setup, phone, settings, car_home, car_screens, car_apps, service, changelog, xonos,
  language), tradotti in `values-{it,es,de,fr,pt,el,ru,zh}`; nelle composable `stringResource`,
  altrove `Words.get/plural` (mai `context.getString` nel codice nuovo: su Android 12 darebbe la
  lingua del telefono); nei test JVM `TestWords.install()` dà a `Words` l'inglese di res/values.
  Restano in inglese log, report di diagnosi e i valori confrontati o salvati ("Unknown vehicle" di
  MyCarRunner, `BuiltInApp.label` e `CarApp.label` per la voce, accanto a `title`/`shownLabel`,
  `Weather.describe` per l'assistente, `remainingTime` per le risposte rapide inglesi).
  `tools/check_strings.py` (anche in CI, dopo la compilazione): stesse chiavi, segnaposto, plurali
  (ru one/few/many/other, zh solo other), apostrofi, percentuali che aapt prenderebbe per segnaposto,
  chiavi doppie. **Un testo nuovo o cambiato va scritto in tutte e 9 le lingue**, col registro di
  Xonos (it tu, es tú, de du, fr vous, pt você, el e ru di cortesia, zh 你) e i termini già usati
  (glossari dei traduttori nei messaggi dei commit `Lingue: myCar in …`). Bluetooth e il separatore
  degli elenchi da risorse (蓝牙 e "、" in cinese); ore d'arrivo e date dei log nel formato della
  lingua. Fatto in 0.6.0 da agenti in parallelo: 7 parti di estrazione (1433 testi, 25 plurali, 153
  righe di novità) e un traduttore per lingua. Anteprima in CI: scatti `lang-<lingua>` (Home con la
  musica, griglia, impostazioni dell'auto a 1920x720; `lang-<lingua>-800x480`; sul telefono setup,
  Home, impostazioni, aiuto e introduzione in `phone/`, aperti con `--es screen <nome>` della build
  debug), con la lingua messa da `cmd locale set-app-locales`. Da Android 13 Android dà la lingua
  scelta per l'app alle sue activity, non al contesto della `Presentation` sul display dell'auto (gli
  scatti della 0.6.8 lo hanno mostrato: telefono tradotto, auto in inglese): `AppLanguage.localized` e
  `resources` confrontano la lingua delle risorse con quella voluta (`Locale.getDefault()` da Android 13,
  la scelta salvata prima) e, se diverse, la mettono loro.
- **L'assistente in 9 lingue** (0.6.0): segue la lingua di myCar (`VoiceLanguages.phone()` legge
  `Locale.getDefault()`, che Android 13 mette alla lingua scelta per l'app e MyCarApplication su
  Android 12). Frasari nuovi, scritti e provati sul banco JVM (`AssistantLanguage` + frasari +
  `VoiceLanguage` + i test, 49 in tutto): greco (`AssistantGreek.kt`, cortesia; regole tolleranti
  agli accenti e alla ς finale, che Parakeet v3 non scrive; nomi all'accusativo riportati al
  nominativo), russo (`AssistantRussian.kt`, "вы"; nomi e luoghi da dativo/accusativo/genitivo al
  nominativo o a una radice), cinese (`AssistantChinese.kt`: `wordGap` vuoto perché il cinese non ha
  spazi, parole dei comandi anche in caratteri tradizionali, "给妈妈打电话" e "打电话给妈妈").
  `plain()` toglie la punteggiatura cinese e il punto interrogativo greco. Ascolto: Parakeet
  multilingue per greco e russo; **Whisper small** (sherpa-onnx, OpenAI, MIT, 358 MB: encoder 107 +
  decoder 250; base 153 MB è debole sul cinese, medium 902, turbo 989) per il cinese, con la lingua
  detta a Whisper (`modelType = "whisper"`, token `small-tokens.txt`). Contatti e chat si confrontano
  senza accenti (NFD, ς come σ: "Γιώργος" trova "ΓΙΩΡΓΟΣ", "Семён" "Семен"); un nome d'app cinese di
  due caratteri si trova anche a metà. Non provato: il riconoscimento vero in greco, russo e cinese
  (la libreria è solo arm64, le voci sintetiche non bastano a giudicarlo).
- **L'assistente col telefono bloccato: Home Assistant al posto dell'app** (richiesta dell'utente:
  "walk around del limite del telefono sbloccato per far partire l'assistente digitale", suggerendo
  "un ponte stile Xonos"). L'app di un assistente (Claude, Gemini…) col telefono bloccato da PIN non si
  apre da nessun'altra app: il blocco è di Android, sopra il blocco funziona solo un assistente che lo
  prevede (Gemini ha l'opzione, Claude no), e tenere il telefono sbloccato è dei soli agenti di fiducia
  di sistema (Sblocco esteso / Smart Lock: con l'auto come dispositivo attendibile `AssistantUnlock`
  passa già senza chiedere). Un'app ponte ha gli stessi limiti di myCar. Si porta invece il "cervello"
  dentro myCar, che ascolta già col telefono bloccato (microfono dell'auto, riconoscimento sul
  telefono): **Home Assistant Assist** sul collegamento di Plancia del viaggio (e attraverso l'app
  Plancia con Tailscale: il ponte). `car/AssistantBrain.kt`: quello che non è un comando di myCar
  (`AssistantCommands.missed`: `UNKNOWN`, o "open X" senza un'app X: "apri il cancello" è per la casa)
  va a Home Assistant con le parole come dette, e la risposta (già nella lingua del guidatore) si dice
  com'è (`CarAssistant.replyAsSaid`, scartata se nel frattempo il guidatore ha chiuso o richiesto). Una
  domanda entro 5 minuti continua la conversazione (`conversation_id`: "e domani?"). Se Home Assistant
  chiede qualcosa (`continue_conversation`) il microfono non si riapre da solo (dalla 0.6.4, richiesta
  dell'utente: dopo una risposta l'assistente se ne va) e la frase dopo passa prima dai comandi di
  myCar, poi da lui con la conversazione. Home Assistant ha 15 s (`AssistantBrain.ANSWER_MS`), e intanto
  il riquadro scrive "Home Assistant". `home/Assist.kt` + `AssistPlan.kt` (+ `AssistPlanTest`): la pipeline
  preferita se parla la lingua del guidatore, se no un'altra che la parla (`assist_pipeline/run`, dal
  testo: eventi `intent-end` con `intent_output`, `error`, `run-end`, `HaClient.stream`); se nessuna la
  parla, l'agente della preferita chiesto nella lingua (`conversation/process` con `language`):
  l'agente di Home Assistant con la pipeline inglese non capiva una parola d'italiano, chiesto in
  italiano risponde. In Home Assistant si sceglie l'agente (Claude con l'integrazione Anthropic, Gemini,
  ChatGPT, Ollama in casa, o il suo, che comanda la casa): in myCar nessuna chiave. Con "Phone's
  assistant" (o un'app) scelto e il telefono bloccato, se Home Assistant è collegato myCar ascolta da sé
  e lo dice ("The phone is locked: myCar is listening, and Home Assistant answers.") invece di chiedere
  lo sblocco. Sul telefono Impostazioni → Plancia → "Ask Home Assistant in the car" (chiave
  `car_ask_home_assistant`, acceso di predefinito: prima la risposta era "I don't know how to do that
  yet"). Risposte lunghe più piccole su 4 righe (`LONG_REPLY`). Verificato: con Home Assistant 2026.10.4
  in Docker nella sessione cloud (immagine da `mirror.gcr.io/homeassistant/home-assistant`, ghcr.io
  rifiuta i blob) il codice vero di `Assist`/`HaClient` su un banco JVM (stub di `HomeAssistant`,
  `Tailnet`, `MyCarLog`; OkHttp 5.4.0, org.json): "October 29th, 2026", "È giovedì, 29 ottobre 2026",
  "Son las 6 y 14 de la mañana", "Es ist 06:14", "São 6 e 14", "Il est 06:12". Non provato: un agente con
  un modello (serve una chiave), sul telefono vero.
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
- **Introduzione della 0.7** (richiesta dell'utente: "un grande aggiornamento" col veliero, e l'intro che
  presenta le novità dalla 0.6): parte da sola quando `versionName` diventa 0.7.x (`Intro.release`). Pagine
  (`IntroStep`): WELCOME (veliero, etichetta "alpha · smooth sailing", in italiano "a gonfie vele": in ogni
  lingua un modo di dire sul navigare), MUSIC ("Music, built in.": Spotify e gli altri servizi dal loro
  lettore web, accesso col proprio account in Impostazioni > Musica, l'app Spotify del telefono da nascondere;
  sotto scorre la fila dei servizi, `ServiceTicker`, dal catalogo di `WebMusic` più Xonos, senza Apple Music:
  niente nome Apple sulle pagine di myCar), MINI_PLAYER (mini lettore e lettore fino a Molto grande), LYRICS,
  LOCATION (posizione col telefono bloccato in tasca), CAR_ON_PHONE (impostazioni dell'auto, pagine e app
  nascoste dal telefono), VOICE e GRAPHENE come prima. Tolte LANGUAGE, WIRELESS, KNOBS, THEME, APPS (non
  dicono niente di nuovo a chi viene dalla 0.6) coi loro testi e `ui/IntroLanguage.kt`. Distintivi animati
  nuovi in `ui/IntroBadges.kt` (nota che balla, mini lettore spostato sullo schermo, righe del testo che si
  accendono, freccia con gli anelli, telefono con due interruttori), dentro il solito biscotto di
  `BadgeStep`. Titoli di circa dieci lettere per riga (il carattere segue la larghezza): niente parole
  lunghe. Il passo salvato vale solo per la sua release (`step_release`): una pagina lasciata a metà
  dell'intro della 0.6 non fa saltare le prime della 0.7. Scatti: `setup-phone/09-intro-page-2…7` e, per
  lingua, `lang-<lingua>/phone/intro-page-2…7`. Provato col banco Compose Desktop (sopra, "Build"): le
  pagine nuove in inglese, greco, russo, tedesco, francese e cinese stanno anche a 320×640 dp.
- **Il piratesco** (0.7.0, richiesta dell'utente: "un'ulteriore lingua, il piratesco, che funziona in
  tutte le lingue e scrive ogni testo in termini pirateschi, l'emblema della libertà di scelta"): non una
  lingua a sé ma un modo di parlare ciascuna delle 9, la lingua con la regione a uso privato **XP**
  (`it-XP`, `en-XP`…, `AppLanguage.PIRATE`), testi in `res/values-<lingua>-rXP` (inglese `values-en-rXP`,
  cinese `values-zh-rXP`). Un testo che manca lì viene dalla cartella della lingua: si può scrivere a
  pezzi. Non la variante BCP 47 `it-pirate`: aapt2 non costruisce `values-b+it+pirate` ("configuration
  has invalid locale 'it-'"). Impostazioni > Lingua > "Parla da pirata" (`language_pirate`,
  `AppLanguage.choose(…, pirate)`); da Android 13 Android tiene `it-XP` come lingua di myCar, su
  Android 12 la applica myCar (chiave `app_language_pirate`); col piratesco e la lingua del telefono si
  fissa quella in cui è myCar. `AppLanguage.wanted` è un `Locale` (la regione conta per lo schermo
  dell'auto e `Words`); ciò che va col paese (unità automatiche, formato dei numeri di telefono,
  portoghese del Portogallo o del Brasile) prende `AppLanguage.country()`, il paese del telefono quando
  la regione è XP. `tools/check_strings.py` controlla anche le cartelle `-rXP` (chiavi della lingua,
  segnaposto, plurali). Testi scritti da agenti (glossario piratesco per lingua, poi un agente per gruppo
  di file; glossari nella scratchpad della sessione): gergo pirata della lingua, ribelle e colorito ma
  mai volgare (in auto ci sono bambini), stesso registro della lingua, etichette brevi, e i testi di
  sicurezza (video in marcia, chiamate, permessi, errori) seri e chiari come prima. Nomi propri e di
  marchi invariati. Le frasi dell'assistente (codice, non risorse) restano normali. Restano normali
  anche `common_on`/`common_off` (stati degli interruttori), `common_remove`, `common_cancel` e `common_ok`
  (confermano anche una chiamata, la rimozione o la password di un certificato): un testo pirata che li
  nomina usa il loro testo normale. Un testo pirata uguale a quello normale non si tiene (copie vecchie
  quando la lingua cambia). Dopo gli scrittori, un passaggio di coerenza per lingua allinea i nomi citati
  fra virgolette (es. «Usa myCar a bordo» = `settings_use_mycar`) e i termini. Restano normali anche le
  indicazioni di guida dette a voce (`directions_*`).
  **`values-en`** (generata in build da `values/strings*.xml`, task `EnglishStrings` in
  `app/build.gradle.kts`): senza, ogni inglese tranne en-US (en-GB, en-AU, «English» scelto in myCar)
  prendeva `values-en-rXP`, perché Android preferisce una cartella con la lingua a quella senza e la
  regione XP non conta (trovato da una revisione con una replica in C++ della scelta di AOSP; scatto
  `lang-en-GB`). Da Android 13 Android tiene `it-XP`: le impostazioni leggono la scelta da
  `AppLanguage.saved` (la lingua del telefono resta tale), e la voce riceve il paese del telefono, non
  XP (`VoiceLanguages.speakingLocale`). Da Android 13 anche `Resources.getSystem()` ha la lingua scelta per
  myCar (`it-XP`): la lingua del telefono si legge da `LocaleManager.systemLocales` (`AppLanguage.phoneLocale`,
  0.7.1; prima la voce riceveva `it_XP`, "no voice for it_XP" nel log), e `country()` prende il paese del
  telefono anche quando la lingua scelta in myCar non ha regione.
- **Sfondo myCar «Ribelle»** (0.7.0, richiesta dell'utente: "il tema myCar, le onde, la barra del menu
  con effetto glass, e tra le onde un veliero con la bandiera col teschio che sopravvive alle
  intemperie: se va sotto un'onda poi rispunta fuori"): `Motion.Rebel` in `Wallpapers.kt`, disegno in
  `car/design/RebelSea.kt` (le bande di Waves; la nave sulla seconda, in unità sue come il veliero
  dell'intro ma più semplice; ogni 30 s un'onda grande con la schiuma la copre fin quasi alla bandiera,
  poi la nave risale oltre la sua linea e si assesta). Id `mycar`, primo tra gli sfondi in movimento;
  non è il predefinito (ogni fotogramma che cambia costa al video). Il dock resta di vetro sopra di lui
  come con gli altri sfondi. Scatti `motion-mycar`, `motion-mycar-800x480`; provato col banco Compose
  Desktop (`flagbench`, modo `rebel`).
- **Il motto dell'intro della 0.7** (richiesta dell'utente: "un'animazione dopo l'arrivo della nave:
  scegliamo noi le nostre rotte"): issata la bandiera, sul mare si disegna dalla prua una rotta a
  puntini dorati fino a una stella (`PirateShip.drawCourse`, 3,25-4,55 s); sotto l'etichetta della 0.7
  una bussola il cui ago gira e si ferma, e «Scegliamo noi le nostre rotte.» parola per parola
  (`ui/IntroCourse.kt`, `WelcomeCourse`, `intro_course` nelle 9 lingue); sotto, il numero dei bug trovati
  e corretti dalla release prima (`Intro.BUGS_FIXED`, plurale `intro_bugs_fixed`: 220 dalla 0.6.0 alla
  0.7.0, contati commit per commit, nel dubbio come funzione nuova; elenco in `bugcount.tsv` nella
  scratchpad di quella sessione). La pagina dà al testo lo spazio che serve e alla nave quello che resta
  (su un telefono basso la nave è più piccola). Da aggiornare a ogni intro nuova.
- **Seconda caccia della 0.7.0** (richiesta dell'utente: "controlla se trovi altri bug, in merito a crash
  e assistente digitale"): sette cercatori (cuore dell'assistente, comandi, assistenti esterni, crash di
  Compose, crash del processo, WebView, i log dei viaggi), unione, un verificatore scettico per ognuno:
  13 bug veri su 14. Corretti: lo stesso crash di Plancia per altre strade (l'app Plancia che prende il
  posto, account tolto, cambio di tema: `afterFocusLeaves` in CarUi e `rememberAllEntries`); «non
  inviare» e «annulla» come risposta (`VoiceReply`); pausa rimandata che annullava il comando musicale
  dopo; frasi della casa prese per musica; domande inglesi prese per comandi; «vai alla prossima
  canzone» presa per navigazione; «hör auf»; «rispondi alla chiamata persa»; fuoco audio perso e mai
  richiesto (`VoiceFocus`); assistente del telefono a schermo spento senza blocco (`AssistantUnlock`);
  pipeline di Home Assistant con un modello ("*", `AssistPlan`); lunghezze enormi nel log dei messaggi
  dell'auto (`Protobuf.describe`).
- **Revisione dell'assistente prima dell'uscita della 0.7.0** (richiesta dell'utente: "vedi se trovi altri
  bug"): un banco JVM che segue le decisioni di `AssistantCommands.handle` su ~450 frasi nelle 9 lingue,
  0.6.9 contro la 0.7, ha trovato quattro regressioni, corrette: le parole che contano come comando
  della musica (`musicCommandOnly`) erano solo inglesi, e «stop la musica», «stopp die Musik», «pause la
  musique», che i frasari lasciano come detti, andavano a Home Assistant (ora articoli e nomi della musica
  delle lingue); una frase che finisce col nome di un'app musicale è musica (`endsWithMusicApp`: «metti la
  luce su Spotify»); una parola che le canzoni condividono con la casa detta da sola («la luce», «the
  lights», `mayBeASong`) è una canzone se Home Assistant non è collegato; «how long until home», «do you
  have the time», «change settings» (`WholePhrases`). Una revisione dei crash di tutto il codice nuovo
  non ne ha trovati; zone grigie da provare in CI: il ☰ di Home Assistant in una parte che apre Plancia a
  tutto schermo col fuoco nella pagina (scatto da fare, `crash-webfocus-menu`), e il fuoco ripreso dal
  WebView dopo la manopola (`PlanciaWeb.dropFocus` dice "took it back" ma la parte esce lo stesso).
- Condivisione schermo (`mirror/`): mentre il telefono è copiato in auto, `TouchRelayService`
  (accessibilità, già usato per i tocchi) aggiunge un pixel trasparente
  `TYPE_ACCESSIBILITY_OVERLAY` con l'orientamento fisso (non sensore) della forma dell'immagine
  dell'auto (orizzontale, o verticale per uno schermo più alto che largo, dalla 0.7.4) e
  `FLAG_KEEP_SCREEN_ON`; lo toglie a fine condivisione (vedi "Condivisione schermo 0.7.4"). I tocchi si mappano sulle dimensioni
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
  si fa (`MirrorService.sharing`, `CarAudio.askLater`); dalla 0.7.4 la cattura segue la condivisione
  sulla sua proiezione e finisce con lei, e la domanda la rifanno `sharingEnded` e `CarUi`. Accanto alla mappa l'app musicale
  prende il posto solo della scelta automatica del viaggio quando non si collega
  (`mediaAutoPicked`), mai di un'app scelta dal guidatore.
- **Come è finito il processo di prima** (0.7, `LastExit.kt`): un crash nativo (motore web, grafica,
  sherpa-onnx) o Android che chiude myCar non lasciano righe nel log: il file si ferma. All'avvio
  (`MyCarActivity`, e l'anteprima) myCar scrive le uscite nuove da `ApplicationExitInfo` ("last exit:"
  motivo, ora, pss/rss, descrizione), solo dei suoi processi e dei crash del motore web (le pagine web
  chiuse con lei, "isolated not needed", coprivano il motivo vero); per un crash nativo il tombstone
  (protobuf: segnale, abort, thread, 24 frame, ultime righe di avviso), per un ANR lo stack di main.
  Android 17 ha un limite di memoria per app basato sulla RAM (Memory Limiter: motivo OTHER,
  descrizione "MemoryLimiter:AnonSwap"): lo dice con una riga a sé. Nel log anche la pagina della Home
  su cui si ferma ogni cambio ("home: page") e Plancia che entra o esce dalla vista. Nato dalle cadute
  di 0.6.8/0.6.9 (Pixel 10 Pro, Android 17): log fermi senza eccezione e senza alcun tocco prima (la
  seconda a 1 s dall'inizio del caricamento di Parakeet multilingual, 640 MB); lo scorrimento dalla
  pagina in tre parti non cade in emulatore. Al primo avvio con `LastExit` (nessuna uscita già detta)
  si scrivono le uscite degli ultimi due giorni, al massimo 5, dalla più vecchia. Il gestore dei crash
  scrive la traccia intera (fino a 400 righe, con le cause), non le prime 12: nella release le righe
  di Compose riempivano tutto. Il rilascio allega `myCar-v<versione>-mapping.txt` (la mappa di R8):
  le tracce della release hanno nomi abbreviati (`r8-map-id-…`), e si leggono con `retrace` e quel file.
- **Il crash della pagina in tre parti** (0.6.7-0.6.9, corretto in 0.7.0, `car/LeaveWithoutFocus.kt`):
  `IllegalStateException: LayoutNode should be attached to an owner`, myCar chiusa e viaggio finito.
  Un tocco nella pagina di Plancia, in una parte di una pagina della Home, dà il fuoco al WebView; se
  poi la parte lascia lo schermo col fuoco dentro (alla mappa, alla griglia, in modifica:
  `PlanciaPart(runs = false)` mette il segnaposto), `LayoutNode.removeAt` stacca la `Column` prima di
  toglierla dalla lista, l'`AndroidView` toglie la sua view, Android passa il fuoco
  (`rootViewRequestFocus`) e la ricerca del fuoco di Compose passa per il beyond-bounds del pager, che
  rimisura subito (`remeasureSync`): il `Box` della parte è misurato col figlio ancora dentro, poi il
  figlio esce dalla lista senza che il Box sia di nuovo da misurare, e al disegno dopo `Box.replace()`
  posiziona un nodo staccato. Riprodotto in CI a ogni giro (API 37.0 e 34, scatti `crash-*`), mai col
  fuoco tolto prima. Correzione: `leavesWithoutFocus(runs) { web.dropFocus() }` tiene la pagina un
  fotogramma in più e in un effetto, fuori dall'applicazione dei cambi, le toglie il fuoco; la Home
  toglie il fuoco alle view (`View.dropInteropFocus`, mai ai controlli di Compose) quando esce dallo
  schermo e quando una pagina entra in modifica (il browser di una parte resta composto, ma la
  modifica può toglierlo). Plancia accanto alla mappa (`CarHomeScreen`, `planciaShown`): pannello e
  colonna restano un fotogramma dopo lo scorrimento, la griglia, Close o il brano nel dock (trovato
  dalla revisione: lo stesso schema, non coperto). Le pagine cambiate dal telefono mentre la Home è in
  vista arrivano un fotogramma dopo solo se una view dentro ha il fuoco (`afterFocusLeaves`). Ogni
  nuova view focalizzabile (WebView) dentro le pagine del pager va tolta allo stesso modo. Plancia a
  tutto schermo è fuori dal pager. Scatto `crash-webfocus-side`.
- **Caricamento dei modelli della voce** (0.7.0, `SpeechEngine`): uno alla volta (al microfono il
  finale dopo Kroko, non insieme), mai dopo la fine del viaggio (`generation`: `unload()` fa scadere i
  caricamenti in attesa), e prima del primo comando (`warm`) il finale solo se resta memoria
  abbondante (`roomForFinal`: libera meno 5/4 del modello sopra 4 volte la soglia di Android e 2 GB).
  Dalla 0.7.4 il finale si precarica solo dopo 2 minuti di viaggio, e alla pressione decide `SpeechRoom`
  (vedi "Viaggio 0.7.4"). Ogni caricamento scrive la memoria libera e l'heap nativo. L'anteprima dell'auto sul telefono, senza
  viaggio, non li carica.
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
  focus in ritardo andrebbe al canale musica e lo farebbe partire; portata nella 0.7.4 con le
  protezioni di `FocusAnswers`, vedi "Radio dell'auto e casse"); il canale PhoneStatus per gli
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
  col telefono in tasca. Dalla 0.6.2 un'auto che ha chiamato il telefono (servizio Android Auto pubblicato
  da myCar, `waitForCar`) è ricordata (`WirelessAuto.callsPhone`, chiave `calls:<indirizzo>`): la volta dopo
  myCar pubblica il servizio subito invece di provare prima quello dell'auto (pochi secondi in cui l'auto
  cercava e rinunciava); se non chiama, la volta dopo si riprova come prima. Partito da solo
  (`WirelessLink.start(byItself = true)`) aspetta l'auto fino a 5 minuti invece di 2. Dalla 0.6.3, finito
  un viaggio senza fili con un'auto che chiama il telefono (sessione e canale Bluetooth chiusi dall'auto),
  il servizio resta pubblicato altri 10 minuti senza niente sullo schermo (`WirelessLink.listenAgain`,
  `quiet`): in una sosta breve il Bluetooth non si stacca, Android non dice che l'auto è tornata, e l'auto
  che riavvia il suo Android Auto trovava niente da chiamare. Cavo, Connetti o Disconnetti lo fermano.
  Android dà il Wi-Fi di un'auto (`WifiNetworkSpecifier`) solo a un'app in primo piano o con un servizio
  in primo piano: col telefono in tasca il join veniva rifiutato, perché `SessionService` partiva solo
  con l'immagine. Ora parte all'apertura del canale Bluetooth (`WirelessLink.holdService`; `joinWifi`
  aspetta fino a 3 s che sia su) e torna indietro se l'avvio non arriva a un viaggio
  (`giveBackService`, solo se l'ha acceso lui: mai durante un viaggio o col cavo). L'ascolto dopo il
  viaggio cede a un'auto che ricompare (`listeningQuietly`); un'attesa partita dal Bluetooth dell'auto
  andata a vuoto non fa dimenticare che chiama. In Automatico dopo una chiamata l'A2DP ha 10 s pieni
  per tornare (`CarAudioRoute.sawCall`). Trovati da una revisione indipendente, verificati nel codice.
  Dalla 0.6.4: l'auto che compare in Bluetooth va dritta alla schermata di myCar già aperta
  (`connectFromOpenScreen` in `MyCarActivity.kt`; poi la si porta comunque davanti, senza azione, così
  le domande di Android che aspettano myCar sullo schermo compaiono allo sblocco), perché un intent a una schermata ferma, con lo schermo
  acceso e il blocco sopra, Android lo consegna solo quando torna davanti, cioè dopo lo sblocco
  (`ActivityRecord.deliverNewIntentLocked`: subito solo se RESUMED/PAUSED o in cima col telefono che
  dorme); il "Consenti" si lancia solo con myCar davanti (`showApproval` dà false altrimenti) e l'auto
  che aspetta (`waiting`) si chiede in `onResume` (`askWaiting`); Impostazioni > Connessione dice le auto
  permesse (`allowedCars`); all'avvio il log dice quante sono e Android le guarda di nuovo (`rewatch`).
  Verificato nel codice di Android (mirror `aosp-mirror/platform_frameworks_base`): con Android 16 il
  sistema manda ancora `onDeviceAppeared(AssociationInfo)` accanto a `onDevicePresenceEvent`
  ("legacy callback", `DevicePresenceProcessor`), e `startObservingDevicePresence(String)` funziona.
  Dalla 0.6.5 (segnalato dall'utente dopo la 0.6.4: "l'app tenta a connettersi in automatico ma non ci
  riesce; faccio disconnetti, connetti e seleziono e si connette subito"): mentre il servizio del telefono
  aspetta l'auto (`waitForCar`), myCar richiama ogni 8 s quello dell'auto (`KNOCK_MS`, un thread a parte;
  vince il primo canale, l'altro si chiude; `Channel.inbound` dice chi l'ha aperto per `rememberCalls`),
  non durante l'ascolto dopo il viaggio (`quiet`). Prima, se al Bluetooth dell'auto il suo servizio non
  c'era ancora (sistema dell'auto che si avvia), myCar aspettava soltanto, e un'auto che aspetta di essere
  chiamata non faceva niente. Un avvio partito dal Bluetooth dell'auto (`autoStart`) che fallisce dopo
  (Wi-Fi dell'auto non arrivato, TCP, canale chiuso prima di un viaggio) ricomincia da solo dopo 3 s, fino
  a 3 volte (`startOver`, `retriesLeft`, `retryFor` contro i doppioni), non dietro una VPN; un tocco, il
  cavo o Disconnetti lo fermano (`startRun`). Controllo completo prima di pubblicare la 0.6.5 (richiesta
  dell'utente: "risolvere definitivamente i problemi di connessione"; due revisioni indipendenti, ogni
  punto verificato nel codice): un canale Bluetooth su cui l'auto non dice niente che porti al suo Wi-Fi
  si chiude dopo 30 s (`CHANNEL_QUIET_MS`, `joined`) e l'avvio ricomincia (prima "talking to the car"
  per sempre, servizio preso, ogni arrivo dell'auto scambiato per un viaggio); `channelEnded` non
  restituisce il servizio e non ricomincia mentre Wi-Fi o TCP sono in corso (`network != null`: alcune
  auto chiudono il canale dopo il join); `SessionService` ha due titolari (`DRIVE`, `STARTUP`) invece di un
  flag: l'avvio lo prende al canale, il viaggio dall'immagine, `startWireless` restituisce quello
  dell'avvio, e nessuno dei due spegne o lascia acceso quello dell'altro (prima un servizio preso dopo un
  viaggio restava acceso per sempre per via di `drove`); si ricomincia anche quando la sessione finisce
  prima dell'immagine (`pictured`, segnato in `reachedCar`: auto non pronta, certificato rifiutato, allora
  col successivo) e dall'ascolto dopo il viaggio (`autoStart = byItself`); un canale chiuso senza seguito
  dice "l'auto non si è collegata"; dopo il viaggio si ascolta anche un'auto che aspetta di essere chiamata
  (`listenAgain` per ogni auto permessa, chiamata ogni 15 s, `QUIET_KNOCK_MS`) e l'ascolto si ferma quando
  il Bluetooth dell'auto se ne va (`CarPresenceService.onDeviceDisappeared` → `WirelessLink.carGone`, solo
  per l'ascolto dopo il viaggio: un Bluetooth che sfarfalla non deve fermare gli altri avvii). Nella
  sessione (`MyCarRunner`): un PROJECTED ripetuto dall'auto riceve di nuovo CodecConfig e un fotogramma
  completo (`pictureAgain`, al massimo ogni 3 s); il fotogramma chiave chiesto ogni 5 s esce anche a schermo
  fermo (`onRefresh` se non sono usciti fotogrammi); senza fili, avvio fermo 30 s o auto che pingava e tace
  20 s (o 4 dei suoi intervalli) chiudono il socket (`closeTransport`, `giveUp`), e così anche `stop()`;
  un'immagine che non parte si riprova due volte (`showCarUi`, densità 0 → 160); una schermata vecchia che
  si chiude non ferma l'avvio della nuova (`onDestroy`). Lasciati: la richiesta di immagine ripetuta se
  l'auto non risponde alla VideoFocusRequest e la chiusura sui rifiuti deterministici (canale video
  rifiutato, risoluzione): un'auto che li fa li rifarebbe a ogni tentativo. Non provato con un'auto vera.
- **Assistente che non resta fermo** (0.6.4, richiesta dell'utente: "o si rimuove completamente da solo
  o continua ad ascoltare, preferisco il primo"): `CarAssistant` ha un controllo dei tempi (`watchdog`,
  ogni secondo finché il riquadro è su) che lo chiude da solo: microfono non aperto in 10 s, ascolto oltre
  25 s, parole o Home Assistant oltre 20 s (il tempo riparte quando passa a Home Assistant), sintesi
  vocale che non dice di aver finito (8 s + 120 ms a lettera, di più con la voce di Android rallentata,
  e mai mentre il motore segnala le parole che dice, `onRangeStart`; la fine si chiude a mano, così la
  musica riprende). Ricominciando mentre il modello finale carica, la frase lasciata non si decodifica. Il microfono premuto mentre lavora ricomincia (prima non faceva niente); i comandi si
  prendono con `collectLatest` in `CarUi`, così una richiesta nuova o Annulla fanno cadere quella che
  aspetta Home Assistant (prima tutte in coda dietro di lei fino a 40 s).
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
  nella lingua del telefono (`car/QuickReplyTexts.kt`: it, es, fr, de, pt, da 0.6 el, ru, zh; orario come lo scrive il
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
- **WebView dentro Compose: altezza esplicita** (0.6.7). In un `AndroidView` la view riceve
  `WRAP_CONTENT` (`ViewGroup.generateDefaultLayoutParams` di `AndroidViewHolder`), e il WebView con
  altezza `WRAP_CONTENT` impagina la pagina alta 0 px (`AwLayoutSizer` → `setForceZeroLayoutHeight`):
  contenitore iniziale e `100vh` a 0, barre `position: fixed` in fondo fuori schermo. Era la pagina
  d'accesso di Spotify nera sul telefono (il suo `<main>` è `position: absolute; inset: 0; overflow: auto`,
  restava alto solo il suo margine) e, in auto, browser e Cinema (la barra in basso di YouTube spariva).
  Ogni WebView creato in un `AndroidView` ha `layoutParams = MATCH_PARENT` (sign-in, `WebApp`,
  `CinemaApp`); Plancia e i servizi musicali in auto stanno già in un `FrameLayout` con MATCH_PARENT.
  Trovato in CI con CDP dentro la build debug (Chrome 113 su Android 14 e 145 su Android 17: stessa cosa).
- **Setaccio 5** (notte, 0.6.7, branch `fix/music-sign-in`; richiesta dell'utente: "cerca e risolvi
  eventuali bug, soprattutto nel collegamento automatico e nella trasmissione audio... la user experience è
  fondamentale"): dieci letture del codice in parallelo (workflow con agenti, ogni punto poi controllato da
  scettici e da me nel codice prima di correggerlo), una revisione indipendente dei commit. Collegamento:
  wake lock a tempo mentre un avvio aspetta l'auto (`WirelessLink.stayAwake`, fino all'immagine, non
  nell'ascolto dopo il viaggio), prove TCP per almeno un minuto sempre a 1 s (`TCP_WINDOW_MS`: una porta
  rifiutata risponde subito e le 12 prove finivano in 12 s), ascolto dopo il viaggio che ricomincia
  silenzioso, Indietro che non lo chiude (`WirelessLink.busy`), "Consenti" di Android chiuso senza
  risposta che non conta come no (solo `RESULT_USER_REJECTED`, Android 14+), Wi-Fi del telefono spento
  detto subito (`saidWhy`, niente ripartenze), Android 17 senza rete locale (chiesta dalla Home se c'è
  un'auto che si collega da sola, detta alla fine delle prove), "Usa myCar in auto" spento che non apre
  myCar, avvio rifiutato dall'auto senza fili chiuso dopo 30 s, controllo dei tempi su `elapsedRealtime`,
  niente pausa della musica per un collegamento finito prima dell'immagine. Audio: "Chiedi di nuovo"
  dopo un no (`CarAudio.askAgain`, avviso nel lettore `MusicNotReaching`, scheda sulla Home
  `AudioDeniedCard`; aprendo musica/Cinema/browser una volta per viaggio, `askAgainOnce`), assistente e
  messaggi letti sul canale delle indicazioni quando niente è catturato (`DirectionsVoice.speakForAssistant`),
  la coda di 2 s di Xonos che finisce appena la cattura ha suono vero (`DirectFeed.endTailFor`), pausa
  dell'app di prima passando a Xonos/servizi web, chiamata breve che non sposta Automatico su myCar,
  indicazioni vocali accese a mano rispettate in Bluetooth, avviso Bluetooth ricontrollato al cambio di
  strada, 20 s di margine per Xonos prima del ripiego, toast del tasto dell'audio. Auto: svolta nel dock
  per tutta la chiamata, Novità solo da fermi e mai sopra una chiamata, Indietro nel Browse, Play del
  volante che non ferma un film, app accanto alla mappa ricordata quando suona, "metti X su Spotify" su
  Spotify Web. Prestazioni: modelli vocali caricati 20 s dopo il collegamento (`SPEECH_WARM_AFTER_MS`,
  niente col riconoscimento del telefono), aree della mappa mandate solo quando cambiano, meno log dei
  template. **Lasciati apposta**: la finestra delle conferme video che parte con `lastAckAt = 0` (al
  secondo fotogramma si azzera e poi resta un fotogramma in più in volo: correggerla cambierebbe il ritmo
  delle auto che oggi vanno), ByeBye col cavo alla chiusura, la cattura tenuta tra due sessioni dello
  stesso viaggio (la domanda di Android a ogni ricollegamento: servirebbe "parcheggiare" la proiezione),
  il margine di `AudioUnderflow` tenuto tra due Start, il servizio in primo piano preso al tocco di
  Connetti, un'auto che se ne va prima che parta l'ascolto dopo il viaggio, la fascia del seek nel lettore
  accanto alla mappa (cambierebbe l'aspetto scelto). Secondo giro (chiamate e messaggi, Plancia, Cinema,
  cavo e avvio): la lettura di un messaggio interrotta da una svolta non blocca più la risposta
  (`CarAssistant.say` chiama sempre il seguito; i callback di `VoiceReply` valgono solo per il loro giro),
  risposta in corso chiusa a fine viaggio come col microfono, rete mobile sospesa in galleria non più
  "in rete" (`NET_CAPABILITY_NOT_SUSPENDED`: una galleria passava Plancia e Xonos alle app), Plancia con
  20 s all'avvio come Xonos, video coperto in marcia anche senza fix GPS (`CarMotion.movingForVideo`,
  l'ultima velocità vale fino a un fix nuovo, al massimo 5 minuti). Non provato in un'auto vera.
- **Setaccio 6** (0.6.8, branch `feature/hide-apps`; richiesta dell'utente: "cerca nuovi bug sulla
  connessione, sulla user experience"): due cacce in parallelo (collegamento: senza fili, cavo e sessione,
  audio; esperienza d'uso: schermo dell'auto, telefono, messaggi di collegamento), ogni punto verificato da
  uno scettico e nel codice, poi una revisione indipendente delle correzioni. Auto: durante una chiamata gli
  schermi (non la mappa) e la scheda dei messaggi stanno sotto la barra della chiamata (`callTop` in
  `CarUi`, misurata; dalla 0.7.4 la barra è la pillola della chiamata e `callTop` non c'è più); squillo che chiude assistente e lettura dei messaggi; una scelta nuova per "Accanto
  alla mappa" riapre la colonna (`showSidePane`); la X del testo riporta il lettore; il tasto Indietro
  dell'auto risponde alle schede sopra tutto (`car/CardBack.kt`: slot propri, non il `backInterceptor` di
  HomePager); tastiera della pagina web in vista; Novità che non si chiude spegnendo l'interruttore;
  linguette del Telefono e X dell'assistente a 56 dp. Telefono: la scheda della lingua della voce conta
  anche il modello che ascolta; la voce torna nella prima configurazione (`VoiceStep`); "Done" dopo
  l'accesso a un servizio musicale; Xonos spento chiude anche l'app Xonos; Android 12 senza posizione
  precisa lo dice. Collegamento: "l'auto non si è collegata via Bluetooth" solo per avvii mai arrivati a
  una sessione (`drove`); niente scheda rossa prima di una ripartenza (`stop(say = false)`); notifica del
  viaggio che segue `SessionStatus`; wake lock breve nell'ascolto dopo il viaggio (10 s se l'auto chiama
  il telefono, 3 minuti se no) e a ogni ripartenza; "Usa myCar in auto" spento ferma un avvio
  (`stopUnlessDriving`); rete dell'auto rilasciata dopo l'ultimo TCP fallito (`endJoin`); avvio automatico
  fermato se l'auto se ne va per 30 s (`carGone`/`carHere`); niente myCar sopra una chiamata; col cavo,
  dopo 45 s "cosa fare" (`SessionStatus.stillConnecting`); immagine che non si crea dopo tre tentativi →
  sessione chiusa con il motivo; densità 0 → 160 anche per le app; Start e CodecConfig solo con lo schermo
  nostro; riavvii dell'encoder contati in 10 minuti; avvisi e aiuto della voce azzerati fra viaggi;
  messaggi giusti per Plancia col ponte, VPN, chiusura durante l'avvio (`certAcceptedBefore`), senza fili.
  Audio: indicazioni che seguono la strada dell'auto (`followRouteForDirections`) e tacciono in chiamata;
  avviso "la musica non arriva" anche mentre la domanda aspetta (dopo 5 s) e nel lettore a tutto schermo;
  Automatico che aspetta la fine di una chiamata (al massimo 5 minuti); Cinema in pausa sul LOSS
  dell'auto (`CinemaNow`) e che parte dopo una chiamata (focus ritardato). **Lasciati**: i puntini delle
  pagine a 56 dp (con la decisione sulla striscia sopra i contenuti), il ripiego del cavo sul senza fili.
  Non provato in un'auto vera.
- **Testi delle canzoni** (0.6.9, richiesta dell'utente: "evitare brutte figure"; `media/Lyrics.kt`,
  `media/LyricsMatch.kt` con `LyricsMatchTest` e `LyricsWrongSongTest`, `media/LyricsAhead.kt`): prima Music
  Assistant con Xonos dentro myCar (`Library.lyrics`), poi LRCLIB con al massimo poche domande (get senza album,
  search per titolo e primo artista, titolo largo, parole). Titoli puliti (Remastered, feat., Live, From…, Official
  Video, "ARTISTA - TITOLO" delle radio, più artisti); dopo " - " una coda è decorazione solo se lo dice all'inizio
  o alla fine; ogni voce controllata: artista uguale solo se coincide il campo o il primo artista (un ospite o un
  pezzo di "A & B" vale solo alla stessa durata), tempi solo con la durata entro ±3 s. "Strumentale" dal titolo o
  da LRCLIB. Un 503 o la rete assente non diventano mai "nessun testo". Ricerche solo col testo a schermo o tenuto
  accanto alla mappa, più il brano dopo in coda mentre il testo è a schermo (scelta dell'utente: niente ricerche
  in sottofondo). Accanto alla mappa, senza testo una riga per qualche secondo e poi torna il lettore
  (`lyricsGaveWay`/`lyricsBack`, `lyricsWanted`). Niente altri siti di testi (Musixmatch, Genius, NetEase,
  lyrics.ovh: licenze/termini, NonFreeNet su F-Droid). Misura in CI (`lyrics-check`, solo a mano: ~250 richieste
  a lrclib.net): 0.6.8 25/47 giusti e 1 sbagliato, 0.6.9 42/47 giusti, 0 sbagliati, 5 "busy" (503) del test.
  Scatti `xonos-lyrics` (tono con tag LYRICS: "lyrics: found (Music Assistant)"), `media-lyrics`, `lyrics-side*`.
- **Avvio della mappa** (0.6.9, dal log di un viaggio del 2 novembre: Pixel 10 Pro Android 17, VW MIB senza fili
  via smartBox, telefono in tasca). (1) Posizione: Android dà la posizione a un servizio in primo piano avviato dal
  background solo entro 10 s dalla creazione di una schermata di myCar (o con l'accessibilità di myCar accesa,
  token BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS); presa quando la mappa si collegava (14 s dopo) veniva rifiutata e
  Vela restava senza posizione finché non si apriva myCar. Ora `LocationKeepAlive.holdForDrive` all'arrivo
  dell'auto (`connectToNearbyCar`, tocco su un'auto, cavo, `SessionService.start`), tenuta per il viaggio
  (`releaseDrive` in `SessionService.stop`; quella d'avvio va dopo 60 s se non si arriva a un viaggio), solo con
  un'app di navigazione per auto installata; riprova all'accensione dell'accessibilità e con myCar in vista; se
  Android rifiuta lo stesso, scheda in auto solo da fermi (`noPosition`, una volta per viaggio). Non serve
  ACCESS_BACKGROUND_LOCATION: non dà la capability a Vela. Verificato in emulatore 37.0 bloccato (scatto
  `location-locked`): a 1 s "running" (allowWiu ACTIVITY_STARTER), a 15 s rifiutato.
  **0.7.1** (log del 3 novembre con la 0.7.0, "ancora la mappa nera"): il Wi-Fi dell'auto (lo smartBox, che cade e
  riparte: Bluetooth e Wi-Fi insieme) è caduto quattro volte; il servizio veniva lasciato 1 s dopo la fine di ogni
  sessione (l'ascolto dopo il viaggio non contava, per non tenere la notifica 10 minuti), l'auto tornava dopo 4 s e
  Android lo rifiutava: mappa nera fino all'apertura di myCar. Ora la presa del viaggio resta finché c'è viaggio,
  avvio, ripartenza o ascolto dopo il viaggio (`WirelessLink.busy`, anche `listeningQuietly`) e per 5 minuti dopo
  l'ultimo di questi (`AFTER_DRIVE_MS`, `lastNeeded`, controllo ogni 60 s); Disconnetti la lascia subito (`letGo`).
  La notifica della posizione resta quindi qualche minuto dopo la fine del viaggio: è voluto. E con myCar già aperta
  da prima (succede spesso: la schermata resta dietro per ore) l'arrivo dell'auto non creava nessuna schermata, quindi
  niente 10 s: ora `WirelessAuto.carAppeared` → `LocationKeepAlive.screenForArrival` apre `host/LocationStartActivity`
  (Theme.NoDisplay, fuori dai recenti, task suo, si chiude in onCreate e chiama `retry`, che aspetta anche l'esito di
  un avvio in corso) col telefono bloccato o lo schermo spento, una mappa per auto installata e il servizio non acceso
  (su Android 12-13 sempre); non durante una chiamata; sbloccato e acceso, myCar viene davanti e riprova in onResume.
  Auto arrivata durante una chiamata: a chiamata finita (controllo ogni 2 s, al massimo mezz'ora, `afterCall`) la
  schermata per l'arrivo, anche col telefono in uso (`evenIfLit`: dopo una chiamata nessuno porta davanti myCar).
  Scatto `location-arrival`: auto finta associata dalla shell (`cmd companiondevice associate 0 <pkg> <mac>`; su
  Android 17 `list` stampa `Association{mId=…}`), myCar aperta, PIN e 15 s, poi `cmd companiondevice
  simulate-device-appeared <id>`, in quattro situazioni (myCar ultima schermata o dietro la home; schermo spento o
  blocco acceso), ognuna senza (prova di controllo: nella build debug il file `files/no-arrival-screen` salta la
  schermata) e con. Verificato (0.7.1, API 37.0): senza, rifiutato in tre su quattro col messaggio del log del
  guidatore (uid IMPORTANT_FOREGROUND o TOP_SLEEPING); con, sempre "running" (uid TOP, `getFgsAllowWiu_forCapabilities
  =PROC_STATE_TOP`). Solo "dietro la home, schermo spento" passava anche senza: portare davanti myCar la rende TOP lì. (2) Colonna della musica riservata dall'avvio
  (`CarServices.musicStarting`, 20 s, "Starting <app>…") così la prima superficie della mappa è già quella
  stretta (scatto `map-start`: prima superficie 1195x720). (3) Area visibile ridetta a una superficie rifatta
  alla stessa misura; tocchi sulla superficie nel log. (4) Spotify su un altro dispositivo: solo con l'orologio
  che avanza sicuro e niente suonato qui da 5 s, avviso dopo 3 s (prima compariva a ogni cambio di brano).
- **Setaccio 7** (0.6.8, stesso branch; richiesta dell'utente: "controlla anche l'assistente, l'autoplay
  delle app tipo Spotify, le notifiche che non interrompano i servizi, la mappa (Vela) non funziona più
  benissimo"): tre cacce (assistente, musica web, mappa dal lato di myCar), ogni punto verificato da uno
  scettico. **Mappa**: la colonna della musica si chiude solo dopo 6 s senza musica (`MUSIC_HOLD_MS` in
  `CarHome`, si disegna l'ultimo brano attivo): ogni cambio di larghezza è una superficie nuova per l'app della
  mappa (due passaggi, Vela ridisegnava); area visibile che parte dal bordo vero della scheda della lista
  (`MAP_PANE_MIN_EDGE` = 244 dp, `visibleAreaFor(…, minPanePx)`); scheda della svolta al massimo il 60%
  della mappa. Non è dimostrato che fosse questa la causa di "Vela non va benissimo": va provato in auto.
  **Musica web**: aprire un servizio (griglia, dock, voce: `launchEntry`) fa `openMedia(autoplay = true)`,
  Autoplay o no; un Play che ha aspettato la pagina (`patient`) si riprova fino a 45 s dall'avvio della
  pagina (`BOOT_MS`), e dopo una risposta "page" che non suona si rimanda dopo 4 s (`PLAY_CHECK_MS`), finché
  è la musica dell'auto e non c'è stato un altro tasto (`acts`); mai un secondo clic sul tasto play/pausa
  della barra. Schermo dell'auto rifatto (encoder nuovo) → `WebMusicPlayer.resumeAfterRebuild`, svuotato
  dalla condivisione dello schermo; pagina caduta (renderer) → riparte se suonava, una volta al minuto.
  Spotify Connect: l'orologio della barra che avanza senza niente che suona qui (`remote` nello script) conta
  come musica altrove: il Play non clicca, l'auto lo dice una volta (`car_web_music_elsewhere`). Notifiche:
  i popup sono muti, il focus transitorio di voce e assistente lo riprende il WebView da solo (smentiti dagli
  scettici i due punti contrari). **Assistente**: motore di sintesi rifatto al viaggio dopo se non parte o
  smette di parlare (`ttsFailed`, come `DirectionsVoice`); niente ascolto né risposta vocale durante lo
  squillo; un solo `VoiceFocus` dalla domanda alla risposta (in `CarUi`, try/finally); tasto voce in
  condivisione schermo → `PhoneMirror.requestStop()`; riconoscimento del telefono con la musica in pausa
  (`systemFocus`); `stopVoice` chiude tutte le frasi in corso; niente bip per un ascolto annullato durante il
  caricamento del modello. Revisione indipendente delle correzioni: con la barra di Spotify appena comparsa
  la pagina risponde "wait" finché l'orologio non è letto due volte (`clockSeen`), così il Play non mette in
  pausa Spotify che suona altrove; i tentativi si fermano se cambia la musica dell'auto o arriva un altro
  tasto; la fine di una frase dice se il motore l'ha rifiutata (`refused`, anche dal canale delle
  indicazioni) e il motore si segna da rifare solo se il testo non era troppo lungo; il brano tenuto
  durante l'attesa della colonna solo per la stessa app e solo durante l'attesa (`holding`). Non provato
  con un account Spotify vero né in auto con Vela.
- **Prima connessione** (`ui/SetupScreen.kt`, `setup/`): una lista sola, "Set up myCar", al primo
  avvio (al posto di `CertSetupScreen` + le pagine di `SetupGuideScreen`, ancora nel codice finché
  la nuova non è provata sul telefono) e ogni volta che si torna con "Take me back to the initial
  setup" (in cima a "How to connect", `BackToSetupCard`) o da Impostazioni → "Initial setup". Tre
  gruppi: per collegarsi (certificato, myCar accesa se era spenta, Android Auto da spegnere o da
  tenere scegliendo myCar quando Android chiede), per il viaggio (chiamate, mappa e musica: un solo
  "Allow" con tutti i permessi in una richiesta; bloccati: "Open settings"), "More" facoltativi
  (messaggi, calendario, notifiche, filtro chiamate, note d'uso; l'audio in auto dalla 0.7.4 sta col
  viaggio, vedi "Cavo e audio nella prima configurazione"). Un tasto grande
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
  dell'emulatore 320×640; in fondo `09-intro-1…4`, il veliero dell'intro con le animazioni accese, `09-intro-page-2…7` le sue pagine dopo, e
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
  già accettato; quello del guidatore non si cambia mai. Uno del catalogo non ancora scaricato (chi
  aggiorna dalla 0.5.20 ha aasdk e Headunit Revived, non CarService) viene prima di "every
  certificate": l'avviso dice "Get CarService" (`NO_SPARE`, `catalog` in `CertRotationPolicy.decide`),
  anche al posto di un "every certificate" rimasto dalla 0.5.20 (`CertRotationPolicy.shown`). Avvisi su Home (`CertNoticeCard`), scelta a
  mano in Impostazioni (aasdk · Recommended, Headunit Revived · Spare, CarService · Newest cars, con la
  data di scadenza). jsDelivr come terza fonte non
  usato: non raggiungibile dalla sessione cloud per verificarlo. Senza fili non riprova da solo col
  successivo: va provato in auto.
- **Impostazioni dell'auto sul telefono** (0.6.6, richiesta dell'utente: "tutte le impostazioni sullo
  schermo in auto devono poter essere settate anche sul telefono"; "la preview di myCar deve essere fuori
  dai sottomenu come prima possibilità di scelta in alto"): gruppo "Car screen" (`SettingsPage.CAR`,
  `ui/CarScreenSettings.kt`) con ogni impostazione della schermata Impostazioni dell'auto, sulle stesse
  chiavi di `CarSettings` e delle scelte accanto (`SidePaneChoice`, `HomePages`, `WallpaperSetting`): una
  modifica da una parte si vede subito dall'altra, anche durante un viaggio. Le scelte come sul telefono:
  il nome, la scelta sotto, e un tocco apre l'elenco (`PickRow` / `PickDialog`, riusati dalla sezione
  Musica); gli sfondi come campioni disegnati nel tema dell'auto (`MyCarCarTheme`); la pagina del
  browser con la tastiera del telefono; l'adattamento dello schermo solo con l'immagine di un'auto
  (`SessionStatus` PROJECTING), come in auto. Le **pagine accanto alla mappa** si cambiano dal telefono
  (`ui/CarPagesSettings.kt`, sulle funzioni pure di `HomePages`): ogni pagina col disegno del formato
  (`LayoutDiagram`, che ora accende una parte), formato, cosa mostra ogni parte (una sola app per auto a
  pagina), sposta, elimina, aggiungi, pagine pronte (con conferma). L'assistente vocale e il
  riconoscimento del telefono in "Voice" (`ui/AssistantSettings.kt`); l'audio in auto resta in
  "Connection". L'**anteprima dell'auto** è una scheda in cima alle Impostazioni, sopra i gruppi (non più
  in "Aiuto e strumenti"); con un'auto collegata resta lì, spenta, e dice che si apre senza auto.
  Verificato (scatto `phone-settings`): anteprima in cima, gruppo, scelte, pagina aperta con le parti,
  formati, sfondi, Voce, nessun crash.
- **0.7.4, dai log del 3, 6 e 7 novembre e dal forum di GrapheneOS** (richiesta dell'utente: "analizza i log e
  guarda cosa si potrebbe migliorare", poi "resto ok" a tutte le proposte tranne l'interfaccia verticale).
  Analisi: quattro letture in parallelo, ognuna con un verificatore; sette rami (`fix/wireless-start`,
  `fix/drive`, `fix/radio`, `fix/setup-cable`, `fix/portrait`, `fix/mirror`, `fix/waze-hosts`), le parti
  delicate (senza fili, viaggio, radio, condivisione) rilette da una revisione indipendente, uniti in
  `release/v0.7.4-alpha`. Nei log nessuna caduta era colpa di myCar: le 26 fini della Golf erano l'auto che si
  spegne (13), sessioni "fantasma" dello smartBox ad auto spenta (9), il riavvio dello smartBox all'avvio del
  motore (2), una caduta in marcia con l'auto che tace per prima, un risveglio senza viaggio. Non provato in auto.
- **Avvio senza fili più svelto e resoconto che dice come finisce un viaggio** (0.7.4, `fix/wireless-start`).
  Un altro Wi-Fi del telefono nello stesso /24 dell'auto conta come la rete dell'auto presa a mano solo se non è
  validato o ha il nome o il BSSID dell'InfoResponse (`wireless/CarNetwork.kt` + `CarNetworkTest`; il nome con un
  `NetworkCallback(FLAG_INCLUDE_LOCATION_INFO)` d'ascolto, al massimo 400 ms e solo se un candidato c'è; un nome
  nascosto non decide; una rete validata con un altro nome non si prova mai; se non si sa dire si alterna, col
  primo tentativo sulla rete chiesta). Prima il Wi-Fi di casa 192.168.1.x passava per quello dello smartBox e
  l'avvio perdeva 2,5 minuti (6/11 alle 07:46). Persa la rete dell'auto durante i tentativi TCP
  (`TcpTries.afterLoss`): si va avanti solo sulla rete dell'auto presa a mano o dietro una VPN; un'auto che
  richiede il Wi-Fi sullo stesso canale decide lei; col canale aperto 4 s di attesa; poi l'avvio ricomincia
  subito. Porta vecchia (`TcpTries.stalePort`): canale chiuso prima dell'unione al Wi-Fi e porta rifiutata per
  4 s, col Bluetooth dell'auto tornato almeno 10 s dopo, oppure per 5 s: si ricomincia per una porta nuova (7/11
  alle 11:41: un minuto senza schermo a 43 km/h). Richiami (`KnockPlan`): primo tentativo fallito in meno di 1 s
  e auto che non chiama il telefono, richiamo 3 s dopo l'arrivo del Bluetooth, poi ogni 2 s fino a 15 s, poi
  ogni 8 s; un arrivo durante l'attesa sveglia il richiamo, mai prima di 3 s (questo smartBox chiude i canali
  aperti subito dopo il suo arrivo); un arrivo durante una prima chiamata lenta conta (`firstArrival`); un
  canale chiuso dall'auto entro 4 s senza i dati del Wi-Fi riporta il passo a 8 s per tutto l'avvio, e se
  l'aveva aperto un richiamo veloce la ripartenza non si conta (`startOversLeft`). Resoconto (`SessionEnd` +
  `SessionEndTest`; la categoria resta per UsageStats): "ended - the car switched off", "ended - the car went
  silent (N s)", "FAILED - Wi-Fi dropped while driving", e righe last from the car, Wi-Fi, car Bluetooth audio,
  car's own screen, car at the end. Nel log "car: moving/stopped" e "Bluetooth: … disconnected"; senza fili
  niente consiglio del cavo nel VERDICT; `onLost` dice solo "the car's Wi-Fi went away". Da guardare: con
  GrapheneOS e i controlli di connettività spenti ogni Wi-Fi risulta validato, e con il nome nascosto un Wi-Fi
  dell'auto preso a mano diventa "non si sa" (proposta: il gateway uguale all'IP dell'auto).
- **Viaggio 0.7.4** (`fix/drive`). Spotify Web: un Play che arriva mentre il Play paziente è ancora in prova non
  fa più `acts++` e lascia andare i tentativi (`playTries`, al massimo 8 s senza un passo, `TRIES_GAP_MS`; mentre
  la pagina si ricarica il Play resta in attesa come prima): sulla Golf i Play dell'autoradio (AVRCP), circa 8 s
  dopo, fermavano i tentativi e la musica non partiva. Voce: Kroko a 20 s, il modello finale dopo 2 minuti di
  viaggio (`FINAL_WARM_AFTER_MS`: le sessioni fantasma lo caricavano per niente, 19 volte per 2 usi). Alla
  pressione la regola pura `speech/SpeechRoom.kt` + `SpeechRoomTest`: col margine di `roomForFinal` si carica;
  in inglese senza margine restano le parole di Kroko (`car_voice_small_model`, una volta per viaggio sopra
  "Listening…"); in una lingua che solo il finale sente (italiano) basta che dopo restino il doppio della soglia
  di Android e almeno 1 GB (carica coi 2,5 GB del 6/11, non con gli 1,2-1,6 GB delle sere come quelle delle
  cadute della 0.6.8/0.6.9), se no `Said.SHORT_OF_MEMORY`. A fine viaggio una riga per modello tolto. Sintesi
  vocale: il log dice motore, motori installati, voce, risultato di setLanguage e dopo quanto arriva
  `onStart`; una frase senza `onStart` entro 3 s (+10 ms a lettera, al massimo 8 s) resta scritta 5 s,
  VoiceFocus si rilascia, il motore si ferma, l'auto lo dice una volta (`car_voice_silent`); setLanguage
  fallito non toglie la voce. Sul telefono, Impostazioni > Voce, `ui/PhoneVoiceRow.kt`: "The phone has no
  voice for <lingua>" col tasto per `com.android.settings.TTS_SETTINGS` (6/11: il telefono non ha una voce
  italiana, -2 a ogni avvio). Home Assistant: `conversation/agent/list` una volta per collegamento,
  `AssistPlan.usable` lascia fuori le pipeline con un agente che non c'è (quello di Home Assistant resta
  sempre); `intent-not-supported` / `intent-agent-not-found` (arrivano come risposta d'errore di
  `assist_pipeline/run`, non come evento: `HaClient` ne tiene il codice) → `Said.HOME_ASSISTANT_NO_AGENT`.
  Stato all'auto: niente "paused" nella lettura del brano nuovo né entro 1,5 s da avanti/indietro
  (`CarMedia.skippedAt`). Solo log: zeri della cattura solo con la musica che dice di suonare da 5 s
  (`audio/SilentCapture.kt`), codec del campo 10 delle configurazioni, domanda dell'audio coi ms della
  risposta, Xonos "connected after N s" (`xonos/ConnectWatch.kt`), chi manda play/pausa al lettore web, le
  chiamate rifiutate dall'app per auto a fine sessione a livello I.
- **Radio dell'auto e casse** (0.7.4, `fix/radio`; tester del forum: niente DAB+ con l'audio tramite myCar).
  Non è la proiezione a togliere la radio: myCar chiedeva le casse appena Android diceva sì e mandava
  silenzio per tutto il viaggio. Scelta «Keep the car's radio until the phone plays» (chiave
  `car_radio_first`, spenta di predefinito; in auto dopo «Car audio», sul telefono in Connessione > Audio in
  auto): accesa, la cattura parte senza chiedere le casse (`AudioLink.captureOn`, `awaitingFirstSound`) e le
  chiede, con Start subito, al primo suono (300 ms) di un lettore che suona davvero, mandando prima i 300 ms
  sentiti. «Lettore» (`CarServices.aPlayerPlays`): l'app musicale dell'auto in STATE_PLAYING, Cinema (il suo
  lettore e i siti aperti; non il browser nelle parti della Home né Plancia) o una sessione PLAYING
  (`MediaSessionManager`, con l'accesso alle notifiche, al massimo ogni 500 ms). Per tutti: dopo una LOSS
  (`carSource`) il GAIN si chiede solo per un lettore, e una cattura ripartita non riprende le casse.
  Autoplay (`audio/CarSource.kt`, preferenze `mycar_car_source`): una LOSS seguita da 60 s senza la musica del
  telefono segna l'auto, e al viaggio dopo l'Autoplay non parte, **solo con la scelta accesa** (con la scelta
  spenta la cattura riprende le casse comunque, e l'auto restava su myCar in silenzio: trovato dalla
  revisione). Risposte, messaggi letti e bip vanno sul canale delle indicazioni anche con la cattura accesa ma
  le casse non di myCar (`CarAudio.speakersOurs`, `CarAssistant.onPromptChannel`, `SpeechEngine.cueOnCar`).
  **Portata dc61607** (scelta dell'utente: tolta dalle «Non portate»): prima di ogni annuncio, se la musica non
  ha né ha chiesto le casse, GAIN_TRANSIENT_MAY_DUCK, risposta attesa fino a 500 ms, poi RELEASE
  (`askForSpeakers`/`handBackSpeakers`, `DirectionsVoice.streamAsked`); protezioni in `audio/FocusAnswers.kt`:
  un GAIN che la musica non ha chiesto non fa partire niente; nella finestra un GAIN mentre la musica aspetta il
  suo va alla musica; una LOSS entro 1,5 s dal RELEASE con la musica chiesta o partita dopo si ignora; niente
  RELEASE se intanto la musica ha chiesto (`focusLock`). La regola della 0.5.09 resta. `AudioLinkFocusTest`
  (24). Da provare sulla Tayron: DAB+ scelto a metà viaggio (nel log "state=LOSS"; se è LOSS_TRANSIENT,
  `HELD_SOUND_MS` dopo 20 s riprende le casse), una svolta e l'assistente sopra la radio, ▶ dopo la radio.
- **Cavo e audio nella prima configurazione** (0.7.4, `fix/setup-cable`; dal forum: "troppi passi a mano col
  cavo"). Su Home, col telefono che si carica da una porta USB (`ACTION_BATTERY_CHANGED` sticky,
  `BATTERY_PLUGGED_USB`, solo con Home in vista) da 20 s senza un'auto arrivata a myCar né un viaggio, una
  scheda "The phone is charging, but the car doesn't answer" (`ui/ChargingNoCarCard.kt`, regola pura
  `setup/ChargingNoCar.kt` + `ChargingNoCarTest`): cavo che porta i dati, «Sempre» quando Android chiede quale
  app aprire e, su GrapheneOS (`runsGrapheneOs`, da `UsageStats.osName`), la porta USB-C (Impostazioni >
  Sicurezza e privacy > Exploit protection > USB-C port; i nomi di GrapheneOS sono solo in inglese) col tasto
  per `ACTION_SECURITY_SETTINGS`. Conta come arrivata un viaggio, un avvio senza fili (`WirelessLink.busy`) o
  un accessorio dato a myCar (`hasPermission`); un'auto arrivata su quel cavo la tiene via fino allo stacco.
  Come collegarsi: «Sempre» la prima volta col cavo e, solo su GrapheneOS, il passo della porta USB-C. Prima
  configurazione: "Car audio" esce da More e sta col viaggio (`ui/SetupAudioStep.kt`), sempre facoltativo,
  Automatico predefinito, con cosa succede a ogni viaggio per ogni scelta e, se non è Bluetooth, il comando
  adb di PROJECT_MEDIA (`PROJECT_MEDIA_COMMAND`). Da Android 15 il tasto della domanda di Android si chiama
  "Condividi schermo", non "Avvia": l'aiuto ora dice di accettare la domanda con un tocco, senza nominarlo.
  Scatti `setup-phone/04b-04d` e `charging-no-car` (`dumpsys battery set usb 1`, `--ez graphene true`).
- **Schermi verticali** (0.7.4, `fix/portrait`; dal forum: un'auto con lo schermo verticale, e "No vehicle
  profile matched" letto come un errore). Senza profilo l'immagine la sceglie `ScreenFit` (regola pura,
  `ScreenFitTest`): con un touchscreen più alto che largo, se l'auto offre immagini verticali contano solo
  quelle (la più piccola alta almeno l'80% del touchscreen, se no la più grande; "upright touchscreen: only
  upright pictures considered"); col touchscreen 0x0 (Tayron) resta la regola di prima. I tocchi non si
  scalano sul touchscreen: per un'auto verticale guardare nel primo log "config[i] = …" e "input service: …
  touchscreen=…". Profili (`vehicles.json`): `insets` null se manca la chiave; tutti 0 vogliono dire nessun
  margine. Il tasto spento di "Screen fit" dice "No saved profile: myCar uses the car's sizes". Anteprima:
  `portrait-720x1280/`, `portrait-1080x1920/`, `portrait-split-<misura>/`, `portrait-pages-<misura>/`
  (`shots: portrait`): myCar in verticale funziona, ma col lettore stretto accanto alla mappa. L'interfaccia
  verticale (dock in basso) non è fatta: l'utente tiene l'interfaccia com'è.
- **Condivisione schermo 0.7.4** (`fix/mirror`; dal forum, il confronto con ScreenOnAuto). Lo schermo del
  telefono resta acceso: `FLAG_KEEP_SCREEN_ON` sulla finestra di `TouchRelayService` e, perché senza
  l'accessibilità di myCar (spenta nei log dell'utente) quella finestra non c'è, un wake lock
  `SCREEN_BRIGHT_WAKE_LOCK | ON_AFTER_RELEASE` di `MirrorService` per tutta la condivisione (vale per un'app
  con un servizio in primo piano, verificato in AOSP 14-16 e nel bytecode di Android 17). Da Android 16 il blocco
  del telefono chiude una proiezione con un display (`MediaProjectionStopController`): prima il timeout dello
  schermo chiudeva la condivisione. Il telefono si gira come l'immagine dell'auto (più alta che larga →
  verticale, se no orizzontale come prima: `PhoneMirror.upright`). La schermata delle app dice come si torna
  (bande nere, un tasto dell'auto, Interrompi nella notifica) e, senza accessibilità, che il telefono resta in
  verticale. **Musica durante la condivisione** (`audio/SharingHandOver.kt` + `SharingHandOverTest`): con la
  cattura accesa, al tocco su Avvia la cattura passa alla proiezione della condivisione
  (`AudioCaptureService.followSharing` → `follow(token)`: `AudioService.canProjectAudio` chiede solo che sia la
  proiezione corrente, e la cattura non crea display), e finisce con lei (`sharingEnded`: pausa, smuto, Capture
  OFF, la domanda torna); se il servizio si ferma a metà passaggio, `serviceEnds` chiama `captureStopped`
  (trovato dalla revisione). Ripiego: la pausa di prima. Spotify Web si ferma comunque (la sua pagina va via
  con lo schermo dell'auto). Non provato sul telefono: con l'audio tramite myCar, condividere un'app che suona
  e cercare nel log "capturing on the screen sharing's projection".
- **Waze modificata con Morphe** (0.7.4, `fix/waze-hosts`; dal forum). La patch "Allow Gearslip"
  (VoltoxderEchte/waze-gearslip-morphe-patch) accetta solo gli host della sua opzione "Accepted hosts", di
  predefinito solo Gearslip: per myCar si aggiunge
  `;e477a4794b984927360e9ae4a31e87cb08a26c54e0e27e4334116d1c7cdccc31,io.github.sirionero.mycar`
  (`*,io.github.sirionero.mycar.dev` per la build di prova) e si rifà la patch (README, "Some apps refuse
  myCar"). Una copia rifirmata di un'app rotta (`KnownApps.modified`, oggi solo Waze) che rifiuta myCar prima
  dell'handshake (anche i 10 s senza risposta: è così che rifiuta la Car App Library) è ricordata in
  `host/HostRefusals.kt`, fino alla 0.7.4 `PatchRefusals` (pacchetto → `lastUpdateTime`, perché Morphe firma ogni copia con la stessa chiave):
  X rossa e niente avvio automatico, ma la tessera la apre ancora; si dimentica al primo handshake accettato o
  con un'altra copia. In auto il rifiuto dice cosa aggiungere (`car_host_modified_refused`). Non provato con una
  Waze vera.
- **Gearslip 0.3.32 → 0.3.40** (0.7.4, branch `fix/gearslip-0.3.40`): come per il "Motore da Gearslip",
  riscritte a mano, una per commit, solo cose che non cambiano come si collega un'auto che oggi va. Portate: gli
  avvisi delle app per auto (`car/CarAlerts.kt`, in alto al centro sopra ogni schermo, anche sopra la pillola
  della chiamata, fino a due tasti, barra del tempo; scatti `alert*`); icone e `PhoneInfo` nella
  ServiceDiscoveryRequest solo per le auto 1.0/1.1 (`DiscoveryRequest`, dalla 1.2 byte per byte come prima); la
  sintesi vocale rifatta dentro il viaggio dopo 30 s senza risposta (`SpeechRestart`); la musica ripartita dopo
  la fine del viaggio rimessa in pausa 2 s dopo, solo se suonerebbe dal telefono; like dall'icona (`AppButtons`,
  Metrolist); Capitolo successivo di AntennaPod; alias Bluetooth (`shownName`); "--- Android ended this run" in
  fondo al log dell'avvio caduto (pid nella prima riga); Clio V in CARS.md. Su richiesta dell'utente anche:
  **schermo trattenuto** (`HeldScreen`, `HeldScreenCars`: canale della musica chiuso per il Bluetooth e nessuno
  schermo 5 s dopo la richiesta → il canale si apre, dopo 1 s la stessa richiesta, motivo 1; l'auto è ricordata
  solo se lo schermo arriva entro 5 s dall'apertura, per due viaggi di fila (`HeldScreen.TIMES` = 2: una volta
  sola può essere un'auto lenta), e da allora il canale si apre subito); **indirizzo Bluetooth del telefono**
  scritto a mano (Impostazioni > Connessione, `PhoneAddressRow`, chiave `phone_bluetooth_address`, vuoto di
  predefinito: un'auto già abbinata riceve canale Bluetooth e PairingRequest con quell'indirizzo, senza
  abbinamento; mai nel log; GM senza Wi-Fi projection); **tipo di canale Wi-Fi nella VersionResponse**
  (`WirelessVersion`, solo all'auto che si è fermata dopo la nostra risposta, chiave
  `channel_type:<indirizzo>`); **musica in pausa nel momento dell'auto**
  (`AudioLink.onPausedForNow/onGivenBack`, `CarServices.pausePhoneMediaForNow/resumePhoneMedia`, regola
  `PausedForCar`: con l'audio tramite myCar un LOSS_TRANSIENT sulle casse date alla musica in streaming mette in
  pausa ciò che suona e il GAIN riprende solo quello ancora in pausa; niente se il guidatore ha chiesto qualcosa
  o scelto un'altra app, dopo un LOSS o a fine viaggio; in chiamata aspetta la fine, al massimo mezz'ora; se
  l'auto riprende le casse prima che la musica sia ripartita (un GAIN arrivato durante la chiamata) quello che
  aspettava resta e riparte col GAIN dopo (`PausedForCar.Memory`); un'auto senza GAIN lascia la musica in pausa
  finché il guidatore non preme play). Golf e Tayron: collegamento invariato (log: schermo entro 0,51 s, 51
  VersionResponse seguite da StartRequest). Non provato in auto. Dal backlog va via «mettere in pausa Xonos
  mentre l'auto tiene le casse per un momento».  ## 7. Per le novità  - Alerts from car apps show on the car
  screen, such as Waze asking whether a hazard is still there. - When the car needs its speakers for a moment,
  such as for a call, music playing through myCar pauses and picks up where it left off. - Cars that hold back
  their screen, or need the phone's Bluetooth address (now under Settings > Connection), get another way to
  connect. </agent-message>
- **Pillola della chiamata in corso** (0.7.4, `feature/call-bubble`; richiesta dell'utente: "quando una chiamata
  è in corso c'è una nuvoletta con la locandina della chiamata e il tasto per interrompere, un po' stile la
  nuvoletta della musica, ma c'è sempre finché non interrompi la chiamata, qualsiasi schermata aperta ci sia";
  `car/CallPill.kt`, regola pura `car/CallPillPlace.kt` + `CallPillPlaceTest`, chiave `car_call_pill_at`).
  Finché la chiamata è in corso (fatta, presa, in attesa; mentre squilla resta la scheda a tutto schermo) una
  pillola 296×64 dp sta sopra ogni schermo e sotto avvisi, schede e domande. Mostra la foto del contatto o
  l'iniziale come il Telefono, il nome o il numero ("In chiamata" se non si sa), il tempo in verde
  (`CallUiState.Active.since`, da quando il telefono va occupato; una chiamata già in corso quando myCar
  comincia a guardare il telefono non ha tempo) e "Voce sul telefono" in ambra (`rememberCallOnPhone`; col tema
  chiaro `PositiveOnLight`/`WarningOnLight`). Il tasto rosso chiude (`CarCalls.hangUp`, solo con
  ANSWER_PHONE_CALLS), il resto apre il Telefono. Si sposta come il mini lettore (`dragAfterHold`, ma con
  `tapAfterHold = false`: tenuta e lasciata non preme il tasto sotto, un tocco lento non chiude la chiamata). Il
  posto si salva come frazione dello schermo accanto al dock: mai sotto il dock, sempre dentro i margini. Parte
  in alto al centro, a destra della scheda della svolta e a sinistra dei tasti della mappa dove c'è posto, più
  piccola dove non ci sta (`fitScale`, come il mini lettore). Ha preso il posto della barra in alto (`callTop`,
  `LocalCallTop` tolti): gli schermi non scendono più. Il mini lettore le lascia il posto (`CallPillSpot`,
  `CallPillPlace.miniApart`: il posto libero più vicino, fuori dai tasti della mappa; finita la chiamata torna
  dov'era). Una chiamata fatta dall'auto (`placeCall` → `CarCalls.dialing`) ha il suo nome. Anteprima: `--ez
  fakeCall true` (solo debug, `CallPreview.kt`), `--es fakeCaller`, `--es pillAt`, passi `knob:n`/`knob:ok` di
  `PreviewScript`; scatti `call-bubble*` e `call-answer` ("call-bubble" li prende tutti). Verificato in CI
  (0.7.4): pillola su mappa, griglia, Impostazioni, Telefono, 800x480 Normal ed Extra large, tema chiaro;
  trascinata e salvata; il mini lettore che si sposta e torna; col modem dell'emulatore Rispondi, pillola e
  tasto rosso che chiude davvero; la manopola che arriva al tasto rosso e lo preme. Non provato in auto, né con
  la foto di un contatto.
- **Il tasto Jolly** (0.7.4, `feature/jolly`; richiesta dell'utente: "un magic tasto, da preimpostare sopra a
  quello del microfono, dove puoi selezionare un comando veloce che preferisci"; `car/Jolly.kt`,
  `car/JollyScreen.kt`, `car/JollyAction.kt` + `JollyActionTest`, `ui/JollySettings.kt`, testi
  `strings_jolly.xml`): nel dock, sopra il microfono, un tasto fisso della stessa misura (`sym_auto_awesome` su
  una tessera viola, `MyCarPalette.Violet`) che fa con un tocco il comando scelto. Il nome è una stringa sola
  (`jolly_name`: "Jolly" in tutte le lingue, "Jolly Roger" in piratesco), gli altri testi lo prendono come
  segnaposto. Sta in fondo alla parte centrale del dock (`JollyRoom` = 64 + 8 dp): le app perdono un posto (Golf
  800x480 da 3 a 2, Tayron da 5 a 4); dove non resterebbe nemmeno la mappa (800x480 a Extra large) non c'è.
  Spento ("Jolly key", chiave `car_jolly_shown`, in auto e sul telefono in Schermo dell'auto) il dock è come
  prima: lo spazio sopra il microfono è passato da 8 a 6 dp e i 2 dp si tolgono dalla parte centrale. Tocco: il
  comando (chiave `car_jolly`), o la scelta se non c'è; tenuto premuto: la scelta (`CarScreen.Jolly`: sei
  riquadri, poi l'elenco del tipo). Tipi: chiamare un contatto (`placeCall`); parlare con un'IA
  (`PhoneAssistants.launch` come il microfono, col telefono bloccato `AssistantUnlock.start(…, then)` o, con
  Home Assistant pronto, myCar ascolta da sé, `CarAssistant.listenHere()`); Home Assistant (solo con Plancia
  configurata in myCar, sul collegamento del viaggio: script e scene `turn_on`, pulsanti `press`, interruttori,
  luci e cancelli `toggle`, serrature `lock`/`unlock`; aprire una serratura vuole un secondo tocco fra 0,7 e 3 s
  dal primo, `SecondTap`; allarmi no; prima del comando myCar legge lo stato di quella sola cosa,
  `home/EntityWatch.kt` con `subscribe_entities` + `entity_ids`: mancante o `unavailable` non manda niente e lo
  dice, `unknown` va avanti perché è lo stato normale di scene, pulsanti e cancelli senza sensore; una serratura
  si aspetta fino a 10 s, `locked`/`unlocked`/`open` o `jammed`, poi "still locked" o "hasn't said"; lo stesso
  comando non riparte mentre gira e per 1 s dopo, `RepeatGuard`; regole in `HomeChecks` + test; un Home
  Assistant senza `entity_ids` riceve solo il comando, come prima); andare a un posto scritto sul telefono
  (`CarServices.connectNav`, come il «portami a» dell'assistente); un comando dell'assistente come se fosse
  detto (`CarAssistant.runPhrase` → `heard()`, nella lingua in cui è scritto, `written`, solo per la sua risposta;
  quattro pronti per lingua, che `JollyActionTest` fa capire ai frasari); aprire un'app della griglia. In auto
  non si scrive niente. Nell'anteprima dell'auto sul telefono, senza viaggio, il tasto dice solo cosa farebbe.
  Al tocco "Chiama" ricontrolla con `PhoneLookup` che il numero sia ancora di un contatto con quel nome, se no
  riapre la scelta. Col telefono bloccato e lo sblocco negato l'auto dice di ritoccare il tasto Jolly; il
  microfono lascia cadere un assistente del Jolly che aspetta lo sblocco (`AssistantUnlock.dropJolly`). La
  lingua di un comando scritto vale solo per la sua risposta (`CarAssistant.forgetWritten`). Durante lo squillo
  il tasto tace; in chiamata chiamata, IA e comando aspettano. Il comando si salva come "type=…&chiave=valore"
  (%XX per % & = e controlli); quello che la versione non conosce vale come nessuna scelta. Un widget vero di
  Android su un tasto non si può (va ospitato e disegnato intero, le sue azioni sono dell'altra app): lo coprono
  Home Assistant, chiamata e comando. La manopola si ferma anche sul tasto (fra microfono e mappa):
  `call-bubble-knob` arriva al tasto rosso della pillola al settimo giro; altri scatti che contano i giri della
  manopola nel dock vanno contati così. Anteprima `--es jolly <call|ai|home|unlock|navigate|say|app>`, `--es
  jollySay`/`jollyLang`, `--ez jollyKey false`, `--ez jollySettings true`; scatti `jolly-*` (`shots: jolly`, con
  Home Assistant; `jolly-lock` e `jolly-lock-stuck` con una serratura finta scritta con l'API REST, `--es
  jollyHome`). Verificato in CI (0.7.4): tasto e microfono a 800x480 e 1920x720, Large ed Extra large, scelta in
  9 lingue e in piratesco, comando scritto risposto (anche in italiano), assistenti, app, contatti (anche sul
  telefono), cose di un Home Assistant vero, script eseguito, serratura col secondo tocco (troppo presto
  ignorato, poi "done"; una che resta chiusa: "still locked" dopo 10 s; una che non c'è: lo dice), posto passato
  a Vela. Una revisione indipendente ha trovato 13 punti, tutti corretti tranne il tasto acceso di predefinito
  (voluto dall'utente). Non provato con un telefono e un'auto veri. Idee per dopo: dettare posto o comando a
  macchina ferma, più tasti o un Jolly per auto, lo stato di Home Assistant sul tasto, "portami a casa" dalla
  zona home, una sequenza di azioni.
- **Copertina video** (0.7.4, `feature/video-cover`; richiesta dell'utente: "se attivo Spotify Web, la
  possibilità di scelta fra locandina fissa e locandina video", poi "un video anche in marcia, se abilitato; lo
  attiva la prima volta un tasto nel lettore condiviso, poi resta così finché non lo ritocca"). Dove la musica
  dell'auto può avere copertine video (Spotify Web, `WebMusicService.videoCovers`), un tasto in alto a sinistra
  sulla copertina del lettore accanto alla mappa (`car/VideoCover.kt`, `VideoCoverButton`, simbolo
  `sym_motion_play`, bianco quando è acceso) le accende e le spegne; la scelta resta (chiave `car_video_cover`,
  ferma di predefinito), anche in Impostazioni dell'auto ("Cover in the player", accanto alla dimensione del
  lettore, solo con Spotify Web acceso) e sul telefono (Musica → Spotify, "Video cover"). Nessuna regola di
  marcia (decisione dell'utente). Accesa, un brano con la sua Canvas la mostra al posto della copertina:
  `MediaPlayer` su `TextureView` come Cinema, muto, in loop, tagliato dal centro come la copertina (`CoverCrop`
  + test), da una copia nella cache (`VideoCoverFiles`: scaricata una volta, solo col lettore in vista, la
  musica accesa e la copertina video accesa; un brano saltato o il lettore fuori vista interrompono il download,
  `runInterruptible`; le ultime 12, massimo 20 MB; un errore di rete si riprova dopo 30 s, un errore HTTP o un
  file che non si riproduce dopo 10 minuti), sotto la copertina ferma che sfuma al primo fotogramma; in pausa
  con la musica e fuori vista (`LocalPlayerInView`), via quando non si vede, una alla volta. Vetro dei tasti,
  mini lettore e dock tengono la copertina ferma. Il lettore a tutto schermo di Spotify Web è la sua pagina
  (`WebMusicScreen`): lì la Canvas è quella di Spotify, `MediaScreen` non c'entra. Da dove viene: lo script
  della pagina (`WebMusicScripts.videoCover`, che cerca solo con `window.__mycarVideoCovers` acceso:
  `WebMusicPage` lo mette all'inizio di ogni pagina e quando cambia l'impostazione) manda l'indirizzo con lo
  stato (`WebPlayerState.videoUrl`, solo da `https://canvaz.scdn.co/`). Il lettore web di Spotify (letto dal
  runner, novembre 2026; `.github/spotify-canvas.py` nello scatto `video-cover-spotify` ne scrive i fatti nel
  log del job, mai pezzi del codice di Spotify) suona la Canvas col suo lettore video per id del file (`<video>`
  su `blob:`), e il componente riceve l'indirizzo come prop `canvasUrl`: lo script lo legge da React, dal
  `<video>` su fino al componente e, fuori vista se il video non è caricato, cercando in tutto l'albero al
  massimo ogni 5 s e su al massimo 60.000 nodi; tiene il più in vista e mai un'anteprima (`playbackType`
  `loopOnHover`). Al cambio di brano l'albero si rilegge subito, poi ogni secondo per 10 s. `SongVideo` + test:
  il video che la pagina mostra ancora dal brano prima non conta (salvo se la pagina lo mostrava da meno di 2 s
  prima del cambio), ricorda il suo brano (Avanti→Indietro lo ridà) e dopo 10 s non conta più come vecchio. Dopo
  un errore il video non si riavvia (`onError` ferma `start()`). Mentre c'è l'avviso «Music isn't reaching the
  car» (accanto alla X, come prima) il tasto video non compare. Nel Now playing view di Spotify la Canvas c'è
  solo nella vista "Canvas" (artwork/canvas/hero/video/lyrics): va scelta una volta nella pagina. Se Spotify
  cambia questi interni resta la copertina ferma, senza errori. Nel log "with a video cover" e "video cover:
  playing, WxH"; l'indirizzo solo nella build di prova. Anteprima: `--es videoCover`, `--ez videoCoverOn` (solo
  debug), `--ez musicNotReaching true` (l'avviso della musica che non arriva), scatti `video-cover`,
  `video-cover-800x480`, `video-cover-moving`, `video-cover-not-reaching` (Extra large), `video-cover-spotify`
  (Canvas finta di ffmpeg a `10.0.2.2:8003`). Verificato in CI: tasto, dissolvenza, loop, taglio, angoli, mini
  lettore fermo, in marcia. Una revisione indipendente ha trovato 7 punti: 6 corretti nel codice; il settimo
  erano estratti degli script di Spotify pubblicati per sbaglio dalle prime sonde su `ci/car-preview`, ancora
  serviti da 4 commit non più raggiungibili (solo il supporto di GitHub può toglierli: l'utente decide dopo il
  rilascio). Non provato con un account Spotify né in auto.
- **Una VPN tiene myCar fuori dal Wi-Fi dell'auto** (0.7.5, `fix/vpn-wireless`; dal forum di GrapheneOS: Ioniq 5
  con un adattatore Ottocast, `probe: …:5288 failed: … EPERM` ripetuto). Dentro una VPN non scavalcabile Android
  rifiuta il legame alla rete dell'auto (EPERM: `checkUserNetworkAccessLocked` di netd, solo se la VPN si
  applica all'uid ed è sicura). I tentativi passano allora dalla strada normale, che va nella VPN o, col ripiego
  `RULE_PRIORITY_VPN_FALLTHROUGH`, sulla rete principale del telefono: all'auto arriva solo se il Wi-Fi del
  telefono è il suo. "A VPN is blocking the car" arrivava solo dopo un minuto intero, e un'auto che richiede il
  Wi-Fi fa ripartire i tentativi: non arrivava mai. Ora `wireless/VpnBlock.kt` (+ `VpnBlockTest`): 10 s dal
  primo rifiuto e due tentativi sulla strada normale senza risposta, contati su tutto l'avvio, una volta per
  avvio. Un ECONNREFUSED è dell'auto solo con il suo Wi-Fi tra quelli del telefono (`CarNetwork` CAR/MAYBE), e
  allora non si dice niente. Detto, la riga sotto "Connecting" (Home e notifica del viaggio, espandibile con
  `BigTextStyle`) resta quella per tutto l'avvio (`WirelessLink.connecting`, `vpnSaid`; azzerata a un nuovo
  avvio e al TCP riuscito). Testo: escludere myCar nell'app della VPN, spegnerla durante il viaggio o il cavo;
  con «Blocca connessioni senza VPN» Android blocca anche le app escluse (`Vpn.setVpnForcedLocked`). Tailscale
  per nome (`Tailnet.vpnIsTailscale`: DNS 100.100.100.100 o indirizzi 100.64/10). Se nessuna VPN porta più myCar
  (`vpnActive`) i tentativi tornano subito sulla rete dell'auto; la sonda col legame rifiutato non prova le
  porte. Aiuto "How to connect": passo VPN solo con una VPN che porta myCar (`Tailnet.reach` IN_VPN), con "Open
  settings" alle impostazioni VPN di Android. Tutto questo scatta solo dopo un EPERM: con myCar esclusa da
  Tailscale (le auto dell'utente) il legame riesce e niente cambia (controllato nel codice). Da fare se serve:
  «Blocca connessioni senza VPN» con myCar esclusa dà EACCES, non EPERM, e non è riconosciuto (lo dice solo il
  testo). Non provato con una VPN vera né in auto.
- **App che rifiutano myCar come host** (0.7.5, `fix/refusing-apps`; dal forum: ABRP su una Hyundai Inster col
  cavo, "It failed at onHandshakeCompleted: Unknown host 'io.github.sirionero.mycar', uid:…"). Il controllo
  dell'host della Car App Library di alcune app (`HostValidator` con un elenco suo) non accetta il permesso
  `TEMPLATE_RENDERER` di myCar, che il validatore standard accetta: da myCar non si scavalca. Regola pura
  `host/RefusalRule.kt` + `RefusalRuleTest`: conta solo un rifiuto a `onHandshakeCompleted` col messaggio
  "Unknown host '…'" (versioni vecchie della libreria, IllegalArgumentException) o "Unrecognized host…" / "The
  host is not allowed…" (versioni nuove, SecurityException, prima `car_host_not_allowed`); timeout, crash e
  altri errori restano come prima, e un'app che ha accettato myCar (`hostAccepted`) non si segna mai.
  `host/PatchRefusals.kt` è diventato `host/HostRefusals.kt` (preferenze ancora `mycar_patch_refusals`) e
  ricorda anche queste app (pacchetto → `lastUpdateTime`): X rossa sulla tessera della schermata (non sul
  lettore, che è di myCar), mai avviata da sola (mappa all'avvio `KnownApps.wontStart`; accanto alla mappa e
  nelle pagine `rememberRefusal`/`RefusedAppPart`: il perché e "Riprova"), la tessera la prova ancora;
  dimenticata al primo handshake accettato o quando l'app si aggiorna. In auto `car_host_refused` (9 lingue,
  piratesco dove c'è accanto): «X non accetta myCar: lascia entrare solo gli host che conosce, come l'Android
  Auto di Google. Solo chi la sviluppa può cambiarlo.», anche a tutto schermo (`AppWontStart`,
  `Status.refused`). Pagina della mappa: ultima mappa che non parte da sola e nessun'altra → "Scegli un'app di
  navigazione." invece di "Mappa chiusa." con un "Riapri" a vuoto. Le copie modificate (Waze con Morphe) restano
  col loro testo. Golf e Tayron: nessuna delle loro app rifiuta myCar. Non provato con un'app vera che rifiuta.
- **Svolte sul quadro strumenti** (0.7.6, branch `fix/cluster-images`; issue #23: una MG ZS 2024 ibrida, unità
  SAIC "ZS32 Color Radio" con adattatore senza fili, chiudeva la sessione mezzo secondo dopo la prima svolta, a
  ogni destinazione in Vela o Organic Maps). Il NavigationStatusService (campo 8 del Service) si legge in
  `car/ClusterSpec.kt` (+ test): `minimum_interval_ms` (1), tipo (2: IMAGE 1, ENUM 2), `image_options` (3) con
  **l'altezza nel campo 1 e la larghezza nel 2**, poi i bit di colore, come in aasdk e aa-proxy-rs, presi da
  Android Auto. La MG chiede IMAGE 1920x720, 32 bit, 1000 ms; la Tayron ENUM e 1000 ms; la Golf non ha il
  servizio. **Ritmo** (`ClusterNext` + test): intervallo = `minimum_interval_ms` fra 500 ms e 10 s
  (`MAX_INTERVAL_MS`, detto nel log), attese a fette di 500 ms. Alle auto ENUM o che non dicono il tipo stato,
  svolta e distanza insieme come prima, un giro al massimo per intervallo col dato più recente (`owed`). Alle
  auto IMAGE un messaggio alla volta, stato → svolta → distanza; dopo una svolta passa prima la distanza in
  attesa. **Immagine** (`ClusterImage`, `ClusterArrow` + test), solo alle auto IMAGE: campo 4 = PNG della misura
  chiesta (oltre 1920x1080 pixel ridotto con la stessa forma; sotto 32 bit sfondo nero). È l'icona della manovra
  dell'app (bianca se ne lascia il colore all'host) nell'80% del lato corto su sfondo trasparente, o una freccia
  di myCar per tipo di manovra centrata sul suo ingombro. Si rifà solo se cambiano manovra o icona; a 1920x720
  pesa 9-26 KB; senza PNG la svolta non parte. **Rete di sicurezza** (`ClusterBlame`, `ClusterRecord` + test,
  `ClusterCars`; preferenze `mycar_cluster_no_turns`, `_strikes`, `_held`, `_notice`; chiave come `CarRoutes`):
  è un episodio una chiusura dell'auto (EOF, IOException, ByeBye esclusi 1, 2, 6, 7, 8) entro 3 s dai messaggi
  che aprono una navigazione (stato, prima svolta, prima distanza); "non in navigazione" chiude la finestra.
  **Due di fila** e l'auto riceve solo "non in navigazione" all'apertura. Un'apertura che passa la finestra
  azzera il conto; un'auto che mostra una navigazione per un minuto dopo la prima svolta non è mai più
  incolpata; gli errori di myCar stesso non contano (solo `IOException`). Al viaggio dopo l'auto lo dice una
  volta da ferma (`CarDriving.videoAllowed`, dopo 10 s, poi ogni 5 s); nelle impostazioni dell'auto, sotto
  Connessione, "Instrument cluster: No turns for this car" con "Send turns again". Il log della svolta non
  scrive la strada. Scatto `cluster-turns` (solo se nominato; `--ez clusterTurns true`, `ClusterPreview`). Una
  revisione indipendente ha trovato falsi positivi della rete (A1), errori di myCar presi per l'auto (A2), ritmo
  cambiato alle auto ENUM (A3), la distanza che poteva non partire (A4), attese senza tetto (A6), corsa
  start/stop (A7) e nessun avviso (A8): corretti; non fatto A5 (ripiego sui soli codici, che facevano cadere la
  MG). Da provare se la MG cade ancora: la MG non manda mai INSTRUMENT_CLUSTER_START (32769) e myCar manda
  comunque. Non provato con un'auto vera.
- **Indirizzo del telefono che toglie la musica Bluetooth** (0.7.6, branch `fix/phone-address-guard`; dal log
  della Golf dell'utente: con l'indirizzo scritto la MIB Standard2 dietro lo smartBox riceve la PairingRequest,
  risponde `already_paired=true` e da lì non prende più l'A2DP; senza, lo prendeva sempre). Regola pura
  `car/PhoneAddressGuard.kt` + `PhoneAddressGuardTest`, memoria per auto `car/PhoneAddressCars.kt` +
  `PhoneAddressCarsTest` (hash dell'indirizzo dell'auto), sguardi in `car/PhoneAddressCheck.kt`: armato da
  `MyCarRunner` con l'immagine, una volta per viaggio, solo per un'auto già accoppiata che dichiara il suo
  indirizzo. Senza indirizzo detto, 5 minuti dopo l'immagine e con myCar sullo schermo dell'auto
  (`CarFocus.projected`; sul suo schermo lo sguardo aspetta), se l'A2DP verso l'auto c'è l'auto è ricordata per
  quel collegamento (`bluetooth_music_without_address_wireless` / `_cable`: in Wi-Fi un'auto può conoscere il
  telefono dall'avvio senza fili). Con l'indirizzo detto (`CarBluetooth.toldAddress`), 2 minuti dopo: se l'A2DP
  manca, il vivavoce c'è, la strada del viaggio non è «Tramite myCar» (Automatico conta, anche passato da myCar
  proprio per l'A2DP che mancava) e l'auto era ricordata per lo stesso collegamento, non le si dice più
  (`phone_address_not_told`, per l'auto, anche nella PairingRequest di un abbinamento). Profili da
  `getProfileProxy` (A2DP, HEADSET) all'indirizzo dell'auto; una chiamata rimanda lo sguardo (15 s, uno in più
  dopo la fine; al massimo mezz'ora). Il guidatore lo sa da una riga nel log, una scheda sulla Home fino a OK
  (`ui/PhoneAddressCard.kt`), un avviso in auto da fermi (`CarDriving.videoAllowed`) e una riga sotto
  l'indirizzo in Impostazioni > Connessione: dicono che dal viaggio dopo l'auto può di nuovo ricevere la musica
  dal Bluetooth, non dove andrà (con Automatico lo decide il viaggio). Salvare di nuovo l'indirizzo lo ridice a
  tutte e dimentica la storia delle auto da cui era tenuto. Niente falsi allarmi: la Tayron (Bluetooth solo per
  le chiamate, strada «Tramite myCar») non scatta mai; un'auto che ha bisogno dell'indirizzo non arriva a 5
  minuti con l'immagine senza (Bolt 2021 riattacca a 25 s, Civic 2020 tiene il suo schermo). Limite: chi l'ha
  scritto nella 0.7.4/0.7.5 non ha viaggi senza indirizzo ricordati, e la rete scatta solo dopo uno di almeno 5
  minuti; per loro la voce ora dice a chi serve e che può togliere la musica Bluetooth ad altre auto (9 lingue e
  piratesco). Senza indirizzo scritto non cambia niente. Una revisione indipendente ha trovato B2 (strada
  «Tramite myCar» e frase della scheda) e B3 (schermo dell'auto, cavo e senza fili), corretti. Non provato in
  auto.
- **Cinema per le altre app** (0.7.6, richiesta dal forum di GrapheneOS: «i video di NewPipe e simili»,
  approvata dall'utente; `car/OpenInCinemaActivity.kt`, `car/CinemaAsks.kt`, regola pura `cinema/OpenWith.kt` +
  `OpenWithTest`): myCar compare come «myCar · Cinema» (nomi, uguale in ogni lingua; «myCar Debug · Cinema»
  nella build di prova) fra i lettori esterni e nel foglio di condivisione. NewPipe
  (`NavigationHelper.playOnExternalPlayer`) manda ACTION_VIEW con l'indirizzo dello stream, il tipo del formato
  (`video/*` se manca; `application/x-mpegURL` per HLS, cioè le dirette; DASH escluso, il MediaPlayer non lo
  suona) e il titolo in EXTRA_TITLE; ai lettori esterni passa solo flussi con video e audio insieme. Android
  confronta i tipi lettera per lettera: nel manifest le due grafie di HLS. «Condividi» (ACTION_SEND text/plain):
  il primo indirizzo web del testo. Activity senza interfaccia (Theme.NoDisplay) e un messaggio sul telefono.
  Con l'immagine dell'auto (o uno schermo dell'auto su) l'auto passa a Cinema (`CinemaAsks.watch` in `CarUi`;
  torna a un Cinema già sulla strada), che suona il video nel suo lettore (ripresa per titolo, `open:<titolo>`:
  l'app rimanda lo stesso video a un indirizzo nuovo; mai per HLS, che può essere in diretta) o apre il link sul
  suo sito (`OpenWith.siteFor`: stesso host o sotto, youtu.be, senza www./m., host dopo l'ultima @ e fino a una
  barra rovesciata) o, se di nessuno, in un sito a sé col nome dell'host, che "Continue" riapre
  (`CinemaContinue.Site.shared`). Auto che si sta collegando: aspetta al massimo 2 minuti. Senza auto: lo dice e
  non tiene niente (un video che parte da solo a un collegamento dopo sarebbe una sorpresa). Con lo schermo del
  telefono condiviso la condivisione finisce, come per l'assistente. Solo http/https: niente file né content://
  (il permesso temporaneo finisce con la schermata che lo riceve; i video del telefono sono già in "On this
  phone"), niente intent che fanno partire altro, niente intestazioni dall'intent. Nel log della release niente
  indirizzi né host delle pagine condivise, e la loro icona non resta sul telefono. Valgono tutte le regole di
  Cinema. Il lettore non salta più in un flusso senza durata (±10 s lo riportavano all'inizio). Scatti
  `open-with`, `open-with-moving`, `open-with-stream`, `open-with-share`, `open-with-page`, `open-with-no-car`
  (intent mandati con adb 5 s dopo l'avvio); il video di prova del runner ha l'indice in testa (`+faststart`):
  il server di Python non risponde a Range, e col moov in fondo il MediaPlayer si fermava con l'errore
  1/0x80000000. Verificato in CI (API 34). Non provato: NewPipe vero, flussi di YouTube (al runner YouTube
  chiede di accedere), in auto. Una pagina condivisa, o un video verso un indirizzo di casa
  (`LocalNetwork.isLocal`, più `OpenWith.oddHost`), si apre solo col sì del guidatore sulla scheda in auto
  (`CinemaAskCard`: domanda, host e porta, "Not now" / "Open"; Indietro con lo slot `CardBack.cinemaAsk`; una
  nuova condivisione prende il posto della precedente; senza risposta va via dopo 2 minuti): qualsiasi app può
  mandarne una, e myCar raggiunge la rete di casa dove quell'app non può. I video verso internet (l'uso con
  NewPipe) restano senza domanda. "Continue" riapre una pagina condivisa senza scheda (è già un tocco del
  guidatore). Scatti anche `open-with-home`, `open-with-twice`; il video di prova di internet è quello di MDN (i
  video d'esempio di Google rispondono 403).
- **Griglia delle app in elenco e impostazioni dell'auto che restano al loro posto** (0.7.6,
  `feature/grid-vertical`; dal forum di GrapheneOS: «lo scorrimento da sinistra a destra è meno comodo di quello
  dal basso in alto di Android Auto»; un tester con Ioniq 5 2023 e Ottocast: «le impostazioni tornavano su molte
  volte»). "App grid" (chiave `car_app_grid`, `AppGridLayout`): **Pages** (predefinita, com'era) o **List**, in
  auto prima di "Hidden apps" e sul telefono in Schermo dell'auto. L'elenco sono le righe delle pagine
  (`TileRow`: stesse icone, colonne e spazi) una sotto l'altra, tutte composte, in `car/AppGridList.kt`
  (`Column` + `verticalScroll`): la manopola passa da un'icona all'altra e l'elenco scorre fino a quella col
  fuoco. Al posto dei puntini una barra di 4 dp nel margine destro, solo se c'è altro sotto. Anteprima `--es
  appGrid list` (solo debug), scatti `grid-vertical`, `-large`, `-800x480`, `-knob`, `settings-app-grid`. Le
  impostazioni dell'auto tornavano in cima perché la schermata esce dalla composizione ogni volta che sopra ne
  va un'altra (dashboard dall'orologio del dock, "Choose" del Jolly, tasti dell'auto) o lo schermo dell'auto si
  rifà (encoder nuovo, fine della condivisione). Prima/dopo nello scatto `settings-scroll`; l'auto che mostra il
  suo schermo e lo ridà non c'entra. Ora `car/SettingsScroll.kt` (+ `SettingsScrollTest`) tiene la posizione in
  dp per il viaggio e `CarServices.shutdown` la azzera. Da verificare con un log del tester: tocchi a raffica da
  un adattatore senza fili presi per lanci veloci. I tocchi hanno l'ora d'arrivo, non quella dell'auto (campo 1
  dell'InputReport, mai letto). Dashboard e Meteo restano com'erano: mostrano "adesso" in cima.
- Dipendenze approvate dall'utente: OkHttp e androidx.webkit (Plancia integrata), Media3 `session`
  e `common` (Xonos integrato: Apache 2.0, nessuna telemetria).
- Sfondi "stile umbrelOS" ma **nostri**: le immagini di umbrelOS hanno una licenza non libera.
- F-Droid: per ora variante **senza** voce offline (sherpa-onnx).
- Auto-updater delle build GitHub: rilasci di `sirionero/mycar`; disattivato nella build F-Droid.

## Backlog

- **Puntini delle pagine sopra le parti** (revisione degli scatti della 0.7.0): sulle pagine del guidatore
  la pillola dei puntini e della matita sta sopra il fondo delle parti; a 800x480 copre la tessera in basso
  di Plancia (un tocco va ai puntini). Da valutare: farla svanire anche lì come sulla mappa, o lasciarle
  la sua striscia sotto le parti.

- **Spotify Web e "Nascondi l'app del telefono" a inizio viaggio** (dalla caccia della 0.7.0, da provare
  con un account Premium): myCar mette in pausa l'app Spotify del telefono e apre Spotify Web con
  l'Autoplay; l'app in pausa resta il dispositivo attivo di Spotify Connect, quindi il Play della pagina
  potrebbe farla ripartire sul telefono (altoparlante, o niente in auto) e poi dire "suona su un altro
  dispositivo". Correzione pensata, solo nella pagina: un Play di myCar seguito entro 10 s da `remote`
  manda una volta "pause" e un avviso suo ("scegli 'Questo browser web' nei dispositivi di Spotify",
  9 lingue). Prima va visto succedere.

- **Schermo dell'auto tutto nero** dopo un collegamento riuscito, a volte (segnalato dopo la 0.6.3):
  non trovato leggendo il codice. Escluso: il display virtuale di myCar non si spegne col telefono
  (privato, `FLAG_NEVER_BLANK` in `VirtualDisplayAdapter`), la `Presentation` non dipende dalla
  schermata di myCar (token suo). Serve il log di un viaggio così (Impostazioni > Aiuto e strumenti >
  Log in tempo reale > Precedenti > Condividi). Ipotesi principale (secondo giro della notte 0.6.7): i
  fotogrammi chiave di recupero (ogni 5 s) non portano SPS/PPS, che vanno solo nel CodecConfig iniziale;
  un'auto che lo perde e non ripete PROJECTED (che rimanda CodecConfig, `pictureAgain`) resta nera.
  Rimedi possibili, da provare col log: rimandare il CodecConfig prima dei fotogrammi chiave nei primi
  30 s, o `KEY_PREPEND_HEADER_TO_SYNC_FRAMES` nell'encoder; toccano il video di tutte le auto.
- **Audio tramite myCar a ogni ricollegamento**: la domanda di Android si rifà a ogni sessione nuova dello
  stesso viaggio (Wi-Fi che cade, l'auto che riavvia Android Auto). Idea della revisione: a fine sessione
  "parcheggiare" la proiezione (servizio in primo piano tenuto ~2 minuti, lettura ferma, telefono non più
  muto) e riprenderla se un'auto torna, invece di chiederla di nuovo.
- **Dal secondo giro della notte 0.6.7, non fatti** (trovati e confermati da uno scettico, lasciati per
  tempo o rischio): chiamate dall'auto ferme sulla scelta della SIM (doppia SIM con "Chiedi ogni volta":
  un tasto Chiama per SIM); "Leggi"/"Rispondi" del popup che legge solo l'ultimo messaggio
  (`VoiceReply.read` con `unreadCount`); la risposta immediata dell'altra persona presa per l'eco della
  propria (`CarNotifications`, guardare il mittente dell'ultima riga); Assist che continua in Home
  Assistant dopo che myCar ha smesso di aspettare (`unsubscribe_events`); "chiudi il garage" mentre Home
  Assistant si ricollega; il token di Home Assistant confrontato per host e non per origine
  (`PlanciaWeb`); cavo staccato a viaggio in corso che non passa da solo al senza fili (fattibile con
  guardie, ma è un comportamento nuovo: da proporre all'utente).
- Scatti `lang-*` per controllare le lingue.
- Fatto nella 0.6.0: impostazioni del telefono in gruppi (`SettingsPage` in `ui/SettingsScreen.kt`;
  dalla 0.6.6, sotto la scheda dell'anteprima: Generali, Schermo dell'auto, Musica, Connessione, Voce,
  Plancia, Cinema, Aiuto e strumenti, Informazioni; `SettingsAt` apre il gruppo giusto, Indietro torna
  all'elenco; visti negli scatti `phone-settings` e `phone-music`) e temi del
  telefono (`ui/PhoneTheme.kt`, chiave `phone_theme`: come il telefono, chiaro, scuro, nero AMOLED,
  Monokai; barre di sistema col tema, `phoneDark()` al posto di `isSystemInDarkTheme`).
- I bagliori del veliero e della bandiera sono immagini dipinte una volta (`ui/Glow.kt`): come
  sfumature della GPU l'emulatore lasciava puntini (cerchio) o cadeva (quadrato).

- Voce offline nella build F-Droid: compilare sherpa-onnx (+ onnxruntime) da sorgente nella
  ricetta F-Droid invece del `.so` precompilato.
- **Comandi rapidi di Home Assistant** come widget nelle pagine (cancello, garage), sul
  collegamento di Plancia integrata (`HomeAssistant`).
- **Xonos integrato, il resto**: server trovati in rete (mDNS `_mass._tcp`, `NsdFinder` di Xonos)
  e primo account di un Music Assistant nuovo nelle impostazioni del telefono (la pausa mentre l'auto
  tiene le casse per un momento c'è dalla 0.7.4: `PausedForCar`). Xonos e Plancia restano app a sé per
  Gearslip.
- **Spotify con Client ID personale**: Web API (PKCE) per libreria, salvati e ricerca nell'auto
  e per far suonare l'app Spotify del telefono; l'audio arriva via myCar o Bluetooth. Regole di
  Spotify da febbraio 2026: Premium del proprietario, massimo 5 utenti per Client ID, alcuni
  endpoint tolti. Il motore ufficiale per terze parti (Spotify Soloist, quello di Music
  Assistant) gira sul telefono sotto proot, dentro il server Music Assistant dell'app Xonos (dalla
  0.1.49; in modalità Spotify Connect dalla 0.1.54): con Xonos dentro myCar collegato a quel server
  Spotify suona già così. librespot non suona con gli account creati da dicembre 2024.

## Note F-Droid

Punti da risolvere (branch dedicato): `app/src/main/jniLibs/.../libsherpa-onnx-jni.so` prebuilt,
`speech-models/*.bin` non usati, auto-updater (`REQUEST_INSTALL_PACKAGES`) da disattivare nella
build F-Droid, voce `play-services-oss-licenses` inutilizzata in `libs.versions.toml`, toolchain
JetBrains via foojay. Già fatto: nessun server di statistiche (`myCarStatsUrl` vuoto).
