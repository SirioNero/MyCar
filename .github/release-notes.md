myCar 0.6.4-alpha: a voice assistant that never hangs, phrases not cut short in other languages, and connecting by itself with the phone locked.

## Voice assistant
- After words it couldn't act on, the assistant could sit on "…" for up to a minute with the microphone deaf: it waited up to 40 seconds for Home Assistant, and every new request queued behind that one. Now Home Assistant gets 15 seconds, a new request or Cancel drops the one still waiting, and the microphone pressed while it works starts over.
- Whatever stalls - the microphone not opening, a phrase that doesn't end, words that never come, a speech engine that never says it's done - the assistant closes by itself after a while.
- When Home Assistant asks something back, the microphone no longer opens by itself, and the next words no longer go to it no matter what: myCar's commands first, then Home Assistant, carrying on the conversation for five minutes.
- While it waits for Home Assistant, it says so.
- In languages other than English, the end of a phrase was left to the live model, which knows English only: in Italian it could go a second without writing a word while the driver was still talking, and the phrase was cut there. Now its end counts once the microphone is quiet too (back to the car's own noise), at most 2.5 seconds later; and if it writes nothing at all, 1.2 seconds of quiet after speaking ends the phrase.
- myCar in a language with no voice model for it on the phone listens in English: the car now says so over "Listening…", with the model to download.
- The log says which language and model the assistant listens with, and how each phrase ended.

## Connecting wirelessly by itself
- The car coming up over Bluetooth reached myCar as an intent. To a myCar screen already open but stopped, with the screen on and the lock screen up (the car's Bluetooth wakes the phone), Android hands it over only once that screen is back in front - after the phone is unlocked. Now, with a myCar screen open, the connection starts straight from it.
- Android's "Allow" for connecting by itself was asked only with myCar in front: with the phone in a pocket as the car's picture came up, it might never show. Now myCar remembers the car and asks as soon as its screen is up.
- Settings > Connection shows the cars myCar connects to by itself.
- The log says at start how many cars are allowed, and whether Android watches for them.

Not tried in a real car yet.

## Also in 0.6.3-alpha
- After a wireless drive with a car that calls the phone, myCar keeps the phone's Android Auto service published for ten more minutes, with nothing shown on the phone. After a short stop - the car's Bluetooth still connected, so Android never says the car came back - the car finds the service when its Android Auto starts again, and the drive comes back by itself instead of waiting for a tap.
- It stops at once for the cable, a tap on Connect, or Disconnect.
- With the phone locked in a pocket, Android refused myCar the car's Wi-Fi: it hands a car's network only to an app in front or one with a foreground service, and myCar's drive service only started once the picture was up. Now it starts as soon as the car's Bluetooth channel opens, so connecting by itself works with the phone in a pocket too. A start-up that doesn't get to a drive gives it back.
- The car coming back over Bluetooth while myCar is still listening after the drive starts a fresh, full wait instead of being turned away.
- A car that didn't call within the wait that its own Bluetooth started is no longer forgotten as one that calls the phone.
- On Automatic with the music on Bluetooth, a car that drops its Bluetooth music for a call now gets a full ten seconds after the call ends to bring it back, before the sound moves to myCar.

## Also in 0.6.2-alpha
- Through myCar: when the car takes the speakers for a moment (its own voice, for example) it should give them back when done. A car that never did left the music off for the rest of the drive, as the phone played on with no pause to ask again after. Now, after 20 seconds of the phone playing with no word from the car, myCar asks for the speakers again. During a call the phone's music is paused or silenced, so it doesn't ask then.
- Automatic, with the music on Bluetooth: when the car's Bluetooth music connection dropped - for a call, or for a moment - myCar moved the sound to its own link at once, which opened the car's Android Auto music channel and could put Android's sharing question up mid-drive. Now it waits for the connection to come back: the sound moves only if it's still gone 10 seconds later, with no call on.
- A sound button in the player, beside the map and full screen: a Bluetooth sign when the sound reaches the car over Bluetooth, a car when it goes through myCar. A tap sends it the other way - every app's sound, Spotify and Cinema included.
- myCar remembers the way for each car: a choice made with a car connected (the sound button, the car's settings, or the phone's) is that car's and comes back at its next drive, so one car can go through myCar while the others stay on Automatic. A choice made on the phone with no car connected is for every car.
- Most cars call the phone's Android Auto service as soon as their Bluetooth connects. myCar used to try the car's own service first and publish its own only once that failed, a few seconds later - by then a car could have looked, found nothing and given up, and the drive didn't start until the phone was reconnected from the car's menu. Now myCar remembers, for each car, that it calls the phone, and next time is ready for it at once.
- Started by the car's Bluetooth, myCar now waits up to five minutes for the car to call, for cars whose Android Auto takes a while to boot after the engine starts.

## Also in 0.6.1-alpha
- The phone's themes colour the band under the status bar too.

## Also in 0.6.0-alpha
- myCar in your language - English, Italian, Spanish, German, French, Portuguese, Greek, Russian or Chinese - on the phone, on the car's screen and in the voice assistant.
- Everything on the phone and on the car's screen - setup, settings, help, the car's apps and screens, notices, what's new - in English, Italian, Spanish, German, French, Portuguese (Brazilian), Greek, Russian or Simplified Chinese: the languages of the Xonos app.
- myCar follows the phone's language. To pick another: Settings > Language on the phone, or the Your language page shown once after this update. On Android 13 and later Android's own settings for the app (App languages) set it too.
- A new language shows at once on the phone, and on the car's screen at the next drive at the latest. On Android 12 the phone's screen starts again with it, unless a drive is on.
- Times of arrival, the time in quick replies and the dates in the logs are written the way your language writes them.
- Xonos inside myCar speaks your language, and Music Assistant gets it too, for its own words (labels, errors, titles).
- Settings opens on a short list of groups - General, Connection, Voice, Plancia, Xonos, Cinema, Help and tools, About - each a page of its own; Back returns to the list.
- Themes for the phone app: like the phone (Material You, the default), light, dark, black (AMOLED) or Monokai, in Settings > General. The car's screen keeps its own.
- The voice assistant follows myCar's language, now in Greek, Russian and Chinese too: commands and answers written for each, Greek and Russian names and places understood in the forms they take in a sentence, Chinese with or without traditional characters.
- Listening: Greek and Russian with the multilingual Parakeet model, as Italian and Portuguese; Chinese with Whisper small (358 MB), which is told the language rather than guessing it. Settings > Voice says which one to download.
- Contacts and chats are found without minding accents: "Γιώργος" finds "ΓΙΩΡΓΟΣ", "Семён" finds "Семен".
- Quick replies to messages go out in Greek, Russian and Chinese too.
- Listening in Greek, Russian and Chinese hasn't been tried on a real phone yet.

## Also in 0.5.22-alpha
- With Home Assistant set up in myCar (Settings > Plancia on the phone), what the car's voice assistant doesn't know goes to the voice assistant you set up in Home Assistant: Claude, Gemini, ChatGPT, a model at home, or Home Assistant's own, which runs the house. It answers in your language, carries on a conversation ("and tomorrow?") and asks back when it needs to.
- With the phone locked in your pocket and the phone's assistant chosen, myCar listens itself and Home Assistant answers: nothing to unlock.
- Settings > Plancia > Ask Home Assistant in the car turns it off.

## Also in 0.5.21-alpha
- The voice assistant in your phone's language: Italian, Spanish, French, German or Portuguese, with the speech model that knows it (Settings > Voice).
- A third certificate, CarService - Android Auto's own - for the cars that check phones most strictly; if your car turns down the other two, Home offers it.
- Leaving Cinema, the music that was playing before the film starts again by itself.
- After every update the pirate flag flies with the new version; big releases bring a pirate ship.

## Also in 0.5.20-alpha
- Phone: a Recents tab with your latest calls (missed ones in red, a tap to call back), and Call back on a missed call's popup, on LineageOS and other ROMs too.
- Voice: "read my messages", "reply to Anna", "call back", "redial", "call Mom on mobile", "when do I arrive?". Play - said, or the ▶ on the steering wheel - starts the music app you used last when none is open.
- Quick replies to messages go out in your phone's language. After a tap on Reply, myCar reads the message and asks straight away for your reply.
- While a call rings, the next turn shows in the dock. The assistant's errors go by themselves after a few seconds.
- No more dead ends: apps that wouldn't start, lists that didn't load and searches that found nothing get Try again, Close or Open on the phone.
- Dropdowns on web pages open on the car's screen as a list to tap, in the browser, on Cinema's sites and in Plancia. The car's Back key goes back inside apps.
- Stop navigating takes two taps. Bigger buttons where they're most used; Extra large fits better on small screens.
- Steadier: a music library with an empty name, contacts failing to load or a voice command going wrong no longer close myCar.
- Cinema: Continue opens again what you were watching; a film picks up where it stopped after a network drop; TV channels try again by themselves; a magnifier finds a channel by name.
- On the phone: the welcome's band became a pirate flag; buttons stay above three-button navigation; removing certificates, accounts, TV lists and voice models asks first; Disconnect asks first during a drive.

## Also in 0.5.19-alpha
- A new app in the grid: your video sites as tiles, each with the icon it gives itself - YouTube, RaiPlay and Twitch to start with, and any you add on the phone (Settings > Cinema, where More sites adds Dailymotion, Vimeo, La7, TED, Arte, NASA+ and the Internet Archive with a tap). Each opens in a browser made for video: full screen when the player asks for it, the car's keyboard for searching.
- On this phone: the videos on your phone, newest first, with their pictures and lengths. They start again where you left them. Allow it once, on the phone or from the car.
- TV: add an IPTV list (M3U) in Settings > Cinema: TV - from your provider, a public list or a TV server at home - and its channels show with their logos and groups. The list is kept on the phone, so the channels are there even when its server doesn't answer.
- Phone videos and TV play on Android's own player, with nothing extra to install: tap for the controls (back 10 seconds, play or pause, forward 10 seconds).
- Video shows only when the car is parked: while it moves the picture is covered and the sound carries on. That's a setting, on by default, which myCar strongly advises keeping on. Protected video (Netflix, Prime Video, Disney+, DAZN) most likely stays black in the car.
- Beside two halves and four parts, a page can now be a whole page, a half beside two quarters (on either side), or a wide strip above or below two quarters. On the page, the pencil, then Layout: each layout is shown as a picture.
- What the page shows stays, in order, as far as the new layout has room.
- Vela Maps: the turn arrow changes with the turn again, on the routing card, in the "then" row and in the dock (it kept the first arrow, "continue").
- A dropdown on a web page - in the browser, in Plancia, on a Cinema site - no longer closes myCar (and the drive with it). It doesn't open on the car's screen.
- Cars that never confirm the audio they get (some 2024 Volkswagens among them) no longer lose about two seconds of sound at the start of the music, after every call, when the radio gives the speakers back, and in the middle of every spoken direction.
- Wireless: a car that ends its connection and starts over at once comes back by itself, and only cars you allowed can make myCar connect by itself. With a drive already on the cable, the car's Bluetooth no longer brings myCar up.
- Opening myCar again during a drive (from its notification, or by the car arriving over Bluetooth while Android's question was up) no longer ends the music or screen sharing of the drive under way. A Bluetooth keyboard or steering-wheel remote connecting mid-drive no longer ends it either.
- Calls: Decline works on a call that's already ringing when the phone was slow to show it (it hid the card and the phone kept ringing).
- Xonos inside myCar: a passing "access denied" or "not found" from something in front of Music Assistant no longer hands your music to the Xonos app for the whole drive. With Bluetooth, the music comes back by itself after the phone's audio restarts.
- Plancia and Xonos with a VPN other than Tailscale (NetGuard, Proton, a firewall) that leaves myCar out: a name such as http://homeassistant:8123 is reached directly, as it should, instead of being taken for a Tailscale one.
- Spoken directions start again on the next drive if the phone's text-to-speech wasn't ready.
- Initial setup: Back on Android's permission question no longer makes it say Android won't ask again.
- The agenda's date changes at midnight; the player beside the map keeps all its buttons at the Large and Extra large sizes; the focus ring for cars with a knob shows on the players' buttons too.
- Lighter on the phone: moving wallpapers redraw once per frame for every copy on screen, the cover isn't processed again at every play and pause, and players out of sight stop updating their time.

## Also in 0.5.18-alpha
- After your first drive without the cable, Android asks once whether myCar may connect to that car by itself. Allow it, and from then on myCar connects wirelessly whenever the car starts and reaches the phone over Bluetooth: no tap, the phone can stay in your pocket.
- Only cars that have worked wirelessly are asked about. Plugged in, the cable wins; a drive already under way is left alone.
- Settings > Connection > Connect wirelessly by itself turns it off. Turned back on, a car you said no to is asked about again at its next wireless drive.
- Wireless adapters that send their Wi-Fi details again once the picture is up (ZLink ones) no longer drop the drive a second in.
- With the music on Bluetooth - the Bluetooth setting, or Automatic when the car takes the phone's music over it - myCar leaves the car's Android Auto music channel closed: many cars switched their source to Android Auto the moment it opened, and the Bluetooth music stopped. It opens as soon as the sound has to go through myCar.
- myCar no longer sends the start of the music twice when the car answers at the very moment it starts, which could make an older head unit drop the connection.
- Cars that take music only as AAC get its configuration first, as they expect.
- When the car runs out of sound to play, myCar runs a little ahead of it (at most a quarter of a second), so the next slow patch isn't heard as a gap.
- Long messages, like the video's, go to the car in pieces of the size Android Auto itself uses.
- The car's pings come back whole, and when the car says goodbye during a drive myCar answers and ends the drive at once, over Wi-Fi too.
- Fixed a case where myCar could keep the phone busy for nothing when the car said goodbye while still connecting.
- The car's own margins (the edges it crops) stay right at every size in the car's Settings: Large no longer eats the sides, and Small no longer puts buttons under the edge.
- On the car's Phone screen, Ask on the phone shows Android's question for the Phone and Contacts permissions on the phone; the car screen updates by itself once they're allowed.
- Map apps' route options show their time and distance.
- A car app that never answers myCar (some apps only run on Google's Android Auto) is reported after 10 seconds instead of showing Starting... for ever.
- Logs > Earlier shares the log of an earlier launch - the drive before, or one that crashed - straight from the phone. Opening myCar starts a new log, so that one used to need a computer.
- These come from Gearslip's own fixes, picked one by one: what could change how a car that works today connects was left out.

## Also in 0.5.17-alpha
- No Music Assistant at home? Xonos 0.1.42 or later can run one on the phone itself: in the Xonos app, Settings > Make Xonos your Music Assistant server. In myCar, Settings > Xonos > Use the Xonos app's account brings it over, as with any Music Assistant (the first time it comes over by itself).
- Xonos inside myCar reaches that server through the Xonos app, the same way as with Tailscale: as the drive starts, myCar wakes the app, and the app starts its server. The first start takes a few seconds: the car says the server is still starting, and myCar keeps trying.
- With the server on the phone, the Xonos app doesn't take Xonos's place in the car when it can't connect: it would play from the same server.

## Also in 0.5.16-alpha
- To connect wirelessly with Tailscale on, myCar has to be left out of Tailscale (inside a VPN, Android keeps myCar off the car's Wi-Fi), and then Plancia and Xonos inside myCar couldn't reach a Tailscale address such as http://umbrel:8123. Now they reach it through the Plancia and Xonos apps on the phone, which stay inside Tailscale: your dashboards, their controls and your music, wirelessly too.
- Update the apps - Plancia (the latest release) and Xonos 0.1.40 or later - and keep them set to the same addresses as myCar. The apps open connections only for myCar, checked by its signature, and only to the server set up in them.
- A home address works the same way away from home, through a Tailscale subnet route.
- If even the apps can't reach your server, they take the place of the ones inside myCar for the drive, as in 0.5.15.
- Settings > Plancia and Settings > Xonos say which way it goes.

## Also in 0.5.15-alpha
- When Plancia and Xonos inside myCar can't reach your server - a wrong address or token, or no way to it - the Plancia and Xonos apps on the phone take their place in the car for the drive, by themselves: on the app grid, on your pages, beside the map, and as the car's music.
- It happens after 10 seconds without a connection (30 if it was connected and dropped), at once when the token is refused. The next drive tries the ones inside myCar first again.
- Connected with the cable, myCar can stay inside Tailscale: then Plancia and Xonos inside myCar connect directly.
- Xonos connects to Music Assistant as the drive starts, as Plancia does, so it's ready when you open it.

## Also in 0.5.14-alpha
- Setup is one list, with one button for the next step. "Take me back to the initial setup", in How to connect, and Settings > Initial setup bring it back, with nothing deleted or reset.
- Get certificates downloads two public certificates, aasdk (recommended) and Headunit Revived (the spare), each checked against its fingerprint. If your car turns down the first, myCar uses the spare and Home says to connect again. A car with its clock set wrong gets "Check the car's date".

## Also in 0.5.13-alpha
- Plancia and Xonos reached through Tailscale (such as http://umbrel:8123) say why they can't connect: Tailscale leaving myCar out, a name it doesn't know, or Android 17 keeping myCar off your home network.
- myCar stays in the foreground for the whole drive: with the phone locked, Android 17 no longer refuses it the audio focus or freezes it.
- When the car ends the session, myCar ends it too: the phone's sound comes back and Xonos stops.
- Android's questions (the car audio, screen sharing) wait for you to open myCar when the phone is locked, and come one at a time. Sharing the screen no longer leaves the car without music.
- Voice: "call" picks the right contact, "stop navigation" ends the route, and the music no longer stays paused after myCar speaks.
- Dozens of smaller fixes: calls, messages, Plancia, Xonos, the browser and wireless.

## Also in 0.5.12-alpha
- Plancia and Xonos inside myCar couldn't reach Home Assistant and Music Assistant at home, at the very addresses the Plancia and Xonos apps use, such as http://192.168.1.10:8123 or http://homeassistant.local:8095. It wasn't http: Android 17 keeps an app made for it, as myCar is, off your home network until you allow its Nearby devices permission, and myCar asked for that only to connect wirelessly. Apps made for older Android, as the Plancia and Xonos apps are, keep that access without asking.
- myCar now asks for it: when you open it on the phone with Plancia or Xonos set to an address at home, before Save and test or Sign in in their settings, and in the setup guide, together with Bluetooth. If you already allowed Bluetooth, Android allows it without asking again.
- If it's still missing, the car says so - in Plancia, Xonos and the browser - instead of "isn't answering" or the web engine's own error, and Settings > Plancia and Settings > Xonos show Allow the home network until it's allowed.
- Phones on Android 16 or older weren't affected.
- The same permission lets myCar's browser open pages on your home network, such as your router's.

## Also in 0.5.11-alpha
- Xonos, Music Assistant in the car, is now part of myCar: your library - your own files and the services you added to Music Assistant - among the car's music apps, full screen or beside the map, with search, Up next and favorites. In Music Assistant it's a player of its own, called myCar.
- With the car audio through myCar, Xonos plays straight into the car, not through the phone's speakers: Android doesn't ask to share the audio while you listen to it. It asks only once another music app has to reach the car, when you open one there. Over Bluetooth, Xonos plays as the Xonos app does.
- Set it up on the phone, never in the car: in myCar's Settings > Xonos, your Music Assistant's address (and the one away, if you have one), then your username and password. myCar keeps a sign-in of its own, never the password.
- With the Xonos app on the phone (0.1.39 or later), Use the Xonos app's account brings it over with nothing to type; the first time, with nothing set in myCar yet, it comes over by itself.
- Once set up, Xonos inside myCar takes the Xonos app's place in the car, beside the map too. Remove the account in myCar's settings and the app comes back.
- Music Assistant 2.7 or later is needed, as for the Xonos app.
- The Xonos app keeps working with Gearslip, and with myCar for anyone who prefers it.

## Also in 0.5.10-alpha
- Plancia, Home Assistant in the car, is now part of myCar. It connects to your Home Assistant as the drive starts and keeps your dashboard loaded the whole way, so it's there the moment you open it: full screen from the app grid, in a part of one of your pages, or beside the map.
- Home Assistant's ☰ opens Plancia's menu: your dashboards, the one Plancia opens first, the size (60 to 150%) and Reload. Back steps back inside the dashboard before it leaves Plancia.
- Set it up on the phone, never in the car: in myCar's Settings > Plancia, your Home Assistant's address at home, the one away if you have one (Nabu Casa, for example), and a long-lived token. Save and test tries them both.
- With the Plancia app on the phone (0.1.15 or later), Use the Plancia app's account brings its addresses, token, default dashboard and size over, with nothing to type. The first time, with nothing set in myCar yet, they come over by themselves.
- Once set up, Plancia inside myCar takes the Plancia app's place in the car: on the app grid, on your pages and beside the map. Remove the account in myCar's settings and the app comes back.
- Home Assistant at home often has a plain http address, such as http://homeassistant.local:8123: myCar now opens those. The token goes only to your Home Assistant's own pages.
- The Plancia app shares its account only with myCar as published here, signed with myCar's key, and with no other app.
- myCar now loads the phone's web engine while it connects to the car, rather than when the browser or Plancia first opens.

## Also in 0.5.09-alpha
- Car audio through myCar: when the car took its speakers for a moment (a call, the phone's assistant over Bluetooth, the car's own voice, even a navigation prompt that only turns the music down), myCar handed them back for good, and the music stayed off for the rest of the drive. Now myCar waits, and the music comes back by itself when the car is done; a prompt that only turns it down leaves it playing. Switch the car to the radio and the phone's music pauses; press play again and the car comes back to myCar.
- The player, in the style of recent phones: plain controls with nothing behind them, a thin line to seek along with the time played and the time left, and on the full-screen player the cover's colours, blurred, behind everything. The cover now stays in sight while you browse a music app.
- The microphone with the phone locked: assistants such as Claude don't listen over the lock screen, and Android lets no app past it. So with the phone locked or asleep, myCar lights the phone and asks Android to open it, with your face, fingerprint or PIN, and your assistant starts listening as soon as it's open. With nothing to unlock (no lock, or the phone kept open by a trusted device) it goes straight on. This needs myCar turned on under Accessibility, which is what lets it light the phone.
- Android's question to send the car audio through myCar is one tap now: myCar asks for the whole screen, so there's no app to pick first, and the sound of every app reaches the car, not just the one picked.
- Android still asks once each drive before myCar can pass another app's sound to the car. The one-time adb command in Help stops the question for good; Bluetooth never asks.
- To skip unlocking in the car, Android's Extend Unlock (Smart Lock on some phones) can keep the phone unlocked while it's connected to the car's Bluetooth.

