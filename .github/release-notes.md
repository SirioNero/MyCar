myCar 0.5.11-alpha: Xonos inside myCar.

## New
- Xonos, Music Assistant in the car, is now part of myCar: your library - your own files and the services you added to Music Assistant - among the car's music apps, full screen or beside the map, with search, Up next and favorites. In Music Assistant it's a player of its own, called myCar.
- With the car audio through myCar, Xonos plays straight into the car, not through the phone's speakers: Android doesn't ask to share the audio while you listen to it. It asks only once another music app has to reach the car, when you open one there. Over Bluetooth, Xonos plays as the Xonos app does.
- Set it up on the phone, never in the car: in myCar's Settings > Xonos, your Music Assistant's address (and the one away, if you have one), then your username and password. myCar keeps a sign-in of its own, never the password.
- With the Xonos app on the phone (0.1.39 or later), Use the Xonos app's account brings it over with nothing to type; the first time, with nothing set in myCar yet, it comes over by itself.
- Once set up, Xonos inside myCar takes the Xonos app's place in the car, beside the map too. Remove the account in myCar's settings and the app comes back.

## Good to know
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

## Also in 0.5.08-alpha
- Home is now a row of pages. The map's page is as it was, the map with the music or your app beside it; your own pages sit either side of it. Swipe across Home to reach them, wherever your finger doesn't already move something (the map, the browser and car apps keep their swipes), or tap the dots at the foot of Home. Over the map the dots fade with the map's buttons and come back at a touch.
- Ready-made pages: the weather and your agenda on the left of the map, and on its right Plancia beside the browser, when Plancia is on the phone. Plancia shows its default dashboard, with your Home Assistant controls.
- Each page is two halves or four parts. A part holds the weather (now, the next hours and days, as room allows), the agenda (what's coming up in the phone's calendar), myCar's browser, or a car app such as Plancia. Tap the weather for the whole forecast, the agenda for the dashboard.
- To change a page, tap the pencil beside the dots, or hold a widget or the bar over an app: choose two halves or four parts, move the page left or right (past the map too), add a page after it or delete it, then tap a part to choose what goes there. New pages can also be added from the car's Settings > Pages beside the map, which can bring the ready-made pages back too.
- The dock's map button, the car's map key and Back on one of your pages all go back to the map's page.
- One car app runs at a time on the pages, besides the map: a car app on a page runs while that page is in view, and shows as its icon otherwise. Swiping to another page with a different car app starts that one instead.
- The browser's keyboard on a page takes the page's whole width.
- Android widgets from other apps aren't there yet; they're planned for a later version.

## Also in 0.5.07-alpha
- If myCar was closed mid-drive (a crash, or Android ending it) while it was sending the phone's sound to the car, the phone's music stayed muted afterwards. myCar now remembers muting it and gives the sound back the next time it starts.
- Android's question to share audio (or the screen) can stay open on the phone until after the drive. Answering yes then started the capture anyway: the phone went silent with its music sent nowhere, or kept sharing its screen with a notification up. Now nothing starts once the car is gone, and a screen share that started as the car left is ended.
- The big offline voice model was only let go after a voice command: on a drive without any, it held its memory until the car disconnected. It now goes after five idle minutes either way.
- A new log file was kept for every session, forever. Only the 20 most recent are kept now.
- In the Flat controls theme, the wavy progress bar redrew the car's screen every frame, even with the music paused. Its wave now moves with the song.
- The session report at the top of a shared log says which way the car audio went on that drive, and why.

## Also in 0.5.06-alpha
- myCar could close at the end of a drive. With the phone locked and the music on Bluetooth, stopping the car audio or screen sharing that never started was refused by Android, and the refusal closed the app. Now only what started is stopped.
- The weather waited twelve seconds for a fresh location that could never arrive in time, then used an old one or none. It now gets the fresh location.
- Long drives with lots of music could run out of memory: cover art was decoded at full size, up to 36 MB a cover, and up to 48 kept. Covers and app images are now sized for the car's screen, and kept by weight.
- Spoken directions could be watched twice when the car reconnected within half a second.
- The log's times could come out garbled when several parts of myCar wrote at once.

