package io.github.sirionero.mycar

/**
 * What changed in each release, newest first, shown on the car screen after an update (and on
 * the phone). Only the changes a driver would notice. Plain words, short lines: this is read in
 * a parked car.
 */
object Changelog {

    class Release(val versionCode: Int, val versionName: String, val changes: List<String>)

    val releases = listOf(
        Release(
            26, "0.5.13-alpha",
            listOf(
                "Plancia and Xonos say why they can't connect: Tailscale leaving myCar out, a Tailscale name that isn't found, or Android 17 keeping myCar off your home network.",
                "The drive keeps going with the phone locked: myCar stays in the foreground, keeps the phone awake and, over wireless, the Wi-Fi at full speed.",
                "When the car ends the session, myCar ends it too: the phone's sound comes back and Xonos stops, instead of playing on to nobody.",
                "A music app that closes no longer leaves the player stuck, and a song already playing on the phone is the one the car shows.",
                "Voice: \"call\" picks the right contact, \"stop navigation\" ends the route, and the music no longer stays paused after myCar speaks.",
                "After screen sharing the car's music comes back: Android asks again for the car audio, one question at a time.",
                "Dozens of smaller fixes: calls, messages, screen sharing, Plancia, the browser and wireless.",
            ),
        ),
        Release(
            25, "0.5.12-alpha",
            listOf(
                "Plancia and Xonos reach your Home Assistant and Music Assistant at home again, plain http addresses included. On Android 17, myCar needs the Nearby devices permission for your home network, and now asks for it.",
                "If that permission is missing, the car says so, rather than that Home Assistant or Music Assistant isn't answering.",
            ),
        ),
        Release(
            24, "0.5.11-alpha",
            listOf(
                "Xonos is now part of myCar: your Music Assistant library among the car's music apps. Set it up on the phone, or bring your account over from the Xonos app with one tap.",
                "With the car audio through myCar, Xonos plays straight into the car, and Android doesn't ask to share the audio while you listen to it.",
            ),
        ),
        Release(
            23, "0.5.10-alpha",
            listOf(
                "Plancia is now part of myCar: your Home Assistant dashboards, ready the moment you open them, from the app grid, one of your pages, or beside the map.",
                "Set Plancia up in myCar's settings on the phone, or bring your account over from the Plancia app with one tap.",
                "Home Assistant's menu button opens Plancia's menu: your dashboards, the one that opens first, the size and Reload.",
            ),
        ),
        Release(
            22, "0.5.09-alpha",
            listOf(
                "The music comes back after a call or the phone's assistant. Before, once the car took its speakers for a moment, the music stayed off until the next drive.",
                "A new player: plain controls, a thin line to seek, the cover's colours behind it. The cover stays in sight while you browse.",
                "With the phone locked, the microphone lights the phone and asks you to unlock it; your assistant starts listening as soon as it opens.",
                "Android's question for the car audio is one tap: no app to pick, and every app's sound reaches the car.",
            ),
        ),
        Release(
            21, "0.5.08-alpha",
            listOf(
                "Pages beside the map: swipe across Home, or tap the dots at its foot. Ready-made, the weather and your agenda left of the map, and Plancia's dashboard beside the browser right of it.",
                "The pencil beside the dots changes a page: two halves or four parts, each the weather, the agenda, the browser or a car app such as Plancia. Add, move and delete pages there too.",
            ),
        ),
        Release(
            20, "0.5.07-alpha",
            listOf(
                "The phone's music no longer stays muted if myCar was closed mid-drive while sending the sound to the car.",
                "Saying yes to Android's audio or screen-sharing question after the car has gone no longer mutes the phone or leaves it sharing.",
                "Less memory during drives without voice commands, and old logs no longer pile up on the phone.",
            ),
        ),
        Release(
            19, "0.5.06-alpha",
            listOf(
                "myCar no longer risks closing at the end of a drive with the phone locked.",
                "The weather finds where you are without the twelve-second wait.",
                "Long drives with lots of music use far less memory: cover art is sized for the car's screen.",
            ),
        ),
        Release(
            18, "0.5.05-alpha",
            listOf(
                "Car audio: Automatic, now the default, plays over Bluetooth when the car takes the phone's music that way, and through myCar otherwise, over the cable or Wi-Fi.",
                "USB audio is now called Through myCar: it works over Wi-Fi too.",
            ),
        ),
        Release(
            17, "0.5.04-alpha",
            listOf(
                "Beside the map: the music player while something plays, as before, or an app of your choice, such as Plancia or Xonos, always there. Pick it in the car's settings.",
                "The microphone stays at the foot of the dock, just above the app grid button, however many apps are open.",
                "The phone's assistant now wakes the way the swipe up from a bottom corner of the phone wakes it. Choose it under the car's Settings > Voice assistant.",
                "Moving wallpapers: Lava, Polar, Waves and Fireflies, under Wallpaper in the car's settings, below the still ones.",
                "A new myCar icon.",
                "Vela Maps no longer carries the yellow \"works in part\" mark: it navigates with myCar.",
            ),
        ),
        Release(
            16, "0.5.03-alpha",
            listOf(
                "Gemini opens listening too: myCar asks the Google app, which listens for Gemini, and opens a typed chat only if no app will listen.",
                "On the app grid, the button at the foot of the dock takes you back to the map with the music beside it.",
                "Moving wallpapers: set Wallpaper motion to Moving in the car's settings, and the colours drift slowly.",
            ),
        ),
        Release(
            15, "0.5.02-alpha",
            listOf(
                "Two smaller sizes for the car screen: Small (90%) and Extra small (83%), in the car's settings.",
                "The phone's assistant opens listening, as from the corner of the phone's screen, not in its typed chat.",
                "Screen sharing turns the phone sideways, so apps fill the car's wide screen. It turns back when you stop.",
            ),
        ),
        Release(
            14, "0.5.01-alpha",
            listOf(
                "Music and map share the screen again: the player sits beside the map as soon as something plays.",
                "Tap the song in the dock to put the player away or bring it back. Hold it for the full player.",
            ),
        ),
        Release(
            13, "0.5.00-alpha",
            listOf(
                "Wallpapers behind the app grid and the dashboard, with the dock in glass over them. Pick one, or none, in the car's settings.",
                "Icons in glass: light along the edges and a soft shine.",
                "The player beside the map fills its side with the cover, its controls on frosted glass.",
                "Choose the voice assistant: myCar's own, the phone's, or another assistant app.",
                "Apps that work only in part with myCar have a yellow mark.",
            ),
        ),
        Release(
            12, "0.4.00-alpha",
            listOf(
                "A new look: a dock down the left with the clock and your apps, and the app grid in pages.",
                "App icons sit in a rounded square frame, and the car screen uses the Inter typeface.",
                "Dark by default. Choose light, or follow the phone, in the car's settings.",
                "Tap the song in the dock to put the player beside the map. Hold it for the full player.",
                "Calls reach the car only over Bluetooth, USB audio or not. myCar now says when the phone isn't connected, and when a call is playing on the phone.",
                "Car apps show their own icons again, instead of another app's.",
            ),
        ),
        Release(
            11, "0.3.02-alpha",
            listOf(
                "Gearslip is now myCar, with a new name and icon. Updates come from github.com/sirionero/mycar.",
                "Calls from your contacts show on the car screen, and you can answer or decline them there.",
                "A call card no longer stays on the screen after you decline, or when the caller hangs up.",
                "End a call from the car screen or the steering wheel.",
            ),
        ),
        Release(
            9, "0.3.00-alpha",
            listOf(
                "Wireless Android Auto: pair the phone with the car once, and leave the cable at home.",
                "Voice control is built in. Say \"navigate to a gas station\", \"play\" a song or artist, or open an app by name.",
                "\"Navigate to\" picks the nearest place and stops at the map app's start screen, so you only tap Start.",
                "myCar turns speech into text on the phone, with no Google account. Download the voice models once in setup.",
                "The car's rotary knob moves between buttons, so cars without a touchscreen work too.",
                "Choose how music reaches the car: Bluetooth, or USB with no delay. myCar offers to pair with the car the first time.",
                "The car speaks each turn from your map app, and shows the directions on its own display where it can.",
                "The car shows what's playing, and music apps get their own like, shuffle and repeat buttons.",
                "Pick how the car's buttons look. Change it any time in the car's settings.",
                "A new destination mid-route replaces the old route, and switching map apps ends the old app's route.",
                "The car warns you when the phone's location is off.",
                "myCar finds new versions and installs them for you.",
                "More cars play music over USB, show a sharper picture, and put taps where you press.",
                "On GrapheneOS, myCar no longer needs exploit protection compatibility mode.",
                "The app is half the size it was.",
            ),
        ),
        Release(
            8, "0.2.00-alpha",
            listOf(
                "Exit takes you to the car's own screen. Tap Android Auto on the car to come back.",
                "myCar listens through the car's microphone when the car has one, so voice works with the phone in a pocket.",
                "A voice reply sends when its bar runs out, even over road noise or the radio. Say \"cancel\" or \"change it\" before then.",
                "Maps turn dark when the car switches to night mode.",
                "A moving map no longer makes the car screen fall behind your taps.",
                "myCar asks to send sound to the car as soon as you connect, so videos in the web browser play in the car too.",
                "Cars that offer more than one kind of music audio, such as the 2022 Dacia Jogger, play sound again.",
                "When the car shows a keyboard, you can type on the phone instead. The car's keyboard is taller too.",
                "Every car screen has a back button.",
                "App icons show whether an app works in myCar. Metrolist works and shows the green check.",
                "LIVI connects again, and touch works on cars with a second screen.",
                "The phone's Settings screen has a new layout, with What's new and the setup guide at the top.",
                "You can choose to send short usage notes that help the project. They hold no name, account, or location, and they're off unless you turn them on.",
                "Searching inside a media app moved to Settings > Experimental features.",
                "Under Settings > Media audio, the choices are now Phone audio and Car audio.",
                "Logs leave out your location and the car's serial number.",
            ),
        ),
        Release(
            7, "0.1.10-alpha",
            listOf(
                "The launcher groups your apps: Phone, Maps, Music, Messaging, Web, Screen sharing, and Settings.",
                "To move an app, press and hold it on the launcher, then tap Move left or Move right.",
                "MapQuest and Flitsmeister work in myCar and show the green check.",
                "Spotify has one tile. If Spotify isn't open on the phone, tap Open Spotify to start it.",
                "Flick a map and it glides on, as it does in Android Auto.",
                "The map picks up where it left off when you come back from another screen.",
                "Search boxes in map apps are larger and easier to read.",
                "Map apps keep the right icons on their buttons after they update.",
                "When myCar asks \"Do you want to reply?\", a bar shows how long it will listen. The bar stops while you speak.",
                "myCar fits its picture to wide car screens and keeps it clear of the edges the car crops.",
                "myCar answers the car's connection checks correctly. Some cars, Audis among them, hung up after a second without this.",
                "Google Maps no longer shows up as a messaging app.",
            ),
        ),
        Release(
            6, "0.1.0-alpha",
            listOf(
                "Messaging apps such as Signal and Molly open to your recent chats.",
                "myCar reads a message aloud, and you reply by voice. Say \"cancel\" or \"change it\" before it sends, or tap a quick reply such as your arrival time.",
                "Voice works on any phone, GrapheneOS included. myCar turns speech into text itself, so it needs no Google account.",
                "New messages show as a small card at the top right, even while you drive. A bar shows how long until the card closes or the reply sends.",
                "Only messages, missed calls, alarms, and reminders pop up. Everything else waits on the dashboard.",
                "The map stays loaded when you switch screens, so it no longer flashes black.",
                "Map apps draw more reliably and keep their connection when the phone is locked.",
                "The Home button shows your map app, so it no longer appears twice.",
                "The next turn shows in the nav bar when you leave the map.",
                "Steering wheel buttons, control knobs, and D-pads now work. If yours don't, open an issue on GitHub.",
                "To see the whole song title and artist in the nav bar, choose Settings > Player in the nav bar > Song title.",
                "Choose miles or kilometres under Settings > Units.",
                "Try the voice assistant and the vehicle data screen under Settings > Experimental features.",
                "Logs now say why a car ended the connection.",
                "Spotify, Waze, and Google Messages updated to a version of Google's car library that only accepts Android Auto itself. Their own car screens won't open in myCar. Spotify still plays through the myCar player, which has the green check.",
                "Calls go through the car's Bluetooth, as they do in Android Auto. Pair your phone with the car to hear calls on its speakers.",
            ),
        ),
    )

    /** The release that matches this build, if it has notes. */
    val current: Release? get() = releases.firstOrNull { it.versionCode == BuildConfig.VERSION_CODE }
}
