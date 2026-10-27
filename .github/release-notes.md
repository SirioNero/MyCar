myCar 0.5.05-alpha: car audio that finds its way to the car.

## New
- Car audio: Automatic, now the default. When the car shows myCar, myCar checks whether the car takes the phone's music over Bluetooth. If it does, the phone plays over Bluetooth as before, and Android never asks. If it doesn't, as many cars do while Android Auto runs, keeping Bluetooth for calls, myCar sends the sound through its own link to the car, over the cable or Wi-Fi, as Android Auto does. Android then asks once each drive to share the audio; the one-time adb command in Help stops that. If the Bluetooth music connection drops mid-drive, the sound moves to myCar's link.
- USB audio is now called Through myCar: it always worked over Wi-Fi too. Bluetooth and Through myCar can still be picked by hand, in the car's settings or the phone's.

## Changed
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