## Also in 0.5.05-alpha
- Car audio: Automatic, now the default. When the car shows myCar, myCar checks whether the car takes the phone's music over Bluetooth. If it does, the phone plays over Bluetooth as before, and Android never asks. If it doesn't, as many cars do while Android Auto runs, keeping Bluetooth for calls, myCar sends the sound through its own link to the car, over the cable or Wi-Fi, as Android Auto does. Android then asks once each drive to share the audio; the one-time adb command in Help stops that. If the Bluetooth music connection drops mid-drive, the sound moves to myCar's link.
- USB audio is now called Through myCar: it always worked over Wi-Fi too. Bluetooth and Through myCar can still be picked by hand, in the car's settings or the phone's.
- If you had Bluetooth picked, setup's old default, you're on Automatic now, which still uses Bluetooth whenever your car plays the phone's music that way. USB carries over as Through myCar.

## Also in 0.5.04-alpha
- Beside the map, under the car's Settings: Music, the default, puts the player beside the map while something plays, as before. Or pick an app to stay there instead. A car app such as Plancia runs in that space, under a bar with its name and Close; a music app such as Xonos shows its player there, ready to play, and becomes the car's music app only if no other is in use. Close puts it away for the drive, and the button at the foot of the dock brings it back from the app grid.
- The microphone now sits at the foot of the dock, just above the app grid button, always in the same place. Before, a dock full of apps pushed it out of sight.
- The phone's assistant now wakes the way the swipe up from a bottom corner of the phone's screen wakes it: myCar asks Android to start it exactly as that gesture does, so it opens just as it does for the gesture, rather than in a typed chat. Choose your assistant under the car's Settings > Voice assistant.
- Moving wallpapers: Lava and Polar (slow drifting colour), Waves (rolling bands) and Fireflies (lights rising), under Wallpaper in the car's settings, below the still ones. They are drawn by myCar, so they stay myCar's own, with no pictures or videos from elsewhere.
- A new myCar icon.
- Vela Maps no longer carries the yellow "works in part" mark. It connects, draws the map and navigates with myCar; its map moving in steps is how Vela draws it in the car, not a fault in myCar.
- Home no longer shows "Map stopped." for a moment as the car connects, before the map starts by itself.

## Also in 0.5.03-alpha
- Gemini: when chosen as the assistant, myCar asks the Google app, which serves Gemini's voice, before falling back to its typed chat.
- On the app grid, the button at the foot of the dock changes to a map-and-player icon and takes you back to the map with the music beside it. Everywhere else it opens the app grid, as before.

## Also in 0.5.02-alpha
- Two smaller sizes for the car screen, under the car's Settings > Size: Small (90%) and Extra small (83%), beside Normal, Large and Extra large.
- Screen sharing turns the phone sideways while it shows in the car, so apps fill the car's wide screen instead of standing upright between two bars. When sharing stops, the phone is free to turn back. Apps that only work upright stay upright. It needs myCar's touch relay switched on under Accessibility, as touch on the shared screen already does.

## Also in 0.5.01-alpha
- With the dock, the player beside the map opened only by holding the song in the dock, so with music playing the map was on its own. Now the player sits beside the map as soon as something plays, as it always did. Its X puts it away for the drive; tap the song in the dock to bring it back, or hold it for the full player.

## Also in 0.5.00-alpha
- Wallpapers: seven of myCar's own (Aurora, Sunset, Ocean, Nebula, Ember, Forest, Graphite), behind the app grid and the dashboard, with the dock in glass over them. Pick one, or none, under the car's Settings > Wallpaper. They're drawn by myCar itself, with a fine grain so the video to the car doesn't break them into bands.
- Glass icons: myCar's own apps and every installed app's icon catch the light along their edges, with a soft shine on top.
- The player beside the map: the cover fills the whole side, and the song, progress and controls sit on frosted glass over it. Tap the cover for the full player.
- Voice assistant: under the car's Settings > Voice assistant, choose myCar's own (as before), the phone's assistant, or an assistant app on the phone (Google, Gemini, Alexa, ChatGPT and others). The dock's microphone and the steering wheel's voice button start the one you chose. Another assistant listens and answers on the phone, and its voice reaches the car only over Bluetooth.
- Apps that work with myCar only in part (Vela, Spotify, Deezer) have a yellow mark on their icon.

## Changed
- 0.4.00-alpha's changes are all here too: the dock, the paged app grid, Inter, the dark theme, the call and Bluetooth fixes.
