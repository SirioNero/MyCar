myCar 0.5.13-alpha: a scan of the whole app, and Plancia and Xonos that say why they can't connect.

## Plancia and Xonos through Tailscale
- With Home Assistant or Music Assistant reached through Tailscale, as http://umbrel:8123 or a 100.x address, Plancia and Xonos inside myCar now say why they can't connect instead of "isn't answering": Tailscale leaving myCar out (in Tailscale on the phone: Settings, App split tunneling, let myCar in), a Tailscale name that isn't found, or Android 17 keeping myCar off your home network.
- Inside a VPN, Android keeps myCar off the car's Wi-Fi: with myCar let into Tailscale, connect the car with the cable. myCar says so when the wireless connection runs into it.

## The drive
- myCar stays in the foreground for the whole drive, with a "connected to the car" notification, as Android Auto does: with the phone locked, Android 17 no longer refuses it the audio focus or freezes it. It keeps the phone awake and, over wireless, the Wi-Fi at full speed. If Android refuses it as the car connects (the phone locked), it's tried again as soon as you open myCar.
- When the car ends the session by itself (most often over wireless), myCar now ends it too: the phone's sound comes back, Xonos stops, the car's picture and the drive's services close.
- Back on the phone's Home no longer ends the drive when the cable opened myCar, nor while a car is connecting.
- Android's question for the car audio, asked with the phone locked, waits for you to open myCar (the car says so) instead of counting as a "no" for the whole drive. The same for screen sharing.
- A song already playing on the phone when you plug in is the one the car shows, even with a music app chosen beside the map; a music app you closed stays closed.
- A music app that closes or crashes no longer leaves the player stuck on "playing": myCar connects to it again, and says so if it keeps closing.
- A music app you pick in the car stays yours even when it can't connect: you see "Open Spotify" or "Try again", instead of the app beside the map taking its place.
- Android's questions (the microphone, the car audio, screen sharing) come one at a time: accepting the second no longer ends what the first started.

## Voice, calls and messages
- "Call Luca" no longer calls Gianluca: the whole name or a whole word of it comes first, and with more than one person, or only part of a name, the car asks first.
- "Stop navigation" ends the route instead of pausing the music. "Navigate to" said in the drive's first seconds is no longer lost.
- The music no longer stays paused for the rest of the drive after myCar speaks, and a reply counting down is sent when you tap the microphone.
- With the phone locked and its microphone silenced by Android, the car says so at once instead of "Didn't catch that".
- A microphone refused in the setup guide and then again in the car: the phone now says it's blocked and how to allow it, instead of leaving the car silent.
- Calls: the right name on the card during a call waiting, and the call under way stays on screen after you turn a second one away. Calls from the car go through Android's own call service, so they start even with the phone locked.
- Messages: the unread dot on the dock's clock shows, and a chat updated with nothing new no longer pops up again.

## Screen sharing
- The car's keys (Back, Home, map, music, phone) bring you back to myCar.
- Android's question offers only the whole screen; fast taps and scrolls to the edge no longer leave a finger pressed on the phone.
- With the car audio through myCar, the phone's music pauses while its screen is shared, instead of playing out of the phone's speaker.
- Sharing the screen no longer leaves the car without music for the rest of the drive: when it ends, Android asks again for the car audio. It no longer asks during the sharing, which saying yes would end. Xonos inside myCar keeps playing while the screen is shared.

## Plancia, Xonos and the rest
- Plancia: no "Set up Plancia" for a moment when it's set up; Back no longer returns to the home address you left; a token Home Assistant refuses is no longer tried again and again (it waits for a new one), while a proxy's 401/403 is tried again like any failure.
- Xonos: pause stays paused without signal and while the assistant speaks; the sound comes back right after a tunnel; with the audio focus refused it still pauses for calls; opening another music app pauses it; its menu no longer holds up the car's screen; Sign in tries the address away too.
- Xonos and calls: after a call it no longer takes the sound from the music app you switched to, and a signal that comes back during a call waits for the call to end, so the song doesn't play on silently meanwhile.
- The browser types into search boxes that aren't plain fields.
- Wireless: a home Wi-Fi with addresses like the car's no longer takes every try; two start-ups no longer steal the car from each other; the car's Wi-Fi is let go at the end of the drive even when the car closes Bluetooth last; plugging the cable in just as the wireless connection starts no longer closes myCar.
- And many smaller ones: Android 12 crashes on plugging in, the location for car maps (asked properly, and enough when approximate), the phone's typing bar, update checks, voice model downloads, lyrics, album art sent to the car.

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

