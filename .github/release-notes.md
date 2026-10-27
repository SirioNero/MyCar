myCar 0.5.12-alpha: Plancia and Xonos at home on Android 17.

## Fixed
- Plancia and Xonos inside myCar couldn't reach Home Assistant and Music Assistant at home, at the very addresses the Plancia and Xonos apps use, such as http://192.168.1.10:8123 or http://homeassistant.local:8095. It wasn't http: Android 17 keeps an app made for it, as myCar is, off your home network until you allow its Nearby devices permission, and myCar asked for that only to connect wirelessly. Apps made for older Android, as the Plancia and Xonos apps are, keep that access without asking.
- myCar now asks for it: when you open it on the phone with Plancia or Xonos set to an address at home, before Save and test or Sign in in their settings, and in the setup guide, together with Bluetooth. If you already allowed Bluetooth, Android allows it without asking again.
- If it's still missing, the car says so - in Plancia, Xonos and the browser - instead of "isn't answering" or the web engine's own error, and Settings > Plancia and Settings > Xonos show Allow the home network until it's allowed.

## Good to know
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

