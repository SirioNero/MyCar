myCar 0.5.13-alpha: Cinema.

## New
- Cinema, myCar's video app: your video sites as tiles on the car screen, each with the icon it gives itself. YouTube, RaiPlay and Twitch are there to start with; add your own, or take them away, in myCar's Settings > Cinema on the phone - your Jellyfin at home too.
- Each site opens in a browser made for video: full screen when the player asks for it, the car's own keyboard for searching, and Back steps out of full screen, then back through the site, then back to your sites. A site can open as on a computer, when its phone layout doesn't suit the car's screen.
- Video only when parked: while the car moves, the picture is covered and the sound carries on. The car says when it's moving, as it does to Android Auto; a car that doesn't, the phone's GPS does. It's a setting, on by default, which myCar strongly advises keeping on: turning it off asks first.

## Good to know
- Protected video (Netflix, Prime Video, Disney+, DAZN and the like) most likely stays black in the car: Android hides it from a screen sent to the car as video. Its sites can still be added, and Cinema says so on their tiles.
- A site on your home network, such as Jellyfin, needs myCar's Nearby devices permission on Android 17: myCar asks for it when you add the site.

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

